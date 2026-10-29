package com.safeguard.family

import android.content.Context
import java.security.SecureRandom

/** Simple saved settings for this phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("safeguard", Context.MODE_PRIVATE)

    var role: String?                       // "sharer" or "viewer"
        get() = sp.getString("role", null)
        set(v) = sp.edit().putString("role", v).apply()

    var code: String?                       // family code, 12 letters, no dashes
        get() = sp.getString("code", null)
        set(v) = sp.edit().putString("code", v).apply()

    var name: String                        // sharer's own name, or the person the viewer follows
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()

    var sharing: Boolean
        get() = sp.getBoolean("sharing", false)
        set(v) = sp.edit().putBoolean("sharing", v).apply()

    var batteryWarned: Boolean
        get() = sp.getBoolean("bat_warned", false)
        set(v) = sp.edit().putBoolean("bat_warned", v).apply()

    // Saved places. NaN = not set.
    fun placeLat(p: String) = sp.getFloat("${p}_lat", Float.NaN).toDouble()
    fun placeLon(p: String) = sp.getFloat("${p}_lon", Float.NaN).toDouble()
    fun hasPlace(p: String) = !placeLat(p).isNaN()
    fun setPlace(p: String, lat: Double, lon: Double) {
        sp.edit().putFloat("${p}_lat", lat.toFloat()).putFloat("${p}_lon", lon.toFloat())
            .remove("${p}_state").apply()
    }

    // Geofence state per place: 0 unknown, 1 inside, 2 outside
    fun placeState(p: String) = sp.getInt("${p}_state", 0)
    fun setPlaceState(p: String, s: Int) = sp.edit().putInt("${p}_state", s).apply()

    // Last location sent, so we only send when she actually moves.
    var lastSentLat: Double
        get() = sp.getFloat("ls_lat", Float.NaN).toDouble()
        set(v) = sp.edit().putFloat("ls_lat", v.toFloat()).apply()
    var lastSentLon: Double
        get() = sp.getFloat("ls_lon", Float.NaN).toDouble()
        set(v) = sp.edit().putFloat("ls_lon", v.toFloat()).apply()
    var lastSentTime: Long
        get() = sp.getLong("ls_time", 0L)
        set(v) = sp.edit().putLong("ls_time", v).apply()

    fun clear() = sp.edit().clear().apply()

    companion object {
        const val OFFICE = "office"
        const val HOME = "home"
        const val RADIUS_M = 200.0

        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        fun newCode(): String {
            val r = SecureRandom()
            return (1..12).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
        }

        fun pretty(code: String) = code.chunked(4).joinToString("-")

        fun normalize(input: String) = input.uppercase().filter { it.isLetterOrDigit() }
    }
}
