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
  ['APP:com.phonepe.app', '₹700 Payment Successful. To Rahul Kumar', 700, 'Rahul Kumar'],
  ['APP:com.phonepe.app', 'Rahul Kumar. You paid ₹700', 700, 'Rahul Kumar'],
  ['APP:com.phonepe.app', '₹ 700 paid successfully to Sri Balaji Stores', 700, 'Sri Balaji Stores'],
  ['APP:com.phonepe.app', 'Paid to Sri Balaji Stores. ₹700', 700, 'Sri Balaji Stores'],
  ['APP:com.phonepe.app', 'Money sent successfully. ₹700 to Rahul', 700, 'Rahul'],
  ['APP:com.phonepe.app', 'Transaction Successful. ₹700 debited from your account', 700, null],
];
const SMS = [
  ['AX-AXISBK', 'INR 700.00 debited A/c no. XX1234 11-10-26, 14:05:11 UPI/P2A/123456/RAHUL KUMAR Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank', 'Rahul Kumar'],
  ['AD-CANBNK', 'An amount of INR 700.00 has been DEBITED to your account XXX123 on 11/10/2026 towards UPI/123456/Rahul Kumar. Total Avail.bal INR 5,000.00 - Canara Bank', 'Rahul Kumar'],
  ['VM-IDFCFB', 'Your A/C XXXXX1234 has been debited by INR 700.00 on 11/10/26 towards UPI/RAHUL KUMAR. New bal: INR 5,000.00. Not you? call 18001024. IDFC FIRST Bank', 'Rahul Kumar'],
  ['AD-YESBNK', 'INR 700.00 debited from A/c XX1234 on 11-OCT-2026 for UPI-RAHUL KUMAR-rahul@ybl. Avl Bal INR 5000.00', 'Rahul Kumar'],
  ['AD-PNBSMS', 'A/c XX1234 debited INR 700.00 Dt 11-10-26 14:05 thru UPI:123456.Bal INR 5000.00 CR Not u?Fwd this SMS to 9264092640 to block UPI.-PNB', 'UPI payment'],
  ['JD-UNIONB', 'A/c *1234 Debited for Rs:700.00 on 11-10-2026 14:05:11 by Mob Bk ref no 123456 Avl Bal Rs:5000.00.If not you, Call 1800222243 -Union Bank of India', null],
];
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message + ' @ ' + (e.stack || '').split('\n').slice(1, 3).join(' ')));
  await p.addInitScript(() => { window.__fast = 1; window.__q = []; window.__share = '';
    window.Native = { hasSmsPermission: () => false, requestSms() {}, loadState() { return ''; }, saveState() {}, haptic() {}, setBars() {}, isNight() { return false; }, consumeInvite() { return ''; }, consumeShared() { const v = window.__share; window.__share = ''; return v; }, share() {}, copy() {}, reminders() {}, hasNotifAccess: () => true, takeNotifs() { const v = JSON.stringify(window.__q); window.__q = []; return v; } }; });
  await p.goto('file://' + __dirname + '/../app/src/main/assets/www/index.html'); await p.waitForTimeout(400);
  const r = await p.evaluate(N => N.map(([a, b]) => { const x = notifPay({ a, b, d: Date.now(), id: '1' }); return x ? [x.amt, x.title, x.cat] : null; }), N);
  N.forEach(([a, b, amt, title], i) => ok(amt ? (r[i] && r[i][0] === amt && (title === null || r[i][1] === title) && r[i][2] !== 'cash') : r[i] === null, `${amt ? 'reads' : 'ignores'}: ${b.slice(0, 48)}… → ${JSON.stringify(r[i])}`));
  const rs = await p.evaluate(Q => Q.map(([a, b]) => { const x = parseSms({ a, b, d: Date.now(), id: '1' }); return x ? [x.amt, x.title] : null; }), SMS);
  SMS.forEach(([a, b, title], i) => ok(rs[i] && rs[i][0] === 700 && (title === null || rs[i][1] === title), `bank SMS ${a}: ₹700 → ${JSON.stringify(rs[i])}`));
  // an unreadable payment notification lands in Review with one-tap add, instead of vanishing
  const sk = await p.evaluate(() => { S.onboarded = true; document.querySelector('#ob').classList.remove('on'); S.unparsed = []; window.__q = [{ id: 'zz1', a: 'APP:com.phonepe.app', b: 'PhonePe. ₹700 • Rahul Kumar • 2:05 PM', d: Date.now() }, { id: 'zz2', a: 'APP:com.phonepe.app', b: 'Get ₹50 cashback now', d: Date.now() }]; importNotifs(); return (S.unparsed || []).map(u => u.id + ':' + u.a); });
  ok(sk.join() === 'zz1:PhonePe', 'an unreadable PhonePe payment goes to Review (promos do not): ' + sk.join());
  await p.evaluate(() => { S.onboarded = true; document.querySelector('#ob').classList.remove('on'); openReview(); }); await p.waitForTimeout(400);
  ok(await p.evaluate(() => /Payment notification we couldn't read/.test(document.querySelector('#sheet').innerText) && !!document.querySelector('[data-act=un-skip]')), 'Review shows it with "Add as expense" and "Not a payment"');
  await p.click('[data-act=un-add][data-arg=zz1]'); await p.waitForTimeout(600);
  ok(await p.evaluate(() => document.querySelector('#f-amt') && document.querySelector('#f-amt').value === '700'), 'one tap opens the add form with ₹700 filled in');
  await p.evaluate(() => closeSheet()); await p.waitForTimeout(400);
  // end to end: notifications queue → expenses, deduped against the bank SMS of the same payment
  const e2e = await p.evaluate(N => { S.onboarded = true; S.me.name = 'Sri'; document.querySelector('#ob').classList.remove('on'); S.exp = []; const t = Date.now();
    S.exp.push({ id: 's1', amt: 40, title: 'UPI payment', cat: 'other', paid: 'me', sm: 50, ts: t - 60e3, src: 'sms', u: t });
    window.__q = N.map(([a, b], i) => ({ id: 'q' + i, a, b, d: t - i * 25 * 60e3 })); importNotifs();
    return { n: S.exp.length, merged: S.exp.find(e => e.id === 's1'), titles: S.exp.map(e => e.title).sort() }; }, N);
  ok(e2e.n === 15, `15 real payments become 15 expenses, junk ignored (got ${e2e.n}: ${e2e.titles.join(', ')})`);
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
