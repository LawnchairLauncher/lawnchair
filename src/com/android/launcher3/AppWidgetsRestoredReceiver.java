package com.android.launcher3;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import com.android.launcher3.LauncherSettings.Favorites;

import java.util.ArrayList;
import java.util.List;

public class AppWidgetsRestoredReceiver extends BroadcastReceiver {

    private static final String TAG = "AppWidgetsRestoredReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AppWidgetManager.ACTION_APPWIDGET_HOST_RESTORED.equals(intent.getAction())) {
            int[] oldIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS);
            int[] newIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if (oldIds.length == newIds.length) {
                restoreAppWidgetIds(context, oldIds, newIds);
            } else {
                Log.e(TAG, "Invalid host restored received");
            }
        }
    }

    /**
     * Updates the app widgets whose id has changed during the restore process.
     */
    static void restoreAppWidgetIds(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        final ContentResolver cr = context.getContentResolver();
        final List<Integer> idsToRemove = new ArrayList<Integer>();
        final AppWidgetManager widgets = AppWidgetManager.getInstance(context);

        for (int i = 0; i < oldWidgetIds.length; i++) {
            Log.i(TAG, "Widget state restore id " + oldWidgetIds[i] + " => " + newWidgetIds[i]);

            final AppWidgetProviderInfo provider = widgets.getAppWidgetInfo(newWidgetIds[i]);
            final int state;
            if (LauncherModel.isValidProvider(provider)) {
                state = LauncherAppWidgetInfo.RESTORE_COMPLETED;
            } else {
                state = LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;
            }

            ContentValues values = new ContentValues();
            values.put(LauncherSettings.Favorites.APPWIDGET_ID, newWidgetIds[i]);
            values.put(LauncherSettings.Favorites.RESTORED, state);

            String[] widgetIdParams = new String[] { Integer.toString(oldWidgetIds[i]) };

            int result = cr.update(Favorites.CONTENT_URI, values,
                    "appWidgetId=? and (restored & 1) = 1", widgetIdParams);
            if (result == 0) {
                Cursor cursor = cr.query(Favorites.CONTENT_URI,
                        new String[] {Favorites.APPWIDGET_ID},
                        "appWidgetId=?", widgetIdParams, null);
                try {
                    if (!cursor.moveToFirst()) {
                        // The widget no long exists.
                        idsToRemove.add(newWidgetIds[i]);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        // Unregister the widget IDs which are not present on the workspace. This could happen
        // when a widget place holder is removed from workspace, before this method is called.
        if (!idsToRemove.isEmpty()) {
            final AppWidgetHost appWidgetHost =
                    new AppWidgetHost(context, Launcher.APPWIDGET_HOST_ID);
            new AsyncTask<Void, Void, Void>() {
                public Void doInBackground(Void ... args) {
                    for (Integer id : idsToRemove) {
                        appWidgetHost.deleteAppWidgetId(id);
                        Log.e(TAG, "Widget no longer present, appWidgetId=" + id);
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
        }

        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.reloadWorkspace();
        }
    }
}
