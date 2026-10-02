// Five simulated couples, two devices each, against the real Worker (local D1) + mocked Anthropic.
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const assert = require('node:assert/strict');
const URL='file:///home/user/test/android/app/src/main/assets/www/index.html', SERVER='http://127.0.0.1:8788';
const results=[], issues=[]; let browser;
const ok=(c,msg)=>{ results.push([c?'PASS':'FAIL',msg]); if(!c) issues.push(msg); };
async function device(label, opts={}) {
  const ctx = await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true,locale:'en-IN'});
  const p = await ctx.newPage(); p.errs=[]; p.label=label; p.ctx=ctx;
  p.on('pageerror',e=>p.errs.push(e.message)); p.on('console',m=>{ if(m.type()==='error' && !/Failed to load resource|net::ERR/.test(m.text())) p.errs.push(m.text()); });
  await p.addInitScript(([u])=>{ window.__fast=1; try{localStorage.setItem('nest.url',u)}catch(e){} }, [SERVER]);
  await p.goto(URL); await p.waitForTimeout(500); return p;
}
const canon = p => p.evaluate(()=>{ const M=S.sync?S.sync.member:'a'; const A=x=>x==='me'?M:(M==='a'?'b':'a');
  const exp=S.exp.map(e=>[e.id,e.amt,e.title,A(e.paid),e.cat,e.note||''].join('|')).sort();
  const spentA=S.exp.filter(e=>A(e.paid)==='a').reduce((a,e)=>a+e.amt,0); return {exp,set:[],netA:spentA,cur:S.cur,names:[S.me.name,S.p.name]}; });
async function converge(A,B,msg){ for(let i=0;i<4;i++){ await A.evaluate(()=>syncNow()); await B.evaluate(()=>syncNow()); await A.waitForTimeout(500);} const a=await canon(A), b=await canon(B);
  ok(JSON.stringify(a.exp)===JSON.stringify(b.exp)&&JSON.stringify(a.set)===JSON.stringify(b.set)&&Math.abs(a.netA-b.netA)<0.01, `${msg}: both phones agree (${a.exp.length} expenses, net ${a.netA})`); return a; }
async function onboardEmpty(p,me,pt){ await p.click('[data-act=ob-next]'); await p.fill('#ob-me',me); await p.fill('#ob-p',pt); await p.click('[data-act=ob-names]'); await p.click('[data-act=ob-skip]'); await p.waitForTimeout(700); }
async function addUI(p,amt,title,{paid,split}={}){ await p.click('#fab'); await p.waitForTimeout(450); await p.fill('#f-amt',String(amt)); await p.fill('#f-title',title);
  if(paid) await p.click(`[data-act=f-paid][data-arg=${paid}]`); await p.click('[data-act=f-save]'); await p.waitForTimeout(700); }
async function inviteAndJoin(A,B,bName){ // A: quick invite via the month banner (WhatsApp)
  await A.evaluate(()=>{window.__opened=null;window.open=u=>{window.__opened=u}});
  await A.click('[data-act=quick-invite]'); await A.waitForSelector('.qrbox',{timeout:8000}); await A.waitForTimeout(1200); const wa=await A.evaluate(()=>window.__opened);
  const link=await A.evaluate(()=>inviteLink()); const code=await A.evaluate(()=>S.sync.code);
  ok(!!wa && wa.includes('wa.me') && decodeURIComponent(wa).includes(link), 'WhatsApp opens with the invite link');
  ok(await A.evaluate(()=>/<svg/.test(document.querySelector('.qrbox').innerHTML)), 'QR code is shown for the partner to scan');
  const landing=await (await fetch(link)).text(); ok(/invited you/.test(landing)&&/intent:\/\/join/.test(landing),'invite link landing page opens the app via intent');
  await A.click('#sheet .x'); await A.waitForTimeout(400);
  await B.evaluate(([c,s])=>handleInvite(c,s),[code,SERVER]); await B.waitForSelector('#pr-name',{timeout:8000});
  await B.fill('#pr-name',bName); await B.click('#pr-go'); await B.waitForTimeout(2500);
  if(await B.evaluate(()=>document.querySelector('#ob').classList.contains('on'))){ ok(await B.evaluate(()=>!!document.querySelector('[data-act=ob-skip]')),'joiner lands on the SMS/skip-typing step (no names step)'); await B.click('[data-act=ob-skip]'); await B.waitForTimeout(900); } return code; }
(async()=>{
 browser = await chromium.launch(); const t0=Date.now();
 // P1 happy path
 { const A=await device('P1-A'), B=await device('P1-B'); await onboardEmpty(A,'Priya','Rahul');
   await A.evaluate(()=>{ const mm=String(new Date().getMonth()+1).padStart(2,'0'), yy=String(new Date().getFullYear()).slice(2); const t=[0,1,2,3].map(i=>`Rs.${[640,1850,420,2999][i]}.00 debited from A/c XX4821 on ${String(1+i).padStart(2,'0')}-${mm}-${yy} to VPA ${['swiggy','bigbasket','uber','amazon'][i]}@okaxis (UPI Ref 4123456)`).join('\n\n'); const c=parseChunks(t); window.__c=c.length; c.forEach(x=>{S.exp.push({id:uid(),amt:x.amt,title:x.title,cat:x.cat,paid:'me',sm:50,ts:x.ts,src:'sms',u:Date.now()});S.seen.push(x.sid)}); S.onboarded=true; save(); show('month'); });
   ok(await A.evaluate(()=>window.__c)===4,'P1 4 pasted bank texts -> 4 payments'); await inviteAndJoin(A,B,'Rahul');
   const c=await converge(A,B,'P1 after Rahul joins'); ok(c.names.join()==='Priya,Rahul','P1 names mirrored');
   await addUI(B,900,'Zepto groceries'); const c2=await converge(A,B,'P1 Rahul adds'); ok(Math.abs(c2.netA-(640+1850+420+2999))<0.01,'P1 Priya spent 5909 / Rahul 900 on both phones');
   ok(await A.evaluate(()=>/Rahul/.test(document.querySelector('.who2').innerText)&&/900/.test(document.querySelector('.who2').innerText)),'P1 Month header shows each person\'s spend');
   await A.screenshot({path:'shots/sim2_p1.png'}); for(const p of [A,B]) ok(p.errs.length===0,`P1 ${p.label} no JS errors ${p.errs.join(';')}`); await A.ctx.close(); await B.ctx.close(); }
 // P2 conflicts
 { const A=await device('P2-A'), B=await device('P2-B'); await onboardEmpty(A,'Sam','Jordan'); await addUI(A,3000,'Dinner at Olive'); await inviteAndJoin(A,B,'Jordan'); await converge(A,B,'P2 joined');
   await B.click('.tn .li >> nth=0'); await B.waitForTimeout(600); ok(await B.evaluate(()=>/Spent by/.test(document.querySelector('#sheet').innerText)&&/in context/i.test(document.querySelector('#sheet').innerText)),'P2 expense card opens extended details'); await B.screenshot({path:'shots/sim2_detail.png'}); await B.click('[data-act=edit]'); await B.waitForTimeout(800); await B.fill('#f-note','Anniversary'); await B.click('[data-act=f-save]'); await B.waitForTimeout(800);
   const c=await converge(A,B,'P2 note edited on one phone'); ok(c.exp[0].endsWith('|Anniversary'),'P2 note syncs to the other phone');
   await B.ctx.setOffline(true); await A.evaluate(()=>{ const e=S.exp[0]; e.amt=3100; touch(e); save(); }); await B.evaluate(()=>{ const e=S.exp[0]; e.amt=2900; touch(e); persist(); }); await B.waitForTimeout(300); await B.ctx.setOffline(false);
   const c2=await converge(A,B,'P2 conflicting edits'); ok(/\|(2900|3100)\|/.test(c2.exp[0]),'P2 one value wins on both');
   await A.evaluate(()=>{ const e=S.exp[0]; e.title='Dinner (edited)'; touch(e); persist(); }); await B.evaluate(()=>{ const id=S.exp[0].id; S.exp=S.exp.filter(e=>e.id!==id); tomb('exp',id); persist(); }); await converge(A,B,'P2 delete vs edit race');
   for(const p of [A,B]) ok(p.errs.length===0,`P2 ${p.label} no JS errors ${p.errs.join(';')}`); await A.ctx.close(); await B.ctx.close(); }
 // P3 offline
 { const A=await device('P3-A'), B=await device('P3-B'); await onboardEmpty(A,'Lena','Tom'); await addUI(A,500,'Coffee'); await inviteAndJoin(A,B,'Tom'); await converge(A,B,'P3 joined');
   await B.ctx.setOffline(true); await addUI(B,1200,'Metro card'); await addUI(B,300,'Snacks'); await B.evaluate(()=>{ const e=S.exp.find(x=>x.title==='Coffee'); S.exp=S.exp.filter(x=>x!==e); tomb('exp',e.id); save(); }); await B.waitForTimeout(800); ok(await B.evaluate(()=>S.exp.length===2),'P3 offline changes are kept locally'); await addUI(A,800,'Pharmacy');
   await B.ctx.setOffline(false); const c=await converge(A,B,'P3 back online'); ok(c.exp.length===3,'P3 3 expenses remain');
   ok(await A.evaluate(()=>S.sync.code===null),'P3 invite code retired once Tom joined'); ok((await fetch(SERVER+'/v1/pair/peek?code=ZZZZZZ')).status===404,'P3 unknown code rejected');
   await A.evaluate(()=>show('you')); await A.click('[data-act=disconnect]'); await A.waitForTimeout(500); await A.click('[data-act=nest-delete]'); await A.waitForTimeout(1200); ok(await A.evaluate(()=>!S.sync),'P3 Lena deletes server data'); await B.evaluate(()=>syncNow()); await B.waitForTimeout(800); ok(await B.evaluate(()=>U.sync==='auth'),'P3 Tom is told to reconnect');
   for(const p of [A,B]) ok(p.errs.length===0,`P3 ${p.label} no JS errors ${p.errs.join(';')}`); await A.ctx.close(); await B.ctx.close(); }
 // P4 chaos + agent
 { const A=await device('P4-A'), B=await device('P4-B'); await onboardEmpty(A,'Mia <img src=x onerror=window.__xss=1>','Chris'); await A.evaluate(()=>{ S.cur='EUR'; S.mu=Date.now(); save(); }); await addUI(A,45,'<b>Brunch</b> "Café" & more'); await inviteAndJoin(A,B,'Chris');
   ok(await B.evaluate(()=>S.cur==='EUR'),'P4 Chris adopts EUR automatically'); ok(await B.evaluate(()=>!window.__xss)&&await A.evaluate(()=>!window.__xss),'P4 hostile text escaped (no XSS)');
   await B.evaluate(()=>{ const d=new Date(); d.setMonth(d.getMonth()-1); const e={id:uid(),amt:120,title:'Train tickets',cat:'move',paid:'me',sm:50,ts:d.getTime(),src:'manual'}; touch(e); S.exp.push(e); save(); }); await converge(A,B,'P4 back-dated expense');
   await A.click('[data-act=mprev]'); await A.waitForTimeout(700); ok(await A.evaluate(()=>document.querySelector('#tl').innerText.includes('Train tickets')),'P4 last month shows the back-dated expense'); await A.click('[data-act=mnext]'); await A.waitForTimeout(500);
   const wk=await A.evaluate(()=>document.querySelectorAll('.wchip').length); ok(wk>=5,'P4 week chips are shown ('+wk+')'); await A.click('.wchip >> nth=1'); await A.waitForTimeout(500); ok(await A.evaluate(()=>U.week!=null),'P4 tapping a week filters the timeline'); await A.click('.wchip >> nth=0');
   const cj=await fetch(SERVER+'/v1/pair/join',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({code:'ABCDEF',name:'Eve'})}); ok(cj.status===404,'P4 third person cannot join with a guessed code');
   await A.click('[data-act=chat]'); await A.waitForTimeout(600); await A.fill('#chat-in','log dinner 60 I paid'); await A.press('#chat-in','Enter'); await A.waitForSelector('.acard',{timeout:15000}); await A.click('[data-act=act-yes]'); await A.waitForTimeout(700); const c2=await converge(A,B,'P4 agent expense'); ok(c2.exp.length===3,'P4 agent expense on both phones');
   for(const p of [A,B]) ok(p.errs.length===0,`P4 ${p.label} no JS errors ${p.errs.join(';')}`); await A.ctx.close(); await B.ctx.close(); }
 console.log(results.map(r=>r.join('  ')).join('\n')); console.log(`\n${results.filter(r=>r[0]==='PASS').length}/${results.length} checks passed in ${Math.round((Date.now()-t0)/1000)}s`);
 await browser.close(); process.exit(issues.length?1:0);
})().catch(e=>{console.error('SIM CRASH',e);process.exit(2)});
