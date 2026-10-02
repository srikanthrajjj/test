// Shared, deterministic money maths. Members are absolute: 'a' (creator) and 'b' (joiner).
// exp.data = {amt, title, cat, paid:'a'|'b', sa: share% of 'a', ts, src}
// set.data = {from:'a'|'b', amt, ts}
const DAY = 864e5;
const r2 = n => Math.round(n * 100) / 100;
const dkey = ts => new Date(ts).toISOString().slice(0, 10);

export function load(rows) {
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

export function summary({ exp, prof }, now = Date.now()) {
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

export function query(data, q = {}) {
  const { exp, prof } = data; let l = exp;
  if (q.from) l = l.filter(e => dkey(e.ts) >= q.from);
  if (q.to) l = l.filter(e => dkey(e.ts) <= q.to);
  if (q.category) l = l.filter(e => e.cat === q.category);
  if (q.paid_by === "a" || q.paid_by === "b") l = l.filter(e => e.paid === q.paid_by);
  if (q.text) { const t = String(q.text).toLowerCase(); l = l.filter(e => e.title.toLowerCase().includes(t)); }
  l = [...l].sort((x, y) => y.ts - x.ts);
  return { count: l.length, total: r2(l.reduce((a, e) => a + e.amt, 0)), rows: l.slice(0, Math.min(+q.limit || 15, 40)).map(e => ({ id: e.id, date: dkey(e.ts), title: e.title, category: e.cat, amount: e.amt, paid_by: prof[e.paid].name, split_a_pct: undefined })) };
}
