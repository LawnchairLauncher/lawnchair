/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.util.Log
import android.util.SparseArray
import androidx.annotation.VisibleForTesting
import androidx.core.util.valueIterator
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.quickstep.RecentsModel
import kotlin.collections.filterNotNull

/**
 * Shows running apps when in Desktop Mode.
 *
 * Users can enter and exit Desktop Mode at run-time, meaning this class falls back to the default
 * recent-apps behaviour when outside of Desktop Mode.
 *
 * This class should only be used if
 * [com.android.window.flags.Flags.enableDesktopWindowingTaskbarRunningApps] is enabled.
 */
class DesktopTaskbarRunningAppsController(
    private val recentsModel: RecentsModel,
    private val desktopVisibilityController: DesktopVisibilityController?,
) : TaskbarRecentAppsController() {

    private var apps: Array<AppInfo>? = null
    private var allRunningDesktopAppInfos: List<AppInfo>? = null
    private var runningDesktopAppInfosExceptHotseatItems: List<ItemInfo>? = null

    private val isInDesktopMode: Boolean
        get() = desktopVisibilityController?.areDesktopTasksVisible() ?: false

    override fun onDestroy() {
        super.onDestroy()
        apps = null
    }

    @VisibleForTesting
    public override fun setApps(apps: Array<AppInfo>?) {
        this.apps = apps
    }

    override fun isEnabled() = true

    @VisibleForTesting
    public override fun updateHotseatItemInfos(hotseatItems: Array<ItemInfo>?): Array<ItemInfo>? {
        val actualHotseatItems = hotseatItems ?: return super.updateHotseatItemInfos(null)
        if (!isInDesktopMode) {
            Log.d(TAG, "updateHotseatItemInfos: not in Desktop Mode")
            return hotseatItems
        }
        val newHotseatItemInfos =
            actualHotseatItems
                // Ignore predicted apps - we show running apps instead
                .filter { itemInfo -> !itemInfo.isPredictedItem }
                .toMutableList()
        val runningDesktopAppInfos =
            runningDesktopAppInfosExceptHotseatItems ?: return newHotseatItemInfos.toTypedArray()
        newHotseatItemInfos.addAll(runningDesktopAppInfos)
        return newHotseatItemInfos.toTypedArray()
    }

    @VisibleForTesting
    public override fun updateRunningApps(hotseatItems: SparseArray<ItemInfo>?) {
        if (!isInDesktopMode) {
            Log.d(TAG, "updateRunningApps: not in Desktop Mode")
            mControllers.taskbarViewController.commitRunningAppsToUI()
            return
        }
        val allRunningDesktopAppInfos = getRunningDesktopAppInfos()
        this.allRunningDesktopAppInfos = allRunningDesktopAppInfos
        runningDesktopAppInfosExceptHotseatItems =
            hotseatItems?.let {
                getRunningDesktopAppInfosExceptHotseatApps(allRunningDesktopAppInfos, it.toList())
            }

        mControllers.taskbarViewController.commitRunningAppsToUI()
    }

    private fun getRunningDesktopAppInfosExceptHotseatApps(
        allRunningDesktopAppInfos: List<AppInfo>,
        hotseatItems: List<ItemInfo>
    ): List<ItemInfo> {
        val hotseatPackages = hotseatItems.map { it.targetPackage }
        return allRunningDesktopAppInfos
            .filter { appInfo -> !hotseatPackages.contains(appInfo.targetPackage) }
            .map { WorkspaceItemInfo(it) }
    }

    private fun getRunningDesktopAppInfos(): List<AppInfo> {
        return getAppInfosFromRunningTasks(
            recentsModel.runningTasks
                .filter { taskInfo: RunningTaskInfo ->
                    taskInfo.windowingMode == WindowConfiguration.WINDOWING_MODE_FREEFORM
                }
                .toList()
        )
    }

    // TODO(b/335398876) fetch app icons from Tasks instead of AppInfos
    private fun getAppInfosFromRunningTasks(tasks: List<RunningTaskInfo>): List<AppInfo> {
        // Early return if apps is empty, since we then have no AppInfo to compare to
        if (apps == null) {
            return emptyList()
        }
        val packageNames = tasks.map { it.realActivity?.packageName }.distinct().filterNotNull()
        return packageNames
            .map { packageName -> apps?.find { app -> packageName == app.targetPackage } }
            .filterNotNull()
    }

    private fun <E> SparseArray<E>.toList(): List<E> {
        return valueIterator().asSequence().toList()
    }

    companion object {
        private const val TAG = "TabletDesktopTaskbarRunningAppsController"
    }
}
