package com.android.launcher3;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Handler;
import android.util.Log;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.util.ContentWriter;

public class AppWidgetsRestoredReceiver extends BroadcastReceiver {

    private static final String TAG = "AWRestoredReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (AppWidgetManager.ACTION_APPWIDGET_HOST_RESTORED.equals(intent.getAction())) {
            final int[] oldIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS);
            final int[] newIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if (oldIds.length == newIds.length) {
                final PendingResult asyncResult = goAsync();
                new Handler(LauncherModel.getWorkerLooper())
                        .postAtFrontOfQueue(new Runnable() {
                            @Override
                            public void run() {
                                restoreAppWidgetIds(context, asyncResult, oldIds, newIds);
                            }
                        });
            } else {
                Log.e(TAG, "Invalid host restored received");
            }
        }
    }

    /**
     * Updates the app widgets whose id has changed during the restore process.
     */
    static void restoreAppWidgetIds(Context context, PendingResult asyncResult,
            int[] oldWidgetIds, int[] newWidgetIds) {
        final ContentResolver cr = context.getContentResolver();
        final AppWidgetManager widgets = AppWidgetManager.getInstance(context);
        AppWidgetHost appWidgetHost = new AppWidgetHost(context, Launcher.APPWIDGET_HOST_ID);

        for (int i = 0; i < oldWidgetIds.length; i++) {
            Log.i(TAG, "Widget state restore id " + oldWidgetIds[i] + " => " + newWidgetIds[i]);

            final AppWidgetProviderInfo provider = widgets.getAppWidgetInfo(newWidgetIds[i]);
            final int state;
            if (LauncherModel.isValidProvider(provider)) {
                // This will ensure that we show 'Click to setup' UI if required.
                state = LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
            } else {
                state = LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;
            }

            String[] widgetIdParams = new String[] { Integer.toString(oldWidgetIds[i]) };
            int result = new ContentWriter(context, new ContentWriter.CommitParams(
                    "appWidgetId=? and (restored & 1) = 1", widgetIdParams))
                    .put(LauncherSettings.Favorites.APPWIDGET_ID, newWidgetIds[i])
                    .put(LauncherSettings.Favorites.RESTORED, state)
                    .commit();

            if (result == 0) {
                Cursor cursor = cr.query(Favorites.CONTENT_URI,
                        new String[] {Favorites.APPWIDGET_ID},
                        "appWidgetId=?", widgetIdParams, null);
                try {
                    if (!cursor.moveToFirst()) {
                        // The widget no long exists.
                        appWidgetHost.deleteAppWidgetId(newWidgetIds[i]);
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.getModel().forceReload();
        }
        asyncResult.finish();
    }
}
