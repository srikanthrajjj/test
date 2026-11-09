package app.aidock;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Invisible trampoline behind every widget tap, the Quick Settings tile, the share sheet
 * ("Ask AI") and the text-selection menu. Shows a small composer only when typing is needed.
 */
public class ActionActivity extends Activity {
    private static final int REQ_SPEECH = 7;
    private String pendingClip;      // clipboard action waiting for window focus (Android 10+ rule)
    private Ai target;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        target = Ai.defaultAi(this);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handle(i); }

    private void handle(Intent in) {
        String action = in.getAction() == null ? "" : in.getAction();
        if (Intent.ACTION_SEND.equals(action)) { composer(str(in.getStringExtra(Intent.EXTRA_TEXT))); return; }
        if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            composer(str(in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))); return;
        }
        String act = str(in.getStringExtra("act"));
        switch (act) {
            case "open": Ai.byId(in.getStringExtra("ai")).open(this); finish(); break;
            case "type": composer(""); break;
            case "speak": speak(); break;
            case "assist": assist(); break;
            case "lens": lens(); break;
            case "clip": case "t0": case "t1": case "t2": case "t3":
                pendingClip = act; setContentView(new View(this)); break;  // read once we have focus
            default: finish();
        }
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus || pendingClip == null) return;
        String act = pendingClip; pendingClip = null;
        String clip = readClipboard();
        if (clip.trim().isEmpty()) { toast("Copy some text first, then tap again"); finish(); return; }
        if (act.equals("clip")) { composer(clip); return; }
        target.ask(this, Ai.TEMPLATES[act.charAt(1) - '0'][1] + clip);
        finish();
    }

    private String readClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData d = cm.getPrimaryClip();
        if (d == null || d.getItemCount() == 0) return "";
        CharSequence t = d.getItemAt(0).coerceToText(this);
        return t == null ? "" : t.toString();
    }

    // ---- voice ----
    private void speak() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask " + target.name);
        try { startActivityForResult(i, REQ_SPEECH); }
        catch (Exception e) { toast("No speech recogniser on this phone"); composer(""); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SPEECH) return;
        ArrayList<String> r = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (res == RESULT_OK && r != null && !r.isEmpty() && !r.get(0).trim().isEmpty()) target.ask(this, r.get(0));
        finish();
    }

    private void assist() {
        for (String a : new String[]{Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST}) {
            Intent i = new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(getPackageManager()) != null) {
                try { startActivity(i); finish(); return; } catch (Exception ignored) { }
            }
        }
        speak();
    }

    private void lens() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens");
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com/"));
        Ai.start(this, i);
        finish();
    }

    // ---- composer ----
    private void composer(String prefill) {
        final LinearLayout card = Ui.col(this);
        int p = Ui.dp(this, 18);
        card.setPadding(p, p, p, Ui.dp(this, 10));
        card.setBackground(Ui.round(this, Ui.CARD, 26));

        final TextView title = Ui.text(this, "", 13, Ui.MUTED, false);
        card.addView(title);

        // AI picker
        final LinearLayout picks = Ui.row(this);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false); hs.addView(picks);
        hs.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        card.addView(hs);
        final Runnable[] drawPicks = new Runnable[1];
        drawPicks[0] = new Runnable() { @Override public void run() {
            picks.removeAllViews();
            title.setText("Ask " + target.name);
            for (final Ai a : Ai.ALL) {
                TextView c = Ui.chip(ActionActivity.this, a.name, a == target);
                c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                    target = a; drawPicks[0].run(); } });
                picks.addView(c);
            }
        } };
        drawPicks[0].run();

        final EditText box = new EditText(this);
        box.setText(prefill); box.setSelection(box.getText().length());
        box.setHint("Type your question…"); box.setHintTextColor(Ui.MUTED); box.setTextColor(Ui.TEXT);
        box.setTextSize(16); box.setMinLines(3); box.setMaxLines(8); box.setGravity(Gravity.TOP);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.setBackground(Ui.round(this, Ui.TILE, 16));
        box.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.addView(box, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = Ui.text(this, "Or one tap:", 12, Ui.MUTED, false);
        hint.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
        card.addView(hint);
        TextView[] tpl = new TextView[Ai.TEMPLATES.length + 1];
        for (int i = 0; i < Ai.TEMPLATES.length; i++) {
            final String prefix = Ai.TEMPLATES[i][1];
            tpl[i] = Ui.chip(this, Ai.TEMPLATES[i][0], false);
            tpl[i].setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { send(prefix, box); } });
        }
        tpl[tpl.length - 1] = Ui.chip(this, "Send ➜", true);
        tpl[tpl.length - 1].setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { send("", box); } });
        card.addView(Ui.flow(this, tpl, 3));

        LinearLayout root = Ui.col(this);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0x99000000);
        int m = Ui.dp(this, 16);
        root.setPadding(m, m, m, m);
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));
        root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        card.setClickable(true);
        setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        box.requestFocus();
    }

    private void send(String prefix, EditText box) {
        String t = box.getText().toString().trim();
        if (t.isEmpty()) { toast("Type something first"); return; }
        target.ask(this, prefix + t);
        finish();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static String str(CharSequence s) { return s == null ? "" : s.toString(); }
}
