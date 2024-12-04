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
package com.android.wm.shell.common

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.view.WindowManager.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.R
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL
import com.android.wm.shell.util.KtProtoLog
import java.util.Arrays

/**
 * Helper for multi-instance related checks.
 */
class MultiInstanceHelper @JvmOverloads constructor(
    private val context: Context,
    private val packageManager: PackageManager,
    private val staticAppsSupportingMultiInstance: Array<String> = context.resources
            .getStringArray(R.array.config_appsSupportMultiInstancesSplit),
    private val supportsMultiInstanceProperty: Boolean) {

    /**
     * Returns whether a specific component desires to be launched in multiple instances.
     */
    @VisibleForTesting
    fun supportsMultiInstanceSplit(componentName: ComponentName?): Boolean {
        if (componentName == null || componentName.packageName == null) {
            // TODO(b/262864589): Handle empty component case
            return false
        }

        // Check the pre-defined allow list
        val packageName = componentName.packageName
        for (pkg in staticAppsSupportingMultiInstance) {
            if (pkg == packageName) {
                KtProtoLog.v(WM_SHELL, "application=%s in allowlist supports multi-instance",
                    packageName)
                return true
            }
        }

        if (!supportsMultiInstanceProperty) {
            // If not checking the multi-instance properties, then return early
            return false;
        }

        // Check the activity property first
        try {
            val activityProp = packageManager.getProperty(
                PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI, componentName)
            // If the above call doesn't throw a NameNotFoundException, then the activity property
            // should override the application property value
            if (activityProp.isBoolean) {
                KtProtoLog.v(WM_SHELL, "activity=%s supports multi-instance", componentName)
                return activityProp.boolean
            } else {
                KtProtoLog.w(WM_SHELL, "Warning: property=%s for activity=%s has non-bool type=%d",
                    PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI, packageName, activityProp.type)
            }
        } catch (nnfe: PackageManager.NameNotFoundException) {
            // Not specified in the activity, fall through
        }

        // Check the application property otherwise
        try {
            val appProp = packageManager.getProperty(
                PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI, packageName)
            if (appProp.isBoolean) {
                KtProtoLog.v(WM_SHELL, "application=%s supports multi-instance", packageName)
                return appProp.boolean
            } else {
                KtProtoLog.w(WM_SHELL,
                    "Warning: property=%s for application=%s has non-bool type=%d",
                    PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI, packageName, appProp.type)
            }
        } catch (nnfe: PackageManager.NameNotFoundException) {
            // Not specified in either application or activity
        }
        return false
    }

    companion object {
        /** Returns the component from a PendingIntent  */
        @JvmStatic
        fun getComponent(pendingIntent: PendingIntent?): ComponentName? {
            return pendingIntent?.intent?.component
        }

        /** Returns the component from a shortcut  */
        @JvmStatic
        fun getShortcutComponent(packageName: String, shortcutId: String,
                user: UserHandle, launcherApps: LauncherApps): ComponentName? {
            val query = LauncherApps.ShortcutQuery()
            query.setPackage(packageName)
            query.setShortcutIds(Arrays.asList(shortcutId))
            query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_ALL_KINDS_WITH_ALL_PINNED)
            val shortcuts = launcherApps.getShortcuts(query, user)
            val info = if (shortcuts != null && shortcuts.size > 0) shortcuts[0] else null
            return info?.activity
        }

        /** Returns true if package names and user ids match.  */
        @JvmStatic
        fun samePackage(packageName1: String?, packageName2: String?,
                userId1: Int, userId2: Int): Boolean {
            return (packageName1 != null && packageName1 == packageName2) && (userId1 == userId2)
        }
    }
}
