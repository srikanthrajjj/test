package com.safeguard.family

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts sharing after the phone reboots (needs "Allow all the time" location). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = Prefs(context)
        if (p.role == "sharer" && p.sharing) {
            try { LocationService.start(context) } catch (_: Exception) {}
        }
    }
}
