/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.shared.desktopmode;

import static android.hardware.display.DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED;

import static com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper.enableBubbleToFullscreen;

import android.content.res.Resources.NotFoundException;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Build.VERSION_CODES_FULL;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemProperties;
import android.view.Display;
import android.view.WindowManager;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.window.flags2.Flags;

import java.util.Arrays;

/**
 * Constants for desktop mode feature
 *
 * @deprecated Use {@link DesktopState} or {@link DesktopConfig} instead.
 */
@Deprecated(forRemoval = true)
public class DesktopModeStatus {

    @Nullable
    private static Boolean sIsLargeScreenDevice = null;

    /**
     * Flag to indicate whether to use rounded corners for windows in desktop mode.
     */
    private static final boolean USE_ROUNDED_CORNERS = SystemProperties.getBoolean(
            "persist.wm.debug.desktop_use_rounded_corners", true);

    /**
     * Flag to indicate whether to restrict desktop mode to supported devices.
     */
    private static final boolean ENFORCE_DEVICE_RESTRICTIONS = SystemProperties.getBoolean(
            "persist.wm.debug.desktop_mode_enforce_device_restrictions", true);

    /**
     * Sysprop declaring whether to enters desktop mode by default when the windowing mode of the
     * display's root TaskDisplayArea is set to WINDOWING_MODE_FREEFORM.
     *
     * <p>If it is not defined, then {@code R.integer.config_enterDesktopByDefaultOnFreeformDisplay}
     * is used.
     */
    public static final String ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP =
            "persist.wm.debug.enter_desktop_by_default_on_freeform_display";

    /**
     * Return whether to use rounded corners for windows.
     */
    public static boolean useRoundedCorners() {
        return USE_ROUNDED_CORNERS;
    }

    /**
     * Return {@code true} if desktop mode should be restricted to supported devices.
     */
    @VisibleForTesting
    public static boolean enforceDeviceRestrictions() {
        return ENFORCE_DEVICE_RESTRICTIONS;
    }

    /**
     * Return {@code true} if the current device supports desktop mode.
     */
    private static boolean isDesktopModeSupported(@NonNull Context context) {
        try {
            return context.getResources().getBoolean(R.bool.config_isDesktopModeSupported);
        } catch (NotFoundException e) {
            return false;
        }
    }

    /**
     * Return {@code true} if the current device supports the developer option for desktop mode.
     */
    private static boolean isDesktopModeDevOptionSupported(@NonNull Context context) {
        try {
            return context.getResources().getBoolean(R.bool.config_isDesktopModeDevOptionSupported);
        } catch (NotFoundException e) {
            return false;
        }
    }

    /**
     * Return {@code true} if the current device can host desktop sessions on its internal display.
     */
    private static boolean canInternalDisplayHostDesktops(@NonNull Context context) {
        try {
            return context.getResources().getBoolean(R.bool.config_canInternalDisplayHostDesktops);
        } catch (NotFoundException e) {
            return false;
        }
    }


    /**
     * Return {@code true} if desktop mode dev option should be shown on current device
     */
    public static boolean canShowDesktopModeDevOption(@NonNull Context context) {
        return isDeviceEligibleForDesktopModeDevOption(context)
                && Flags.showDesktopWindowingDevOption();
    }

    /**
     * Return {@code true} if desktop mode dev option should be shown on current device
     */
    public static boolean canShowDesktopExperienceDevOption(@NonNull Context context) {
        return Flags.showDesktopExperienceDevOption()
                && isDeviceEligibleForDesktopExperienceDevOption(context);
    }

    /** Returns if desktop mode dev option should be enabled if there is no user override. */
    public static boolean shouldDevOptionBeEnabledByDefault(Context context) {
        return isDeviceEligibleForDesktopMode(context)
            && Flags.enableDesktopWindowingMode();
    }

    /**
     * Return {@code true} if desktop mode is enabled and can be entered on the current device.
     */
    public static boolean canEnterDesktopMode(@NonNull Context context) {
        boolean ENABLED_PROJECTED_DISPLAY_DESKTOP_MODE;
        if ((VERSION.SDK_INT >= VERSION_CODES.BAKLAVA)
            && (VERSION.SDK_INT_FULL >= 3600001)) {
            ENABLED_PROJECTED_DISPLAY_DESKTOP_MODE = DesktopExperienceFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE.isTrue();
        } else {
            ENABLED_PROJECTED_DISPLAY_DESKTOP_MODE = false;
        }
        boolean isEligibleForDesktopMode = isDeviceEligibleForDesktopMode(context) && (
            ENABLED_PROJECTED_DISPLAY_DESKTOP_MODE
                        || canInternalDisplayHostDesktops(context));
        
        boolean ENABLE_DESKTOP_WINDOWING_MODE;
        if ((VERSION.SDK_INT >= VERSION_CODES.BAKLAVA)
            && (VERSION.SDK_INT_FULL >= 3600001)) {
            ENABLE_DESKTOP_WINDOWING_MODE = DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MODE.isTrue();
        } else {
            ENABLE_DESKTOP_WINDOWING_MODE = false;
        }
        boolean desktopModeEnabled =
                isEligibleForDesktopMode && ENABLE_DESKTOP_WINDOWING_MODE;
        return desktopModeEnabled || isDesktopModeEnabledByDevOption(context);
    }

    /**
     * Check if Desktop mode should be enabled because the dev option is shown and enabled.
     */
    private static boolean isDesktopModeEnabledByDevOption(@NonNull Context context) {
        boolean isDesktopModeForcedEnabled;
        if ((VERSION.SDK_INT >= VERSION_CODES.BAKLAVA)
            && (VERSION.SDK_INT_FULL >= 3600001)) {
            // LC-Ignored
            isDesktopModeForcedEnabled = DesktopModeFlags.isDesktopModeForcedEnabled();
        } else {
            isDesktopModeForcedEnabled = false;
        }
        return isDesktopModeForcedEnabled
                && canShowDesktopModeDevOption(context);
    }

    /**
     * Check to see if a display should have desktop mode enabled or not. Internal
     * and external displays have separate logic.
     */
    public static boolean isDesktopModeSupportedOnDisplay(Context context, Display display) {
        if (!canEnterDesktopMode(context)) {
            return false;
        }
        if (!enforceDeviceRestrictions()) {
            return true;
        }
        if (display.getType() == Display.TYPE_INTERNAL) {
            return canInternalDisplayHostDesktops(context);
        }
        
        boolean ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT;
        if ((VERSION.SDK_INT >= VERSION_CODES.BAKLAVA)
            && (VERSION.SDK_INT_FULL >= 3600001)) {
            ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT = DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue();
        } else {
            ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT = false;
        }

        // TODO (b/395014779): Change this to use WM API
        if (!ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT) {
            return false;
        }
        final WindowManager wm = context.getSystemService(WindowManager.class);
        return wm != null && wm.isEligibleForDesktopMode(display.getDisplayId());
    }

    /**
     * Returns true if the multi-desks frontend should be enabled on the display.
     */
    public static boolean isMultipleDesktopFrontendEnabledOnDisplay(@NonNull Context context,
            Display display) {
        
        boolean ENABLE_MULTIPLE_DESKTOPS_FRONTEND;
        boolean ENABLE_MULTIPLE_DESKTOPS_BACKEND;

        if ((VERSION.SDK_INT >= VERSION_CODES.BAKLAVA)
            && (VERSION.SDK_INT_FULL >= 3600001)) {
            ENABLE_MULTIPLE_DESKTOPS_FRONTEND = DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND.isTrue();
            ENABLE_MULTIPLE_DESKTOPS_BACKEND = DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue();
        } else {
            ENABLE_MULTIPLE_DESKTOPS_FRONTEND = false;
            ENABLE_MULTIPLE_DESKTOPS_BACKEND = false;
        }
        
        return ENABLE_MULTIPLE_DESKTOPS_FRONTEND
                && ENABLE_MULTIPLE_DESKTOPS_BACKEND
                && isDesktopModeSupportedOnDisplay(context, display);
    }

    /**
     * Returns whether the multiple desktops feature is enabled for this device (both backend and
     * frontend implementations).
     */
    public static boolean enableMultipleDesktops(@NonNull Context context) {
        boolean ENABLE_MULTIPLE_DESKTOPS_FRONTEND;
        boolean ENABLE_MULTIPLE_DESKTOPS_BACKEND;

        if ((VERSION.SDK_INT >= VERSION_CODES.BAKLAVA)
            && (VERSION.SDK_INT_FULL >= 3600001)) {
            ENABLE_MULTIPLE_DESKTOPS_FRONTEND = DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND.isTrue();
            ENABLE_MULTIPLE_DESKTOPS_BACKEND = DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue();
        } else {
            ENABLE_MULTIPLE_DESKTOPS_FRONTEND = false;
            ENABLE_MULTIPLE_DESKTOPS_BACKEND = false;
        }
        
        return ENABLE_MULTIPLE_DESKTOPS_BACKEND
                && ENABLE_MULTIPLE_DESKTOPS_FRONTEND
                && canEnterDesktopMode(context);
    }

    /**
     * @return {@code true} if this device is requesting to show the app handle despite non
     * necessarily enabling desktop mode
     */
    public static boolean overridesShowAppHandle(@NonNull Context context) {
        return (Flags.showAppHandleLargeScreens() || enableBubbleToFullscreen())
                && deviceHasLargeScreen(context);
    }

    /**
     * @return {@code true} if the app handle should be shown because desktop mode is enabled or
     * the device has a large screen
     */
    public static boolean canEnterDesktopModeOrShowAppHandle(@NonNull Context context) {
        return canEnterDesktopMode(context) || overridesShowAppHandle(context);
    }

   /**
     * Return {@code true} if desktop mode is unrestricted and is supported on the device.
     */
    public static boolean isDeviceEligibleForDesktopMode(@NonNull Context context) {
        if (!enforceDeviceRestrictions()) {
            return true;
        }
        final boolean enableDesktopModeThroughDevOption;
        if ((VERSION.SDK_INT >= VERSION_CODES.BAKLAVA)
            && (VERSION.SDK_INT_FULL >= 3600001)) {
            enableDesktopModeThroughDevOption = Flags.enableDesktopModeThroughDevOption();
        } else {
            enableDesktopModeThroughDevOption = false;
        }
        final boolean desktopModeSupportedByDevOptions =
            enableDesktopModeThroughDevOption
                    && isDesktopModeDevOptionSupported(context);
        return isDesktopModeSupported(context) || desktopModeSupportedByDevOptions;
    }

    /**
     * Return {@code true} if the developer option for desktop mode is supported on this device.
     *
     * <p> This method doesn't check if the developer option flag is enabled or not.
     */
    private static boolean isDeviceEligibleForDesktopModeDevOption(@NonNull Context context) {
        if (!enforceDeviceRestrictions()) {
            return true;
        }
        final boolean desktopModeSupported = isDesktopModeSupported(context)
                && canInternalDisplayHostDesktops(context);
        return desktopModeSupported || isDesktopModeDevOptionSupported(context);
    }

    /**
     * Return {@code true} if the developer option for desktop experience is supported on this
     * device.
     *
     * <p> This method doesn't check if the developer option flag is enabled or not.
     */
    private static boolean isDeviceEligibleForDesktopExperienceDevOption(@NonNull Context context) {
        if (!enforceDeviceRestrictions()) {
            return true;
        }
        return isDesktopModeSupported(context) || isDesktopModeDevOptionSupported(context);
    }

    /**
     * @return {@code true} if this device has an internal large screen
     */
    private static boolean deviceHasLargeScreen(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            if (sIsLargeScreenDevice == null) {
                sIsLargeScreenDevice = Arrays.stream(
                    context.getSystemService(DisplayManager.class)
                            .getDisplays(DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED))
                    .filter(display -> display.getType() == Display.TYPE_INTERNAL)
                    .anyMatch(display -> display.getMinSizeDimensionDp()
                            >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP);
            }
        } else sIsLargeScreenDevice = false; // pE-TODO(QPR1, QPR2): Should make it better
        return sIsLargeScreenDevice;
    }

    /**
     * Return {@code true} if a display should enter desktop mode by default when the windowing mode
     * of the display's root [TaskDisplayArea] is set to WINDOWING_MODE_FREEFORM.
     */
    public static boolean enterDesktopByDefaultOnFreeformDisplay(@NonNull Context context) {
        if (DesktopExperienceFlags.ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX.isTrue()) {
            return true;
        }
        if (!DesktopExperienceFlags.ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS.isTrue()) {
            return false;
        }
        return SystemProperties.getBoolean(ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP,
                context.getResources().getBoolean(
                        R.bool.config_enterDesktopByDefaultOnFreeformDisplay));
    }
}
