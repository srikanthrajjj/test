package com.safeguard.guardian

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.safeguard.guardian.databinding.ActivityLiveBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

/**
 * Live view of the paired phone: its last known location on a map, and any
 * new safety alerts, polled from Supabase every 15 seconds while this screen
 * is open. This app never writes anything -- it only reads the other
 * device's location and alerts, which that device chose to share by showing
 * its pairing code.
 */
class LiveActivity : AppCompatActivity() {

    private lateinit var b: ActivityLiveBinding
    private val main = Handler(Looper.getMainLooper())
    private var polling = false
    private val code get() = Store.pairingCode(this)

    private val pollTask = object : Runnable {
        override fun run() {
            if (!polling) return
            poll()
            main.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osm", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        NotificationHelper.createChannel(this)

        b = ActivityLiveBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = "Live"

        b.map.setTileSource(TileSourceFactory.MAPNIK)
        b.map.setMultiTouchControls(true)
        b.map.controller.setZoom(15.0)
        b.map.controller.setCenter(GeoPoint(12.9716, 77.5946))

        b.statusTitle.text = "Code: $code"
        b.changeCode.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Disconnect?")
                .setMessage("You'll need to enter the code again to reconnect.")
                .setPositiveButton("Disconnect") { _, _ ->
                    Store.setPairingCode(this, "")
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        b.map.onResume()
        polling = true
        pollTask.run()
    }

    override fun onPause() {
        super.onPause()
        b.map.onPause()
        polling = false
        main.removeCallbacks(pollTask)
    }

    private fun poll() {
        val c = code
        if (c.isBlank()) return
        Thread {
            val loc = SupabaseApi.latestLocation(c)
            val alerts = SupabaseApi.newAlerts(c, Store.lastAlertId(this))
            main.post {
                if (loc != null) {
                    b.map.overlays.clear()
                    val m = Marker(b.map)
                    m.position = GeoPoint(loc.lat, loc.lon)
                    m.title = "Last seen"
                    b.map.overlays.add(m)
                    b.map.controller.setCenter(GeoPoint(loc.lat, loc.lon))
                    b.map.invalidate()
                    b.statusSub.text = "Last update: ${loc.updatedAt}"
                } else {
                    b.statusSub.text = "Waiting for the first location update..."
                }
                for (a in alerts) {
                    NotificationHelper.alert(this, a.title, a.body)
                    Store.setLastAlertId(this, a.id)
                }
            }
        }.start()
    }
}
