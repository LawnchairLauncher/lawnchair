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

package com.android.wm.shell.shared.desktopmode

import android.Manifest.permission.SYSTEM_ALERT_WINDOW
import android.app.TaskInfo
import android.content.Context
import android.content.pm.PackageManager
import android.window.DesktopModeFlags
import com.android.internal.R
import com.android.internal.policy.DesktopModeCompatUtils
import java.util.function.Supplier

/**
 * Class to decide whether to apply app compat policies in desktop mode.
 */
// TODO(b/347289970): Consider replacing with API
class DesktopModeCompatPolicy(private val context: Context) {

    private val systemUiPackage: String = context.resources.getString(R.string.config_systemUi)
    private val pkgManager: PackageManager
        get() = context.getPackageManager()
    private val defaultHomePackage: String?
        get() = defaultHomePackageSupplier?.get()
            ?: pkgManager.getHomeActivities(ArrayList())?.packageName
    private val packageInfoCache = mutableMapOf<String, Boolean>()

    var defaultHomePackageSupplier: Supplier<String?>? = null

    /**
     * If the top activity should be exempt from desktop windowing and forced back to fullscreen.
     * Currently includes all system ui, default home and transparent stack activities. However if
     * the top activity is not being displayed, regardless of its configuration, we will not exempt
     * it as to remain in the desktop windowing environment.
     */
    fun isTopActivityExemptFromDesktopWindowing(task: TaskInfo) =
        isTopActivityExemptFromDesktopWindowing(task.baseActivity?.packageName,
            task.numActivities, task.isTopActivityNoDisplay, task.isActivityStackTransparent,
            task.userId)

    fun isTopActivityExemptFromDesktopWindowing(
        packageName: String?,
        numActivities: Int,
        isTopActivityNoDisplay: Boolean,
        isActivityStackTransparent: Boolean,
        userId: Int
    ) =
        DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MODALS_POLICY.isTrue &&
                ((isSystemUiTask(packageName) ||
                        isPartOfDefaultHomePackageOrNoHomeAvailable(packageName) ||
                        (isTransparentTask(isActivityStackTransparent, numActivities) &&
                                hasFullscreenTransparentPermission(packageName, userId))) &&
                        !isTopActivityNoDisplay)

    /** @see DesktopModeCompatUtils.shouldExcludeCaptionFromAppBounds */
    fun shouldExcludeCaptionFromAppBounds(taskInfo: TaskInfo): Boolean =
        taskInfo.topActivityInfo?.let {
            DesktopModeCompatUtils.shouldExcludeCaptionFromAppBounds(it, taskInfo.isResizeable,
                taskInfo.appCompatTaskInfo.hasOptOutEdgeToEdge())
        } ?: false

    /**
     * Returns true if all activities in a tasks stack are transparent. If there are no activities
     * will return false.
     */
    fun isTransparentTask(task: TaskInfo): Boolean =
        isTransparentTask(task.isActivityStackTransparent, task.numActivities)

    private fun isTransparentTask(isActivityStackTransparent: Boolean, numActivities: Int) =
        isActivityStackTransparent && numActivities > 0

    private fun isSystemUiTask(packageName: String?) = packageName == systemUiPackage

    // Checks if the app for the given package has the SYSTEM_ALERT_WINDOW permission.
    private fun hasFullscreenTransparentPermission(packageName: String?, userId: Int): Boolean {
        if (DesktopModeFlags.ENABLE_MODALS_FULLSCREEN_WITH_PERMISSIONS.isTrue) {
            if (packageName == null) {
                return false
            }
            return packageInfoCache.getOrPut("$userId@$packageName") {
                try {
                    val packageInfo = pkgManager.getPackageInfoAsUser(
                        packageName,
                        PackageManager.GET_PERMISSIONS,
                        userId
                    )
                    packageInfo?.requestedPermissions?.contains(SYSTEM_ALERT_WINDOW) == true
                } catch (e: PackageManager.NameNotFoundException) {
                    false // Package not found
                }
            }
        }
        // If the flag is disabled we make this condition neutral.
        return true
    }

    /**
     * Returns true if the tasks base activity is part of the default home package, or there is
     * currently no default home package available.
     */
    private fun isPartOfDefaultHomePackageOrNoHomeAvailable(packageName: String?) =
        defaultHomePackage == null || (packageName != null && packageName == defaultHomePackage)
}
