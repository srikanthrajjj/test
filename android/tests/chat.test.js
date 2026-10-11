// Chat: rich on-device answers, follow-ups, review card for investment/person payments, quick add
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(() => { window.__fast = 1; });
  await p.goto('file://' + __dirname + '/../app/src/main/assets/www/index.html'); await p.waitForTimeout(300);
  await p.evaluate(() => {
    S.onboarded = true; S.me.name = 'Srikanth'; S.mode = 'solo'; document.querySelector('#ob').classList.remove('on');
    const m = monthStart(new Date()), pm = addMonths(m, -1), d = (base, k, h = 12) => base.getTime() + k * 864e5 + h * 36e5, add = (ts, amt, title, cat) => S.exp.push({ id: uid(), amt, title, cat, paid: 'me', sm: 50, ts, src: 'sms', u: Date.now() });
    [499, 520, 310, 640, 425, 488, 400].forEach((a, i) => add(Math.min(Date.now() - 6e5, d(m, i % 3)), a, 'Amazon', 'shop'));
    add(Math.min(Date.now() - 6e5, d(m, 1)), 1366, 'Zepto', 'groc'); add(Math.min(Date.now() - 6e5, d(m, 2)), 1366, 'Zepto', 'groc');
    add(Math.min(Date.now() - 6e5, d(m, 0, 9)), 10080, 'Dhan', 'other'); add(Math.min(Date.now() - 6e5, d(m, 1, 9)), 5000, 'Gunjan', 'other');
    add(Math.min(Date.now() - 6e5, d(m, 0, 15)), 640, 'Third Wave Coffee', 'food'); add(Math.min(Date.now() - 6e5, d(m, 1, 15)), 500, 'Starbucks', 'food');
    add(d(pm, 3), 900, 'Third Wave Coffee', 'food'); add(d(pm, 4), 2100, 'Amazon', 'shop'); add(d(pm, 5), 300, 'Zepto', 'groc');
    persist(); show('month'); openChat();
  });
  await p.evaluate(() => sendChat('Where did I spend most this month?')); await p.waitForTimeout(700);
  const a1 = await p.evaluate(() => { const m = CH.msgs[CH.msgs.length - 1]; return { head: m.card && m.card.head, bars: m.card && m.card.bars.length, period: m.card && m.card.period, review: m.review && m.review.head, acts: [...document.querySelectorAll('.ans .cacts button')].map(b => b.innerText.trim()) }; });
  ok(a1.head && a1.bars === 3 && /–/.test(a1.period), 'answer is a card with period, headline and 3 bars (' + a1.head + ')');
  ok(a1.acts.includes('View transactions') && a1.acts.some(t => /^Compare/.test(t)), 'card has View transactions + Compare buttons');
  ok(/Dhan/.test(a1.review || ''), 'flags Dhan as a possible investment: ' + a1.review);
  await p.evaluate(() => document.querySelector('[data-act=rv-xfer]').click()); await p.waitForTimeout(200);
  ok(await p.evaluate(() => S.exp.find(e => e.title === 'Dhan').cat === 'xfer'), '"Mark as investment" moves it out of spending');
  await p.evaluate(() => sendChat('Where did I spend most this month?')); await p.waitForTimeout(700);
  ok(await p.evaluate(() => /Gunjan/.test((CH.msgs[CH.msgs.length - 1].review || {}).head || '')), 'next, a large payment to a person asks "was this spending?"');
  await p.evaluate(() => sendChat('And last month?')); await p.waitForTimeout(700)
  const a2 = await p.evaluate(() => CH.msgs[CH.msgs.length - 1].card);
  ok(a2 && !/–/.test(a2.period) && /Amazon/.test(a2.head), 'follow-up "And last month?" answers the same question for last month (' + a2.period + ')');
  await p.evaluate(() => sendChat('Compare with last month')); await p.waitForTimeout(700);
  const a3 = await p.evaluate(() => CH.msgs[CH.msgs.length - 1]);
  ok(a3.card && a3.card.bars.every(r => r.length === 3) && /than/.test(a3.card.head), 'compare shows this vs last month per category: ' + a3.card.head);
  await p.evaluate(() => sendChat('How much on food this month?')); await p.waitForTimeout(700);
  ok(await p.evaluate(() => /dining/i.test(CH.msgs[CH.msgs.length - 1].card.head)), 'category question answers with the category total');
  const n0 = await p.evaluate(() => S.exp.length);
  await p.evaluate(() => sendChat('chai 40')); await p.waitForTimeout(600);
  await p.evaluate(() => document.querySelector('.acard [data-act=act-yes]').click()); await p.waitForTimeout(200);
  ok(await p.evaluate(n => S.exp.length === n + 1 && S.exp.some(e => e.amt === 40 && /chai/i.test(e.title)), n0), '"chai 40" adds an expense after one tap');
  ok(await p.evaluate(() => document.querySelectorAll('.tsu').length > 3 && !!document.querySelector('.cstat')), 'user messages show time + ticks, header shows data status');
  ok(await p.evaluate(() => S.chatLog.length > 5), 'conversation is saved for next time');
  ok(!errs.length, 'no JS errors ' + errs.join(';'));
  await p.evaluate(() => { CH.msgs = CH.msgs.slice(0, 3); renderChat(); document.querySelector('#msgs').scrollTop = 0; }); await p.screenshot({ path: '/tmp/claude-0/-home-user-test/efa09150-834c-5941-8bea-cc0b76604455/scratchpad/shots/chat.png' });
  res.forEach(r => console.log(r[0], ' ', r[1])); console.log(res.filter(r => r[0] === 'PASS').length + '/' + res.length); await b.close();
})().catch(e => { console.error('CRASH', e); process.exit(1); });
