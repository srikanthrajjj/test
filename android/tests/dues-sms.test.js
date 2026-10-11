// Dues read from texts: credit card statements + bill / EMI / recharge reminders (real Indian formats)
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(() => { window.__fast = 1; window.__rem = [];
    window.Native = { hasSmsPermission: () => false, requestSms() {}, loadState() { return ''; }, saveState() {}, haptic() {}, setBars() {}, isNight() { return false; }, consumeInvite() { return ''; }, consumeShared() { return ''; }, share() {}, copy() {}, reminders(j) { window.__rem = JSON.parse(j); } }; });
  await p.goto('file://' + __dirname + '/../app/src/main/assets/www/index.html'); await p.waitForTimeout(300);
  const out = await p.evaluate(() => {
    S.onboarded = true; S.me.name = 'Sri'; S.mode = 'solo'; document.querySelector('#ob').classList.remove('on');
    const D = 864e5, now = Date.now(), M = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    const at = k => new Date(now + k * D), dd = d => String(d.getDate()).padStart(2, '0'), mm = d => String(d.getMonth() + 1).padStart(2, '0');
    const f1 = k => { const d = at(k); return `${dd(d)}-${mm(d)}-${d.getFullYear()}`; }, f2 = k => { const d = at(k); return `${dd(d)}-${M[d.getMonth()].toUpperCase()}-${String(d.getFullYear()).slice(2)}`; },
      f3 = k => { const d = at(k); return `${dd(d)} ${M[d.getMonth()]} ${d.getFullYear()}`; }, f4 = k => { const d = at(k); return `${M[d.getMonth()]} ${d.getDate()}`; }, f5 = k => { const d = at(k); return `${dd(d)}/${mm(d)}/${d.getFullYear()}`; };
    const day = k => new Date(at(k)).toDateString();
    const C = [
      ['AX-HDFCBK', `Statement for HDFC Bank Credit Card ending 4321 has been generated. Total Amt Due: Rs.18,450.00 Min Amt Due: Rs.920.00 Due by ${f1(19)}. Pay now: hdfcbk.io/x`, { kind: 'card', amt: 18450, min: 920, card: '4321', k: 19 }],
      ['VM-ICICIB', `ICICI Bank Credit Card XX1234 Statement: Total Amount Due INR 12,345.67; Minimum Amount Due INR 620.00; Payment Due Date ${f2(25)}.`, { kind: 'card', amt: 12345.67, min: 620, card: '1234', k: 25 }],
      ['JD-SBICRD', `Your SBI Card ending 5678 statement dated ${f5(-3)} is generated. Total Amt Due Rs 9,876; Min Amt Due Rs 494; payable by ${f3(17)}.`, { kind: 'card', amt: 9876, card: '5678', k: 17 }],
      ['AD-AXISBK', `Payment of INR 23,100.00 for Axis Bank Credit Card XX9012 is due on ${f3(14)}. Minimum amount due is INR 1,155.00.`, { kind: 'card', amt: 23100, min: 1155, card: '9012', k: 14 }],
      ['BZ-KOTAKB', `Reminder: Your Kotak Credit Card xx3456 bill of Rs.5,432.10 is due on ${f5(4)}. Please ignore if paid.`, { kind: 'card', amt: 5432.1, card: '3456', k: 4 }],
      ['VK-ONECRD', `Your OneCard bill of ₹7,890 is due on ${f4(9)}. Pay now to avoid late fee.`, { kind: 'card', amt: 7890, k: 9 }],
      ['AD-AIRTEL', `Dear Customer, your Airtel bill of Rs 599.00 for 98XXXXXX12 is due on ${f2(7)}. Pay via Airtel Thanks app.`, { kind: 'bill', title: 'Airtel bill', amt: 599, k: 7 }],
      ['JM-JIOINF', `Your Jio plan of Rs 299 will expire on ${f2(3)}. Recharge now to continue enjoying services.`, { kind: 'bill', title: 'Jio recharge', amt: 299, k: 3 }],
      ['VM-BESCOM', `BESCOM: Bill for RR No. XYZ123 is Rs.1,234.00. Due date ${f5(14)}.`, { kind: 'bill', title: 'Electricity bill', amt: 1234, k: 14 }],
      ['AX-BAJAJF', `Dear Customer, EMI of Rs 4,999 for your loan a/c XX7788 will be debited on ${f1(25)}. Please maintain sufficient balance.`, { kind: 'bill', title: 'Bajaj Finserv EMI', amt: 4999, k: 25 }],
      ['VM-LICIND', `LIC: Premium of Rs 12,500 for policy 123456789 is due on ${f5(17)}. Pay online at licindia.in`, { kind: 'bill', title: 'LIC premium', amt: 12500, k: 17 }],
      ['AD-ACTBRD', `Your ACT Fibernet bill of Rs.1,179 is due on ${f3(11)}.`, { kind: 'bill', title: 'ACT Fibernet bill', amt: 1179, k: 11 }],
      ['AD-HDFCBK', `Your EMI of Rs 8,250 for Home Loan a/c XX4455 is due on ${f1(6)}.`, { kind: 'bill', title: 'HDFC Bank loan EMI', amt: 8250, k: 6 }],
      ['AD-HDFCBK', `Get a pre-approved loan of up to Rs 5,00,000. Apply before ${f1(20)}.`, null],
      ['AD-HDFCBK', `Rs 500 debited from a/c XX1234 on ${f1(-1)} to VPA swiggy@upi. Not you? Call 1800`, null],
      ['AD-ICICIB', `123456 is OTP for txn of Rs 2,000 at Amazon. Valid till ${f1(0)}`, null],
      ['AD-AXISBK', `Convert your purchase of Rs 25,000 to EMI before ${f1(9)}. T&C apply`, null],
      ['AD-SBICRD', `Your reward points 1,200 will expire on ${f1(20)}. Redeem now`, null],
      ['AD-HDFCBK', `Your HDFC Bank Debit Card XX1234 annual fee of Rs 500 will be debited on ${f1(9)}.`, null],
      ['AD-AIRTEL', `Get 20% off on Airtel Black. Offer valid till ${f1(5)}`, null],
    ];
    const R = C.map(([a, body, exp]) => { const r = dueSms({ a, b: body, d: now }); if (!exp) return [body.slice(0, 40), !r || !!r.paid, r]; const good = r && r.kind === exp.kind && Math.abs(r.amt - exp.amt) < .01 && new Date(r.due).toDateString() === day(exp.k) && (!exp.title || r.title === exp.title) && (exp.min == null || r.min === exp.min) && (exp.card == null || r.card === exp.card); return [body.slice(0, 40), !!good, r]; });
    // full flow through sideSms → Dues tab + reminders
    S.dues = []; C.forEach(([a, body], i) => sideSms({ a, b: body, d: now - (30 - i) * 60e3, id: 'm' + i }, true));
    const L = allDues(), titles = L.map(d => d.title);
    // paid texts
    sideSms({ a: 'AX-HDFCBK', b: 'Payment of Rs 18,450.00 received towards your HDFC Bank Credit Card ending 4321. Thank you.', d: now + 1000, id: 'p1' }, true);
    sideSms({ a: 'AD-AIRTEL', b: 'Thank you for payment of Rs 599 for Airtel mobile 98XXXX12.', d: now + 2000, id: 'p2' }, true);
    const L2 = allDues(), hd = L2.find(d => /HDFC.*4321/.test(d.title)), air = L2.find(d => d.title === 'Airtel bill');
    // older statement must not overwrite a newer one (deep scan reads newest first)
    sideSms({ a: 'AX-HDFCBK', b: `Statement for HDFC Bank Credit Card ending 4321. Total Amt Due: Rs.3,000.00 Due by ${f1(-20)}.`, d: now - 40 * D, id: 'old' }, true);
    const keep = (S.dues.find(x => x.card === '4321') || {}).amt;
    // recurring Airtel history must not duplicate the text-based Airtel due
    [3, 2, 1].forEach(k => { const d = new Date(now); d.setMonth(d.getMonth() - k); d.setDate(at(7).getDate()); S.exp.push({ id: 'r' + k, amt: 599, title: 'Airtel', cat: 'bill', paid: 'me', sm: 50, ts: d.getTime() - 5 * D, src: 'sms', u: now }); });
    const airCount = allDues().filter(d => /^airtel/i.test(d.title)).length;
    show('dues'); const html = document.querySelector('#main').innerText; scheduleReminders();
    return { R, titles, hdPaid: hd && hd.paid, airPaid: air && air.paid, keep, airCount, html, rem: window.__rem.map(r => r.title) };
  });
  out.R.forEach(([t, g, r]) => ok(g, `parse: ${t}… → ${r ? JSON.stringify({ k: r.kind, t: r.title, a: r.amt, m: r.min, c: r.card, due: r.due && new Date(r.due).toDateString(), paid: r.paid }) : 'null'}`));
  ok(out.titles.length === 13, `13 dues on the Dues tab (got ${out.titles.length}): ${out.titles.join(' | ')}`);
  ok(out.hdPaid === true, 'card bill marked paid by "payment received" text');
  ok(out.airPaid === true, 'Airtel bill marked paid by "thank you for payment" text');
  ok(out.keep === 18450, 'older statement does not overwrite newer one');
  ok(out.airCount === 1, `Airtel not duplicated by recurring detection (got ${out.airCount})`);
  ok(/Jio recharge/.test(out.html) && /Bajaj Finserv EMI/.test(out.html) && /ICICI Bank card ••1234 bill/.test(out.html), 'Dues tab lists text-based dues');
  ok(out.rem.some(r => /Jio recharge.*due/.test(r)) && out.rem.some(r => /ICICI/.test(r)), `reminders scheduled (${out.rem.length})`);
  ok(!errs.length, 'no page errors ' + errs.join('; '));
  await b.close();
  res.forEach(r => console.log(r[0], r[1])); const f = res.filter(r => r[0] === 'FAIL').length; console.log(`\n${res.length - f}/${res.length} passed`); process.exit(f ? 1 : 0);
})();
