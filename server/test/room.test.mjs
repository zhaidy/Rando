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
