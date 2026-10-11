// Solo / Together toggle + "fix the name once, all similar payments follow"
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(() => { window.__fast = 1; window.__img = 0; window.__auth = 0; window.Native = { hasSmsPermission: () => false, requestSms() { setTimeout(() => window.__onPerm(false), 5); }, loadState() { return localStorage.getItem('ns') || ''; }, saveState(j) { localStorage.setItem('ns', j); }, haptic() {}, setBars() {}, isNight() { return false; }, consumeInvite() { return ''; }, consumeShared() { return ''; }, share() {}, copy() {}, shareImage(b) { window.__img = b.length; }, lockAvailable: () => true, authenticate() { window.__auth++; setTimeout(() => window.__unlock(window.__authOk !== false), 10); }, setWeekly(on) { window.__weekly = on; } }; });
  await p.goto('file://' + __dirname + '/../app/src/main/assets/www/index.html'); await p.waitForTimeout(400);
  await p.click('[data-act=ob-next]');
  ok(await p.$('#ob-p') === null, 'new users start with "Just me" (no partner field)');
  await p.fill('#ob-me', 'Sri'); await p.click('[data-act=ob-names]'); await p.click('[data-act=ob-sample]');
  await p.waitForSelector('[data-act=ob-add]', { timeout: 9000 }); await p.click('[data-act=ob-add]'); await p.waitForTimeout(2800); await p.evaluate(() => closeSheet());
  await p.evaluate(() => { U.month = monthStart(new Date()); show('month'); }); await p.waitForTimeout(400);
  const solo = await p.evaluate(() => ({ lbl: document.querySelector('.h2t small').innerText, avs: document.querySelectorAll('.pr2 .avc').length, duo: !!document.querySelector('.duo'), promo: !!document.querySelector('[data-act=duo-on]') }));
  ok(solo.lbl === 'Spent this month' && solo.avs === 1 && !solo.duo, 'solo month screen has no partner split');
  ok(solo.promo, 'solo users see the "Track together" feature card');
  await p.click('[data-act=duo-on]'); await p.waitForTimeout(400); await p.fill('#duo-name', 'Gunjan'); await p.click('[data-act=duo-save]'); await p.waitForTimeout(500);
  const duo = await p.evaluate(() => ({ mode: S.mode, name: S.p.name, lbl: document.querySelector('.h2t small').innerText, avs: document.querySelectorAll('.pr2 .avc').length }));
  ok(duo.mode === 'duo' && duo.name === 'Gunjan' && duo.lbl === 'Spent together' && duo.avs === 2, 'turning on Together adds the partner everywhere');
  // duo: side-by-side week split + who filter
  await p.evaluate(() => { S.exp.slice(0, 6).forEach((e, i) => { if (i % 2) e.paid = 'p'; }); show('month'); }); await p.waitForTimeout(300);
  ok(await p.evaluate(() => !!document.querySelector('.tn-m.sbs') && document.querySelectorAll('.whoseg button').length === 3), 'Together shows each person side by side per week, with a Both / Sri / Gunjan filter');
  await p.click('.whoseg [data-arg=p]'); await p.waitForTimeout(300);
  ok(await p.evaluate(() => [...document.querySelectorAll('#tl .li[data-act]')].length > 0 && S.exp.filter(e => e.paid === 'p').length > 0), 'filtering to the partner shows only their spends');
  await p.evaluate(() => { U.who = 'all'; S.mode = 'solo'; show('month'); });
  // solo summary: one column, trust line
  await p.evaluate(() => openBreakdown()); await p.waitForTimeout(500);
  const sm = await p.evaluate(() => ({ one: document.querySelectorAll('#bd .tr.one').length, head: !!document.querySelector('#bd .thead'), trust: !!document.querySelector('.tq'), lab: document.querySelector('#bd .lab').innerText }));
  ok(sm.one > 0 && !sm.head && /where it went/i.test(sm.lab), 'solo month summary has no empty partner column');
  ok(sm.trust, 'month summary asks "does this look right?" with sources');
  await p.click('[data-act=month-ok]'); await p.waitForTimeout(300); ok(await p.evaluate(() => !!document.querySelector('.tok')), 'confirming the month is remembered');
  await p.click('[data-act=share-report]'); await p.waitForTimeout(800); ok(await p.evaluate(() => window.__img > 10000), 'share creates a month story image');
  await p.evaluate(() => closeSheet()); await p.waitForTimeout(300);
  // app lock
  await p.evaluate(() => show('you')); await p.waitForTimeout(300); await p.click('[data-act=lock-toggle]'); await p.waitForTimeout(300);
  ok(await p.evaluate(() => S.lock === true), 'app lock turns on after confirming fingerprint');
  await p.evaluate(() => { window.__authOk = false; showLock(); }); await p.waitForTimeout(200); ok(await p.evaluate(() => !!document.querySelector('#lock')), 'locked screen stays when unlock fails');
  await p.evaluate(() => { window.__authOk = true; }); await p.click('#lock [data-act=unlock]'); await p.waitForTimeout(200); ok(await p.evaluate(() => !document.querySelector('#lock')), 'unlock removes the lock screen');
  ok(await p.evaluate(() => window.__weekly === true), 'Sunday summary is scheduled by default');
  await p.evaluate(() => show('month'));
  // learn names
  const n = await p.evaluate(() => {
    const t = Date.now(); const mk = (id, a, d) => ({ id: 'x' + id, a: 'VM-HDFCBK', b: `Rs.${a}.00 debited from A/c XX4821 to VPA abcxyzmart@okaxis (UPI Ref ${id})`, d: t - d * 864e5 });
    [mk(1, 120, 1), mk(2, 340, 2), mk(3, 99, 3)].forEach(m => { const x = parseSms(m); S.exp.push({ id: m.id, amt: x.amt, title: x.title, cat: x.cat, paid: 'me', sm: 50, ts: x.ts, src: 'sms', raw: x.raw, mk: x.mk, u: t }); });
    const e = S.exp.find(x => x.id === 'x1'); openForm(e); F.title = 'Corner Bakery'; F.cat = 'food';
    document.querySelector('#f-save') ? document.querySelector('#f-save').click() : null;
    return 0;
  });
  await p.waitForTimeout(600);
  const fixed = await p.evaluate(() => ['x1', 'x2', 'x3'].map(id => S.exp.find(e => e.id === id)).map(e => e.title + '|' + e.cat));
  ok(fixed.every(x => x === 'Corner Bakery|food'), 'renaming one payment renames all from the same sender (' + fixed.join(', ') + ')');
  const next = await p.evaluate(() => { const x = parseSms({ id: 9, a: 'VM-HDFCBK', b: 'Rs.55.00 debited from A/c XX4821 to VPA abcxyzmart@okaxis (UPI Ref 9)', d: Date.now() }); return x.title + '|' + x.cat; });
  ok(next === 'Corner Bakery|food', 'new payments from that sender use the learned name');
  ok(!errs.length, 'no JS errors ' + errs.join(';'));
  res.forEach(r => console.log(r[0], ' ', r[1])); console.log(res.filter(r => r[0] === 'PASS').length + '/' + res.length); await b.close();
})().catch(e => { console.error('CRASH', e); process.exit(1); });
