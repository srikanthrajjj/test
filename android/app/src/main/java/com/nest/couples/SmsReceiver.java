package com.nest.couples;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Wakes when an SMS arrives. A text that looks like a payment is queued on the device straight away
 * (the open app imports it within a second; a closed app imports it the moment it opens) and, if the
 * app is closed, a quiet notification shows the amount. Nothing leaves the phone.
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String CH = "payments";

    @Override
    public void onReceive(Context ctx, Intent in) {
        try {
            if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(in.getAction())) return;
            Bundle b = in.getExtras();
            if (b == null) return;
            Object[] pdus = (Object[]) b.get("pdus");
            if (pdus == null) return;
            StringBuilder sb = new StringBuilder();
            String from = "";
            long when = System.currentTimeMillis();
            for (int i = 0; i < pdus.length; i++) {
                SmsMessage m = SmsMessage.createFromPdu((byte[]) pdus[i], b.getString("format"));
                if (m == null) continue;
                if (m.getMessageBody() != null) sb.append(m.getMessageBody());
                if (from.isEmpty() && m.getOriginatingAddress() != null) from = m.getOriginatingAddress();
            }
            String body = sb.toString(), t = body.toLowerCase();
            boolean pay = (t.contains("debit") || t.contains("spent") || t.contains("paid") || t.contains("sent") || t.contains("purchase") || t.contains("withdrawn"))
                    && (t.contains("rs") || t.contains("inr") || t.contains("\u20b9") || t.contains("$"))
                    && !t.contains("otp") && !t.contains("credited");
            if (!pay) return;
            // queue it on the device right away: Nest imports the queue the moment it opens (or instantly if open)
            PayNotifService.store(ctx, "SMS:" + from, body, when);
            MainActivity a = MainActivity.live;
            if (a != null && MainActivity.foreground) { a.notifPing(); return; }
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel(CH, "New payments", NotificationManager.IMPORTANCE_LOW));
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(ctx, CH)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(amountOf(body) != null ? amountOf(body) + " spent" : "New payment")
                    .setContentText("Saved to Nest · tap to see it")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(7, n);
        } catch (Throwable ignored) {
        }
    }

    private static final java.util.regex.Pattern AMT = java.util.regex.Pattern.compile("(?i)(?:rs\\.?|inr|\u20b9)\\s*:?\\s*([\\d,]+(?:\\.\\d{1,2})?)");

    static String amountOf(String body) {
        java.util.regex.Matcher m = AMT.matcher(body);
        if (!m.find()) return null;
        String v = m.group(1).replace(",", "");
        if (v.endsWith(".00")) v = v.substring(0, v.length() - 3);
        return "\u20b9" + v;
    }
}
