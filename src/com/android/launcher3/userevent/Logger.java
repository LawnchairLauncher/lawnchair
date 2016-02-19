package com.android.launcher3.userevent;

import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Stats;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.util.Locale;

/**
 * Creates {@LauncherLogProto} nano protobuf object that can be used for user event
 * metrics analysis.
 */
public class Logger {

    private static final String TAG = "UserEventLogger";
    private static final boolean DEBUG = false;

    private long mActionDurationMillis;
    private long mElapsedContainerMillis;
    private long mElapsedSessionMillis;

    private Context mContext;

    public Logger(Context context) {
        mContext = context;
    }

    public void logAppLaunch(String provider, ShortcutInfo shortcut, Bundle bundle) {
        LauncherEvent event = new LauncherEvent();
        event.action = new Action();
        event.action.type = Action.TOUCH;
        event.action.touch = Action.TAP;

        event.srcTarget = new Target();
        event.srcTarget.type = Target.ITEM;
        event.srcTarget.itemType = LauncherLogProto.APP_ICON;
        // TODO: package hash name should be different per device.
        event.srcTarget.packageNameHash = provider.hashCode();

        event.srcTarget.parent = new Target();
        String subContainer = bundle.getString(Stats.SOURCE_EXTRA_SUB_CONTAINER);

        if (shortcut != null) {
            event.srcTarget.parent.containerType = getContainerType(shortcut);
            event.srcTarget.pageIndex = (int) shortcut.screenId;
            event.srcTarget.gridX = shortcut.cellX;
            event.srcTarget.gridX = shortcut.cellY;
        }
        if (subContainer != null) {
            event.srcTarget.parent.type = Target.CONTAINER;
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


        // Assign timeToAction
        event.elapsedContainerMillis = System.currentTimeMillis() - mElapsedContainerMillis;
        event.elapsedSessionMillis = System.currentTimeMillis() - mElapsedSessionMillis;

        // Debug
        processLauncherEvent(event);
    }

    public void resetElapsedContainerMillis() {
        mElapsedContainerMillis = System.currentTimeMillis();
    }

    public void resetElapsedSessionMillis() {
        mElapsedSessionMillis = System.currentTimeMillis();
    }

    //
    // Debugging helper methods.
    // toString() cannot be overriden inside auto generated {@link LauncherLogProto}.
    // Note: switch statement cannot be replaced with reflection as proguard strips the constants

    private static void processLauncherEvent(LauncherEvent ev) {
        if (DEBUG) {
            if (ev.action.touch == Action.TAP && ev.srcTarget.itemType == LauncherLogProto.APP_ICON) {
                Log.d(TAG, String.format(Locale.US, "action:%s target:%s\n\telapsed container %d ms session %d ms",
                        getActionStr(ev.action),
                        getTargetStr(ev.srcTarget),
                        ev.elapsedContainerMillis,
                        ev.elapsedSessionMillis));
            }
        }
    }

    private static int getContainerType(ShortcutInfo shortcut) {
        switch ((int) shortcut.container) {
            case LauncherSettings.Favorites.CONTAINER_DESKTOP: return LauncherLogProto.WORKSPACE;
            case LauncherSettings.Favorites.CONTAINER_HOTSEAT: return LauncherLogProto.HOTSEAT;
            default:
                return (int) shortcut.container;
        }
    }

    private static String getActionStr(Action action) {
        switch(action.touch) {
            case Action.TAP: return "TAP";
            case Action.LONGPRESS: return "LONGPRESS";
            case Action.DRAGDROP: return "DRAGDROP";
            case Action.PINCH: return "PINCH";
            default: return "UNKNOWN";
        }
    }

    private static String getTargetStr(Target t) {
        String typeStr;
        switch (t.type) {
            case LauncherLogProto.Target.ITEM:
                return getItemStr(t);
            case LauncherLogProto.Target.CONTROL:
                return getControlStr(t);
            case LauncherLogProto.Target.CONTAINER:
                return getContainerStr(t);
            default:
                return "UNKNOWN TARGET TYPE";
        }
    }

    private static String getItemStr(Target t) {
        String typeStr = "";
        switch(t.itemType){
            case LauncherLogProto.APP_ICON: typeStr = "ICON"; break;
            case LauncherLogProto.SHORTCUT: typeStr = "SHORTCUT"; break;
            case LauncherLogProto.WIDGET: typeStr = "WIDGET"; break;
            default: typeStr = "UNKNOWN";
        }

        return typeStr + " " + t.packageNameHash + " grid=(" + t.gridX + "," + t.gridY + ") "
                + getContainerStr(t.parent);
    }

    private static String getControlStr(Target t) {
        switch(t.controlType) {
            case LauncherLogProto.ALL_APPS_BUTTON: return "ALL_APPS_BUTTON";
            case LauncherLogProto.WIDGETS_BUTTON: return "WIDGETS_BUTTON";
            case LauncherLogProto.WALLPAPER_BUTTON: return "WALLPAPER_BUTTON";
            case LauncherLogProto.SETTINGS_BUTTON: return "SETTINGS_BUTTON";
            case LauncherLogProto.REMOVE_TARGET: return "REMOVE_TARGET";
            case LauncherLogProto.UNINSTALL_TARGET: return "UNINSTALL_TARGET";
            case LauncherLogProto.APPINFO_TARGET: return "APPINFO_TARGET";
            case LauncherLogProto.RESIZE_HANDLE: return "RESIZE_HANDLE";
            case LauncherLogProto.FAST_SCROLL_HANDLE: return "FAST_SCROLL_HANDLE";
            default: return "UNKNOWN";
        }
    }

    private static String getContainerStr(Target t) {
        String str;
        Log.d(TAG, "t.containerType" + t.containerType);
        switch (t.containerType) {
            case LauncherLogProto.WORKSPACE:
                str = "WORKSPACE";
                break;
            case LauncherLogProto.HOTSEAT:
                str = "HOTSEAT";
                break;
            case LauncherLogProto.FOLDER:
                str = "FOLDER";
                break;
            case LauncherLogProto.ALLAPPS:
                str = "ALLAPPS";
                break;
            case LauncherLogProto.WIDGETS:
                str = "WIDGETS";
                break;
            case LauncherLogProto.OVERVIEW:
                str = "OVERVIEW";
                break;
            case LauncherLogProto.PREDICTION:
                str = "PREDICTION";
                break;
            case LauncherLogProto.SEARCHRESULT:
                str = "SEARCHRESULT";
                break;
            default:
                str = "UNKNOWN";
        }
        return str + " id=" + t.pageIndex;
    }
}

