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
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer

/** A menu item, "Desktop", that allows the user to bring the current app into Desktop Windowing. */
class DesktopSystemShortcut(
    container: RecentsViewContainer,
    private val mTaskContainer: TaskIdAttributeContainer,
    abstractFloatingViewHelper: AbstractFloatingViewHelper
) :
    SystemShortcut<RecentsViewContainer>(
        R.drawable.ic_caption_desktop_button_foreground,
        R.string.recent_task_option_desktop,
        container,
        mTaskContainer.itemInfo,
        mTaskContainer.taskView,
        abstractFloatingViewHelper
    ) {
    override fun onClick(view: View) {
        dismissTaskMenuView()
        val recentsView = mTarget!!.getOverviewPanel<RecentsView<*, *>>()
        recentsView.moveTaskToDesktop(mTaskContainer) {
            mTarget.statsLogManager
                .logger()
                .withItemInfo(mTaskContainer.itemInfo)
                .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DESKTOP_TAP)
        }
    }

    companion object {
        /** Creates a factory for creating Desktop system shortcuts. */
        @JvmOverloads
        fun createFactory(
            abstractFloatingViewHelper: AbstractFloatingViewHelper = AbstractFloatingViewHelper()
        ): TaskShortcutFactory {
            return object : TaskShortcutFactory {
                override fun getShortcuts(
                    container: RecentsViewContainer,
                    taskContainer: TaskIdAttributeContainer
                ): List<DesktopSystemShortcut>? {
                    return if (!DesktopModeStatus.canEnterDesktopMode(container.asContext())) null
                    else if (!taskContainer.task.isDockable) null
                    else
                        listOf(
                            DesktopSystemShortcut(
                                container,
                                taskContainer,
                                abstractFloatingViewHelper
                            )
                        )
                }

                override fun showForSplitscreen() = true
            }
        }
    }
}
