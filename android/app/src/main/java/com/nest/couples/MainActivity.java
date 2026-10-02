package com.nest.couples;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Nest hosts the UI (assets/www) in a WebView and exposes a tiny native bridge:
 * on-device SMS reading, durable storage, haptics, system bar colours, sharing.
 * Nothing ever leaves the device.
 */
public class MainActivity extends Activity {
    private static final int REQ_SMS = 71;
    private static final String PREFS = "nest";
    private static final int INK = 0xFF0B0B14;

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(INK);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true; // the app is fully local; never navigate away
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        js("window.__setNight && window.__setNight(" + isNight() + ")");
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back ? window.__back() : false", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (!"true".equals(v)) moveTaskToBack(true);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_SMS) {
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            js("window.__onPerm && window.__onPerm(" + ok + ")");
        }
    }

    private boolean isNight() {
        int m = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    private void js(final String code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (web != null) web.evaluateJavascript(code, null);
            }
        });
    }

    private boolean hasSms() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Methods callable from JavaScript as window.Native.* */
    public class Bridge {
        @JavascriptInterface
        public boolean hasSmsPermission() {
            return hasSms();
        }

        @JavascriptInterface
        public void requestSms() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (hasSms()) {
                        js("window.__onPerm && window.__onPerm(true)");
                    } else {
                        requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQ_SMS);
                    }
                }
            });
        }

        /** Reads likely-transaction messages from the last N days; replies via window.__onSms(json). */
        @JavascriptInterface
        public void scanSms(final int days) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String out;
                    try {
                        out = readInbox(days);
                    } catch (Throwable t) {
                        out = "{\"scanned\":0,\"msgs\":[],\"error\":\"" + String.valueOf(t.getMessage()).replace("\"", "'") + "\"}";
                    }
                    js("window.__onSms && window.__onSms(" + JSONObject.quote(out) + ")");
                }
            }).start();
        }

        @JavascriptInterface
        public String loadState() {
            return getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("state", "");
        }

        @JavascriptInterface
        public void saveState(String json) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("state", json).apply();
        }

        @JavascriptInterface
        public boolean isNight() {
            return MainActivity.this.isNight();
        }

        @JavascriptInterface
        public void haptic(final int kind) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    int f = HapticFeedbackConstants.VIRTUAL_KEY;
                    if (kind == 1) f = HapticFeedbackConstants.CLOCK_TICK;
                    if (kind == 2 && Build.VERSION.SDK_INT >= 30) f = HapticFeedbackConstants.CONFIRM;
                    if (kind == 3 && Build.VERSION.SDK_INT >= 30) f = HapticFeedbackConstants.REJECT;
                    web.performHapticFeedback(f);
                }
            });
        }

        /** statusHex like "#F2F2F7"; dark=true means dark icons (light background). */
        @JavascriptInterface
        public void setBars(final String hex, final boolean darkIcons) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        int c = Color.parseColor(hex);
                        Window w = getWindow();
                        w.setStatusBarColor(c);
                        w.setNavigationBarColor(c);
                        int flags = 0;
                        if (darkIcons) {
                            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                        w.getDecorView().setSystemUiVisibility(flags);
                        web.setBackgroundColor(c);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void share(final String title, final String text) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, title);
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, title));
                }
            });
        }
    }

    private static final String[] HINTS = {
            "debit", "spent", "paid", "sent", "purchase", "withdraw", "txn", "upi", "charged", "deducted", "payment"
    };

    private String readInbox(int days) throws Exception {
        long since = System.currentTimeMillis() - days * 86400000L;
        JSONArray msgs = new JSONArray();
        int scanned = 0;
        Cursor c = getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                new String[]{"_id", "address", "body", "date"},
                "date > ?", new String[]{String.valueOf(since)}, "date DESC");
        if (c != null) {
            try {
                while (c.moveToNext() && scanned < 5000) {
                    scanned++;
                    String body = c.getString(2);
                    if (body == null || body.length() > 700) continue;
                    String low = body.toLowerCase();
                    boolean hit = false;
                    for (int i = 0; i < HINTS.length; i++) {
                        if (low.contains(HINTS[i])) { hit = true; break; }
                    }
                    if (!hit) continue;
                    JSONObject o = new JSONObject();
                    o.put("id", c.getLong(0));
                    o.put("a", c.getString(1));
                    o.put("b", body);
                    o.put("d", c.getLong(3));
                    msgs.put(o);
                }
            } finally {
                c.close();
            }
        }
        JSONObject res = new JSONObject();
        res.put("scanned", scanned);
        res.put("msgs", msgs);
        return res.toString();
    }
}
