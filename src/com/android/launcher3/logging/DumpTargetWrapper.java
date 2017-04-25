/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.Process;
import android.text.TextUtils;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.nano.LauncherDumpProto;
import com.android.launcher3.model.nano.LauncherDumpProto.ContainerType;
import com.android.launcher3.model.nano.LauncherDumpProto.DumpTarget;
import com.android.launcher3.model.nano.LauncherDumpProto.ItemType;
import com.android.launcher3.model.nano.LauncherDumpProto.UserType;

import java.util.ArrayList;
import java.util.List;

/**
 * This class can be used when proto definition doesn't support nesting.
 */
public class DumpTargetWrapper {
    DumpTarget node;
    ArrayList<DumpTargetWrapper> children;

    public DumpTargetWrapper() {
        children = new ArrayList<>();
    }

    public DumpTargetWrapper(int containerType, int id) {
        this();
        node = newContainerTarget(containerType, id);
    }

    public DumpTargetWrapper(ItemInfo info) {
        this();
        node = newItemTarget(info);
    }

    public DumpTarget getDumpTarget() {
        return node;
    }

    public void add(DumpTargetWrapper child) {
        children.add(child);
    }

    public List<DumpTarget> getFlattenedList() {
        ArrayList<DumpTarget> list = new ArrayList<>();
        list.add(node);
        if (!children.isEmpty()) {
            for(DumpTargetWrapper t: children) {
                list.addAll(t.getFlattenedList());
            }
            list.add(node); // add a delimiter empty object
        }
        return list;
    }
    public DumpTarget newItemTarget(ItemInfo info) {
        DumpTarget dt = new DumpTarget();
        dt.type = DumpTarget.Type.ITEM;

        switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                dt.itemType = ItemType.APP_ICON;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                dt.itemType = ItemType.UNKNOWN_ITEMTYPE;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                dt.itemType = ItemType.WIDGET;
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                dt.itemType = ItemType.SHORTCUT;
                break;
        }
        return dt;
    }

    public DumpTarget newContainerTarget(int type, int id) {
        DumpTarget dt = new DumpTarget();
        dt.type = DumpTarget.Type.CONTAINER;
        dt.containerType = type;
        dt.pageId = id;
        return dt;
    }

    public static String getDumpTargetStr(DumpTarget t) {
        if (t == null){
            return "";
        }
        switch (t.type) {
            case LauncherDumpProto.DumpTarget.Type.ITEM:
                return getItemStr(t);
            case LauncherDumpProto.DumpTarget.Type.CONTAINER:
                String str = LoggerUtils.getFieldName(t.containerType, ContainerType.class);
                if (t.containerType == ContainerType.WORKSPACE) {
                    str += " id=" + t.pageId;
                } else if (t.containerType == ContainerType.FOLDER) {
                    str += " grid(" + t.gridX + "," + t.gridY+ ")";
                }
                return str;
            default:
                return "UNKNOWN TARGET TYPE";
        }
    }

    private static String getItemStr(DumpTarget t) {
        String typeStr = LoggerUtils.getFieldName(t.itemType, ItemType.class);
        if (!TextUtils.isEmpty(t.packageName)) {
            typeStr += ", package=" + t.packageName;
        }
        if (!TextUtils.isEmpty(t.component)) {
            typeStr += ", component=" + t.component;
        }
        return typeStr + ", grid(" + t.gridX + "," + t.gridY + "), span(" + t.spanX + "," + t.spanY
                + "), pageIdx=" + t.pageId + " user=" + t.userType;
    }

    public DumpTarget writeToDumpTarget(ItemInfo info) {
        node.component = info.getTargetComponent() == null? "":
                info.getTargetComponent().flattenToString();
        node.packageName = info.getTargetComponent() == null? "":
                info.getTargetComponent().getPackageName();
        if (info instanceof LauncherAppWidgetInfo) {
            node.component = ((LauncherAppWidgetInfo) info).providerName.flattenToString();
            node.packageName = ((LauncherAppWidgetInfo) info).providerName.getPackageName();
        }

        node.gridX = info.cellX;
        node.gridY = info.cellY;
        node.spanX = info.spanX;
        node.spanY = info.spanY;
        node.userType = (info.user.equals(Process.myUserHandle()))? UserType.DEFAULT : UserType.WORK;
        return node;
    }
}
