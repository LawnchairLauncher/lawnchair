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

package com.android.launcher3.taskbar.customization

import android.content.Context
import com.android.launcher3.Flags.enableRecentsInTaskbar
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING_IN_DESKTOP_MODE
import com.android.launcher3.config.FeatureFlags.enableTaskbarPinning
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.NavigationMode.*
import javax.inject.Inject

/** Evaluates all the features taskbar can have. */
@LauncherAppSingleton
class TaskbarFeatureEvaluator
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val displayController: DisplayController,
    private val desktopVisibilityController: DesktopVisibilityController,
    private val launcherPrefs: LauncherPrefs,
) {
    val primaryDisplayId = context.displayId
    val hasBubbles = false
    val hasNavButtons: Boolean
        get() = displayController.info.navigationMode == THREE_BUTTONS

    val isRecentsEnabled: Boolean
        get() = enableRecentsInTaskbar()

    val isTransient: Boolean
        get() =
            if (
                displayController.info.navigationMode != NO_BUTTON ||
                    desktopVisibilityController.isInDesktopMode(primaryDisplayId) ||
                    displayController.info.showDesktopTaskbarForFreeformDisplay() ||
                    (displayController.info.showLockedTaskbarOnHome() &&
                        displayController.info.isHomeVisible)
            ) {
                false
            } else if (enableTaskbarPinning()) {
                !isPinned
            } else {
                true
            }

    val isPinned: Boolean
        get() =
            if (
                desktopVisibilityController.isInDesktopModeAndNotInOverview(primaryDisplayId) ||
                    displayController.info.showDesktopTaskbarForFreeformDisplay()
            ) {
                true
            } else if (desktopVisibilityController.isInDesktopMode(primaryDisplayId)) {
                launcherPrefs.get(TASKBAR_PINNING_IN_DESKTOP_MODE)
            } else {
                launcherPrefs.get(TASKBAR_PINNING)
            }

    val supportsPinningPopup: Boolean
        get() = !hasNavButtons

    val isPersistent: Boolean
        get() = isPinned || hasNavButtons

    val supportsTransitionToTransientTaskbar: Boolean
        get() = !hasNavButtons && !DisplayController.showDesktopTaskbarForFreeformDisplay(context)

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getTaskbarFeatureEvaluator)
    }
}
