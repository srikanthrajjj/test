const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{ const b=await chromium.launch(); const p=await (await b.newContext({viewport:{width:390,height:844},deviceScaleFactor:2,isMobile:true,hasTouch:true,locale:'en-IN'})).newPage(); const errs=[]; p.on('pageerror',e=>errs.push(e.message));
 await p.addInitScript(()=>{window.__fast=1}); await p.goto('file:///home/user/test/android/app/src/main/assets/www/index.html'); await p.waitForTimeout(500);
 await p.click('[data-act=ob-next]'); await p.fill('#ob-me','Arjun'); await p.fill('#ob-p','Meera'); await p.click('[data-act=ob-names]'); await p.click('[data-act=ob-skip]'); await p.waitForTimeout(600);
 const res=await p.evaluate(()=>{
   const now=new Date(), mk=(d,h=12)=>new Date(now.getFullYear(),now.getMonth(),d,h).getTime(), pm=(d)=>new Date(now.getFullYear(),now.getMonth()-1,d,12).getTime();
   const add=(amt,title,paid,ts)=>S.exp.push({id:uid(),amt,title,cat:catOf(title),paid,sm:50,ts,src:'sms',u:Date.now()});
   // this month: name variants must merge
   add(450,'Swiggy','me',mk(1)); add(380,'swiggy','p',mk(2)); add(290,'Swiggy.Stores','me',mk(3)); add(640,'Swiggy Instamart','p',mk(3));
   add(1299,'Amazon Pay','me',mk(2)); add(2999,'Amazon India','p',mk(4)); add(649,'Netflix','me',mk(1)); add(120,'Rapido','p',mk(2)); add(210,'Uber India Systems','me',mk(2));
   add(1800,'Bigbasket','p',mk(1)); add(2100,'Big Basket','me',mk(3)); add(75,'Chai Point','me',mk(2)); add(60,'Tea stall','p',mk(2)); add(45,'Corner shop','me',mk(1)); add(30,'Kirana','p',mk(1));
   add(5000,'Rent','p',mk(1)); add(1200,'BESCOM Electricity','me',mk(1));
   // previous months for recurring + delta
   add(649,'Netflix','me',pm(1)); add(649,'Netflix','me',new Date(now.getFullYear(),now.getMonth()-2,1,12).getTime()); add(9000,'Misc','me',pm(5));
   U.month=monthStart(new Date()); const R=monthReport(U.month); const sum=a=>a.reduce((x,y)=>x+y,0);
   const total=S.exp.filter(e=>inMonth(e.ts,U.month)).reduce((a,e)=>a+e.amt,0), meT=S.exp.filter(e=>inMonth(e.ts,U.month)&&e.paid==='me').reduce((a,e)=>a+e.amt,0);
   const g=n=>R.keep.concat(R.tail.cnt?[R.tail]:[]);
   return { total, T:R.T, catSum:sum(R.catList.map(c=>c.t)), catMe:sum(R.catList.map(c=>c.me)), rowOK:R.catList.every(c=>Math.abs(c.me+c.p-c.t)<0.01&&Math.abs(sum(c.list.map(x=>x.t))-c.t)<0.01), merchSum:sum(g().map(x=>x.t)), merchNames:R.keep.map(x=>x.name), tail:R.tail.cnt, swiggy:R.keep.find(x=>x.name==='Swiggy'), amazon:R.keep.find(x=>x.name==='Amazon'), big:R.keep.find(x=>x.name==='BigBasket'), pat:R.pat.map(x=>x.replace(/<[^>]+>/g,'')) , meT }; });
 console.log(JSON.stringify(res,null,1));
 assert.equal(res.T.t,res.total); assert.equal(res.T.me,res.meT); assert.ok(Math.abs(res.catSum-res.total)<0.01,'categories sum to total'); assert.ok(Math.abs(res.merchSum-res.total)<0.01,'places+smaller sum to total'); assert.ok(res.rowOK,'each category row = sum of its places');
 assert.equal(res.swiggy.n,3,'3 Swiggy variants merged'); assert.equal(res.swiggy.t,450+380+290); assert.equal(res.amazon.t,1299+2999); assert.equal(res.big.t,3900,'BigBasket variants merged');
 await p.evaluate(()=>{show('month')}); await p.waitForTimeout(800); await p.click('[data-act=breakdown]'); await p.waitForTimeout(1000); await p.click('[data-act=exp-cat][data-arg=food]'); await p.waitForTimeout(400); await p.screenshot({path:'shots/sum_1.png'});
 await p.evaluate(()=>document.querySelector('#sheet .body').scrollTo(0,640)); await p.waitForTimeout(300); await p.screenshot({path:'shots/sum_2.png'}); console.log('ERR',errs); console.log('REPORT OK'); await b.close(); })().catch(e=>{console.error('FAIL',e.message);process.exit(1)});
