package com.safeguard.guardian

import android.content.Context

/** Tiny local store: just the pairing code and the id of the last alert seen. */
object Store {
    private const val FILE = "guardian"
    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun pairingCode(c: Context): String = prefs(c).getString("code", "") ?: ""
    fun setPairingCode(c: Context, code: String) =
        prefs(c).edit().putString("code", code).apply()

    fun lastAlertId(c: Context): Long = prefs(c).getLong("last_alert_id", 0L)
    fun setLastAlertId(c: Context, id: Long) =
        prefs(c).edit().putLong("last_alert_id", id).apply()
}
