# Five-couple end-to-end test (simulated)

**What this is:** scripted users driving the real UI against the real Worker (local D1, mocked Anthropic). It proves flows converge; it does not replace real people.

| Couple | Behaviour | Result |
|---|---|---|
| **Priya & Rahul** | paste 4 bank texts, invite on WhatsApp (link + QR + landing page), partner joins, both add | pass — mirrored names, same totals per person on both phones |
| **Sam & Jordan** | open an expense card (extended details), edit its note, edit the same expense offline on both phones, delete vs edit race | pass — note syncs, one value wins, both converge |
| **Lena & Tom** | Tom works offline (adds 2, deletes 1) while Lena adds 1; invite code reuse; Lena deletes Nest data | pass — converge after reconnect, code retired, Tom told to reconnect |
| **Mia & Chris** | hostile names/merchants, EUR, back-dated expense, week filter, third person with a guessed code, agent adds an expense | pass — no XSS, EUR adopted, last month shows the item, week chips filter, agent expense on both phones |

54 UI/sync checks passed (scripted users, not real people).

## What changed after the "too much happening" feedback (v1.4)
- **No owing/balance/settle-up.** Nest is a plain shared tracker: who spent what, together.
- **One calm Month screen:** big total, a two-colour bar (Arjun / Meera), week text-tabs, the timeline. Nothing else.
- **Fewer icons:** no tab bar, no emoji tiles. Real brand logos where they exist, otherwise a neutral letter tile. Two top-right buttons (✦ Ask, avatars) and one + button.
- **Details on tap:** each expense card opens a full details sheet — spent by, category, date/time, source, note, share of the month and week, how often/usual amount at that merchant, recent visits.
- **Apple-style system:** large title, inset grouped lists, system blue/greys, flat surfaces, plain-text actions.

## Do next
- Watch real couples do first-run with real WhatsApp + camera scan.
- Logos: Amazon, Flipkart, Myntra, Ola, Rapido, MakeMyTrip, Blinkit, Zepto aren't in the open icon set (they show a letter tile).
- Real Anthropic call untested (mock only); push-style sync instead of 20 s polling.
