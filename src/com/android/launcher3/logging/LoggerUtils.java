package com.android.launcher3.logging;

import android.os.Bundle;
import android.util.Log;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Stats;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;


/**
 * Debugging helper methods.
 * toString() cannot be overriden inside auto generated {@link LauncherLogProto}.
 * Note: switch statement cannot be replaced with reflection as proguard strips the constants
 */
public class LoggerUtils {
    private static final String TAG = "LoggerUtils";
    private static final boolean DEBUG = false;

    static int getContainerType(ShortcutInfo shortcut) {
        switch ((int) shortcut.container) {
            case LauncherSettings.Favorites.CONTAINER_DESKTOP: return LauncherLogProto.WORKSPACE;
            case LauncherSettings.Favorites.CONTAINER_HOTSEAT: return LauncherLogProto.HOTSEAT;
            default:
                return (int) shortcut.container;
        }
    }

    public static String getActionStr(LauncherLogProto.Action action) {
        switch(action.touch) {
            case Action.TAP: return "TAP";
            case Action.LONGPRESS: return "LONGPRESS";
            case Action.DRAGDROP: return "DRAGDROP";
            case Action.PINCH: return "PINCH";
            default: return "UNKNOWN";
        }
    }

    public static String getTargetStr(Target t) {
        String typeStr;
        switch (t.type) {
            case Target.ITEM:
                return getItemStr(t);
            case Target.CONTROL:
                return getControlStr(t);
            case Target.CONTAINER:
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

    private static String getContainerStr(LauncherLogProto.Target t) {
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
