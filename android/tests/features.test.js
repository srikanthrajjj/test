// Top-10 competitor features: review inbox, duplicates, health, card dues, cash, search/tags, splits, backup, CSV, payday months, FY, reminders, home
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(() => { window.__fast = 1; window.__inbox = []; window.__rem = null; window.__file = null;
    window.Native = { hasSmsPermission: () => true, requestSms() {}, scanSms(days) { setTimeout(() => window.__onSms(JSON.stringify({ scanned: window.__inbox.length, msgs: window.__inbox })), 10); }, scanSmsSince(s) { const t = +s; setTimeout(() => window.__onSms(JSON.stringify({ scanned: window.__inbox.length, msgs: window.__inbox.filter(m => m.d > t) })), 10); },
      loadState() { return localStorage.getItem('fs') || ''; }, saveState(j) { localStorage.setItem('fs', j); }, haptic() {}, setBars() {}, isNight() { return false; }, consumeInvite() { return ''; }, consumeShared() { return ''; }, share() {}, copy() {}, shareWhatsApp(t) { window.__wa = t; },
      takeNotifs() { const q = window.__nq || []; window.__nq = []; return JSON.stringify(q); }, hasNotifAccess: () => true, reminders(j) { window.__rem = JSON.parse(j); }, shareFile(b64, n) { window.__file = n; } }; });
  await p.goto('file://' + __dirname + '/../app/src/main/assets/www/index.html'); await p.waitForTimeout(300);
  await p.evaluate(() => { S.onboarded = true; S.me.name = 'Sri'; S.mode = 'solo'; S.smsLast = Date.now() - 5 * 864e5; document.querySelector('#ob').classList.remove('on'); persist(); show('home'); });
  const now = Date.now();
  // inbox: an unreadable bank text, a card bill due, an ATM withdrawal, and a credit from Rahul later
  await p.evaluate(n => { let id = 1; const m = (d, a, body) => window.__inbox.push({ id: id++, a, b: body, d: n - d * 36e5 });
    m(30, 'VM-ICICIB', 'Alert: Rs 2,340.00 txn processed on ICICI card XX9911. Ref 99812');
    m(28, 'AD-HDFCBK', 'Your HDFC Bank Credit Card XX4321 statement: Total amount due Rs 18,450.00 due on ' + new Date(n + 2 * 864e5).toLocaleDateString('en-GB').replace(/\//g, '-'));
    m(20, 'VM-SBIINB', 'Rs 3,000.00 withdrawn at ATM from A/c XX1234 on ' + new Date(n).toLocaleDateString('en-GB') + '. Avl Bal Rs 20,000');
    m(10, 'VM-HDFCBK', 'Rs.1,200.00 debited from A/c XX4821 to VPA swiggy@axb (UPI Ref 1)'); }, now);
  await p.evaluate(() => window.__smsChanged()); await p.waitForTimeout(800);

  ok(await p.evaluate(() => (S.unparsed || []).some(u => /ICICI/.test(u.a))), 'a bank text Nest can\'t parse is kept for review (not silently lost)');
  ok(await p.evaluate(() => (S.dues || []).some(d => d.amt === 18450)), 'credit-card bill due date is picked up from the statement SMS');
  ok(await p.evaluate(() => S.exp.some(e => e.cat === 'cash' && e.amt === 3000) && !counted(S.exp.find(e => e.cat === 'cash'))), 'ATM withdrawal goes to cash, not counted as spending');
  // duplicate from GPay a minute after the bank SMS with a different name
  await p.evaluate(n => { const sw = S.exp.find(e => e.amt === 1200); S.exp.push({ id: 'g1', amt: 1200, title: 'Swiggy Order', cat: 'food', paid: 'me', sm: 50, ts: sw.ts + 9e5, src: 'manual', u: n }); show('home'); }, now);
  await p.waitForTimeout(300);
  const home = await p.evaluate(() => ({ rev: !!document.querySelector('#main [data-act=review]'), due: !!document.querySelector('.hdue [data-act=due-ignore]'), cash: !!document.querySelector('[data-act=add-cash]'), qa: !!document.querySelector('#main [data-act=add]') && !!document.querySelector('#tabs2 [data-act=chat]'), tabs: [...document.querySelectorAll('#tabs2 .t')].map(t => t.textContent.trim()).join('·') }));
  ok(home.rev && home.due && home.cash, 'home shows review, card-bill-due (with Paid/Ignore) and cash-unaccounted cards');
  ok(home.qa && home.tabs === 'Home·Activity·Ask Nest', 'home has Add expense, and the tabs are Home · Activity · Ask Nest (' + home.tabs + ')');
  await p.evaluate(() => openReview()); await p.waitForTimeout(400);
  ok(await p.evaluate(() => /twice/.test(document.querySelector('#sheet').innerText)), 'review inbox shows the possible duplicate');
  await p.evaluate(() => document.querySelector('[data-act=dup-merge]').click()); await p.waitForTimeout(300);
  ok(await p.evaluate(() => S.exp.filter(e => e.amt === 1200).length === 1), 'Merge keeps one payment');
  await p.evaluate(() => document.querySelector('[data-act=un-ignore]').click()); await p.waitForTimeout(300);
  ok(await p.evaluate(() => (S.ignore || []).includes('ICICIB') && !(S.unparsed || []).length), 'Ignore sender clears it and stops future texts from it');
  await p.evaluate(() => { closeSheet(); openHealth(); }); await p.waitForTimeout(400);
  ok(await p.evaluate(() => /HDFC Bank/i.test(document.querySelector('#sheet').innerText)), 'health check lists each bank sender with counts');
  // search + tags
  await p.evaluate(() => { closeSheet(); S.exp.push({ id: 't1', amt: 4500, title: 'Taj Resort', cat: 'trip', paid: 'me', sm: 50, ts: Date.now() - 3e6, src: 'manual', note: 'dinner #goa-trip', u: Date.now() }); S.exp.push({ id: 't2', amt: 900, title: 'Cab', cat: 'move', paid: 'me', sm: 50, ts: Date.now() - 2e6, src: 'manual', note: '#goa-trip', u: Date.now() }); openSearch(); }); await p.waitForTimeout(500);
  ok(await p.evaluate(() => /#goa-trip/.test(document.querySelector('#sres').innerText) && /5,400/.test(document.querySelector('#sres').innerText)), 'search shows tag totals (#goa-trip ₹5,400)');
  await p.fill('#sq', 'taj'); await p.waitForTimeout(200); ok(await p.evaluate(() => /1 result/i.test(document.querySelector('#sres').innerText)), 'search finds by merchant');
  // split
  await p.evaluate(() => { closeSheet(); openSplit('t1'); }); await p.waitForTimeout(400); await p.fill('#sp-who', 'Rahul'); await p.fill('#sp-amt', '2250'); await p.evaluate(() => document.querySelector('[data-act=sp-save]').click()); await p.waitForTimeout(300);
  const tot = await p.evaluate(() => S.exp.filter(e => e.id === 't1' || e.cat === 'iou').reduce((a, e) => a + e.amt, 0));
  ok(tot === 2250, 'after a split only my share (₹2,250) counts');
  await p.evaluate(() => { openPeople(); }); await p.waitForTimeout(300); await p.evaluate(() => { window.prompt = () => 'sri@okaxis'; document.querySelector('[data-act=iou-req]').click(); });
  ok(await p.evaluate(() => /upi:\/\/pay\?pa=sri%40okaxis/.test(window.__wa || '') && /2250/.test(window.__wa)), 'Request sends a WhatsApp message with a UPI pay link');
  await p.evaluate(n => { closeSheet(); window.__inbox.push({ id: 99, a: 'VM-HDFCBK', b: 'Rs 2,250.00 credited to A/c XX4821 by UPI from Rahul Sharma. Ref 77', d: n + 6e4 }); window.__smsChanged(); }, now); await p.waitForTimeout(800);
  ok(await p.evaluate(() => S.exp.find(e => e.cat === 'iou').settled > 0), 'when Rahul pays back, the IOU settles itself');
  // backup round trip + CSV import
  const n0 = await p.evaluate(() => S.exp.length);
  const bk = await p.evaluate(async () => { const t = await makeBackup('secret123'); const before = S.exp.length; const keep = S.exp.slice(); S.exp = S.exp.slice(0, 2); const n = await restoreBackup(t, 'secret123'); let bad = ''; try { await restoreBackup(t, 'wrong-pass'); } catch (e) { bad = e.message; } return { n, after: S.exp.length, before, bad, enc: !/Swiggy/.test(t) }; });
  ok(bk.after === bk.before && bk.enc && /Wrong/.test(bk.bad), 'encrypted backup restores everything; wrong passphrase is refused; file is unreadable without it');
  const ci = await p.evaluate(() => importCsv('Date,Description,Category,Amount,Note\n2026-09-03,BigBasket,Groceries,1450.50,weekly\n04/09/2026,"Uber, airport",Transport,620,\n2026-09-05,Refund,,0,'));
  ok(ci === 2 && await p.evaluate(() => S.exp.some(e => e.title === 'BigBasket' && e.amt === 1450.5 && e.cat === 'groc')), 'CSV import from another app (2 rows, categories mapped)');
  // payday month + FY + budget + reminders
  const md = await p.evaluate(() => { S.mstart = 25; const m = monthStart(new Date(2026, 9, 11)); const lbl = monthLabel(m); const inn = inMonth(new Date(2026, 8, 26).getTime(), m), out = inMonth(new Date(2026, 9, 26).getTime(), m); S.mstart = 1; return { d: m.getDate(), mo: m.getMonth(), lbl, inn, out }; });
  ok(md.d === 25 && md.mo === 8 && md.inn && !md.out && /25 Sept/.test(md.lbl), 'months can run payday to payday (25 Sep – 24 Oct)');
  await p.evaluate(() => { U.month = monthStart(new Date()); openBreakdown(); }); await p.waitForTimeout(500);
  ok(await p.evaluate(() => /Financial year/i.test(document.querySelector('#sheet').innerText) && document.querySelectorAll('#sheet .fyb').length === 12), 'month summary shows an April–March financial year view');
  await p.evaluate(() => closeSheet());
  await p.evaluate(() => { const t = Date.now(); [3, 2, 1].forEach(k => { const d = new Date(); d.setMonth(d.getMonth() - k); d.setDate(5); S.exp.push({ id: 'n' + k, amt: 649, title: 'Netflix', cat: 'subs', paid: 'me', sm: 50, ts: d.getTime(), src: 'sms', u: t }); }); scheduleReminders(); show('dues'); }); await p.waitForTimeout(300);
  ok(await p.evaluate(() => (window.__rem || []).some(r => /Netflix/.test(r.title)) && /Netflix/.test(document.querySelector('.page').innerText)), 'recurring Netflix shows up in Dues and gets a reminder');
  await p.evaluate(() => show('home')); await p.waitForTimeout(300);
  ok(await p.evaluate(() => !/budget/i.test(document.querySelector('.page').innerText) && !!document.querySelector('.rcpt .rc-t')), 'home has no budget — shows spend and the comparison receipt');
  ok(!errs.length, 'no JS errors ' + errs.join(';'));
  res.forEach(r => console.log(r[0], ' ', r[1])); console.log(res.filter(r => r[0] === 'PASS').length + '/' + res.length); await b.close();
})().catch(e => { console.error('CRASH', e); process.exit(1); });
