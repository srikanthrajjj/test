// End-to-end test: local D1 + Worker (wrangler dev) + mocked Anthropic API. No network needed.
import { spawn, execSync } from "node:child_process";
import { startMock } from "./mock-anthropic.mjs";
import assert from "node:assert/strict";

const PORT = 8788, base = `http://127.0.0.1:${PORT}`;
const sh = c => execSync(c, { stdio: "pipe", cwd: new URL("..", import.meta.url).pathname });
sh("rm -rf .state && npx wrangler d1 execute nest --local --persist-to .state --file=schema.sql");
const { srv, seen } = await startMock();
const w = spawn("npx", ["wrangler", "dev", "--local", "--persist-to", ".state", "--port", String(PORT), "--var", "ANTHROPIC_API_KEY:test", "--var", "ANTHROPIC_BASE_URL:http://127.0.0.1:8799"], { cwd: new URL("..", import.meta.url).pathname, stdio: "pipe" });
let log = ""; w.stdout.on("data", d => log += d); w.stderr.on("data", d => log += d);
const api = async (path, { method = "GET", token, body } = {}) => {
  const r = await fetch(base + path, { method, headers: { "content-type": "application/json", ...(token ? { authorization: "Bearer " + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { status: r.status, body: await r.json() };
};
try {
  for (let i = 0; i < 60; i++) { try { if ((await fetch(base + "/v1/health")).ok) break; } catch { } await new Promise(r => setTimeout(r, 500)); }
  const h = await api("/v1/health"); assert.equal(h.body.ok, true); console.log("health ok, agent:", h.body.agent);

  const A = (await api("/v1/pair/create", { method: "POST", body: { name: "Arjun", color: "#5B5BD6" } })).body;
  assert.equal(A.member, "a"); assert.match(A.code, /^[A-Z2-9]{6}$/);
  assert.equal((await api("/v1/sync", { method: "POST", body: { since: 0 } })).status, 401);
  assert.equal((await api("/v1/pair/join", { method: "POST", body: { code: "ZZZZZZ", name: "x" } })).status, 404);
  const B = (await api("/v1/pair/join", { method: "POST", body: { code: A.code.toLowerCase(), name: "Meera", color: "#F0508B" } })).body;
  assert.equal(B.member, "b"); assert.equal(B.couple, A.couple);
  assert.equal((await api("/v1/pair/join", { method: "POST", body: { code: A.code, name: "Eve" } })).status, 409);
  const pk = await fetch(base + "/v1/pair/peek?code=" + A.code); assert.equal((await pk.json()).inviter, "Arjun");
  assert.equal((await fetch(base + "/v1/pair/peek?code=ZZZZZZ")).status, 404);
  const lp = await (await fetch(base + "/j/" + A.code)).text(); assert.match(lp, /Arjun invited you/); assert.match(lp, /intent:\/\/join\?code=/); assert.match(lp, /scheme=nest;package=com.nest.couples/);
  assert.match(await (await fetch(base + "/j/NOPE12")).text(), /Invite not found/);
  assert.match(await (await fetch(base + "/privacy")).text(), /Privacy Policy/);
  console.log("peek + landing + privacy ok");
  console.log("pairing ok");

  const t0 = Date.now();
  const e1 = { id: "e1", kind: "exp", u: t0, data: { amt: 1240, title: "Dinner at Toit", cat: "food", paid: "a", sa: 50, ts: t0, src: "manual" } };
  const e2 = { id: "e2", kind: "exp", u: t0 + 1, data: { amt: 600, title: "Zepto", cat: "groc", paid: "b", sa: 50, ts: t0, src: "manual" } };
  let r = (await api("/v1/sync", { method: "POST", token: A.token, body: { since: 0, changes: [e1] } })).body;
  assert.equal(r.accepted, 1); assert.ok(r.partnerJoined);
  const bs = (await api("/v1/sync", { method: "POST", token: B.token, body: { since: 0, changes: [e2] } })).body;
  const ids = bs.changes.map(c => c.id).sort(); assert.deepEqual(ids, ["e1", "e2", "profile:a", "profile:b"]);
  const cursorA = r.cursor;
  r = (await api("/v1/sync", { method: "POST", token: A.token, body: { since: cursorA } })).body;
  assert.deepEqual(r.changes.map(c => c.id), ["e2"]); console.log("two-device sync ok");

  // last-write-wins + stale write ignored + delete tombstone
  await api("/v1/sync", { method: "POST", token: B.token, body: { since: 0, changes: [{ ...e1, u: t0 + 100, data: { ...e1.data, amt: 1300 } }] } });
  const stale = (await api("/v1/sync", { method: "POST", token: A.token, body: { since: 0, changes: [{ ...e1, u: t0 + 50, data: { ...e1.data, amt: 9999 } }] } })).body;
  assert.equal(stale.changes.find(c => c.id === "e1").data.amt, 1300); assert.equal(stale.accepted, 0);
  const del = (await api("/v1/sync", { method: "POST", token: A.token, body: { since: 0, changes: [{ id: "e2", kind: "exp", u: t0 + 200, del: true }] } })).body;
  assert.equal(del.changes.find(c => c.id === "e2").del, true); console.log("LWW + tombstone ok");
  // can't write the partner's profile; invalid rows rejected
  const bad = (await api("/v1/sync", { method: "POST", token: A.token, body: { since: 0, changes: [{ id: "profile:b", kind: "profile", u: t0 + 500, data: { name: "Hacked" } }, { id: "x", kind: "exp", u: 1, data: { amt: -5 } }] } })).body;
  assert.equal(bad.accepted, 0); assert.equal(bad.changes.find(c => c.id === "profile:b").data.name, "Meera"); console.log("validation ok");

  // agent
  const c1 = (await api("/v1/agent/chat", { method: "POST", token: A.token, body: { messages: [{ role: "user", content: "log dinner 1240 I paid" }] } })).body;
  assert.equal(c1.actions[0].type, "add_expense"); assert.equal(c1.actions[0].paid, "a");
  const c2 = (await api("/v1/agent/chat", { method: "POST", token: B.token, body: { messages: [{ role: "user", content: "how much on food?" }] } })).body;
  assert.match(c2.reply, /"count":1/); assert.match(c2.reply, /"total":1300/);
  const sys = seen.find(s => typeof s.system === "string" && s.messages.some(m => /how much/.test(String(m.content)))).system;
  assert.match(sys, /talking to Meera/); assert.doesNotMatch(sys, /"balance"/);
  assert.equal(seen.every(s => s.model === "claude-opus-5-5"), true);
  assert.equal(seen.some(s => "temperature" in s || JSON.stringify(s).includes("budget_tokens")), false);
  console.log("agent ok (tool loop, proposals, query tool, model=claude-opus-5-5)");
  assert.match(await (await fetch(base + "/delete")).text(), /Delete your Nest data/);
  const dres = await api("/v1/nest/delete", { method: "POST", token: A.token, body: {} }); assert.equal(dres.body.deleted, true);
  assert.equal((await api("/v1/sync", { method: "POST", token: B.token, body: { since: 0 } })).status, 401);
  assert.equal((await api("/v1/pair/peek?code=" + A.code)).status, 404);
  console.log("deletion ok (data gone, tokens revoked)");
  console.log("ALL PASSED");
} catch (e) { console.error("FAILED", e); console.error(log.slice(-2500)); process.exitCode = 1; }
finally { w.kill("SIGTERM"); srv.close(); setTimeout(() => process.exit(process.exitCode || 0), 500); }
