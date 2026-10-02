import { chat } from "./agent.js";
import { landing, privacy, deletion } from "./pages.js";

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, content-type",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-max-age": "86400"
};
const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json", ...CORS } });
const err = (status, message) => json({ error: message }, status);
const html = (body, status = 200) => new Response(body, { status, headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });

const b64u = buf => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const rand = n => b64u(crypto.getRandomValues(new Uint8Array(n)));
const sha = async s => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].map(x => x.toString(16).padStart(2, "0")).join("");
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const makeCode = () => Array.from(crypto.getRandomValues(new Uint8Array(6)), x => CODE_ALPHABET[x % CODE_ALPHABET.length]).join("");
const today = () => new Date().toISOString().slice(0, 10);
const KINDS = new Set(["exp", "set", "profile"]);

async function auth(req, env) {
  const m = /^Bearer (.+)$/.exec(req.headers.get("authorization") || "");
  if (!m) return null;
  return await env.DB.prepare("SELECT couple, member FROM members WHERE token_hash=?").bind(await sha(m[1])).first();
}

async function body(req, max = 256 * 1024) {
  const t = await req.text();
  if (t.length > max) throw Object.assign(new Error("payload too large"), { status: 413 });
  try { return JSON.parse(t || "{}"); } catch { throw Object.assign(new Error("invalid JSON"), { status: 400 }); }
}
const cleanProfile = p => ({ name: String((p && p.name) || "").trim().slice(0, 18) || "Partner", color: /^#[0-9a-fA-F]{6}$/.test(p && p.color) ? p.color : "#5B5BD6", cur: /^[A-Z]{3}$/.test(p && p.cur) ? p.cur : "INR" });

async function createPair(req, env) {
  const b = await body(req), id = rand(9), token = rand(32);
  let code = makeCode();
  for (let i = 0; i < 5; i++) { if (!(await env.DB.prepare("SELECT 1 FROM couples WHERE code=?").bind(code).first())) break; code = makeCode(); }
  const now = Date.now(), prof = cleanProfile(b);
  await env.DB.batch([
    env.DB.prepare("INSERT INTO couples (id, code, seq, created) VALUES (?,?,1,?)").bind(id, code, now),
    env.DB.prepare("INSERT INTO members (token_hash, couple, member, created) VALUES (?,?,?,?)").bind(await sha(token), id, "a", now),
    env.DB.prepare("INSERT INTO rec (couple,id,kind,data,u,del,seq) VALUES (?,?,?,?,?,0,1)").bind(id, "profile:a", "profile", JSON.stringify(prof), now)
  ]);
  return json({ couple: id, member: "a", token, code });
}

async function joinPair(req, env) {
  const b = await body(req), code = String(b.code || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  const c = await env.DB.prepare("SELECT id, created FROM couples WHERE code=?").bind(code).first();
  if (!c) return err(404, "That code doesn't match any Nest.");
  if (Date.now() - c.created > 14 * 864e5 && !(await env.DB.prepare("SELECT 1 FROM members WHERE couple=? AND member='b'").bind(c.id).first())) return err(410, "That code has expired - ask for a new one.");
  if (await env.DB.prepare("SELECT 1 FROM members WHERE couple=? AND member='b'").bind(c.id).first()) return err(409, "This Nest already has two people.");
  const token = rand(32), now = Date.now(), prof = cleanProfile(b);
  const seq = (await env.DB.prepare("UPDATE couples SET seq=seq+1 WHERE id=? RETURNING seq").bind(c.id).first()).seq;
  await env.DB.batch([
    env.DB.prepare("INSERT INTO members (token_hash, couple, member, created) VALUES (?,?,?,?)").bind(await sha(token), c.id, "b", now),
    env.DB.prepare("INSERT INTO rec (couple,id,kind,data,u,del,seq) VALUES (?,?,?,?,?,0,?) ON CONFLICT(couple,id) DO UPDATE SET data=excluded.data,u=excluded.u,seq=excluded.seq").bind(c.id, "profile:b", "profile", JSON.stringify(prof), now, seq)
  ]);
  return json({ couple: c.id, member: "b", token });
}

function validChange(ch, member) {
  if (!ch || typeof ch.id !== "string" || ch.id.length > 64 || !KINDS.has(ch.kind) || !Number.isFinite(ch.u)) return false;
  if (ch.kind === "profile" && ch.id !== "profile:" + member) return false;      // you may only edit your own profile
  if (ch.del) return true;
  const d = ch.data; if (!d || typeof d !== "object" || JSON.stringify(d).length > 2048) return false;
  if (ch.kind === "exp") return d.amt > 0 && d.amt < 1e8 && typeof d.title === "string" && ["a", "b"].includes(d.paid) && d.sa >= 0 && d.sa <= 100 && Number.isFinite(d.ts) && (d.note === undefined || (typeof d.note === "string" && d.note.length <= 160));
  if (ch.kind === "set") return d.amt > 0 && ["a", "b"].includes(d.from) && Number.isFinite(d.ts);
  return typeof d.name === "string" && (d.cur === undefined || /^[A-Z]{3}$/.test(d.cur));
}

async function sync(req, env, who) {
  const b = await body(req), since = Math.max(0, +b.since || 0);
  const incoming = (Array.isArray(b.changes) ? b.changes : []).slice(0, 500).filter(c => validChange(c, who.member));
  let accepted = 0;
  if (incoming.length) {
    const ids = incoming.map(c => c.id);
    const have = new Map();
    for (let i = 0; i < ids.length; i += 80) {
      const part = ids.slice(i, i + 80);
      const r = await env.DB.prepare(`SELECT id,u FROM rec WHERE couple=? AND id IN (${part.map(() => "?").join(",")})`).bind(who.couple, ...part).all();
      r.results.forEach(x => have.set(x.id, x.u));
    }
    const fresh = incoming.filter(c => !(have.has(c.id) && have.get(c.id) >= c.u));
    if (fresh.length) {
      const top = (await env.DB.prepare("UPDATE couples SET seq=seq+? WHERE id=? RETURNING seq").bind(fresh.length, who.couple).first()).seq;
      const stmts = fresh.map((c, i) => env.DB.prepare(
        "INSERT INTO rec (couple,id,kind,data,u,del,seq) VALUES (?,?,?,?,?,?,?) ON CONFLICT(couple,id) DO UPDATE SET data=excluded.data,u=excluded.u,del=excluded.del,seq=excluded.seq WHERE excluded.u > rec.u"
      ).bind(who.couple, c.id, c.kind, JSON.stringify(c.del ? {} : c.data), c.u, c.del ? 1 : 0, top - fresh.length + i + 1));
      for (let i = 0; i < stmts.length; i += 50) await env.DB.batch(stmts.slice(i, i + 50));
      accepted = fresh.length;
    }
  }
  const out = await env.DB.prepare("SELECT id,kind,data,u,del,seq FROM rec WHERE couple=? AND seq>? ORDER BY seq LIMIT 1000").bind(who.couple, since).all();
  const changes = out.results.map(r => ({ id: r.id, kind: r.kind, data: r.del ? null : JSON.parse(r.data), u: r.u, del: !!r.del, seq: r.seq }));
  const cur = (await env.DB.prepare("SELECT seq FROM couples WHERE id=?").bind(who.couple).first()).seq;
  const cursor = changes.length === 1000 ? changes[changes.length - 1].seq : cur;
  const partner = await env.DB.prepare("SELECT 1 FROM members WHERE couple=? AND member=?").bind(who.couple, who.member === "a" ? "b" : "a").first();
  return json({ cursor, changes, accepted, partnerJoined: !!partner, serverTime: Date.now() });
}

async function allRows(env, couple) {
  return (await env.DB.prepare("SELECT id,kind,data,del FROM rec WHERE couple=?").bind(couple).all()).results;
}

// Cap agent calls per couple per day to bound spend.
async function meter(env, couple, cap = 80) {
  const row = await env.DB.prepare("INSERT INTO usage (couple,day,n) VALUES (?,?,1) ON CONFLICT(couple,day) DO UPDATE SET n=n+1 RETURNING n").bind(couple, today()).first();
  return row.n <= cap;
}
const agentReady = env => env.ANTHROPIC_API_KEY ? null : err(503, "The agent isn't switched on yet - add ANTHROPIC_API_KEY to the Worker.");

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    const url = new URL(req.url), path = url.pathname.replace(/\/+$/, "");
    try {
      if (path === "" || path === "/v1/health") return json({ ok: true, agent: !!env.ANTHROPIC_API_KEY });
      if (req.method === "GET" && path.startsWith("/j/")) return html(await landing(env, url, path.slice(3)));
      if (req.method === "GET" && path === "/delete") return html(deletion(env));
      if (req.method === "GET" && path === "/privacy") return html(privacy(env));
      if (req.method === "GET" && path === "/.well-known/assetlinks.json") return json(env.ASSET_FINGERPRINT ? [{ relation: ["delegate_permission/common.handle_all_urls"], target: { namespace: "android_app", package_name: env.APP_PACKAGE || "com.nest.couples", sha256_cert_fingerprints: String(env.ASSET_FINGERPRINT).split(",") } }] : []);
      if (req.method === "GET" && path === "/v1/pair/peek") {
        const code = (url.searchParams.get("code") || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
        const c = code && await env.DB.prepare("SELECT id FROM couples WHERE code=?").bind(code).first();
        if (!c) return err(404, "That code doesn't match any Nest.");
        const p = await env.DB.prepare("SELECT data FROM rec WHERE couple=? AND id='profile:a'").bind(c.id).first();
        const joined = await env.DB.prepare("SELECT 1 FROM members WHERE couple=? AND member='b'").bind(c.id).first();
        const pd = p ? JSON.parse(p.data) : {}; return json({ inviter: pd.name || "Your partner", cur: pd.cur || "INR", color: pd.color, full: !!joined });
      }
      if (req.method === "POST" && path === "/v1/pair/create") return await createPair(req, env);
      if (req.method === "POST" && path === "/v1/pair/join") return await joinPair(req, env);
      const who = await auth(req, env);
      if (!who) return err(401, "Not signed in to a Nest.");
      if (req.method === "POST" && path === "/v1/sync") return await sync(req, env, who);
      if (req.method === "POST" && path === "/v1/nest/delete") {   // erase this couple's server-side data (either partner may do it)
        await env.DB.batch(["rec", "digests", "usage", "members"].map(t => env.DB.prepare(`DELETE FROM ${t} WHERE couple=?`).bind(who.couple)).concat([env.DB.prepare("DELETE FROM couples WHERE id=?").bind(who.couple)]));
        return json({ deleted: true });
      }
      if (req.method === "GET" && path === "/v1/me") {
        const c = await env.DB.prepare("SELECT code FROM couples WHERE id=?").bind(who.couple).first();
        const partner = await env.DB.prepare("SELECT 1 FROM members WHERE couple=? AND member=?").bind(who.couple, who.member === "a" ? "b" : "a").first();
        return json({ couple: who.couple, member: who.member, code: partner ? null : c.code, partnerJoined: !!partner, agent: !!env.ANTHROPIC_API_KEY });
      }
      if (req.method === "POST" && path === "/v1/agent/chat") {
        const off = agentReady(env); if (off) return off;
        const b = await body(req, 32 * 1024);
        if (!Array.isArray(b.messages) || !b.messages.length) return err(400, "messages required");
        if (!(await meter(env, who.couple))) return err(429, "Daily agent limit reached - try again tomorrow.");
        return json(await chat(env, await allRows(env, who.couple), who.member, b.messages));
      }
      return err(404, "Not found");
    } catch (e) {
      const status = e.status && e.status >= 400 && e.status < 600 ? (e.status === 401 || e.status === 403 ? 502 : e.status) : 500;
      console.error("error", path, e && e.message);
      return err(status, status >= 500 ? "Something went wrong on the server." : e.message);
    }
  }
};
