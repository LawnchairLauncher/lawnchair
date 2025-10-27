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
import static android.window.DesktopExperienceFlags.ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE;

import static com.android.server.display.feature.flags.Flags.enableDisplayContentModeManagement;
import static com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper.enableBubbleToFullscreen;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.TaskInfo;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemProperties;
import android.view.Display;
import android.view.WindowManager;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.window.flags.Flags;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Constants for desktop mode feature
 */
// TODO(b/237575897): Move this file to the `com.android.wm.shell.shared.desktopmode` package
public class DesktopModeStatus {

    private static final String TAG = "DesktopModeStatus";

    @Nullable
    private static Boolean sIsLargeScreenDevice = null;

    /**
     * Flag to indicate whether task resizing is veiled.
     */
    private static final boolean IS_VEILED_RESIZE_ENABLED = SystemProperties.getBoolean(
            "persist.wm.debug.desktop_veiled_resizing", true);

    /**
     * Flag to indicate is moving task to another display is enabled.
     */
    public static final boolean IS_DISPLAY_CHANGE_ENABLED = SystemProperties.getBoolean(
            "persist.wm.debug.desktop_change_display", false);

    /**
     * Flag to indicate whether to apply shadows to windows in desktop mode.
     */
    private static final boolean USE_WINDOW_SHADOWS = SystemProperties.getBoolean(
            "persist.wm.debug.desktop_use_window_shadows", true);

    /**
     * Flag to indicate whether to apply shadows to the focused window in desktop mode.
     *
     * Note: this flag is only relevant if USE_WINDOW_SHADOWS is false.
     */
    private static final boolean USE_WINDOW_SHADOWS_FOCUSED_WINDOW = SystemProperties.getBoolean(
            "persist.wm.debug.desktop_use_window_shadows_focused_window", false);

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

    private static final boolean USE_APP_TO_WEB_BUILD_TIME_GENERIC_LINKS =
            SystemProperties.getBoolean(
                    "persist.wm.debug.use_app_to_web_build_time_generic_links", true);

    /** Whether the desktop density override is enabled. */
    public static final boolean DESKTOP_DENSITY_OVERRIDE_ENABLED =
            SystemProperties.getBoolean("persist.wm.debug.desktop_mode_density_enabled", false);

    /** Override density for tasks when they're inside the desktop. */
    public static final int DESKTOP_DENSITY_OVERRIDE =
            SystemProperties.getInt("persist.wm.debug.desktop_mode_density", 284);

    /** The minimum override density allowed for tasks inside the desktop. */
    private static final int DESKTOP_DENSITY_MIN = 100;

    /** The maximum override density allowed for tasks inside the desktop. */
    private static final int DESKTOP_DENSITY_MAX = 1000;

    /** The number of [WindowDecorViewHost] instances to warm up on system start. */
    private static final int WINDOW_DECOR_PRE_WARM_SIZE = 2;

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
     * Sysprop declaring whether to enable drag-to-maximize for desktop windows.
     *
     * <p>If it is not defined, then {@code R.integer.config_dragToMaximizeInDesktopMode}
     * is used.
     */
    public static final String ENABLE_DRAG_TO_MAXIMIZE_SYS_PROP =
            "persist.wm.debug.enable_drag_to_maximize";

    /**
     * Sysprop declaring the maximum number of Tasks to show in Desktop Mode at any one time.
     *
     * <p>If it is not defined, then {@code R.integer.config_maxDesktopWindowingActiveTasks} is
     * used.
     *
     * <p>The limit does NOT affect Picture-in-Picture, Bubbles, or System Modals (like a screen
     * recording window, or Bluetooth pairing window).
     */
    private static final String MAX_TASK_LIMIT_SYS_PROP = "persist.wm.debug.desktop_max_task_limit";

    /**
     * Sysprop declaring the number of [WindowDecorViewHost] instances to warm up on system start.
     *
     * <p>If it is not defined, then [WINDOW_DECOR_PRE_WARM_SIZE] is used.
     */
    private static final String WINDOW_DECOR_PRE_WARM_SIZE_SYS_PROP =
            "persist.wm.debug.desktop_window_decor_pre_warm_size";

    /**
     * Return {@code true} if veiled resizing is active. If false, fluid resizing is used.
     */
    public static boolean isVeiledResizeEnabled() {
        return IS_VEILED_RESIZE_ENABLED;
    }

    /**
     * Return whether to use window shadows.
     *
     * @param isFocusedWindow whether the window to apply shadows to is focused
     */
    public static boolean useWindowShadow(boolean isFocusedWindow) {
        return USE_WINDOW_SHADOWS
                || (USE_WINDOW_SHADOWS_FOCUSED_WINDOW && isFocusedWindow);
    }

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
     * Return the maximum limit on the number of Tasks to show in Desktop Mode at any one time.
     */
    public static int getMaxTaskLimit(@NonNull Context context) {
        return SystemProperties.getInt(MAX_TASK_LIMIT_SYS_PROP,
                context.getResources().getInteger(R.integer.config_maxDesktopWindowingActiveTasks));
    }

    /**
     * Return the maximum size of the window decoration surface control view host pool, or zero if
     * there should be no pooling.
     */
    public static int getWindowDecorScvhPoolSize(@NonNull Context context) {
        if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_SCVH_CACHE.isTrue()) return 0;
        final int maxTaskLimit = getMaxTaskLimit(context);
        if (maxTaskLimit > 0) {
            return maxTaskLimit;
        }
        // TODO: b/368032552 - task limit equal to 0 means unlimited. Figure out what the pool
        //  size should be in that case.
        return 0;
    }

    /** The number of [WindowDecorViewHost] instances to warm up on system start. */
    public static int getWindowDecorPreWarmSize() {
        return SystemProperties.getInt(WINDOW_DECOR_PRE_WARM_SIZE_SYS_PROP,
                WINDOW_DECOR_PRE_WARM_SIZE);
    }

    /**
     * Return {@code true} if the current device supports desktop mode.
     */
    private static boolean isDesktopModeSupported(@NonNull Context context) {
        return context.getResources().getBoolean(R.bool.config_isDesktopModeSupported);
    }

    /**
     * Return {@code true} if the current device supports the developer option for desktop mode.
     */
    private static boolean isDesktopModeDevOptionSupported(@NonNull Context context) {
        return context.getResources().getBoolean(R.bool.config_isDesktopModeDevOptionSupported);
    }

    /**
     * Return {@code true} if the current device can host desktop sessions on its internal display.
     */
    private static boolean canInternalDisplayHostDesktops(@NonNull Context context) {
        return context.getResources().getBoolean(R.bool.config_canInternalDisplayHostDesktops);
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
            && isDeviceEligibleForDesktopMode(context);
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
        try {
            return (isDeviceEligibleForDesktopMode(context)
                && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MODE.isTrue())
                || isDesktopModeEnabledByDevOption(context);
        } catch (Throwable e) {
            // Lawnchair-TODO-Postmerge: All of the LC-Ignored MAY be only accessible to newer APIs.
            // LC-Ignored
            return false;
        }
    }

    /**
     * Check if Desktop mode should be enabled because the dev option is shown and enabled.
     */
    private static boolean isDesktopModeEnabledByDevOption(@NonNull Context context) {
        try {
            return DesktopModeFlags.isDesktopModeForcedEnabled()
                && canShowDesktopModeDevOption(context);
        } catch (Throwable e) {
            // LC-Ignored
            return false;
        }
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

        // TODO (b/395014779): Change this to use WM API
        if ((display.getType() == Display.TYPE_EXTERNAL
                || display.getType() == Display.TYPE_OVERLAY)
                && enableDisplayContentModeManagement()) {
            final WindowManager wm = context.getSystemService(WindowManager.class);
            return wm != null && wm.shouldShowSystemDecors(display.getDisplayId());
        }

        return false;
    }

    /**
     * Returns whether the multiple desktops feature is enabled for this device (both backend and
     * frontend implementations).
     */
    public static boolean enableMultipleDesktops(@NonNull Context context) {
        try {
            return DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue()
                && Flags.enableMultipleDesktopsFrontend()
                && canEnterDesktopMode(context);
        } catch (Throwable e) {
            // LC-Ignored
            return false;
        }
    }

    /**
     * @return {@code true} if this device is requesting to show the app handle despite non
     * necessarily enabling desktop mode
     */
    public static boolean overridesShowAppHandle(@NonNull Context context) {
        try {
            return (Flags.showAppHandleLargeScreens() || enableBubbleToFullscreen())
                    && deviceHasLargeScreen(context);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @return If {@code true} we set opaque background for all freeform tasks to prevent freeform
     * tasks below from being visible if freeform task window above is translucent.
     * Otherwise if fluid resize is enabled, add a background to freeform tasks.
     */
    public static boolean shouldSetBackground(@NonNull TaskInfo taskInfo) {
        try {
            return taskInfo.isFreeform() && (!DesktopModeStatus.isVeiledResizeEnabled()
                || DesktopModeFlags.ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS.isTrue());
        } catch (Throwable e) {
            // LC-Ignored
            return false;
        }
    }

    /**
     * @return {@code true} if the app handle should be shown because desktop mode is enabled or
     * the device has a large screen
     */
    public static boolean canEnterDesktopModeOrShowAppHandle(@NonNull Context context) {
        return canEnterDesktopMode(context) || overridesShowAppHandle(context);
    }

    /**
     * Return {@code true} if the override desktop density is enabled and valid.
     */
    public static boolean useDesktopOverrideDensity() {
        return isDesktopDensityOverrideEnabled() && isValidDesktopDensityOverrideSet();
    }

    /**
     * Returns {@code true} if the app-to-web feature is using the build-time generic links list.
     */
    public static boolean useAppToWebBuildTimeGenericLinks() {
        return USE_APP_TO_WEB_BUILD_TIME_GENERIC_LINKS;
    }

    /**
     * Return {@code true} if the override desktop density is enabled.
     */
    private static boolean isDesktopDensityOverrideEnabled() {
        return DESKTOP_DENSITY_OVERRIDE_ENABLED;
    }

    /**
     * Return {@code true} if the override desktop density is set and within a valid range.
     */
    private static boolean isValidDesktopDensityOverrideSet() {
        return DESKTOP_DENSITY_OVERRIDE >= DESKTOP_DENSITY_MIN
                && DESKTOP_DENSITY_OVERRIDE <= DESKTOP_DENSITY_MAX;
    }

    /**
     * Return {@code true} if desktop mode is unrestricted and is supported on the device.
     */
    public static boolean isDeviceEligibleForDesktopMode(@NonNull Context context) {
        if (!enforceDeviceRestrictions()) {
            return true;
        }
        try {
            // If projected display is enabled, #canInternalDisplayHostDesktops is no longer a
            // requirement.
            final boolean desktopModeSupported = ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE.isTrue()
                ? isDesktopModeSupported(context) : (isDesktopModeSupported(context)
                && canInternalDisplayHostDesktops(context));
            final boolean desktopModeSupportedByDevOptions =
                Flags.enableDesktopModeThroughDevOption()
                    && isDesktopModeDevOptionSupported(context);
            return desktopModeSupported || desktopModeSupportedByDevOptions;
        } catch (Throwable e) {
            // LC-Ignored
            return false;
        }
    }

    /**
     * Return {@code true} if the developer option for desktop mode is unrestricted and is supported
     * in the device.
     *
     * Note that, if {@link #isDeviceEligibleForDesktopMode(Context)} is true, then
     * {@link #isDeviceEligibleForDesktopModeDevOption(Context)} is also true.
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
     * @return {@code true} if this device has an internal large screen
     */
    private static boolean deviceHasLargeScreen(@NonNull Context context) {
        if (sIsLargeScreenDevice == null) {
            sIsLargeScreenDevice = Arrays.stream(
                context.getSystemService(DisplayManager.class)
                        .getDisplays(DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED))
                .filter(display -> display.getType() == Display.TYPE_INTERNAL)
                .anyMatch(display -> display.getMinSizeDimensionDp()
                        >= WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP);
        }
        return sIsLargeScreenDevice;
    }

    /**
     * Return {@code true} if a display should enter desktop mode by default when the windowing mode
     * of the display's root [TaskDisplayArea] is set to WINDOWING_MODE_FREEFORM.
     */
    public static boolean enterDesktopByDefaultOnFreeformDisplay(@NonNull Context context) {
        try {
            if (!DesktopExperienceFlags.ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS.isTrue()) {
                return false;
            }
        } catch (Throwable e) {
            // LC-Ignored
            return false;
        }

        return SystemProperties.getBoolean(ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAY_SYS_PROP,
                context.getResources().getBoolean(
                        R.bool.config_enterDesktopByDefaultOnFreeformDisplay));
    }

    /**
     * Return {@code true} if a window should be maximized when it's dragged to the top edge of the
     * screen.
     */
    public static boolean shouldMaximizeWhenDragToTopEdge(@NonNull Context context) {
        try {
            if (!DesktopExperienceFlags.ENABLE_DRAG_TO_MAXIMIZE.isTrue()) {
                return false;
            }
        } catch (Throwable e) {
            // LC-Ignored
            return false;
        }
        return SystemProperties.getBoolean(ENABLE_DRAG_TO_MAXIMIZE_SYS_PROP,
                context.getResources().getBoolean(R.bool.config_dragToMaximizeInDesktopMode));
    }

    /** Dumps DesktopModeStatus flags and configs. */
    public static void dump(PrintWriter pw, String prefix, Context context) {
        String innerPrefix = prefix + "  ";
        pw.print(prefix); pw.println(TAG);
        pw.print(innerPrefix); pw.print("maxTaskLimit="); pw.println(getMaxTaskLimit(context));

        pw.print(innerPrefix); pw.print("maxTaskLimit config override=");
        pw.println(context.getResources().getInteger(
                R.integer.config_maxDesktopWindowingActiveTasks));

        SystemProperties.Handle maxTaskLimitHandle = SystemProperties.find(MAX_TASK_LIMIT_SYS_PROP);
        pw.print(innerPrefix); pw.print("maxTaskLimit sysprop=");
        pw.println(maxTaskLimitHandle == null ? "null" : maxTaskLimitHandle.getInt(/* def= */ -1));

        pw.print(innerPrefix); pw.print("showAppHandle config override=");
        pw.println(overridesShowAppHandle(context));
    }
}
