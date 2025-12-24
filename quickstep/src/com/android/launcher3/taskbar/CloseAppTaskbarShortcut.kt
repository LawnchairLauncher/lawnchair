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

package com.android.launcher3.taskbar

import android.content.Context
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.RecentsModel
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.system.ActivityManagerWrapper

/** A single menu item shortcut to close a task in Desktop Mode. */
class CloseAppTaskbarShortcut<T>(
    target: T,
    private val itemInfo: ItemInfo?,
    originalView: View?,
    controllers: TaskbarControllers,
) :
    SystemShortcut<T>(
        R.drawable.ic_gm_close_24,
        R.string.close_app_taskbar,
        target,
        itemInfo,
        originalView,
    ) where T : Context?, T : ActivityContext? {
    private val recentsModel = RecentsModel.INSTANCE[controllers.taskbarActivityContext]
    private val activityManagerWrapper = ActivityManagerWrapper.getInstance()

    override fun onClick(v: View?) {
        findAndCloseAppInstances(itemInfo)
        AbstractFloatingView.closeAllOpenViews(mTarget)
    }

    /**
     * Finds and closes all instances of the application specified by [itemInfo] by matching package
     * and user ID to recent tasks.
     */
    private fun findAndCloseAppInstances(itemInfo: ItemInfo?) {
        itemInfo ?: return
        val targetPackage = itemInfo.targetPackage
        val targetUserId = itemInfo.user.identifier
        val isTargetPackageTask: (Task) -> Boolean = { task ->
            task.key?.packageName == targetPackage && task.key?.userId == targetUserId
        }
        recentsModel.getTasks { tasks ->
            val allUniqueTasks = tasks.flatMap { it.tasks }.distinctBy { it.key.id }
            val taskIdsToClose =
                allUniqueTasks.filter(isTargetPackageTask).mapNotNull { it.key?.id }

            // Close all instances of this app (applicable to multi instance scenarios)
            for (taskId in taskIdsToClose) {
                activityManagerWrapper.removeTask(taskId)
            }
        }
    }
}
