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

import static com.android.launcher3.uioverrides.flags.FlagsFactory.getDebugFlag;
import static com.android.launcher3.uioverrides.flags.FlagsFactory.getReleaseFlag;

import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Utilities;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Defines a set of flags used to control various launcher behaviors.
 *
 * <p>All the flags should be defined here with appropriate default values.
 */
public final class FeatureFlags {

    public static final String FLAGS_PREF_NAME = "featureFlags";

    @VisibleForTesting
    public static Predicate<BooleanFlag> sBooleanReader = f -> f.mCurrentValue;
    @VisibleForTesting
    public static ToIntFunction<IntFlag> sIntReader = f -> f.mCurrentValue;

    private FeatureFlags() { }

    public static boolean showFlagTogglerUi(Context context) {
        return BuildConfig.IS_DEBUG_DEVICE && Utilities.isDevelopersOptionsEnabled(context);
    }

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
     *
     *
     * To add a new flag that can be toggled through the flags UI:
     *
     * Declare a new ToggleableFlag below. Give it a unique key (e.g. "QSB_ON_FIRST_SCREEN"),
     * and set a default value for the flag. This will be the default value on Debug builds.
     */
    public static final BooleanFlag ENABLE_INPUT_CONSUMER_REASON_LOGGING = getDebugFlag(270390028,
            "ENABLE_INPUT_CONSUMER_REASON_LOGGING",
            true,
            "Log the reason why an Input Consumer was selected for a gesture.");

    public static final BooleanFlag ENABLE_GESTURE_ERROR_DETECTION = getDebugFlag(270389990,
            "ENABLE_GESTURE_ERROR_DETECTION",
            true,
            "Analyze gesture events and log detected errors");

    // When enabled the promise icon is visible in all apps while installation an app.
    public static final BooleanFlag PROMISE_APPS_IN_ALL_APPS = getDebugFlag(270390012,
            "PROMISE_APPS_IN_ALL_APPS", false, "Add promise icon in all-apps");

    public static final BooleanFlag KEYGUARD_ANIMATION = getDebugFlag(270390904,
            "KEYGUARD_ANIMATION", false, "Enable animation for keyguard going away on wallpaper");

    public static final BooleanFlag ENABLE_DEVICE_SEARCH = getReleaseFlag(270390907,
            "ENABLE_DEVICE_SEARCH", true, "Allows on device search in all apps");

    public static final BooleanFlag ENABLE_FLOATING_SEARCH_BAR =
            getDebugFlag(270390286, "ENABLE_FLOATING_SEARCH_BAR", false,
                    "Keep All Apps search bar at the bottom (but above keyboard if open)");

    public static final BooleanFlag ENABLE_HIDE_HEADER = getReleaseFlag(270390930,
            "ENABLE_HIDE_HEADER", true, "Hide header on keyboard before typing in all apps");

    public static final BooleanFlag ENABLE_EXPANDING_PAUSE_WORK_BUTTON = getReleaseFlag(270390779,
            "ENABLE_EXPANDING_PAUSE_WORK_BUTTON", false,
            "Expand and collapse pause work button while scrolling");

    public static final BooleanFlag ENABLE_RECENT_BLOCK = getDebugFlag(270390950,
            "ENABLE_RECENT_BLOCK", false, "Show recently tapped search target block in zero state");

    public static final BooleanFlag COLLECT_SEARCH_HISTORY = getReleaseFlag(270391455,
            "COLLECT_SEARCH_HISTORY", false, "Allow launcher to collect search history for log");

    public static final BooleanFlag ENABLE_TWOLINE_ALLAPPS = getDebugFlag(270390937,
            "ENABLE_TWOLINE_ALLAPPS", false, "Enables two line label inside all apps.");

    public static final BooleanFlag ENABLE_TWOLINE_DEVICESEARCH = getDebugFlag(201388851,
            "ENABLE_TWOLINE_DEVICESEARCH", false,
            "Enable two line label for icons with labels on device search.");

    public static final BooleanFlag ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING = getReleaseFlag(
            270391397, "ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING", false,
            "Allows on device search in all apps logging");

    public static final BooleanFlag IME_STICKY_SNACKBAR_EDU = getDebugFlag(270391693,
            "IME_STICKY_SNACKBAR_EDU", true, "Show sticky IME edu in AllApps");

    public static final BooleanFlag ENABLE_PEOPLE_TILE_PREVIEW = getDebugFlag(270391653,
            "ENABLE_PEOPLE_TILE_PREVIEW", false,
            "Experimental: Shows conversation shortcuts on home screen as search results");

    public static final BooleanFlag FOLDER_NAME_MAJORITY_RANKING = getDebugFlag(270391638,
            "FOLDER_NAME_MAJORITY_RANKING", true,
            "Suggests folder names based on majority based ranking.");

    public static final BooleanFlag INJECT_FALLBACK_APP_CORPUS_RESULTS = getReleaseFlag(270391706,
            "INJECT_FALLBACK_APP_CORPUS_RESULTS", false,
            "Inject fallback app corpus result when AiAi fails to return it.");

    public static final BooleanFlag ASSISTANT_GIVES_LAUNCHER_FOCUS = getDebugFlag(270391641,
            "ASSISTANT_GIVES_LAUNCHER_FOCUS", false,
            "Allow Launcher to handle nav bar gestures while Assistant is running over it");

    public static final BooleanFlag ENABLE_BULK_WORKSPACE_ICON_LOADING = getDebugFlag(270392203,
            "ENABLE_BULK_WORKSPACE_ICON_LOADING",
            true,
            "Enable loading workspace icons in bulk.");

    public static final BooleanFlag ENABLE_BULK_ALL_APPS_ICON_LOADING = getDebugFlag(270392465,
            "ENABLE_BULK_ALL_APPS_ICON_LOADING",
            true,
            "Enable loading all apps icons in bulk.");

    public static final BooleanFlag ENABLE_DATABASE_RESTORE = getDebugFlag(270392706,
            "ENABLE_DATABASE_RESTORE", false,
            "Enable database restore when new restore session is created");

    public static final BooleanFlag ENABLE_SMARTSPACE_DISMISS = getDebugFlag(270391664,
            "ENABLE_SMARTSPACE_DISMISS", true,
            "Adds a menu option to dismiss the current Enhanced Smartspace card.");

    public static final BooleanFlag ENABLE_OVERLAY_CONNECTION_OPTIM = getDebugFlag(270392629,
            "ENABLE_OVERLAY_CONNECTION_OPTIM",
            false,
            "Enable optimizing overlay service connection");

    /**
     * Enables region sampling for text color: Needs system health assessment before turning on
     */
    public static final BooleanFlag ENABLE_REGION_SAMPLING = getDebugFlag(270391669,
            "ENABLE_REGION_SAMPLING", false,
            "Enable region sampling to determine color of text on screen.");

    public static final BooleanFlag ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS =
            getDebugFlag(270393096,
                    "ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS", false,
                    "Always use hardware optimization for folder animations.");

    public static final BooleanFlag SEPARATE_RECENTS_ACTIVITY = getDebugFlag(270392980,
            "SEPARATE_RECENTS_ACTIVITY", false,
            "Uses a separate recents activity instead of using the integrated recents+Launcher UI");

    public static final BooleanFlag ENABLE_MINIMAL_DEVICE = getDebugFlag(270392984,
            "ENABLE_MINIMAL_DEVICE", false,
            "Allow user to toggle minimal device mode in launcher.");

    public static final BooleanFlag ENABLE_TASKBAR_POPUP_MENU = getDebugFlag(
            270392477, "ENABLE_TASKBAR_POPUP_MENU", true,
            "Enables long pressing taskbar icons to show the popup menu.");

    public static final BooleanFlag ENABLE_TWO_PANEL_HOME = getDebugFlag(270392643,
            "ENABLE_TWO_PANEL_HOME", true,
            "Uses two panel on home screen. Only applicable on large screen devices.");

    public static final BooleanFlag ENABLE_SCRIM_FOR_APP_LAUNCH = getDebugFlag(270393276,
            "ENABLE_SCRIM_FOR_APP_LAUNCH", false,
            "Enables scrim during app launch animation.");

    public static final BooleanFlag ENABLE_ENFORCED_ROUNDED_CORNERS = getReleaseFlag(270393258,
            "ENABLE_ENFORCED_ROUNDED_CORNERS", true, "Enforce rounded corners on all App Widgets");

    public static final BooleanFlag NOTIFY_CRASHES = getDebugFlag(
            270393108, "NOTIFY_CRASHES", false,
            "Sends a notification whenever launcher encounters an uncaught exception.");

    public static final BooleanFlag ENABLE_WALLPAPER_SCRIM = getDebugFlag(270393604,
            "ENABLE_WALLPAPER_SCRIM", false,
            "Enables scrim over wallpaper for text protection.");

    public static final BooleanFlag WIDGETS_IN_LAUNCHER_PREVIEW = getDebugFlag(270393268,
            "WIDGETS_IN_LAUNCHER_PREVIEW", true,
            "Enables widgets in Launcher preview for the Wallpaper app.");

    public static final BooleanFlag QUICK_WALLPAPER_PICKER = getDebugFlag(270393112,
            "QUICK_WALLPAPER_PICKER", true,
            "Shows quick wallpaper picker in long-press menu");

    public static final BooleanFlag ENABLE_BACK_SWIPE_HOME_ANIMATION = getDebugFlag(270393426,
            "ENABLE_BACK_SWIPE_HOME_ANIMATION", true,
            "Enables home animation to icon when user swipes back.");

    public static final BooleanFlag ENABLE_ICON_LABEL_AUTO_SCALING = getDebugFlag(270393294,
            "ENABLE_ICON_LABEL_AUTO_SCALING", true,
            "Enables scaling/spacing for icon labels to make more characters visible");

    public static final BooleanFlag ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT = getDebugFlag(270393897,
            "ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT", false,
            "Enables displaying the all apps button in the hotseat.");

    public static final BooleanFlag ENABLE_ALL_APPS_ONE_SEARCH_IN_TASKBAR = getDebugFlag(270393900,
            "ENABLE_ALL_APPS_ONE_SEARCH_IN_TASKBAR", false,
            "Enables One Search box in Taskbar All Apps.");

    public static final BooleanFlag ENABLE_SPLIT_FROM_WORKSPACE = getDebugFlag(270393906,
            "ENABLE_SPLIT_FROM_WORKSPACE", true,
            "Enable initiating split screen from workspace.");

    public static final BooleanFlag ENABLE_SPLIT_FROM_FULLSCREEN_WITH_KEYBOARD_SHORTCUTS =
            getDebugFlag(270394122, "ENABLE_SPLIT_FROM_FULLSCREEN_SHORTCUT", false,
                    "Enable splitting from fullscreen app with keyboard shortcuts");

    public static final BooleanFlag ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE = getDebugFlag(
            270393453, "ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE", false,
            "Enable initiating split screen from workspace to workspace.");

    public static final BooleanFlag ENABLE_NEW_MIGRATION_LOGIC = getDebugFlag(270393455,
            "ENABLE_NEW_MIGRATION_LOGIC", true,
            "Enable the new grid migration logic, keeping pages when src < dest");

    public static final BooleanFlag ENABLE_WIDGET_HOST_IN_BACKGROUND = getDebugFlag(270394384,
            "ENABLE_WIDGET_HOST_IN_BACKGROUND", false,
            "Enable background widget updates listening for widget holder");

    public static final BooleanFlag ENABLE_ONE_SEARCH_MOTION = getReleaseFlag(270394223,
            "ENABLE_ONE_SEARCH_MOTION", true, "Enables animations in OneSearch.");

    public static final BooleanFlag ENABLE_SEARCH_RESULT_BACKGROUND_DRAWABLES = getReleaseFlag(
            270394041, "ENABLE_SEARCH_RESULT_BACKGROUND_DRAWABLES", false,
            "Enable option to replace decorator-based search result backgrounds with drawables");

    public static final BooleanFlag ENABLE_SEARCH_RESULT_LAUNCH_TRANSITION = getReleaseFlag(
            270394392, "ENABLE_SEARCH_RESULT_LAUNCH_TRANSITION", false,
            "Enable option to launch search results using the new view container transitions");

    public static final BooleanFlag ENABLE_SHOW_KEYBOARD_OPTION_IN_ALL_APPS = getReleaseFlag(
            270394468, "ENABLE_SHOW_KEYBOARD_OPTION_IN_ALL_APPS", true,
            "Enable option to show keyboard when going to all-apps");

    public static final BooleanFlag USE_LOCAL_ICON_OVERRIDES = getDebugFlag(270394973,
            "USE_LOCAL_ICON_OVERRIDES", true,
            "Use inbuilt monochrome icons if app doesn't provide one");

    public static final BooleanFlag ENABLE_DISMISS_PREDICTION_UNDO = getDebugFlag(270394476,
            "ENABLE_DISMISS_PREDICTION_UNDO", false,
            "Show an 'Undo' snackbar when users dismiss a predicted hotseat item");

    public static final BooleanFlag ENABLE_CACHED_WIDGET = getDebugFlag(270395008,
            "ENABLE_CACHED_WIDGET", true,
            "Show previously cached widgets as opposed to deferred widget where available");

    public static final BooleanFlag USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES = getDebugFlag(270395010,
            "USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES", false,
            "Use local overrides for search request timeout");

    public static final BooleanFlag CONTINUOUS_VIEW_TREE_CAPTURE = getDebugFlag(270395171,
            "CONTINUOUS_VIEW_TREE_CAPTURE", false, "Capture View tree every frame");

    public static final BooleanFlag FOLDABLE_WORKSPACE_REORDER = getDebugFlag(270395070,
            "FOLDABLE_WORKSPACE_REORDER", true,
            "In foldables, when reordering the icons and widgets, is now going to use both sides");

    public static final BooleanFlag ENABLE_MULTI_DISPLAY_PARTIAL_DEPTH = getDebugFlag(270395073,
            "ENABLE_MULTI_DISPLAY_PARTIAL_DEPTH", false,
            "Allow bottom sheet depth to be smaller than 1 for multi-display devices.");

    public static final BooleanFlag SCROLL_TOP_TO_RESET = getReleaseFlag(
            270395177, "SCROLL_TOP_TO_RESET", true,
            "Bring up IME and focus on input when scroll to top if 'Always show keyboard'"
                    + " is enabled or in prefix state");

    public static final BooleanFlag ENABLE_MATERIAL_U_POPUP = getDebugFlag(270395516,
            "ENABLE_MATERIAL_U_POPUP", false, "Switch popup UX to use material U");

    public static final BooleanFlag ENABLE_SEARCH_UNINSTALLED_APPS = getReleaseFlag(270395269,
            "ENABLE_SEARCH_UNINSTALLED_APPS", false, "Search uninstalled app results.");

    public static final BooleanFlag SHOW_HOME_GARDENING = getDebugFlag(270395183,
            "SHOW_HOME_GARDENING", false,
            "Show the new home gardening mode");

    public static final BooleanFlag HOME_GARDENING_WORKSPACE_BUTTONS = getDebugFlag(270395133,
            "HOME_GARDENING_WORKSPACE_BUTTONS", false,
            "Change workspace edit buttons to reflect home gardening");

    public static final BooleanFlag ENABLE_DOWNLOAD_APP_UX_V2 = getReleaseFlag(270395134,
            "ENABLE_DOWNLOAD_APP_UX_V2", true, "Updates the download app UX"
                    + " to have better visuals");

    public static final BooleanFlag ENABLE_DOWNLOAD_APP_UX_V3 = getDebugFlag(270395186,
            "ENABLE_DOWNLOAD_APP_UX_V3", false, "Updates the download app UX"
                    + " to have better visuals, improve contrast, and color");

    public static final BooleanFlag FORCE_PERSISTENT_TASKBAR = getDebugFlag(270395077,
            "FORCE_PERSISTENT_TASKBAR", false, "Forces taskbar to be persistent, even in gesture"
                    + " nav mode and when transient taskbar is enabled.");

    public static final BooleanFlag FOLDABLE_SINGLE_PAGE = getDebugFlag(270395274,
            "FOLDABLE_SINGLE_PAGE", false,
            "Use a single page for the workspace");

    public static final BooleanFlag ENABLE_TRANSIENT_TASKBAR = getDebugFlag(270395798,
            "ENABLE_TRANSIENT_TASKBAR", true, "Enables transient taskbar.");

    public static final BooleanFlag SECONDARY_DRAG_N_DROP_TO_PIN = getDebugFlag(270395140,
            "SECONDARY_DRAG_N_DROP_TO_PIN", false,
            "Enable dragging and dropping to pin apps within secondary display");

    public static final BooleanFlag ENABLE_ICON_IN_TEXT_HEADER = getDebugFlag(270395143,
            "ENABLE_ICON_IN_TEXT_HEADER", false, "Show icon in textheader");

    public static final BooleanFlag ENABLE_APP_ICON_FOR_INLINE_SHORTCUTS = getDebugFlag(270395087,
            "ENABLE_APP_ICON_IN_INLINE_SHORTCUTS", false, "Show app icon for inline shortcut");

    public static final BooleanFlag SHOW_DOT_PAGINATION = getDebugFlag(270395278,
            "SHOW_DOT_PAGINATION", false, "Enable showing dot pagination in workspace");

    public static final BooleanFlag LARGE_SCREEN_WIDGET_PICKER = getDebugFlag(270395809,
            "LARGE_SCREEN_WIDGET_PICKER", false, "Enable new widget picker that takes "
                    + "advantage of large screen format");

    public static final BooleanFlag ENABLE_NEW_GESTURE_NAV_TUTORIAL = getDebugFlag(270396257,
            "ENABLE_NEW_GESTURE_NAV_TUTORIAL", false,
            "Enable the redesigned gesture navigation tutorial");

    public static final BooleanFlag ENABLE_LAUNCH_FROM_STAGED_APP = getDebugFlag(270395567,
            "ENABLE_LAUNCH_FROM_STAGED_APP", true,
            "Enable the ability to tap a staged app during split select to launch it in full screen"
    );

    public static final BooleanFlag ENABLE_PREMIUM_HAPTICS_ALL_APPS = getDebugFlag(270396358,
            "ENABLE_PREMIUM_HAPTICS_ALL_APPS", false,
            "Enables haptics opening/closing All apps");

    public static final BooleanFlag ENABLE_FORCED_MONO_ICON = getDebugFlag(270396209,
            "ENABLE_FORCED_MONO_ICON", false,
            "Enable the ability to generate monochromatic icons, if it is not provided by the app"
    );

    public static final BooleanFlag ENABLE_TASKBAR_EDU_TOOLTIP = getDebugFlag(270396268,
            "ENABLE_TASKBAR_EDU_TOOLTIP", true,
            "Enable the tooltip version of the Taskbar education flow.");

    public static final BooleanFlag ENABLE_MULTI_INSTANCE = getDebugFlag(270396680,
            "ENABLE_MULTI_INSTANCE", false,
            "Enables creation and filtering of multiple task instances in overview");

    public static final BooleanFlag ENABLE_TASKBAR_PINNING = getDebugFlag(270396583,
            "ENABLE_TASKBAR_PINNING", false,
            "Enables taskbar pinning to allow user to switch between transient and persistent "
                    + "taskbar flavors");

    public static final BooleanFlag ENABLE_WORKSPACE_LOADING_OPTIMIZATION = getDebugFlag(251502424,
            "ENABLE_WORKSPACE_LOADING_OPTIMIZATION", false, "load the current workspace screen "
                    + "visible to the user before the rest rather than loading all of them at once."
    );

    public static final BooleanFlag ENABLE_GRID_ONLY_OVERVIEW = getDebugFlag(270397206,
            "ENABLE_GRID_ONLY_OVERVIEW", false,
            "Enable a grid-only overview without a focused task.");

    public static final BooleanFlag RECEIVE_UNFOLD_EVENTS_FROM_SYSUI = getDebugFlag(270397209,
            "RECEIVE_UNFOLD_EVENTS_FROM_SYSUI", true,
            "Enables receiving unfold animation events from sysui instead of calculating "
                    + "them in launcher process using hinge sensor values.");

    public static final BooleanFlag ENABLE_KEYBOARD_QUICK_SWITCH = getDebugFlag(270396844,
            "ENABLE_KEYBOARD_QUICK_SWITCH", false,
            "Enables keyboard quick switching");

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
}
