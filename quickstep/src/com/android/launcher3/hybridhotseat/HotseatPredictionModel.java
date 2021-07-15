/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.hybridhotseat;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;

import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Workspace;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Model helper for app predictions in workspace
 */
public class HotseatPredictionModel {
    private static final String APP_LOCATION_HOTSEAT = "hotseat";
    private static final String APP_LOCATION_WORKSPACE = "workspace";

    private static final String BUNDLE_KEY_PIN_EVENTS = "pin_events";
    private static final String BUNDLE_KEY_CURRENT_ITEMS = "current_items";

    /**
     * Creates and returns bundle using workspace items
     */
    public static Bundle convertDataModelToAppTargetBundle(Context context, BgDataModel dataModel) {
        Bundle bundle = new Bundle();
        ArrayList<AppTargetEvent> events = new ArrayList<>();
        ArrayList<ItemInfo> workspaceItems = dataModel.getAllWorkspaceItems();
        for (ItemInfo item : workspaceItems) {
            AppTarget target = getAppTargetFromInfo(context, item);
            if (target != null && !isTrackedForPrediction(item)) continue;
            events.add(wrapAppTargetWithLocation(target, AppTargetEvent.ACTION_PIN, item));
        }
        ArrayList<AppTarget> currentTargets = new ArrayList<>();
        FixedContainerItems hotseatItems = dataModel.extraItems.get(CONTAINER_HOTSEAT_PREDICTION);
        if (hotseatItems != null) {
            for (ItemInfo itemInfo : hotseatItems.items) {
                AppTarget target = getAppTargetFromInfo(context, itemInfo);
                if (target != null) currentTargets.add(target);
            }
        }
        bundle.putParcelableArrayList(BUNDLE_KEY_PIN_EVENTS, events);
        bundle.putParcelableArrayList(BUNDLE_KEY_CURRENT_ITEMS, currentTargets);
        return bundle;
    }

    /**
     * Creates and returns for {@link AppTarget} object given an {@link ItemInfo}. Returns null
     * if item is not supported prediction
     */
    public static AppTarget getAppTargetFromInfo(Context context, ItemInfo info) {
        if (info == null) return null;
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                && info instanceof LauncherAppWidgetInfo
                && ((LauncherAppWidgetInfo) info).providerName != null) {
            ComponentName cn = ((LauncherAppWidgetInfo) info).providerName;
            return new AppTarget.Builder(new AppTargetId("widget:" + cn.getPackageName()),
                    cn.getPackageName(), info.user).setClassName(cn.getClassName()).build();
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                && info.getTargetComponent() != null) {
            ComponentName cn = info.getTargetComponent();
            return new AppTarget.Builder(new AppTargetId("app:" + cn.getPackageName()),
                    cn.getPackageName(), info.user).setClassName(cn.getClassName()).build();
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                && info instanceof WorkspaceItemInfo) {
            ShortcutKey shortcutKey = ShortcutKey.fromItemInfo(info);
            //TODO: switch to using full shortcut info
            return new AppTarget.Builder(new AppTargetId("shortcut:" + shortcutKey.getId()),
                    shortcutKey.componentName.getPackageName(), shortcutKey.user).build();
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
            return new AppTarget.Builder(new AppTargetId("folder:" + info.id),
                    context.getPackageName(), info.user).build();
        }
        return null;
    }

    /**
     * Creates and returns {@link AppTargetEvent} from an {@link AppTarget}, action, and item
     * location using {@link ItemInfo}
     */
    public static AppTargetEvent wrapAppTargetWithLocation(
            AppTarget target, int action, ItemInfo info) {
        String location = String.format(Locale.ENGLISH, "%s/%d/[%d,%d]/[%d,%d]",
                info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                        ? APP_LOCATION_HOTSEAT : APP_LOCATION_WORKSPACE,
                info.screenId, info.cellX, info.cellY, info.spanX, info.spanY);
        return new AppTargetEvent.Builder(target, action).setLaunchLocation(location).build();
    }

    /**
     * Helper method to determine if {@link ItemInfo} should be tracked and reported to predictors
     */
    public static boolean isTrackedForPrediction(ItemInfo info) {
        return info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT || (
                info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP
                        && info.screenId == Workspace.FIRST_SCREEN_ID);
    }
}
