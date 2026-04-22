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
package com.android.launcher3.model

import android.content.pm.ShortcutInfo
import android.os.UserHandle
import com.android.launcher3.Flags
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.icons.CacheableShortcutInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.ItemInfoMatcher

/** Handles changes due to shortcut manager updates (deep shortcut changes) */
class ShortcutsChangedTask(
    private val packageName: String,
    private val shortcuts: List<ShortcutInfo>,
    private val user: UserHandle,
    private val shouldUpdateIdMap: Boolean,
) : ModelUpdateTask {

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        val context = taskController.context
        val itemFilter: (WorkspaceItemInfo) -> Boolean = {
            it.itemType == ITEM_TYPE_DEEP_SHORTCUT && packageName == it.targetPackage
        }

        // Find WorkspaceItemInfo's that have changed on the workspace.
        val matchingShortcutIds = mutableSetOf<String>()
        synchronized(dataModel) {
            dataModel.updateAndCollectWorkspaceItemInfos(
                user,
                {
                    if (itemFilter.invoke(it)) matchingShortcutIds.add(it.deepShortcutId)

                    // We don't care about the returned list
                    false
                },
            )
        }

        if (matchingShortcutIds.isNotEmpty()) {
            val infoWrapper = ApplicationInfoWrapper(context, packageName, user)
            if (shortcuts.isEmpty()) {
                // Verify that the app is indeed installed.
                if (
                    (!infoWrapper.isInstalled() && !infoWrapper.isArchived()) ||
                        (Flags.restoreArchivedShortcuts() && infoWrapper.isArchived())
                ) {
                    // App is not installed or is archived, ignoring package events
                    return
                }
            }
            // Update the workspace to reflect the changes to updated shortcuts residing on it.
            val pinnedShortcuts: Map<String, ShortcutInfo> =
                ShortcutRequest(context, user)
                    .forPackage(packageName, matchingShortcutIds.filterNotNullTo(mutableListOf()))
                    .query(ShortcutRequest.ALL)
                    .associateBy { it.id }
            val nonPinnedIds = matchingShortcutIds.toMutableSet()
            val updatedWorkspaceItemInfos: List<ItemInfo>
            synchronized(dataModel) {
                updatedWorkspaceItemInfos =
                    dataModel.updateAndCollectWorkspaceItemInfos(
                        user,
                        {
                            if (!itemFilter.invoke(it))
                                return@updateAndCollectWorkspaceItemInfos false
                            val shortcutId =
                                it.deepShortcutId ?: return@updateAndCollectWorkspaceItemInfos false
                            val fullDetails =
                                pinnedShortcuts[shortcutId]
                                    ?: return@updateAndCollectWorkspaceItemInfos false

                            if (!fullDetails.isPinned && !Flags.restoreArchivedShortcuts())
                                return@updateAndCollectWorkspaceItemInfos false

                            nonPinnedIds.remove(shortcutId)
                            it.updateFromDeepShortcutInfo(fullDetails, context)
                            taskController.iconCache.getShortcutIcon(
                                it,
                                CacheableShortcutInfo(fullDetails, infoWrapper),
                            )
                            true
                        },
                    )
            }

            taskController.bindUpdatedWorkspaceItems(updatedWorkspaceItemInfos)
            if (nonPinnedIds.isNotEmpty()) {
                taskController.deleteAndBindComponentsRemoved(
                    ItemInfoMatcher.ofShortcutKeys(
                        nonPinnedIds.mapTo(mutableSetOf()) { ShortcutKey(packageName, user, it) }
                    ),
                    "removed because the shortcut is no longer available in shortcut service",
                )
            }
        }

        if (shouldUpdateIdMap) {
            // Update the deep shortcut map if the list of ids has changed for an activity.
            dataModel.updateDeepShortcutCounts(packageName, user, shortcuts)
            taskController.bindDeepShortcuts(dataModel)
        }
    }
}
