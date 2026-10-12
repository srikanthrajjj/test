package com.safeguard.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A place the user cares about: home, a bar, a cinema, etc. */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Int,
    // "arrive" = alert when they reach it, "leave" = alert when they leave it
    val mode: String
)

/**
 * All saved data lives in SharedPreferences as small JSON.
 * No backend, no account. Simple and offline.
 */
object Store {
    private const val FILE = "safeguard"

    private fun prefs(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Safe zone (the main radius around home/office) ----
    fun setSafeZone(c: Context, lat: Double, lon: Double, radiusM: Int) {
        prefs(c).edit()
            .putString("sz_lat", lat.toString())
            .putString("sz_lon", lon.toString())
            .putInt("sz_radius", radiusM)
            .apply()
    }

    fun hasSafeZone(c: Context) = prefs(c).contains("sz_lat")

    fun safeZone(c: Context): Triple<Double, Double, Int>? {
        val p = prefs(c)
        if (!p.contains("sz_lat")) return null
        return Triple(
            p.getString("sz_lat", "0")!!.toDouble(),
            p.getString("sz_lon", "0")!!.toDouble(),
            p.getInt("sz_radius", 300)
        )
    }

    // ---- Guardian phone (optional SMS alert target) ----
    fun setGuardian(c: Context, phone: String) =
        prefs(c).edit().putString("guardian", phone).apply()

    fun guardian(c: Context): String =
        prefs(c).getString("guardian", "") ?: ""

    // ---- Tracking on/off ----
    fun setTracking(c: Context, on: Boolean) =
        prefs(c).edit().putBoolean("tracking", on).apply()

    fun isTracking(c: Context) = prefs(c).getBoolean("tracking", false)

    // ---- Places ----
    fun places(c: Context): List<Place> {
        val raw = prefs(c).getString("places", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Place(
                    o.getString("id"),
                    o.getString("name"),
                    o.getDouble("lat"),
                    o.getDouble("lon"),
                    o.getInt("radiusM"),
                    o.optString("mode", "arrive")
                )
            )
        }
        return out
    }

    fun savePlaces(c: Context, list: List<Place>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("lat", p.lat)
            o.put("lon", p.lon)
            o.put("radiusM", p.radiusM)
            o.put("mode", p.mode)
            arr.put(o)
        }
        prefs(c).edit().putString("places", arr.toString()).apply()
    }

    fun addPlace(c: Context, p: Place) = savePlaces(c, places(c) + p)

    fun removePlace(c: Context, id: String) =
        savePlaces(c, places(c).filter { it.id != id })

    // Remember which places we are currently "inside", so we only alert on change.
    fun insideSet(c: Context): MutableSet<String> =
        HashSet(prefs(c).getStringSet("inside", emptySet()) ?: emptySet())

    fun setInsideSet(c: Context, s: Set<String>) =
        prefs(c).edit().putStringSet("inside", s).apply()

    fun wasInsideSafeZone(c: Context) =
        prefs(c).getBoolean("in_sz", true)

    fun setInsideSafeZone(c: Context, v: Boolean) =
        prefs(c).edit().putBoolean("in_sz", v).apply()

    // ---- Dwell timers (for the ">30 min in one place" / ">30 min outside" reminder) ----
    // Epoch millis of when the current inside/outside state began. 0 = not currently timing.
    fun safeZoneOutsideSince(c: Context) = prefs(c).getLong("sz_out_since", 0L)
    fun setSafeZoneOutsideSince(c: Context, t: Long) =
        prefs(c).edit().putLong("sz_out_since", t).apply()
    fun safeZoneDwellAlerted(c: Context) = prefs(c).getBoolean("sz_dwell_alerted", false)
    fun setSafeZoneDwellAlerted(c: Context, v: Boolean) =
        prefs(c).edit().putBoolean("sz_dwell_alerted", v).apply()

    fun placeInsideSince(c: Context, id: String) =
        prefs(c).getLong("place_in_since_$id", 0L)
    fun setPlaceInsideSince(c: Context, id: String, t: Long) =
        prefs(c).edit().putLong("place_in_since_$id", t).apply()
    fun placeDwellAlerted(c: Context, id: String) =
        prefs(c).getBoolean("place_dwell_alerted_$id", false)
    fun setPlaceDwellAlerted(c: Context, id: String, v: Boolean) =
        prefs(c).edit().putBoolean("place_dwell_alerted_$id", v).apply()

    // ---- Pairing with a guardian app (via Supabase) ----
    fun pairingCode(c: Context): String = prefs(c).getString("pairing_code", "") ?: ""
    fun setPairingCode(c: Context, code: String) =
        prefs(c).edit().putString("pairing_code", code).apply()
}
