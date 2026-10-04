// Router test for the Edge Function with a tiny in-memory stand-in for the SQL functions (the SQL itself was verified in Postgres).
import fs from "node:fs"; import assert from "node:assert/strict";
import { startMock } from "../worker/test/mock-anthropic.mjs";
const { srv } = await startMock(8801);
const db = { couples: {}, members: {}, rec: {}, usage: {} };
const rpcs = {
  nest_auth: ({ p_hash }) => db.members[p_hash] ? { couple: db.members[p_hash].couple, member: db.members[p_hash].member } : null,
  nest_create: ({ p_id, p_code, p_hash, p_prof, p_now }) => { if (Object.values(db.couples).some(c => c.code === p_code)) return { error: "code_taken" }; db.couples[p_id] = { code: p_code, seq: 1, created: p_now }; db.members[p_hash] = { couple: p_id, member: "a" }; db.rec[p_id + "|profile:a"] = { couple: p_id, id: "profile:a", kind: "profile", data: p_prof, u: p_now, del: false, seq: 1 }; return { couple: p_id, member: "a", code: p_code }; },
  nest_peek: ({ p_code }) => { const e = Object.entries(db.couples).find(([, c]) => c.code === p_code); if (!e) return { error: "not_found" }; const p = db.rec[e[0] + "|profile:a"].data; return { inviter: p.name, cur: p.cur, color: p.color, full: false }; },
  nest_join: ({ p_code, p_hash, p_prof, p_now }) => { const e = Object.entries(db.couples).find(([, c]) => c.code === p_code); if (!e) return { error: "not_found" }; db.members[p_hash] = { couple: e[0], member: "b" }; e[1].seq++; db.rec[e[0] + "|profile:b"] = { couple: e[0], id: "profile:b", kind: "profile", data: p_prof, u: p_now, del: false, seq: e[1].seq }; e[1].code = null; return { couple: e[0], member: "b" }; },
  nest_sync: ({ p_couple, p_member, p_since, p_changes }) => { const c = db.couples[p_couple]; let acc = 0; for (const ch of p_changes) { const k = p_couple + "|" + ch.id, o = db.rec[k]; if (o && o.u >= ch.u) continue; c.seq++; db.rec[k] = { couple: p_couple, id: ch.id, kind: ch.kind, data: ch.del ? {} : ch.data, u: ch.u, del: !!ch.del, seq: c.seq }; acc++; } const chg = Object.values(db.rec).filter(r => r.couple === p_couple && r.seq > p_since).sort((a, b) => a.seq - b.seq).map(r => ({ id: r.id, kind: r.kind, data: r.del ? null : r.data, u: r.u, del: r.del, seq: r.seq })); return { cursor: c.seq, changes: chg, accepted: acc, partnerJoined: Object.values(db.members).some(m => m.couple === p_couple && m.member !== p_member), serverTime: Date.now() }; },
  nest_rows: ({ p_couple }) => Object.values(db.rec).filter(r => r.couple === p_couple).map(r => ({ id: r.id, kind: r.kind, data: r.data, del: r.del })),
  nest_meter: ({ p_couple }) => { db.usage[p_couple] = (db.usage[p_couple] || 0) + 1; return db.usage[p_couple] <= 80; },
  nest_delete: ({ p_couple }) => { delete db.couples[p_couple]; for (const k of Object.keys(db.members)) if (db.members[k].couple === p_couple) delete db.members[k]; for (const k of Object.keys(db.rec)) if (db.rec[k].couple === p_couple) delete db.rec[k]; }
};
let handler; const envs = { SUPABASE_URL: "http://sb.local", SUPABASE_SERVICE_ROLE_KEY: "k", ANTHROPIC_API_KEY: "test", ANTHROPIC_BASE_URL: "http://127.0.0.1:8801" };
globalThis.Deno = { serve: h => { handler = h; }, env: { get: k => envs[k] } };
const realFetch = globalThis.fetch;
globalThis.fetch = async (u, o) => { u = String(u); if (u.startsWith("http://sb.local/rest/v1/rpc/")) { const n = u.split("/").pop(); const out = rpcs[n](JSON.parse(o.body)); return new Response(out === undefined ? "" : JSON.stringify(out), { status: 200 }); } return realFetch(u, o); };
let src = fs.readFileSync(new URL("./functions/nest/index.ts", import.meta.url), "utf8").replace('await import("npm:@anthropic-ai/sdk")', 'await import("@anthropic-ai/sdk")');
fs.writeFileSync("/home/user/test/worker/_fn_test.mjs", src); await import("/home/user/test/worker/_fn_test.mjs");
const call = async (path, { method = "GET", token, body } = {}) => { const r = await handler(new Request("https://x.supabase.co/functions/v1/nest" + path, { method, headers: { "content-type": "application/json", ...(token ? { authorization: "Bearer " + token } : {}) }, body: body ? JSON.stringify(body) : undefined })); return { status: r.status, body: await r.json() }; };
const A = (await call("/v1/pair/create", { method: "POST", body: { name: "Arjun", color: "#5B5BD6", cur: "INR" } })).body; assert.equal(A.member, "a"); assert.match(A.code, /^[A-Z2-9]{6}$/);
assert.equal((await call("/v1/pair/peek?code=" + A.code)).body.inviter, "Arjun"); assert.equal((await call("/v1/pair/peek?code=ZZZZZZ")).status, 404);
const B = (await call("/v1/pair/join", { method: "POST", body: { code: A.code, name: "Meera", color: "#F0508B" } })).body; assert.equal(B.member, "b");
assert.equal((await call("/v1/sync", { method: "POST", body: {} })).status, 401);
const t = Date.now(), e = (id, amt, paid) => ({ id, kind: "exp", u: t, data: { amt, title: "Swiggy", cat: "food", paid, sa: 50, ts: t, src: "sms" } });
let r = (await call("/v1/sync", { method: "POST", token: A.token, body: { since: 0, changes: [e("e1", 450, "a"), { id: "x", kind: "exp", u: 1, data: { amt: -5 } }, { id: "profile:b", kind: "profile", u: t, data: { name: "Hack" } }] } })).body; assert.equal(r.accepted, 1);
r = (await call("/v1/sync", { method: "POST", token: B.token, body: { since: 0, changes: [e("e2", 120, "b")] } })).body; assert.deepEqual(r.changes.map(c => c.id).sort(), ["e1", "e2", "profile:a", "profile:b"]); assert.equal(r.changes.find(c => c.id === "profile:b").data.name, "Meera");
const c1 = (await call("/v1/agent/chat", { method: "POST", token: A.token, body: { messages: [{ role: "user", content: "log dinner 1240 I paid" }] } })).body; assert.equal(c1.actions[0].type, "add_expense"); assert.equal(c1.actions[0].paid, "a");
assert.equal((await call("/v1/nest/delete", { method: "POST", token: A.token, body: {} })).body.deleted, true); assert.equal((await call("/v1/sync", { method: "POST", token: B.token, body: {} })).status, 401);
assert.equal((await handler(new Request("https://x/functions/v1/nest/v1/sync", { method: "OPTIONS" }))).status, 204);
console.log("EDGE FUNCTION ROUTER TESTS PASSED"); fs.unlinkSync("/home/user/test/worker/_fn_test.mjs"); srv.close(); process.exit(0);
