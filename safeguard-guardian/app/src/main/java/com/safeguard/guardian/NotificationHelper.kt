package com.safeguard.guardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/** Alerts coming in from the paired phone (left zone, arrived/left a place, dwell reminders). */
object NotificationHelper {
    const val CHANNEL_ALERTS = "guardian_alerts"
    private var alertId = 3000

    fun createChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = c.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ALERTS,
                "SafeGuard alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts from the paired phone." }
            mgr.createNotificationChannel(channel)
        }
    }

    fun alert(c: Context, title: String, text: String) {
        val mgr = c.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(c, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        mgr.notify(alertId++, n)
    }
}
