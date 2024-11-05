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

import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.CallbackTask
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.model.BgDataModel.FixedContainerItems
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.PackageUserKey
import java.util.Objects
import java.util.concurrent.Executor
import java.util.function.Predicate

/** Class with utility methods and properties for running a LauncherModel Task */
class ModelTaskController(
    val app: LauncherAppState,
    val dataModel: BgDataModel,
    val allAppsList: AllAppsList,
    private val model: LauncherModel,
    private val uiExecutor: Executor
) {

    /** Schedules a {@param task} to be executed on the current callbacks. */
    fun scheduleCallbackTask(task: CallbackTask) {
        for (cb in model.callbacks) {
            uiExecutor.execute { task.execute(cb) }
        }
    }

    /**
     * Updates from model task, do not deal with icon position in hotseat. Also no need to verify
     * changes as the ModelTasks always push the changes to callbacks
     */
    fun getModelWriter() = model.getWriter(false /* verifyChanges */, CellPosMapper.DEFAULT, null)

    fun bindUpdatedWorkspaceItems(allUpdates: List<WorkspaceItemInfo>) {
        // Bind workspace items
        val workspaceUpdates =
            allUpdates.stream().filter { info -> info.id != ItemInfo.NO_ID }.toList()
        if (workspaceUpdates.isNotEmpty()) {
            scheduleCallbackTask { it.bindWorkspaceItemsChanged(workspaceUpdates) }
        }

        // Bind extra items if any
        allUpdates
            .stream()
            .mapToInt { info: WorkspaceItemInfo -> info.container }
            .distinct()
            .mapToObj { dataModel.extraItems.get(it) }
            .filter { Objects.nonNull(it) }
            .forEach { bindExtraContainerItems(it) }
    }

    fun bindExtraContainerItems(item: FixedContainerItems) {
        scheduleCallbackTask { it.bindExtraContainerItems(item) }
    }

    fun bindDeepShortcuts(dataModel: BgDataModel) {
        val shortcutMapCopy = HashMap(dataModel.deepShortcutMap)
        scheduleCallbackTask { it.bindDeepShortcutMap(shortcutMapCopy) }
    }

    fun bindUpdatedWidgets(dataModel: BgDataModel) {
        val widgets = dataModel.widgetsModel.getWidgetsListForPicker(app.context)
        scheduleCallbackTask { it.bindAllWidgets(widgets) }
    }

    fun deleteAndBindComponentsRemoved(matcher: Predicate<ItemInfo?>, reason: String?) {
        getModelWriter().deleteItemsFromDatabase(matcher, reason)

        // Call the components-removed callback
        scheduleCallbackTask { it.bindWorkspaceComponentsRemoved(matcher) }
    }

    fun bindApplicationsIfNeeded() {
        if (allAppsList.getAndResetChangeFlag()) {
            val apps = allAppsList.copyData()
            val flags = allAppsList.flags
            val packageUserKeyToUidMap =
                apps.associateBy(
                    keySelector = { PackageUserKey(it.componentName!!.packageName, it.user) },
                    valueTransform = { it.uid }
                )
            scheduleCallbackTask { it.bindAllApplications(apps, flags, packageUserKeyToUidMap) }
        }
    }
}
