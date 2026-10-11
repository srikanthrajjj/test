// Small UPI payments with no bank SMS: payment-app + bank-email notifications, quick-add tile/shortcut
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
const N = [
  ['APP:com.phonepe.app', 'Payment Successful. Paid ₹40 to Ramesh Tea Stall', 40, 'Ramesh Tea Stall'],
  ['APP:com.phonepe.app', '₹25 paid to SRI LAKSHMI BAKERY. Paid from UPI Lite', 25, 'Sri Lakshmi Bakery'],
  ['APP:net.one97.paytm', 'Paid Rs.40 to Shiva Juice Centre from Paytm UPI Lite', 40, 'Shiva Juice Centre'],
  ['APP:net.one97.paytm', '₹60 sent to Raju Auto successfully', 60, 'Raju Auto'],
  ['APP:com.google.android.apps.nbu.paisa.user', 'You paid ₹30 to Annapurna Tiffins', 30, 'Annapurna Tiffins'],
  ['APP:com.google.android.apps.nbu.paisa.user', 'Payment of ₹45 to Kumar Stores was successful', 45, 'Kumar Stores'],
  ['APP:com.dreamplug.androidapp', '₹50 paid to Chai Point via CRED UPI', 50, 'Chai Point'],
  ['APP:com.google.android.gm', 'HDFC Bank InstaAlerts. ❗ You have done a UPI txn. Check details! Dear Customer, Rs.40.00 has been debited from account **1234 to VPA paytmqr281005050101@paytm SHIVA JUICE on 10-10-26. Your UPI transaction reference number is 123.', 40, 'Shiva Juice'],
  ['APP:com.google.android.gm', 'alerts@axisbank.com. Debit transaction alert. INR 35.00 debited from A/c no. XX1234 on 10-10-2026 UPI/P2M/123/CHAI WALA', 35, 'Chai Wala'],
  ['APP:com.google.android.gm', 'Amazon.in. Your order has shipped. Rs 499 order will arrive tomorrow', 0],
  ['APP:com.phonepe.app', 'Get ₹50 cashback on your next recharge!', 0],
  ['APP:com.google.android.apps.nbu.paisa.user', 'Rahul sent you ₹200', 0],
  ['APP:net.one97.paytm', 'You received ₹500 from Priya', 0],
  ['APP:com.phonepe.app', 'Payment of ₹120 to Zomato failed. Money will be refunded', 0],
  ['APP:com.phonepe.app', 'Your Airtel bill of ₹599 is due on 18 Oct. Pay now', 0],
];
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message + ' @ ' + (e.stack || '').split('\n').slice(1, 3).join(' ')));
  await p.addInitScript(() => { window.__fast = 1; window.__q = []; window.__share = '';
    window.Native = { hasSmsPermission: () => false, requestSms() {}, loadState() { return ''; }, saveState() {}, haptic() {}, setBars() {}, isNight() { return false; }, consumeInvite() { return ''; }, consumeShared() { const v = window.__share; window.__share = ''; return v; }, share() {}, copy() {}, reminders() {}, hasNotifAccess: () => true, takeNotifs() { const v = JSON.stringify(window.__q); window.__q = []; return v; } }; });
  await p.goto('file://' + __dirname + '/../app/src/main/assets/www/index.html'); await p.waitForTimeout(400);
  const r = await p.evaluate(N => N.map(([a, b]) => { const x = notifPay({ a, b, d: Date.now(), id: '1' }); return x ? [x.amt, x.title, x.cat] : null; }), N);
  N.forEach(([a, b, amt, title], i) => ok(amt ? (r[i] && r[i][0] === amt && r[i][1] === title && r[i][2] !== 'cash') : r[i] === null, `${amt ? 'reads' : 'ignores'}: ${b.slice(0, 48)}… → ${JSON.stringify(r[i])}`));
  // end to end: notifications queue → expenses, deduped against the bank SMS of the same payment
  const e2e = await p.evaluate(N => { S.onboarded = true; S.me.name = 'Sri'; document.querySelector('#ob').classList.remove('on'); S.exp = []; const t = Date.now();
    S.exp.push({ id: 's1', amt: 40, title: 'UPI payment', cat: 'other', paid: 'me', sm: 50, ts: t - 60e3, src: 'sms', u: t });
    window.__q = N.map(([a, b], i) => ({ id: 'q' + i, a, b, d: t - i * 25 * 60e3 })); importNotifs();
    return { n: S.exp.length, merged: S.exp.find(e => e.id === 's1'), titles: S.exp.map(e => e.title).sort() }; }, N);
  ok(e2e.n === 9, `9 real payments become 9 expenses, junk ignored (got ${e2e.n}: ${e2e.titles.join(', ')})`);
  ok(e2e.merged.title === 'Ramesh Tea Stall' && (e2e.merged.srcs || []).some(s => /app:/.test(s)), 'a notification for a payment already read from SMS adds the payee name instead of a duplicate');
  // quick add tile / shortcut and Ask shortcut
  await p.evaluate(() => { window.__share = 'nest:add'; window.__shared(); }); await p.waitForTimeout(500);
  ok(await p.evaluate(() => !!document.querySelector('#sheet .body input, #sheet input')), 'Quick Settings tile / "Add expense" shortcut opens the add form');
  await p.evaluate(() => { closeSheet(); window.__share = 'nest:ask'; window.__shared(); }); await p.waitForTimeout(500);
  ok(await p.evaluate(() => document.querySelector('#chat').classList.contains('on')), '"Ask Nest" shortcut opens chat');
  ok(!errs.length, 'no page errors ' + errs.join('; '));
  await b.close();
  res.forEach(x => console.log(x[0], x[1])); const f = res.filter(x => x[0] === 'FAIL').length; console.log(`\n${res.length - f}/${res.length} passed`); process.exit(f ? 1 : 0);
})();
