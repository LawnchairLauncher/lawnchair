/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.model;

import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.WORKSPACE;

import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Workspace;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;

import java.util.Locale;

/** Helper class with methods for converting launcher items to form usable by predictors */
public final class PredictionHelper {
    private static final String APP_LOCATION_HOTSEAT = "hotseat";
    private static final String APP_LOCATION_WORKSPACE = "workspace";

    /**
     * Creates and returns an {@link AppTarget} object for an {@link ItemInfo}. Returns null
     * if item type is not supported in predictions
     */
    @Nullable
    public static AppTarget getAppTargetFromItemInfo(Context context, ItemInfo info) {
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
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR) {
            return new AppTarget.Builder(new AppTargetId("app_pair:" + info.id),
                    context.getPackageName(), info.user).build();
        }
        return null;
    }

    /**
     * Creates and returns {@link AppTargetEvent} from an {@link AppTarget}, action, and item
     * location using {@link ItemInfo}
     */
    public static AppTargetEvent wrapAppTargetWithItemLocation(
            AppTarget target, int action, ItemInfo info) {
        String location = String.format(Locale.ENGLISH, "%s/%d/[%d,%d]/[%d,%d]",
                info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                        ? APP_LOCATION_HOTSEAT : APP_LOCATION_WORKSPACE,
                info.screenId, info.cellX, info.cellY, info.spanX, info.spanY);
        return new AppTargetEvent.Builder(target, action).setLaunchLocation(location).build();
    }

    /**
     * Helper method to determine if {@link ItemInfo} should be tracked and reported to hotseat
     * predictors
     */
    public static boolean isTrackedForHotseatPrediction(ItemInfo info) {
        return info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT || (
                info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP
                        && info.screenId == Workspace.FIRST_SCREEN_ID);
    }

    /**
     * Helper method to determine if {@link LauncherAtom.ItemInfo} should be tracked and reported to
     * hotseat predictors
     */
    public static boolean isTrackedForHotseatPrediction(LauncherAtom.ItemInfo info) {
        LauncherAtom.ContainerInfo ci = info.getContainerInfo();
        switch (ci.getContainerCase()) {
            case HOTSEAT:
                return true;
            case WORKSPACE:
                return ci.getWorkspace().getPageIndex() == 0;
            default:
                return false;
        }
    }

    /**
     * Helper method to determine if {@link ItemInfo} should be tracked and reported to widget
     * predictors
     */
    public static boolean isTrackedForWidgetPrediction(ItemInfo info) {
        return info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                && info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP;
    }

    /**
     * Helper method to determine if {@link LauncherAtom.ItemInfo} should be tracked and reported
     * to widget predictors
     */
    public static boolean isTrackedForWidgetPrediction(LauncherAtom.ItemInfo info) {
        return info.getItemCase() == LauncherAtom.ItemInfo.ItemCase.WIDGET
                && info.getContainerInfo().getContainerCase() == WORKSPACE;
    }
}
