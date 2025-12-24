/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.model.tasks

import android.os.UserHandle
import android.util.Log
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.ItemInfoMatcher

/** Factory for creating model tasks to handle various package events */
object PackageTaskFactory {
    private const val TAG = "PackageUpdateTaskFactory"
    private const val DEBUG = false

    /** Task to handle apps being removed */
    fun appsRemoved(user: UserHandle, packages: Set<String>) =
        ModelUpdateTask { taskController, dataModel, apps ->
            packages.forEach {
                if (DEBUG) Log.i(TAG, "appsRemoved package=$it")
                taskController.iconCache.removeIconsForPkg(it, user)
                apps.removePackage(it, user)
            }
            taskController.bindApplicationsIfNeeded()

            val matcher = ItemInfoMatcher.ofPackages(packages, user)
            // Shortcuts to keep even if the corresponding app was removed
            val forceKeepShortcuts =
                synchronized(dataModel) {
                    dataModel.itemsIdMap.filter {
                        matcher.test(it) &&
                            it is WorkspaceItemInfo &&
                            it.hasStatusFlag(FLAG_SUPPORTS_WEB_UI)
                    }
                }

            val removeMatch = matcher.and(ItemInfoMatcher.ofItems(forceKeepShortcuts).negate())
            taskController.deleteAndBindComponentsRemoved(
                removeMatch,
                "removed because the corresponding package is removed. removedPackages=$packages",
            )
            // Remove any queued items from the install queue
            ItemInstallQueue.INSTANCE[taskController.context].removeFromInstallQueue(packages, user)
        }

    /** Task to handle apps being temporarily unavailable */
    fun appsUnavailable(user: UserHandle, packages: Set<String>) =
        ModelUpdateTask { taskController, dataModel, apps ->
            packages.forEach {
                if (DEBUG) Log.i(TAG, "appsUnavailable package=$it")
                apps.removePackage(it, user)
            }
            taskController.bindApplicationsIfNeeded()
            updateRuntimeStatus(
                taskController,
                dataModel,
                user,
                packages,
                FlagOp.NO_OP.addFlag(FLAG_DISABLED_NOT_AVAILABLE),
            )
        }

    /** Task to handle apps being suspended */
    fun appsSuspended(user: UserHandle, packages: Set<String>) =
        ModelUpdateTask { taskController, dataModel, apps ->
            val flagOp = FlagOp.NO_OP.addFlag(FLAG_DISABLED_SUSPENDED)

            apps.updateDisabledFlags(ItemInfoMatcher.ofPackages(packages, user), flagOp)
            taskController.bindApplicationsIfNeeded()

            updateRuntimeStatus(taskController, dataModel, user, packages, flagOp)
        }

    /** Task to handle apps being suspended */
    fun appsUnsuspended(user: UserHandle, packages: Set<String>) =
        ModelUpdateTask { taskController, dataModel, apps ->
            val flagOp = FlagOp.NO_OP.removeFlag(FLAG_DISABLED_SUSPENDED)

            apps.updateDisabledFlags(ItemInfoMatcher.ofPackages(packages, user), flagOp)
            taskController.bindApplicationsIfNeeded()

            updateRuntimeStatus(taskController, dataModel, user, packages, flagOp)
        }

    /** Updates matching workspace items with the provided [updateOp] */
    private fun updateRuntimeStatus(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        user: UserHandle,
        packages: Set<String>,
        updateOp: FlagOp,
    ) {

        // Update workspace items, widget suspension is handled by the platform itself
        val updatedItems =
            dataModel.updateAndCollectWorkspaceItemInfos(
                user,
                { itemInfo ->
                    val oldRuntimeFlags = itemInfo.runtimeStatusFlags
                    if (packages.contains(itemInfo.targetPackage))
                        itemInfo.runtimeStatusFlags = updateOp.apply(itemInfo.runtimeStatusFlags)
                    itemInfo.runtimeStatusFlags != oldRuntimeFlags
                },
            )

        taskController.bindUpdatedWorkspaceItems(updatedItems)
    }
}
