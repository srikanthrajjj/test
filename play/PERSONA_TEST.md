# Five-couple end-to-end test (simulated)

**What this is:** scripted users, not real people. Ten browser "phones" (two per couple) drove the real UI against the real Worker
(local D1 + mocked Anthropic API). Re-run: `scratchpad`-style harness is in `worker/test/` (API) and was run as 69 UI/sync checks.
It proves the flows converge and the maths is right; it does **not** replace watching real couples use the app (see "Do next").

| Couple | Behaviour | What was checked | Result |
|---|---|---|---|
| **Priya & Rahul** — happy path | paste 4 bank texts, invite on WhatsApp, partner joins, add, settle | WhatsApp link + QR + landing page, joiner skips names step, mirrored balances, settle → ₹0, "Settled up" milestone | pass |
| **Sam & Jordan** — they argue | Jordan *questions* a ₹3,000 dinner ("Not mine"), Sam fixes the split and resolves; both edit the same expense offline; one deletes while the other edits | flag banner on Sam's phone, resolve clears on both, conflicting edits converge (last write wins), delete-vs-edit converges | pass |
| **Aisha & Omar** — unequal incomes | rent 70/30, bill 30/70, partial settlement | balance = +4,800 → +2,800 after ₹2,000; per-person fair shares ₹25,200 / ₹10,800 add up to ₹36,000 | pass |
| **Lena & Tom** — offline day | Tom works offline (adds 2, deletes 1) while Lena adds 1 | converge after reconnect, invite code retired after join, unknown code rejected, **Lena deletes Nest data → Tom told to reconnect** | pass |
| **Mia & Chris** — chaos | hostile names (`<img onerror>`), HTML in merchant, EUR currency, back-dated expense, delete+restore, guessed invite code, AI agent proposes an expense | no XSS, Chris adopts EUR automatically, last month's timeline shows the back-dated item, third person can't join, agent expense appears on both phones | pass |

## What the "argue" couples told us is important (and what changed)
1. **Disagreements need a kind, one-tap path** → added *Question this* (reason chips + note), an amber banner above everything on the partner's Month screen, and a "Sorted ✓ / Fix it" response. Nothing gets deleted behind anyone's back.
2. **Silent overwrites cause fights** → when a partner changes an amount you now get a toast ("Jordan changed “Dinner” to ₹2,900"). Remaining gap: last-write-wins still decides the value; a per-expense history ("who changed what") would be the next step.
3. **People think in months and weeks, not tabs** → one Month screen: balance → month totals (Together / each person) → week chips → timeline with weekly totals, settlements, balance flips and "carried over from last month".
4. **Joiners want to *see* the month, not be onboarded again** → joining from a link skips names/currency (adopted from the inviter) and offers "Skip — show our month".
5. **Whoever pays sets the mood** → every row shows who paid (colour dot) and only the effect on the balance (+ you're owed / − you owe).
6. **Trust** → Play requires a way to delete data: added *Delete our Nest data from the server* + a public `/delete` page.

## Known gaps / do next (real users, not scripts)
- Watch 5 real couples do first-run with the **real WhatsApp + camera-scan** path on real phones; nobody has run that here.
- Brand logos exist for ~60 merchants (Swiggy, Zomato, Netflix, Uber, BigBasket, BookMyShow, …). **Amazon, Flipkart, Myntra, Ola, Rapido, MakeMyTrip, Blinkit, Zepto are not in the open icon set**, so those show a category emoji. Add licensed assets if you want them.
- The real Claude call was never exercised (mock only). Try it with a real key before launch.
- Push-style sync (Durable Objects / FCM) instead of 20-second polling; push notification when a partner questions an expense.
