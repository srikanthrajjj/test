package com.safeguard.family

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts sharing after the phone reboots (needs "Allow all the time" location). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Restart sharing after a reboot AND right after an app update, so the
        // family connection never drops. The pairing itself (code, role) is kept
        // in storage across updates, so nothing needs re-entering.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val p = Prefs(context)
        if (p.role == "sharer" && p.sharing) {
            try { LocationService.start(context) } catch (_: Exception) {}
        }
    }
}
