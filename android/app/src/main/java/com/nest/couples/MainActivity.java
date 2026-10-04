package com.nest.couples;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
    private FrameLayout root;
    private String pendingInvite = "";
    private String pendingShare = "";
    static volatile boolean foreground = false;
    private ContentObserver smsObserver;
    private final Runnable smsPing = new Runnable() {
        @Override
        public void run() {
            js("window.__smsChanged && window.__smsChanged()");
        }
    };
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(INK);
        root = new FrameLayout(this);
        root.setBackgroundColor(INK);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        // Target 35 draws edge-to-edge: keep the web content clear of system bars and the keyboard.
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                int l, t, r, b;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                    l = i.left; t = i.top; r = i.right; b = i.bottom;
                } else {
                    l = ins.getSystemWindowInsetLeft(); t = ins.getSystemWindowInsetTop();
                    r = ins.getSystemWindowInsetRight(); b = ins.getSystemWindowInsetBottom();
                }
                v.setPadding(l, t, r, b);
                return ins;
            }
        });
        handleIntent(getIntent());

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
    protected void onResume() {
        super.onResume();
        foreground = true;
        if (smsObserver == null && hasSms()) {
            try {
                smsObserver = new ContentObserver(ui) {
                    @Override
                    public void onChange(boolean selfChange) {
                        ui.removeCallbacks(smsPing);
                        ui.postDelayed(smsPing, 1500); // debounce bursts; the SMS provider fires several times per message
                    }
                };
                getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, smsObserver);
            } catch (Throwable ignored) {
                smsObserver = null;
            }
        }
    }

    @Override
    protected void onPause() {
        foreground = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (smsObserver != null) {
            try { getContentResolver().unregisterContentObserver(smsObserver); } catch (Throwable ignored) { }
            smsObserver = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /** nest://join?code=ABC123&s=https://server  -> invite;  ACTION_SEND text -> shared bank text. */
    private void handleIntent(Intent in) {
        if (in == null) return;
        try {
            Uri d = in.getData();
            if (d != null && "nest".equals(d.getScheme()) && "join".equals(d.getHost())) {
                JSONObject o = new JSONObject();
                o.put("code", d.getQueryParameter("code"));
                o.put("server", d.getQueryParameter("s"));
                pendingInvite = o.toString();
                js("window.__invite && window.__invite()");
            } else if (Intent.ACTION_SEND.equals(in.getAction()) && in.getStringExtra(Intent.EXTRA_TEXT) != null) {
                pendingShare = in.getStringExtra(Intent.EXTRA_TEXT);
                js("window.__shared && window.__shared()");
            }
        } catch (Throwable ignored) {
        }
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
                        if (Build.VERSION.SDK_INT >= 33) {
                            requestPermissions(new String[]{Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, "android.permission.POST_NOTIFICATIONS"}, REQ_SMS);
                        } else {
                            requestPermissions(new String[]{Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS}, REQ_SMS);
                        }
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

        /** Messages newer than `since` (epoch ms, passed as a string to avoid JS number precision issues). */
        @JavascriptInterface
        public void scanSmsSince(final String since) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String out;
                    long t = 0;
                    try { t = Long.parseLong(since); } catch (Throwable ignored) { }
                    try {
                        out = readInboxSince(t);
                    } catch (Throwable e) {
                        out = "{\"scanned\":0,\"msgs\":[]}";
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
        public String consumeInvite() {
            String v = pendingInvite;
            pendingInvite = "";
            return v;
        }

        @JavascriptInterface
        public String consumeShared() {
            String v = pendingShare;
            pendingShare = "";
            return v;
        }

        /** Opens WhatsApp (or WhatsApp Business) with the text ready to send; falls back to the system share sheet. */
        @JavascriptInterface
        public void shareWhatsApp(final String text) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    String[] pkgs = {"com.whatsapp", "com.whatsapp.w4b"};
                    for (int k = 0; k < pkgs.length; k++) {
                        try {
                            Intent i = new Intent(Intent.ACTION_SEND);
                            i.setType("text/plain");
                            i.putExtra(Intent.EXTRA_TEXT, text);
                            i.setPackage(pkgs[k]);
                            startActivity(i);
                            return;
                        } catch (ActivityNotFoundException ignored) {
                        }
                    }
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, "Invite your partner"));
                }
            });
        }

        /** Attaches this app's APK plus the invite text and opens WhatsApp, so the partner can install and join in one go. */
        @JavascriptInterface
        public void shareAppWhatsApp(final String text) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        File dir = new File(getCacheDir(), "share");
                        dir.mkdirs();
                        File out = new File(dir, "Nest.apk");
                        InputStream in = new FileInputStream(getApplicationInfo().sourceDir);
                        OutputStream os = new FileOutputStream(out);
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                        os.close();
                        in.close();
                        Uri uri = Uri.parse("content://com.nest.couples.apk/Nest.apk");
                        String[] pkgs = {"com.whatsapp", "com.whatsapp.w4b", null};
                        for (int k = 0; k < pkgs.length; k++) {
                            try {
                                Intent i = new Intent(Intent.ACTION_SEND);
                                i.setType("application/vnd.android.package-archive");
                                i.putExtra(Intent.EXTRA_STREAM, uri);
                                i.putExtra(Intent.EXTRA_TEXT, text);
                                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                if (pkgs[k] != null) {
                                    i.setPackage(pkgs[k]);
                                    startActivity(i);
                                } else {
                                    startActivity(Intent.createChooser(i, "Invite your partner"));
                                }
                                return;
                            } catch (ActivityNotFoundException ignored) {
                            }
                        }
                    } catch (Throwable t) {
                        // fall back to plain text if the file can't be attached
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_TEXT, text);
                        startActivity(Intent.createChooser(i, "Invite your partner"));
                    }
                }
            });
        }

        /** Current clipboard text (only readable while the app is in the foreground). */
        @JavascriptInterface
        public String readClip() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return "";
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                return t == null ? "" : t.toString();
            } catch (Throwable ignored) {
                return "";
            }
        }

        @JavascriptInterface
        public void copy(final String text) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Nest", text));
                }
            });
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
                        if (Build.VERSION.SDK_INT < 35) {
                            w.setStatusBarColor(c);
                            w.setNavigationBarColor(c);
                        }
                        if (Build.VERSION.SDK_INT >= 30) {
                            int m = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                            w.getInsetsController().setSystemBarsAppearance(darkIcons ? m : 0, m);
                        } else {
                            int flags = 0;
                            if (darkIcons) {
                                flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                                if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                            }
                            w.getDecorView().setSystemUiVisibility(flags);
                        }
                        root.setBackgroundColor(c);
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
        return readInboxSince(System.currentTimeMillis() - days * 86400000L);
    }

    private String readInboxSince(long since) throws Exception {
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
