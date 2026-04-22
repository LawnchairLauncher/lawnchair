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
package com.android.launcher3.model

import android.app.prediction.AppTarget
import android.app.prediction.AppTargetEvent
import android.app.prediction.AppTargetId
import android.content.Context
import android.os.Bundle
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.logger.LauncherAtom.ContainerInfo
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.ALL_APPS_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.EXTENDED_CONTAINERS
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.FOLDER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.HOTSEAT
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.PREDICTED_HOTSEAT_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.PREDICTION_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.SEARCH_RESULT_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.SHORTCUTS_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.TASK_BAR_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.TASK_SWITCHER_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.WORKSPACE
import com.android.launcher3.logger.LauncherAtom.FolderContainer.ParentContainerCase
import com.android.launcher3.logger.LauncherAtom.HotseatContainer
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.WIDGET
import com.android.launcher3.logger.LauncherAtom.WorkspaceContainer
import com.android.launcher3.logger.LauncherAtomExtensions.ExtendedContainers.ContainerCase.DEVICE_SEARCH_RESULT_CONTAINER
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey

/** Helper class with methods for converting launcher items to form usable by predictors */
object PredictionHelper {
    const val BUNDLE_KEY_PIN_EVENTS = "pin_events"
    const val BUNDLE_KEY_CURRENT_ITEMS = "current_items"
    const val BUNDLE_KEY_ADDED_APP_WIDGETS = "added_app_widgets"

    /**
     * Helper method to determine if [ItemInfo] should be tracked and reported to hotseat predictors
     */
    @JvmStatic
    fun ItemInfo.isTrackedForHotseatPrediction(): Boolean =
        container == CONTAINER_HOTSEAT ||
            (container == CONTAINER_DESKTOP && screenId == FIRST_SCREEN_ID)

    /**
     * Helper method to determine if [LauncherAtom.ItemInfo] should be tracked and reported to
     * hotseat predictors
     */
    @JvmStatic
    fun LauncherAtom.ItemInfo.isTrackedForHotseatPrediction(): Boolean =
        containerInfo.run {
            when (containerCase) {
                HOTSEAT -> true
                WORKSPACE -> workspace.pageIndex == 0
                else -> false
            }
        }

    /**
     * Helper method to determine if [ItemInfo] should be tracked and reported to widget predictors
     */
    @JvmStatic
    fun ItemInfo.isTrackedForWidgetPrediction(): Boolean =
        itemType == ITEM_TYPE_APPWIDGET && container == CONTAINER_DESKTOP

    /**
     * Helper method to determine if [LauncherAtom.ItemInfo] should be tracked and reported to
     * widget predictors
     */
    @JvmStatic
    fun LauncherAtom.ItemInfo.isTrackedForWidgetPrediction(): Boolean =
        itemCase == WIDGET && containerInfo.containerCase == WORKSPACE

    /** Creates and returns bundle using workspace items for hotseat predictions */
    @JvmStatic
    fun getBundleForHotseatPredictions(context: Context, dataModel: BgDataModel): Bundle {
        return Bundle().apply {
            putParcelableArrayList(
                BUNDLE_KEY_PIN_EVENTS,
                dataModel.getPinItemEvents(context) { it.isTrackedForHotseatPrediction() },
            )
            putParcelableArrayList(
                BUNDLE_KEY_CURRENT_ITEMS,
                dataModel.itemsIdMap
                    .getPredictedContents(CONTAINER_HOTSEAT_PREDICTION)
                    .mapNotNullTo(ArrayList()) { getAppTargetFromItemInfo(context, it) },
            )
        }
    }

    /** Creates and returns bundle using workspace items for widget predictions */
    @JvmStatic
    fun getBundleForWidgetPredictions(context: Context, dataModel: BgDataModel) =
        Bundle().apply {
            putParcelableArrayList(
                BUNDLE_KEY_ADDED_APP_WIDGETS,
                dataModel.getPinItemEvents(context) { it.isTrackedForWidgetPrediction() },
            )
        }

    /** Returns a location string to be used with [AppTargetEvent] for the [ContainerInfo] */
    @JvmStatic
    fun ContainerInfo.getLocationString(spanX: Int, spanY: Int): String =
        when (containerCase) {
            // In case the item type is not widgets, the spaceX and spanY default to 1.
            WORKSPACE -> workspace.getLocationString(spanX, spanY)
            HOTSEAT -> hotseat.getLocationString()
            FOLDER ->
                folder.let {
                    when (it.parentContainerCase) {
                        ParentContainerCase.WORKSPACE ->
                            "folder/" + it.workspace.getLocationString(1, 1)
                        ParentContainerCase.HOTSEAT -> "folder/" + it.hotseat.getLocationString()
                        else -> "folder"
                    }
                }
            TASK_SWITCHER_CONTAINER -> "task-switcher"
            ALL_APPS_CONTAINER -> "all-apps"
            PREDICTED_HOTSEAT_CONTAINER -> "predictions/hotseat"
            PREDICTION_CONTAINER -> "predictions"
            SHORTCUTS_CONTAINER -> "deep-shortcuts"
            TASK_BAR_CONTAINER -> "taskbar"
            SEARCH_RESULT_CONTAINER -> "search-results"
            EXTENDED_CONTAINERS ->
                if (extendedContainers.containerCase == DEVICE_SEARCH_RESULT_CONTAINER)
                    "search-results"
                else ""
            else -> ""
        }

    private fun WorkspaceContainer.getLocationString(spanX: Int, spanY: Int) =
        "workspace/$pageIndex/[$gridX,$gridY]/[$spanX,$spanY]"

    private fun HotseatContainer.getLocationString() = "hotseat/$index/[$index,0]/[1,1]"

    /**
     * Creates [AppTargetEvent] for every item in [BgDataModel] with [AppTargetEvent.ACTION_PIN] and
     * item location using [ItemInfo]
     */
    private fun BgDataModel.getPinItemEvents(context: Context, filter: (ItemInfo) -> Boolean) =
        itemsIdMap.filter(filter).mapNotNullTo(ArrayList()) { info ->
            getAppTargetFromItemInfo(context, info)?.let { target ->
                AppTargetEvent.Builder(target, AppTargetEvent.ACTION_PIN)
                    .setLaunchLocation(info.containerInfo.getLocationString(info.spanX, info.spanY))
                    .build()
            }
        }

    /**
     * Creates and returns an [AppTarget] object for an [ItemInfo]. Returns null if item type is not
     * supported in predictions
     */
    private fun getAppTargetFromItemInfo(context: Context, info: ItemInfo?): AppTarget? {
        if (info == null) return null
        return when {
            info.itemType == ITEM_TYPE_APPWIDGET && info is LauncherAppWidgetInfo ->
                info.providerName?.let { cn ->
                    AppTarget.Builder(
                            AppTargetId("widget:" + cn.packageName),
                            cn.packageName,
                            info.user,
                        )
                        .setClassName(cn.className)
                        .build()
                }
            info.itemType == Favorites.ITEM_TYPE_APPLICATION ->
                info.targetComponent?.let { cn ->
                    AppTarget.Builder(
                            AppTargetId("app:" + cn.packageName),
                            cn.packageName,
                            info.user,
                        )
                        .setClassName(cn.className)
                        .build()
                }

            info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT && info is WorkspaceItemInfo ->
                // TODO: switch to using full shortcut info
                ShortcutKey.fromItemInfo(info).let { shortcutKey ->
                    AppTarget.Builder(
                            AppTargetId("shortcut:" + shortcutKey.id),
                            shortcutKey.componentName.packageName,
                            shortcutKey.user,
                        )
                        .build()
                }
            info.itemType == Favorites.ITEM_TYPE_FOLDER ->
                AppTarget.Builder(AppTargetId("folder:" + info.id), context.packageName, info.user)
                    .build()
            info.itemType == Favorites.ITEM_TYPE_APP_PAIR ->
                AppTarget.Builder(
                        AppTargetId("app_pair:" + info.id),
                        context.packageName,
                        info.user,
                    )
                    .build()
            else -> null
        }
    }
}
