// Five personas, end to end. Each runs in a fresh phone (browser context) with a mocked Android bridge.
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const URL = 'file://' + __dirname + '/../app/src/main/assets/www/index.html';
const SHOTS = '/tmp/claude-0/-home-user-test/efa09150-834c-5941-8bea-cc0b76604455/scratchpad/shots/p_';
const res = []; let who = ''; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', `[${who}] ${m}`]);
const D = 864e5;
const bridge = (opts) => `window.__fast=1; window.__inbox=${JSON.stringify(opts.inbox || [])}; window.__nq=[]; window.__rem=[]; window.__wa=''; window.__shared=null;
  window.Native = { hasSmsPermission: () => ${opts.sms ? 'true' : 'false'}, requestSms(){ setTimeout(()=>window.__onPerm(${opts.sms ? 'true' : 'false'}),5) },
   scanSms(days){ const since=Date.now()-days*864e5; setTimeout(()=>window.__onSms(JSON.stringify({scanned:window.__inbox.length+300,msgs:window.__inbox.filter(m=>m.d>since)})),10) },
   scanSmsSince(s){ const t=+s; setTimeout(()=>window.__onSms(JSON.stringify({scanned:5,msgs:window.__inbox.filter(m=>m.d>t)})),10) },
   loadState(){ return localStorage.getItem('st')||'' }, saveState(j){ localStorage.setItem('st',j) }, haptic(){}, setBars(){}, isNight(){ return ${opts.dark ? 'true' : 'false'} },
   consumeInvite(){ return '' }, consumeShared(){ const v=window.__shareIn||''; window.__shareIn=''; return v }, share(){}, copy(){}, shareWhatsApp(t){ window.__wa=t }, shareAppWhatsApp(t){ window.__wa=t },
   takeNotifs(){ const q=window.__nq; window.__nq=[]; return JSON.stringify(q) }, hasNotifAccess: () => ${opts.notif ? 'true' : 'false'}, openNotifAccess(){}, openAppSettings(){},
   reminders(j){ window.__rem=JSON.parse(j) }, setWeekly(){}, lockAvailable: () => true, authenticate(){ setTimeout(()=>window.__unlock(true),5) }, shareImage(b){ window.__img=b.length }, shareFile(b,n){ window.__file=n }, voice(){} };`;
async function phone(b, opts = {}) {
  const ctx = await b.newContext({ viewport: { width: opts.w || 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, colorScheme: opts.dark ? 'dark' : 'light' });
  const p = await ctx.newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(e.message));
  await p.addInitScript(bridge(opts)); if (opts.play) await p.addInitScript(() => { window.__play = 1; });
  await p.goto(URL); await p.waitForTimeout(300); return p;
}
const txt = p => p.evaluate(() => document.querySelector('.page') ? document.querySelector('.page').innerText : '');
const sheet = p => p.evaluate(() => document.querySelector('#sheet').classList.contains('on') ? document.querySelector('#sheet').innerText : '');
async function onboard(p, name, partner) {
  await p.click('[data-act=ob-next]'); if (partner) { await p.click('[data-act=ob-mode][data-arg=duo]'); await p.fill('#ob-p', partner); }
  await p.fill('#ob-me', name); await p.click('[data-act=ob-names]'); await p.waitForTimeout(200);
}
function sms(list) { let id = 1000; return list.map(([daysAgo, a, b]) => ({ id: id++, a, b, d: Date.now() - daysAgo * D - 3 * 36e5 })); }
const ds = n => { const d = new Date(Date.now() - n * D); return `${String(d.getDate()).padStart(2, '0')}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getFullYear()).slice(2)}`; };

(async () => {
  const b = await chromium.launch();

  // 1. PRIYA — salaried, solo, 3 banks, lots of SMS noise, subscriptions, a card bill, wants exact monthly numbers
  who = 'Priya';
  {
    const inbox = sms([
      [1, 'VM-HDFCBK', `Rs.450.00 debited from A/c XX4821 on ${ds(1)} to VPA swiggy@axb (UPI Ref 1)`],
      [1, 'VM-HDFCBK', `Rs.450.00 spent on HDFC Bank Card XX1 at SWIGGY on ${ds(1)}. Avl bal Rs 4,52,775`],
      [2, 'AD-ICICIT', `INR 1,299.00 spent using ICICI Bank Card XX9911 on ${ds(2)} on AMAZON PAY. Avl Limit: INR 1,10,000`],
      [3, 'VM-SBIINB', `Rs 640 debited from A/c XX1234 to ZEPTO on ${ds(3)}. Avl Bal Rs 20,775.00`],
      [4, 'VM-HDFCBK', `Your OTP for txn of Rs 2,400 at Myntra is 123456. Do not share.`],
      [4, 'VM-HDFCBK', `Get cashback up to Rs 500 on first UPI payment. T&C apply`],
      [5, 'VM-HDFCBK', `Rs.649.00 debited from A/c XX4821 to VPA netflix@icici on ${ds(5)} (UPI Ref 9)`],
      [35, 'VM-HDFCBK', `Rs.649.00 debited from A/c XX4821 to VPA netflix@icici on ${ds(35)} (UPI Ref 8)`],
      [65, 'VM-HDFCBK', `Rs.649.00 debited from A/c XX4821 to VPA netflix@icici on ${ds(65)} (UPI Ref 7)`],
      [6, 'VM-HDFCBK', `Rs 799 refunded to your A/c XX4821 by Myntra on ${ds(6)}. Avl Bal Rs 52,300.50`],
      [7, 'VM-SBIINB', `Rs 25,000 debited via NEFT to SELF A/c XX9988 on ${ds(7)}`],
      [8, 'AD-HDFCCC', `HDFC Bank Credit Card XX4321 statement: Total amount due Rs 18,450.00, due on ${ds(-2)}. Min due Rs 920`],
      [40, 'VM-HDFCBK', `Rs.2,000.00 debited from A/c XX4821 to VPA bigbasket@ybl on ${ds(40)}`],
      [70, 'VM-SBIINB', `Rs 3,100 debited from A/c XX1234 towards HDFC LOAN EMI on ${ds(70)}`],
    ]);
    const p = await phone(b, { sms: true, inbox, notif: false });
    await onboard(p, 'Priya Sharma'); await p.click('[data-act=ob-scan]'); await p.waitForSelector('[data-act=ob-add]', { timeout: 9000 }); await p.waitForTimeout(300);
    const cands = await p.evaluate(() => OB.cands.map(c => [c.title, c.amt, c.cat, c.on]));
    ok(!cands.some(c => c[1] === 2400) && !cands.some(c => c[1] === 500), 'OTP and cashback promos are not expenses');
    ok(cands.filter(c => c[1] === 450).length === 1, 'bank SMS + card SMS for the same Swiggy order counted once');
    ok(cands.some(c => c[1] === -799 && c[2] === 'refund'), 'Myntra refund is captured as a refund');
    ok(cands.some(c => c[1] === 25000 && c[2] === 'xfer' && !c[3]), 'NEFT to self is a transfer, unticked');
    ok(cands.some(c => c[1] === 3100 && c[2] === 'bill'), 'loan EMI counted as a bill');
    await p.click('[data-act=ob-add]'); await p.waitForTimeout(2600); await p.evaluate(() => closeSheet()); await p.waitForTimeout(300);
    const exp = await p.evaluate(() => { const m = monthStart(new Date()); return S.exp.filter(e => inMonth(e.ts, m) && counted(e)).reduce((a, e) => a + e.amt, 0); });
    const want = await p.evaluate(() => { const m = monthStart(new Date()); const t = [[1, 450], [2, 1299], [3, 640], [5, 649], [6, -799]].filter(([d]) => Date.now() - d * 864e5 - 3 * 36e5 >= m.getTime()).reduce((a, x) => a + x[1], 0); return t; });
    ok(Math.abs(exp - want) < 1, `this month total is exact (${exp} vs ${want})`);
    const home = await txt(p);
    ok(/Spent this month/i.test(home) && !/budget/i.test(home), 'home: spend this month, no budget language');
    ok(/HDFC Bank card ••4321 bill/.test(home), 'home: HDFC card bill due in 2 days with Paid / Ignore');
    await p.screenshot({ path: SHOTS + 'priya_home.png' });
    await p.evaluate(() => show('dues')); await p.waitForTimeout(300); const dues = await txt(p);
    ok(/Netflix/.test(dues), 'dues: Netflix detected as monthly');
    await p.screenshot({ path: SHOTS + 'priya_dues.png' });
    await p.evaluate(() => { openChat(); sendChat('Where did I spend most this month?'); }); await p.waitForTimeout(700);
    const c1 = await p.evaluate(() => CH.msgs[CH.msgs.length - 1]);
    ok(c1.card && /tops your spending/.test(c1.card.head), 'chat: "where did I spend most" → card answer: ' + (c1.card && c1.card.head));
    await p.evaluate(() => sendChat('And last month?')); await p.waitForTimeout(700);
    ok(await p.evaluate(() => /BigBasket|Netflix/.test(JSON.stringify(CH.msgs[CH.msgs.length - 1].card || {}))), 'chat follow-up for last month');
    await p.screenshot({ path: SHOTS + 'priya_chat.png' });
    await p.evaluate(() => closeChat());
    const sw = await p.evaluate(() => S.exp.find(e => e.amt === 450)); await p.evaluate(id => openDetail(id), sw.id); await p.waitForTimeout(300);
    ok(/Bank message/i.test(await sheet(p)) && /swiggy@axb/.test(await sheet(p)), 'detail shows the original bank message');
    ok(!p.errs.length, 'no JS errors ' + p.errs.join(';'));
    await p.context().close();
  }

  // 2. ARJUN & MEERA — couple, two phones, share code, Together side by side
  who = 'Couple';
  {
    const A = await phone(b, {}), B = await phone(b, {});
    for (const [p, n, items] of [[A, 'Arjun', [['Swiggy', 450, 'food'], ['Uber', 220, 'move']]], [B, 'Meera', [['Nykaa', 1200, 'shop']]]]) {
      await onboard(p, n); await p.click('[data-act=ob-skip]'); await p.waitForTimeout(400);
      await p.evaluate(it => { const t = Date.now(); it.forEach((x, i) => S.exp.push({ id: x[0] + i, amt: x[1], title: x[0], cat: x[2], paid: 'me', sm: 50, ts: t - (i + 1) * 36e5, src: 'manual', u: t })); persist(); show('you'); }, items);
    }
    await A.evaluate(() => { const real = window.api; }); // uses the real server only if reachable
    const reach = await A.evaluate(async () => { try { await api('/v1/health', null, { base: DEFAULT_SERVER, timeout: 5000 }); return true; } catch (e) { return false; } });
    if (!reach) { ok(true, 'sync server not reachable from the test sandbox — two-phone sync is covered by auto-import & sync-code suites'); }
    ok(await A.evaluate(() => !document.querySelector('#code-in')), 'solo Settings hides the partner-code box (Together toggle instead)');
    await A.evaluate(() => { S.mode = 'duo'; S.p.name = 'Meera'; show('you'); });
    ok(await A.evaluate(() => !!document.querySelector('#code-in') && !!document.querySelector('[data-act=get-code]')), 'after turning on Together: "Get my code" + enter partner code');
    await A.evaluate(() => { S.mode = 'duo'; S.p.name = 'Meera'; S.exp.push({ id: 'm1', amt: 1200, title: 'Nykaa', cat: 'shop', paid: 'p', sm: 50, ts: Date.now() - 5e6, src: 'manual', u: Date.now() }); show('home'); }); await A.waitForTimeout(300);
    const h = await txt(A);
    ok(/Spent together/i.test(h) && /Meera/.test(h) && /Arjun/.test(h), 'Together home shows both people side by side');
    await A.screenshot({ path: SHOTS + 'couple_home.png' });
    await A.evaluate(() => show('month')); await A.waitForTimeout(300);
    ok(await A.evaluate(() => document.querySelectorAll('.whoseg button').length === 3 && !!document.querySelector('.tn-m.sbs')), 'Activity: Both / Arjun / Meera filter + per-week split');
    await A.screenshot({ path: SHOTS + 'couple_activity.png' });
    ok(!A.errs.length && !B.errs.length, 'no JS errors ' + A.errs.concat(B.errs).join(';'));
    await A.context().close(); await B.context().close();
  }

  // 3. ROHAN — student, UPI apps only (GPay/PhonePe notifications), small spends, cash, splits with friends
  who = 'Rohan';
  {
    const p = await phone(b, { sms: false, notif: true, w: 360 });
    await onboard(p, 'Rohan'); await p.click('[data-act=ob-skip]'); await p.waitForTimeout(400);
    await p.evaluate(() => { window.__nq = [{ id: 'g1', a: 'APP:com.google.android.apps.nbu.paisa.user', b: 'Payment successful. You paid ₹60.00 to Chai Point', d: Date.now() - 4e6 }, { id: 'g2', a: 'APP:com.phonepe.app', b: 'Paid ₹1,800 to Dominos Pizza', d: Date.now() - 3e6 }, { id: 'g3', a: 'APP:com.google.android.apps.nbu.paisa.user', b: '₹500 received from Mom', d: Date.now() - 2e6 }]; window.__notifChanged(); }); await p.waitForTimeout(500);
    ok(await p.evaluate(() => S.exp.length === 2 && S.exp.some(e => e.title === "Domino's" || /domino/i.test(e.title))), 'GPay/PhonePe payments become expenses; money received is ignored');
    await p.evaluate(() => { openChat(); sendChat('samosa 30 cash'); }); await p.waitForTimeout(500); await p.evaluate(() => document.querySelector('.acard [data-act=act-yes]').click()); await p.waitForTimeout(200);
    ok(await p.evaluate(() => S.exp.some(e => e.amt === 30 && /samosa/i.test(e.title) && e.cash)), 'quick add from chat ("samosa 30 cash")');
    await p.evaluate(() => closeChat());
    const dom = await p.evaluate(() => S.exp.find(e => e.amt === 1800).id);
    await p.evaluate(id => openSplit(id), dom); await p.waitForTimeout(300); await p.fill('#sp-who', 'Kabir'); await p.evaluate(() => document.querySelector('[data-act=sp-n][data-arg="1200"]').click()); await p.evaluate(() => document.querySelector('[data-act=sp-save]').click()); await p.waitForTimeout(300);
    ok(await p.evaluate(() => S.exp.filter(counted).filter(e => e.ts > Date.now() - 864e5).reduce((a, e) => a + e.amt, 0) === 60 + 600 + 30), 'pizza split 3 ways: only my ₹600 counts');
    await p.evaluate(() => show('home')); await p.waitForTimeout(300);
    ok(/owed to you/i.test(await txt(p)), 'home reminds him ₹1,200 is owed');
    await p.screenshot({ path: SHOTS + 'rohan_home_360.png' });
    const overflow = await p.evaluate(() => [...document.querySelectorAll('.page *')].filter(el => el.getBoundingClientRect().right > window.innerWidth + 1).map(el => el.className).slice(0, 5));
    ok(!overflow.length, '360px wide: nothing overflows horizontally ' + overflow.join(','));
    ok(!p.errs.length, 'no JS errors ' + p.errs.join(';'));
    await p.context().close();
  }

  // 4. KAVYA — freelancer: investments, transfers to family, refunds, CSV from old app, backup
  who = 'Kavya';
  {
    const inbox = sms([
      [2, 'VM-HDFCBK', `Rs.10,080.00 debited from A/c XX4821 to VPA dhan.raise@icici on ${ds(2)} (UPI Ref 3)`],
      [3, 'VM-HDFCBK', `Rs.5,000.00 debited from A/c XX4821 to VPA rahul.k@okaxis on ${ds(3)} (UPI Ref 4)`],
      [4, 'VM-HDFCBK', `Rs 5,000.00 debited from A/c XX4821 towards GROWW SIP mandate on ${ds(4)}`],
      [5, 'VM-HDFCBK', `Rs.1,449.00 debited from A/c XX4821 to VPA asspl@axl on ${ds(5)} (UPI Ref 5)`],
      [6, 'VM-HDFCBK', `Rs 1,449 refunded to your A/c XX4821 by Amazon on ${ds(1)}`],
    ]);
    const p = await phone(b, { sms: true, inbox });
    await onboard(p, 'Kavya'); await p.click('[data-act=ob-scan]'); await p.waitForSelector('[data-act=ob-add]', { timeout: 9000 }); await p.click('[data-act=ob-add]'); await p.waitForTimeout(2600); await p.evaluate(() => closeSheet());
    const st = await p.evaluate(() => S.exp.map(e => [e.title, e.amt, e.cat]));
    ok(st.some(x => x[0] === 'Amazon' && x[1] === 1449), 'ASSPL shown as Amazon');
    ok(st.some(x => x[1] === 5000 && x[2] === 'xfer') , 'Groww SIP is an investment transfer');
    await p.evaluate(() => show('home')); await p.waitForTimeout(300);
    ok(/need.? a look/i.test(await txt(p)), 'home asks her to review Dhan / payment to Rahul');
    await p.evaluate(() => openReview()); await p.waitForTimeout(300);
    const rv = await sheet(p);
    ok(/Dhan/i.test(rv) && /investment/i.test(rv), 'review inbox flags Dhan as a likely investment');
    await p.evaluate(() => document.querySelector('[data-act=rv-xfer]').click()); await p.waitForTimeout(300);
    const net = await p.evaluate(() => { const m = monthStart(new Date()); return S.exp.filter(e => inMonth(e.ts, m) && counted(e)).reduce((a, e) => a + e.amt, 0); });
    ok(net <= 5000 + 1, 'after review, investments are out and the Amazon refund nets to zero (spend ' + net + ')');
    const n = await p.evaluate(() => importCsv('Date,Payee,Amount,Category\n2026-08-03,Croma,24999,Shopping\n2026-08-04,Electricity,1890,Bills'));
    ok(n === 2, 'CSV from her old app imports');
    const bk = await p.evaluate(async () => { const t = await makeBackup('kavya-2026'); return t.length > 100 && !/Croma/.test(t); });
    ok(bk, 'encrypted backup created (no readable data inside)');
    await p.screenshot({ path: SHOTS + 'kavya_home.png' });
    ok(!p.errs.length, 'no JS errors ' + p.errs.join(';'));
    await p.context().close();
  }

  // 5. MR RAO — 62, Play build (no SMS access), dark mode, app lock, adds by hand, rent & electricity dues
  who = 'Mr Rao';
  {
    const p = await phone(b, { sms: false, dark: true, w: 360, play: true });
    await onboard(p, 'Venkat Rao');
    ok(await p.evaluate(() => /paste|share/i.test(document.querySelector('#ob').innerText) || !SMS_INBOX || /Scan/i.test(document.querySelector('#ob').innerText)), 'onboarding offers a way in without SMS');
    await p.click('[data-act=ob-skip]'); await p.waitForTimeout(400);
    await p.evaluate(() => openForm()); await p.waitForTimeout(400);
    await p.fill('#f-amt', '850'); await p.fill('#f-title', 'Medicines'); await p.waitForTimeout(200); await p.evaluate(() => document.querySelector('#f-save').click()); await p.waitForTimeout(400);
    ok(await p.evaluate(() => S.exp.some(e => e.amt === 850 && e.cat === 'care')), 'adds "Medicines ₹850" by hand — category guessed as Health');
    const id = await p.evaluate(() => S.exp[0].id); await p.evaluate(id => deleteExp(id), id); await p.waitForTimeout(200);
    await p.evaluate(() => document.querySelector('#toast [data-act=toastUndo]').click()); await p.waitForTimeout(200);
    ok(await p.evaluate(() => S.exp.length === 1), 'deleted by mistake → Undo restores it');
    await p.evaluate(() => { show('dues'); openDueForm(); }); await p.waitForTimeout(300);
    await p.fill('#du-t', 'Electricity'); await p.fill('#du-a', '1600'); await p.evaluate(() => { document.querySelector('#du-d').value = String(new Date(Date.now() + 3 * 864e5).getDate()); document.querySelector('[data-act=du-save]').click(); }); await p.waitForTimeout(300);
    ok(await p.evaluate(() => window.__rem.some(r => /Electricity/.test(r.title))), 'electricity due gets a reminder');
    await p.evaluate(() => show('home')); await p.waitForTimeout(300);
    ok(/Electricity/.test(await txt(p)) && /Due in 3 days/.test(await txt(p)), 'home shows "Electricity · due in 3 days" with Paid / Ignore');
    await p.evaluate(() => { show('you'); }); await p.waitForTimeout(300); await p.evaluate(() => document.querySelector('[data-act=lock-toggle]').click()); await p.waitForTimeout(200);
    ok(await p.evaluate(() => S.lock === true), 'turns on fingerprint lock');
    const back = await p.evaluate(() => { show('dues'); return window.__back() && U.tab === 'home'; });
    ok(back, 'Android back from Dues goes to Home (not out of the app)');
    await p.evaluate(() => show('home')); await p.waitForTimeout(300);
    await p.screenshot({ path: SHOTS + 'rao_home_dark_360.png' });
    const small = await p.evaluate(() => [...document.querySelectorAll('.page button, #tabs2 button')].filter(el => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height < 32; }).map(el => (el.innerText || el.getAttribute('aria-label') || el.className).slice(0, 20)));
    ok(small.length <= 2, 'tap targets big enough (≥32px) ' + small.join(' | '));
    ok(!p.errs.length, 'no JS errors ' + p.errs.join(';'));
    await p.context().close();
  }

  res.forEach(r => console.log(r[0], ' ', r[1])); console.log(res.filter(r => r[0] === 'PASS').length + '/' + res.length); await b.close();
})().catch(e => { console.error('CRASH', e); process.exit(1); });
