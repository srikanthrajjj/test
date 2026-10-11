package com.nest.couples;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads payment notifications from UPI and bank apps (GPay, PhonePe, Paytm, BHIM, CRED, bank apps)
 * and bank alert emails (Gmail, Outlook…) so payments are caught even when the bank sends no SMS
 * (e.g. UPI payments under ₹100, UPI Lite). Only notifications from payment apps that
 * mention an amount are kept, in a small on-device queue that the app drains on open.
 */
public class PayNotifService extends NotificationListenerService {
    static final String PREFS = "nest_notif";
    private static final String[] PAY_APPS = {
            "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
            "com.dreamplug.androidapp", "in.amazon.mShop.android.shopping", "com.mobikwik_new", "com.freecharge.android",
            "money.super.payments", "com.naviapp", "com.whatsapp.payments", "com.flipkart.android", "com.slice",
            "com.csam.icici.bank.imobile", "com.snapwork.hdfc", "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus",
            "com.axis.mobile", "com.msf.kbank.mobile", "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge",
            "com.idfcfirstbank.optimus", "com.bankofbaroda.mconnect", "com.infrasofttech.indianbank", "com.yesbank",
            "com.fedmobile", "com.jupiter.money", "com.fi.money", "com.onecard"
    };

    private static final java.util.regex.Pattern BANK_ALERT = java.util.regex.Pattern.compile("(a/c|acct|account|\\bcard\\b|\\bupi\\b|vpa)");
    private static final String[] MAIL_APPS = { "com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail", "com.samsung.android.email.provider", "ch.protonmail.android", "com.zoho.mail" };

    static boolean isMailApp(String pkg) {
        if (pkg == null) return false;
        for (String p : MAIL_APPS) if (p.equals(pkg)) return true;
        return false;
    }

    static boolean isPayApp(String pkg) {
        if (pkg == null) return false;
        for (String p : PAY_APPS) if (p.equals(pkg)) return true;
        String l = pkg.toLowerCase();
        if (l.contains("whatsapp") || l.contains("telegram") || l.contains("messag") || l.contains("mms") || l.contains("gmail") || l.contains("mail")) return false;
        return l.contains("bank") || l.contains(".upi") || l.contains("upi.") || l.contains("pay");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            boolean mail = isMailApp(pkg);
            if (!mail && !isPayApp(pkg)) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle x = n.extras;
            if (x == null) return;
            CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = x.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (text == null) text = x.getCharSequence(Notification.EXTRA_TEXT);
            String body = ((title != null ? title + ". " : "") + (text != null ? text : "")).trim();
            String low = body.toLowerCase();
            if (body.length() < 8 || body.length() > 600) return;
            if (!(low.contains("₹") || low.contains("rs") || low.contains("inr"))) return;
            // from mail apps keep only bank debit alerts (banks email small UPI payments they no longer text)
            if (mail && !(BANK_ALERT.matcher(low).find() && (low.contains("debited") || low.contains("spent") || low.contains("paid") || low.contains("upi txn") || low.contains("transaction")))) return;
            store(this, pkg, body, sbn.getPostTime());
            MainActivity a = MainActivity.live;
            if (a != null && MainActivity.foreground) a.notifPing();
        } catch (Throwable ignored) {
        }
    }

    static synchronized void store(Context ctx, String pkg, String body, long when) throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray q = new JSONArray(sp.getString("q", "[]"));
        for (int i = 0; i < q.length(); i++) {
            JSONObject o = q.getJSONObject(i);
            if (body.equals(o.optString("b")) && Math.abs(o.optLong("d") - when) < 120000) return; // same notification re-posted
        }
        JSONObject o = new JSONObject();
        o.put("id", "n" + when + "_" + Math.abs(body.hashCode()));
        o.put("a", "APP:" + pkg);
        o.put("b", body);
        o.put("d", when);
        q.put(o);
        while (q.length() > 400) q.remove(0);
        sp.edit().putString("q", q.toString()).apply();
    }

    static synchronized String take(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String v = sp.getString("q", "[]");
        sp.edit().putString("q", "[]").apply();
        return v;
    }
}
