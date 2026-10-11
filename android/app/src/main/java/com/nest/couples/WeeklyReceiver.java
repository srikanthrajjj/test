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

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Sunday 9pm: "Your week in 10 seconds" — computed on-device from the saved state, then re-armed for next week. */
public class WeeklyReceiver extends BroadcastReceiver {
    private static final String CH = "weekly";

    static void schedule(Context ctx) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        c.set(Calendar.HOUR_OF_DAY, 21);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis() + 60000) c.add(Calendar.WEEK_OF_YEAR, 1);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setWindow(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), 15 * 60 * 1000L, pi(ctx));
    }

    static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(ctx));
    }

    private static PendingIntent pi(Context ctx) {
        Intent i = new Intent(ctx, WeeklyReceiver.class);
        return PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context ctx, Intent in) {
        try {
            String raw = ctx.getSharedPreferences("nest", Context.MODE_PRIVATE).getString("state", "");
            if (raw.length() == 0) return;
            JSONObject s = new JSONObject(raw);
            if (s.optBoolean("weeklyOff", false)) return;
            String cur = s.optString("cur", "INR");
            String sym = "INR".equals(cur) ? "₹" : "USD".equals(cur) ? "$" : "EUR".equals(cur) ? "€" : "GBP".equals(cur) ? "£" : "";
            JSONArray exp = s.optJSONArray("exp");
            long now = System.currentTimeMillis(), wk = 7L * 86400000L;
            double tot = 0, prev = 0;
            int n = 0;
            Map<String, Double> by = new HashMap<>();
            for (int i = 0; exp != null && i < exp.length(); i++) {
                JSONObject e = exp.getJSONObject(i);
                if ("xfer".equals(e.optString("cat"))) continue;
                long ts = e.optLong("ts");
                double a = e.optDouble("amt", 0);
                if (ts > now - wk && ts <= now) {
                    tot += a; n++;
                    String t = e.optString("title", "Other");
                    Double v = by.get(t);
                    by.put(t, (v == null ? 0 : v) + a);
                } else if (ts > now - 2 * wk && ts <= now - wk) prev += a;
            }
            String top = null;
            double topV = 0;
            for (Map.Entry<String, Double> x : by.entrySet()) if (x.getValue() > topV) { topV = x.getValue(); top = x.getKey(); }
            String title = n == 0 ? "A quiet week" : "Your week: " + sym + String.format("%,.0f", tot);
            String body = n == 0 ? "No spending recorded this week. Nice." :
                    n + " payment" + (n > 1 ? "s" : "") + (top != null ? " · most at " + top + " (" + sym + String.format("%,.0f", topV) + ")" : "") +
                    (prev > 0 ? (tot <= prev ? " · " + Math.round((prev - tot) / prev * 100) + "% less than last week" : " · " + Math.round((tot - prev) / prev * 100) + "% more than last week") : "");
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel(CH, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(ctx, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent tap = PendingIntent.getActivity(ctx, 8, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CH) : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle(title).setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(tap);
            nm.notify(77, b.build());
        } catch (Throwable ignored) {
        } finally {
            schedule(ctx);
        }
    }
}
