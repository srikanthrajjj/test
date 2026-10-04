// Generated from worker/src (metrics.js + agent.js) + router. See supabase/README.md
// Shared, deterministic money maths. Members are absolute: 'a' (creator) and 'b' (joiner).
// exp.data = {amt, title, cat, paid:'a'|'b', sa: share% of 'a', ts, src}
// set.data = {from:'a'|'b', amt, ts}
const DAY = 864e5;
const r2 = n => Math.round(n * 100) / 100;
const dkey = ts => new Date(ts).toISOString().slice(0, 10);

function load(rows) {
  const exp = [], set = [], prof = { a: { name: "Partner A", color: "#5B5BD6" }, b: { name: "Partner B", color: "#F0508B" } };
  for (const r of rows) {
    if (r.del) continue;
    let d; try { d = JSON.parse(r.data); } catch { continue; }
    if (r.kind === "exp") exp.push({ id: r.id, ...d });
    else if (r.kind === "set") set.push({ id: r.id, ...d });
    else if (r.kind === "profile") prof[r.id.split(":")[1]] = d;
  }
  return { exp, set, prof };
}

function agg(list, key) {
  const m = {};
  for (const e of list) { const k = key(e); const o = (m[k] = m[k] || { total: 0, n: 0 }); o.total = r2(o.total + e.amt); o.n++; }
  return Object.entries(m).sort((x, y) => y[1].total - x[1].total).map(([k, v]) => ({ k, ...v }));
}

function summary({ exp: all, prof }, now = Date.now()) {
  const exp = all.filter(e => e.cat !== "xfer"); // transfers/investments are not spending
  const d30 = exp.filter(e => e.ts > now - 30 * DAY), d90 = exp.filter(e => e.ts > now - 90 * DAY);
  const mk = new Date(now).toISOString().slice(0, 7), pm = new Date(Date.UTC(new Date(now).getUTCFullYear(), new Date(now).getUTCMonth() - 1, 1)).toISOString().slice(0, 7);
  const month = ym => exp.filter(e => dkey(e.ts).startsWith(ym));
  const sum = l => r2(l.reduce((a, e) => a + e.amt, 0));
  const person = id => {
    const mine = d30.filter(e => e.paid === id), cats = {}; for (const e of mine) cats[e.cat] = r2((cats[e.cat] || 0) + e.amt);
    return { name: prof[id].name, spent_30d: sum(mine), by_category_30d: cats };
  };
  const merchants = agg(d90, e => e.title.toLowerCase()).slice(0, 8).map(m => ({ merchant: m.k, total: m.total, times: m.n }));
  const recurring = agg(d90, e => e.title.toLowerCase()).filter(m => m.n >= 3).slice(0, 6).map(m => ({ merchant: m.k, times: m.n, avg: r2(m.total / m.n) }));
  const daily = []; for (let i = 13; i >= 0; i--) { const k = dkey(now - i * DAY); daily.push({ day: k, spent: sum(exp.filter(e => dkey(e.ts) === k)) }); }
    return {
    today: dkey(now),
    people: { a: prof.a.name, b: prof.b.name },
    combined: {
      last_30d_total: sum(d30), this_month: sum(month(mk)), last_month: sum(month(pm)),
      by_category_30d: agg(d30, e => e.cat).map(c => ({ category: c.k, total: c.total, count: c.n })),
      top_merchants_90d: merchants, recurring_90d: recurring, daily_14d: daily,
      biggest_30d: [...d30].sort((x, y) => y.amt - x.amt).slice(0, 3).map(e => ({ title: e.title, amount: e.amt, date: dkey(e.ts), paid_by: prof[e.paid].name }))
    },
    individual: { a: person("a"), b: person("b") },
    totals: { expenses: exp.length }
  };
}

function query(data, q = {}) {
  const { exp, prof } = data; let l = q.category === "xfer" ? exp : exp.filter(e => e.cat !== "xfer");
  if (q.from) l = l.filter(e => dkey(e.ts) >= q.from);
  if (q.to) l = l.filter(e => dkey(e.ts) <= q.to);
  if (q.category) l = l.filter(e => e.cat === q.category);
  if (q.paid_by === "a" || q.paid_by === "b") l = l.filter(e => e.paid === q.paid_by);
  if (q.text) { const t = String(q.text).toLowerCase(); l = l.filter(e => e.title.toLowerCase().includes(t)); }
  l = [...l].sort((x, y) => y.ts - x.ts);
  return { count: l.length, total: r2(l.reduce((a, e) => a + e.amt, 0)), rows: l.slice(0, Math.min(+q.limit || 15, 40)).map(e => ({ id: e.id, date: dkey(e.ts), title: e.title, category: e.cat, amount: e.amt, paid_by: prof[e.paid].name, split_a_pct: undefined })) };
}


const CATEGORIES = ["food", "groc", "move", "shop", "home", "fun", "trip", "care", "subs", "gift", "other"];

const TOOLS = [
  {
    name: "query_expenses",
    description: "Look up the couple's recorded expenses. Use it for any specific question the summary can't answer exactly (a merchant, a date range, a category, who paid). Returns count, total and up to `limit` newest rows.",
    input_schema: { type: "object", properties: {
      from: { type: "string", description: "Inclusive start date YYYY-MM-DD" }, to: { type: "string", description: "Inclusive end date YYYY-MM-DD" },
      category: { type: "string", enum: CATEGORIES }, paid_by: { type: "string", enum: ["a", "b"], description: "a/b member id as in the summary people map" },
      text: { type: "string", description: "Case-insensitive substring of the merchant/title" }, limit: { type: "integer" } } }
  },
  {
    name: "add_expense",
    description: "Propose a new expense. The couple confirms it in the app before it is saved - never claim it is already saved. Expenses are just records of who spent what; there is no splitting or owing.",
    input_schema: { type: "object", additionalProperties: false, required: ["amount", "title", "category", "paid_by"], properties: {
      amount: { type: "number" }, title: { type: "string" }, category: { type: "string", enum: CATEGORIES },
      paid_by: { type: "string", enum: ["me", "partner"], description: "Relative to the person you are talking to" },
      date: { type: "string", description: "YYYY-MM-DD, default today" } } }
  },
];

async function client(env) {
  const { default: Anthropic } = await import("npm:@anthropic-ai/sdk");
  return new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, baseURL: env.ANTHROPIC_BASE_URL || undefined, maxRetries: 1 });
}
const model = env => env.MODEL || "claude-opus-5-5";
const text = msg => msg.content.filter(b => b.type === "text").map(b => b.text).join("\n").trim();

function persona(sum, me, partner) {
  return `You are Nest, the money agent for a couple, living inside their shared expense app. You are warm, upbeat and brief - like a thoughtful friend who is good with numbers.

You are talking to ${me} (their partner is ${partner}). "I/me/my" means ${me}. Currency symbol/code is in the data's context; amounts are plain numbers in the couple's currency.
Rules:
- Every number you state must come from the summary below or from query_expenses. Never guess or invent figures; if you can't know, say so.
- This is a simple shared tracker for two people: it records who spent what. There is no splitting, owing or settling - never mention balances or who owes whom.
- To change data you may only call add_expense. It is a proposal the couple confirms in the app - never say something is already saved.
- Keep answers to 1-4 short sentences unless asked for detail. Prefer concrete observations and one useful suggestion. At most one emoji. No lecturing about spending habits, no financial-advice disclaimers.
- Be fair to both partners; never take sides.

Today's data summary (JSON):
${JSON.stringify(sum)}`;
}

async function chat(env, rows, member, history) {
  const data = load(rows), sum = summary(data), me = data.prof[member].name, partner = data.prof[member === "a" ? "b" : "a"].name;
  const other = member === "a" ? "b" : "a";
  const messages = history.slice(-12).map(m => ({ role: m.role === "assistant" ? "assistant" : "user", content: String(m.content).slice(0, 2000) }));
  if (!messages.length || messages[messages.length - 1].role !== "user") throw Object.assign(new Error("last message must be from the user"), { status: 400 });
  const actions = [], c = await client(env);
  for (let i = 0; i < 6; i++) {
    const res = await c.messages.create({
      model: model(env), max_tokens: 2000, system: persona(sum, me, partner), tools: TOOLS, messages,
      output_config: { effort: env.CHAT_EFFORT || "medium" }
    });
    if (res.stop_reason === "refusal") return { reply: "I can't help with that one - but I'm happy to dig into your spending.", actions };
    if (res.stop_reason !== "tool_use") return { reply: text(res) || "Done.", actions };
    messages.push({ role: "assistant", content: res.content });
    const results = [];
    for (const b of res.content.filter(x => x.type === "tool_use")) {
      let out;
      if (b.name === "query_expenses") out = query(data, b.input || {});
      else if (b.name === "add_expense") {
        const a = b.input || {};
        if (!(a.amount > 0)) out = { error: "amount must be > 0" };
        else {
          actions.push({ type: "add_expense", amount: Math.round(a.amount * 100) / 100, title: String(a.title || "Expense").slice(0, 40), category: CATEGORIES.includes(a.category) ? a.category : "other", paid: a.paid_by === "partner" ? other : member, date: /^\d{4}-\d{2}-\d{2}$/.test(a.date || "") ? a.date : null });
          out = { status: "proposed", note: "Shown to the couple as a confirm card." };
        }
      } else out = { error: "unknown tool" };
      results.push({ type: "tool_result", tool_use_id: b.id, content: JSON.stringify(out) });
    }
    messages.push({ role: "user", content: results });
  }
  return { reply: "That took more steps than expected - could you try asking in a simpler way?", actions };
}



// ================= Nest sync API (Supabase Edge Function) =================
const SB = Deno.env.get("SUPABASE_URL") || "";
const KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
const ENV = { ANTHROPIC_API_KEY: Deno.env.get("ANTHROPIC_API_KEY") || "", MODEL: Deno.env.get("MODEL") || "claude-opus-5-5", ANTHROPIC_BASE_URL: Deno.env.get("ANTHROPIC_BASE_URL") || "", CHAT_EFFORT: Deno.env.get("CHAT_EFFORT") || "medium" };
const CORS = { "access-control-allow-origin": "*", "access-control-allow-headers": "authorization, content-type, apikey, x-client-info", "access-control-allow-methods": "GET, POST, OPTIONS", "access-control-max-age": "86400" };
const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json", ...CORS } });
const err = (status, message) => json({ error: message }, status);
const plain = (t) => new Response(t, { headers: { "content-type": "text/plain; charset=utf-8", ...CORS } });

async function rpc(name, args) {
  const r = await fetch(`${SB}/rest/v1/rpc/${name}`, { method: "POST", headers: { apikey: KEY, authorization: "Bearer " + KEY, "content-type": "application/json" }, body: JSON.stringify(args) });
  const t = await r.text();
  if (!r.ok) { console.error("rpc", name, r.status, t.slice(0, 300)); throw Object.assign(new Error("database error"), { status: 500 }); }
  return t ? JSON.parse(t) : null;
}
const b64u = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const rand = (n) => b64u(crypto.getRandomValues(new Uint8Array(n)));
const sha = async (s) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].map((x) => x.toString(16).padStart(2, "0")).join("");
const ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const makeCode = () => Array.from(crypto.getRandomValues(new Uint8Array(6)), (x) => ALPHA[x % ALPHA.length]).join("");
const today = () => new Date().toISOString().slice(0, 10);
const KINDS = new Set(["exp", "set", "profile"]);
const cleanProfile = (p) => ({ name: String((p && p.name) || "").trim().slice(0, 18) || "Partner", color: /^#[0-9a-fA-F]{6}$/.test(p && p.color) ? p.color : "#5B5BD6", cur: /^[A-Z]{3}$/.test(p && p.cur) ? p.cur : "INR" });

async function body(req, max = 256 * 1024) {
  const t = await req.text();
  if (t.length > max) throw Object.assign(new Error("payload too large"), { status: 413 });
  try { return JSON.parse(t || "{}"); } catch { throw Object.assign(new Error("invalid JSON"), { status: 400 }); }
}
async function auth(req) {
  const m = /^Bearer (.+)$/.exec(req.headers.get("authorization") || ""); if (!m) return null;
  return await rpc("nest_auth", { p_hash: await sha(m[1]) });
}
function validChange(ch, member) {
  if (!ch || typeof ch.id !== "string" || ch.id.length > 64 || !KINDS.has(ch.kind) || !Number.isFinite(ch.u)) return false;
  if (ch.kind === "profile" && ch.id !== "profile:" + member) return false;
  if (ch.del) return true;
  const d = ch.data; if (!d || typeof d !== "object" || JSON.stringify(d).length > 2048) return false;
  if (ch.kind === "exp") return d.amt > 0 && d.amt < 1e8 && typeof d.title === "string" && ["a", "b"].includes(d.paid) && d.sa >= 0 && d.sa <= 100 && Number.isFinite(d.ts) && (d.note === undefined || (typeof d.note === "string" && d.note.length <= 160));
  if (ch.kind === "set") return d.amt > 0 && ["a", "b"].includes(d.from) && Number.isFinite(d.ts);
  return typeof d.name === "string" && (d.cur === undefined || /^[A-Z]{3}$/.test(d.cur));
}
const agentReady = () => (ENV.ANTHROPIC_API_KEY ? null : err(503, "The AI agent isn't switched on yet - add ANTHROPIC_API_KEY to the function secrets."));

async function handle(req) {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  const url = new URL(req.url);
  const path = url.pathname.replace(/^\/(?:functions\/v1\/)?nest/, "").replace(/\/+$/, "");
  try {
    if (path === "" || path === "/v1/health") return json({ ok: true, agent: !!ENV.ANTHROPIC_API_KEY });
    if (req.method === "GET" && path === "/v1/pair/peek") {
      const code = (url.searchParams.get("code") || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
      const r = code ? await rpc("nest_peek", { p_code: code }) : { error: "not_found" };
      return r.error ? err(404, "That code doesn't match any Nest.") : json(r);
    }
    if (req.method === "POST" && path === "/v1/pair/create") {
      const b = await body(req), id = rand(9), token = rand(32), prof = cleanProfile(b);
      for (let i = 0; i < 6; i++) {
        const code = makeCode();
        const r = await rpc("nest_create", { p_id: id, p_code: code, p_hash: await sha(token), p_prof: prof, p_now: Date.now() });
        if (!r.error) return json({ couple: id, member: "a", token, code });
      }
      return err(500, "Couldn't create a Nest, try again.");
    }
    if (req.method === "POST" && path === "/v1/pair/join") {
      const b = await body(req), code = String(b.code || "").toUpperCase().replace(/[^A-Z0-9]/g, ""), token = rand(32);
      const r = await rpc("nest_join", { p_code: code, p_hash: await sha(token), p_prof: cleanProfile(b), p_now: Date.now() });
      if (r.error === "not_found") return err(404, "That code doesn't match any Nest.");
      if (r.error === "full") return err(409, "This Nest already has two people.");
      if (r.error === "expired") return err(410, "That code has expired - ask for a new one.");
      return json({ couple: r.couple, member: r.member, token });
    }
    if (req.method === "GET" && path === "/privacy") return plain("Nest privacy: your expenses, names and settlements are stored on a private database only to sync you and your partner. Bank SMS are read on your phone and never uploaded. Delete everything in the app: You > Sync > Disconnect > Delete our Nest data.");
    const who = await auth(req);
    if (!who) return err(401, "Not signed in to a Nest.");
    if (req.method === "POST" && path === "/v1/sync") {
      const b = await body(req), since = Math.max(0, +b.since || 0), byId = new Map();
      (Array.isArray(b.changes) ? b.changes : []).slice(0, 500).filter((c) => validChange(c, who.member)).forEach((c) => { const o = byId.get(c.id); if (!o || o.u < c.u) byId.set(c.id, c); });
      return json(await rpc("nest_sync", { p_couple: who.couple, p_member: who.member, p_since: since, p_changes: [...byId.values()].map((c) => ({ id: c.id, kind: c.kind, u: c.u, del: !!c.del, data: c.del ? null : c.data })) }));
    }
    if (req.method === "GET" && path === "/v1/me") return json({ couple: who.couple, member: who.member, agent: !!ENV.ANTHROPIC_API_KEY });
    if (req.method === "POST" && path === "/v1/nest/delete") { await rpc("nest_delete", { p_couple: who.couple }); return json({ deleted: true }); }
    if (req.method === "POST" && path === "/v1/agent/chat") {
      const off = agentReady(); if (off) return off;
      const b = await body(req, 32 * 1024);
      if (!Array.isArray(b.messages) || !b.messages.length) return err(400, "messages required");
      if (!(await rpc("nest_meter", { p_couple: who.couple, p_day: today(), p_cap: 80 }))) return err(429, "Daily agent limit reached - try again tomorrow.");
      const rows = (await rpc("nest_rows", { p_couple: who.couple })).map((r) => ({ id: r.id, kind: r.kind, data: JSON.stringify(r.data), del: r.del }));
      return json(await chat(ENV, rows, who.member, b.messages));
    }
    return err(404, "Not found");
  } catch (e) {
    const status = e.status && e.status >= 400 && e.status < 600 ? e.status : 500;
    console.error("error", path, e && e.message);
    return err(status, status >= 500 ? "Something went wrong on the server." : e.message);
  }
}
Deno.serve(handle);
