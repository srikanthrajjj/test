package app.aidock;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Catalog of AI services + everything needed to open them or hand them a prompt. */
final class Ai {
    final String id, name, pkg, web, query; final int color;

    private Ai(String id, String name, String pkg, String web, String query, int color) {
        this.id = id; this.name = name; this.pkg = pkg; this.web = web; this.query = query; this.color = color;
    }

    /** query = web URL that accepts a prefilled prompt (null when the site has no such URL). */
    static final Ai[] ALL = {
        new Ai("chatgpt", "ChatGPT", "com.openai.chatgpt", "https://chatgpt.com/", "https://chatgpt.com/?q=", 0xFF10A37F),
        new Ai("claude", "Claude", "com.anthropic.claude", "https://claude.ai/new", "https://claude.ai/new?q=", 0xFFD97757),
        new Ai("gemini", "Gemini", "com.google.android.apps.bard", "https://gemini.google.com/app", null, 0xFF4285F4),
        new Ai("perplexity", "Perplexity", "ai.perplexity.app.android", "https://www.perplexity.ai/", "https://www.perplexity.ai/search?q=", 0xFF20808D),
        new Ai("copilot", "Copilot", "com.microsoft.copilot", "https://copilot.microsoft.com/", "https://copilot.microsoft.com/?q=", 0xFF0078D4),
        new Ai("meta", "Meta AI", "com.facebook.stella", "https://www.meta.ai/", null, 0xFF0866FF),
        new Ai("deepseek", "DeepSeek", "com.deepseek.chat", "https://chat.deepseek.com/", null, 0xFF4D6BFE),
        new Ai("grok", "Grok", "ai.x.grok", "https://grok.com/", "https://grok.com/?q=", 0xFF222222),
        new Ai("mistral", "Le Chat", "ai.mistral.chat", "https://chat.mistral.ai/chat", "https://chat.mistral.ai/chat?q=", 0xFFFA520F),
        new Ai("notebooklm", "NotebookLM", "com.google.android.apps.labs.language.tailwind", "https://notebooklm.google.com/", null, 0xFF5F6368),
    };

    static final String[][] TEMPLATES = {
        {"Summarise", "Summarise this in 5 short bullet points:\n\n"},
        {"Explain", "Explain this simply, like I'm new to the topic:\n\n"},
        {"Translate", "Translate this to English (if it's already English, translate to Hindi):\n\n"},
        {"Fix text", "Fix the grammar and make this clear and polite. Reply with only the fixed text:\n\n"},
        {"Reply", "Write a short, friendly reply to this message:\n\n"},
    };

    static Ai byId(String id) {
        for (Ai a : ALL) if (a.id.equals(id)) return a;
        return ALL[0];
    }

    // ---- prefs ----
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("aidock", Context.MODE_PRIVATE); }

    static Ai defaultAi(Context c) { return byId(prefs(c).getString("default", "chatgpt")); }

    static void setDefault(Context c, String id) { prefs(c).edit().putString("default", id).apply(); }

    /** Ordered dock list. First run: installed apps first, then ChatGPT/Claude/Gemini/Perplexity as web. */
    static List<Ai> dock(Context c) {
        String saved = prefs(c).getString("dock", null);
        List<Ai> out = new ArrayList<>();
        if (saved == null) {
            for (Ai a : ALL) if (a.installed(c)) out.add(a);
            for (String id : new String[]{"chatgpt", "claude", "gemini", "perplexity"})
                if (!out.contains(byId(id))) out.add(byId(id));
            return out;
        }
        if (saved.isEmpty()) return out;
        for (String id : saved.split(",")) for (Ai a : ALL) if (a.id.equals(id)) out.add(a);
        return out;
    }

    static void saveDock(Context c, List<Ai> list) {
        StringBuilder sb = new StringBuilder();
        for (Ai a : list) { if (sb.length() > 0) sb.append(','); sb.append(a.id); }
        prefs(c).edit().putString("dock", sb.toString()).apply();
    }

    // ---- actions ----
    boolean installed(Context c) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    void open(Activity a) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse(web));
        start(a, i);
    }

    /** Hand a prompt to this AI: app share sheet -> prefilled web URL -> copy + open. */
    void ask(Activity a, String text) {
        if (installed(a)) {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(pkg)
                    .putExtra(Intent.EXTRA_TEXT, text);
            if (!a.getPackageManager().queryIntentActivities(send, 0).isEmpty()) { start(a, send); return; }
        }
        if (query != null && text.length() < 6000) {
            try {
                start(a, new Intent(Intent.ACTION_VIEW, Uri.parse(query + URLEncoder.encode(text, "UTF-8"))));
                return;
            } catch (Exception ignored) { }
        }
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("prompt", text));
        Toast.makeText(a, "Prompt copied. Paste it into " + name, Toast.LENGTH_LONG).show();
        open(a);
    }

    static void start(Activity a, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { a.startActivity(i); }
        catch (Exception e) { Toast.makeText(a, "Couldn't open that", Toast.LENGTH_SHORT).show(); }
    }

    // ---- icons ----
    Bitmap icon(Context c, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            d.setBounds(0, 0, px, px); d.draw(cv); return b;
        } catch (Exception notInstalled) { /* monogram below */ }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        cv.drawCircle(px / 2f, px / 2f, px / 2f, p);
        p.setColor(0xFFFFFFFF); p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); p.setTextSize(px * 0.42f);
        cv.drawText(name.substring(0, 1), px / 2f, px / 2f - (p.descent() + p.ascent()) / 2f, p);
        return b;
    }

    static void refreshWidgets(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] dock = m.getAppWidgetIds(new ComponentName(c, DockWidget.class));
        DockWidget.update(c, m, dock);
        m.notifyAppWidgetViewDataChanged(dock, R.id.grid);
        ActionsWidget.update(c, m, m.getAppWidgetIds(new ComponentName(c, ActionsWidget.class)));
        VoiceWidget.update(c, m, m.getAppWidgetIds(new ComponentName(c, VoiceWidget.class)));
    }
}
