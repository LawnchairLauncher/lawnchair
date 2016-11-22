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
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

/**
 * Debugging helper methods.
 * toString() cannot be overriden inside auto generated {@link LauncherLogProto}.
 * Note: switch statement cannot be replaced with reflection as proguard strips the constants
 */
public class LoggerUtils {
    private static final String TAG = "LoggerUtils";

    private static String getCommandStr(Action action) {
        switch (action.command) {
            case Action.HOME_INTENT: return "HOME_INTENT";
            case Action.BACK: return "BACK";
            default: return "UNKNOWN";
        }
    }

    private static String getTouchStr(Action action) {
        switch (action.touch) {
            case Action.TAP: return "TAP";
            case Action.LONGPRESS: return "LONGPRESS";
            case Action.DRAGDROP: return "DRAGDROP";
            case Action.PINCH: return "PINCH";
            case Action.SWIPE: return "SWIPE";
            case Action.FLING: return "FLING";
            default: return "UNKNOWN";
        }
    }

    public static String getActionStr(LauncherLogProto.Action action) {
        switch (action.type) {
            case Action.TOUCH: return getTouchStr(action);
            case Action.COMMAND: return getCommandStr(action);
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
            case LauncherLogProto.EDITTEXT: typeStr = "EDITTEXT"; break;

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

    public static Target newItemTarget(View v) {
        return (v.getTag() instanceof ItemInfo)
                ? newItemTarget((ItemInfo) v.getTag())
                : newTarget(Target.ITEM);
    }

    public static Target newItemTarget(ItemInfo info) {
        Target t = newTarget(Target.ITEM);
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

    public static Target newDropTarget(View v) {
        if (!(v instanceof ButtonDropTarget)) {
            return newTarget(Target.CONTAINER);
        }
        Target t = newTarget(Target.CONTROL);
        if (v instanceof InfoDropTarget) {
            t.controlType = LauncherLogProto.APPINFO_TARGET;
        } else if (v instanceof UninstallDropTarget) {
            t.controlType = LauncherLogProto.UNINSTALL_TARGET;
        } else if (v instanceof DeleteDropTarget) {
            t.controlType = LauncherLogProto.REMOVE_TARGET;
        }
        return t;
    }

    public static Target newTarget(int targetType) {
        Target t = new LauncherLogProto.Target();
        t.type = targetType;
        return t;
    }
    public static Target newContainerTarget(int containerType) {
        Target t = newTarget(Target.CONTAINER);
        t.containerType = containerType;
        return t;
    }

    public static Action newAction(int type) {
        Action a = new Action();
        a.type = type;
        return a;
    }
    public static Action newCommandAction(int command) {
        Action a = newAction(Action.COMMAND);
        a.command = command;
        return a;
    }
    public static Action newTouchAction(int touch) {
        Action a = newAction(Action.TOUCH);
        a.touch = touch;
        return a;
    }

    public static LauncherEvent newLauncherEvent(Action action, Target... srcTargets) {
        LauncherLogProto.LauncherEvent event = new LauncherLogProto.LauncherEvent();
        event.srcTarget = srcTargets;
        event.action = action;
        return event;
    }
}
