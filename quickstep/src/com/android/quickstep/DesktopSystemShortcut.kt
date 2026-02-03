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

import android.view.Display
import android.view.View
import com.android.internal.jank.Cuj
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.popup.SystemShortcut
import com.android.quickstep.fallback.window.RecentsWindowFlags.enableDesktopMenuOnSecondaryDisplay
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskContainer
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import com.android.wm.shell.shared.desktopmode.DesktopModeCompatPolicy
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource

/** A menu item, "Desktop", that allows the user to bring the current app into Desktop Windowing. */
class DesktopSystemShortcut(
    container: RecentsViewContainer,
    private val taskContainer: TaskContainer,
    abstractFloatingViewHelper: AbstractFloatingViewHelper,
) :
    SystemShortcut<RecentsViewContainer>(
        R.drawable.ic_desktop,
        R.string.recent_task_option_desktop,
        container,
        taskContainer.itemInfo,
        taskContainer.taskView,
        abstractFloatingViewHelper,
    ) {
    override fun onClick(view: View) {
        InteractionJankMonitorWrapper.begin(view, Cuj.CUJ_DESKTOP_MODE_ENTER_FROM_OVERVIEW_MENU)
        dismissTaskMenuView()
        val recentsView = mTarget.getOverviewPanel<RecentsView<*, *>>()
        recentsView.moveTaskToDesktop(
            taskContainer,
            DesktopModeTransitionSource.APP_FROM_OVERVIEW,
        ) {
            InteractionJankMonitorWrapper.end(Cuj.CUJ_DESKTOP_MODE_ENTER_FROM_OVERVIEW_MENU)
            mTarget.statsLogManager
                .logger()
                .withItemInfo(taskContainer.itemInfo)
                .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DESKTOP_TAP)
        }
    }

    companion object {
        /** Creates a factory for creating Desktop system shortcuts. */
        @JvmOverloads
        fun createFactory(
            abstractFloatingViewHelper: AbstractFloatingViewHelper = AbstractFloatingViewHelper()
        ): TaskShortcutFactory =
            object : TaskShortcutFactory {

                override fun getShortcuts(
                    container: RecentsViewContainer,
                    taskContainer: TaskContainer,
                ): List<DesktopSystemShortcut>? {
                    val context = container.asContext()
                    val taskKey = taskContainer.task.key
                    val desktopModeCompatPolicy = DesktopModeCompatPolicy(context)
                    val isShortcutSupported =
                        enableDesktopMenuOnSecondaryDisplay ||
                            context.displayId == Display.DEFAULT_DISPLAY

                    return when {
                        !isShortcutSupported -> null

                        !DesktopModeStatus.isDesktopModeSupportedOnDisplay(
                            context,
                            context.display,
                        ) -> null

                        desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(
                            taskKey.baseActivity?.packageName,
                            taskKey.numActivities,
                            taskKey.isTopActivityNoDisplay,
                            taskKey.isActivityStackTransparent,
                        ) -> null

                        !taskContainer.task.isDockable -> null

                        else ->
                            listOf(
                                DesktopSystemShortcut(
                                    container,
                                    taskContainer,
                                    abstractFloatingViewHelper,
                                )
                            )
                    }
                }

                override fun showForGroupedTask() = true
            }
    }
}
