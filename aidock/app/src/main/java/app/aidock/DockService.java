package app.aidock;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class DockService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) { return new Factory(getApplicationContext()); }

    static final class Factory implements RemoteViewsFactory {
        private final Context c; private List<Ai> items = new ArrayList<>();
        Factory(Context c) { this.c = c; }

        @Override public void onCreate() { }
        @Override public void onDataSetChanged() { items = Ai.dock(c); }
        @Override public void onDestroy() { }
        @Override public int getCount() { return items.size(); }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean hasStableIds() { return false; }

        @Override public RemoteViews getViewAt(int i) {
            Ai a = items.get(i);
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_dock_item);
            int px = (int) (44 * c.getResources().getDisplayMetrics().density);
            v.setImageViewBitmap(R.id.icon, a.icon(c, px));
            v.setTextViewText(R.id.label, a.name);
            v.setOnClickFillInIntent(R.id.item, new Intent().putExtra("act", "open").putExtra("ai", a.id));
            return v;
        }
    }
}
