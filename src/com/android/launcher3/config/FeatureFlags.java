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

    // TODO: b/206508141: Long pressing on some icons on home screen cause launcher to crash.
    public static final BooleanFlag ENABLE_LOCAL_COLOR_POPUPS = getDebugFlag(
            "ENABLE_LOCAL_COLOR_POPUPS", false, "Enable local color extraction for popups.");

    public static final BooleanFlag KEYGUARD_ANIMATION = getDebugFlag(
            "KEYGUARD_ANIMATION", false, "Enable animation for keyguard going away on wallpaper");

    public static final BooleanFlag ADAPTIVE_ICON_WINDOW_ANIM = getDebugFlag(
            "ADAPTIVE_ICON_WINDOW_ANIM", true, "Use adaptive icons for window animations.");

    public static final BooleanFlag ENABLE_QUICKSTEP_LIVE_TILE = getDebugFlag(
            "ENABLE_QUICKSTEP_LIVE_TILE", true, "Enable live tile in Quickstep overview");

    public static final BooleanFlag ENABLE_QUICKSTEP_WIDGET_APP_START = getDebugFlag(
            "ENABLE_QUICKSTEP_WIDGET_APP_START", true,
            "Enable Quickstep animation when launching activities from an app widget");

    public static final BooleanFlag ENABLE_DEVICE_SEARCH = new DeviceFlag(
            "ENABLE_DEVICE_SEARCH", true, "Allows on device search in all apps");

    public static final BooleanFlag ENABLE_TWOLINE_ALLAPPS = getDebugFlag(
            "ENABLE_TWOLINE_ALLAPPS", false, "Enables two line label inside all apps.");

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

    public static final BooleanFlag ASSISTANT_GIVES_LAUNCHER_FOCUS = getDebugFlag(
            "ASSISTANT_GIVES_LAUNCHER_FOCUS", false,
            "Allow Launcher to handle nav bar gestures while Assistant is running over it");

    public static final BooleanFlag HOTSEAT_MIGRATE_TO_FOLDER = getDebugFlag(
            "HOTSEAT_MIGRATE_TO_FOLDER", false, "Should move hotseat items into a folder");

    public static final BooleanFlag ENABLE_DEEP_SHORTCUT_ICON_CACHE = getDebugFlag(
            "ENABLE_DEEP_SHORTCUT_ICON_CACHE", true, "R/W deep shortcut in IconCache");

    public static final BooleanFlag ENABLE_THEMED_ICONS = getDebugFlag(
            "ENABLE_THEMED_ICONS", true, "Enable themed icons on workspace");

    public static final BooleanFlag ENABLE_BULK_WORKSPACE_ICON_LOADING = getDebugFlag(
            "ENABLE_BULK_WORKSPACE_ICON_LOADING",
            false,
            "Enable loading workspace icons in bulk.");

    public static final BooleanFlag ENABLE_BULK_ALL_APPS_ICON_LOADING = getDebugFlag(
            "ENABLE_BULK_ALL_APPS_ICON_LOADING",
            false,
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
            "ENABLE_LAUNCHER_ACTIVITY_THEME_CROSSFADE", false, "Enables a "
            + "crossfade animation when the system these changes.");

    // TODO: b/174174514 Remove ENABLE_APP_PREDICTIONS_WHILE_VISIBLE feature flag.
    public static final BooleanFlag ENABLE_APP_PREDICTIONS_WHILE_VISIBLE = new DeviceFlag(
            "ENABLE_APP_PREDICTIONS_WHILE_VISIBLE", true, "Allows app "
            + "predictions to be updated while they are visible to the user.");

    public static final BooleanFlag ENABLE_TASKBAR = getDebugFlag(
            "ENABLE_TASKBAR", true, "Allows a system Taskbar to be shown on larger devices.");

    public static final BooleanFlag ENABLE_TASKBAR_EDU = getDebugFlag("ENABLE_TASKBAR_EDU", true,
            "Enables showing taskbar education the first time an app is opened.");

    public static final BooleanFlag ENABLE_TASKBAR_POPUP_MENU = getDebugFlag(
            "ENABLE_TASKBAR_POPUP_MENU", false, "Enables long pressing taskbar icons to show the"
                    + " popup menu.");

    public static final BooleanFlag ENABLE_OVERVIEW_GRID = getDebugFlag(
            "ENABLE_OVERVIEW_GRID", true, "Uses grid overview layout. "
            + "Only applicable on large screen devices.");

    public static final BooleanFlag ENABLE_TWO_PANEL_HOME = getDebugFlag(
            "ENABLE_TWO_PANEL_HOME", true,
            "Uses two panel on home screen. Only applicable on large screen devices.");

    public static final BooleanFlag ENABLE_SCRIM_FOR_APP_LAUNCH = getDebugFlag(
            "ENABLE_SCRIM_FOR_APP_LAUNCH", false,
            "Enables scrim during app launch animation.");

    public static final BooleanFlag ENABLE_SPLIT_SELECT = getDebugFlag(
            "ENABLE_SPLIT_SELECT", true, "Uses new split screen selection overview UI");

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
