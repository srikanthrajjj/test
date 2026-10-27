package com.safeguard.family

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Calendar
import java.util.concurrent.Executors

/** YOUR screen: see her on the map, get directions, turn on instant alerts. */
class ViewerActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var map: MapView
    private lateinit var nameView: TextView
    private lateinit var sub: TextView
    private var marker: Marker? = null
    private var route: Polyline? = null
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastTime = 0L
    private var lastBat = -1
    private var sos = false
    private var subColor = 0
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 30_000L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_viewer)
        Ui.applyInsets(findViewById(R.id.card), top = false)
        Ui.applyInsets(findViewById(R.id.topBar), top = true)

        map = findViewById(R.id.map)
        Ui.setupMap(this, map)
        nameView = findViewById(R.id.personName)
        sub = findViewById(R.id.personStatus)
        subColor = sub.currentTextColor
        nameView.text = prefs.name.ifBlank { "Family" }
        sub.text = "Looking for location…"

        findViewById<Button>(R.id.btnDirections).setOnClickListener { directions() }
        findViewById<Button>(R.id.btnAlerts).setOnClickListener { setupAlerts() }
        findViewById<Button>(R.id.btnRoute).setOnClickListener { toggleRoute() }
        findViewById<TextView>(R.id.btnReset).setOnClickListener { confirmReset() }
        findViewById<android.view.View>(R.id.btnCenter).setOnClickListener {
            val la = lastLat; val lo = lastLon
            if (la != null && lo != null) map.controller.animateTo(GeoPoint(la, lo), 16.0, 600L) else refresh()
        }

        val sp = getSharedPreferences("safeguard", MODE_PRIVATE)
        if (!sp.getBoolean("alerts_intro", false)) {
            sp.edit().putBoolean("alerts_intro", true).apply()
            setupAlerts()
        }
    }

    private fun refresh() {
        val code = prefs.code ?: return
        io.execute {
            val msg = Ntfy.latest(Ntfy.locTopic(code))
            runOnUiThread { show(msg) }
        }
    }

    private fun show(msg: String?) {
        if (msg == null) {
            if (lastTime == 0L) sub.text = "No location yet. Ask ${prefs.name.ifBlank { "them" }} to turn on sharing."
            else updateSub()
            return
        }
        try {
            val o = JSONObject(msg)
            val lat = o.getDouble("lat"); val lon = o.getDouble("lon")
            val first = lastLat == null
            lastLat = lat; lastLon = lon; lastTime = o.optLong("t", System.currentTimeMillis())
            lastBat = o.optInt("bat", -1)
            // Ignore an old SOS flag if updates stopped (phone off, no signal)
            sos = o.optBoolean("sos") && System.currentTimeMillis() - lastTime < 3 * 60_000L
            val name = o.optString("name").ifBlank { prefs.name }
            nameView.text = name

            val p = GeoPoint(lat, lon)
            val m = marker ?: Marker(map).also {
                it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                it.icon = ContextCompat.getDrawable(this, R.drawable.ic_person_pin)
                map.overlays.add(it); marker = it
            }
            m.position = p
            m.title = name
            if (first) map.controller.animateTo(p, 16.0, 800L)
            map.invalidate()
            updateSub()
        } catch (_: Exception) {}
    }

    private fun updateSub() {
        val parts = mutableListOf("Updated ${Ui.ago(lastTime)}")
        if (lastBat >= 0) parts.add("Battery $lastBat%")
        if (sos) parts.add(0, "SOS: LIVE")
        sub.text = parts.joinToString("  ·  ")
        sub.setTextColor(if (sos) ContextCompat.getColor(this, R.color.sos) else subColor)
    }

    /** Draws where she went today (since midnight, up to the last 12 h). Tap again to hide. */
    private fun toggleRoute() {
        val btn = findViewById<Button>(R.id.btnRoute)
        route?.let { map.overlays.remove(it); route = null; map.invalidate(); btn.text = "Route"; return }
        val code = prefs.code ?: return
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000
        btn.text = "Loading…"
        io.execute {
            val pts = Ntfy.history(Ntfy.locTopic(code), midnight).mapNotNull {
                try {
                    val o = JSONObject(it)
                    if (o.optInt("acc", 0) > 200) null else GeoPoint(o.getDouble("lat"), o.getDouble("lon"))
                } catch (_: Exception) { null }
            }
            runOnUiThread {
                if (pts.size < 2) { btn.text = "Route"; toast("No route yet today"); return@runOnUiThread }
                val line = Polyline(map).apply {
                    setPoints(pts)
                    outlinePaint.color = ContextCompat.getColor(this@ViewerActivity, R.color.primary)
                    outlinePaint.strokeWidth = 10f
                }
                map.overlays.add(0.coerceAtLeast(map.overlays.size - 1), line)   // under her pin
                route = line
                btn.text = "Hide route"
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts).increaseByScale(1.3f), true)
            }
        }
    }

    /** Opens Google Maps, or any map app the phone has. */
    private fun directions() {
        val la = lastLat; val lo = lastLon
        if (la == null || lo == null) { toast("No location yet"); return }
        val label = Uri.encode(prefs.name.ifBlank { "Family" })
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$la,$lo?q=$la,$lo($label)")))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LocationService.mapLink(la, lo))))
        }
    }

    /**
     * Instant alerts ("Left office", "Reached home", "SOS") come through the free ntfy app.
     * It is open-source and delivers pushes reliably even when SafeGuard is closed.
     */
    private fun setupAlerts() {
        val code = prefs.code ?: return
        AlertDialog.Builder(this)
            .setTitle("Get instant alerts")
            .setMessage("You'll get a notification when ${prefs.name.ifBlank { "they" }} leaves or reaches home or office, or sends SOS.\n\n" +
                "1. Install the free \"ntfy\" app.\n2. Come back and tap \"Subscribe\".")
            .setPositiveButton("Subscribe") { _, _ -> subscribe(code) }
            .setNeutralButton("Install ntfy") { _, _ -> openStore() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun subscribe(code: String) {
        val topicUrl = Ntfy.alertUrl(code)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("ntfy topic", Ntfy.alertTopic(code)))
        try {
            val display = Uri.encode("${prefs.name.ifBlank { "Family" }} alerts")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("ntfy://ntfy.sh/${Ntfy.alertTopic(code)}?display=$display")))
            toast("Tap Subscribe in ntfy")
        } catch (_: ActivityNotFoundException) {
            toast("Install ntfy first. Topic copied: you can also paste it in ntfy.")
            openStore()
        }
    }

    private fun openStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=io.heckel.ntfy")))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=io.heckel.ntfy")))
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset app?")
            .setMessage("Removes the family code from this phone.")
            .setPositiveButton("Reset") { _, _ ->
                prefs.clear()
                startActivity(Intent(this, MainActivity::class.java)); finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onResume() { super.onResume(); map.onResume(); handler.post(poll) }
    override fun onPause() { super.onPause(); map.onPause(); handler.removeCallbacks(poll) }
    override fun onDestroy() { io.shutdown(); super.onDestroy() }
}
