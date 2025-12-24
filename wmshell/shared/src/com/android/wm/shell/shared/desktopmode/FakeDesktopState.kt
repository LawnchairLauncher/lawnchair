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

import android.view.Display

class FakeDesktopState : DesktopState {

    /**
     * Change whether or not the system can enter desktop mode.
     *
     * This will be the default value for all displays. To change the value for a particular
     * display, update [overrideDesktopModeSupportPerDisplay].
     *
     * When set to `true`, [isFreeformEnabled] is also set to `true`, as this is what we want most
     * of the time (if freeform is not enabled, desktop mode cannot really exist).
     */
    override var canEnterDesktopMode: Boolean = false
        set(value) {
            field = value
            if (value) isFreeformEnabled = true
        }

    override var canShowDesktopExperienceDevOption: Boolean = false
    override var canShowDesktopModeDevOption: Boolean = false
    override var enterDesktopByDefaultOnFreeformDisplay: Boolean = false
    override var isDeviceEligibleForDesktopMode: Boolean = false
    override var enableMultipleDesktops: Boolean = false

    /** Override [canEnterDesktopMode] for a specific display. */
    val overrideDesktopModeSupportPerDisplay = mutableMapOf<Int, Boolean>()
    /** Overrides [isProjectedMode] */
    var isProjected: Boolean = false

    override fun isMultipleDesktopFrontendEnabledOnDisplay(display: Display): Boolean =
        enableMultipleDesktops && isDesktopModeSupportedOnDisplay(display)

    override fun isMultipleDesktopFrontendEnabledOnDisplay(displayId: Int): Boolean =
        enableMultipleDesktops && isDesktopModeSupportedOnDisplay(displayId)

    /**
     * This implementation returns [canEnterDesktopMode] unless overridden in
     * [overrideDesktopModeSupportPerDisplay].
     */
    override fun isDesktopModeSupportedOnDisplay(displayId: Int): Boolean {
        return overrideDesktopModeSupportPerDisplay[displayId] ?: canEnterDesktopMode
    }

    override fun isDesktopModeSupportedOnDisplay(display: Display): Boolean {
        return isDesktopModeSupportedOnDisplay(display.displayId)
    }

    override fun isProjectedMode(): Boolean {
        return isProjected
    }

    override var overridesShowAppHandle: Boolean = false

    override var isFreeformEnabled: Boolean = false

    override var shouldShowHomeBehindDesktop: Boolean = false
}