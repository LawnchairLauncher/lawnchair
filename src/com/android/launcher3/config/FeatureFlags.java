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

import static com.android.launcher3.BuildConfig.WIDGET_ON_FIRST_SCREEN;
import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_EXTRA_TOUCH_WIDTH_DP;
import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_DELAY;
import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_END_SCALE_PERCENT;
import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_ITERATIONS;
import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_SCALE_EXPONENT;
import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_START_SCALE_PERCENT;
import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_SLOP_PERCENTAGE;
import static com.android.launcher3.LauncherPrefs.LONG_PRESS_NAV_HANDLE_TIMEOUT_MS;
import static com.android.launcher3.config.FeatureFlags.FlagState.DISABLED;
import static com.android.launcher3.config.FeatureFlags.FlagState.ENABLED;
import static com.android.launcher3.config.FeatureFlags.FlagState.TEAMFOOD;
import static com.android.launcher3.uioverrides.flags.FlagsFactory.getDebugFlag;
import static com.android.launcher3.uioverrides.flags.FlagsFactory.getReleaseFlag;
import static com.android.wm.shell.Flags.enableTaskbarNavbarUnification;

import android.content.res.Resources;
import android.view.ViewConfiguration;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Flags;
import com.android.launcher3.uioverrides.flags.FlagsFactory;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Defines a set of flags used to control various launcher behaviors.
 * <p>
 * <p>All the flags should be defined here with appropriate default values.
 */
public final class FeatureFlags {

    @VisibleForTesting
    public static Predicate<BooleanFlag> sBooleanReader = f -> f.mCurrentValue;
    @VisibleForTesting
    public static ToIntFunction<IntFlag> sIntReader = f -> f.mCurrentValue;

    private FeatureFlags() { }

    /**
     * True when the build has come from Android Studio and is being used for local debugging.
     * @deprecated Use {@link BuildConfig#IS_STUDIO_BUILD} directly
     */
    @Deprecated
    public static final boolean IS_STUDIO_BUILD = BuildConfig.IS_STUDIO_BUILD;

    /**
     * Enable moving the QSB on the 0th screen of the workspace. This is not a configuration feature
     * and should be modified at a project level.
     * @deprecated Use {@link BuildConfig#QSB_ON_FIRST_SCREEN} directly
     */
    @Deprecated
    public static final boolean QSB_ON_FIRST_SCREEN = BuildConfig.QSB_ON_FIRST_SCREEN;

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
    // TODO(Block 1): Clean up flags
    public static final BooleanFlag ENABLE_SEARCH_RESULT_BACKGROUND_DRAWABLES = getReleaseFlag(
            270394041, "ENABLE_SEARCH_RESULT_BACKGROUND_DRAWABLES", ENABLED,
            "Enable option to replace decorator-based search result backgrounds with drawables");

    public static final BooleanFlag ENABLE_SEARCH_RESULT_LAUNCH_TRANSITION = getReleaseFlag(
            270394392, "ENABLE_SEARCH_RESULT_LAUNCH_TRANSITION", ENABLED,
            "Enable option to launch search results using the new view container transitions");

    // TODO(Block 2): Clean up flags
    public static final BooleanFlag ENABLE_MULTI_DISPLAY_PARTIAL_DEPTH = getDebugFlag(270395073,
            "ENABLE_MULTI_DISPLAY_PARTIAL_DEPTH", DISABLED,
            "Allow bottom sheet depth to be smaller than 1 for multi-display devices.");

    // TODO(Block 3): Clean up flags
    public static final BooleanFlag ENABLE_DISMISS_PREDICTION_UNDO = getDebugFlag(270394476,
            "ENABLE_DISMISS_PREDICTION_UNDO", DISABLED,
            "Show an 'Undo' snackbar when users dismiss a predicted hotseat item");

    public static final BooleanFlag MOVE_STARTUP_DATA_TO_DEVICE_PROTECTED_STORAGE = getDebugFlag(
            251502424, "ENABLE_BOOT_AWARE_STARTUP_DATA", DISABLED,
            "Marks LauncherPref data as (and allows it to) available while the device is"
                    + " locked. Enabling this causes a 1-time movement of certain SharedPreferences"
                    + " data. Improves startup latency.");

    public static final BooleanFlag CONTINUOUS_VIEW_TREE_CAPTURE = getDebugFlag(270395171,
            "CONTINUOUS_VIEW_TREE_CAPTURE", ENABLED, "Capture View tree every frame");

    public static final BooleanFlag ENABLE_WORKSPACE_LOADING_OPTIMIZATION = getDebugFlag(251502424,
            "ENABLE_WORKSPACE_LOADING_OPTIMIZATION", DISABLED,
            "load the current workspace screen visible to the user before the rest rather than "
                    + "loading all of them at once.");

    public static final BooleanFlag CHANGE_MODEL_DELEGATE_LOADING_ORDER = getDebugFlag(251502424,
            "CHANGE_MODEL_DELEGATE_LOADING_ORDER", DISABLED,
            "changes the timing of the loading and binding of delegate items during "
                    + "data preparation for loading the home screen");

    // TODO(Block 4): Cleanup flags
    public static final BooleanFlag ENABLE_FLOATING_SEARCH_BAR =
            getReleaseFlag(268388460, "ENABLE_FLOATING_SEARCH_BAR", DISABLED,
                    "Allow search bar to persist and animate across states, and attach to"
                            + " the keyboard from the bottom of the screen");

    public static final BooleanFlag ENABLE_ALL_APPS_FROM_OVERVIEW =
            getDebugFlag(275132633, "ENABLE_ALL_APPS_FROM_OVERVIEW", DISABLED,
                    "Allow entering All Apps from Overview (e.g. long swipe up from app)");

    public static final BooleanFlag CUSTOM_LPNH_THRESHOLDS =
            getReleaseFlag(301680992, "CUSTOM_LPNH_THRESHOLDS", DISABLED,
                    "Add dev options to customize the LPNH trigger slop and milliseconds");

    public static final BooleanFlag ANIMATE_LPNH =
            getReleaseFlag(308693847, "ANIMATE_LPNH", TEAMFOOD,
                    "Animates navbar when long pressing");

    public static final BooleanFlag SHRINK_NAV_HANDLE_ON_PRESS =
            getReleaseFlag(314158312, "SHRINK_NAV_HANDLE_ON_PRESS", DISABLED,
                    "Shrinks navbar when long pressing if ANIMATE_LPNH is enabled");

    public static final IntFlag LPNH_SLOP_PERCENTAGE =
            FlagsFactory.getIntFlag(301680992, "LPNH_SLOP_PERCENTAGE", 100,
                    "Controls touch slop percentage for lpnh",
                    LONG_PRESS_NAV_HANDLE_SLOP_PERCENTAGE);

    public static final IntFlag LPNH_EXTRA_TOUCH_WIDTH_DP =
            FlagsFactory.getIntFlag(301680992, "LPNH_EXTRA_TOUCH_WIDTH_DP", 0,
                    "Controls extra dp on the nav bar sides to trigger LPNH."
                            + " Can be negative for a smaller touch region.",
                    LONG_PRESS_NAV_HANDLE_EXTRA_TOUCH_WIDTH_DP);

    public static final IntFlag LPNH_TIMEOUT_MS =
            FlagsFactory.getIntFlag(301680992, "LPNH_TIMEOUT_MS",
                    ViewConfiguration.getLongPressTimeout(),
                    "Controls lpnh timeout in milliseconds", LONG_PRESS_NAV_HANDLE_TIMEOUT_MS);

    public static final BooleanFlag ENABLE_SHOW_KEYBOARD_OPTION_IN_ALL_APPS = getReleaseFlag(
            270394468, "ENABLE_SHOW_KEYBOARD_OPTION_IN_ALL_APPS", ENABLED,
            "Enable option to show keyboard when going to all-apps");

    // TODO(Block 5): Clean up flags
    public static final BooleanFlag ENABLE_TWOLINE_DEVICESEARCH = getDebugFlag(201388851,
            "ENABLE_TWOLINE_DEVICESEARCH", DISABLED,
            "Enable two line label for icons with labels on device search.");

    public static final BooleanFlag ENABLE_ICON_IN_TEXT_HEADER = getDebugFlag(270395143,
            "ENABLE_ICON_IN_TEXT_HEADER", DISABLED, "Show icon in textheader");

    public static final BooleanFlag ENABLE_PREMIUM_HAPTICS_ALL_APPS = getDebugFlag(270396358,
            "ENABLE_PREMIUM_HAPTICS_ALL_APPS", DISABLED,
            "Enables haptics opening/closing All apps");

    // TODO(Block 6): Clean up flags
    public static final BooleanFlag ENABLE_ALL_APPS_SEARCH_IN_TASKBAR = getDebugFlag(270393900,
            "ENABLE_ALL_APPS_SEARCH_IN_TASKBAR", ENABLED,
            "Enables Search box in Taskbar All Apps.");

    public static final BooleanFlag SECONDARY_DRAG_N_DROP_TO_PIN = getDebugFlag(270395140,
            "SECONDARY_DRAG_N_DROP_TO_PIN", DISABLED,
            "Enable dragging and dropping to pin apps within secondary display");

    // TODO(Block 8): Clean up flags

    // TODO(Block 9): Clean up flags
    public static final BooleanFlag MULTI_SELECT_EDIT_MODE = getDebugFlag(270709220,
            "MULTI_SELECT_EDIT_MODE", DISABLED, "Enable new multi-select edit mode "
                    + "for home screen");

    public static final BooleanFlag SMARTSPACE_AS_A_WIDGET = getDebugFlag(299181941,
            "SMARTSPACE_AS_A_WIDGET", DISABLED, "Enable SmartSpace as a widget");

    public static boolean shouldShowFirstPageWidget() {
        return SMARTSPACE_AS_A_WIDGET.get() && WIDGET_ON_FIRST_SCREEN;
    }

    public static final BooleanFlag ENABLE_SMARTSPACE_REMOVAL = getDebugFlag(290799975,
            "ENABLE_SMARTSPACE_REMOVAL", DISABLED, "Enable SmartSpace removal for "
            + "home screen");

    // TODO(Block 11): Clean up flags
    public static final BooleanFlag FOLDABLE_SINGLE_PAGE = getDebugFlag(270395274,
            "FOLDABLE_SINGLE_PAGE", DISABLED, "Use a single page for the workspace");

    public static final BooleanFlag ENABLE_PARAMETRIZE_REORDER = getDebugFlag(289420844,
            "ENABLE_PARAMETRIZE_REORDER", DISABLED,
            "Enables generating the reorder using a set of parameters");

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
            TEAMFOOD, "Sends a notification whenever launcher encounters an uncaught exception.");

    public static final boolean ENABLE_TASKBAR_NAVBAR_UNIFICATION =
            enableTaskbarNavbarUnification() && !isPhone();

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

    public static final BooleanFlag KEYGUARD_ANIMATION = getDebugFlag(270390904,
            "KEYGUARD_ANIMATION", DISABLED,
            "Enable animation for keyguard going away on wallpaper");

    public static final BooleanFlag ENABLE_DEVICE_SEARCH = getReleaseFlag(270390907,
            "ENABLE_DEVICE_SEARCH", ENABLED, "Allows on device search in all apps");

    public static final BooleanFlag ENABLE_HIDE_HEADER = getReleaseFlag(270390930,
            "ENABLE_HIDE_HEADER", ENABLED, "Hide header on keyboard before typing in all apps");

    // Aconfig migration complete for ENABLE_EXPANDING_PAUSE_WORK_BUTTON.
    public static final BooleanFlag ENABLE_EXPANDING_PAUSE_WORK_BUTTON = getDebugFlag(270390779,
            "ENABLE_EXPANDING_PAUSE_WORK_BUTTON", DISABLED,
            "Expand and collapse pause work button while scrolling");

    public static final BooleanFlag COLLECT_SEARCH_HISTORY = getReleaseFlag(270391455,
            "COLLECT_SEARCH_HISTORY", DISABLED, "Allow launcher to collect search history for log");

    // Aconfig migration complete for ENABLE_TWOLINE_ALLAPPS.
    public static final BooleanFlag ENABLE_TWOLINE_ALLAPPS = getDebugFlag(270390937,
            "ENABLE_TWOLINE_ALLAPPS", DISABLED, "Enables two line label inside all apps.");

    public static final BooleanFlag IME_STICKY_SNACKBAR_EDU = getDebugFlag(270391693,
            "IME_STICKY_SNACKBAR_EDU", ENABLED, "Show sticky IME edu in AllApps");

    public static final BooleanFlag FOLDER_NAME_MAJORITY_RANKING = getDebugFlag(270391638,
            "FOLDER_NAME_MAJORITY_RANKING", ENABLED,
            "Suggests folder names based on majority based ranking.");

    public static final BooleanFlag INJECT_FALLBACK_APP_CORPUS_RESULTS = getReleaseFlag(270391706,
            "INJECT_FALLBACK_APP_CORPUS_RESULTS", DISABLED,
            "Inject fallback app corpus result when AiAi fails to return it.");

    public static final BooleanFlag ENABLE_LONG_PRESS_NAV_HANDLE =
            getReleaseFlag(299682306, "ENABLE_LONG_PRESS_NAV_HANDLE", ENABLED,
                    "Enables long pressing on the bottom bar nav handle to trigger events.");

    public static final BooleanFlag ENABLE_SEARCH_HAPTIC_HINT =
            getReleaseFlag(314005131, "ENABLE_SEARCH_HAPTIC_HINT", ENABLED,
                    "Enables haptic hint while long pressing on the bottom bar nav handle.");

    public static final BooleanFlag ENABLE_SEARCH_HAPTIC_COMMIT =
            getReleaseFlag(314005577, "ENABLE_SEARCH_HAPTIC_COMMIT", ENABLED,
                    "Enables haptic hint at end of long pressing on the bottom bar nav handle.");

    public static final IntFlag LPNH_HAPTIC_HINT_START_SCALE_PERCENT =
            FlagsFactory.getIntFlag(309972570,
                    "LPNH_HAPTIC_HINT_START_SCALE_PERCENT", 0,
                    "Haptic hint start scale.",
                    LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_START_SCALE_PERCENT);

    public static final IntFlag LPNH_HAPTIC_HINT_END_SCALE_PERCENT =
            FlagsFactory.getIntFlag(309972570,
                    "LPNH_HAPTIC_HINT_END_SCALE_PERCENT", 100,
                    "Haptic hint end scale.", LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_END_SCALE_PERCENT);

    public static final IntFlag LPNH_HAPTIC_HINT_SCALE_EXPONENT =
            FlagsFactory.getIntFlag(309972570,
                    "LPNH_HAPTIC_HINT_SCALE_EXPONENT", 1,
                    "Haptic hint scale exponent.",
                    LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_SCALE_EXPONENT);

    public static final IntFlag LPNH_HAPTIC_HINT_ITERATIONS =
            FlagsFactory.getIntFlag(309972570, "LPNH_HAPTIC_HINT_ITERATIONS",
                    50,
                    "Haptic hint number of iterations.",
                    LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_ITERATIONS);

    public static final BooleanFlag ENABLE_LPNH_DEEP_PRESS =
            getReleaseFlag(310952290, "ENABLE_LPNH_DEEP_PRESS", ENABLED,
                    "Long press of nav handle is instantly triggered if deep press is detected.");

    public static final IntFlag LPNH_HAPTIC_HINT_DELAY =
            FlagsFactory.getIntFlag(309972570, "LPNH_HAPTIC_HINT_DELAY", 0,
                    "Delay before haptic hint starts.", LONG_PRESS_NAV_HANDLE_HAPTIC_HINT_DELAY);

    // TODO(Block 17): Clean up flags
    // Aconfig migration complete for ENABLE_TASKBAR_PINNING.
    private static final BooleanFlag ENABLE_TASKBAR_PINNING = getDebugFlag(296231746,
            "ENABLE_TASKBAR_PINNING", TEAMFOOD,
            "Enables taskbar pinning to allow user to switch between transient and persistent "
                    + "taskbar flavors");

    public static boolean enableTaskbarPinning() {
        return ENABLE_TASKBAR_PINNING.get() || Flags.enableTaskbarPinning();
    }

    // Aconfig migration complete for ENABLE_APP_PAIRS.
    public static final BooleanFlag ENABLE_APP_PAIRS = getDebugFlag(274189428,
            "ENABLE_APP_PAIRS", DISABLED,
            "Enables the ability to create and save app pairs on the Home screen for easy"
                    + " split screen launching.");
    public static boolean enableAppPairs() {
        return ENABLE_APP_PAIRS.get() || com.android.wm.shell.Flags.enableAppPairs();
    }

    // TODO(Block 19): Clean up flags
    public static final BooleanFlag SCROLL_TOP_TO_RESET = getReleaseFlag(270395177,
            "SCROLL_TOP_TO_RESET", ENABLED,
            "Bring up IME and focus on input when scroll to top if 'Always show keyboard'"
                    + " is enabled or in prefix state");

    public static final BooleanFlag ENABLE_SEARCH_UNINSTALLED_APPS = getReleaseFlag(270395269,
            "ENABLE_SEARCH_UNINSTALLED_APPS", ENABLED, "Search uninstalled app results.");

    // TODO(Block 20): Clean up flags
    public static final BooleanFlag ENABLE_SCRIM_FOR_APP_LAUNCH = getDebugFlag(270393276,
            "ENABLE_SCRIM_FOR_APP_LAUNCH", DISABLED, "Enables scrim during app launch animation.");

    public static final BooleanFlag ENABLE_BACK_SWIPE_HOME_ANIMATION = getDebugFlag(270393426,
            "ENABLE_BACK_SWIPE_HOME_ANIMATION", ENABLED,
            "Enables home animation to icon when user swipes back.");

    public static final BooleanFlag ENABLE_DYNAMIC_TASKBAR_THRESHOLDS = getDebugFlag(294252473,
            "ENABLE_DYNAMIC_TASKBAR_THRESHOLDS", ENABLED,
            "Enables taskbar thresholds that scale based on screen size.");

    // Aconfig migration complete for ENABLE_HOME_TRANSITION_LISTENER.
    public static final BooleanFlag ENABLE_HOME_TRANSITION_LISTENER = getDebugFlag(306053414,
            "ENABLE_HOME_TRANSITION_LISTENER", TEAMFOOD,
            "Enables launcher to listen to all transitions that include home activity.");

    public static boolean enableHomeTransitionListener() {
        return ENABLE_HOME_TRANSITION_LISTENER.get() || Flags.enableHomeTransitionListener();
    }

    // TODO(Block 21): Clean up flags
    public static final BooleanFlag ENABLE_APP_ICON_FOR_INLINE_SHORTCUTS = getDebugFlag(270395087,
            "ENABLE_APP_ICON_IN_INLINE_SHORTCUTS", DISABLED, "Show app icon for inline shortcut");

    // TODO(Block 22): Clean up flags
    public static final BooleanFlag ENABLE_WIDGET_TRANSITION_FOR_RESIZING = getDebugFlag(268553314,
            "ENABLE_WIDGET_TRANSITION_FOR_RESIZING", DISABLED,
            "Enable widget transition animation when resizing the widgets");

    public static final BooleanFlag PREEMPTIVE_UNFOLD_ANIMATION_START = getDebugFlag(270397209,
            "PREEMPTIVE_UNFOLD_ANIMATION_START", ENABLED,
            "Enables starting the unfold animation preemptively when unfolding, without"
                    + "waiting for SystemUI and then merging the SystemUI progress whenever we "
                    + "start receiving the events");

    // TODO(Block 24): Clean up flags
    public static final BooleanFlag ENABLE_NEW_MIGRATION_LOGIC = getDebugFlag(270393455,
            "ENABLE_NEW_MIGRATION_LOGIC", ENABLED,
            "Enable the new grid migration logic, keeping pages when src < dest");

    // TODO(Block 25): Clean up flags
    public static final BooleanFlag ENABLE_NEW_GESTURE_NAV_TUTORIAL = getDebugFlag(270396257,
            "ENABLE_NEW_GESTURE_NAV_TUTORIAL", ENABLED,
            "Enable the redesigned gesture navigation tutorial");

    // TODO(Block 26): Clean up flags
    public static final BooleanFlag ENABLE_WIDGET_HOST_IN_BACKGROUND = getDebugFlag(270394384,
            "ENABLE_WIDGET_HOST_IN_BACKGROUND", ENABLED,
            "Enable background widget updates listening for widget holder");

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

    public static final BooleanFlag ENABLE_ENFORCED_ROUNDED_CORNERS = getReleaseFlag(270393258,
            "ENABLE_ENFORCED_ROUNDED_CORNERS", ENABLED,
            "Enforce rounded corners on all App Widgets");

    public static final BooleanFlag USE_LOCAL_ICON_OVERRIDES = getDebugFlag(270394973,
            "USE_LOCAL_ICON_OVERRIDES", ENABLED,
            "Use inbuilt monochrome icons if app doesn't provide one");

    // Aconfig migration complete for ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE.
    public static final BooleanFlag ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE = getDebugFlag(
            270393453, "ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE", DISABLED,
            "Enable initiating split screen from workspace to workspace.");
    public static boolean enableSplitContextually() {
        return ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE.get() ||
                com.android.wm.shell.Flags.enableSplitContextual();
    }

    public static final BooleanFlag ENABLE_TRACKPAD_GESTURE = getDebugFlag(271010401,
            "ENABLE_TRACKPAD_GESTURE", ENABLED, "Enables trackpad gesture.");

    // TODO(Block 29): Clean up flags
    public static final BooleanFlag ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT = getDebugFlag(270393897,
            "ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT", DISABLED,
            "Enables displaying the all apps button in the hotseat.");

    public static final BooleanFlag ENABLE_KEYBOARD_QUICK_SWITCH = getDebugFlag(270396844,
            "ENABLE_KEYBOARD_QUICK_SWITCH", ENABLED, "Enables keyboard quick switching");

    public static final BooleanFlag ENABLE_KEYBOARD_TASKBAR_TOGGLE = getDebugFlag(281726846,
            "ENABLE_KEYBOARD_TASKBAR_TOGGLE", ENABLED,
            "Enables keyboard taskbar stash toggling");

    // TODO(Block 30): Clean up flags
    public static final BooleanFlag USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES = getDebugFlag(270395010,
            "USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES", DISABLED,
            "Use local overrides for search request timeout");

    // TODO(Block 31): Clean up flags

    // TODO(Block 32): Clean up flags
    // Aconfig migration complete for ENABLE_RESPONSIVE_WORKSPACE.
    @VisibleForTesting
    public static final BooleanFlag ENABLE_RESPONSIVE_WORKSPACE = getDebugFlag(241386436,
            "ENABLE_RESPONSIVE_WORKSPACE", TEAMFOOD,
            "Enables new workspace grid calculations method.");
    public static boolean enableResponsiveWorkspace() {
        return ENABLE_RESPONSIVE_WORKSPACE.get() || Flags.enableResponsiveWorkspace();
    }

    // TODO(Block 33): Clean up flags
    public static final BooleanFlag ENABLE_ALL_APPS_RV_PREINFLATION = getDebugFlag(288161355,
            "ENABLE_ALL_APPS_RV_PREINFLATION", ENABLED,
            "Enables preinflating all apps icons to avoid scrolling jank.");
    public static final BooleanFlag ALL_APPS_GONE_VISIBILITY = getDebugFlag(291651514,
            "ALL_APPS_GONE_VISIBILITY", ENABLED,
            "Set all apps container view's hidden visibility to GONE instead of INVISIBLE.");

    // TODO(Block 34): Empty block
    // Please only add flags to your assigned block. If you do not have a block:
    // 1. Assign yourself this block
    // 2. Add your flag to this block
    // 3. Add a new empty block below this one
    // 4. Move this comment to that new empty block
    // This is all to prevent merge conflicts in the future and help keep track of who owns which
    // flags.
    // List of assigned blocks can be found: http://go/gnl-flags-block-directory

    public static class BooleanFlag {

        private final boolean mCurrentValue;

        public BooleanFlag(boolean currentValue) {
            mCurrentValue = currentValue;
        }

        public boolean get() {
            return sBooleanReader.test(this);
        }
    }

    /**
     * Class representing an integer flag
     */
    public static class IntFlag {

        private final int mCurrentValue;

        public IntFlag(int currentValue) {
            mCurrentValue = currentValue;
        }

        public int get() {
            return sIntReader.applyAsInt(this);
        }
    }

    /**
     * Enabled state for a flag
     */
    public enum FlagState {
        ENABLED,
        DISABLED,
        TEAMFOOD    // Enabled in team food
    }
}
