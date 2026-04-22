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
import android.view.Display

/**
 * Interface defining which features are available on the device.
 *
 * A feature may be specific to a task or a display and may change over time (e.g.
 * [isDesktopModeSupportedOnDisplay] depends on user settings).
 *
 * This class is meant to be used in WM Shell, System UI and Launcher so they all understand what
 * features are enabled on the current device.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
interface DesktopState {
    /** Returns if desktop mode is enabled and can be entered on the current device. */
    @get:JvmName("canEnterDesktopMode")
    val canEnterDesktopMode: Boolean

    /**
     * Whether desktop mode is enabled or app handles should be shown for other reasons.
     */
    @get:JvmName("canEnterDesktopModeOrShowAppHandle")
    val canEnterDesktopModeOrShowAppHandle: Boolean
        get() = canEnterDesktopMode || overridesShowAppHandle

    /** Whether desktop experience dev option should be shown on current device. */
    @get:JvmName("canShowDesktopExperienceDevOption")
    val canShowDesktopExperienceDevOption: Boolean

    /** Whether desktop mode dev option should be shown on current device. */
    @get:JvmName("canShowDesktopModeDevOption")
    val canShowDesktopModeDevOption: Boolean

    /**
     * Whether a display should enter desktop mode by default when the windowing mode of the
     * display's root [TaskDisplayArea] is set to `WINDOWING_MODE_FREEFORM`.
     */
    @Deprecated("Use isDisplayDesktopFirst() instead.", ReplaceWith("isDisplayDesktopFirst()"))
    @get:JvmName("enterDesktopByDefaultOnFreeformDisplay")
    val enterDesktopByDefaultOnFreeformDisplay: Boolean

    /** Whether desktop mode is unrestricted and is supported on the device. */
    val isDeviceEligibleForDesktopMode: Boolean

    /**
     * Whether the multiple desktops feature is enabled for this device (both backend and
     * frontend implementations).
     */
    @get:JvmName("enableMultipleDesktops")
    val enableMultipleDesktops: Boolean

    /**
     * Returns true if the multi-desks frontend should be enabled on the display.
     */
    fun isMultipleDesktopFrontendEnabledOnDisplay(display: Display): Boolean

    /**
     *  Returns true if the multi-desks frontend should be enabled on the display with [displayId].
     */
    fun isMultipleDesktopFrontendEnabledOnDisplay(displayId: Int): Boolean

    /**
     * Checks if the display with id [displayId] should have desktop mode enabled or not. Internal
     * and external displays have separate logic.
     */
    fun isDesktopModeSupportedOnDisplay(displayId: Int): Boolean

    /**
     * Checks if [display] should have desktop mode enabled or not. Internal and external displays
     * have separate logic.
     */
    fun isDesktopModeSupportedOnDisplay(display: Display): Boolean

    /**
     * Check if the current device is in projected display mode.
     *
     * Note, if the device is not connected to any display, this will return false.
     */
    fun isProjectedMode(): Boolean

    /**
     * Whether the app handle should be shown on this device.
     */
    @get:JvmName("overridesShowAppHandle")
    val overridesShowAppHandle: Boolean

    /**
     * Whether freeform windowing is enabled on the system.
     */
    val isFreeformEnabled: Boolean

    /**
     * Whether the home screen should be shown behind freeform tasks in the desktop.
     */
    val shouldShowHomeBehindDesktop: Boolean

    companion object {
        /** Creates a new [DesktopState] from a context. */
        @JvmStatic
        fun fromContext(context: Context): DesktopState = DesktopStateImpl(context)
    }
}
