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

import android.content.ComponentName
import android.os.UserHandle
import android.text.TextUtils
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.icons.cache.BaseIconCache
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.ItemInfoMatcher

/** Model task run when there is a package install session failure */
class SessionFailureTask(val packageName: String, val user: UserHandle) : ModelUpdateTask {

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        val iconCache = taskController.iconCache
        val isAppArchived =
            ApplicationInfoWrapper(taskController.context, packageName, user).isArchived()
        synchronized(dataModel) {
            if (isAppArchived) {
                val updatedItems = mutableListOf<WorkspaceItemInfo>()
                // Remove package icon cache entry for archived app in case of a session
                // failure.
                iconCache.remove(
                    ComponentName(packageName, packageName + BaseIconCache.EMPTY_CLASS_NAME),
                    user,
                )
                for (info in dataModel.itemsIdMap) {
                    if (info is WorkspaceItemInfo && info.isArchived && user == info.user) {
                        // Refresh icons on the workspace for archived apps.
                        iconCache.getTitleAndIcon(info, info.matchingLookupFlag)
                        updatedItems.add(info)
                    }
                }

                if (updatedItems.isNotEmpty()) {
                    taskController.bindUpdatedWorkspaceItems(updatedItems)
                }
                apps.updateIconsAndLabels(hashSetOf(packageName), user)
                taskController.bindApplicationsIfNeeded()
            } else {
                val removedItems =
                    dataModel.itemsIdMap.filter { info ->
                        (info is WorkspaceItemInfo && info.hasPromiseIconUi()) &&
                            user == info.user &&
                            TextUtils.equals(packageName, info.intent.getPackage())
                    }
                if (removedItems.isNotEmpty()) {
                    taskController.deleteAndBindComponentsRemoved(
                        ItemInfoMatcher.ofItems(removedItems),
                        "removed because install session failed",
                    )
                }
            }
        }
    }
}
