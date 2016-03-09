package com.android.launcher3.logging;

import android.os.Bundle;
import android.util.Log;

import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Stats;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.userevent.nano.LauncherLogProto;

import java.util.Locale;

public abstract class UserEventLogger {

    private String TAG = "UserEventLogger";
    private boolean DEBUG = false;

    private long mElapsedContainerMillis;
    private long mElapsedSessionMillis;
    private long mActionDurationMillis;


    public final void logAppLaunch(String provider, ShortcutInfo shortcut, Bundle bundle) {
        if (FeatureFlags.LAUNCHER3_LEGACY_LOGGING) return;

        LauncherLogProto.LauncherEvent event = new LauncherLogProto.LauncherEvent();
        event.action = new LauncherLogProto.Action();
        event.action.type = LauncherLogProto.Action.TOUCH;
        event.action.touch = LauncherLogProto.Action.TAP;

        event.srcTarget = new LauncherLogProto.Target();
        event.srcTarget.type = LauncherLogProto.Target.ITEM;
        event.srcTarget.itemType = LauncherLogProto.APP_ICON;
        // TODO: package hash name should be different per device.
        event.srcTarget.packageNameHash = provider.hashCode();

        event.srcTarget.parent = new LauncherLogProto.Target();
        String subContainer = bundle.getString(Stats.SOURCE_EXTRA_SUB_CONTAINER);

        if (shortcut != null) {
            event.srcTarget.parent.containerType = LoggerUtils.getContainerType(shortcut);
            event.srcTarget.pageIndex = (int) shortcut.screenId;
            event.srcTarget.gridX = shortcut.cellX;
            event.srcTarget.gridX = shortcut.cellY;
        }
        if (subContainer != null) {
            event.srcTarget.parent.type = LauncherLogProto.Target.CONTAINER;
            if (subContainer.equals(Stats.SUB_CONTAINER_FOLDER)) {
                event.srcTarget.parent.containerType = LauncherLogProto.FOLDER;
            } else if (subContainer.equals(Stats.SUB_CONTAINER_ALL_APPS_A_Z)) {
                event.srcTarget.parent.containerType = LauncherLogProto.ALLAPPS;
            } else if (subContainer.equals(Stats.CONTAINER_HOTSEAT)) {
                event.srcTarget.parent.containerType = LauncherLogProto.HOTSEAT;
            } else if (subContainer.equals(Stats.SUB_CONTAINER_ALL_APPS_PREDICTION)) {
                event.srcTarget.parent.containerType = LauncherLogProto.PREDICTION;
            }

            if (DEBUG) {
                Log.d(TAG, String.format("parent bundle: %s %s %s %s",
                        bundle.getString(Stats.SOURCE_EXTRA_CONTAINER),
                        bundle.getString(Stats.SOURCE_EXTRA_CONTAINER_PAGE),
                        bundle.getString(Stats.SOURCE_EXTRA_SUB_CONTAINER),
                        bundle.getString(Stats.SOURCE_EXTRA_SUB_CONTAINER_PAGE)));
            }
        }
        event.elapsedContainerMillis = System.currentTimeMillis() - mElapsedContainerMillis;
        event.elapsedSessionMillis = System.currentTimeMillis() - mElapsedSessionMillis;
        processEvent(event);
    }

    /**
     * Currently logs following containers: workspace, allapps, widget tray.
     */
    public final void resetElapsedContainerMillis() {
        mElapsedContainerMillis = System.currentTimeMillis();
        if(DEBUG) {
            Log.d(TAG, "resetElapsedContainerMillis " + mElapsedContainerMillis);
        }
    }

    public final void resetElapsedSessionMillis() {
        mElapsedSessionMillis = System.currentTimeMillis();
        mElapsedContainerMillis = System.currentTimeMillis();
        if(DEBUG) {
            Log.d(TAG, "resetElapsedSessionMillis " + mElapsedSessionMillis);
        }
    }

    public final void resetActionDurationMillis() {
        mActionDurationMillis = System.currentTimeMillis();
        if(DEBUG) {
            Log.d(TAG, "resetElapsedContainerMillis " + mElapsedContainerMillis);
        }
    }

    public abstract void processEvent(LauncherLogProto.LauncherEvent ev);
}