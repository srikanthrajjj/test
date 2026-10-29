package com.safeguard.family

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** HER screen: turn sharing on, set Office / Home, send SOS. */
class SharerActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var map: MapView
    private lateinit var sw: MaterialSwitch
    private lateinit var status: TextView
    private var me: MyLocationNewOverlay? = null
    private val placeCircles = HashMap<String, Polygon>()
    private var centered = false

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasFine()) { showMe(); askNotificationsThenBackground() }
        else { sw.isChecked = false; toast("Location permission is needed to share") }
    }
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        askBackground()
    }
    private val askBg = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startSharing()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_sharer)
        Ui.applyInsets(findViewById(R.id.card), top = false)
        Ui.applyInsets(findViewById(R.id.topBar), top = true)

        map = findViewById(R.id.map)
        Ui.setupMap(this, map)
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { pickPlace(it.latitude, it.longitude) }; return true
            }
        }))
        drawPlaces()
        if (hasFine()) showMe()

        sw = findViewById(R.id.switchShare)
        status = findViewById(R.id.statusText)
        sw.isChecked = prefs.sharing
        sw.setOnCheckedChangeListener { _, on ->
            if (on && !prefs.sharing) beginSharingFlow()
            else if (!on) stopSharing()
        }
        updateStatus()

        findViewById<TextView>(R.id.codeText).text = Prefs.pretty(prefs.code ?: "")
        findViewById<Button>(R.id.btnShareCode).setOnClickListener { shareCode() }
        findViewById<Button>(R.id.btnOffice).setOnClickListener { setPlaceHere(Prefs.OFFICE) }
        findViewById<Button>(R.id.btnHome).setOnClickListener { setPlaceHere(Prefs.HOME) }
        findViewById<Button>(R.id.btnSos).setOnClickListener { confirmSos() }
        findViewById<Button>(R.id.btnSafe).setOnClickListener {
            if (!hasFine()) { toast("Turn on sharing first"); return@setOnClickListener }
            prefs.sharing = true
            sw.isChecked = true
            LocationService.checkIn(this)
            toast("Sent: you're safe")
        }
        findViewById<TextView>(R.id.btnReset).setOnClickListener { confirmReset() }
        findViewById<View>(R.id.btnCenter).setOnClickListener {
            me?.myLocation?.let { map.controller.animateTo(it, 17.0, 600L) } ?: toast("Finding your location…")
        }

        if (prefs.sharing && hasFine()) LocationService.start(this)
    }

    // ---------- Sharing on / off ----------

    private fun beginSharingFlow() {
        if (!hasFine()) {
            AlertDialog.Builder(this)
                .setTitle("Share your location")
                .setMessage("Your family will see where you are and get an alert when you leave office or reach home.\n\nYou can pause anytime. A notification always shows while sharing.")
                .setPositiveButton("Continue") { _, _ ->
                    askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                .setNegativeButton("Cancel") { _, _ -> sw.isChecked = false }
                .setCancelable(false)
                .show()
        } else askNotificationsThenBackground()
    }

    private fun askNotificationsThenBackground() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        else askBackground()
    }

    private fun askBackground() {
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle("One more step")
                .setMessage("So alerts work when the phone is in your bag, choose \"Allow all the time\" on the next screen.")
                .setPositiveButton("OK") { _, _ -> askBg.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                .setNegativeButton("Skip") { _, _ -> startSharing() }
                .show()
        } else startSharing()
    }

    private fun startSharing() {
        prefs.sharing = true
        LocationService.start(this)
        updateStatus()
        maybeAskBattery()
    }

    private fun stopSharing() {
        prefs.sharing = false
        LocationService.stop(this)
        updateStatus()
    }

    private fun updateStatus() {
        status.text = when {
            !prefs.sharing -> "Paused. Family can't see you."
            !prefs.hasPlace(Prefs.OFFICE) -> "On. Set your Office below to get leave alerts."
            else -> "On. Family gets an alert when you leave office."
        }
    }

    /** Phone brands like Xiaomi, Oppo, Vivo, Samsung kill background apps. */
    private fun maybeAskBattery() {
        val sp = getSharedPreferences("safeguard", MODE_PRIVATE)
        if (sp.getBoolean("asked_battery", false)) return
        sp.edit().putBoolean("asked_battery", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Keep alerts reliable")
            .setMessage("On the next screen, find SafeGuard and set battery to \"Unrestricted\" or \"Don't optimise\". Otherwise the phone may stop alerts.")
            .setPositiveButton("Open settings") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    // ---------- Office / Home ----------

    private fun setPlaceHere(place: String) {
        val loc = me?.myLocation
        if (loc == null) {
            toast(if (hasFine()) "Finding your location… try again in a moment" else "Turn on sharing first")
            return
        }
        savePlace(place, loc.latitude, loc.longitude)
    }

    private fun pickPlace(lat: Double, lon: Double) {
        AlertDialog.Builder(this)
            .setTitle("Save this spot as")
            .setItems(arrayOf("Office", "Home")) { _, which ->
                savePlace(if (which == 0) Prefs.OFFICE else Prefs.HOME, lat, lon)
            }
            .show()
    }

    private fun savePlace(place: String, lat: Double, lon: Double) {
        prefs.setPlace(place, lat, lon)
        drawPlaces()
        updateStatus()
        toast(if (place == Prefs.OFFICE) "Office saved" else "Home saved")
        if (prefs.sharing) LocationService.start(this)
    }

    private fun drawPlaces() {
        placeCircles.values.forEach { map.overlays.remove(it) }
        placeCircles.clear()
        for ((place, color) in listOf(Prefs.OFFICE to Color.parseColor("#4F46E5"), Prefs.HOME to Color.parseColor("#059669"))) {
            if (!prefs.hasPlace(place)) continue
            val c = Ui.circle(map, prefs.placeLat(place), prefs.placeLon(place), Prefs.RADIUS_M, color)
            c.title = if (place == Prefs.OFFICE) "Office" else "Home"
            placeCircles[place] = c
            map.overlays.add(1.coerceAtMost(map.overlays.size), c)
        }
        findViewById<Button>(R.id.btnOffice).text = if (prefs.hasPlace(Prefs.OFFICE)) "Office ✓" else "Set Office here"
        findViewById<Button>(R.id.btnHome).text = if (prefs.hasPlace(Prefs.HOME)) "Home ✓" else "Set Home here"
        map.invalidate()
    }

    // ---------- Map dot ----------

    private fun showMe() {
        if (me != null) return
        val o = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        o.enableMyLocation()
        o.runOnFirstFix {
            runOnUiThread {
                if (!centered) { centered = true; o.myLocation?.let { map.controller.animateTo(it, 16.0, 800L) } }
            }
        }
        map.overlays.add(o)
        me = o
    }

    // ---------- Code, SOS, reset ----------

    private fun shareCode() {
        val text = "I'm sharing my location with you on SafeGuard.\n\n" +
            "1. Install SafeGuard and choose \"I want to see family\".\n" +
            "2. Enter this family code: ${Prefs.pretty(prefs.code ?: "")}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Send code"))
    }

    private fun confirmSos() {
        AlertDialog.Builder(this)
            .setTitle("Send SOS?")
            .setMessage("Your family gets an urgent alert with your location.")
            .setPositiveButton("Send SOS") { _, _ ->
                if (!hasFine()) { toast("Location permission needed"); return@setPositiveButton }
                prefs.sharing = true
                sw.isChecked = true
                val l = me?.myLocation
                LocationService.sos(this, l?.latitude, l?.longitude)
                updateStatus()
                toast("SOS sent")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset app?")
            .setMessage("Stops sharing and removes your family code. Family will need a new code.")
            .setPositiveButton("Reset") { _, _ ->
                LocationService.stop(this)
                prefs.clear()
                startActivity(Intent(this, MainActivity::class.java)); finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasFine() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onResume() { super.onResume(); map.onResume(); me?.enableMyLocation() }
    override fun onPause() { super.onPause(); map.onPause(); me?.disableMyLocation() }
}
