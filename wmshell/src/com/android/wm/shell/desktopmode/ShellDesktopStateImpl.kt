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
package com.android.wm.shell.desktopmode

import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.transition.FocusTransitionObserver

class ShellDesktopStateImpl(
    private val desktopState: DesktopState,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val shellController: ShellController,
    private val shellTaskOrganizer: ShellTaskOrganizer,
) : ShellDesktopState, DesktopState by desktopState {
    /** Checks if the given display has an active desktop session (i.e., running freeform tasks). */
    private fun isInDesktop(displayId: Int): Boolean =
        desktopUserRepositories
            .getProfile(shellController.currentUserId)
            .getActiveDeskId(displayId) != null

    /** Checks if the currently focused task on the given display is the home screen. */
    private fun isHomeFocused(displayId: Int): Boolean {
        val focusedTask =
            shellTaskOrganizer.getRunningTaskInfo(
                focusTransitionObserver.getFocusedTaskIdOnDisplay(displayId)
            )
        if (focusedTask == null) {
            // A null focused task can occur if the Home activity launched before Shell was
            // fully initialized, and this display has not yet received focus. In this case,
            // we assume that Home is the focused activity on the display.
            return true
        }
        return focusedTask.activityType == ACTIVITY_TYPE_HOME
    }

    /**
     * Determines if a display with [displayId] is an eligible drop target for a window in the
     * context of desktop mode.
     *
     * A display is considered an eligible target if either:
     * 1. It already has an active desktop session.
     * 2. It supports desktop mode and the home screen is currently focused.
     */
    override fun isEligibleWindowDropTarget(displayId: Int): Boolean =
        isInDesktop(displayId) ||
            (desktopState.isDesktopModeSupportedOnDisplay(displayId) && isHomeFocused(displayId))
}
