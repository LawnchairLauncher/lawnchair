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

package com.android.quickstep

import android.view.View
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.popup.SystemShortcut
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskContainer
import com.android.window.flags.Flags
import com.android.wm.shell.shared.desktopmode.DesktopModeCompatPolicy
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus

/** A menu item that allows the user to move the current app into external display. */
class ExternalDisplaySystemShortcut(
    container: RecentsViewContainer,
    abstractFloatingViewHelper: AbstractFloatingViewHelper,
    private val taskContainer: TaskContainer,
) :
    SystemShortcut<RecentsViewContainer>(
        R.drawable.ic_external_display,
        R.string.recent_task_option_external_display,
        container,
        taskContainer.itemInfo,
        taskContainer.taskView,
        abstractFloatingViewHelper,
    ) {
    override fun onClick(view: View) {
        dismissTaskMenuView()
        val recentsView = mTarget.getOverviewPanel<RecentsView<*, *>>()
        recentsView.moveTaskToExternalDisplay(taskContainer) {
            mTarget.statsLogManager
                .logger()
                .withItemInfo(taskContainer.itemInfo)
                .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_EXTERNAL_DISPLAY_TAP)
        }
    }

    companion object {
        @JvmOverloads
        /**
         * Creates a factory for creating move task to external display system shortcuts in
         * [com.android.quickstep.TaskOverlayFactory].
         */
        fun createFactory(
            abstractFloatingViewHelper: AbstractFloatingViewHelper = AbstractFloatingViewHelper()
        ): TaskShortcutFactory =
            object : TaskShortcutFactory {
                override fun getShortcuts(
                    container: RecentsViewContainer,
                    taskContainer: TaskContainer,
                ): List<ExternalDisplaySystemShortcut>? {
                    val context = container.asContext()
                    val taskKey = taskContainer.task.key
                    val desktopModeCompatPolicy = DesktopModeCompatPolicy(context)
                    return when {
                        !DesktopModeStatus.canEnterDesktopMode(context) -> null

                        !Flags.moveToExternalDisplayShortcut() -> null

                        desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
                            taskKey.baseActivity?.packageName,
                            taskKey.numActivities,
                            taskKey.isTopActivityNoDisplay,
                            taskKey.isActivityStackTransparent,
                            taskKey.userId,
                        ) -> null

                        else -> {
                            listOf(
                                ExternalDisplaySystemShortcut(
                                    container,
                                    abstractFloatingViewHelper,
                                    taskContainer,
                                )
                            )
                        }
                    }
                }

                override fun showForGroupedTask() = true
            }
    }
}
