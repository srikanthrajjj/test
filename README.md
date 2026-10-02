# Nest — a couples expense tracker (Android)

Premium, Apple‑style money app for two. One honest balance, updated with every expense.

**Download:** [`dist/Nest.apk`](dist/Nest.apk) → open on your phone → allow "install unknown apps" → install.
Requires Android 8.0+.

## What's inside
- **Splash → onboarding → SMS "wow"**: on first run Nest scans your bank/UPI texts *on‑device*, finds payments
  (OTPs, credits, refunds, offers are ignored), auto‑categorises merchants and lets you approve each one.
  No SMS access? Preview with sample messages or start empty.
- **Home**: live balance ("Meera owes you ₹9,685"), today's change, 14‑day balance trend, spend breakdown, who paid.
- **Activity**: every expense with the split and its effect; each day shows the running balance at day's end.
- **Insights**: category donut, per‑person paid vs. fair share, day‑by‑day bars, top places, highlights.
- **Add**: amount, name (auto‑category), who paid, how to split (equal / just one / custom %), with a live
  "Meera will owe you ₹620 more" preview. **Settle up** records a payment and zeroes the balance.
- Light/dark, 4 currencies, CSV export, undo on every destructive action. Data stays on the device unless you connect sync.

## Sync + agent (v1.1)
- **Two phones, one Nest**: one partner taps *You -> Create our Nest* and shares the 6-letter code; the other joins from the
  welcome screen or *I have a code*. Expenses, settlements and names sync live (poll + on resume), with "Meera added ..." toasts.
- **Together vs individual**: Insights has a *Together / Arjun / Meera* switch (fair-share spending, paid upfront, net effect);
  Home shows both shares side by side.
- **Nest agent**: a daily AI brief on Home and a chat that answers from your real numbers and can *propose* expenses or
  settlements you confirm with one tap. Without a server it falls back to on-device insights.
- Backend: [`worker/`](worker/README.md) - Cloudflare Worker + D1, in your own account.

## v1.3 — Month timeline, WhatsApp/QR invite, disputes, Play kit
- One **Month** screen (balance → Together/each person → week chips → timeline with real merchant logos); breakdown opens as a sheet.
- **Invite**: WhatsApp link + QR (partner scans with the camera → landing page → app opens via `nest://join`), shown right after the SMS import.
- **Question this** for disagreements; partner edits surface as toasts; couple currency syncs; in-app + web data deletion.
- `PLAY=1 ./android/build.sh` builds the Play-policy variant (no READ_SMS). See `play/` (listing, data safety, checklist, 5-couple test report).

## Balance rule
`Balance = what you paid − your fair share` (positive ⇒ partner owes you). Settlements are added on top.

## Design principles applied (Norman × Apple HIG)
| Principle | Where |
|---|---|
| Visibility of system status | Live balance hero, "updated just now", scan progress counters, per‑day running balance |
| Feedback | Haptics, count‑up numbers, toasts, instant preview of how an expense changes the balance |
| Mapping & consistency | Each partner keeps one colour everywhere; "+" is always bottom‑centre |
| Constraints / error prevention | Save disabled until valid, split presets, amounts sanitised, review‑before‑import |
| Forgiveness | Undo toast on add / delete / settle; drag‑to‑dismiss sheets; confirm before erasing |
| Conceptual model | One sentence rule, explained in the "?" sheet |
| Clarity, deference, depth (HIG) | Large titles, tab bar, bottom sheets, system‑style segmented controls, Inter type |

## Build
`./android/build.sh` — no Gradle / Android SDK needed (aapt2 from Apktool, javac, dx, apksig; fetched from Maven Central).
UI is in `android/app/src/main/assets/www/`; the native bridge (SMS read, storage, haptics) is `MainActivity.java`.
`android/tools/nest-release.p12` is a throw‑away self‑signed key (password `nestnest`) so updates install over earlier builds;
replace it before any store release.
