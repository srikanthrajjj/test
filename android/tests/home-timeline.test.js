// Home "Recent transactions": today in min/hr ago (live), earlier days grouped under This week / Last week
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 390, height: 844 } })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(() => { window.__fast = 1; });
  await p.goto('file://' + __dirname + '/../app/src/main/assets/www/index.html'); await p.waitForTimeout(400);
  const r = await p.evaluate(() => { S.onboarded = true; S.me.name = 'Sri'; S.mode = 'solo'; document.querySelector('#ob').classList.remove('on'); const n = Date.now(), M = 60e3, H = 36e5, D = 864e5;
    const sod = new Date(n).setHours(0, 0, 0, 0), safeH = Math.min(2 * H + 5 * M, n - sod - M); // keep "today" items inside today
    [['Chai', 40, 20e3], ['Swiggy', 450, 12 * M], ['Uber', 210, safeH], ['Zepto', 640, (n - sod) + 3 * H], ['Myntra', 2400, 9 * D], ['Old', 99, 40 * D]].forEach((x, i) => S.exp.push({ id: 'e' + i, amt: x[1], title: x[0], cat: 'food', paid: 'me', sm: 50, ts: n - x[2], src: 'sms', u: n }));
    show('home'); const t = [...document.querySelectorAll('.hwk b, .hday span:first-child')].map(x => x.textContent), rt = [...document.querySelectorAll('.rt')].map(x => x.innerText);
    return { t, rt, live: !!document.querySelector('.hdg.live'), titles: [...document.querySelectorAll('.hrow .t1')].map(x => x.innerText) }; });
  ok(r.rt[0] === 'Just now' && r.rt[1] === '12 min ago' && /hr ago|min ago/.test(r.rt[2]), 'today shows "Just now", "12 min ago", "N hr ago": ' + r.rt.slice(0, 3).join(', '));
  ok(/AM|PM/.test(r.rt[3]), 'earlier days show the clock time: ' + r.rt[3]);
  ok(r.t[0] === 'This week' && r.t[1] === 'Today' && r.t.includes('Yesterday') || r.t.includes('Last week'), 'grouped by week, then day: ' + r.t.join(' > '));
  ok(r.t.includes('Last week') || r.t.some(x => /–/.test(x)), 'older payments sit under "Last week" or a date range');
  ok(!r.titles.includes('Old'), 'payments older than two weeks are left to Activity');
  ok(r.live, "today's group is marked live");
  await p.evaluate(() => { const el = document.querySelector('.rt'); el.dataset.ts = String(Date.now() - 5 * 60e3); }); await p.waitForTimeout(31000);
  ok(await p.evaluate(() => document.querySelector('.rt').innerText === '5 min ago'), 'relative times update by themselves while Home is open');
  ok(!errs.length, 'no page errors ' + errs.join('; '));
  await b.close();
  res.forEach(x => console.log(x[0], x[1])); const f = res.filter(x => x[0] === 'FAIL').length; console.log(`\n${res.length - f}/${res.length} passed`); process.exit(f ? 1 : 0);
})();
