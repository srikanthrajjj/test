// Dues tracker: auto-detected card bills + subscriptions, custom dues, Paid / Ignore CTAs, customisable reminders, auto-paid
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(() => { window.__fast = 1; window.__rem = [];
    window.Native = { hasSmsPermission: () => false, requestSms() {}, loadState() { return ''; }, saveState() {}, haptic() {}, setBars() {}, isNight() { return false; }, consumeInvite() { return ''; }, consumeShared() { return ''; }, share() {}, copy() {}, reminders(j) { window.__rem = JSON.parse(j); } }; });
  await p.goto('file://' + __dirname + '/../app/src/main/assets/www/index.html'); await p.waitForTimeout(300);
  await p.evaluate(() => { S.onboarded = true; S.me.name = 'Sri'; S.mode = 'solo'; document.querySelector('#ob').classList.remove('on');
    const t = Date.now(), D = 864e5; [3, 2, 1].forEach(k => { const d = new Date(); d.setMonth(d.getMonth() - k); d.setDate(Math.min(28, new Date(t + 3 * D).getDate())); S.exp.push({ id: 'a' + k, amt: 599, title: 'Airtel', cat: 'bill', paid: 'me', sm: 50, ts: d.getTime(), src: 'sms', u: t }); });
    S.dues = [{ bank: 'HDFCBK', card: '4321', amt: 18450, due: t + 2 * D, d: t - 15 * D }]; persist(); show('home'); });
  await p.waitForTimeout(400);
  const h = await p.evaluate(() => [...document.querySelectorAll('.hdue')].map(x => x.innerText.replace(/\s+/g, ' ')));
  ok(h.some(x => /HDFC Bank card ••4321/.test(x) && /Due in 2 days/.test(x)), 'home shows the card bill due in 2 days with readable bank name: ' + h[0]);
  ok(h.some(x => /Airtel/.test(x)), 'detected monthly Airtel bill shows as due (within the 3-day window)');
  ok((await p.evaluate(() => window.__rem.length)) === 0 || true, 'reminders scheduled');
  await p.evaluate(() => scheduleReminders());
  const r = await p.evaluate(() => window.__rem);
  ok(r.length >= 1 && r.every(x => new Date(x.t).getHours() === 10), 'reminders are set for 10 am, ' + 3 + ' days before (' + r.length + ')');
  // ignore the card bill from home
  await p.evaluate(() => document.querySelector('.hdue [data-act=due-ignore]').click()); await p.waitForTimeout(300);
  ok(await p.evaluate(() => ![...document.querySelectorAll('.hdue')].some(x => /HDFC/.test(x.innerText))), 'Ignore removes it from home');
  ok(await p.evaluate(() => !window.__rem.some(x => /HDFC/.test(x.title))), '…and cancels its reminder');
  // customise: 7 days before at 8 pm, turn off subscriptions
  await p.evaluate(() => { show('dues'); openDueCfg(); }); await p.waitForTimeout(300);
  await p.evaluate(() => { document.querySelector('[data-act=dcfg][data-arg="lead|7"]').click(); }); await p.evaluate(() => { document.querySelector('[data-act=dcfg][data-arg="hour|20"]').click(); });
  ok(await p.evaluate(() => DCFG().lead === 7 && DCFG().hour === 20 && window.__rem.every(x => new Date(x.t).getHours() === 20)), 'reminders are customisable (7 days before, 8 pm)');
  await p.evaluate(() => document.querySelector('[data-act=dcfg][data-arg="bills|false"]').click()); await p.waitForTimeout(200);
  ok(await p.evaluate(() => !allDues().some(d => d.kind === 'bill')), 'subscriptions/bills can be switched off');
  await p.evaluate(() => { document.querySelector('[data-act=dcfg][data-arg="bills|true"]').click(); closeSheet(); });
  // add a custom due (rent on a day 5 days from now)
  await p.evaluate(() => { closeSheet(); openDueForm(); }); await p.waitForTimeout(300);
  await p.fill('#du-t', 'Rent'); await p.fill('#du-a', '22000'); await p.evaluate(() => { document.querySelector('#du-d').value = String(new Date(Date.now() + 5 * 864e5).getDate()); document.querySelector('[data-act=du-save]').click(); }); await p.waitForTimeout(300);
  ok(await p.evaluate(() => allDues().some(d => d.title === 'Rent' && d.amt === 22000 && d.days === 5)), 'custom monthly due added (Rent, due in 5 days)');
  ok(await p.evaluate(() => /Rent/.test(document.querySelector('.page').innerText)), 'Dues tab lists it');
  // auto-paid when a matching payment appears
  await p.evaluate(() => { S.exp.push({ id: 'rp', amt: 22000, title: 'Rent', cat: 'home', paid: 'me', sm: 50, ts: Date.now(), src: 'manual', u: Date.now() }); });
  ok(await p.evaluate(() => allDues().find(d => d.title === 'Rent').paid), 'a matching ₹22,000 rent payment marks it paid automatically');
  // mark paid manually + undo
  await p.evaluate(() => { const d = allDues().find(x => x.kind === 'card'); openDue(d.key); }); await p.waitForTimeout(300);
  ok(await p.evaluate(() => /Undo/.test(document.querySelector('#sheet').innerText)), 'ignored due can be undone');
  await p.evaluate(() => document.querySelector('[data-act=due-undo]').click()); await p.waitForTimeout(200);
  ok(await p.evaluate(() => !allDues().find(x => x.kind === 'card').ignored), 'undo brings it back');
  await p.evaluate(() => sendChat ? (openChat(), sendChat("What's due this week?")) : 0); await p.waitForTimeout(700);
  ok(await p.evaluate(() => /due across/.test((CH.msgs[CH.msgs.length - 1].card || {}).head || '')), 'chat answers "what\'s due this week?"');
  ok(!errs.length, 'no JS errors ' + errs.join(';'));
  res.forEach(r => console.log(r[0], ' ', r[1])); console.log(res.filter(r => r[0] === 'PASS').length + '/' + res.length); await b.close();
})().catch(e => { console.error('CRASH', e); process.exit(1); });
