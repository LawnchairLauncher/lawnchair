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
import androidx.annotation.VisibleForTesting
import com.android.launcher3.Flags.enableRecentsInTaskbar
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.quickstep.RecentsModel
import com.android.window.flags.Flags.enableDesktopWindowingMode
import com.android.window.flags.Flags.enableDesktopWindowingTaskbarRunningApps
import java.io.PrintWriter

/**
 * Provides recent apps functionality, when the Taskbar Recent Apps section is enabled. Behavior:
 * - When in Fullscreen mode: show the N most recent Tasks
 * - When in Desktop Mode: show the currently running (open) Tasks
 */
class TaskbarRecentAppsController(
    private val recentsModel: RecentsModel,
    // Pass a provider here instead of the actual DesktopVisibilityController instance since that
    // instance might not be available when this constructor is called.
    private val desktopVisibilityControllerProvider: () -> DesktopVisibilityController?,
) : LoggableTaskbarController {

    // TODO(b/335401172): unify DesktopMode checks in Launcher.
    val canShowRunningApps =
        enableDesktopWindowingMode() && enableDesktopWindowingTaskbarRunningApps()

    // TODO(b/343532825): Add a setting to disable Recents even when the flag is on.
    @VisibleForTesting
    var isEnabled = enableRecentsInTaskbar() || canShowRunningApps

    // Initialized in init.
    private lateinit var controllers: TaskbarControllers

    private var apps: Array<AppInfo>? = null
    private var allRunningDesktopAppInfos: List<AppInfo>? = null
    private var allMinimizedDesktopAppInfos: List<AppInfo>? = null

    private val desktopVisibilityController: DesktopVisibilityController?
        get() = desktopVisibilityControllerProvider()

    private val isInDesktopMode: Boolean
        get() = desktopVisibilityController?.areDesktopTasksVisible() ?: false

    val runningApps: Set<String>
        get() {
            if (!isEnabled || !isInDesktopMode) {
                return emptySet()
            }
            return allRunningDesktopAppInfos?.mapNotNull { it.targetPackage }?.toSet() ?: emptySet()
        }

    val minimizedApps: Set<String>
        get() {
            if (!isInDesktopMode) {
                return emptySet()
            }
            return allMinimizedDesktopAppInfos?.mapNotNull { it.targetPackage }?.toSet()
                ?: emptySet()
        }

    fun init(taskbarControllers: TaskbarControllers) {
        controllers = taskbarControllers
    }

    fun onDestroy() {
        apps = null
    }

    /** Stores the current [AppInfo] instances, no-op except in desktop environment. */
    fun setApps(apps: Array<AppInfo>?) {
        this.apps = apps
    }

    /** Called to update hotseatItems, in order to de-dupe them from Recent/Running tasks later. */
    // TODO(next CL): add new section of Tasks instead of changing Hotseat items
    fun updateHotseatItemInfos(hotseatItems: Array<ItemInfo?>): Array<ItemInfo?> {
        if (!isEnabled || !isInDesktopMode) {
            return hotseatItems
        }
        val newHotseatItemInfos =
            hotseatItems
                .filterNotNull()
                // Ignore predicted apps - we show running apps instead
                .filter { itemInfo -> !itemInfo.isPredictedItem }
                .toMutableList()
        val runningDesktopAppInfos =
            allRunningDesktopAppInfos?.let {
                getRunningDesktopAppInfosExceptHotseatApps(it, newHotseatItemInfos.toList())
            }
        if (runningDesktopAppInfos != null) {
            newHotseatItemInfos.addAll(runningDesktopAppInfos)
        }
        return newHotseatItemInfos.toTypedArray()
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

    private fun getDesktopRunningTasks(): List<RunningTaskInfo> =
        recentsModel.runningTasks.filter { taskInfo: RunningTaskInfo ->
            taskInfo.windowingMode == WindowConfiguration.WINDOWING_MODE_FREEFORM
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

    /** Called to update the list of currently running apps, no-op except in desktop environment. */
    fun updateRunningApps() {
        if (!isEnabled || !isInDesktopMode) {
            return controllers.taskbarViewController.commitRunningAppsToUI()
        }
        val runningTasks = getDesktopRunningTasks()
        val runningAppInfo = getAppInfosFromRunningTasks(runningTasks)
        allRunningDesktopAppInfos = runningAppInfo
        updateMinimizedApps(runningTasks, runningAppInfo)
        controllers.taskbarViewController.commitRunningAppsToUI()
    }

    private fun updateMinimizedApps(
        runningTasks: List<RunningTaskInfo>,
        runningAppInfo: List<AppInfo>,
    ) {
        val allRunningAppTasks =
            runningAppInfo
                .mapNotNull { appInfo -> appInfo.targetPackage?.let { appInfo to it } }
                .associate { (appInfo, targetPackage) ->
                    appInfo to
                            runningTasks
                                .filter { it.realActivity?.packageName == targetPackage }
                                .map { it.taskId }
                }
        val minimizedTaskIds = runningTasks.associate { it.taskId to !it.isVisible }
        allMinimizedDesktopAppInfos =
            allRunningAppTasks
                .filterValues { taskIds -> taskIds.all { minimizedTaskIds[it] ?: false } }
                .keys
                .toList()
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println("$prefix TaskbarRecentAppsController:")
        pw.println("$prefix\tisEnabled=$isEnabled")
        pw.println("$prefix\tcanShowRunningApps=$canShowRunningApps")
        // TODO(next CL): add more logs
    }
}
