// 在本机跑 wrangler dev，模拟两个队员连进同一个房间
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";

const PORT = 8799;
let dev;

before(async () => {
  // detached：结束时连 npx 下面的 wrangler、workerd 一起关掉
  dev = spawn("npx", ["wrangler", "dev", "--port", String(PORT), "--ip", "127.0.0.1"], { stdio: ["ignore", "pipe", "pipe"], detached: true });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("wrangler dev 没起来")), 60_000);
    const onData = (d) => {
      if (String(d).includes("Ready on")) {
        clearTimeout(timer);
        resolve();
      }
    };
    dev.stdout.on("data", onData);
    dev.stderr.on("data", onData);
  });
});

after(() => {
  if (dev) process.kill(-dev.pid);
});

function join(code, id, name) {
  const ws = new WebSocket(`ws://127.0.0.1:${PORT}/room/${code}?id=${id}&name=${encodeURIComponent(name)}`);
  const inbox = [];
  const waiters = [];
  ws.addEventListener("message", (e) => {
    const msg = e.data === "pong" ? { t: "pong" } : JSON.parse(e.data);
    const w = waiters.findIndex((x) => x.match(msg));
    if (w >= 0) waiters.splice(w, 1)[0].resolve(msg);
    else inbox.push(msg);
  });
  const next = (t) =>
    new Promise((resolve, reject) => {
      const i = inbox.findIndex((m) => m.t === t);
      if (i >= 0) return resolve(inbox.splice(i, 1)[0]);
      waiters.push({ match: (m) => m.t === t, resolve });
      setTimeout(() => reject(new Error(`${name} 没收到 ${t}`)), 5000);
    });
  const opened = new Promise((resolve, reject) => {
    ws.addEventListener("open", resolve);
    ws.addEventListener("error", reject);
  });
  return { ws, next, opened, send: (m) => ws.send(typeof m === "string" ? m : JSON.stringify(m)) };
}

test("队员互相看到位置、听到对讲，离队有通知", async () => {
  const a = join("TEST01", "aaaaaaaa-1", "阿明");
  await a.opened;
  assert.deepEqual((await a.next("roster")).members, []);
  a.send({ t: "pos", lat: 1.3, lon: 103.8, speed: 5, bearing: 90 });

  const b = join("TEST01", "bbbbbbbb-2", "小红");
  await b.opened;
  const roster = await b.next("roster");
  assert.equal(roster.members.length, 1);
  assert.equal(roster.members[0].name, "阿明");
  assert.equal(roster.members[0].lat, 1.3);
  assert.equal((await a.next("join")).name, "小红");

  b.send({ t: "pos", lat: 1.31, lon: 103.81 });
  const pos = await a.next("pos");
  assert.equal(pos.id, "bbbbbbbb-2");
  assert.equal(pos.lat, 1.31);

  a.send({ t: "voice", format: "aac-live", sid: 7, seq: 3, end: true, data: "AAAA" });
  const voice = await b.next("voice");
  assert.equal(voice.name, "阿明");
  assert.equal(voice.sid, 7);
  assert.equal(voice.seq, 3);
  assert.equal(voice.end, true);

  a.send("ping");
  await a.next("pong");

  b.ws.close();
  assert.equal((await a.next("leave")).id, "bbbbbbbb-2");
  a.ws.close();
});

test("不同队伍互不干扰，队伍码不对就拒绝", async () => {
  const a = join("TEAMAA", "aaaaaaaa-3", "甲");
  const b = join("TEAMBB", "bbbbbbbb-4", "乙");
  await Promise.all([a.opened, b.opened]);
  await a.next("roster");
  assert.deepEqual((await b.next("roster")).members, []);
  a.ws.close();
  b.ws.close();
  // 队伍码先检查，不用 WebSocket 也能看到被拒
  const res = await fetch(`http://127.0.0.1:${PORT}/room/bad!code`);
  assert.equal(res.status, 400);
});

test("分享的路线转给队友，后来加入的也能收到，结束或离队就撤掉", async () => {
  const a = join("TEST02", "aaaaaaaa-5", "阿明");
  await a.opened;
  await a.next("roster");
  const b = join("TEST02", "bbbbbbbb-6", "小红");
  await b.opened;
  await b.next("roster");
  await a.next("join");

  const stops = [[1.3, 103.8], [1.29, 103.85]];
  a.send({ t: "route", ts: 42, title: "Marina Bay Sands", meters: 13600, profile: "PCN", avoidRoads: true, stops });
  const got = await b.next("route");
  assert.equal(got.id, "aaaaaaaa-5");
  assert.equal(got.name, "阿明");
  assert.equal(got.ts, 42);
  assert.equal(got.title, "Marina Bay Sands");
  assert.equal(got.avoidRoads, true);
  assert.equal(got.avoidBridges, false);
  assert.deepEqual(got.stops, stops);

  // 不像路线的不转
  a.send({ t: "route", stops: [[1.3, "x"]] });

  // 后来加入的
  const c = join("TEST02", "cccccccc-7", "小李");
  await c.opened;
  await c.next("roster");
  assert.equal((await c.next("route")).ts, 42);

  a.send({ t: "route_clear" });
  assert.equal((await b.next("route_clear")).id, "aaaaaaaa-5");
  await c.next("route_clear");

  // 再分享一次然后离队：之后加入的收不到
  a.send({ t: "route", ts: 43, title: "x", stops });
  await b.next("route");
  a.ws.close();
  await b.next("leave");
  const d = join("TEST02", "dddddddd-8", "小周");
  await d.opened;
  await d.next("roster");
  await assert.rejects(d.next("route"));
  for (const x of [b, c, d]) x.ws.close();
});

test("可能摔车了告诉队友，后来加入的也能收到，我没事就撤掉", async () => {
  const a = join("TEST03", "aaaaaaaa-9", "阿明");
  await a.opened;
  await a.next("roster");
  const b = join("TEST03", "bbbbbbbb-10", "小红");
  await b.opened;
  await b.next("roster");
  await a.next("join");

  a.send({ t: "sos", lat: 1.3, lon: 103.8 });
  const sos = await b.next("sos");
  assert.equal(sos.id, "aaaaaaaa-9");
  assert.equal(sos.name, "阿明");
  assert.equal(sos.lat, 1.3);

  const c = join("TEST03", "cccccccc-11", "小李");
  await c.opened;
  await c.next("roster");
  assert.equal((await c.next("sos")).id, "aaaaaaaa-9");

  // 停了导航（清路线）不代表没事：之后加入的还要能收到求救
  a.send({ t: "route_clear" });
  await b.next("route_clear");
  await c.next("route_clear");
  const d = join("TEST03", "dddddddd-14", "小周");
  await d.opened;
  await d.next("roster");
  assert.equal((await d.next("sos")).id, "aaaaaaaa-9");

  a.send({ t: "sos_clear" });
  assert.equal((await b.next("sos_clear")).id, "aaaaaaaa-9");
  await c.next("sos_clear");
  await d.next("sos_clear");
  for (const x of [a, b, c, d]) x.ws.close();
});

test("App 被杀了没说我没事：重新连上就撤掉存着的求助；roster 带服务器时间", async () => {
  const a = join("TEST08", "aaaaaaaa-30", "阿明");
  await a.opened;
  await a.next("roster");
  a.send({ t: "sos", lat: 1.3, lon: 103.8 });
  a.send({ t: "pos", lat: 1.3, lon: 103.8 });
  const b = join("TEST08", "bbbbbbbb-31", "小红");
  await b.opened;
  const roster = await b.next("roster");
  assert.ok(Number.isFinite(roster.now));
  assert.ok(roster.now >= roster.members[0].ts);
  await b.next("sos");

  // 进程没了：连上以后不再发求助
  a.ws.close();
  await b.next("leave");
  const a2 = join("TEST08", "aaaaaaaa-30", "阿明");
  await a2.opened;
  await a2.next("roster");
  const c = join("TEST08", "cccccccc-32", "小李");
  await c.opened;
  await c.next("roster");
  await assert.rejects(c.next("sos"));
  for (const x of [a2, b, c]) x.ws.close();
});

test("标的路况转给队友", async () => {
  const a = join("TEST04", "aaaaaaaa-12", "阿明");
  const b = join("TEST04", "bbbbbbbb-13", "小红");
  await Promise.all([a.opened, b.opened]);
  await a.next("roster");
  await b.next("roster");
  a.send({ t: "hazard", hid: "h1", type: "CLOSED", lat: 1.3, lon: 103.8, ts: 42 });
  const h = await b.next("hazard");
  assert.equal(h.name, "阿明");
  assert.equal(h.hid, "h1");
  assert.equal(h.type, "CLOSED");
  assert.equal(h.ts, 42);
  a.send({ t: "hazard_del", hid: "h1" });
  assert.equal((await b.next("hazard_del")).hid, "h1");
  a.ws.close();
  b.ws.close();
});

test("附近的队伍：有人报了位置就列出来，走远了、没人了就不列", async () => {
  const near = await fetch(`http://127.0.0.1:${PORT}/nearby?lat=1.3001&lon=103.8001`);
  assert.equal(near.status, 200);
  assert.ok(!(await near.json()).teams.some((t) => t.code === "NEAR01"));

  const a = join("near01", "aaaaaaaa-20", "阿明");
  await a.opened;
  await a.next("roster");
  a.send({ t: "pos", lat: 1.3, lon: 103.8 });
  // 等房间报给大厅
  let team;
  for (let i = 0; i < 20 && !team; i++) {
    await new Promise((r) => setTimeout(r, 100));
    const res = await (await fetch(`http://127.0.0.1:${PORT}/nearby?lat=1.3001&lon=103.8001`)).json();
    team = res.teams.find((t) => t.code === "NEAR01");
  }
  assert.ok(team, "附近应该有 NEAR01");
  assert.deepEqual(team.names, ["阿明"]);
  assert.ok(team.meters < 50);

  const far = await (await fetch(`http://127.0.0.1:${PORT}/nearby?lat=1.35&lon=103.9`)).json();
  assert.ok(!far.teams.some((t) => t.code === "NEAR01"));
  assert.equal((await fetch(`http://127.0.0.1:${PORT}/nearby`)).status, 400);

  a.ws.close();
  let gone = false;
  for (let i = 0; i < 20 && !gone; i++) {
    await new Promise((r) => setTimeout(r, 100));
    const res = await (await fetch(`http://127.0.0.1:${PORT}/nearby?lat=1.3001&lon=103.8001`)).json();
    gone = !res.teams.some((t) => t.code === "NEAR01");
  }
  assert.ok(gone, "没人了就不列");
});

test("WebRTC 信令：指定了人只发给他，没指定发给所有人；/ice 至少有 STUN", async () => {
  const a = join("RTC001", "aaaaaaaa-30", "阿明");
  await a.opened;
  await a.next("roster");
  const b = join("RTC001", "bbbbbbbb-31", "小红");
  await b.opened;
  await b.next("roster");
  const c = join("RTC001", "cccccccc-32", "大伟");
  await c.opened;
  await c.next("roster");

  a.send({ t: "rtc", data: { k: "hi" } });
  assert.deepEqual(await b.next("rtc"), { t: "rtc", id: "aaaaaaaa-30", data: { k: "hi" } });
  assert.equal((await c.next("rtc")).id, "aaaaaaaa-30");

  b.send({ t: "rtc", to: "aaaaaaaa-30", data: { k: "offer", sdp: "v=0" } });
  const offer = await a.next("rtc");
  assert.equal(offer.id, "bbbbbbbb-31");
  assert.equal(offer.data.sdp, "v=0");
  // 大伟收不到发给阿明的
  await assert.rejects(c.next("rtc"));

  const ice = await (await fetch(`http://127.0.0.1:${PORT}/ice?room=RTC001&id=aaaaaaaa-30`)).json();
  assert.ok(ice.iceServers.some((s) => s.urls.some((u) => u.startsWith("stun:"))));
  // 不在队里的拿不到
  assert.equal((await fetch(`http://127.0.0.1:${PORT}/ice?room=RTC001&id=zzzzzzzz-99`)).status, 403);
  assert.equal((await fetch(`http://127.0.0.1:${PORT}/ice`)).status, 400);
  a.ws.close();
  b.ws.close();
  c.ws.close();
});
