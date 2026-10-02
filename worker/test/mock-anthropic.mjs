// Tiny stand-in for api.anthropic.com so the Worker's agent loop can be tested offline.
import http from "node:http";
export function startMock(port = 8799) {
  const seen = [];
  const srv = http.createServer((req, res) => {
    let b = ""; req.on("data", d => b += d); req.on("end", () => {
      const j = JSON.parse(b || "{}"); seen.push(j);
      const last = j.messages[j.messages.length - 1];
      const resultBlock = Array.isArray(last.content) && last.content.find(x => x.type === "tool_result");
      let content, stop = "end_turn";
      if (j.output_config && j.output_config.format) {
        content = [{ type: "text", text: JSON.stringify({ headline: "Dining is trending up", bullets: [{ emoji: "🍜", text: "You spent 1,240 on dining." }], nudge: { text: "Settle up soon.", action: "settle" } }) }];
      } else if (resultBlock) {
        content = [{ type: "text", text: "Done - check the card to confirm. Result: " + String(resultBlock.content).slice(0, 80) }];
      } else if (/dinner/i.test(String(last.content))) {
        content = [{ type: "tool_use", id: "tu_1", name: "add_expense", input: { amount: 1240, title: "Dinner at Toit", category: "food", paid_by: "me", split: "equal" } }]; stop = "tool_use";
      } else if (/how much/i.test(String(last.content))) {
        content = [{ type: "tool_use", id: "tu_2", name: "query_expenses", input: { category: "food" } }]; stop = "tool_use";
      } else content = [{ type: "text", text: "Hello from the mock agent." }];
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ id: "msg_x", type: "message", role: "assistant", model: j.model, content, stop_reason: stop, stop_sequence: null, usage: { input_tokens: 10, output_tokens: 10 } }));
    });
  });
  return new Promise(r => srv.listen(port, "127.0.0.1", () => r({ srv, seen })));
}
