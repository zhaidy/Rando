// Rando 一起骑的服务端。
// 每个队伍（队伍码）是一个 Durable Object 房间，队员用 WebSocket 连进来，
// 房间把位置、对讲语音转给其他队员。只在内存里记每人最后的位置，不存历史。
//
// 连接：GET /room/<队伍码>?id=<队员 id>&name=<名字>，带 Upgrade: websocket
// 客户端发：{"t":"pos","lat":..,"lon":..,"speed":..,"bearing":..}
//          {"t":"voice","format":"aac","ms":..,"data":"<base64>"}
// 服务端发：{"t":"roster","members":[{id,name,lat,lon,speed,bearing,ts}...]}（刚连上时）
//          {"t":"join","id","name"} {"t":"leave","id"}
//          {"t":"pos",id,name,lat,lon,speed,bearing,ts} {"t":"voice",id,name,format,ms,data,ts}
// "ping" 直接回 "pong"，不用唤醒房间

import { DurableObject } from "cloudflare:workers";

const CODE = /^[A-Z0-9]{4,12}$/;
const MAX_MEMBERS = 20;
const MAX_NAME = 20;
// 对讲语音一段十几 KB，base64 以后也远小于这个
const MAX_MESSAGE = 512 * 1024;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const match = url.pathname.match(/^\/room\/([^/]+)$/);
    if (!match) {
      return new Response("rando-group ok\n", { headers: { "content-type": "text/plain; charset=utf-8" } });
    }
    const code = decodeURIComponent(match[1]).toUpperCase();
    if (!CODE.test(code)) return new Response("队伍码不对", { status: 400 });
    if (request.headers.get("Upgrade") !== "websocket") return new Response("要用 WebSocket 连接", { status: 426 });
    const id = url.searchParams.get("id") ?? "";
    if (!/^[A-Za-z0-9-]{8,64}$/.test(id)) return new Response("缺少队员 id", { status: 400 });
    const room = env.ROOMS.get(env.ROOMS.idFromName(code));
    return room.fetch(request);
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
    server.serializeAttachment({ id, name, pos: null });

    server.send(JSON.stringify({ t: "roster", members: this.members().filter((m) => m.id !== id) }));
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
    } else if (msg.t === "voice" && typeof msg.data === "string") {
      this.broadcast(
        { t: "voice", id: me.id, name: me.name, format: String(msg.format ?? "aac"), ms: Number(msg.ms) || 0, data: msg.data, ts },
        me.id,
      );
    }
  }

  async webSocketClose(ws) {
    this.left(ws);
  }

  async webSocketError(ws) {
    this.left(ws);
  }

  left(ws) {
    const me = ws.deserializeAttachment();
    if (!me) return;
    // 被同一个人的新连接顶掉时，新连接还在，不算离队
    const stillHere = this.ctx.getWebSockets(me.id).some((other) => other !== ws);
    if (!stillHere) this.broadcast({ t: "leave", id: me.id }, me.id);
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

  broadcast(msg, exceptId) {
    const text = JSON.stringify(msg);
    for (const ws of this.ctx.getWebSockets()) {
      const tags = this.ctx.getTags(ws);
      if (tags.includes(exceptId)) continue;
      try { ws.send(text); } catch {}
    }
  }
}
