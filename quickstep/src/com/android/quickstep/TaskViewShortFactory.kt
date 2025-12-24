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

package com.android.quickstep

import android.view.View
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.views.DesktopTaskView
import com.android.quickstep.views.GroupedTaskView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.RecentsViewContainer.containerFromContext
import com.android.quickstep.views.TaskView

/**
 * Represents a system shortcut that can be shown for a [TaskView]. Appears as a single entry in the
 * dropdown menu that shows up when you tap the app chip in Overview.
 */
interface TaskViewShortFactory {

    fun getShortcuts(
        container: RecentsViewContainer,
        taskView: TaskView,
    ): List<SystemShortcut<ActivityContext>>

    fun showForGroupedTask() = false

    fun showForDesktopTask() = false

    class RemoveTaskSystemShortcut(
        iconResId: Int,
        textResId: Int,
        container: RecentsViewContainer,
        private val taskView: TaskView,
    ) :
        SystemShortcut<ActivityContext>(
            iconResId,
            textResId,
            container,
            taskView.itemInfo,
            taskView,
        ) {
        override fun onClick(view: View) {
            val recentsView = taskView.recentsView ?: return
            dismissTaskMenuView()
            recentsView.dismissTaskView(taskView, true, true)
            mTarget.statsLogManager
                .logger()
                .withItemInfo(taskView.itemInfo)
                .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_CLOSE_APP_TAP)
        }
    }

    companion object {
        /** Returns menu options associated with TaskView. */
        fun getEnabledShortcuts(taskView: TaskView) =
            TASK_VIEW_MENU_OPTIONS.filter {
                    taskView !is GroupedTaskView || it.showForGroupedTask()
                }
                .filter { taskView !is DesktopTaskView || it.showForDesktopTask() }
                .flatMap { it.getShortcuts(containerFromContext(taskView.context), taskView) }

        private val REMOVE_TASK: TaskViewShortFactory =
            object : TaskViewShortFactory {
                override fun getShortcuts(
                    container: RecentsViewContainer,
                    taskView: TaskView,
                ): List<SystemShortcut<ActivityContext>> {
                    val recentsView = taskView.recentsView ?: return emptyList()
                    if (!recentsView.canRemoveTaskView(taskView)) {
                        return emptyList()
                    }
                    return listOf<SystemShortcut<ActivityContext>>(
                        RemoveTaskSystemShortcut(
                            R.drawable.ic_remove_task_option,
                            R.string.recent_task_option_remove_task,
                            container,
                            taskView,
                        )
                    )
                }

                override fun showForGroupedTask() = true

                override fun showForDesktopTask() = true
            }

        private val TASK_VIEW_MENU_OPTIONS: Array<TaskViewShortFactory> = arrayOf(REMOVE_TASK)
    }
}
