// "Your code" + "Enter partner's code" card -> both phones get "start syncing" -> expenses merge
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const URL = 'file:///tmp/claude-0/-home-user-test/efa09150-834c-5941-8bea-cc0b76604455/scratchpad/www-test/index.html';
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch(); const errs = [];
  const mk = async (name, amts) => { const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage(); p.on('pageerror', e => errs.push(e.message));
    await p.addInitScript(() => { window.__fast = 1; }); await p.goto(URL); await p.waitForTimeout(300);
    await p.evaluate(([n, a]) => { S.onboarded = true; S.me.name = n; S.mode = 'solo'; document.querySelector('#ob').classList.remove('on'); const t = Date.now(); a.forEach((x, i) => S.exp.push({ id: n + i, amt: x, title: n + ' shop ' + i, cat: 'shop', paid: 'me', sm: 50, ts: t - i * 36e5, src: 'manual', u: t })); persist(); show('you'); }, [name, amts]); await p.waitForTimeout(300); return p; };
  const A = await mk('Sri', [100, 200]), B = await mk('Gunjan', [50]);
  ok(await A.evaluate(() => !!document.querySelector('#code-in') && !!document.querySelector('[data-act=get-code]')), 'You screen shows "Get my code" and a box to enter a partner code');
  await A.click('[data-act=get-code]'); await A.waitForTimeout(1500);
  const code = await A.evaluate(() => S.sync && S.sync.code);
  ok(code && await A.evaluate(c => document.querySelector('.code.sm').innerText === c, code), 'my code is shown with WhatsApp / Copy');
  await B.fill('#code-in', code.toLowerCase()); await B.click('#code-go'); await B.waitForTimeout(1500);
  ok(await B.evaluate(() => /is connected/.test(document.querySelector('#sheet').innerText) && S.mode === 'duo'), 'entering the code connects and pops up "start syncing"');
  await A.evaluate(() => syncNow()); await A.waitForTimeout(1000);
  ok(await A.evaluate(() => /Gunjan is connected/.test(document.querySelector('#sheet').innerText)), 'the other phone detects the partner and pops up too');
  ok(await B.evaluate(() => S.exp.length === 1), 'nothing is shared until Start is tapped');
  await A.evaluate(() => document.querySelector('[data-act=sync-start]').click()); await B.evaluate(() => document.querySelector('[data-act=sync-start]').click()); await A.waitForTimeout(500);
  for (let i = 0; i < 3; i++) { await A.evaluate(() => syncNow()); await B.evaluate(() => syncNow()); await A.waitForTimeout(500); }
  ok(await A.evaluate(() => S.exp.length === 3) && await B.evaluate(() => S.exp.length === 3), 'after both tap Start, both phones have all 3 expenses');
  ok(await B.evaluate(() => S.exp.filter(e => e.paid === 'p').length === 2), 'partner\'s expenses show under their name');
  ok(!errs.length, 'no JS errors ' + errs.join(';'));
  res.forEach(r => console.log(r[0], ' ', r[1])); console.log(res.filter(r => r[0] === 'PASS').length + '/' + res.length); await b.close();
})().catch(e => { console.error('CRASH', e); process.exit(1); });
