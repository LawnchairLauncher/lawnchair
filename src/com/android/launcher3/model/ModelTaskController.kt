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

import android.content.Context
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.CallbackTask
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.BgDataModel.FixedContainerItems
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.widget.model.WidgetsListBaseEntriesBuilder
import java.util.function.Predicate
import java.util.stream.Collectors
import javax.inject.Inject

/** Class with utility methods and properties for running a LauncherModel Task */
class ModelTaskController
@Inject
constructor(
    @ApplicationContext val context: Context,
    val iconCache: IconCache,
    val dataModel: BgDataModel,
    val allAppsList: AllAppsList,
    val model: LauncherModel,
) {

    private val uiExecutor = MAIN_EXECUTOR

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

    fun bindUpdatedWorkspaceItems(allUpdates: Collection<ItemInfo>) {
        // Bind workspace items
        val workspaceUpdates = allUpdates.filter { it.id != ItemInfo.NO_ID }.toSet()
        if (workspaceUpdates.isNotEmpty()) {
            scheduleCallbackTask { it.bindItemsUpdated(workspaceUpdates) }
        }
        dataModel.updateItems(allUpdates.toList(), null)
    }

    fun bindExtraContainerItems(item: FixedContainerItems) {
        scheduleCallbackTask { it.bindExtraContainerItems(item) }
    }

    fun bindDeepShortcuts(dataModel: BgDataModel) {
        val shortcutMapCopy = HashMap(dataModel.deepShortcutMap)
        scheduleCallbackTask { it.bindDeepShortcutMap(shortcutMapCopy) }
    }

    fun bindUpdatedWidgets(dataModel: BgDataModel) {
        val allWidgets =
            WidgetsListBaseEntriesBuilder(context)
                .build(dataModel.widgetsModel.widgetsByPackageItemForPicker)
        scheduleCallbackTask { it.bindAllWidgets(allWidgets) }
    }

    fun deleteAndBindComponentsRemoved(matcher: Predicate<ItemInfo?>, reason: String?) {
        getModelWriter().deleteItemsFromDatabase(matcher, reason)

        // Call the components-removed callback
        scheduleCallbackTask { it.bindWorkspaceComponentsRemoved(matcher) }
    }

    fun bindApplicationsIfNeeded() {
        if (allAppsList.getAndResetChangeFlag()) {
            // shallow copy
            val data = allAppsList.immutableData
            scheduleCallbackTask {
                it.bindAllApplications(data.apps, data.flags, data.packageUserKeyToUidMap)
            }
        }
    }

    fun bindIncrementalUpdates(updatedAppInfos: List<AppInfo>) {
        if (updatedAppInfos.isNotEmpty()) {
            updatedAppInfos.forEach { info ->
                scheduleCallbackTask { it.bindIncrementalDownloadProgressUpdated(info) }
            }
        }
    }
}
