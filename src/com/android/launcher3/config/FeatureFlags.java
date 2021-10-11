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
        return Utilities.isDevelopersOptionsEnabled(context);
    }

    /**
     * True when the build has come from Android Studio and is being used for local debugging.
     */
    public static final boolean IS_STUDIO_BUILD = BuildConfig.DEBUG;

    /**
     * Enable moving the QSB on the 0th screen of the workspace. This is not a configuration feature
     * and should be modified at a project level.
     */
    public static final boolean QSB_ON_FIRST_SCREEN = false;

    /**
     * Feature flag to handle define config changes dynamically instead of killing the process.
     *
     *
     * To add a new flag that can be toggled through the flags UI:
     *
     * Declare a new ToggleableFlag below. Give it a unique key (e.g. "QSB_ON_FIRST_SCREEN"),
     *    and set a default value for the flag. This will be the default value on Debug builds.
     */
    // When enabled the promise icon is visible in all apps while installation an app.
    public static final BooleanFlag PROMISE_APPS_IN_ALL_APPS = getDebugFlag(
            "PROMISE_APPS_IN_ALL_APPS", false, "Add promise icon in all-apps");

    // When enabled a promise icon is added to the home screen when install session is active.
    public static final BooleanFlag PROMISE_APPS_NEW_INSTALLS = getDebugFlag(
            "PROMISE_APPS_NEW_INSTALLS", true,
            "Adds a promise icon to the home screen for new install sessions.");

    public static final BooleanFlag QUICKSTEP_SPRINGS = getDebugFlag(
            "QUICKSTEP_SPRINGS", true, "Enable springs for quickstep animations");

    public static final BooleanFlag UNSTABLE_SPRINGS = getDebugFlag(
            "UNSTABLE_SPRINGS", false, "Enable unstable springs for quickstep animations");

    public static final BooleanFlag ENABLE_LOCAL_COLOR_POPUPS = getDebugFlag(
            "ENABLE_LOCAL_COLOR_POPUPS", true, "Enable local color extraction for popups.");

    public static final BooleanFlag KEYGUARD_ANIMATION = getDebugFlag(
            "KEYGUARD_ANIMATION", true, "Enable animation for keyguard going away on wallpaper");

    public static final BooleanFlag ADAPTIVE_ICON_WINDOW_ANIM = getDebugFlag(
            "ADAPTIVE_ICON_WINDOW_ANIM", true, "Use adaptive icons for window animations.");

    public static final BooleanFlag ENABLE_QUICKSTEP_LIVE_TILE = getDebugFlag(
            "ENABLE_QUICKSTEP_LIVE_TILE", true, "Enable live tile in Quickstep overview");

    public static final BooleanFlag ENABLE_QUICKSTEP_WIDGET_APP_START = getDebugFlag(
            "ENABLE_QUICKSTEP_WIDGET_APP_START", true,
            "Enable Quickstep animation when launching activities from an app widget");

    // Keep as DeviceFlag to allow remote disable in emergency.
    public static final BooleanFlag ENABLE_SUGGESTED_ACTIONS_OVERVIEW = new DeviceFlag(
            "ENABLE_SUGGESTED_ACTIONS_OVERVIEW", false, "Show chip hints on the overview screen");


    public static final BooleanFlag ENABLE_DEVICE_SEARCH = new DeviceFlag(
            "ENABLE_DEVICE_SEARCH", true, "Allows on device search in all apps");

    public static final BooleanFlag ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING = new DeviceFlag(
            "ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING", true,
            "Allows on device search in all apps logging");

    public static final BooleanFlag IME_STICKY_SNACKBAR_EDU = getDebugFlag(
            "IME_STICKY_SNACKBAR_EDU", true, "Show sticky IME edu in AllApps");

    public static final BooleanFlag ENABLE_PEOPLE_TILE_PREVIEW = getDebugFlag(
            "ENABLE_PEOPLE_TILE_PREVIEW", false,
            "Experimental: Shows conversation shortcuts on home screen as search results");

    public static final BooleanFlag FOLDER_NAME_SUGGEST = new DeviceFlag(
            "FOLDER_NAME_SUGGEST", true,
            "Suggests folder names instead of blank text.");

    public static final BooleanFlag FOLDER_NAME_MAJORITY_RANKING = getDebugFlag(
            "FOLDER_NAME_MAJORITY_RANKING", true,
            "Suggests folder names based on majority based ranking.");

    public static final BooleanFlag ENABLE_PREDICTION_DISMISS = getDebugFlag(
            "ENABLE_PREDICTION_DISMISS", true, "Allow option to dimiss apps from predicted list");

    public static final BooleanFlag ENABLE_QUICK_CAPTURE_GESTURE = getDebugFlag(
            "ENABLE_QUICK_CAPTURE_GESTURE", true, "Swipe from right to left to quick capture");

    public static final BooleanFlag ENABLE_QUICK_CAPTURE_WINDOW = getDebugFlag(
            "ENABLE_QUICK_CAPTURE_WINDOW", false, "Use window to host quick capture");

    public static final BooleanFlag FORCE_LOCAL_OVERSCROLL_PLUGIN = getDebugFlag(
            "FORCE_LOCAL_OVERSCROLL_PLUGIN", false,
            "Use a launcher-provided OverscrollPlugin if available");

    public static final BooleanFlag ASSISTANT_GIVES_LAUNCHER_FOCUS = getDebugFlag(
            "ASSISTANT_GIVES_LAUNCHER_FOCUS", false,
            "Allow Launcher to handle nav bar gestures while Assistant is running over it");

    public static final BooleanFlag HOTSEAT_MIGRATE_TO_FOLDER = getDebugFlag(
            "HOTSEAT_MIGRATE_TO_FOLDER", false, "Should move hotseat items into a folder");

    public static final BooleanFlag ENABLE_DEEP_SHORTCUT_ICON_CACHE = getDebugFlag(
            "ENABLE_DEEP_SHORTCUT_ICON_CACHE", true, "R/W deep shortcut in IconCache");

    public static final BooleanFlag MULTI_DB_GRID_MIRATION_ALGO = getDebugFlag(
            "MULTI_DB_GRID_MIRATION_ALGO", true, "Use the multi-db grid migration algorithm");

    public static final BooleanFlag ENABLE_THEMED_ICONS = getDebugFlag(
            "ENABLE_THEMED_ICONS", true, "Enable themed icons on workspace");

    // Keep as DeviceFlag for remote disable in emergency.
    public static final BooleanFlag ENABLE_OVERVIEW_SELECTIONS = new DeviceFlag(
            "ENABLE_OVERVIEW_SELECTIONS", true, "Show Select Mode button in Overview Actions");

    public static final BooleanFlag ENABLE_WIDGETS_PICKER_AIAI_SEARCH = new DeviceFlag(
            "ENABLE_WIDGETS_PICKER_AIAI_SEARCH", false, "Enable AiAi search in the widgets picker");

    public static final BooleanFlag ENABLE_OVERVIEW_SHARE = getDebugFlag(
            "ENABLE_OVERVIEW_SHARE", false, "Show Share button in Overview Actions");

    public static final BooleanFlag ENABLE_OVERVIEW_SHARING_TO_PEOPLE = getDebugFlag(
            "ENABLE_OVERVIEW_SHARING_TO_PEOPLE", true,
            "Show indicators for content on Overview to share with top people. ");

    public static final BooleanFlag ENABLE_OVERVIEW_CONTENT_PUSH = getDebugFlag(
            "ENABLE_OVERVIEW_CONTENT_PUSH", false, "Show Content Push button in Overview Actions");

    public static final BooleanFlag ENABLE_DATABASE_RESTORE = getDebugFlag(
            "ENABLE_DATABASE_RESTORE", false,
            "Enable database restore when new restore session is created");

    public static final BooleanFlag ENABLE_SMARTSPACE_UNIVERSAL = getDebugFlag(
            "ENABLE_SMARTSPACE_UNIVERSAL", false,
            "Replace Smartspace with a version rendered by System UI.");

    public static final BooleanFlag ENABLE_SMARTSPACE_ENHANCED = getDebugFlag(
            "ENABLE_SMARTSPACE_ENHANCED", true,
            "Replace Smartspace with the enhanced version. "
                    + "Ignored if ENABLE_SMARTSPACE_UNIVERSAL is enabled.");

    public static final BooleanFlag ENABLE_SMARTSPACE_FEEDBACK = getDebugFlag(
            "ENABLE_SMARTSPACE_FEEDBACK", true,
            "Adds a menu option to send feedback for Enhanced Smartspace.");

    public static final BooleanFlag ENABLE_SMARTSPACE_DISMISS = getDebugFlag(
            "ENABLE_SMARTSPACE_DISMISS", true,
            "Adds a menu option to dismiss the current Enhanced Smartspace card.");

    public static final BooleanFlag ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS =
            getDebugFlag(
            "ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS", false,
            "Always use hardware optimization for folder animations.");

    public static final BooleanFlag ENABLE_ALL_APPS_EDU = getDebugFlag(
            "ENABLE_ALL_APPS_EDU", true,
            "Shows user a tutorial on how to get to All Apps after X amount of attempts.");

    public static final BooleanFlag SEPARATE_RECENTS_ACTIVITY = getDebugFlag(
            "SEPARATE_RECENTS_ACTIVITY", false,
            "Uses a separate recents activity instead of using the integrated recents+Launcher UI");

    public static final BooleanFlag ENABLE_MINIMAL_DEVICE = getDebugFlag(
            "ENABLE_MINIMAL_DEVICE", false,
            "Allow user to toggle minimal device mode in launcher.");

    public static final BooleanFlag EXPANDED_SMARTSPACE = new DeviceFlag(
            "EXPANDED_SMARTSPACE", false, "Expands smartspace height to two rows. "
              + "Any apps occupying the first row will be removed from workspace.");

    // TODO: b/172467144 Remove ENABLE_LAUNCHER_ACTIVITY_THEME_CROSSFADE feature flag.
    public static final BooleanFlag ENABLE_LAUNCHER_ACTIVITY_THEME_CROSSFADE = new DeviceFlag(
            "ENABLE_LAUNCHER_ACTIVITY_THEME_CROSSFADE", true, "Enables a "
            + "crossfade animation when the system these changes.");

    // TODO: b/174174514 Remove ENABLE_APP_PREDICTIONS_WHILE_VISIBLE feature flag.
    public static final BooleanFlag ENABLE_APP_PREDICTIONS_WHILE_VISIBLE = new DeviceFlag(
            "ENABLE_APP_PREDICTIONS_WHILE_VISIBLE", true, "Allows app "
            + "predictions to be updated while they are visible to the user.");

    public static final BooleanFlag ENABLE_TASKBAR = getDebugFlag(
            "ENABLE_TASKBAR", false, "Allows a system Taskbar to be shown on larger devices.");

    public static final BooleanFlag ENABLE_OVERVIEW_GRID = getDebugFlag(
            "ENABLE_OVERVIEW_GRID", false, "Uses grid overview layout. "
            + "Only applicable on large screen devices.");

    public static final BooleanFlag ENABLE_TWO_PANEL_HOME = getDebugFlag(
            "ENABLE_TWO_PANEL_HOME", false,
            "Uses two panel on home screen. Only applicable on large screen devices.");

    public static final BooleanFlag ENABLE_SCRIM_FOR_APP_LAUNCH = getDebugFlag(
            "ENABLE_SCRIM_FOR_APP_LAUNCH", true,
            "Enables scrim during app launch animation.");

    public static final BooleanFlag ENABLE_SPLIT_SELECT = getDebugFlag(
            "ENABLE_SPLIT_SELECT", false, "Uses new split screen selection overview UI");

    public static final BooleanFlag ENABLE_ENFORCED_ROUNDED_CORNERS = new DeviceFlag(
            "ENABLE_ENFORCED_ROUNDED_CORNERS", true, "Enforce rounded corners on all App Widgets");

    public static final BooleanFlag ENABLE_LOCAL_RECOMMENDED_WIDGETS_FILTER = new DeviceFlag(
            "ENABLE_LOCAL_RECOMMENDED_WIDGETS_FILTER", true,
            "Enables a local filter for recommended widgets.");

    public static final BooleanFlag NOTIFY_CRASHES = getDebugFlag("NOTIFY_CRASHES", false,
            "Sends a notification whenever launcher encounters an uncaught exception.");

    public static final BooleanFlag PROTOTYPE_APP_CLOSE = getDebugFlag(
            "PROTOTYPE_APP_CLOSE", true, "Enables new app close");

    public static final BooleanFlag ENABLE_WALLPAPER_SCRIM = getDebugFlag(
            "ENABLE_WALLPAPER_SCRIM", false,
            "Enables scrim over wallpaper for text protection.");

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
        public boolean defaultValue;

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

        public void addChangeListener(Context context, Runnable r) { }

        public void removeChangeListener(Runnable r) {}
    }

    public static class DebugFlag extends BooleanFlag {

        public final String description;
        private boolean mCurrentValue;

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
        return new DebugFlag(key, defaultValue, description);
    }
}
