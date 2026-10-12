# SafeGuard Guardian

The companion app for the **guardian's own phone**. It pairs with a SafeGuard
app on another phone using a 6-digit code, then shows that phone's last known
location on a map and the same safety alerts (left the safe zone, arrived at
or left a place, 30-minute dwell reminders).

This app is read-only: it never sends location anywhere, it only reads the
paired phone's last update and alert log.

## How to get the APK

Same as SafeGuard:

```
./gradlew assembleDebug
```

APK comes out at `app/build/outputs/apk/debug/app-debug.apk`. Or open this
folder in Android Studio and use Build > Build APK(s).

## Wiring up to the same backend

This app and SafeGuard must point at the same Supabase project. Run
`../safeguard/supabase/schema.sql` once in that project, then set the same
`SUPABASE_URL` and `SUPABASE_ANON_KEY` values for this app's build (see
SafeGuard's README for where those go).

## First run

1. Open the app, enter the 6-digit code shown on the tracked phone's
   "Pair guardian app" screen.
2. Tap Connect. Once the code is recognised you'll see the live map and get
   notified for every alert the tracked phone raises.
3. "Disconnect" on the live screen clears the code so you can re-pair.

## Tech

Same stack as SafeGuard: Kotlin, osmdroid (OpenStreetMap, no API key),
ViewBinding, Material 3. Polls Supabase every 15 seconds while the live
screen is open.
