// AI features end-to-end: needs the function running locally with a mock LLM (scratchpad aitest/serve.mjs on :8797)
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const URL = 'file:///tmp/claude-0/-home-user-test/efa09150-834c-5941-8bea-cc0b76604455/scratchpad/www-ai/index.html';
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch({ args: ['--allow-file-access-from-files', '--disable-web-security'] });
  const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(() => { window.__fast = 1; });
  await p.goto(URL); await p.waitForTimeout(300);
  await p.click('[data-act=ob-next]'); await p.fill('#ob-me', 'Sri'); await p.click('[data-act=ob-names]'); await p.click('[data-act=ob-sample]');
  await p.waitForSelector('[data-act=ob-add]', { timeout: 9000 }); await p.click('[data-act=ob-add]'); await p.waitForTimeout(2800); await p.evaluate(() => closeSheet());
  await p.evaluate(() => aiCheck(true)); await p.waitForTimeout(300);
  ok(await p.evaluate(() => aiOk()), 'app detects the AI is switched on (solo user, no sync needed)');
  const ctx = await p.evaluate(() => { const c = aiContext(); return { keys: Object.keys(c), raw: JSON.stringify(c).includes('debited'), recent: c.ledger.length, mode: c.mode, line: c.ledger[0] }; });
  ok(ctx.recent > 5 && ctx.mode === 'solo' && ctx.keys.includes('this_month') && ctx.keys.includes('months') && /^[^|]+\|\d{4}-\d\d-\d\d\|[\d.-]+\|/.test(ctx.line), 'chat context has month totals, 6 months and the full payment ledger (' + ctx.line + ')');
  ok(!ctx.raw, 'context never includes raw SMS text');
  await p.evaluate(() => { openChat(); }); await p.waitForTimeout(300); await p.evaluate(() => sendChat('How should I save money this month?')); await p.waitForTimeout(800);
  ok(await p.evaluate(() => CH.msgs.some(m => /CTX_OK/.test(m.content))), 'chat answer is built on the user\'s data');
  await p.evaluate(() => sendChat('log 500 groceries')); await p.waitForTimeout(800);
  const n0 = await p.evaluate(() => S.exp.length);
  await p.evaluate(() => { const mi = CH.msgs.length - 1; doAction(mi, 0, true); }); await p.waitForTimeout(300);
  ok(await p.evaluate(n => S.exp.length === n + 1 && S.exp.some(e => e.amt === 500 && e.cat === 'groc'), n0), 'asking to log an expense gives a confirm card that adds it');
  await p.evaluate(() => closeChat());
  await p.evaluate(() => { const t = Date.now(); S.exp.push({ id: 'z1', amt: 230, title: 'Zxqmart', cat: 'other', paid: 'me', sm: 50, ts: t - 3e6, src: 'sms', mk: 'zxqmart@okaxis', u: t }); S.exp.push({ id: 'z2', amt: 120, title: 'Zxqmart', cat: 'other', paid: 'me', sm: 50, ts: t - 9e6, src: 'sms', mk: 'zxqmart@okaxis', u: t }); });
  await p.evaluate(() => smartNames()); await p.waitForTimeout(800);
  ok(await p.evaluate(() => ['z1', 'z2'].every(id => { const e = S.exp.find(x => x.id === id); return e.title === 'ZXQ Mart' && e.cat === 'groc'; })), 'smart names turns an unknown merchant code into a real name + category');
  ok(await p.evaluate(() => S.names['zxqmart@okaxis'] && parseSms({ id: 77, a: 'VM-X', b: 'Rs.60.00 debited from A/c XX1 to VPA zxqmart@okaxis UPI Ref 1', d: Date.now() }).title === 'ZXQ Mart'), 'future payments to that merchant get the clean name');
  await p.evaluate(() => { S.insight = null; return dailyInsight(); }); await p.waitForTimeout(800); await p.evaluate(() => show('home')); await p.waitForTimeout(300);
  ok(await p.evaluate(() => { if (!S.insight) return false; const h = document.querySelector('#main .hin2'); if (h && h.innerText.includes(S.insight.t)) return true; openChanged(); const w = document.querySelector('#wc .wins'); return !!w && w.textContent.includes(S.insight.t); }), 'the daily AI insight appears on Home or in What changed');
  // agent search card: exact totals computed on the phone from the agent's filter
  await p.evaluate(() => { closeChanged(); CH.msgs = []; openChat(); return sendChat('where did my money go'); }); await p.waitForTimeout(900);
  ok(await p.evaluate(() => { const m = CH.msgs[CH.msgs.length - 1]; const n = S.exp.filter(e => /amazon/i.test(e.title)).length; return m.ai && m.card && new RegExp(n + ' payment').test(m.card.sub) && m.card.acts[0][1] === 'chat-list'; }), 'any question goes to the agent; its search becomes a card with exact totals + View transactions');
  // agent proposes a re-categorisation; one tap applies it
  await p.evaluate(() => { const t = Date.now(); S.exp.unshift({ id: 'zr', amt: 99, title: 'Zxq Snacks', cat: 'other', paid: 'me', sm: 50, ts: t - 1e5, src: 'manual', u: t }); return sendChat('move zxq to groceries'); }); await p.waitForFunction(() => !CH.busy); await p.waitForTimeout(200);
  ok(await p.evaluate(() => !!document.querySelector('[data-act=recat-yes]')), 'a "move X to Y" request shows an Apply card');
  await p.waitForSelector('[data-act=recat-yes]'); await p.evaluate(() => document.querySelector('[data-act=recat-yes]').click()); await p.waitForTimeout(300);
  ok(await p.evaluate(() => S.exp.find(e => e.id === 'zr').cat === 'groc'), 'Apply changes the category');
  await p.evaluate(() => closeChat());
  // smart sort: unclear payments read from their (masked) bank text
  const sorted = await p.evaluate(async () => { const t = Date.now(); S.exp.push({ id: 's1', amt: 40, title: 'Local store (QR)', cat: 'other', paid: 'me', sm: 50, ts: t - 2e5, src: 'sms', u: t, raw: 'Rs.40.00 debited from a/c XX4821 to VPA paytmqr2810050501@paytm SHIVA JUICE CENTRE. Avl bal Rs 52,301.22' }, { id: 's2', amt: 5000, title: 'UPI payment', cat: 'other', paid: 'me', sm: 50, ts: t - 3e5, src: 'sms', u: t, raw: 'INR 5000.00 debited A/c no. XX4821 UPI/P2M/628191919191/Zerodha Broking Ltd' }); await smartSort(true); return ['s1', 's2'].map(id => { const e = S.exp.find(x => x.id === id); return e.title + ':' + e.cat; }); });
  ok(sorted.join() === 'Shiva Juice Centre:food,Zerodha:xfer', 'smart sort names + categorises unclear payments from their texts: ' + sorted.join());
  ok(await p.evaluate(() => { const m = maskText('Rs.40.00 debited from a/c XX4821 to VPA paytmqr2810050501@paytm on 12-10-26 Ref 628191919191. Avl bal Rs 52,301.22'); return /Rs\.40\.00/.test(m) && !/4821|2810050501|628191919191|52,301/.test(m); }), 'account, reference and balance numbers are masked before anything is sent');
  await p.evaluate(() => { S.smartOff = true; S.insight = null; S.askedMk = {}; }); await p.evaluate(() => dailyInsight()); ok(await p.evaluate(() => !S.insight), 'turning smart features off stops AI calls');
  await p.evaluate(() => { S.aiOn = false; window.aiCheck = async () => {}; }); await p.evaluate(() => { CH.msgs = []; openChat(); return sendChat('Any tips to save?'); }); await p.waitForTimeout(700);
  ok(await p.evaluate(() => CH.msgs.some(m => m.role === 'assistant' && /Smart insights/.test(m.content) && (m.chips || []).length)), 'without AI, chat still answers on-device');
  ok(!errs.length, 'no JS errors ' + errs.join(';'));
  res.forEach(r => console.log(r[0], ' ', r[1])); console.log(res.filter(r => r[0] === 'PASS').length + '/' + res.length); await b.close();
})().catch(e => { console.error('CRASH', e); process.exit(1); });
