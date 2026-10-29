package com.safeguard.family

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

object Ui {
    /** Keeps content clear of the status bar and gesture bar (Android 15 edge-to-edge). */
    fun applyInsets(v: View, top: Boolean = true) {
        val l = v.paddingLeft; val t = v.paddingTop; val r = v.paddingRight; val b = v.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(l + bars.left, t + if (top) bars.top else 0, r + bars.right, b + bars.bottom)
            insets
        }
    }

    /** OpenStreetMap setup. Free, no API key. */
    fun setupMap(ctx: Context, map: MapView) {
        Configuration.getInstance().userAgentValue = ctx.packageName
        Configuration.getInstance().osmdroidBasePath = ctx.cacheDir
        Configuration.getInstance().osmdroidTileCache = java.io.File(ctx.cacheDir, "tiles")
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        map.controller.setZoom(5.0)
        map.controller.setCenter(GeoPoint(20.59, 78.96))   // India
    }

    fun circle(map: MapView, lat: Double, lon: Double, radius: Double, color: Int): Polygon {
        val p = Polygon(map)
        p.points = Polygon.pointsAsCircle(GeoPoint(lat, lon), radius)
        p.fillPaint.color = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))
        p.outlinePaint.color = color
        p.outlinePaint.strokeWidth = 3f
        p.setOnClickListener { _, _, _ -> true }
        return p
    }

    fun ago(ms: Long): String {
        val m = (System.currentTimeMillis() - ms) / 60_000
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 1440 -> "${m / 60} h ago"
            else -> "${m / 1440} d ago"
        }
    }
}
