// Rando 一起骑的服务端。
// 每个队伍（队伍码）是一个 Durable Object 房间，队员用 WebSocket 连进来，
// 房间把位置、对讲语音转给其他队员。只在内存里记每人最后的位置，不存历史。
//
// 连接：GET /room/<队伍码>?id=<队员 id>&name=<名字>，带 Upgrade: websocket
// 客户端发：{"t":"pos","lat":..,"lon":..,"speed":..,"bearing":..}
//          {"t":"voice","format":"aac-live","sid":..,"seq":..,"end":false,"data":"<base64>"}（实时对讲，一小段一小段地发）
//          {"t":"route","ts":..,"title":"Marina Bay Sands","meters":13600,"profile":"PCN","avoidRoads":true,"avoidBridges":false,
//           "stops":[[lat,lon],...]}（开始导航时分享路线；队友按同样的点和配置算，得到同一条路）
//          {"t":"route_clear"}（结束导航）
//          {"t":"sos","lat":..,"lon":..}（可能摔车了） {"t":"sos_clear"}（我没事）
//          {"t":"hazard","hid":..,"type":"CONSTRUCTION","lat":..,"lon":..,"ts":..}（标了一处路况） {"t":"hazard_del","hid":..}
//          {"t":"rtc","to":"<队员 id>","data":{..}}（WebRTC 直连的信令：offer、answer、ICE 候选；没有 to 就发给所有人）
// 服务端发：{"t":"roster","members":[{id,name,lat,lon,speed,bearing,ts}...]}（刚连上时）
//          {"t":"join","id","name"} {"t":"leave","id"}
//          {"t":"pos",id,name,lat,lon,speed,bearing,ts} {"t":"voice",id,name,format,sid,seq,end,data,ts}
//          {"t":"route",id,name,ts,title,meters,profile,avoidRoads,avoidBridges,stops} {"t":"route_clear",id}
//          {"t":"sos",id,name,lat,lon,ts} {"t":"sos_clear",id}
//          {"t":"hazard",id,name,hid,type,lat,lon,ts} {"t":"hazard_del",id,hid}（只转发，不存；各人手机自己存）
//          {"t":"rtc",id,data}（只转发，不看内容）
//          分享的路线、求助存在房间里，后来加入的人连上就能收到；本人离队就删掉
// "ping" 直接回 "pong"，不用唤醒房间
//
// WebRTC 直连用的服务器：GET /ice，返回 {"iceServers":[{urls,username?,credential?}...]}。
// 一直有 STUN；配了 Cloudflare TURN（Secrets：TURN_KEY_ID、TURN_KEY_API_TOKEN）再加上 TURN，打不通直连时中转
//
// 附近的队伍：GET /nearby?lat=..&lon=..，返回 {"teams":[{code,names,members,meters}...]}，近的在前。
// 房间收到队员位置时（每个房间最多半分钟一次）告诉大厅（Lobby，全局只有一个）自己在哪；
// 十分钟没报的、没人了的就不列了。只在内存里记，不存

import { DurableObject } from "cloudflare:workers";

const CODE = /^[A-Z0-9]{4,12}$/;
const MAX_MEMBERS = 20;
const MAX_NAME = 20;
// 对讲一小段只有几百字节，这个上限只是防止乱发
const MAX_MESSAGE = 512 * 1024;
// 一条路线最多这么多个点（起点、途经点、终点；原路返回会多一些）
const MAX_STOPS = 100;
const MAX_TITLE = 40;
// 列出这么远以内的队伍
const NEARBY_METERS = 1000;
// 这么久没报位置的队伍不列
const NEARBY_STALE_MS = 10 * 60 * 1000;
// 房间最多这么久报一次
const REPORT_EVERY_MS = 30 * 1000;
const NEARBY_LIMIT = 10;
const STUN = [{ urls: ["stun:stun.cloudflare.com:3478", "stun:stun.l.google.com:19302"] }];
// TURN 临时账号的有效期
const TURN_TTL_S = 24 * 3600;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/nearby") {
      // Number(null) 是 0，没带参数要先挡掉
      const lat = Number(url.searchParams.get("lat") ?? NaN);
      const lon = Number(url.searchParams.get("lon") ?? NaN);
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) return new Response("缺少位置", { status: 400 });
      const teams = await lobby(env).nearby(lat, lon);
      return Response.json({ teams });
    }
    if (url.pathname === "/ice") return Response.json({ iceServers: await iceServers(env) });
    const match = url.pathname.match(/^\/room\/([^/]+)$/);
    if (!match) {
      return new Response("rando-group ok\n", { headers: { "content-type": "text/plain; charset=utf-8" } });
    }
    let code;
    try {
      code = decodeURIComponent(match[1]).toUpperCase();
    } catch {
      return new Response("队伍码不对", { status: 400 });
    }
    if (!CODE.test(code)) return new Response("队伍码不对", { status: 400 });
    if (request.headers.get("Upgrade") !== "websocket") return new Response("要用 WebSocket 连接", { status: 426 });
    const id = url.searchParams.get("id") ?? "";
    if (!/^[A-Za-z0-9-]{8,64}$/.test(id)) return new Response("缺少队员 id", { status: 400 });
    const room = env.ROOMS.get(env.ROOMS.idFromName(code));
    // 房间自己不知道队伍码（Durable Object 只知道 id），带过去
    const forward = new URL(request.url);
    forward.searchParams.set("code", code);
    return room.fetch(new Request(forward, request));
  },
};

export class Room extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    // 客户端每隔一会儿发 "ping" 保活，自动回 "pong"，房间可以一直休眠
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(request) {
    const url = new URL(request.url);
    const id = url.searchParams.get("id");
    const name = (url.searchParams.get("name") ?? "").trim().slice(0, MAX_NAME) || "队友";
    const code = url.searchParams.get("code") ?? "";

    // 同一个人重连：把旧连接关掉，名额不重复算
    for (const old of this.ctx.getWebSockets(id)) {
      try { old.close(4000, "replaced"); } catch {}
    }
    if (this.members().length >= MAX_MEMBERS) {
      return new Response("队伍满了", { status: 429 });
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server, [id]);
    server.serializeAttachment({ id, name, code, pos: null });

    const others = this.members().filter((m) => m.id !== id);
    server.send(JSON.stringify({ t: "roster", members: others }));
    // 队友已经分享的路线、还没撤掉的求助
    const keys = others.flatMap((m) => [`route:${m.id}`, `sos:${m.id}`]);
    const saved = await this.ctx.storage.get(keys);
    for (const msg of saved.values()) server.send(JSON.stringify(msg));
    this.broadcast({ t: "join", id, name }, id);
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws, message) {
    if (typeof message !== "string" || message.length > MAX_MESSAGE) return;
    let msg;
    try {
      msg = JSON.parse(message);
    } catch {
      return;
    }
    const me = ws.deserializeAttachment();
    if (!me) return;
    const ts = Date.now();
    if (msg.t === "pos" && Number.isFinite(msg.lat) && Number.isFinite(msg.lon)) {
      const pos = {
        lat: msg.lat,
        lon: msg.lon,
        speed: Number.isFinite(msg.speed) ? msg.speed : null,
        bearing: Number.isFinite(msg.bearing) ? msg.bearing : null,
        ts,
      };
      ws.serializeAttachment({ ...me, pos });
      this.broadcast({ t: "pos", id: me.id, name: me.name, ...pos }, me.id);
      await this.report(me.code, pos);
    } else if (msg.t === "voice" && typeof msg.data === "string") {
      // 实时对讲：一句话切成 0.1 秒多一段连续发，sid 是这句话的编号，seq 是第几段，end 表示说完了
      this.broadcast(
        {
          t: "voice", id: me.id, name: me.name, format: String(msg.format ?? "aac"),
          sid: Number(msg.sid) || 0, seq: Number(msg.seq) || 0, end: msg.end === true, data: msg.data, ts,
        },
        me.id,
      );
    } else if (msg.t === "route") {
      const route = parseRoute(msg);
      if (!route) return;
      const out = { t: "route", id: me.id, name: me.name, ...route };
      await this.ctx.storage.put(`route:${me.id}`, out);
      this.broadcast(out, me.id);
    } else if (msg.t === "route_clear") {
      // 只清路线：求救要等 sos_clear（我没事）才清，停了导航不代表人没事
      await this.ctx.storage.delete(`route:${me.id}`);
      this.broadcast({ t: "route_clear", id: me.id }, me.id);
    } else if (msg.t === "sos" && Number.isFinite(msg.lat) && Number.isFinite(msg.lon)) {
      const out = { t: "sos", id: me.id, name: me.name, lat: msg.lat, lon: msg.lon, ts };
      await this.ctx.storage.put(`sos:${me.id}`, out);
      this.broadcast(out, me.id);
    } else if (msg.t === "hazard" && Number.isFinite(msg.lat) && Number.isFinite(msg.lon) && typeof msg.hid === "string") {
      this.broadcast(
        {
          t: "hazard", id: me.id, name: me.name, hid: msg.hid.slice(0, 64), type: String(msg.type ?? "").slice(0, 20),
          lat: msg.lat, lon: msg.lon, ts: Number(msg.ts) || ts,
        },
        me.id,
      );
    } else if (msg.t === "hazard_del" && typeof msg.hid === "string") {
      this.broadcast({ t: "hazard_del", id: me.id, hid: msg.hid.slice(0, 64) }, me.id);
    } else if (msg.t === "rtc" && msg.data && typeof msg.data === "object") {
      const out = { t: "rtc", id: me.id, data: msg.data };
      if (typeof msg.to === "string") this.sendTo(msg.to, out);
      else this.broadcast(out, me.id);
    } else if (msg.t === "sos_clear") {
      await this.ctx.storage.delete(`sos:${me.id}`);
      this.broadcast({ t: "sos_clear", id: me.id }, me.id);
    }
  }

  async webSocketClose(ws) {
    await this.left(ws);
  }

  async webSocketError(ws) {
    await this.left(ws);
  }

  async left(ws) {
    const me = ws.deserializeAttachment();
    if (!me) return;
    // 被同一个人的新连接顶掉时，新连接还在，不算离队
    const stillHere = this.ctx.getWebSockets(me.id).some((other) => other !== ws);
    if (stillHere) return;
    // 断线重连时客户端会重新分享
    await this.ctx.storage.delete(`route:${me.id}`);
    this.broadcast({ t: "leave", id: me.id }, me.id);
    if (me.code && this.members().every((m) => m.id === me.id)) {
      try { await lobby(this.env).remove(me.code); } catch {}
    }
  }

  /** 告诉大厅这个队伍在哪；半分钟内报过就不报了（休眠醒来后会多报一次，不要紧） */
  async report(code, pos) {
    if (!code || Date.now() - (this.reportedAt ?? 0) < REPORT_EVERY_MS) return;
    this.reportedAt = Date.now();
    const names = this.members().map((m) => m.name);
    try {
      await lobby(this.env).report(code, { lat: pos.lat, lon: pos.lon, names, ts: pos.ts });
    } catch {}
  }

  /** 房间里的人和他们最后的位置 */
  members() {
    const seen = new Map();
    for (const ws of this.ctx.getWebSockets()) {
      const a = ws.deserializeAttachment();
      if (a) seen.set(a.id, { id: a.id, name: a.name, ...(a.pos ?? {}) });
    }
    return [...seen.values()];
  }

  sendTo(id, msg) {
    const text = JSON.stringify(msg);
    for (const ws of this.ctx.getWebSockets(id)) {
      try { ws.send(text); } catch {}
    }
  }

  broadcast(msg, exceptId) {
    const text = JSON.stringify(msg);
    for (const ws of this.ctx.getWebSockets()) {
      const tags = this.ctx.getTags(ws);
      if (tags.includes(exceptId)) continue;
      try { ws.send(text); } catch {}
    }
  }
}

/** STUN 加上 Cloudflare TURN 的临时账号；TURN 没配或者拿不到就只有 STUN */
async function iceServers(env) {
  if (!env.TURN_KEY_ID || !env.TURN_KEY_API_TOKEN) return STUN;
  try {
    const res = await fetch(`https://rtc.live.cloudflare.com/v1/turn/keys/${env.TURN_KEY_ID}/credentials/generate-ice-servers`, {
      method: "POST",
      headers: { Authorization: `Bearer ${env.TURN_KEY_API_TOKEN}`, "Content-Type": "application/json" },
      body: JSON.stringify({ ttl: TURN_TTL_S }),
    });
    if (!res.ok) return STUN;
    const { iceServers } = await res.json();
    // 返回的有时是一个对象，有时是数组
    const turn = Array.isArray(iceServers) ? iceServers : [iceServers];
    return [...STUN, ...turn.filter((s) => s && s.urls)];
  } catch {
    return STUN;
  }
}

function lobby(env) {
  return env.LOBBY.get(env.LOBBY.idFromName("lobby"));
}

/** 大厅：记着每个队伍最近在哪，回答「附近有哪些队伍」 */
export class Lobby extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.teams = new Map();
  }

  report(code, team) {
    this.teams.set(code, team);
  }

  remove(code) {
    this.teams.delete(code);
  }

  nearby(lat, lon) {
    const now = Date.now();
    const result = [];
    for (const [code, t] of this.teams) {
      if (now - t.ts > NEARBY_STALE_MS) {
        this.teams.delete(code);
        continue;
      }
      const meters = distance(lat, lon, t.lat, t.lon);
      if (meters <= NEARBY_METERS) result.push({ code, names: t.names, members: t.names.length, meters: Math.round(meters) });
    }
    return result.sort((a, b) => a.meters - b.meters).slice(0, NEARBY_LIMIT);
  }
}

/** 两点距离（米），新加坡这么小的范围按平面算就够 */
function distance(lat1, lon1, lat2, lon2) {
  const k = 111320;
  const x = (lon2 - lon1) * k * Math.cos(((lat1 + lat2) / 2) * Math.PI / 180);
  const y = (lat2 - lat1) * k;
  return Math.hypot(x, y);
}

/** 检查分享的路线；不对返回 null */
function parseRoute(msg) {
  const stops = msg.stops;
  if (!Array.isArray(stops) || stops.length < 2 || stops.length > MAX_STOPS) return null;
  const ok = stops.every((p) => Array.isArray(p) && p.length === 2 && p.every(Number.isFinite));
  if (!ok) return null;
  return {
    ts: Number(msg.ts) || Date.now(),
    title: String(msg.title ?? "").slice(0, MAX_TITLE),
    meters: Number.isFinite(msg.meters) ? msg.meters : 0,
    profile: String(msg.profile ?? "").slice(0, 20),
    avoidRoads: msg.avoidRoads === true,
    avoidBridges: msg.avoidBridges === true,
    stops,
  };
}
