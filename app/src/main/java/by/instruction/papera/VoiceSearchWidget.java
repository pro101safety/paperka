package by.instruction.papera;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

public class VoiceSearchWidget extends AppWidgetProvider {

    static void refreshAll(Context context) {
        if (context == null) {
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, VoiceSearchWidget.class));
        if (ids == null || ids.length == 0) {
            return;
        }
        RemoteViews views = buildViews(context);
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        RemoteViews views = buildViews(context);
        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_voice_search);
        views.setOnClickPendingIntent(R.id.widgetMicButton, voicePendingIntent(context, 11));
        views.setOnClickPendingIntent(R.id.widgetTitle, voicePendingIntent(context, 12));

        WidgetSearchStore.Result last = WidgetSearchStore.load(context);
        if (last.query == null || last.query.trim().isEmpty()) {
            views.setTextViewText(R.id.widgetQuery, context.getString(R.string.widget_search_idle));
            views.setTextViewText(R.id.widgetResultTitle, "");
            views.setTextViewText(R.id.widgetResultSnippet, context.getString(R.string.widget_search_hint));
            views.setViewVisibility(R.id.widgetResultTitle, View.GONE);
            views.setOnClickPendingIntent(R.id.widgetResultCard, voicePendingIntent(context, 13));
            return views;
        }

        views.setTextViewText(R.id.widgetQuery, context.getString(R.string.widget_search_query, last.query));
        if (last.found) {
            views.setViewVisibility(R.id.widgetResultTitle, View.VISIBLE);
            views.setTextViewText(R.id.widgetResultTitle, last.documentName);
            views.setTextViewText(R.id.widgetResultSnippet, last.snippet);
            PendingIntent openDocument = documentPendingIntent(context, last);
            views.setOnClickPendingIntent(R.id.widgetResultCard,
                    openDocument != null ? openDocument : voicePendingIntent(context, 13));
        } else {
            views.setViewVisibility(R.id.widgetResultTitle, View.GONE);
            views.setTextViewText(R.id.widgetResultSnippet, context.getString(R.string.widget_search_nothing));
            views.setOnClickPendingIntent(R.id.widgetResultCard, voicePendingIntent(context, 13));
        }
        return views;
    }

    private static PendingIntent voicePendingIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, VoiceSearchActivity.class);
        intent.putExtra(VoiceSearchActivity.EXTRA_AUTO_LISTEN, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, requestCode, intent, pendingFlags());
    }

    private static PendingIntent documentPendingIntent(Context context, WidgetSearchStore.Result result) {
        Intent intent = DocumentOpener.createOpenIntent(
                context, result.fileKey, result.documentName, result.highlightQuery);
        if (intent == null) {
            return null;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 21, intent, pendingFlags());
    }

    private static int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
