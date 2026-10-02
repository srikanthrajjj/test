import Anthropic from "@anthropic-ai/sdk";
import { summary, query, load } from "./metrics.js";

export const CATEGORIES = ["food", "groc", "move", "shop", "home", "fun", "trip", "care", "subs", "gift", "other"];

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
    description: "Propose a new expense. The couple confirms it in the app before it is saved - never claim it is already saved.",
    input_schema: { type: "object", additionalProperties: false, required: ["amount", "title", "category", "paid_by", "split"], properties: {
      amount: { type: "number" }, title: { type: "string" }, category: { type: "string", enum: CATEGORIES },
      paid_by: { type: "string", enum: ["me", "partner"], description: "Relative to the person you are talking to" },
      split: { type: "string", enum: ["equal", "me_only", "partner_only"] }, date: { type: "string", description: "YYYY-MM-DD, default today" } } }
  },
  {
    name: "settle_up",
    description: "Propose recording a settlement payment that clears (or reduces) the current balance. The couple confirms in the app.",
    input_schema: { type: "object", additionalProperties: false, properties: { amount: { type: "number", description: "Omit to settle the full balance" } } }
  }
];

function client(env) {
  return new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, baseURL: env.ANTHROPIC_BASE_URL || undefined, maxRetries: 1 });
}
const model = env => env.MODEL || "claude-opus-5-5";
const text = msg => msg.content.filter(b => b.type === "text").map(b => b.text).join("\n").trim();

function persona(sum, me, partner) {
  return `You are Nest, the money agent for a couple, living inside their shared expense app. You are warm, upbeat and brief - like a thoughtful friend who is good with numbers.

You are talking to ${me} (their partner is ${partner}). "I/me/my" means ${me}. Currency symbol/code is in the data's context; amounts are plain numbers in the couple's currency.
Rules:
- Every number you state must come from the summary below or from query_expenses. Never guess or invent figures; if you can't know, say so.
- Balance rule: balance = what someone paid minus their fair share of each expense; settlements adjust it. The summary's "balance" is authoritative.
- To change data you may only call add_expense or settle_up. They are proposals the couple confirms in the app - never say something is already saved.
- Keep answers to 1-4 short sentences unless asked for detail. Prefer concrete observations and one useful suggestion. At most one emoji. No lecturing about spending habits, no financial-advice disclaimers.
- Be fair to both partners; never take sides.

Today's data summary (JSON):
${JSON.stringify(sum)}`;
}

export async function chat(env, rows, member, history) {
  const data = load(rows), sum = summary(data), me = data.prof[member].name, partner = data.prof[member === "a" ? "b" : "a"].name;
  const other = member === "a" ? "b" : "a";
  const messages = history.slice(-12).map(m => ({ role: m.role === "assistant" ? "assistant" : "user", content: String(m.content).slice(0, 2000) }));
  if (!messages.length || messages[messages.length - 1].role !== "user") throw Object.assign(new Error("last message must be from the user"), { status: 400 });
  const actions = [], c = client(env);
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
          const paid = a.paid_by === "partner" ? other : member;
          const sa = a.split === "equal" ? 50 : (a.split === "me_only") === (member === "a") ? 100 : 0;
          actions.push({ type: "add_expense", amount: Math.round(a.amount * 100) / 100, title: String(a.title || "Expense").slice(0, 40), category: CATEGORIES.includes(a.category) ? a.category : "other", paid, sa, date: /^\d{4}-\d{2}-\d{2}$/.test(a.date || "") ? a.date : null });
          out = { status: "proposed", note: "Shown to the couple as a confirm card." };
        }
      } else if (b.name === "settle_up") {
        actions.push({ type: "settle_up", amount: b.input && b.input.amount > 0 ? Math.round(b.input.amount * 100) / 100 : null });
        out = { status: "proposed", note: "Shown to the couple as a confirm card." };
      } else out = { error: "unknown tool" };
      results.push({ type: "tool_result", tool_use_id: b.id, content: JSON.stringify(out) });
    }
    messages.push({ role: "user", content: results });
  }
  return { reply: "That took more steps than expected - could you try asking in a simpler way?", actions };
}

const DIGEST_SCHEMA = {
  type: "object", additionalProperties: false, required: ["headline", "bullets", "nudge"],
  properties: {
    headline: { type: "string" },
    bullets: { type: "array", items: { type: "object", additionalProperties: false, required: ["emoji", "text"], properties: { emoji: { type: "string" }, text: { type: "string" } } } },
    nudge: { type: "object", additionalProperties: false, required: ["text", "action"], properties: { text: { type: "string" }, action: { type: "string", enum: ["settle", "none"] } } }
  }
};

export async function digest(env, rows) {
  const data = load(rows), sum = summary(data);
  if (!data.exp.length) return { headline: "Your Nest is ready", bullets: [{ emoji: "🌱", text: "Add a few expenses and I'll start spotting patterns for you both." }], nudge: { text: "Tap + to log your first one.", action: "none" } };
  const res = await client(env).messages.create({
    model: model(env), max_tokens: 1200,
    system: `You are Nest, a warm money agent for a couple. Write today's brief from the data. Rules: headline <= 8 words, specific and friendly (not generic). 2-3 bullets, each one sentence with one concrete figure from the data and one emoji. A nudge: one practical suggestion; action "settle" only if the balance is not settled and >= 500 in magnitude (else "none"). Use only numbers present in the data. Refer to people by name, be fair to both. No financial-advice disclaimers.\n\nData:\n${JSON.stringify(sum)}`,
    messages: [{ role: "user", content: "Write today's brief." }],
    output_config: { effort: env.DIGEST_EFFORT || "low", format: { type: "json_schema", schema: DIGEST_SCHEMA } }
  });
  if (res.stop_reason === "refusal") throw Object.assign(new Error("refused"), { status: 502 });
  try { return JSON.parse(text(res)); } catch { throw Object.assign(new Error("bad digest"), { status: 502 }); }
}
