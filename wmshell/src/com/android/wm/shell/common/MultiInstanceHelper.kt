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

import android.annotation.UserIdInt
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.PackageManager.Property
import android.os.UserHandle
import android.view.WindowManager.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.R
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import java.io.PrintWriter
import java.util.Arrays

/**
 * Helper for multi-instance related checks.
 */
class MultiInstanceHelper @JvmOverloads constructor(
    private val context: Context,
    private val packageManager: PackageManager,
    private val staticAppsSupportingMultiInstance: Array<String> = context.resources
            .getStringArray(R.array.config_appsSupportMultiInstancesSplit),
    shellInit: ShellInit,
    private val shellCommandHandler: ShellCommandHandler,
    private val supportsMultiInstanceProperty: Boolean
) : ShellCommandHandler.ShellCommandActionHandler {

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        shellCommandHandler.addCommandCallback("multi-instance", this, this)
    }

    /**
     * Returns whether a specific component desires to be launched in multiple instances.
     */
    fun supportsMultiInstanceSplit(componentName: ComponentName?, @UserIdInt userId: Int): Boolean {
        if (componentName == null || componentName.packageName == null) {
            // TODO(b/262864589): Handle empty component case
            return false
        }

        // Check the pre-defined allow list
        val packageName = componentName.packageName
        for (pkg in staticAppsSupportingMultiInstance) {
            if (pkg == packageName) {
                ProtoLog.v(WM_SHELL, "application=%s in allowlist supports multi-instance",
                    packageName)
                return true
            }
        }

        if (!supportsMultiInstanceProperty) {
            // If not checking the multi-instance properties, then return early
            return false
        }

        // Check the activity property first
        try {
            val activityProp = packageManager.getPropertyAsUser(
                PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI, componentName.packageName,
                componentName.className, userId)
            // If the above call doesn't throw a NameNotFoundException, then the activity property
            // should override the application property value
            if (activityProp.isBoolean) {
                ProtoLog.v(WM_SHELL, "activity=%s supports multi-instance", componentName)
                return activityProp.boolean
            } else {
                ProtoLog.w(WM_SHELL, "Warning: property=%s for activity=%s has non-bool type=%d",
                    PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI, packageName, activityProp.type)
            }
        } catch (nnfe: PackageManager.NameNotFoundException) {
            // Not specified in the activity, fall through
        }

        // Check the application property otherwise
        try {
            val appProp = packageManager.getPropertyAsUser(
                PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI, packageName, null /* className */,
                userId)
            if (appProp.isBoolean) {
                ProtoLog.v(WM_SHELL, "application=%s supports multi-instance", packageName)
                return appProp.boolean
            } else {
                ProtoLog.w(WM_SHELL,
                    "Warning: property=%s for application=%s has non-bool type=%d",
                    PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI, packageName, appProp.type)
            }
        } catch (nnfe: PackageManager.NameNotFoundException) {
            // Not specified in either application or activity
        }
        return false
    }

    override fun onShellCommand(args: Array<out String>?, pw: PrintWriter?): Boolean {
        if (pw == null || args == null || args.isEmpty()) {
            return false
        }
        when (args[0]) {
            "list" -> return dumpSupportedApps(pw)
        }
        return false
    }

    override fun printShellCommandHelp(pw: PrintWriter, prefix: String) {
        pw.println("${prefix}list")
        pw.println("$prefix   Lists all the packages that support the multiinstance property")
    }

    /**
     * Dumps the static allowlist and list of apps that have the declared property in the manifest.
     */
    private fun dumpSupportedApps(pw: PrintWriter): Boolean {
        pw.println("Static allow list (for all users):")
        staticAppsSupportingMultiInstance.forEach { pkg ->
            pw.println("  $pkg")
        }

        // TODO(b/391693747): Dump this per-user once PM allows us to query properties
        //                    for non-calling users
        val apps = packageManager.queryApplicationProperty(
            PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI)
        val activities = packageManager.queryActivityProperty(
            PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI)
        val appsWithProperty = (apps + activities)
            .sortedWith(object : Comparator<Property?> {
                override fun compare(o1: Property?, o2: Property?): Int {
                    if (o1?.packageName != o2?.packageName) {
                        return o1?.packageName!!.compareTo(o2?.packageName!!)
                    } else {
                        if (o1?.className != null) {
                            return o1.className!!.compareTo(o2?.className!!)
                        } else if (o2?.className != null) {
                            return -o2.className!!.compareTo(o1?.className!!)
                        }
                        return 0
                    }
                }
            })
        if (appsWithProperty.isNotEmpty()) {
            pw.println("Apps (User ${context.userId}):")
            appsWithProperty.forEach { prop ->
                if (prop.isBoolean && prop.boolean) {
                    if (prop.className != null) {
                        pw.println("  ${prop.packageName}/${prop.className}")
                    } else {
                        pw.println("  ${prop.packageName}")
                    }
                }
            }
        }
        return true
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
