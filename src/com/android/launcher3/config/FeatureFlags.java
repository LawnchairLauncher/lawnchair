/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.config;

import static com.android.launcher3.config.FeatureFlags.BooleanFlag.DISABLED;
import static com.android.launcher3.config.FeatureFlags.BooleanFlag.ENABLED;
import static com.android.wm.shell.Flags.enableTaskbarNavbarUnification;
import static com.android.wm.shell.Flags.enableTaskbarOnPhones;

import android.content.res.Resources;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Utilities;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Flags;

import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import app.lawnchair.preferences2.PreferenceManager2;

/**
 * Defines a set of flags used to control various launcher behaviors.
 * <p>
 * <p>All the flags should be defined here with appropriate default values.
 */
public final class FeatureFlags {

    private FeatureFlags() { }

    /**
     * True when the build has come from Android Studio and is being used for local debugging.
     * @deprecated Use {@link BuildConfig#IS_STUDIO_BUILD} directly
     */
    @Deprecated
    public static final boolean IS_STUDIO_BUILD = false;

    /**
     * Enable moving the QSB on the 0th screen of the workspace. This is not a configuration feature
     * and should be modified at a project level.
     * @deprecated Use {@link BuildConfig#QSB_ON_FIRST_SCREEN} directly
     */
    @Deprecated
    public static final boolean QSB_ON_FIRST_SCREEN = true;

    /**
     * Feature flag to handle define config changes dynamically instead of killing the process.
     * <p>
     *
     * To add a new flag that can be toggled through the flags UI:
     * <p>
     * Declare a new ToggleableFlag below. Give it a unique key (e.g. "QSB_ON_FIRST_SCREEN"),
     * and set a default value for the flag. This will be the default value on Debug builds.
     * <p>
     */
    // TODO(Block 6): Clean up flags
    public static final BooleanFlag SECONDARY_DRAG_N_DROP_TO_PIN = getDebugFlag(270395140,
            "SECONDARY_DRAG_N_DROP_TO_PIN", DISABLED,
            "Enable dragging and dropping to pin apps within secondary display");

    // TODO(Block 8): Clean up flags

    // TODO(Block 9): Clean up flags
    public static final BooleanFlag MULTI_SELECT_EDIT_MODE = getDebugFlag(270709220,
            "MULTI_SELECT_EDIT_MODE", DISABLED, "Enable new multi-select edit mode "
                    + "for home screen");

    // TODO(Block 11): Clean up flags
    public static final BooleanFlag FOLDABLE_SINGLE_PAGE = getDebugFlag(270395274,
            "FOLDABLE_SINGLE_PAGE", DISABLED, "Use a single page for the workspace");

    // TODO(Block 12): Clean up flags
    public static final BooleanFlag ENABLE_MULTI_INSTANCE = getDebugFlag(270396680,
            "ENABLE_MULTI_INSTANCE", DISABLED,
            "Enables creation and filtering of multiple task instances in overview");

    // TODO(Block 13): Clean up flags
    public static final BooleanFlag ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING = getReleaseFlag(
            270391397, "ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING", DISABLED,
            "Allows on device search in all apps logging");

    // TODO(Block 14): Cleanup flags
    public static final BooleanFlag NOTIFY_CRASHES = getDebugFlag(270393108, "NOTIFY_CRASHES",
            DISABLED, "Sends a notification whenever launcher encounters an uncaught exception.");

    public static final boolean ENABLE_TASKBAR_NAVBAR_UNIFICATION =
            enableTaskbarNavbarUnification() && (!isPhone() || enableTaskbarOnPhones());

    private static boolean isPhone() {
        final boolean isPhone;
        int foldedDeviceStatesId = Resources.getSystem().getIdentifier(
                "config_foldedDeviceStates", "array", "android");
        if (foldedDeviceStatesId != 0) {
            isPhone = Resources.getSystem().getIntArray(foldedDeviceStatesId).length == 0;
        } else {
            isPhone = true;
        }
        return isPhone;
    }

    // Aconfig migration complete for ENABLE_TASKBAR_NO_RECREATION.
    public static final BooleanFlag ENABLE_TASKBAR_NO_RECREATION = getDebugFlag(299193589,
            "ENABLE_TASKBAR_NO_RECREATION", DISABLED,
            "Enables taskbar with no recreation from lifecycle changes of TaskbarActivityContext.");
    public static boolean enableTaskbarNoRecreate() {
        return ENABLE_TASKBAR_NO_RECREATION.get() || Flags.enableTaskbarNoRecreate()
                // Task bar pinning and task bar nav bar unification are both dependent on
                // ENABLE_TASKBAR_NO_RECREATION. We want to turn ENABLE_TASKBAR_NO_RECREATION on
                // when either of the dependent features is turned on.
                || enableTaskbarPinning() || ENABLE_TASKBAR_NAVBAR_UNIFICATION;
    }

    // TODO(Block 16): Clean up flags
    // When enabled the promise icon is visible in all apps while installation an app.
    public static final BooleanFlag PROMISE_APPS_IN_ALL_APPS = getDebugFlag(270390012,
            "PROMISE_APPS_IN_ALL_APPS", DISABLED, "Add promise icon in all-apps");

    // Aconfig migration complete for ENABLE_EXPANDING_PAUSE_WORK_BUTTON.
    public static final BooleanFlag ENABLE_EXPANDING_PAUSE_WORK_BUTTON = getDebugFlag(270390779,
            "ENABLE_EXPANDING_PAUSE_WORK_BUTTON", DISABLED,
            "Expand and collapse pause work button while scrolling");

    public static final BooleanFlag INJECT_FALLBACK_APP_CORPUS_RESULTS = getReleaseFlag(270391706,
            "INJECT_FALLBACK_APP_CORPUS_RESULTS", DISABLED,
            "Inject fallback app corpus result when AiAi fails to return it.");

    // TODO(Block 17): Clean up flags
    // Aconfig migration complete for ENABLE_TASKBAR_PINNING.
    private static final BooleanFlag ENABLE_TASKBAR_PINNING = getDebugFlag(296231746,
            "ENABLE_TASKBAR_PINNING", DISABLED,
            "Enables taskbar pinning to allow user to switch between transient and persistent "
                    + "taskbar flavors");

    public static boolean enableTaskbarPinning() {
        return ENABLE_TASKBAR_PINNING.get() || Flags.enableTaskbarPinning();
    }

    // TODO(Block 20): Clean up flags
    // Aconfig migration complete for ENABLE_HOME_TRANSITION_LISTENER.
    public static final BooleanFlag ENABLE_HOME_TRANSITION_LISTENER = getDebugFlag(306053414,
            "ENABLE_HOME_TRANSITION_LISTENER", DISABLED,
            "Enables launcher to listen to all transitions that include home activity.");

    public static boolean enableHomeTransitionListener() {
        return ENABLE_HOME_TRANSITION_LISTENER.get() || Flags.enableHomeTransitionListener();
    }

    // TODO(Block 21): Clean up flags
    public static final BooleanFlag ENABLE_APP_ICON_FOR_INLINE_SHORTCUTS = getDebugFlag(270395087,
            "ENABLE_APP_ICON_IN_INLINE_SHORTCUTS", DISABLED, "Show app icon for inline shortcut");

    // TODO(Block 22): Clean up flags
    public static final BooleanFlag ENABLE_WIDGET_TRANSITION_FOR_RESIZING = getDebugFlag(268553314,
            "ENABLE_WIDGET_TRANSITION_FOR_RESIZING", ENABLED,
            "Enable widget transition animation when resizing the widgets");

    // TODO(Block 27): Clean up flags
    public static final BooleanFlag ENABLE_OVERLAY_CONNECTION_OPTIM = getDebugFlag(270392629,
            "ENABLE_OVERLAY_CONNECTION_OPTIM", DISABLED,
            "Enable optimizing overlay service connection");

    /**
     * Enables region sampling for text color: Needs system health assessment before turning on
     */
    public static final BooleanFlag ENABLE_REGION_SAMPLING = getDebugFlag(270391669,
            "ENABLE_REGION_SAMPLING", DISABLED,
            "Enable region sampling to determine color of text on screen.");

    public static final BooleanFlag ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS =
            getDebugFlag(270393096, "ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS",
            DISABLED, "Always use hardware optimization for folder animations.");

    public static final BooleanFlag SEPARATE_RECENTS_ACTIVITY = getDebugFlag(270392980,
            "SEPARATE_RECENTS_ACTIVITY", DISABLED,
            "Uses a separate recents activity instead of using the integrated recents+Launcher UI");

    public static final BooleanFlag USE_LOCAL_ICON_OVERRIDES = getDebugFlag(270394973,
            "USE_LOCAL_ICON_OVERRIDES", ENABLED,
            "Use inbuilt monochrome icons if app doesn't provide one");

    // TODO(Block 29): Clean up flags
    // Aconfig migration complete for ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT.
    public static final BooleanFlag ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT = getDebugFlag(270393897,
            "ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT", DISABLED,
            "Enables displaying the all apps button in the hotseat.");

    public static boolean enableAllAppsButtonInHotseat() {
        return ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT.get() || Flags.enableAllAppsButtonInHotseat();
    }

    // TODO(Block 30): Clean up flags
    public static final BooleanFlag USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES = getDebugFlag(270395010,
            "USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES", DISABLED,
            "Use local overrides for search request timeout");

    // TODO(Block 31): Clean up flags

    // TODO(Block 32): Clean up flags
    // Aconfig migration complete for ENABLE_RESPONSIVE_WORKSPACE.
    @VisibleForTesting
    public static final BooleanFlag ENABLE_RESPONSIVE_WORKSPACE = getDebugFlag(241386436,
            "ENABLE_RESPONSIVE_WORKSPACE", ENABLED,
            "Enables new workspace grid calculations method.");
    public static boolean enableResponsiveWorkspace() {
        return ENABLE_RESPONSIVE_WORKSPACE.get() || Flags.enableResponsiveWorkspace();
    }

    public static BooleanFlag getDebugFlag(
            int bugId, String key, BooleanFlag flagState, String description) {
        return flagState;
    }

    public static BooleanFlag getReleaseFlag(
            int bugId, String key, BooleanFlag flagState, String description) {
        return flagState;
    }

    /**
     * Enabled state for a flag
     */
    public enum BooleanFlag {
        ENABLED(true),
        DISABLED(false);

        private final boolean mValue;

        BooleanFlag(boolean value) {
            mValue = value;
        }

        public boolean get() {
            return mValue;
        }
    }
}
