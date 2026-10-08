const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const URL='file:///tmp/claude-0/-home-user-test/efa09150-834c-5941-8bea-cc0b76604455/scratchpad/www-test/index.html', SERVER='http://127.0.0.1:8788';
const res=[]; const ok=(c,m)=>{res.push([c?'PASS':'FAIL',m])};
(async()=>{ const b=await chromium.launch();
 const mk=async(withSms)=>{ const ctx=await b.newContext({viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true,locale:'en-IN'}); const p=await ctx.newPage(); p.errs=[]; p.on('pageerror',e=>p.errs.push(e.message));
  await p.addInitScript(([u,w])=>{ window.__fast=1; try{localStorage.setItem('nest.url',u)}catch(e){}
    if(w){ window.__inbox=[]; window.Native={ hasSmsPermission:()=>true, requestSms(){setTimeout(()=>window.__onPerm&&window.__onPerm(true),10)},
      scanSms(days){ const since=Date.now()-days*864e5; setTimeout(()=>window.__onSms(JSON.stringify({scanned:window.__inbox.length,msgs:window.__inbox.filter(m=>m.d>since)})),20)},
      scanSmsSince(s){ const t=+s; setTimeout(()=>window.__onSms(JSON.stringify({scanned:window.__inbox.length,msgs:window.__inbox.filter(m=>m.d>t)})),20)},
      loadState(){return localStorage.getItem('nest.native')||''}, saveState(j){localStorage.setItem('nest.native',j)}, haptic(){}, setBars(){}, isNight(){return false}, consumeInvite(){return ''}, consumeShared(){return ''}, share(){}, copy(){}, shareWhatsApp(){} }; } },[SERVER,withSms]);
  await p.goto(URL); await p.waitForTimeout(600); return p; };
 const A=await mk(true), B=await mk(false); const day=864e5, now=Date.now();
 // old inbox: normal spends + a big NEFT self-transfer + a bank-and-card double SMS + SIP + a large real-looking payment
 await A.evaluate(([now,day])=>{ let id=1; const m=(d,b)=>window.__inbox.push({id:id++,a:'VM-BANK',b,d:now-d*day});
   m(20,'Rs.450.00 debited from A/c XX4821 to VPA swiggy@axb (UPI Ref 1)'); m(20,'Rs.450.00 spent on Card XX1 at SWIGGY on 02-07-26. Avl bal Rs 4,52,775'); // same purchase twice
   m(19,'Rs.380 debited a/c XX1234 UPI to Swiggy Ref 2'); m(10,'Rs 640 debited from A/c XX1234 to ZEPTO. Avl Bal Rs 4,52,775.00'); m(9,'Avl Bal Rs 4,52,775.00. Rs 210.00 debited from A/c to Uber India Systems');
   m(8,'Rs 4,50,000 debited via NEFT to SELF A/c XX9988'); m(7,'Rs 5,000.00 debited from A/c XX1234 towards GROWW SIP mandate'); m(6,'Rs 62,000 debited to Sandeep Kumar UPI Ref 9'); m(3,'Your OTP for txn of Rs 2,400 is 123456'); },[now,day]);
 await A.click('[data-act=ob-next]'); await A.fill('#ob-me','Sri'); await A.fill('#ob-p','Gun'); await A.click('[data-act=ob-names]'); await A.click('[data-act=ob-scan]'); await A.waitForSelector('[data-act=ob-add]',{timeout:9000});
 const rv=await A.evaluate(()=>OB.cands.map(c=>[c.title,c.amt,c.cat,c.on,!!c.big]));
 console.log(JSON.stringify(rv));
 ok(rv.filter(r=>r[1]===450).length===1,'bank+card double SMS for the same purchase counted once');
 ok(rv.find(r=>r[1]===450000)[3]===false&&rv.find(r=>r[1]===450000)[2]==='xfer','₹4,50,000 NEFT-to-self is a Transfer and starts unticked');
 ok(rv.find(r=>r[1]===5000)[2]==='xfer'&&!rv.find(r=>r[1]===5000)[3],'SIP is a Transfer, unticked');
 ok(rv.find(r=>r[1]===62000)[3]===false&&rv.find(r=>r[1]===62000)[4]===true,'₹62,000 payment to a person is flagged Large and unticked');
 ok(rv.find(r=>r[1]===640)[1]===640&&rv.find(r=>r[1]===210)[1]===210,'balances are never mistaken for the amount (640, 210)');
 ok(!rv.some(r=>r[1]===2400),'OTP message ignored');
 await A.click('[data-act=ob-add]'); await A.waitForTimeout(2800); await A.evaluate(()=>closeSheet());
 const total0=await A.evaluate(()=>S.exp.filter(counted).reduce((a,e)=>a+e.amt,0)); ok(total0===450+380+640+210,'month total counts only the real spending ('+total0+')');
 ok(await A.evaluate(()=>S.smsLast>0),'scan cursor stored for automatic catch-up');
 // new SMS arrives -> auto
 const before=await A.evaluate(()=>S.exp.length);
 await A.evaluate(()=>{ window.__inbox.push({id:100,a:'VM-BANK',b:'Rs.299.00 debited from A/c XX4821 to VPA netflix@axb (UPI Ref 3)',d:Date.now()}); window.__inbox.push({id:101,a:'VM-BANK',b:'Rs.299.00 spent on Card XX1 at NETFLIX on 03-10-26',d:Date.now()+1000}); window.__smsChanged(); }); await A.waitForTimeout(900);
 ok(await A.evaluate(b=>S.exp.length===b+1,before),'new SMS (and its duplicate) added exactly once automatically');
 await A.evaluate(()=>window.__smsChanged()); await A.waitForTimeout(600); ok(await A.evaluate(b=>S.exp.length===b+1,before),'running auto-import again adds nothing (no duplicates)');
 // pair, sync
 await A.evaluate(()=>{ show('month'); }); await A.waitForTimeout(400); const code=await (async()=>{ await A.click('[data-act=quick-invite]'); await A.waitForFunction(()=>S.sync&&S.sync.code,null,{timeout:8000}); return A.evaluate(()=>S.sync.code); })(); await A.click('#sheet .x');
 await B.evaluate(([c,s])=>handleInvite(c,s),[code,SERVER]); await B.waitForSelector('#pr-name',{timeout:8000}); await B.fill('#pr-name','Gun'); await B.click('#pr-go'); await B.waitForTimeout(2500); await B.click('[data-act=ob-skip]'); await B.waitForTimeout(1000);
 for(let i=0;i<3;i++){ await A.evaluate(()=>syncNow()); await B.evaluate(()=>syncNow()); await B.waitForTimeout(500); }
 const cnt=await B.evaluate(()=>S.exp.length), cntA=await A.evaluate(()=>S.exp.length); ok(cnt===cntA,'partner receives every imported + auto-added expense ('+cnt+'/'+cntA+')');
 // arrives after sync -> partner gets it live
 await A.evaluate(()=>{ window.__inbox.push({id:200,a:'VM-BANK',b:'Rs.120.00 debited from A/c XX4821 to VPA rapido@ybl (UPI Ref 4)',d:Date.now()+5000}); window.__smsChanged(); }); await A.waitForTimeout(1500);
 await A.evaluate(()=>syncNow()); await B.evaluate(()=>syncNow()); await B.waitForTimeout(1200); ok(await B.evaluate(()=>S.exp.some(e=>e.title==='Rapido'&&e.amt===120)),'a message arriving later appears on the partner\'s phone');
 // partner adds an expense; A sees it on app resume (no manual sync), and via pull-to-refresh
 await B.evaluate(()=>{ const n=Date.now(); S.exp.push({id:uid(),amt:777,title:'Resume Cafe',cat:'food',paid:'me',sm:50,ts:n,src:'manual',u:n}); persist(); return syncNow(); }); await B.waitForTimeout(800);
 await A.evaluate(()=>window.__resume()); await A.waitForTimeout(2500);
 ok(await A.evaluate(()=>S.exp.some(e=>e.title==='Resume Cafe')),'opening the app pulls the partner\'s latest expense');
 await B.evaluate(()=>{ const n=Date.now(); S.exp.push({id:uid(),amt:888,title:'Pull Bakery',cat:'food',paid:'me',sm:50,ts:n,src:'manual',u:n}); persist(); return syncNow(); }); await B.waitForTimeout(800);
 await A.evaluate(()=>{ show('month'); }); await A.waitForTimeout(500);
 await A.evaluate(()=>{ const pg=document.querySelector('.page'); const mk=(t,y)=>{ const tc=new Touch({identifier:1,target:pg,clientX:150,clientY:y}); return new TouchEvent(t,{touches:t==='touchend'?[]:[tc],changedTouches:[tc],bubbles:true,cancelable:true}); }; pg.dispatchEvent(mk('touchstart',200)); pg.dispatchEvent(mk('touchmove',330)); pg.dispatchEvent(mk('touchend',330)); });
 await A.waitForTimeout(3500);
 ok(await A.evaluate(()=>S.exp.some(e=>e.title==='Pull Bakery')),'pull down on the Month screen refreshes and brings the partner\'s expense');
 // refund arrives by SMS -> lowers the month total, syncs to partner as a refund; balance noted
 await A.evaluate(()=>{ window.__inbox.push({id:300,a:'VM-HDFCBK',b:'Rs 799.00 refunded to your A/c XX4821 by Myntra on 06-10-26. Avl Bal Rs 52,300.50',d:Date.now()+9000}); window.__smsChanged(); }); await A.waitForTimeout(1500);
 ok(await A.evaluate(()=>S.exp.some(e=>e.cat==='refund'&&e.amt===-799)),'a refund text is added as a negative expense');
 ok(await A.evaluate(()=>Object.values(S.bal||{}).some(b=>b.v===52300.5&&b.tail==='4821')),'bank balance is read from the text');
 await A.evaluate(()=>syncNow()); await B.evaluate(()=>syncNow()); await B.waitForTimeout(1200);
 ok(await B.evaluate(()=>S.exp.some(e=>e.cat==='refund'&&e.amt===-799)),'refund syncs to the partner as a refund');
 // UI: grouping + no dots + guide
 await A.evaluate(()=>{ const n=Date.now(); for(const a of [210,180,330]) S.exp.push({id:uid(),amt:a,title:'swiggy@axb',cat:'food',paid:'me',sm:50,ts:n-3600e3*a/100,src:'sms',u:n}); S.exp.push({id:uid(),amt:480000,title:'Sandeep Kumar',cat:'other',paid:'me',sm:50,ts:n-7200e3,src:'sms',u:n}); U.month=monthStart(new Date()); U.week=null; show('month'); }); await A.waitForTimeout(800);
 const ui=await A.evaluate(()=>({dots:document.querySelectorAll('.pd, .dot2').length, swiggyRows:[...document.querySelectorAll('#tl .li .t1')].filter(e=>/swiggy/i.test(e.innerText)).length, group:/\d payments/.test(document.querySelector('#tl').innerText), guide:!!document.querySelector('.gcard.warn')}));
 ok(ui.dots===0,'no payer dots anywhere on the Month screen'); ok(ui.swiggyRows<=2,'same-merchant payments are merged into one row per week ('+ui.swiggyRows+' Swiggy rows)'); ok(ui.group,'merged rows say "N payments"'); ok(ui.guide,'a guiding line flags the large payment share');
 await A.screenshot({path:'shots/g_1.png'}); await A.click('.gcard.warn'); await A.waitForTimeout(700); await A.screenshot({path:'shots/g_2.png'}); const t1=await A.evaluate(()=>S.exp.filter(counted).reduce((a,e)=>a+e.amt,0));
 await A.click('[data-act=lg-no]'); await A.waitForTimeout(600); const t2=await A.evaluate(()=>S.exp.filter(counted).reduce((a,e)=>a+e.amt,0)); ok(t1-t2===480000,'"Not spending" removes it from the total');
 await A.evaluate(()=>closeSheet()); await A.waitForTimeout(500); await A.screenshot({path:'shots/g_3.png'});
 await A.click('#tl .li >> nth=0'); await A.waitForTimeout(500); await A.screenshot({path:'shots/g_4.png'});
 for(const p of [A,B]) ok(p.errs.length===0,'no JS errors '+p.errs.join(';'));
 console.log(res.map(r=>r.join('  ')).join('\n')); console.log(res.filter(r=>r[0]==='PASS').length+'/'+res.length); await b.close(); })().catch(e=>{console.error('CRASH',e);process.exit(1)});
