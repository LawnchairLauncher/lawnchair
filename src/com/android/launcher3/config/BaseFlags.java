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

import static androidx.core.util.Preconditions.checkNotNull;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.GuardedBy;
import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Defines a set of flags used to control various launcher behaviors.
 *
 * <p>All the flags should be defined here with appropriate default values.
 *
 * <p>This class is kept package-private to prevent direct access.
 */
@Keep
abstract class BaseFlags {

    private static final Object sLock = new Object();
    @GuardedBy("sLock")
    private static final List<TogglableFlag> sFlags = new ArrayList<>();

    static final String FLAGS_PREF_NAME = "featureFlags";

    BaseFlags() {
        throw new UnsupportedOperationException("Don't instantiate BaseFlags");
    }

    public static boolean showFlagTogglerUi(Context context) {
        return Utilities.IS_DEBUG_DEVICE && Utilities.isDevelopersOptionsEnabled(context);
    }

    public static final boolean IS_DOGFOOD_BUILD = false;

    // When enabled the promise icon is visible in all apps while installation an app.
    public static final boolean LAUNCHER3_PROMISE_APPS_IN_ALL_APPS = false;

    // Enable moving the QSB on the 0th screen of the workspace
    public static final boolean QSB_ON_FIRST_SCREEN = true;

    public static final TogglableFlag EXAMPLE_FLAG = new TogglableFlag("EXAMPLE_FLAG", true,
            "An example flag that doesn't do anything. Useful for testing");

    //Feature flag to enable pulling down navigation shade from workspace.
    public static final boolean PULL_DOWN_STATUS_BAR = true;

    // When true, custom widgets are loaded using CustomWidgetParser.
    public static final boolean ENABLE_CUSTOM_WIDGETS = false;

    // Features to control Launcher3Go behavior
    public static final boolean GO_DISABLE_WIDGETS = false;

    // When enabled shows a work profile tab in all apps
    public static final boolean ALL_APPS_TABS_ENABLED = true;

    // When true, overview shows screenshots in the orientation they were taken rather than
    // trying to make them fit the orientation the device is in.
    public static final boolean OVERVIEW_USE_SCREENSHOT_ORIENTATION = true;

    /**
     * Feature flag to handle define config changes dynamically instead of killing the process.
     */
    public static final TogglableFlag APPLY_CONFIG_AT_RUNTIME = new TogglableFlag(
            "APPLY_CONFIG_AT_RUNTIME", true, "Apply display changes dynamically");

    public static final TogglableFlag QUICKSTEP_SPRINGS = new TogglableFlag("QUICKSTEP_SPRINGS",
            false, "Enable springs for quickstep animations");

    public static final TogglableFlag ADAPTIVE_ICON_WINDOW_ANIM = new TogglableFlag(
            "ADAPTIVE_ICON_WINDOW_ANIM", true,
            "Use adaptive icons for window animations.");

    public static final TogglableFlag ENABLE_QUICKSTEP_LIVE_TILE = new TogglableFlag(
            "ENABLE_QUICKSTEP_LIVE_TILE", false, "Enable live tile in Quickstep overview");

    public static final TogglableFlag ENABLE_HINTS_IN_OVERVIEW = new TogglableFlag(
            "ENABLE_HINTS_IN_OVERVIEW", true,
            "Show chip hints and gleams on the overview screen");

    public static final TogglableFlag FAKE_LANDSCAPE_UI = new TogglableFlag(
            "FAKE_LANDSCAPE_UI", false,
            "Rotate launcher UI instead of using transposed layout");

    public static void initialize(Context context) {
        // Avoid the disk read for user builds
        if (Utilities.IS_DEBUG_DEVICE) {
            synchronized (sLock) {
                for (TogglableFlag flag : sFlags) {
                    flag.initialize(context);
                }
            }
        }
    }

    static List<TogglableFlag> getTogglableFlags() {
        // By Java Language Spec 12.4.2
        // https://docs.oracle.com/javase/specs/jls/se7/html/jls-12.html#jls-12.4.2, the
        // TogglableFlag instances on BaseFlags will be created before those on the FeatureFlags
        // subclass. This code handles flags that are redeclared in FeatureFlags, ensuring the
        // FeatureFlags one takes priority.
        SortedMap<String, TogglableFlag> flagsByKey = new TreeMap<>();
        synchronized (sLock) {
            for (TogglableFlag flag : sFlags) {
                flagsByKey.put(flag.key, flag);
            }
        }
        return new ArrayList<>(flagsByKey.values());
    }

    public static class TogglableFlag {
        private final String key;
        private final boolean defaultValue;
        private final String description;
        private boolean currentValue;

        TogglableFlag(
                String key,
                boolean defaultValue,
                String description) {
            this.key = checkNotNull(key);
            this.currentValue = this.defaultValue = defaultValue;
            this.description = checkNotNull(description);
            synchronized (sLock) {
                sFlags.add(this);
            }
        }

        /** Set the value of this flag. This should only be used in tests. */
        @VisibleForTesting
        void setForTests(boolean value) {
            currentValue = value;
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        public String getKey() {
            return key;
        }
        void initialize(Context context) {
            currentValue = getFromStorage(context, defaultValue);
        }

        public void updateStorage(Context context, boolean value) {
            SharedPreferences.Editor editor = context.getSharedPreferences(FLAGS_PREF_NAME,
                    Context.MODE_PRIVATE).edit();
            if (value == defaultValue) {
                editor.remove(key).apply();
            } else {
                editor.putBoolean(key, value).apply();
            }
        }

        boolean getFromStorage(Context context, boolean defaultValue) {
            return context.getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE)
                    .getBoolean(key, defaultValue);
        }

        boolean getDefaultValue() {
            return defaultValue;
        }

        /** Returns the value of the flag at process start, including any overrides present. */
        public boolean get() {
            return currentValue;
        }

        String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "TogglableFlag{"
                    + "key=" + key + ", "
                    + "defaultValue=" + defaultValue + ", "
                    + "description=" + description
                    + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof TogglableFlag) {
                TogglableFlag that = (TogglableFlag) o;
                return (this.key.equals(that.getKey()))
                        && (this.defaultValue == that.getDefaultValue())
                        && (this.description.equals(that.getDescription()));
            }
            return false;
        }

        @Override
        public int hashCode() {
            int h$ = 1;
            h$ *= 1000003;
            h$ ^= key.hashCode();
            h$ *= 1000003;
            h$ ^= defaultValue ? 1231 : 1237;
            h$ *= 1000003;
            h$ ^= description.hashCode();
            return h$;
        }
    }

    /**
     * Stores the FeatureFlag's value in Settings.Global instead of our SharedPrefs.
     * This is useful if we want to be able to control this flag from another process.
     */
    public static final class ToggleableGlobalSettingsFlag extends TogglableFlag {
        private ContentResolver contentResolver;

        ToggleableGlobalSettingsFlag(String key, boolean defaultValue, String description) {
            super(key, defaultValue, description);
        }

        @Override
        public void initialize(Context context) {
            contentResolver = context.getContentResolver();
            contentResolver.registerContentObserver(Settings.Global.getUriFor(getKey()), true,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            superInitialize(context);
                    }});
            superInitialize(context);
        }

        private void superInitialize(Context context) {
            super.initialize(context);
        }

        @Override
        public void updateStorage(Context context, boolean value) {
            if (contentResolver == null) {
                return;
            }
            Settings.Global.putInt(contentResolver, getKey(), value ? 1 : 0);
        }

        @Override
        boolean getFromStorage(Context context, boolean defaultValue) {
            if (contentResolver == null) {
                return defaultValue;
            }
            return Settings.Global.getInt(contentResolver, getKey(), defaultValue ? 1 : 0) == 1;
        }
    }
}
