// Function test: SMS → expense → partner sync on two phones (local sync server), plus solo mode.
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const URL = 'file:///tmp/claude-0/-home-user-test/efa09150-834c-5941-8bea-cc0b76604455/scratchpad/www-test/index.html';
const res = []; const ok = (c, m) => res.push([c ? 'PASS' : 'FAIL', m]);
(async () => {
  const b = await chromium.launch(); const errs = [];
  const mk = async (name) => { const p = await (await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage(); p.on('pageerror', e => errs.push(name + ': ' + e.message));
    await p.addInitScript(() => { window.__fast = 1; window.__q = []; window.__inbox = []; const reply = s => setTimeout(() => window.__onSms && window.__onSms(JSON.stringify({ scanned: window.__inbox.length, msgs: window.__inbox.filter(m => m.d > s) })), 80);
      window.Native = { hasSmsPermission: () => true, requestSms() {}, loadState() { return ''; }, saveState() {}, haptic() {}, setBars() {}, isNight() { return false; }, consumeInvite() { return ''; }, consumeShared() { return ''; }, share() {}, copy() {}, reminders() {}, hasNotifAccess: () => true, takeNotifs() { const v = JSON.stringify(window.__q); window.__q = []; return v; }, scanSmsSince(s) { reply(+s); }, scanSms(d) { reply(Date.now() - d * 864e5); } }; });
    await p.goto(URL); await p.waitForTimeout(300);
    await p.evaluate(n => { S.onboarded = true; S.me.name = n; S.mode = 'solo'; S.smsLast = Date.now() - 36e5; S.dueV2 = 1; S.exp = []; document.querySelector('#ob').classList.remove('on'); persist(); show('home'); }, name); await p.waitForTimeout(200); return p; };
  const sync = async (...ps) => { for (let i = 0; i < 3; i++) for (const p of ps) { await p.evaluate(async () => { await syncNow(); while (syncing) await new Promise(r => setTimeout(r, 50)); }); } };
  const A = await mk('Sri'), B = await mk('Gunjan');
  const sms = (p, id, body, ago = 6e4) => p.evaluate(([id, body, ago]) => { window.__q.push({ id, a: 'SMS:AD-HDFCBK', b: body, d: Date.now() - ago }); window.__notifChanged(); }, [id, body, ago]);

  // ---- SOLO: works on its own, nothing leaves the phone ----
  await sms(A, 's1', 'Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 12/10/26 Ref 1 Not You? Call 18002586161'); await A.waitForTimeout(500);
  const solo = await A.evaluate(() => ({ n: S.exp.length, e: S.exp[0] && [S.exp[0].title, S.exp[0].amt, S.exp[0].paid], sync: !!(S.sync && S.sync.token), split: !!document.querySelector('.rc-p'), who: !!document.querySelector('[data-act=who-pick]') }));
  ok(solo.n === 1 && solo.e[0] === 'Swiggy' && solo.e[1] === 450 && solo.e[2] === 'me', 'solo: a bank SMS becomes an expense on its own (' + JSON.stringify(solo.e) + ')');
  ok(!solo.sync && !solo.split, 'solo: no sync connection and no partner split on Home');
  await A.evaluate(() => { U.av = 'story'; show('month'); }); ok(await A.evaluate(() => !document.querySelector('[data-act=who-pick]') && !document.querySelector('.apeople')), 'solo: Activity has no partner filter or split');

  // ---- PAIR: Together mode, share code, both tap Start ----
  await A.evaluate(() => { S.mode = 'duo'; S.p.name = 'Gunjan'; show('you'); }); await B.evaluate(() => { S.mode = 'duo'; S.p.name = 'Sri'; show('you'); });
  await A.click('[data-act=get-code]'); await A.waitForTimeout(1500); const code = await A.evaluate(() => S.sync && S.sync.code);
  await B.fill('#code-in', code); await B.click('#code-go'); await B.waitForTimeout(1500); await A.evaluate(() => syncNow()); await A.waitForTimeout(800);
  ok(await B.evaluate(() => S.exp.length === 0), 'before both tap Start, nothing is shared (privacy)');
  await A.evaluate(() => document.querySelector('[data-act=sync-start]').click()); await B.evaluate(() => document.querySelector('[data-act=sync-start]').click()); await sync(A, B);
  const p1 = await B.evaluate(() => { const e = S.exp.find(x => x.amt === 450); return e && [e.title, e.paid, e.raw || '']; });
  ok(p1 && p1[0] === 'Swiggy' && p1[1] === 'p', "after Start, Sri's earlier Swiggy shows on Gunjan's phone as Sri's (" + JSON.stringify(p1 && p1.slice(0, 2)) + ')');
  ok(p1 && p1[2] === '', "the bank SMS text itself is never sent to the partner");

  // ---- LIVE: a new SMS arriving on each phone reaches the other ----
  await sms(B, 's2', 'Rs.1,299.00 debited from A/c XX9912 to VPA amazon@apl AMAZON on 12-10-26', 3e4); await B.waitForTimeout(400); await sync(B, A);
  ok(await A.evaluate(() => { const e = S.exp.find(x => x.amt === 1299); return e && e.paid === 'p' && /amazon/i.test(e.title); }), "Gunjan's live SMS (₹1,299 Amazon) reaches Sri, under Gunjan's name");
  await sms(A, 's3', 'Dear UPI user A/C X1234 debited by 640.0 on date 12Oct26 trf to ZEPTO Refno 9. If not u? call 1800111109. -SBI', 2e4); await A.waitForTimeout(400); await sync(A, B);
  ok(await B.evaluate(() => S.exp.some(x => x.amt === 640 && x.paid === 'p')), "Sri's live SMS (₹640 Zepto) reaches Gunjan");
  const tot = async p => p.evaluate(() => { const m = monthStart(new Date()), l = S.exp.filter(e => inMonth(e.ts, m) && counted(e)); return [l.reduce((a, e) => a + e.amt, 0), l.filter(e => e.paid === 'me').reduce((a, e) => a + e.amt, 0)]; });
  const [ta, ma] = await tot(A), [tb, mb] = await tot(B);
  ok(ta === tb && ta === 2389, `both phones agree on the month total (Sri ${ta}, Gunjan ${tb})`);
  ok(ma === 1090 && mb === 1299 && ma + mb === ta, `the "who paid" split is mirrored correctly (Sri sees self ₹${ma}, Gunjan sees self ₹${mb})`);
  await A.evaluate(() => { U.month = monthStart(new Date()); show('home'); }); const split = await A.evaluate(() => (document.querySelector('.rc-p') || {}).innerText || '');
  ok(/Sri\s*₹1,090/.test(split) && /Gunjan\s*₹1,299/.test(split), 'Home shows both people side by side: ' + split.replace(/\n/g, ' '));

  // ---- EDITS: category fixes, edits and deletes sync both ways ----
  await A.evaluate(() => { const e = S.exp.find(x => x.amt === 1299); e.cat = 'gift'; e.ok = 1; touch(e); save(); }); await sync(A, B);
  ok(await B.evaluate(() => S.exp.find(x => x.amt === 1299).cat === 'gift'), "Sri recategorising Gunjan's payment (e.g. via Quick review / AI Apply) updates Gunjan's phone");
  await B.evaluate(() => { const e = S.exp.find(x => x.amt === 640); e.title = 'Zepto groceries'; e.amt = 650; touch(e); save(); }); await sync(B, A);
  ok(await A.evaluate(() => S.exp.some(x => x.amt === 650 && x.title === 'Zepto groceries') && !S.exp.some(x => x.amt === 640)), 'an edit on one phone (name + amount) replaces the old version on the other');
  await A.evaluate(() => { const e = S.exp.find(x => x.amt === 450); removeRec('exp', e.id); save(); }); await sync(A, B);
  ok(await B.evaluate(() => !S.exp.some(x => x.amt === 450)), 'deleting on one phone deletes on the other');
  await sync(A, B); ok(await B.evaluate(() => !S.exp.some(x => x.amt === 450)), 'a deleted payment does not come back on the next sync');

  // ---- OFFLINE + CONFLICT ----
  await B.evaluate(() => { window.__realFetch = window.fetch; window.fetch = () => Promise.reject(new Error('offline')); });
  await sms(B, 's4', 'Rs.210.00 debited from A/c XX9912 to VPA uber@axis UBER INDIA on 12-10-26', 1e4); await B.waitForTimeout(400); await B.evaluate(() => syncNow().catch(() => {})); await B.waitForTimeout(400);
  ok(await B.evaluate(() => S.exp.some(x => x.amt === 210)), 'offline: a new SMS is still added on the phone');
  await B.evaluate(() => { window.fetch = window.__realFetch; }); await sync(B, A);
  ok(await A.evaluate(() => S.exp.some(x => x.amt === 210 && x.paid === 'p')), 'back online: the offline expense syncs to the partner');
  await A.evaluate(() => { const e = S.exp.find(x => x.amt === 210); e.cat = 'move'; e.note = 'from A'; e.u = Date.now(); touch(e); save(); });
  await B.waitForTimeout(30); await B.evaluate(() => { const e = S.exp.find(x => x.amt === 210); e.note = 'from B (later)'; touch(e); save(); });
  await sync(A, B, A, B);
  const notes = [await A.evaluate(() => S.exp.find(x => x.amt === 210).note), await B.evaluate(() => S.exp.find(x => x.amt === 210).note)];
  ok(notes[0] === notes[1] && notes[0] === 'from B (later)', 'both edit the same payment: both phones settle on the latest edit (' + notes.join(' / ') + ')');
  const n = [await A.evaluate(() => S.exp.length), await B.evaluate(() => S.exp.length)];
  ok(n[0] === n[1], `final check: both phones hold the same ${n[0]} payments`);
  ok(!errs.length, 'no JS errors ' + errs.join('; '));
  await b.close();
  res.forEach(x => console.log(x[0], x[1])); const f = res.filter(x => x[0] === 'FAIL').length; console.log(`\n${res.length - f}/${res.length} passed`); process.exit(f ? 1 : 0);
})();
