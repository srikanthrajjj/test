# SafeGuard

A consent-based safety app. It watches a "safe zone" and places you choose,
and alerts the phone (and optionally a paired guardian app, and optionally an
SMS to a guardian's phone number) when the person arrives, leaves, or stays
too long. Map is OpenStreetMap, so there is NO Google Maps key and NO billing.

This is the app installed **on the tracked person's phone**, openly, with
their knowledge. The companion app for the guardian's own phone is
`safeguard-guardian/` next to this folder.

## What it does

- Shows a clean map with one status card. Nothing crowded.
- Search a place, company or school by name (OpenStreetMap, no typing of
  coordinates) and pick a radius: 200 m / 300 m / 500 m / 1 km. That becomes
  the safe zone.
- Add more places the same way (restaurants, bars, cinemas, a school...),
  each with its own radius and "alert on arrive" or "alert on leave".
- Alerts once on crossing the safe zone's boundary, and again if still
  outside it after 30 minutes (a dwell reminder), without repeating every
  update.
- Alerts if the person stays more than 30 minutes at any watched place.
- Runs as a foreground service with a permanent "SafeGuard is on"
  notification -- the allowed, visible way to track in the background.
  This app is never hidden: it always has its own icon, name, and that
  notification while active.
- Optional: pair a SafeGuard Guardian app on another phone (6-digit code) so
  it can see live location and the same alerts.
- Optional: also text a guardian's phone number directly, with a map link,
  no pairing needed.

## Important: keep it legal

- Install it openly on the phone being tracked. The person must know and agree.
- Tracking an adult without consent is illegal. This app is built the open, consented way.
- SEND_SMS is a sensitive permission. Fine for personal use. For a Play Store release you
  must declare why you use it, or remove it and send alerts another way.

## How to get the APK (about 5 minutes)

You need Android Studio once. It downloads the Android SDK and all libraries for you.

1. Install Android Studio (free): https://developer.android.com/studio
2. Open Android Studio, choose "Open", and select this `SafeGuard` folder.
3. Wait for it to sync (it fetches osmdroid, Material, Play Services location, OkHttp).
4. Plug in your phone with USB debugging on, OR use the menu:
   Build > Build Bundle(s) / APK(s) > Build APK(s).
5. When it finishes, click "locate". The file is:
   `app/build/outputs/apk/debug/app-debug.apk`
6. Copy that APK to your phone and install it (allow "install from unknown sources").

### Build from the command line instead

From this folder:

```
./gradlew assembleDebug
```

APK comes out at `app/build/outputs/apk/debug/app-debug.apk`.

## Wiring up live pairing (optional)

Pairing and live location sharing with the Guardian app need a small backend.
This project uses Supabase (Postgres + a simple REST API):

1. Create a free Supabase project.
2. Open its SQL editor and run `supabase/schema.sql` from this folder.
3. Add two values before building, either in `gradle.properties`
   (`SUPABASE_URL=...`, `SUPABASE_ANON_KEY=...`) or as `-P` flags to Gradle.
4. Build the same way for `safeguard-guardian/` with the *same* two values.

If you skip this, the app still works fully for safe-zone and place alerts
and SMS -- pairing just won't connect until it's configured.

## First run on the phone

1. Allow location. Choose "Allow all the time" when asked (needed for background alerts).
2. Allow notifications.
3. Tap "Set safe zone", search for home, your office, or school, pick it, then pick a radius.
4. Tap "Places" and add more spots with the + button (also by search).
5. Optional: tap "Add guardian" to text a phone number on every alert.
6. Optional: tap "Pair guardian app" and enter the shown 6-digit code into
   SafeGuard Guardian on another phone.
7. Tap "Start protection".

## Phase 2, done

- Two phones: this app (tracked) and `safeguard-guardian/` (guardian),
  paired by a 6-digit code, with live location + alerts relayed through
  Supabase.
- 30-minute dwell alerts, both inside a place and outside the safe zone.
- Pick a place by searching its name instead of typing lat/lon.

Not built: an SOS button, and encrypting/limiting who can read a pairing
code beyond Supabase's row-level security (see the note at the top of
`supabase/schema.sql`).

## Tech

- Kotlin, single module, min Android 7.0 (API 24).
- Map: osmdroid (OpenStreetMap), no API key.
- Location: Google Play Services Fused Location (accurate, battery friendly).
- Storage: SharedPreferences (small JSON) for local settings. Supabase (Postgres
  via REST) only for the pairing code, last location, and alert log.
