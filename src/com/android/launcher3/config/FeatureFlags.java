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

import android.content.Context;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.DeviceFlag;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a set of flags used to control various launcher behaviors.
 *
 * <p>All the flags should be defined here with appropriate default values.
 */
public final class FeatureFlags {

    private static final List<DebugFlag> sDebugFlags = new ArrayList<>();

    public static final String FLAGS_PREF_NAME = "featureFlags";

    private FeatureFlags() { }

    public static boolean showFlagTogglerUi(Context context) {
        return Utilities.IS_DEBUG_DEVICE && Utilities.isDevelopersOptionsEnabled(context);
    }

    /**
     * True when the build has come from Android Studio and is being used for local debugging.
     */
    public static final boolean IS_STUDIO_BUILD = BuildConfig.DEBUG;

    /**
     * Enable moving the QSB on the 0th screen of the workspace. This is not a configuration feature
     * and should be modified at a project level.
     */
    public static final boolean QSB_ON_FIRST_SCREEN = BuildConfig.QSB_ON_FIRST_SCREEN;

    /**
     * Feature flag to handle define config changes dynamically instead of killing the process.
     *
     *
     * To add a new flag that can be toggled through the flags UI:
     *
     * Declare a new ToggleableFlag below. Give it a unique key (e.g. "QSB_ON_FIRST_SCREEN"),
     *    and set a default value for the flag. This will be the default value on Debug builds.
     */
    public static final BooleanFlag ENABLE_INPUT_CONSUMER_REASON_LOGGING = getDebugFlag(
            "ENABLE_INPUT_CONSUMER_REASON_LOGGING",
            true,
            "Log the reason why an Input Consumer was selected for a gesture.");

    public static final BooleanFlag ENABLE_GESTURE_ERROR_DETECTION = getDebugFlag(
            "ENABLE_GESTURE_ERROR_DETECTION",
            true,
            "Analyze gesture events and log detected errors");

    // When enabled the promise icon is visible in all apps while installation an app.
    public static final BooleanFlag PROMISE_APPS_IN_ALL_APPS = getDebugFlag(
            "PROMISE_APPS_IN_ALL_APPS", false, "Add promise icon in all-apps");

    // TODO: b/206508141: Long pressing on some icons on home screen cause launcher to crash.
    public static final BooleanFlag ENABLE_LOCAL_COLOR_POPUPS = getDebugFlag(
            "ENABLE_LOCAL_COLOR_POPUPS", false, "Enable local color extraction for popups.");

    public static final BooleanFlag KEYGUARD_ANIMATION = getDebugFlag(
            "KEYGUARD_ANIMATION", false, "Enable animation for keyguard going away on wallpaper");

    public static final BooleanFlag ENABLE_QUICKSTEP_LIVE_TILE = getDebugFlag(
            "ENABLE_QUICKSTEP_LIVE_TILE", true, "Enable live tile in Quickstep overview");

    public static final BooleanFlag ENABLE_DEVICE_SEARCH = new DeviceFlag(
            "ENABLE_DEVICE_SEARCH", true, "Allows on device search in all apps");

    public static final BooleanFlag ENABLE_FLOATING_SEARCH_BAR =
            getDebugFlag("ENABLE_FLOATING_SEARCH_BAR", false,
                    "Keep All Apps search bar at the bottom (but above keyboard if open)");

    public static final BooleanFlag ENABLE_HIDE_HEADER = new DeviceFlag("ENABLE_HIDE_HEADER",
            true, "Hide header on keyboard before typing in all apps");

    public static final BooleanFlag ENABLE_HIDE_HEADER_STATIC = new DeviceFlag(
            "ENABLE_HIDE_HEADER_STATIC", false, "Hide keyboard suggestion strip");

    public static final BooleanFlag COLLECT_SEARCH_HISTORY = new DeviceFlag(
            "COLLECT_SEARCH_HISTORY", false, "Allow launcher to collect search history for log");

    public static final BooleanFlag ENABLE_TWOLINE_ALLAPPS = getDebugFlag(
            "ENABLE_TWOLINE_ALLAPPS", false, "Enables two line label inside all apps.");

    public static final BooleanFlag ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING = new DeviceFlag(
            "ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING", false,
            "Allows on device search in all apps logging");

    public static final BooleanFlag IME_STICKY_SNACKBAR_EDU = getDebugFlag(
            "IME_STICKY_SNACKBAR_EDU", true, "Show sticky IME edu in AllApps");

    public static final BooleanFlag ENABLE_PEOPLE_TILE_PREVIEW = getDebugFlag(
            "ENABLE_PEOPLE_TILE_PREVIEW", false,
            "Experimental: Shows conversation shortcuts on home screen as search results");

    public static final BooleanFlag FOLDER_NAME_MAJORITY_RANKING = getDebugFlag(
            "FOLDER_NAME_MAJORITY_RANKING", true,
            "Suggests folder names based on majority based ranking.");

    public static final BooleanFlag ASSISTANT_GIVES_LAUNCHER_FOCUS = getDebugFlag(
            "ASSISTANT_GIVES_LAUNCHER_FOCUS", false,
            "Allow Launcher to handle nav bar gestures while Assistant is running over it");

    public static final BooleanFlag ENABLE_BULK_WORKSPACE_ICON_LOADING = getDebugFlag(
            "ENABLE_BULK_WORKSPACE_ICON_LOADING",
            true,
            "Enable loading workspace icons in bulk.");

    public static final BooleanFlag ENABLE_BULK_ALL_APPS_ICON_LOADING = getDebugFlag(
            "ENABLE_BULK_ALL_APPS_ICON_LOADING",
            true,
            "Enable loading all apps icons in bulk.");

    // Keep as DeviceFlag for remote disable in emergency.
    public static final BooleanFlag ENABLE_OVERVIEW_SELECTIONS = new DeviceFlag(
            "ENABLE_OVERVIEW_SELECTIONS", true, "Show Select Mode button in Overview Actions");

    public static final BooleanFlag ENABLE_WIDGETS_PICKER_AIAI_SEARCH = new DeviceFlag(
            "ENABLE_WIDGETS_PICKER_AIAI_SEARCH", true, "Enable AiAi search in the widgets picker");

    public static final BooleanFlag ENABLE_OVERVIEW_SHARING_TO_PEOPLE = getDebugFlag(
            "ENABLE_OVERVIEW_SHARING_TO_PEOPLE", true,
            "Show indicators for content on Overview to share with top people. ");

    public static final BooleanFlag ENABLE_DATABASE_RESTORE = getDebugFlag(
            "ENABLE_DATABASE_RESTORE", false,
            "Enable database restore when new restore session is created");

    public static final BooleanFlag ENABLE_SMARTSPACE_DISMISS = getDebugFlag(
            "ENABLE_SMARTSPACE_DISMISS", true,
            "Adds a menu option to dismiss the current Enhanced Smartspace card.");

    /**
     * Enables region sampling for text color: Needs system health assessment before turning on
     */
    public static final BooleanFlag ENABLE_REGION_SAMPLING =  getDebugFlag(
            "ENABLE_REGION_SAMPLING", false,
            "Enable region sampling to determine color of text on screen.");

    public static final BooleanFlag ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS =
            getDebugFlag(
            "ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS", false,
            "Always use hardware optimization for folder animations.");

    public static final BooleanFlag SEPARATE_RECENTS_ACTIVITY = getDebugFlag(
            "SEPARATE_RECENTS_ACTIVITY", false,
            "Uses a separate recents activity instead of using the integrated recents+Launcher UI");

    public static final BooleanFlag ENABLE_MINIMAL_DEVICE = getDebugFlag(
            "ENABLE_MINIMAL_DEVICE", false,
            "Allow user to toggle minimal device mode in launcher.");

    // TODO: b/172467144 Remove ENABLE_LAUNCHER_ACTIVITY_THEME_CROSSFADE feature flag.
    public static final BooleanFlag ENABLE_LAUNCHER_ACTIVITY_THEME_CROSSFADE = new DeviceFlag(
            "ENABLE_LAUNCHER_ACTIVITY_THEME_CROSSFADE", false, "Enables a "
            + "crossfade animation when the system these changes.");

    // TODO: b/174174514 Remove ENABLE_APP_PREDICTIONS_WHILE_VISIBLE feature flag.
    public static final BooleanFlag ENABLE_APP_PREDICTIONS_WHILE_VISIBLE = new DeviceFlag(
            "ENABLE_APP_PREDICTIONS_WHILE_VISIBLE", true, "Allows app "
            + "predictions to be updated while they are visible to the user.");

    public static final BooleanFlag ENABLE_TASKBAR_POPUP_MENU = getDebugFlag(
            "ENABLE_TASKBAR_POPUP_MENU", true, "Enables long pressing taskbar icons to show the"
                    + " popup menu.");

    public static final BooleanFlag ENABLE_TWO_PANEL_HOME = getDebugFlag(
            "ENABLE_TWO_PANEL_HOME", true,
            "Uses two panel on home screen. Only applicable on large screen devices.");

    public static final BooleanFlag ENABLE_SCRIM_FOR_APP_LAUNCH = getDebugFlag(
            "ENABLE_SCRIM_FOR_APP_LAUNCH", false,
            "Enables scrim during app launch animation.");

    public static final BooleanFlag ENABLE_ENFORCED_ROUNDED_CORNERS = new DeviceFlag(
            "ENABLE_ENFORCED_ROUNDED_CORNERS", true, "Enforce rounded corners on all App Widgets");

    public static final BooleanFlag ENABLE_LOCAL_RECOMMENDED_WIDGETS_FILTER = new DeviceFlag(
            "ENABLE_LOCAL_RECOMMENDED_WIDGETS_FILTER", true,
            "Enables a local filter for recommended widgets.");

    public static final BooleanFlag NOTIFY_CRASHES = getDebugFlag("NOTIFY_CRASHES", false,
            "Sends a notification whenever launcher encounters an uncaught exception.");

    public static final BooleanFlag ENABLE_WALLPAPER_SCRIM = getDebugFlag(
            "ENABLE_WALLPAPER_SCRIM", false,
            "Enables scrim over wallpaper for text protection.");

    public static final BooleanFlag WIDGETS_IN_LAUNCHER_PREVIEW = getDebugFlag(
            "WIDGETS_IN_LAUNCHER_PREVIEW", true,
            "Enables widgets in Launcher preview for the Wallpaper app.");

    public static final BooleanFlag QUICK_WALLPAPER_PICKER = getDebugFlag(
            "QUICK_WALLPAPER_PICKER", true,
            "Shows quick wallpaper picker in long-press menu");

    public static final BooleanFlag ENABLE_BACK_SWIPE_HOME_ANIMATION = getDebugFlag(
            "ENABLE_BACK_SWIPE_HOME_ANIMATION", true,
            "Enables home animation to icon when user swipes back.");

    public static final BooleanFlag ENABLE_ICON_LABEL_AUTO_SCALING = getDebugFlag(
            "ENABLE_ICON_LABEL_AUTO_SCALING", true,
            "Enables scaling/spacing for icon labels to make more characters visible");

    public static final BooleanFlag ENABLE_ALL_APPS_IN_TASKBAR = getDebugFlag(
            "ENABLE_ALL_APPS_IN_TASKBAR", true,
            "Enables accessing All Apps from the system Taskbar.");

    public static final BooleanFlag ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT = getDebugFlag(
            "ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT", false,
            "Enables displaying the all apps button in the hotseat.");

    public static final BooleanFlag ENABLE_ALL_APPS_ONE_SEARCH_IN_TASKBAR = getDebugFlag(
            "ENABLE_ALL_APPS_ONE_SEARCH_IN_TASKBAR", false,
            "Enables One Search box in Taskbar All Apps.");

    public static final BooleanFlag ENABLE_SPLIT_FROM_WORKSPACE = getDebugFlag(
            "ENABLE_SPLIT_FROM_WORKSPACE", true,
            "Enable initiating split screen from workspace.");

    public static final BooleanFlag ENABLE_NEW_MIGRATION_LOGIC = getDebugFlag(
            "ENABLE_NEW_MIGRATION_LOGIC", true,
            "Enable the new grid migration logic, keeping pages when src < dest");

    public static final BooleanFlag ENABLE_ONE_SEARCH_MOTION = new DeviceFlag(
            "ENABLE_ONE_SEARCH_MOTION", true, "Enables animations in OneSearch.");

    public static final BooleanFlag ENABLE_KEYBOARD_TRANSITION_SYNC = new DeviceFlag(
            "ENABLE_KEYBOARD_TRANSITION_SYNC", IS_STUDIO_BUILD,
            "Enable option to synchronize the keyboard open and close animations when transitioning"
                    + " between home and all apps");

    public static final BooleanFlag ENABLE_SHOW_KEYBOARD_OPTION_IN_ALL_APPS = new DeviceFlag(
            "ENABLE_SHOW_KEYBOARD_OPTION_IN_ALL_APPS", true,
            "Enable option to show keyboard when going to all-apps");

    public static final BooleanFlag USE_LOCAL_ICON_OVERRIDES = getDebugFlag(
            "USE_LOCAL_ICON_OVERRIDES", true,
            "Use inbuilt monochrome icons if app doesn't provide one");

    public static final BooleanFlag ENABLE_DISMISS_PREDICTION_UNDO = getDebugFlag(
            "ENABLE_DISMISS_PREDICTION_UNDO", false,
            "Show an 'Undo' snackbar when users dismiss a predicted hotseat item");

    public static final BooleanFlag ENABLE_CACHED_WIDGET = getDebugFlag(
            "ENABLE_CACHED_WIDGET", true,
            "Show previously cached widgets as opposed to deferred widget where available");

    public static final BooleanFlag USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES = getDebugFlag(
            "USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES", false,
            "Use local overrides for search request timeout");

    public static final BooleanFlag CONTINUOUS_VIEW_TREE_CAPTURE = getDebugFlag(
            "CONTINUOUS_VIEW_TREE_CAPTURE", false, "Capture View tree every frame");

    public static final BooleanFlag FOLDABLE_WORKSPACE_REORDER = getDebugFlag(
            "FOLDABLE_WORKSPACE_REORDER", true,
            "In foldables, when reordering the icons and widgets, is now going to use both sides");

    public static final BooleanFlag ENABLE_WIDGET_PICKER_DEPTH = new DeviceFlag(
            "ENABLE_WIDGET_PICKER_DEPTH", false, "Enable changing depth in widget picker.");

    public static final BooleanFlag SHOW_DELIGHTFUL_PAGINATION_FOLDER = new DeviceFlag(
            "SHOW_DELIGHTFUL_PAGINATION_FOLDER", false,
            "Enable showing the new 'delightful pagination'"
                    + " which is a brand new animation for folder pagination");

    public static final BooleanFlag POPUP_MATERIAL_U = new DeviceFlag(
            "POPUP_MATERIAL_U", false, "Switch popup UX to use material U");

    public static void initialize(Context context) {
        synchronized (sDebugFlags) {
            for (DebugFlag flag : sDebugFlags) {
                flag.initialize(context);
            }
            sDebugFlags.sort((f1, f2) -> f1.key.compareToIgnoreCase(f2.key));
        }
    }

    static List<DebugFlag> getDebugFlags() {
        synchronized (sDebugFlags) {
            return new ArrayList<>(sDebugFlags);
        }
    }

    public static void dump(PrintWriter pw) {
        pw.println("DeviceFlags:");
        synchronized (sDebugFlags) {
            for (DebugFlag flag : sDebugFlags) {
                if (flag instanceof DeviceFlag) {
                    pw.println("  " + flag.toString());
                }
            }
        }
        pw.println("DebugFlags:");
        synchronized (sDebugFlags) {
            for (DebugFlag flag : sDebugFlags) {
                if (!(flag instanceof DeviceFlag)) {
                    pw.println("  " + flag.toString());
                }
            }
        }
    }

    public static class BooleanFlag {

        public final String key;
        public final boolean defaultValue;

        public BooleanFlag(String key, boolean defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        public boolean get() {
            return defaultValue;
        }

        @Override
        public String toString() {
            return appendProps(new StringBuilder()).toString();
        }

        protected StringBuilder appendProps(StringBuilder src) {
            return src.append(key).append(", defaultValue=").append(defaultValue);
        }
    }

    public static class DebugFlag extends BooleanFlag {

        public final String description;
        protected boolean mCurrentValue;

        public DebugFlag(String key, boolean defaultValue, String description) {
            super(key, defaultValue);
            this.description = description;
            mCurrentValue = this.defaultValue;
            synchronized (sDebugFlags) {
                sDebugFlags.add(this);
            }
        }

        @Override
        public boolean get() {
            return mCurrentValue;
        }

        public void initialize(Context context) {
            mCurrentValue = context.getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE)
                    .getBoolean(key, defaultValue);
        }

        @Override
        protected StringBuilder appendProps(StringBuilder src) {
            return super.appendProps(src).append(", mCurrentValue=").append(mCurrentValue);
        }
    }

    private static BooleanFlag getDebugFlag(String key, boolean defaultValue, String description) {
        return Utilities.IS_DEBUG_DEVICE
                ? new DebugFlag(key, defaultValue, description)
                : new BooleanFlag(key, defaultValue);
    }
}
