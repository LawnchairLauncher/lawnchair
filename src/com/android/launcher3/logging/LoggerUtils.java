package com.android.launcher3.logging;

import android.view.View;

import com.android.launcher3.ButtonDropTarget;
import com.android.launcher3.DeleteDropTarget;
import com.android.launcher3.InfoDropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.UninstallDropTarget;
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

    public static String getActionStr(LauncherLogProto.Action action) {
        switch(action.touch) {
            case Action.TAP: return "TAP";
            case Action.LONGPRESS: return "LONGPRESS";
            case Action.DRAGDROP: return "DRAGDROP";
            case Action.PINCH: return "PINCH";
            case Action.SWIPE: return "SWIPE";
            case Action.FLING: return "FLING";
            default: return "UNKNOWN";
        }
    }

    public static String getTargetStr(Target t) {
        String typeStr = "";
        if (t == null){
            return typeStr;
        }
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
        if (t == null){
            return typeStr;
        }
        switch(t.itemType){
            case LauncherLogProto.APP_ICON: typeStr = "APPICON"; break;
            case LauncherLogProto.SHORTCUT: typeStr = "SHORTCUT"; break;
            case LauncherLogProto.WIDGET: typeStr = "WIDGET"; break;
            case LauncherLogProto.DEEPSHORTCUT: typeStr = "DEEPSHORTCUT"; break;
            case LauncherLogProto.FOLDER_ICON: typeStr = "FOLDERICON"; break;
            case LauncherLogProto.SEARCHBOX: typeStr = "SEARCHBOX"; break;

            default: typeStr = "UNKNOWN";
        }

        if (t.packageNameHash != 0) {
            typeStr += ", packageHash=" + t.packageNameHash;
        }
        if (t.componentHash != 0) {
            typeStr += ", componentHash=" + t.componentHash;
        }
        if (t.intentHash != 0) {
            typeStr += ", intentHash=" + t.intentHash;
        }
        if (t.spanX != 0) {
            typeStr += ", spanX=" + t.spanX;
        }
        return typeStr += ", grid=(" + t.gridX + "," + t.gridY + "), id=" + t.pageIndex;
    }

    private static String getControlStr(Target t) {
        if (t == null){
            return "";
        }
        switch(t.controlType) {
            case LauncherLogProto.ALL_APPS_BUTTON: return "ALL_APPS_BUTTON";
            case LauncherLogProto.WIDGETS_BUTTON: return "WIDGETS_BUTTON";
            case LauncherLogProto.WALLPAPER_BUTTON: return "WALLPAPER_BUTTON";
            case LauncherLogProto.SETTINGS_BUTTON: return "SETTINGS_BUTTON";
            case LauncherLogProto.REMOVE_TARGET: return "REMOVE_TARGET";
            case LauncherLogProto.UNINSTALL_TARGET: return "UNINSTALL_TARGET";
            case LauncherLogProto.APPINFO_TARGET: return "APPINFO_TARGET";
            case LauncherLogProto.RESIZE_HANDLE: return "RESIZE_HANDLE";
            default: return "UNKNOWN";
        }
    }

    private static String getContainerStr(LauncherLogProto.Target t) {
        String str = "";
        if (t == null) {
            return str;
        }
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
            case LauncherLogProto.DEEPSHORTCUTS:
                str = "DEEPSHORTCUTS";
                break;
            default:
                str = "UNKNOWN";
        }
        return str + " id=" + t.pageIndex;
    }

    /**
     * Used for launching an event by tapping on an icon.
     */
    public static LauncherLogProto.LauncherEvent initLauncherEvent(
            int actionType,
            View v,
            int parentTargetType){
        LauncherLogProto.LauncherEvent event = new LauncherLogProto.LauncherEvent();

        event.srcTarget = new LauncherLogProto.Target[2];
        event.srcTarget[0] = initTarget(v);
        event.srcTarget[1] = new LauncherLogProto.Target();
        event.srcTarget[1].type = parentTargetType;

        event.action = new LauncherLogProto.Action();
        event.action.type = actionType;
        return event;
    }

    /**
     * Used for clicking on controls and buttons.
     */
    public static LauncherLogProto.LauncherEvent initLauncherEvent(
            int actionType,
            int childTargetType){
        LauncherLogProto.LauncherEvent event = new LauncherLogProto.LauncherEvent();

        event.srcTarget = new LauncherLogProto.Target[1];
        event.srcTarget[0] = new LauncherLogProto.Target();
        event.srcTarget[0].type = childTargetType;

        event.action = new LauncherLogProto.Action();
        event.action.type = actionType;
        return event;
    }

    /**
     * Used for drag and drop interaction.
     */
    public static LauncherLogProto.LauncherEvent initLauncherEvent(
            int actionType,
            View v,
            ItemInfo info,
            int parentSrcTargetType,
            View parentDestTargetType){
        LauncherLogProto.LauncherEvent event = new LauncherLogProto.LauncherEvent();

        event.srcTarget = new LauncherLogProto.Target[2];
        event.srcTarget[0] = initTarget(v, info);
        event.srcTarget[1] = new LauncherLogProto.Target();
        event.srcTarget[1].type = parentSrcTargetType;

        event.destTarget = new LauncherLogProto.Target[2];
        event.destTarget[0] = initTarget(v, info);
        event.destTarget[1] = initDropTarget(parentDestTargetType);

        event.action = new LauncherLogProto.Action();
        event.action.type = actionType;
        return event;
    }

    private static Target initTarget(View v, ItemInfo info) {
        Target t = new LauncherLogProto.Target();
        t.type = Target.ITEM;
        switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                t.itemType = LauncherLogProto.APP_ICON;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                t.itemType = LauncherLogProto.SHORTCUT;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                t.itemType = LauncherLogProto.FOLDER_ICON;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                t.itemType = LauncherLogProto.WIDGET;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                t.itemType = LauncherLogProto.DEEPSHORTCUT;
                break;
        }
        return t;
    }

    private static Target initDropTarget(View v) {
        Target t = new LauncherLogProto.Target();
        t.type = (v instanceof ButtonDropTarget)? Target.CONTROL : Target.CONTAINER;
        if (t.type == Target.CONTAINER) {
            return t;
        }

        if (v instanceof InfoDropTarget) {
            t.controlType = LauncherLogProto.APPINFO_TARGET;
        } else if (v instanceof UninstallDropTarget) {
            t.controlType = LauncherLogProto.UNINSTALL_TARGET;
        } else if (v instanceof DeleteDropTarget) {
            t.controlType = LauncherLogProto.REMOVE_TARGET;
        }
        return t;
    }

    private static Target initTarget(View v) {
        Target t = new LauncherLogProto.Target();
        t.type = Target.ITEM;
        if (!(v.getTag() instanceof ItemInfo)) {
            return t;
        }
        return initTarget(v, (ItemInfo) v.getTag());
    }
}
