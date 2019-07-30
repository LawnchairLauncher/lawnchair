package com.android.launcher3;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.ContentWriter;

import androidx.annotation.WorkerThread;

import static android.os.Process.myUserHandle;

public class AppWidgetsRestoredReceiver extends BroadcastReceiver {

    private static final String TAG = "AWRestoredReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (AppWidgetManager.ACTION_APPWIDGET_HOST_RESTORED.equals(intent.getAction())) {
            int hostId = intent.getIntExtra(AppWidgetManager.EXTRA_HOST_ID, 0);
            Log.d(TAG, "Widget ID map received for host:" + hostId);
            if (hostId != LauncherAppWidgetHost.APPWIDGET_HOST_ID) {
                return;
            }

            final int[] oldIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS);
            final int[] newIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if (oldIds != null && newIds != null && oldIds.length == newIds.length) {
                RestoreDbTask.setRestoredAppWidgetIds(context, oldIds, newIds);
            } else {
                Log.e(TAG, "Invalid host restored received");
            }
        }
    }

    /**
     * Updates the app widgets whose id has changed during the restore process.
     */
    @WorkerThread
    public static void restoreAppWidgetIds(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        AppWidgetHost appWidgetHost = new LauncherAppWidgetHost(context);
        if (FeatureFlags.GO_DISABLE_WIDGETS) {
            Log.e(TAG, "Skipping widget ID remap as widgets not supported");
            appWidgetHost.deleteHost();
            return;
        }
        if (!RestoreDbTask.isPending(context)) {
            // Someone has already gone through our DB once, probably LoaderTask. Skip any further
            // modifications of the DB.
            Log.e(TAG, "Skipping widget ID remap as DB already in use");
            for (int widgetId : newWidgetIds) {
                Log.d(TAG, "Deleting widgetId: " + widgetId);
                appWidgetHost.deleteAppWidgetId(widgetId);
            }
            return;
        }
        final ContentResolver cr = context.getContentResolver();
        final AppWidgetManager widgets = AppWidgetManager.getInstance(context);

        for (int i = 0; i < oldWidgetIds.length; i++) {
            Log.i(TAG, "Widget state restore id " + oldWidgetIds[i] + " => " + newWidgetIds[i]);

            final AppWidgetProviderInfo provider = widgets.getAppWidgetInfo(newWidgetIds[i]);
            final int state;
            if (LoaderTask.isValidProvider(provider)) {
                // This will ensure that we show 'Click to setup' UI if required.
                state = LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
            } else {
                state = LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;
            }

            // b/135926478: Work profile widget restore is broken in platform. This forces us to
            // recreate the widget during loading with the correct host provider.
            long mainProfileId = UserManagerCompat.getInstance(context)
                    .getSerialNumberForUser(myUserHandle());
            String oldWidgetId = Integer.toString(oldWidgetIds[i]);
            int result = new ContentWriter(context, new ContentWriter.CommitParams(
                    "appWidgetId=? and (restored & 1) = 1 and profileId=?",
                    new String[] { oldWidgetId, Long.toString(mainProfileId) }))
                    .put(LauncherSettings.Favorites.APPWIDGET_ID, newWidgetIds[i])
                    .put(LauncherSettings.Favorites.RESTORED, state)
                    .commit();

            if (result == 0) {
                Cursor cursor = cr.query(Favorites.CONTENT_URI,
                        new String[] {Favorites.APPWIDGET_ID},
                        "appWidgetId=?", new String[] { oldWidgetId }, null);
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
    }
}
