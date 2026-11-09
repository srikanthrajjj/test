package app.aidock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/** Resizable grid: one tile per AI chat app. */
public class DockWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { update(c, m, ids); }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_dock);
            Intent svc = new Intent(c, DockService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
            v.setRemoteAdapter(R.id.grid, svc);
            v.setEmptyView(R.id.grid, R.id.empty);
            Intent tpl = new Intent(c, ActionActivity.class).setAction("dock");
            v.setPendingIntentTemplate(R.id.grid, PendingIntent.getActivity(c, 0, tpl,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
            m.updateAppWidget(id, v);
        }
    }
}
