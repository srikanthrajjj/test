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
 * Wakes when an SMS arrives. If the app is closed and the text looks like a payment, shows a quiet
 * notification; opening Nest then imports it automatically. The text itself is never stored or sent.
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String CH = "payments";

    @Override
    public void onReceive(Context ctx, Intent in) {
        try {
            if (MainActivity.foreground) return; // the open app picks it up itself
            if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(in.getAction())) return;
            Bundle b = in.getExtras();
            if (b == null) return;
            Object[] pdus = (Object[]) b.get("pdus");
            if (pdus == null) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pdus.length; i++) {
                SmsMessage m = SmsMessage.createFromPdu((byte[]) pdus[i], b.getString("format"));
                if (m != null && m.getMessageBody() != null) sb.append(m.getMessageBody());
            }
            String t = sb.toString().toLowerCase();
            boolean pay = (t.contains("debit") || t.contains("spent") || t.contains("paid") || t.contains("sent") || t.contains("purchase") || t.contains("withdrawn"))
                    && (t.contains("rs") || t.contains("inr") || t.contains("\u20b9") || t.contains("$"))
                    && !t.contains("otp") && !t.contains("credited");
            if (!pay) return;
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel(CH, "New payments", NotificationManager.IMPORTANCE_LOW));
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(ctx, CH)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("New payment")
                    .setContentText("Tap to add it to Nest")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(7, n);
        } catch (Throwable ignored) {
        }
    }
}
