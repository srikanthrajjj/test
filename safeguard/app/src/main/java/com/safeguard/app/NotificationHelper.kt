package com.safeguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_SERVICE = "tracking_service"
    const val CHANNEL_ALERTS = "safety_alerts"
    const val SERVICE_ID = 1001
    private var alertId = 2000

    fun createChannels(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = c.getSystemService(NotificationManager::class.java)

            val service = NotificationChannel(
                CHANNEL_SERVICE,
                "Protection running",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows while SafeGuard is watching location." }

            val alerts = NotificationChannel(
                CHANNEL_ALERTS,
                "Safety alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Zone and place alerts." }

            mgr.createNotificationChannel(service)
            mgr.createNotificationChannel(alerts)
        }
    }

    /** The persistent notification the foreground service must show. */
    fun serviceNotification(c: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(c, CHANNEL_SERVICE)
            .setContentTitle("SafeGuard is on")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
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
