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

package com.android.wm.shell.crashhandling

import android.app.WindowConfiguration
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.wm.shell.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.util.BubbleUtils
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.NoOpTransitionHandler
import com.android.wm.shell.transition.Transitions
import java.util.Optional

/**
 * [ShellCrashHandler] for shell to use when it's being initialized. Currently it only restores the
 * home task to top.
 */
class ShellCrashHandler(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val transitions: Transitions,
    private val homeIntentProvider: HomeIntentProvider,
    private val desktopState: DesktopState,
    private val bubbleController: Optional<BubbleController>,
    shellInit: ShellInit,
) {
    init {
        shellInit.addInitCallback(::onInit, this)
    }

    private fun onInit() {
        handleCrashIfNeeded()
    }

    private fun handleCrashIfNeeded() {
        // For now only handle crashes when desktop mode is enabled on the device.
        if (
            desktopState.canEnterDesktopMode &&
                !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            var freeformTaskExists = false
            // If there are running tasks at init, WMShell has crashed but WMCore is still alive.
            for (task in shellTaskOrganizer.getRunningTasks()) {
                if (task.windowingMode == WindowConfiguration.WINDOWING_MODE_FREEFORM) {
                    freeformTaskExists = true
                }

                if (freeformTaskExists) {
                    shellTaskOrganizer.applyTransaction(
                        addLaunchHomePendingIntent(WindowContainerTransaction(), DEFAULT_DISPLAY)
                    )
                    break
                }
            }
        }

        if (Flags.enableShellRestartBubbleCleanup()) {
            bubbleController.ifPresent { handleBubbleTaskCleanup(it) }
        }
    }

    private fun addLaunchHomePendingIntent(
        wct: WindowContainerTransaction,
        displayId: Int,
    ): WindowContainerTransaction {
        // TODO: b/400462917 - Check that crashes are also handled correctly on HSUM devices. We
        // might need to pass the [userId] here to launch the correct home.
        homeIntentProvider.addLaunchHomePendingIntent(wct, displayId)
        return wct
    }

    /**
     * Cleans up any existing bubble tasks by removing bubble specific overrides.
     * After cleanup, the device will be transitioned to the home screen.
     */
    private fun handleBubbleTaskCleanup(bc: BubbleController) {
        val wct = WindowContainerTransaction()
        for (task in shellTaskOrganizer.getRunningTasks()) {
            if (bc.shouldBeAppBubble(task)) {
                val exitWct =
                    BubbleUtils.getExitBubbleTransaction(task.token, /* captionInsetsOwner= */ null)
                wct.merge(exitWct, /* transfer= */ true)
            }
        }
        if (!wct.isEmpty) {
            // Make sure we end up on the home screen
            addLaunchHomePendingIntent(wct, DEFAULT_DISPLAY)
            transitions.startTransition(WindowManager.TRANSIT_CHANGE, wct, NoOpTransitionHandler())
        }
    }
}
