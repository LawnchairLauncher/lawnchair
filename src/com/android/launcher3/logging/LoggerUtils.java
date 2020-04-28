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

import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.NAVBAR;

import android.util.ArrayMap;
import android.util.SparseArray;
import android.view.View;

import com.android.launcher3.AppInfo;
import com.android.launcher3.ButtonDropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.userevent.nano.LauncherLogExtensions.TargetExtension;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ItemType;
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.userevent.nano.LauncherLogProto.TipType;
import com.android.launcher3.util.InstantAppResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Helper methods for logging.
 */
public class LoggerUtils {
    private static final ArrayMap<Class, SparseArray<String>> sNameCache = new ArrayMap<>();
    private static final String UNKNOWN = "UNKNOWN";
    private static final int DEFAULT_PREDICTED_RANK = -100;

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
                if (action.touch == Action.Touch.SWIPE || action.touch == Action.Touch.FLING) {
                    str += " direction=" + getFieldName(action.dir, Action.Direction.class);
                }
                break;
            case Action.Type.COMMAND:
                str += getFieldName(action.command, Action.Command.class);
                break;
            default: return getFieldName(action.type, Action.Type.class);
        }
        if (action.touch == Action.Touch.SWIPE || action.touch == Action.Touch.FLING ||
                (action.command == Action.Command.BACK && action.dir != Action.Direction.NONE)) {
            str += " direction=" + getFieldName(action.dir, Action.Direction.class);
        }
        return str;
    }

    public static String getTargetStr(Target t) {
        if (t == null) {
            return "";
        }
        String str = "";
        switch (t.type) {
            case Target.Type.ITEM:
                str = getItemStr(t);
                break;
            case Target.Type.CONTROL:
                str = getFieldName(t.controlType, ControlType.class);
                break;
            case Target.Type.CONTAINER:
                str = getFieldName(t.containerType, ContainerType.class);
                if (t.containerType == ContainerType.WORKSPACE ||
                        t.containerType == ContainerType.HOTSEAT ||
                        t.containerType == NAVBAR) {
                    str += " id=" + t.pageIndex;
                } else if (t.containerType == ContainerType.FOLDER) {
                    str += "[PageIndex=" + t.pageIndex + ", grid(" + t.gridX + "," + t.gridY + ")]";
                }
                break;
            default:
                str += "UNKNOWN TARGET TYPE";
        }

        if (t.spanX != 1 || t.spanY != 1) {
            str += " span(" + t.spanX + "," + t.spanY + ")";
        }

        if (t.tipType != TipType.DEFAULT_NONE) {
            str += " " + getFieldName(t.tipType, TipType.class);
        }

        return str;
    }

    private static String getItemStr(Target t) {
        String typeStr = getFieldName(t.itemType, ItemType.class);
        if (t.packageNameHash != 0) {
            typeStr += ", packageHash=" + t.packageNameHash;
        }
        if (t.componentHash != 0) {
            typeStr += ", componentHash=" + t.componentHash;
        }
        if (t.intentHash != 0) {
            typeStr += ", intentHash=" + t.intentHash;
        }
        if (t.itemType == ItemType.FOLDER_ICON) {
            typeStr += ", grid(" + t.gridX + "," + t.gridY + ")";
        } else if ((t.packageNameHash != 0 || t.componentHash != 0 || t.intentHash != 0)
                && t.itemType != ItemType.TASK) {
            typeStr += ", predictiveRank=" + t.predictedRank + ", grid(" + t.gridX + "," + t.gridY
                    + "), span(" + t.spanX + "," + t.spanY + "), pageIdx=" + t.pageIndex;
        }
        if (t.searchQueryLength != 0) {
            typeStr += ", searchQueryLength=" + t.searchQueryLength;
        }
        if (t.itemType == ItemType.TASK) {
            typeStr += ", pageIdx=" + t.pageIndex;
        }
        return typeStr;
    }

    public static Target newItemTarget(int itemType) {
        Target t = newTarget(Target.Type.ITEM);
        t.itemType = itemType;
        return t;
    }

    public static Target newItemTarget(View v, InstantAppResolver instantAppResolver) {
        return (v != null) && (v.getTag() instanceof ItemInfo)
                ? newItemTarget((ItemInfo) v.getTag(), instantAppResolver)
                : newTarget(Target.Type.ITEM);
    }

    public static Target newItemTarget(ItemInfo info, InstantAppResolver instantAppResolver) {
        Target t = newTarget(Target.Type.ITEM);
        switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                t.itemType = (instantAppResolver != null && info instanceof AppInfo
                        && instantAppResolver.isInstantApp(((AppInfo) info)))
                        ? ItemType.WEB_APP
                        : ItemType.APP_ICON;
                t.predictedRank = DEFAULT_PREDICTED_RANK;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                t.itemType = ItemType.SHORTCUT;
                t.predictedRank = DEFAULT_PREDICTED_RANK;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                t.itemType = ItemType.FOLDER_ICON;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                t.itemType = ItemType.WIDGET;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                t.itemType = ItemType.DEEPSHORTCUT;
                t.predictedRank = DEFAULT_PREDICTED_RANK;
                break;
        }
        return t;
    }

    public static Target newDropTarget(View v) {
        if (!(v instanceof ButtonDropTarget)) {
            return newTarget(Target.Type.CONTAINER);
        }
        if (v instanceof ButtonDropTarget) {
            return ((ButtonDropTarget) v).getDropTargetForLogging();
        }
        return newTarget(Target.Type.CONTROL);
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

    public static Target newControlTarget(int controlType) {
        Target t = newTarget(Target.Type.CONTROL);
        t.controlType = controlType;
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
