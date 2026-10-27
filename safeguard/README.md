# SafeGuard

Family safety app. No more "share your location?" messages.

## What it does

- **Her phone:** shares location. Sends an alert when she **leaves or reaches** Home or Office (200 m circle). Each alert can be turned off in **Alert settings**.
- **Hands-free SOS:** shake the phone hard, or press power 5 times. The phone buzzes when SOS goes out. Either can be turned off in **Alerts & SOS settings**.
- **Route:** your phone can show where she went today (up to the last 12 h).
- **SOS:** urgent alert, then live location every 30 s for 15 min. **"I'm safe"** (on screen or in the notification) stops it and tells family.
- **Your phone:** shows her on the map, battery level, last update. **Directions** opens Google Maps or any map app.
- Map: **OpenStreetMap** (free, no API key).
- Alerts: **ntfy** (free, open-source push app, no account).
- Sharing is always visible: a notification shows while on. She can pause anytime.

## Get the APK (no coding, about 10 minutes)

1. Make a free account on github.com.
2. Create a new repository (Private is fine). Click **"uploading an existing file"**.
3. Drag in **everything inside** this SafeGuard folder (including the hidden `.github` folder). Commit.
   - Tip: on Mac press `Cmd + Shift + .` to see hidden folders.
4. Open the **Actions** tab. Wait for the green tick (about 5 to 8 minutes).
5. Open **Releases** (right side of the repo page). Download **SafeGuard.apk**.

Or: open this folder in **Android Studio** > Build > Build APK(s).

## Set up (2 phones)

**Her phone**
1. Install SafeGuard.apk (allow "Install unknown apps" when asked).
2. Tap "I'll share my location", enter her name.
3. Turn sharing on. Allow location **"All the time"**.
4. Set battery to **Unrestricted** for SafeGuard (Settings > Apps > SafeGuard > Battery).
5. At office tap **Set Office here** (or long-press the map on the office). Same for Home.
6. Tap **Send** next to the family code. Send it to you on WhatsApp.

**Your phone**
1. Install SafeGuard.apk. Tap "I want to see family". Enter the code.
2. Install **ntfy** from Play Store. Back in SafeGuard tap **Alerts > Subscribe**.
3. Done. You get "Priya left office", "Priya reached home" and SOS alerts automatically.

## How it works

```
Her phone (GPS) --> ntfy.sh --> Your phone
   - location update (quiet, for the map)
   - "Left office" / "Reached home" / "SOS" (alert, via ntfy app)
```

- Office/Home circle = 200 m. Needs 2 GPS readings in a row before alerting (stops false alarms).
- Location sent only when she moves 150 m+, or every 30 min. Easy on battery and free limits.

## Good to know

- The 4-digit family code is the only key. Anyone with the code, or who guesses it, can see the location. Keep it private.
  To change it: Reset app on both phones.
- ntfy.sh free use has daily limits. Normal use (one person) stays well under. For many users (Play Store), run your own ntfy server or move to Firebase/Supabase.
- Play Store: background location apps need a Google review and a privacy policy. Fine for personal use now.

## Files

- `LocationService.kt` : runs on her phone. GPS, office/home alerts, SOS.
- `SharerActivity.kt` : her screen.
- `ViewerActivity.kt` : your screen.
- `Ntfy.kt` : sends and reads messages.
