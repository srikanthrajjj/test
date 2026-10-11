package com.nest.couples;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** One-off reminders set by the app (bills/subscriptions due tomorrow). */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String CH = "bills";

    static void scheduleAll(Context ctx, String json) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            String old = ctx.getSharedPreferences("nest_rem", Context.MODE_PRIVATE).getString("ids", "[]");
            JSONArray o = new JSONArray(old);
            for (int i = 0; i < o.length(); i++) am.cancel(pi(ctx, o.getInt(i), "", ""));
            JSONArray a = new JSONArray(json), ids = new JSONArray();
            for (int i = 0; i < a.length() && i < 30; i++) {
                JSONObject r = a.getJSONObject(i);
                long t = r.optLong("t");
                if (t <= System.currentTimeMillis()) continue;
                int id = 1000 + Math.abs(r.optString("id").hashCode() % 100000);
                am.setWindow(AlarmManager.RTC_WAKEUP, t, 30 * 60 * 1000L, pi(ctx, id, r.optString("title"), r.optString("body")));
                ids.put(id);
            }
            ctx.getSharedPreferences("nest_rem", Context.MODE_PRIVATE).edit().putString("ids", ids.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private static PendingIntent pi(Context ctx, int id, String title, String body) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra("title", title);
        i.putExtra("body", body);
        i.putExtra("nid", id);
        return PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context ctx, Intent in) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CH, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(ctx, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent tap = PendingIntent.getActivity(ctx, 9, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CH) : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(in.getStringExtra("title")).setContentText(in.getStringExtra("body")).setAutoCancel(true).setContentIntent(tap);
            nm.notify(in.getIntExtra("nid", 88), b.build());
        } catch (Throwable ignored) {
        }
    }
}
