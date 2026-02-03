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
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemProperties
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import com.android.internal.R
import com.android.internal.annotations.VisibleForTesting
import com.android.window.flags.Flags
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper

@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
class DesktopStateImpl(context: Context) : DesktopState {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    private val enforceDeviceRestrictions =
        SystemProperties.getBoolean(ENFORCE_DEVICE_RESTRICTIONS_SYS_PROP, true)

    private val isDesktopModeDevOptionSupported =
        try {
            context.getResources().getBoolean(R.bool.config_isDesktopModeDevOptionSupported)
        } catch (e: Resources.NotFoundException) {
            false
        }

    private val isDesktopModeSupported =
        try {
            context.getResources().getBoolean(R.bool.config_isDesktopModeSupported)
        } catch (e: Resources.NotFoundException) {
            false
        }

    private val canInternalDisplayHostDesktops =
        try {
            context.getResources().getBoolean(R.bool.config_canInternalDisplayHostDesktops)
        } catch (e: Resources.NotFoundException) {
            false
        }

    private val isDeviceEligibleForDesktopModeDevOption =
        if (!enforceDeviceRestrictions) {
            true
        } else {
            val desktopModeSupportedOnInternalDisplay =
                isDesktopModeSupported && canInternalDisplayHostDesktops
            desktopModeSupportedOnInternalDisplay || isDesktopModeDevOptionSupported
        }

    override val canShowDesktopModeDevOption: Boolean =
        isDeviceEligibleForDesktopModeDevOption && if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            && (Build.VERSION.SDK_INT_FULL >= 3600001)
        ) {
            Flags.showDesktopWindowingDevOption()
        } else {
            false
        }

    private val isDesktopModeEnabledByDevOption =
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            && (Build.VERSION.SDK_INT_FULL >= 3600001)
        ) {
            DesktopModeFlags.isDesktopModeForcedEnabled()
        } else {
            false
        } && canShowDesktopModeDevOption

    override val canEnterDesktopMode: Boolean = run {
        val isEligibleForDesktopMode =
            isDeviceEligibleForDesktopMode &&
                    (if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
                        && (Build.VERSION.SDK_INT_FULL >= 3600001)
                    ) {
                        DesktopExperienceFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE.isTrue
                    } else {
                        false
                    } || canInternalDisplayHostDesktops)
        val desktopModeEnabled =
            isEligibleForDesktopMode && if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
                && (Build.VERSION.SDK_INT_FULL >= 3600001)
            ) {
                DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MODE.isTrue
            } else { false}
        desktopModeEnabled || isDesktopModeEnabledByDevOption
    }

    private val isDeviceEligibleForDesktopExperienceDevOption =
        !enforceDeviceRestrictions || isDesktopModeSupported || isDesktopModeDevOptionSupported

    override val canShowDesktopExperienceDevOption: Boolean =
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            && (Build.VERSION.SDK_INT_FULL >= 3600001)
        ) {
            Flags.showDesktopExperienceDevOption()
        } else {
            false
        } && isDeviceEligibleForDesktopExperienceDevOption

    override val enterDesktopByDefaultOnFreeformDisplay: Boolean =
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            && (Build.VERSION.SDK_INT_FULL >= 3600001)
        ) {
            DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX.isTrue ||
            DesktopExperienceFlags.ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS.isTrue &&
                SystemProperties.getBoolean(
                    ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP,
                    context
                        .getResources()
                        .getBoolean(R.bool.config_enterDesktopByDefaultOnFreeformDisplay),
                )
        } else {
            false
        }

    override val isDeviceEligibleForDesktopMode: Boolean
        get() {
            if (!enforceDeviceRestrictions) return true
            val desktopModeSupportedByDevOptions =
                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
                    && (Build.VERSION.SDK_INT_FULL >= 3600001)
                ) {
                    Flags.enableDesktopModeThroughDevOption()
                } else {
                    false
                } && isDesktopModeDevOptionSupported
            return isDesktopModeSupported || desktopModeSupportedByDevOptions
        }

    override val enableMultipleDesktops: Boolean =
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            && (Build.VERSION.SDK_INT_FULL >= 3600001)
        ) {
            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
                    && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND.isTrue
        } else {
            false
        } && canEnterDesktopMode

    override fun isMultipleDesktopFrontendEnabledOnDisplay(display: Display): Boolean =
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            && (Build.VERSION.SDK_INT_FULL >= 3600001)
        ) {
            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND.isTrue
                    && DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        } else {
            false
        } && isDesktopModeSupportedOnDisplay(display)

    override fun isMultipleDesktopFrontendEnabledOnDisplay(displayId: Int): Boolean =
        displayManager.getDisplay(displayId)?.let { isMultipleDesktopFrontendEnabledOnDisplay(it) }
            ?: false

    override fun isDesktopModeSupportedOnDisplay(displayId: Int): Boolean =
        displayManager.getDisplay(displayId)?.let { isDesktopModeSupportedOnDisplay(it) } ?: false

    override fun isDesktopModeSupportedOnDisplay(display: Display): Boolean {
        if (!canEnterDesktopMode) return false
        if (!enforceDeviceRestrictions) return true
        if (display.type == Display.TYPE_INTERNAL) return canInternalDisplayHostDesktops
        if (!if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
                && (Build.VERSION.SDK_INT_FULL >= 3600001)
            ) {
                DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue
            } else {false}
        ) return false
        return windowManager?.isEligibleForDesktopMode(display.displayId) ?: false
    }

    override fun isProjectedMode(): Boolean {
        if (!if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
                && (Build.VERSION.SDK_INT_FULL >= 3600001)
            ) {
                DesktopExperienceFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE.isTrue
            } else {
                false
            }
        ) {
            return false
        }

        if (isDesktopModeSupportedOnDisplay(Display.DEFAULT_DISPLAY)) {
            return false
        }

        return displayManager.displays
            ?.any { display -> isDesktopModeSupportedOnDisplay(display)
            } ?: false
    }

    private val deviceHasLargeScreen =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)
                ?.filter { display -> display.type == Display.TYPE_INTERNAL }
                ?.any { display ->
                    display.minSizeDimensionDp >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP
                } ?: false
        } else {
            false
        }

    override val overridesShowAppHandle: Boolean =
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            && (Build.VERSION.SDK_INT_FULL >= 3600001)
        ) {
            (Flags.showAppHandleLargeScreens() ||
                BubbleAnythingFlagHelper.enableBubbleToFullscreen()) && deviceHasLargeScreen
        } else {
            false
        }

    private val hasFreeformFeature =
        context.getPackageManager().hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT)
    private val hasFreeformDevOption =
        Settings.Global.getInt(
            context.getContentResolver(),
            Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT,
            0,
        ) != 0
    override val isFreeformEnabled: Boolean = hasFreeformFeature || hasFreeformDevOption

    override val shouldShowHomeBehindDesktop: Boolean =
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            && (Build.VERSION.SDK_INT_FULL >= 3600001)
        ) {
            Flags.showHomeBehindDesktop() && context.resources.getBoolean(
                R.bool.config_showHomeBehindDesktop,
            )
        } else {
            false
        }

    companion object {
        @VisibleForTesting
        const val ENFORCE_DEVICE_RESTRICTIONS_SYS_PROP =
            "persist.wm.debug.desktop_mode_enforce_device_restrictions"

        @VisibleForTesting
        const val ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP =
            "persist.wm.debug.enter_desktop_by_default_on_freeform_display"
    }
}
