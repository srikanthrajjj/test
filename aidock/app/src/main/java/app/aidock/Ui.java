package app.aidock;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny view helpers so screens can be built in code without a support library. */
final class Ui {
    static final int BG = 0xFF0E0F13, CARD = 0xFF1A1C22, TILE = 0xFF262932, TEXT = 0xFFF2F3F5,
            MUTED = 0xFF9AA0AB, ACCENT = 0xFF8B7CFF;

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable round(Context c, int color, float r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, r)); return g;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    static TextView chip(Context c, String s, boolean on) {
        TextView t = text(c, s, 14, on ? 0xFF0E0F13 : TEXT, on);
        t.setBackground(round(c, on ? ACCENT : TILE, 18));
        t.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8)); t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(c, 8), dp(c, 8)); t.setLayoutParams(lp);
        return t;
    }

    static LinearLayout col(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }

    /** Wrapping row of chips (simple flow layout using nested rows of up to n items). */
    static LinearLayout flow(Context c, TextView[] items, int perRow) {
        LinearLayout box = col(c); LinearLayout r = null;
        for (int i = 0; i < items.length; i++) {
            if (i % perRow == 0) { r = row(c); box.addView(r); }
            r.addView(items[i]);
        }
        return box;
    }
}
