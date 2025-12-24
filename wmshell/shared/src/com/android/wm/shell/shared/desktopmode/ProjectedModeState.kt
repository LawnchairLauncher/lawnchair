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

package com.android.wm.shell.shared.desktopmode

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.IDisplayWindowListener
import android.view.WindowManagerGlobal
import android.window.DesktopExperienceFlags

/** Caches and manages the projected mode state. */
class ProjectedModeState(context: Context, val desktopState: DesktopState) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    var isProjectedMode: Boolean = getProjectedMode()
        private set

    val callback =
        object : IDisplayWindowListener.Stub() {
            override fun onDisplayAddSystemDecorations(displayId: Int) {}

            override fun onDisplayRemoveSystemDecorations(displayId: Int) {}

            override fun onDesktopModeEligibleChanged(displayId: Int) {
                isProjectedMode = getProjectedMode()
            }

            override fun onDisplayAdded(displayId: Int) {}

            override fun onDisplayConfigurationChanged(displayId: Int, newConfig: Configuration?) {}

            override fun onDisplayRemoved(displayId: Int) {}

            override fun onFixedRotationStarted(displayId: Int, newRotation: Int) {}

            override fun onFixedRotationFinished(displayId: Int) {}

            override fun onKeepClearAreasChanged(
                displayId: Int,
                restricted: MutableList<Rect>?,
                unrestricted: MutableList<Rect>?,
            ) {}

            override fun onDisplayAnimationsDisabledChanged(displayId: Int, disabled: Boolean) {}
        }

    init {
        WindowManagerGlobal.getWindowManagerService()?.registerDisplayWindowListener(callback)
    }

    private fun getProjectedMode(): Boolean {
        if (!DesktopExperienceFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE.isTrue) {
            return false
        }

        if (desktopState.isDesktopModeSupportedOnDisplay(Display.DEFAULT_DISPLAY)) {
            return false
        }

        return displayManager?.displays?.any { display: Display ->
            desktopState.isDesktopModeSupportedOnDisplay(display)
        } ?: false
    }
}
