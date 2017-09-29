/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.logging;

import android.util.ArrayMap;
import android.util.SparseArray;
import android.view.View;

import com.android.launcher3.ButtonDropTarget;
import com.android.launcher3.DeleteDropTarget;
import com.android.launcher3.InfoDropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.UninstallDropTarget;
import com.android.launcher3.userevent.nano.LauncherLogExtensions.TargetExtension;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ItemType;
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Helper methods for logging.
 */
public class LoggerUtils {
    private static final ArrayMap<Class, SparseArray<String>> sNameCache = new ArrayMap<>();
    private static final String UNKNOWN = "UNKNOWN";

    public static String getFieldName(int value, Class c) {
        SparseArray<String> cache;
        synchronized (sNameCache) {
            cache = sNameCache.get(c);
            if (cache == null) {
                cache = new SparseArray<>();
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == int.class && Modifier.isStatic(f.getModifiers())) {
                        try {
                            f.setAccessible(true);
                            cache.put(f.getInt(null), f.getName());
                        } catch (IllegalAccessException e) {
                            // Ignore
                        }
                    }
                }
                sNameCache.put(c, cache);
            }
        }
        String result = cache.get(value);
        return result != null ? result : UNKNOWN;
    }

    public static String getActionStr(Action action) {
        String str = "";
        switch (action.type) {
            case Action.Type.TOUCH:
                str += getFieldName(action.touch, Action.Touch.class);
                if (action.touch == Action.Touch.SWIPE) {
                    str += " direction=" + getFieldName(action.dir, Action.Direction.class);
                }
                return str;
            case Action.Type.COMMAND: return getFieldName(action.command, Action.Command.class);
            default: return UNKNOWN;
        }
    }

    public static String getTargetStr(Target t) {
        if (t == null){
            return "";
        }
        switch (t.type) {
            case Target.Type.ITEM:
                return getItemStr(t);
            case Target.Type.CONTROL:
                return getFieldName(t.controlType, ControlType.class);
            case Target.Type.CONTAINER:
                String str = getFieldName(t.containerType, ContainerType.class);
                if (t.containerType == ContainerType.WORKSPACE) {
                    str += " id=" + t.pageIndex;
                } else if (t.containerType == ContainerType.FOLDER) {
                    str += " grid(" + t.gridX + "," + t.gridY+ ")";
                }
                return str;
            default:
                return "UNKNOWN TARGET TYPE";
        }
    }

    private static String getItemStr(Target t) {
        String typeStr = getFieldName(t.itemType, ItemType.class);
        if (t.packageNameHash != 0) {
            typeStr += ", packageHash=" + t.packageNameHash + ", predictiveRank=" + t.predictedRank;
        }
        if (t.componentHash != 0) {
            typeStr += ", componentHash=" + t.componentHash + ", predictiveRank=" + t.predictedRank;
        }
        if (t.intentHash != 0) {
            typeStr += ", intentHash=" + t.intentHash + ", predictiveRank=" + t.predictedRank;
        }
        return typeStr + ", grid(" + t.gridX + "," + t.gridY + "), span(" + t.spanX + "," + t.spanY
                + "), pageIdx=" + t.pageIndex;
    }

    public static Target newItemTarget(View v) {
        return (v.getTag() instanceof ItemInfo)
                ? newItemTarget((ItemInfo) v.getTag())
                : newTarget(Target.Type.ITEM);
    }

    public static Target newItemTarget(ItemInfo info) {
        Target t = newTarget(Target.Type.ITEM);

        switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                t.itemType = ItemType.APP_ICON;
                t.predictedRank = -100; // Never assigned
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                t.itemType = ItemType.SHORTCUT;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                t.itemType = ItemType.FOLDER_ICON;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                t.itemType = ItemType.WIDGET;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                t.itemType = ItemType.DEEPSHORTCUT;
                break;
        }
        return t;
    }

    public static Target newDropTarget(View v) {
        if (!(v instanceof ButtonDropTarget)) {
            return newTarget(Target.Type.CONTAINER);
        }
        Target t = newTarget(Target.Type.CONTROL);
        if (v instanceof InfoDropTarget) {
            t.controlType = ControlType.APPINFO_TARGET;
        } else if (v instanceof UninstallDropTarget) {
            t.controlType = ControlType.UNINSTALL_TARGET;
        } else if (v instanceof DeleteDropTarget) {
            t.controlType = ControlType.REMOVE_TARGET;
        }
        return t;
    }

    public static Target newTarget(int targetType, TargetExtension extension) {
        Target t = new Target();
        t.type = targetType;
        t.extension = extension;
        return t;
    }

    public static Target newTarget(int targetType) {
        Target t = new Target();
        t.type = targetType;
        return t;
    }
    public static Target newContainerTarget(int containerType) {
        Target t = newTarget(Target.Type.CONTAINER);
        t.containerType = containerType;
        return t;
    }

    public static Action newAction(int type) {
        Action a = new Action();
        a.type = type;
        return a;
    }
    public static Action newCommandAction(int command) {
        Action a = newAction(Action.Type.COMMAND);
        a.command = command;
        return a;
    }
    public static Action newTouchAction(int touch) {
        Action a = newAction(Action.Type.TOUCH);
        a.touch = touch;
        return a;
    }

    public static LauncherEvent newLauncherEvent(Action action, Target... srcTargets) {
        LauncherEvent event = new LauncherEvent();
        event.srcTarget = srcTargets;
        event.action = action;
        return event;
    }
}
