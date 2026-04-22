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
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit

/** [ShellCrashHandler] for shell to use when it's being initialized. Currently it only restores
 *  the home task to top.
 **/
class ShellCrashHandler(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val homeIntentProvider: HomeIntentProvider,
    private val desktopState: DesktopState,
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
        if (desktopState.canEnterDesktopMode &&
            !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
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
    }

    private fun addLaunchHomePendingIntent(
        wct: WindowContainerTransaction, displayId: Int
    ): WindowContainerTransaction {
        // TODO: b/400462917 - Check that crashes are also handled correctly on HSUM devices. We
        // might need to pass the [userId] here to launch the correct home.
        homeIntentProvider.addLaunchHomePendingIntent(wct, displayId)
        return wct
    }
}