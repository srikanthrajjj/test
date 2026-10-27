package com.safeguard.family

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Runs on HER phone. Always shows a notification, so sharing is never hidden.
 * - Sends location only when she moves 150 m+, or every 30 min (saves battery and free-tier quota).
 * - Sends an alert when she leaves or reaches Office / Home (each can be turned off).
 * - SOS: sends live location every 30 s for 15 min, until she taps "I'm safe".
 */
class LocationService : Service(), LocationListener {

    private lateinit var prefs: Prefs
    private lateinit var lm: LocationManager
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var lastFix: Location? = null
    private var started = false
    private val pendingCount = HashMap<String, Int>()

    private val heartbeat = object : Runnable {
        override fun run() {
            lastFix?.let { maybeSend(it, force = true) }
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private val sosTick = object : Runnable {
        override fun run() {
            if (!prefs.sosActive) { endSos(safe = false); return }
            lastFix?.let { publishLoc(it) }
            handler.postDelayed(this, SOS_TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        if (!started) {
            started = true
            startUpdates()
            handler.postDelayed(heartbeat, HEARTBEAT_MS)
            if (prefs.sosActive) handler.post(sosTick)   // service restarted mid-SOS
        }
        if (intent?.action == ACTION_SOS_STOP) {
            if (prefs.sosActive) endSos(safe = true)
            return START_STICKY
        }
        if (intent?.action == ACTION_SOS) {
            intent.takeIf { it.hasExtra("lat") }?.let {
                val l = Location("sos")
                l.latitude = it.getDoubleExtra("lat", 0.0)
                l.longitude = it.getDoubleExtra("lon", 0.0)
                if (lastFix == null) lastFix = l
            }
            sendSos()
        }
        return START_STICKY
    }

    private fun startUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) { stopSelf(); return }
        lm.removeUpdates(this)
        // Faster GPS during SOS
        val (interval, dist) = if (prefs.sosActive) 5_000L to 0f else 60_000L to 30f
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (lm.isProviderEnabled(p)) {
                try { lm.requestLocationUpdates(p, interval, dist, this, Looper.getMainLooper()) } catch (_: Exception) {}
                try { lm.getLastKnownLocation(p)?.let { onLocationChanged(it) } } catch (_: SecurityException) {}
            }
        }
    }

    override fun onLocationChanged(loc: Location) {
        val prev = lastFix
        // keep the better / newer fix
        if (prev != null && loc.time < prev.time - 120_000) return
        lastFix = loc
        if (loc.accuracy <= 150f) checkPlaces(loc)
        maybeSend(loc, force = false)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ---------- Geofence: left office / reached home ----------

    private fun checkPlaces(loc: Location) {
        for (place in listOf(Prefs.OFFICE, Prefs.HOME)) {
            if (!prefs.hasPlace(place)) continue
            val d = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, prefs.placeLat(place), prefs.placeLon(place), d)
            val dist = d[0]
            val inside = dist < Prefs.RADIUS_M
            val clearlyOutside = dist > Prefs.RADIUS_M + 60   // buffer so GPS jitter does not trigger alerts
            val state = prefs.placeState(place)

            when {
                state == 0 -> prefs.setPlaceState(place, if (inside) 1 else 2)   // first fix: just learn
                state == 1 && clearlyOutside -> confirm(place, loc, entering = false)
                state == 2 && inside -> confirm(place, loc, entering = true)
                else -> pendingCount[place] = 0
            }
        }
    }

    /** Needs 2 readings in a row before alerting, to avoid false alarms. */
    private fun confirm(place: String, loc: Location, entering: Boolean) {
        val n = (pendingCount[place] ?: 0) + 1
        pendingCount[place] = n
        if (n < 2) return
        pendingCount[place] = 0
        prefs.setPlaceState(place, if (entering) 1 else 2)

        if (prefs.alertOn(place, leaving = !entering)) {
            val who = prefs.name.ifBlank { "Family member" }
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val tag = if (place == Prefs.OFFICE) "office" else "house"
            if (entering) alert("$who reached $place", "Reached $place at $time.", loc, 3, tag)
            else alert("$who left $place", "Left $place at $time. Tap to see on map.", loc, 4, tag)
        }
        maybeSend(loc, force = true)
    }

    private fun sendSos() {
        prefs.sosUntil = System.currentTimeMillis() + SOS_MS
        startUpdates()
        handler.removeCallbacks(sosTick)
        handler.post(sosTick)
        refreshNotification()

        val loc = lastFix
        val who = prefs.name.ifBlank { "Family member" }
        if (loc != null) alert("SOS from $who", "$who needs help. Live location for 15 min. Tap to see on map.", loc, 5, "rotating_light")
        else {
            val code = prefs.code ?: return
            io.execute {
                Ntfy.publish(Ntfy.alertTopic(code), "$who needs help. Location not available yet.",
                    "SOS from $who", 5, "rotating_light")
            }
        }
    }

    /** Ends live SOS tracking. safe = she tapped "I'm safe"; otherwise the 15 min ran out. */
    private fun endSos(safe: Boolean) {
        prefs.sosUntil = 0L
        handler.removeCallbacks(sosTick)
        startUpdates()
        refreshNotification()
        val code = prefs.code ?: return
        val who = prefs.name.ifBlank { "Family member" }
        lastFix?.let { publishLoc(it) }
        io.execute {
            if (safe) Ntfy.publish(Ntfy.alertTopic(code), "$who stopped SOS and says they're safe.", "$who is safe", 4, "white_check_mark")
            else Ntfy.publish(Ntfy.alertTopic(code), "Live SOS tracking ended after 15 min. Normal sharing continues.", "SOS tracking ended", 3, "hourglass")
        }
    }

    private fun alert(title: String, body: String, loc: Location, priority: Int, tag: String) {
        val code = prefs.code ?: return
        val map = mapLink(loc.latitude, loc.longitude)
        io.execute { Ntfy.publish(Ntfy.alertTopic(code), body, title, priority, tag, map) }
    }

    // ---------- Quiet location updates for the map ----------

    private fun maybeSend(loc: Location, force: Boolean) {
        if (prefs.sosActive) return   // sosTick sends every 30 s
        val now = System.currentTimeMillis()
        if (!force) {
            val lat = prefs.lastSentLat
            if (!lat.isNaN()) {
                val d = FloatArray(1)
                Location.distanceBetween(lat, prefs.lastSentLon, loc.latitude, loc.longitude, d)
                val tooSoon = now - prefs.lastSentTime < HEARTBEAT_MS
                if (d[0] < MOVE_M && tooSoon) return
            }
        }
        if (now - prefs.lastSentTime < MIN_GAP_MS) return
        publishLoc(loc)
    }

    private fun publishLoc(loc: Location) {
        val code = prefs.code ?: return
        val now = System.currentTimeMillis()
        prefs.lastSentLat = loc.latitude
        prefs.lastSentLon = loc.longitude
        prefs.lastSentTime = now

        val json = JSONObject()
            .put("lat", loc.latitude)
            .put("lon", loc.longitude)
            .put("acc", loc.accuracy.toInt())
            .put("t", now)
            .put("bat", batteryPct())
            .put("name", prefs.name)
            .put("sos", prefs.sosActive)
            .toString()
        io.execute { Ntfy.publish(Ntfy.locTopic(code), json, priority = 1) }
    }

    private fun batteryPct(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, SharerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL_SHARING)
            .setSmallIcon(R.drawable.ic_pin)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (prefs.sosActive) {
            val safe = PendingIntent.getService(
                this, 1, Intent(this, LocationService::class.java).setAction(ACTION_SOS_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.setContentTitle("SOS on: sending live location")
                .setContentText("Family sees you every 30 s for 15 min")
                .addAction(0, "I'm safe", safe)
        } else {
            b.setContentTitle("Sharing location with family")
                .setContentText("Tap to open SafeGuard or pause sharing")
        }
        return b.build()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        handler.removeCallbacks(sosTick)
        try { lm.removeUpdates(this) } catch (_: Exception) {}
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SOS = "com.safeguard.family.SOS"
        const val ACTION_SOS_STOP = "com.safeguard.family.SOS_STOP"
        const val SOS_MS = 15 * 60_000L
        private const val SOS_TICK_MS = 30_000L
        private const val CHANNEL_SHARING = "sharing"
        private const val NOTIF_ID = 7
        private const val HEARTBEAT_MS = 30 * 60_000L
        private const val MIN_GAP_MS = 60_000L
        private const val MOVE_M = 150f

        fun mapLink(lat: Double, lon: Double) =
            "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon"

        fun createChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_SHARING, "Location sharing", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LocationService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LocationService::class.java))
        }

        fun sos(ctx: Context, lat: Double?, lon: Double?) {
            val i = Intent(ctx, LocationService::class.java).setAction(ACTION_SOS)
            if (lat != null && lon != null) i.putExtra("lat", lat).putExtra("lon", lon)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stopSos(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LocationService::class.java).setAction(ACTION_SOS_STOP))
        }
    }
}
