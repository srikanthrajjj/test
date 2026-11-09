package app.aidock;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/** 2x1: speak a question to the default AI, or open the phone's own assistant. */
public class VoiceWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { update(c, m, ids); }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_voice);
        v.setTextViewText(R.id.v_label, "Speak to " + Ai.defaultAi(c).name);
        v.setOnClickPendingIntent(R.id.v_speak, ActionsWidget.act(c, "speak", 1));
        v.setOnClickPendingIntent(R.id.v_assist, ActionsWidget.act(c, "assist", 2));
        for (int id : ids) m.updateAppWidget(id, v);
    }
}
