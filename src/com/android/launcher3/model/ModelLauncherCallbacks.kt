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

package com.android.launcher3.model

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.text.TextUtils
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.PackageUpdatedTask.OP_ADD
import com.android.launcher3.model.PackageUpdatedTask.OP_REMOVE
import com.android.launcher3.model.PackageUpdatedTask.OP_SUSPEND
import com.android.launcher3.model.PackageUpdatedTask.OP_UNAVAILABLE
import com.android.launcher3.model.PackageUpdatedTask.OP_UNSUSPEND
import com.android.launcher3.model.PackageUpdatedTask.OP_UPDATE
import java.util.function.Consumer

/**
 * Implementation of {@link LauncherApps#Callbacks} which converts various events to corresponding
 * model tasks
 */
class ModelLauncherCallbacks(private var taskExecutor: Consumer<ModelUpdateTask>) :
    LauncherApps.Callback() {

    override fun onPackageAdded(packageName: String, user: UserHandle) {
        taskExecutor.accept(PackageUpdatedTask(OP_ADD, user, packageName))
    }

    override fun onPackageChanged(packageName: String, user: UserHandle) {
        taskExecutor.accept(PackageUpdatedTask(OP_UPDATE, user, packageName))
    }

    override fun onPackageLoadingProgressChanged(
        packageName: String,
        user: UserHandle,
        progress: Float
    ) {
        taskExecutor.accept(PackageIncrementalDownloadUpdatedTask(packageName, user, progress))
    }

    override fun onPackageRemoved(packageName: String, user: UserHandle) {
        FileLog.d(TAG, "package removed received $packageName")
        taskExecutor.accept(PackageUpdatedTask(OP_REMOVE, user, packageName))
    }

    override fun onPackagesAvailable(
        vararg packageNames: String,
        user: UserHandle,
        replacing: Boolean
    ) {
        taskExecutor.accept(PackageUpdatedTask(OP_UPDATE, user, *packageNames))
    }

    override fun onPackagesSuspended(vararg packageNames: String, user: UserHandle) {
        taskExecutor.accept(PackageUpdatedTask(OP_SUSPEND, user, *packageNames))
    }

    override fun onPackagesUnavailable(
        packageNames: Array<String>,
        user: UserHandle,
        replacing: Boolean
    ) {
        if (!replacing) {
            taskExecutor.accept(PackageUpdatedTask(OP_UNAVAILABLE, user, *packageNames))
        }
    }

    override fun onPackagesUnsuspended(vararg packageNames: String, user: UserHandle) {
        taskExecutor.accept(PackageUpdatedTask(OP_UNSUSPEND, user, *packageNames))
    }

    override fun onShortcutsChanged(
        packageName: String,
        shortcuts: MutableList<ShortcutInfo>,
        user: UserHandle
    ) {
        taskExecutor.accept(ShortcutsChangedTask(packageName, shortcuts, user, true))
    }

    fun onPackagesRemoved(user: UserHandle, packages: List<String>) {
        FileLog.d(TAG, "package removed received " + TextUtils.join(",", packages))
        taskExecutor.accept(PackageUpdatedTask(OP_REMOVE, user, *packages.toTypedArray()))
    }

    companion object {
        private const val TAG = "LauncherAppsCallbackImpl"
    }
}
