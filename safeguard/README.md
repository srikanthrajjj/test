# SafeGuard

A simple, consent based safety app. It watches a "safe zone" and places you choose,
and alerts the phone (and optionally texts a guardian) when the person arrives or leaves.
Map is OpenStreetMap, so there is NO Google Maps key and NO billing.

## What it does now (works offline, no server)

- Shows a clean map with one status card. Nothing crowded.
- Set a safe zone: a circle around home, office, or campus.
- Add places: restaurants, bars, cinemas, anything. Alert on arrive or on leave.
- Runs as a foreground service (the allowed way to track in the background).
- Optional: text a guardian's phone with a map link when an alert fires. No server needed.

## Important: keep it legal

- Install it openly on the phone being tracked. The person must know and agree.
- Tracking an adult without consent is illegal. This app is built the open, consented way.
- SEND_SMS is a sensitive permission. Fine for personal use. For a Play Store release you
  must declare why you use it, or remove it and send alerts another way.

## How to get the APK (about 5 minutes)

You need Android Studio once. It downloads the Android SDK and all libraries for you.

1. Install Android Studio (free): https://developer.android.com/studio
2. Open Android Studio, choose "Open", and select this `SafeGuard` folder.
3. Wait for it to sync (it fetches osmdroid, Material, Play Services location).
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

## First run on the phone

1. Allow location. Choose "Allow all the time" when asked (needed for background alerts).
2. Allow notifications.
3. Tap "Set safe zone", pan the map so the centre is your home, pick a radius.
4. Tap "Places" and add the spots you care about (name, lat, lon, radius).
   Tip: long tap a spot in Google Maps or OSM to copy its lat, lon.
5. Optional: tap "Add guardian" and enter a phone number for SMS alerts.
6. Tap "Start protection".

## What is NOT built yet (phase 2)

- Guardian and dependent on two different phones with live map sharing.
  That needs a small backend. Supabase or Firebase both fit. The current app
  alerts the tracked phone and can SMS a guardian, which already covers the core.
- SOS button.
- Picking a place by tapping the map (right now you type lat, lon).

## Tech

- Kotlin, single module, min Android 7.0 (API 24).
- Map: osmdroid (OpenStreetMap), no API key.
- Location: Google Play Services Fused Location (accurate, battery friendly).
- Storage: SharedPreferences (small JSON). No account, no server.
