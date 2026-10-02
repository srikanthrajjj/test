# Play Store launch checklist — read this before you spend time on screenshots

## Blockers I could not fix from here
1. **Play needs an Android App Bundle (.aab), not an APK.** The build here makes APKs because `bundletool` isn't reachable from this sandbox.
   Easiest path: open `android/` in Android Studio (or wrap the same sources in a Gradle project) → *Build → Generate Signed Bundle*, **or** ask me for a Gradle project once you can run builds locally.
2. **Target API level.** Built for API 35 now; Play raises the minimum every August — check the current requirement in Play Console.
3. **SMS permission.** `READ_SMS` is a *restricted permission*; expense tracking is not on Google's approved-use list, so a Play build **must not** request it.
   → Build the Play variant with `PLAY=1 NEST_SERVER=https://<worker> ./android/build.sh` (no READ_SMS; users **share or paste** bank texts instead). Keep the sideload APK for inbox scanning.
4. **Signing.** Use Play App Signing with a *new* upload key. `android/tools/nest-release.p12` is a throw-away key with a public password — never upload with it.
5. **New personal developer accounts** must run a closed test with ≥12 testers for 14 days before production (check current rule) — your "5 real couples" = 10 testers; add two more.
6. **Server.** Deploy `worker/` (see its README), set `PLAY_URL` var to your listing, `CONTACT_EMAIL`, optionally `ASSET_FINGERPRINT` (Play signing SHA-256) to enable verified App Links later.

## Console forms
- Privacy policy URL → `/privacy` · Data deletion URL → `/delete` · Data safety → `DATA_SAFETY.md`
- Content rating questionnaire (Finance, no UGC visible to strangers) · Target audience 18+ · Ads: none
- Finance apps: declare it is **not** a loan / banking / payments app (Nest never moves money)
- AI disclosure: the agent sends spending summaries to Anthropic

## Before production
- [ ] Real-phone test of: WhatsApp invite → link → app opens → joined (needs the Play/app installed)
- [ ] Real Anthropic key, watch cost (80 agent calls/couple/day cap is in the Worker)
- [ ] Back up D1 (`wrangler d1 export`) and set a Cloudflare usage alert
