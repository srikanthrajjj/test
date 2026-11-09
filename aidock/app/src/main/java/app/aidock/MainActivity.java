package app.aidock;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Settings: add widgets, choose where asks go, pick and order the dock. */
public class MainActivity extends Activity {
    private LinearLayout page;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        page = Ui.col(this);
        int p = Ui.dp(this, 20);
        page.setPadding(p, Ui.dp(this, 28), p, Ui.dp(this, 40));
        sv.addView(page);
        setContentView(sv);
    }

    @Override protected void onResume() { super.onResume(); render(); Ai.refreshWidgets(this); }

    private void render() {
        page.removeAllViews();
        page.addView(Ui.text(this, "AI Dock", 30, Ui.TEXT, true));
        TextView sub = Ui.text(this, "Every AI chat, voice and quick ask, one tap from your home screen.", 15, Ui.MUTED, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        page.addView(sub);

        // 1. widgets
        section("Add a widget");
        LinearLayout ws = Ui.col(this);
        ws.addView(widgetButton("All chats", "Grid of every AI app. Resize it freely.", DockWidget.class));
        ws.addView(widgetButton("Quick actions", "Type, speak, Lens, and clipboard asks.", ActionsWidget.class));
        ws.addView(widgetButton("Voice", "Speak a question, or open your assistant.", VoiceWidget.class));
        page.addView(ws);

        // 2. default AI
        section("Questions go to");
        Ai def = Ai.defaultAi(this);
        TextView[] chips = new TextView[Ai.ALL.length];
        for (int i = 0; i < Ai.ALL.length; i++) {
            final Ai a = Ai.ALL[i];
            chips[i] = Ui.chip(this, a.name, a == def);
            chips[i].setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                Ai.setDefault(MainActivity.this, a.id); render(); Ai.refreshWidgets(MainActivity.this);
            } });
        }
        page.addView(Ui.flow(this, chips, 3));
        page.addView(note(def.installed(this) ? def.name + " app found. Your text opens in it, ready to send."
                : def.name + " app not installed. Asks open in the browser."));

        // 3. dock
        section("In your dock");
        final List<Ai> dock = Ai.dock(this);
        List<Ai> order = new ArrayList<>(dock);
        for (Ai a : Ai.ALL) if (!order.contains(a)) order.add(a);
        for (final Ai a : order) page.addView(dockRow(a, dock));

        // 4. try it
        section("Try it");
        TextView[] tries = {
            tryChip("Type", "type"), tryChip("Speak", "speak"), tryChip("Assistant", "assist"),
            tryChip("Summarise clipboard", "t0"), tryChip("Lens", "lens"),
        };
        page.addView(Ui.flow(this, tries, 3));
        page.addView(note("Also: select text in any app → menu → Ask AI. Or share text → Ask AI. "
                + "Pull down Quick Settings → edit → add \"Speak to AI\"."));
    }

    private void section(String s) {
        TextView t = Ui.text(this, s, 13, Ui.ACCENT, true);
        t.setAllCaps(true); t.setLetterSpacing(0.08f);
        t.setPadding(0, Ui.dp(this, 26), 0, Ui.dp(this, 10));
        page.addView(t);
    }

    private TextView note(String s) {
        TextView t = Ui.text(this, s, 13, Ui.MUTED, false);
        t.setPadding(0, Ui.dp(this, 4), 0, 0);
        return t;
    }

    private View widgetButton(String name, String desc, final Class<?> provider) {
        LinearLayout r = Ui.row(this);
        r.setBackground(Ui.round(this, Ui.CARD, 18));
        int p = Ui.dp(this, 14); r.setPadding(p, p, p, p);
        LinearLayout txt = Ui.col(this);
        txt.addView(Ui.text(this, name, 16, Ui.TEXT, true));
        txt.addView(Ui.text(this, desc, 13, Ui.MUTED, false));
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = Ui.chip(this, "Add", true);
        ((LinearLayout.LayoutParams) add.getLayoutParams()).setMargins(0, 0, 0, 0);
        r.addView(add);
        View.OnClickListener l = new View.OnClickListener() { @Override public void onClick(View v) { pin(provider); } };
        r.setOnClickListener(l); add.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, Ui.dp(this, 8)); r.setLayoutParams(lp);
        return r;
    }

    private void pin(Class<?> provider) {
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        if (m.isRequestPinAppWidgetSupported() && m.requestPinAppWidget(new ComponentName(this, provider), null, null)) return;
        Toast.makeText(this, "Long-press your home screen → Widgets → AI Dock", Toast.LENGTH_LONG).show();
    }

    private View dockRow(final Ai a, final List<Ai> dock) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        ImageView ic = new ImageView(this);
        int s = Ui.dp(this, 36);
        ic.setImageDrawable(new BitmapDrawable(getResources(), a.icon(this, s)));
        r.addView(ic, new LinearLayout.LayoutParams(s, s));

        LinearLayout txt = Ui.col(this);
        txt.setPadding(Ui.dp(this, 12), 0, 0, 0);
        txt.addView(Ui.text(this, a.name, 16, Ui.TEXT, false));
        txt.addView(Ui.text(this, a.installed(this) ? "Installed" : "Opens in browser", 12, Ui.MUTED, false));
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));

        final boolean in = dock.contains(a);
        if (in && dock.indexOf(a) > 0) {
            TextView up = Ui.text(this, "↑", 20, Ui.MUTED, false);
            up.setGravity(Gravity.CENTER);
            up.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            up.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                int i = dock.indexOf(a); dock.remove(i); dock.add(i - 1, a); save(dock);
            } });
            r.addView(up);
        }
        CheckBox cb = new CheckBox(this);
        cb.setChecked(in);
        cb.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            if (in) dock.remove(a); else dock.add(a);
            save(dock);
        } });
        r.addView(cb);
        return r;
    }

    private void save(List<Ai> dock) { Ai.saveDock(this, dock); render(); Ai.refreshWidgets(this); }

    private TextView tryChip(String label, final String act) {
        TextView t = Ui.chip(this, label, false);
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            startActivity(new Intent(MainActivity.this, ActionActivity.class).setAction("act:" + act).putExtra("act", act));
        } });
        return t;
    }
}
