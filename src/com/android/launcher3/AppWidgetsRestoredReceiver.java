package com.android.launcher3;

import static android.os.Process.myUserHandle;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.ModelDbController;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.widget.LauncherWidgetHolder;

public class AppWidgetsRestoredReceiver extends BroadcastReceiver {

    private static final String TAG = "AWRestoredReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (AppWidgetManager.ACTION_APPWIDGET_HOST_RESTORED.equals(intent.getAction())) {
            int hostId = intent.getIntExtra(AppWidgetManager.EXTRA_HOST_ID, 0);
            Log.d(TAG, "Widget ID map received for host:" + hostId);
            if (hostId != LauncherWidgetHolder.APPWIDGET_HOST_ID) {
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
    public static void restoreAppWidgetIds(Context context, ModelDbController controller,
            int[] oldWidgetIds, int[] newWidgetIds, @NonNull AppWidgetHost host) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            Log.e(TAG, "Skipping widget ID remap as widgets not supported");
            host.deleteHost();
            return;
        }
        if (!RestoreDbTask.isPending(context)) {
            // Someone has already gone through our DB once, probably LoaderTask. Skip any further
            // modifications of the DB.
            Log.e(TAG, "Skipping widget ID remap as DB already in use");
            for (int widgetId : newWidgetIds) {
                Log.d(TAG, "Deleting widgetId: " + widgetId);
                host.deleteAppWidgetId(widgetId);
            }
            return;
        }

        final AppWidgetManager widgets = AppWidgetManager.getInstance(context);

        Log.d(TAG, "restoreAppWidgetIds: "
                + "oldWidgetIds=" + IntArray.wrap(oldWidgetIds).toConcatString()
                + ", newWidgetIds=" + IntArray.wrap(newWidgetIds).toConcatString());

        // TODO(b/234700507): Remove the logs after the bug is fixed
        logDatabaseWidgetInfo(controller);

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
            long mainProfileId = UserCache.INSTANCE.get(context)
                    .getSerialNumberForUser(myUserHandle());
            long controllerProfileId = controller.getSerialNumberForUser(myUserHandle());
            String oldWidgetId = Integer.toString(oldWidgetIds[i]);
            final String where = "appWidgetId=? and (restored & 1) = 1 and profileId=?";
            String profileId = Long.toString(mainProfileId);
            final String[] args = new String[] { oldWidgetId, profileId };
            Log.d(TAG, "restoreAppWidgetIds: querying profile id=" + profileId
                    + " with controller profile ID=" + controllerProfileId);
            int result = new ContentWriter(context,
                            new ContentWriter.CommitParams(controller, where, args))
                    .put(LauncherSettings.Favorites.APPWIDGET_ID, newWidgetIds[i])
                    .put(LauncherSettings.Favorites.RESTORED, state)
                    .commit();
            if (result == 0) {
                // TODO(b/234700507): Remove the logs after the bug is fixed
                Log.e(TAG, "restoreAppWidgetIds: remapping failed since the widget is not in"
                        + " the database anymore");
                try (Cursor cursor = controller.getDb().query(
                        Favorites.TABLE_NAME,
                        new String[]{Favorites.APPWIDGET_ID},
                        "appWidgetId=?", new String[]{oldWidgetId}, null, null, null)) {
                    if (!cursor.moveToFirst()) {
                        // The widget no long exists.
                        Log.d(TAG, "Deleting widgetId: " + newWidgetIds[i] + " with old id: "
                                + oldWidgetId);
                        host.deleteAppWidgetId(newWidgetIds[i]);
                    }
                }
            }
        }

        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.getModel().forceReload();
        }
    }

    private static void logDatabaseWidgetInfo(ModelDbController controller) {
        try (Cursor cursor = controller.getDb().query(Favorites.TABLE_NAME,
                new String[]{Favorites.APPWIDGET_ID, Favorites.RESTORED, Favorites.PROFILE_ID},
                Favorites.APPWIDGET_ID + "!=" + LauncherAppWidgetInfo.NO_ID, null,
                null, null, null)) {
            IntArray widgetIdList = new IntArray();
            IntArray widgetRestoreList = new IntArray();
            IntArray widgetProfileIdList = new IntArray();

            if (cursor.moveToFirst()) {
                final int widgetIdColumnIndex = cursor.getColumnIndex(Favorites.APPWIDGET_ID);
                final int widgetRestoredColumnIndex = cursor.getColumnIndex(Favorites.RESTORED);
                final int widgetProfileIdIndex = cursor.getColumnIndex(Favorites.PROFILE_ID);
                while (!cursor.isAfterLast()) {
                    int widgetId = cursor.getInt(widgetIdColumnIndex);
                    int widgetRestoredFlag = cursor.getInt(widgetRestoredColumnIndex);
                    int widgetProfileId = cursor.getInt(widgetProfileIdIndex);

                    widgetIdList.add(widgetId);
                    widgetRestoreList.add(widgetRestoredFlag);
                    widgetProfileIdList.add(widgetProfileId);
                    cursor.moveToNext();
                }
            }

            StringBuilder builder = new StringBuilder();
            builder.append("[");
            for (int i = 0; i < widgetIdList.size(); i++) {
                builder.append("[")
                        .append(widgetIdList.get(i))
                        .append(", ")
                        .append(widgetRestoreList.get(i))
                        .append(", ")
                        .append(widgetProfileIdList.get(i))
                        .append("]");
            }
            builder.append("]");
            Log.d(TAG, "restoreAppWidgetIds: all widget ids in database: "
                    + builder.toString());
        } catch (Exception ex) {
            Log.e(TAG, "Getting widget ids from the database failed", ex);
        }
    }
}
