package com.safeguard.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.*

/**
 * Foreground service. This is the ALLOWED way to keep location alive in the
 * background (not "silent listening"). The user sees a permanent notification,
 * which is exactly what Google requires.
 */
class TrackingService : LifecycleService() {

    companion object {
        private const val DWELL_MS = 30 * 60 * 1000L // 30 minutes
    }

    private lateinit var client: FusedLocationProviderClient
    private var callback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        NotificationHelper.createChannels(this)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Promote to foreground immediately (required within a few seconds of start).
        val n = NotificationHelper.serviceNotification(this, "Watching your safe zone")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.SERVICE_ID,
                n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.SERVICE_ID, n)
        }

        startUpdates()
        Store.setTracking(this, true)
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 20_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .setMinUpdateDistanceMeters(25f)
            .build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { check(it) }
            }
        }
        client.requestLocationUpdates(req, callback!!, Looper.getMainLooper())
    }

    /** Core rule engine: compare current spot to the safe zone and to every place. */
    private fun check(loc: Location) {
        val ctx = this
        val now = System.currentTimeMillis()

        // Keep the paired guardian app's map current.
        val code = Store.pairingCode(ctx)
        if (code.isNotBlank()) {
            Thread { SupabaseApi.pushLocation(code, loc.latitude, loc.longitude) }.start()
        }

        // 1) Safe zone: alert on exit, then a once-only reminder after 30 min still outside.
        Store.safeZone(ctx)?.let { (lat, lon, radius) ->
            val d = distance(loc.latitude, loc.longitude, lat, lon)
            val inside = d <= radius
            val wasInside = Store.wasInsideSafeZone(ctx)

            if (wasInside && !inside) {
                fire("Left the safe zone", "Now about ${d.toInt()} m from the safe zone.", loc)
                Store.setSafeZoneOutsideSince(ctx, now)
                Store.setSafeZoneDwellAlerted(ctx, false)
            }
            if (inside) {
                Store.setSafeZoneOutsideSince(ctx, 0L)
                Store.setSafeZoneDwellAlerted(ctx, false)
            } else {
                val since = Store.safeZoneOutsideSince(ctx)
                if (since > 0 && !Store.safeZoneDwellAlerted(ctx) && now - since >= DWELL_MS) {
                    fire(
                        "Still outside the safe zone",
                        "Has been outside the safe zone for over 30 minutes.",
                        loc
                    )
                    Store.setSafeZoneDwellAlerted(ctx, true)
                }
            }
            Store.setInsideSafeZone(ctx, inside)
        }

        // 2) Places (arrive / leave), plus a once-only ">30 min here" reminder.
        val currentlyInside = Store.insideSet(ctx)
        val nextInside = HashSet<String>()
        for (p in Store.places(ctx)) {
            val d = distance(loc.latitude, loc.longitude, p.lat, p.lon)
            val inside = d <= p.radiusM
            if (inside) nextInside.add(p.id)
            val wasInside = currentlyInside.contains(p.id)

            if (p.mode == "arrive" && inside && !wasInside) {
                fire("Arrived: ${p.name}", "Reached ${p.name}.", loc)
            }
            if (p.mode == "leave" && !inside && wasInside) {
                fire("Left: ${p.name}", "Left ${p.name}.", loc)
            }

            if (inside && !wasInside) {
                Store.setPlaceInsideSince(ctx, p.id, now)
                Store.setPlaceDwellAlerted(ctx, p.id, false)
            } else if (inside) {
                val since = Store.placeInsideSince(ctx, p.id)
                if (since > 0 && !Store.placeDwellAlerted(ctx, p.id) && now - since >= DWELL_MS) {
                    fire("Still at ${p.name}", "Has been at ${p.name} for over 30 minutes.", loc)
                    Store.setPlaceDwellAlerted(ctx, p.id, true)
                }
            } else {
                Store.setPlaceInsideSince(ctx, p.id, 0L)
                Store.setPlaceDwellAlerted(ctx, p.id, false)
            }
        }
        Store.setInsideSet(ctx, nextInside)
    }

    /** Notify on the phone, push to the paired guardian app, and optionally text a guardian. */
    private fun fire(title: String, body: String, loc: Location) {
        NotificationHelper.alert(this, title, body)

        val code = Store.pairingCode(this)
        if (code.isNotBlank()) {
            Thread {
                SupabaseApi.pushAlert(code, title, body, loc.latitude, loc.longitude)
            }.start()
        }

        val phone = Store.guardian(this)
        if (phone.isNotBlank() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val link = "https://www.openstreetmap.org/?mlat=${loc.latitude}&mlon=${loc.longitude}#map=17/${loc.latitude}/${loc.longitude}"
            val msg = "SafeGuard: $title. $body Location: $link"
            try {
                @Suppress("DEPRECATION")
                SmsManager.getDefault().sendTextMessage(phone, null, msg, null, null)
            } catch (_: Exception) {
            }
        }
    }

    private fun distance(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(aLat, aLon, bLat, bLon, r)
        return r[0]
    }

    override fun onDestroy() {
        callback?.let { client.removeLocationUpdates(it) }
        Store.setTracking(this, false)
        super.onDestroy()
    }
}
