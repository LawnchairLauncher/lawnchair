package com.android.launcher3;

import static com.android.launcher3.LauncherPrefs.APP_WIDGET_IDS;
import static com.android.launcher3.LauncherPrefs.OLD_APP_WIDGET_IDS;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.launcher3.logging.FileLog;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.util.Arrays;

public class AppWidgetsRestoredReceiver extends BroadcastReceiver {

    private static final String TAG = "AppWidgetsRestoredReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (AppWidgetManager.ACTION_APPWIDGET_HOST_RESTORED.equals(intent.getAction())) {
            int hostId = intent.getIntExtra(AppWidgetManager.EXTRA_HOST_ID, 0);
            Log.d(TAG, "onReceive: Widget ID map received for host:" + hostId);
            if (hostId != LauncherWidgetHolder.APPWIDGET_HOST_ID) {
                Log.w(TAG,  "onReceive: hostId does not match Launcher."
                        + " Expected: " + LauncherWidgetHolder.APPWIDGET_HOST_ID
                        + ", Actual: " + hostId);
                return;
            }

            final int[] oldIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS);
            final int[] newIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
            if (oldIds != null && newIds != null && oldIds.length == newIds.length) {
                LauncherPrefs.get(context).putSync(
                        OLD_APP_WIDGET_IDS.to(IntArray.wrap(oldIds).toConcatString()),
                        APP_WIDGET_IDS.to(IntArray.wrap(newIds).toConcatString()));
                FileLog.d(TAG, "onReceive: Valid Widget IDs received."
                        + " old IDs=" + Arrays.toString(oldIds)
                        + ", new IDs=" + Arrays.toString(newIds));
                if (!RestoreDbTask.isPending(context)) {
                    FileLog.w(TAG, "onReceive: Restored App Widget Ids received but Launcher"
                            + " restore is not pending. New widget Ids might not get restored.");
                }
            } else {
                Log.e(TAG, "onReceive: Invalid widget ids received for Launcher"
                        + ", skipping restore of widget ids."
                        + " newIds=" + Arrays.toString(newIds)
                        + ", oldIds=" + Arrays.toString(oldIds));
            }
        }
    }
}