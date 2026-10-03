const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const T=[
["UPI txn. Rs. 450.00 debited from a/c **1234 on 02-10-26 to VPA swiggy.stores@axisbank(UPI Ref no 123456789012)",450,"Swiggy"],
["Rs.1299.00 spent on HDFC Bank Card x1234 at AMAZON PAY on 2026-10-02:14:33:10. Avl bal: 50000",1299,"Amazon"],
["Dear Customer, your A/c X1234 is debited by Rs.300.00 on 02Oct26 trf to ZEPTO Refno 123456789",300,"Zepto"],
["A/C X1234 Debited INR 180.00 on 02/10/26 Info: UPI/DR/123456/RAPIDO/ybl/Payment",180,"Rapido"],
["ICICI Bank Acct XX123 debited for Rs 799.00 on 02-Oct-26; NETFLIX COM credited. UPI:12345. Call 18002662 for dispute.",799,"Netflix"],
["INR 2,450.00 spent using ICICI Bank Card XX1234 on 02-Oct-26 on Myntra. Avl Limit: INR 1,00,000",2450,"Myntra"],
["Sent Rs.120.00 from Kotak Bank AC X1234 to rapido@ybl on 02-10-26.UPI Ref 12345",120,"Rapido"],
["Paid ₹350 to Third Wave Coffee. UPI Ref 12345. Bank: HDFC",350,"Third Wave Coffee"],
["You paid ₹220 to Blinkit using Google Pay",220,"Blinkit"],
["Paid Rs.99 to Spotify via Paytm UPI",99,"Spotify"],
["Rs 450 debited a/c XX1234 02-10-26 UPI to Uber India Systems Pvt Ltd Ref 4412",450,"Uber"],
["Your a/c no. XXXXXX1234 is debited for Rs.250.00 on 02-10-26 and a/c XXXX credited (UPI Ref no 12345)",250,null],
["Debit Alert: Rs.349.00 spent using HDFC Card x7890 at STARBUCKS COFFEE on 2026-10-10 Bal Rs 20,000",349,"Starbucks"],
["Txn of USD 12.99 on Card ending 4411 at SPOTIFY USA approved. Available balance $2,340.10",12.99,"Spotify"],
["A/c XX1234 debited Rs.7,500.00 ATM WDL on 03-05-26 Avl bal Rs 10,000",7500,"Atm"],
["Rs.2,999.00 paid at Amazon India via UPI from A/c XX4821. Ref 12345",2999,"Amazon"],
["Payment of Rs.1,200 to BESCOM Electricity successful via CRED",1200,"Bescom"],
["Rs. 640 debited from your account XX1234 towards Swiggy Instamart on 02-10-2026",640,"Swiggy"],
["HDFC Bank: Rs.500.00 debited via UPI to Zomato Limited on 02-10-26. Ref 123",500,"Zomato"],
// real-world traps
["Rs 340.00 debited from A/c XX1234 on 02-07-26 to ZEPTO. Avl Bal Rs 4,52,775.00",340,"Zepto"],
["Avl Bal Rs 4,52,775.00. Rs 340.00 debited from A/c XX1234 to Zepto on 02-07-26",340,"Zepto"],
["Available credit limit Rs 4,50,000. Rs 1,299 spent on Card XX1234 at AMAZON on 02-07-26",1299,"Amazon"],
["Dear Customer, A/c X1234 debited by 300.0 on 02Jul26 trf to ZEPTO Refno 123",300,"Zepto"],
["Rs 4,50,000 debited via NEFT to SELF A/c XX9988 on 29-07-26",450000,null],
["Rs 5,000.00 debited from A/c XX1234 towards GROWW SIP mandate on 05-07-26",5000,null],
["Rs 24,500 debited towards CRED credit card bill payment on 15-07-26",24500,null],
["Payment of Rs 4,52,775 received in your credit card XX1234. Thank you",0,null],
// should skip
["Your OTP for Rs 2,400 txn at Amazon is 123456. Do not share.",0,null],
["Rs 15,000 credited to your a/c XX1234 on 02-10-26 by UPI from rahul@ybl",0,null],
["Your credit card bill of Rs 24,500 is due on 15-Oct. Min due Rs 1,225",0,null],
["Refund of Rs 799 processed for Myntra order",0,null],
["Rs 500 will be debited on 5th for Netflix autopay",0,null],
["Get cashback up to Rs 500 on first UPI payment. T&C apply",0,null]];
(async()=>{ const b=await chromium.launch(); const p=await b.newPage(); await p.goto('file:///home/user/test/android/app/src/main/assets/www/index.html');
 const out=await p.evaluate(T=>T.map(([b,amt,name])=>{const r=parseSms({b,d:Date.now(),id:1}); const got=r?{amt:r.amt,title:r.title}:null; const okAmt=amt===0? !r : (r&&Math.abs(r.amt-amt)<0.01); const okName=!name||!r||(r.title.toLowerCase().includes(name.toLowerCase())); return {ok:okAmt&&okName,exp:[amt,name],got,b:b.slice(0,60)}}),T);
 out.forEach(o=>console.log(o.ok?'OK  ':'FAIL',JSON.stringify(o.exp),'=>',JSON.stringify(o.got),'|',o.b)); const xf=await p.evaluate(()=>[parseSms({b:'Rs 4,50,000 debited via NEFT to SELF A/c XX9988 on 29-07-26',d:1,id:1}).cat,parseSms({b:'Rs 5,000.00 debited from A/c XX1234 towards GROWW SIP mandate on 05-07-26',d:1,id:2}).cat,parseSms({b:'Rs 24,500 debited towards CRED credit card bill payment on 15-07-26',d:1,id:3}).cat]); console.log('transfer cats',JSON.stringify(xf)); console.log(out.filter(o=>o.ok).length+'/'+out.length); await b.close(); })();
