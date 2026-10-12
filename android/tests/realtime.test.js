// Real-time capture: overlapping native SMS reads never wedge auto-import; queued texts import instantly; pull to refresh
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(() => { window.__fast = 1; window.__q = []; window.__inbox = [];
    // a bridge that answers slowly and asynchronously, like the real one (each read takes 600 ms)
    const reply = (since) => setTimeout(() => window.__onSms && window.__onSms(JSON.stringify({ scanned: window.__inbox.length, msgs: window.__inbox.filter(m => m.d > since) })), 600);
    window.Native = { hasSmsPermission: () => true, requestSms() {}, loadState() { return ''; }, saveState() {}, haptic() {}, setBars() {}, isNight() { return false; }, consumeInvite() { return ''; }, consumeShared() { return ''; }, share() {}, copy() {}, reminders() {},
      hasNotifAccess: () => true, takeNotifs() { const v = JSON.stringify(window.__q); window.__q = []; return v; },
      scanSmsSince(s) { reply(+s); }, scanSms(days) { reply(Date.now() - days * 864e5); } }; });
  await p.goto('file://' + __dirname + '/../app/src/main/assets/www/index.html'); await p.waitForTimeout(500);
  await p.evaluate(() => { S.onboarded = true; S.me.name = 'Sri'; S.mode = 'solo'; S.smsLast = Date.now() - 3600e3; S.dueV2 = 0; S.exp = []; document.querySelector('#ob').classList.remove('on'); show('home'); });
  // the v3.4.1 bug: auto-import (with the 75-day dues read) and a full deep scan start together
  await p.evaluate(() => { const n = Date.now(); window.__inbox.push({ id: 501, a: 'AD-HDFCBK', b: 'Sent Rs.700.00 From HDFC Bank A/C *1234 To RAHUL KUMAR On 11/10/26 Ref 123456 Not You? Call 18002586161', d: n - 60e3 });
    autoImport(); deepScan(false); });
  await p.waitForTimeout(3500);
  const r1 = await p.evaluate(() => ({ busy: autoBusy, n: S.exp.filter(e => e.amt === 700).length }));
  ok(!r1.busy, 'auto-import is not left stuck "busy" when two inbox reads overlap');
  ok(r1.n === 1, `the ₹700 text is imported exactly once (got ${r1.n})`);
  // a later text still imports (the old bug blocked every import after the first collision)
  await p.evaluate(() => { window.__inbox.push({ id: 502, a: 'VM-ICICIT', b: 'ICICI Bank Acct XX123 debited for Rs 250.00 on 11-Oct-26; SWIGGY credited. UPI:99. Call 18002662 for dispute.', d: Date.now() - 5e3 }); window.__smsChanged(); });
  await p.waitForTimeout(1800);
  ok(await p.evaluate(() => S.exp.some(e => e.amt === 250)), 'the next text after the overlap is imported too');
  // native queue (SMS receiver / SMS-app notification) → imported instantly, no inbox read needed
  await p.evaluate(() => { window.__q = [{ id: 'r1', a: 'SMS:VM-SBIUPI', b: 'Dear UPI user A/C X1234 debited by 120.0 on date 11Oct26 trf to CHAI POINT Refno 123456789. If not u? call 1800111109. -SBI', d: Date.now() - 2000 }]; window.__notifChanged(); });
  await p.waitForTimeout(1200);
  const q = await p.evaluate(() => { const e = S.exp.find(x => x.amt === 120); return e && [e.title, e.src, document.querySelector('#main').innerText.includes('Chai Point')]; });
  ok(q && q[0] === 'Chai Point' && q[1] === 'sms' && q[2], 'a queued bank text appears on Home within a second, as an SMS expense: ' + JSON.stringify(q));
  // when the inbox read later sees the same text, it is not added twice
  await p.evaluate(() => { window.__inbox.push({ id: 503, a: 'VM-SBIUPI', b: 'Dear UPI user A/C X1234 debited by 120.0 on date 11Oct26 trf to CHAI POINT Refno 123456789. If not u? call 1800111109. -SBI', d: Date.now() - 2000 }); window.__smsChanged(); });
  await p.waitForTimeout(1800);
  ok(await p.evaluate(() => S.exp.filter(e => e.amt === 120).length === 1), 'the same text from the inbox does not create a duplicate');
  // a queued card statement becomes a due, not an expense
  await p.evaluate(() => { const d = new Date(Date.now() + 9 * 864e5); const f = `${String(d.getDate()).padStart(2, '0')}-${String(d.getMonth() + 1).padStart(2, '0')}-${d.getFullYear()}`; window.__q = [{ id: 'r2', a: 'SMS:AX-HDFCBK', b: `Statement for HDFC Bank Credit Card ending 4321 has been generated. Total Amt Due: Rs.18,450.00 Min Amt Due: Rs.920.00 Due by ${f}.`, d: Date.now() }]; window.__notifChanged(); });
  await p.waitForTimeout(1000);
  ok(await p.evaluate(() => (S.dues || []).some(d => d.amt === 18450) && !S.exp.some(e => e.amt === 18450)), 'a queued card statement becomes a due, not an expense');
  // pull to refresh / tap "Updated"
  await p.evaluate(() => { window.__inbox.push({ id: 504, a: 'AD-HDFCBK', b: 'Sent Rs.90.00 From HDFC Bank A/C *1234 To RAMU TEA On 11/10/26 Ref 1 Not You? Call 18002586161', d: Date.now() - 1000 }); closeSheet(); show('home'); });
  await p.waitForTimeout(300); await p.evaluate(() => refreshAll(true)); await p.waitForTimeout(1800);
  ok(await p.evaluate(() => S.exp.some(e => e.amt === 90) && /new payment|up to date/i.test(document.querySelector('#toast') ? document.querySelector('#toast').innerText : document.body.innerText)), 'refresh says what changed');
  const pg = await p.$('.page'); const box = await pg.boundingBox();
  await p.evaluate(() => { window.__inbox.push({ id: 505, a: 'AD-HDFCBK', b: 'Sent Rs.45.00 From HDFC Bank A/C *1234 To KUMAR STORES On 11/10/26 Ref 2 Not You? Call 18002586161', d: Date.now() - 500 }); });
  await p.evaluate(() => { const pg = document.querySelector('.page'); const t = (type, y) => pg.dispatchEvent(new TouchEvent(type, { touches: type === 'touchend' ? [] : [new Touch({ identifier: 1, target: pg, clientX: 200, clientY: y })], changedTouches: [new Touch({ identifier: 1, target: pg, clientX: 200, clientY: y })], bubbles: true })); t('touchstart', 120); t('touchmove', 200); t('touchmove', 330); t('touchend', 330); });
  await p.waitForTimeout(1800);
  ok(await p.evaluate(() => S.exp.some(e => e.amt === 45)), 'pull-to-refresh on Home brings in new texts');
  ok(!errs.length, 'no page errors ' + errs.join('; '));
  await b.close();
  res.forEach(x => console.log(x[0], x[1])); const f = res.filter(x => x[0] === 'FAIL').length; console.log(`\n${res.length - f}/${res.length} passed`); process.exit(f ? 1 : 0);
})();
