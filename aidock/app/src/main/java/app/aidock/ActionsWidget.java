package app.aidock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** 4x2 grid of one-tap asks to the default AI. */
public class ActionsWidget extends AppWidgetProvider {
    static final int[] IDS = {R.id.a_type, R.id.a_speak, R.id.a_clip, R.id.a_lens,
            R.id.a_sum, R.id.a_explain, R.id.a_trans, R.id.a_fix};
    static final String[] ACTS = {"type", "speak", "clip", "lens", "t0", "t1", "t2", "t3"};

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { update(c, m, ids); }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_actions);
        v.setTextViewText(R.id.title, "Ask " + Ai.defaultAi(c).name + "  ·  clipboard tiles use what you copied");
        for (int i = 0; i < IDS.length; i++) v.setOnClickPendingIntent(IDS[i], act(c, ACTS[i], i + 10));
        for (int id : ids) m.updateAppWidget(id, v);
    }

    static PendingIntent act(Context c, String act, int req) {
        Intent i = new Intent(c, ActionActivity.class).setAction("act:" + act).putExtra("act", act);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
