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
import android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT
import android.hardware.display.DisplayManager
import android.os.SystemProperties
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import com.android.internal.R
import com.android.internal.annotations.VisibleForTesting
import com.android.window.flags2.Flags
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper

@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
class DesktopStateImpl(context: Context) : DesktopState {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    private val projectedModeState by lazy { ProjectedModeState(context, this) }

    private val enforceDeviceRestrictions =
        SystemProperties.getBoolean(ENFORCE_DEVICE_RESTRICTIONS_SYS_PROP, true)

    private val isDesktopModeDevOptionSupported =
        context.getResources().getBoolean(R.bool.config_isDesktopModeDevOptionSupported)

    private val isDesktopModeSupported =
        context.getResources().getBoolean(R.bool.config_isDesktopModeSupported)

    private val canInternalDisplayHostDesktops =
        context.getResources().getBoolean(R.bool.config_canInternalDisplayHostDesktops)

    private val isDeviceEligibleForDesktopModeDevOption =
        if (!enforceDeviceRestrictions) {
            true
        } else {
            val desktopModeSupportedOnInternalDisplay =
                isDesktopModeSupported && canInternalDisplayHostDesktops
            desktopModeSupportedOnInternalDisplay || isDesktopModeDevOptionSupported
        }

    override val canShowDesktopModeDevOption: Boolean =
        isDeviceEligibleForDesktopModeDevOption && Flags.showDesktopWindowingDevOption()

    private val isDesktopModeEnabledByDevOption =
        DesktopModeFlags.isDesktopModeForcedEnabled() && canShowDesktopModeDevOption

    override val canEnterDesktopMode: Boolean = run {
        val isEligibleForDesktopMode =
            isDeviceEligibleForDesktopMode &&
                    (DesktopExperienceFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE.isTrue ||
                            canInternalDisplayHostDesktops)
        val desktopModeEnabled =
            isEligibleForDesktopMode && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MODE.isTrue
        desktopModeEnabled || isDesktopModeEnabledByDevOption
    }

    private val isDeviceEligibleForDesktopExperienceDevOption =
        !enforceDeviceRestrictions || isDesktopModeSupported || isDesktopModeDevOptionSupported

    override val canShowDesktopExperienceDevOption: Boolean =
        Flags.showDesktopExperienceDevOption() && isDeviceEligibleForDesktopExperienceDevOption

    override val enterDesktopByDefaultOnFreeformDisplay: Boolean =
        DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX.isTrue ||
        DesktopExperienceFlags.ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS.isTrue &&
            SystemProperties.getBoolean(
                ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP,
                context
                    .getResources()
                    .getBoolean(R.bool.config_enterDesktopByDefaultOnFreeformDisplay),
            )

    override val isDeviceEligibleForDesktopMode: Boolean
        get() {
            if (!enforceDeviceRestrictions) return true
            val desktopModeSupportedByDevOptions =
                Flags.enableDesktopModeThroughDevOption() && isDesktopModeDevOptionSupported
            return isDesktopModeSupported || desktopModeSupportedByDevOptions
        }

    override val enableMultipleDesktops: Boolean =
        DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
                && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND.isTrue
                && canEnterDesktopMode

    override fun isMultipleDesktopFrontendEnabledOnDisplay(display: Display): Boolean =
        DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND.isTrue
                && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
                && isDesktopModeSupportedOnDisplay(display)

    override fun isMultipleDesktopFrontendEnabledOnDisplay(displayId: Int): Boolean =
        displayManager?.getDisplay(displayId)?.let { isMultipleDesktopFrontendEnabledOnDisplay(it) }
            ?: false

    override fun isDesktopModeSupportedOnDisplay(displayId: Int): Boolean =
        displayManager?.getDisplay(displayId)?.let { isDesktopModeSupportedOnDisplay(it) } ?: false

    override fun isDesktopModeSupportedOnDisplay(display: Display): Boolean {
        if (!canEnterDesktopMode) return false
        if (!enforceDeviceRestrictions) return true
        if (display.type == Display.TYPE_INTERNAL) return canInternalDisplayHostDesktops
        if (!DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue) return false
        return windowManager?.isEligibleForDesktopMode(display.displayId) ?: false
    }

    override fun isProjectedMode(): Boolean = projectedModeState.isProjectedMode

    private val deviceHasLargeScreen =
        displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)
            ?.filter { display -> display.type == Display.TYPE_INTERNAL }
            ?.any { display ->
                display.minSizeDimensionDp >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP
            } ?: false

    override val overridesShowAppHandle: Boolean =
        (Flags.showAppHandleLargeScreens() ||
            BubbleAnythingFlagHelper.enableBubbleToFullscreen()) && deviceHasLargeScreen

    private val hasFreeformFeature =
        context.getPackageManager().hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT)
    private val hasFreeformDevOption =
        Settings.Global.getInt(
            context.getContentResolver(),
            Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT,
            0
        ) != 0
    override val isFreeformEnabled: Boolean = hasFreeformFeature || hasFreeformDevOption

    override val shouldShowHomeBehindDesktop: Boolean =
        Flags.showHomeBehindDesktop() && context.resources.getBoolean(
            R.bool.config_showHomeBehindDesktop
        )

    companion object {
        @VisibleForTesting
        const val ENFORCE_DEVICE_RESTRICTIONS_SYS_PROP =
            "persist.wm.debug.desktop_mode_enforce_device_restrictions"

        @VisibleForTesting
        const val ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP =
            "persist.wm.debug.enter_desktop_by_default_on_freeform_display"

        @Volatile
        private var instance: DesktopState? = null

        /**
         * Get or create the [DesktopState] singleton.
         *
         * This method should not be used if Dagger is used to inject the singleton.
         */
        fun getInstance(context: Context): DesktopState {
            return instance ?: synchronized(this) {
                if (instance == null) {
                    instance = DesktopStateImpl(context)
                }
                instance!!
            }
        }
    }
}
