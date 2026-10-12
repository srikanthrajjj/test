package com.safeguard.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.safeguard.app.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val data = res.data ?: return@registerForActivityResult
        val name = data.getStringExtra("name") ?: return@registerForActivityResult
        val lat = data.getDoubleExtra("lat", 0.0)
        val lon = data.getDoubleExtra("lon", 0.0)
        val radius = data.getIntExtra("radius", 300)
        Store.setSafeZone(this, lat, lon, radius)
        Store.setInsideSafeZone(this, true)
        Toast.makeText(this, "Safe zone set: $name", Toast.LENGTH_SHORT).show()
        b.map.controller.setCenter(org.osmdroid.util.GeoPoint(lat, lon))
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance()
            .load(this, getSharedPreferences("osm", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.map.setTileSource(TileSourceFactory.MAPNIK)
        b.map.setMultiTouchControls(true)
        b.map.controller.setZoom(16.0)

        askPermissions()

        b.toggle.setOnClickListener { onToggle() }
        b.btnSafeZone.setOnClickListener {
            searchLauncher.launch(Intent(this, SearchPlaceActivity::class.java))
        }
        b.btnPlaces.setOnClickListener {
            startActivity(Intent(this, PlacesActivity::class.java))
        }
        b.btnGuardian.setOnClickListener { editGuardian() }
        b.btnPair.setOnClickListener {
            startActivity(Intent(this, PairingActivity::class.java))
        }

        centerStart()
    }

    override fun onResume() {
        super.onResume()
        b.map.onResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        b.map.onPause()
    }

    // ---------- permissions ----------
    private fun askPermissions() {
        val need = ArrayList<String>()
        need.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(need.toTypedArray())
    }

    private fun askBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle("Allow all the time")
                .setMessage("To alert you even when the app is closed, set location to \"Allow all the time\" on the next screen.")
                .setPositiveButton("Continue") { _, _ ->
                    permLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // ---------- start / stop ----------
    private fun onToggle() {
        if (!hasLocation()) {
            askPermissions()
            return
        }
        if (Store.isTracking(this)) {
            stopService(Intent(this, TrackingService::class.java))
            Store.setTracking(this, false)
        } else {
            if (!Store.hasSafeZone(this)) {
                Toast.makeText(this, "Set a safe zone first", Toast.LENGTH_SHORT).show()
                return
            }
            askBackground()
            val i = Intent(this, TrackingService::class.java)
            ContextCompat.startForegroundService(this, i)
            Store.setTracking(this, true)
        }
        refresh()
    }

    // ---------- guardian (SMS) ----------
    private fun editGuardian() {
        val input = android.widget.EditText(this)
        input.hint = "+91XXXXXXXXXX"
        input.setText(Store.guardian(this))
        input.inputType = android.text.InputType.TYPE_CLASS_PHONE
        AlertDialog.Builder(this)
            .setTitle("Guardian phone (optional)")
            .setMessage("Alerts are also texted to this number with a map link.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                Store.setGuardian(this, input.text.toString().trim())
                if (input.text.isNotBlank() &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                }
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- map + status ----------
    @SuppressLint("MissingPermission")
    private fun centerStart() {
        val sz = Store.safeZone(this)
        if (sz != null) {
            b.map.controller.setCenter(GeoPoint(sz.first, sz.second))
            drawOverlays()
            return
        }
        if (hasLocation()) {
            LocationServices.getFusedLocationProviderClient(this)
                .lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        b.map.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                    } else {
                        b.map.controller.setCenter(GeoPoint(12.9716, 77.5946)) // Bangalore
                    }
                    drawOverlays()
                }
        } else {
            b.map.controller.setCenter(GeoPoint(12.9716, 77.5946))
            drawOverlays()
        }
    }

    private fun drawOverlays() {
        b.map.overlays.clear()

        Store.safeZone(this)?.let { (lat, lon, radius) ->
            val circle = Polygon()
            circle.points = Polygon.pointsAsCircle(GeoPoint(lat, lon), radius.toDouble())
            circle.fillPaint.color = 0x3300C2A8.toInt()
            circle.outlinePaint.color = 0xFF00C2A8.toInt()
            circle.outlinePaint.strokeWidth = 4f
            b.map.overlays.add(circle)
        }

        for (p in Store.places(this)) {
            val m = Marker(b.map)
            m.position = GeoPoint(p.lat, p.lon)
            m.title = p.name
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            b.map.overlays.add(m)
        }
        b.map.invalidate()
    }

    private fun refresh() {
        val on = Store.isTracking(this)
        b.toggle.text = if (on) "Stop protection" else "Start protection"
        b.statusTitle.text = if (on) "Protection on" else "Protection off"

        b.statusSub.text = when {
            !Store.hasSafeZone(this) -> "Set a safe zone to begin"
            on -> "Watching ${Store.places(this).size} place(s)"
            else -> "Ready. Tap start when you leave."
        }

        val g = Store.guardian(this)
        b.btnGuardian.text = if (g.isBlank()) "Add guardian" else "Guardian: $g"

        val code = Store.pairingCode(this)
        b.btnPair.text = if (code.isBlank()) "Pair guardian app" else "Guardian app code: $code"
        drawOverlays()
    }
}
