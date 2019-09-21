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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.GuardedBy;
import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.TogglableFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Defines a set of flags used to control various launcher behaviors.
 *
 * <p>All the flags should be defined here with appropriate default values.
 */
@Keep
public final class FeatureFlags {

    private static final Object sLock = new Object();
    @GuardedBy("sLock")
    private static final List<TogglableFlag> sFlags = new ArrayList<>();

    static final String FLAGS_PREF_NAME = "featureFlags";

    private FeatureFlags() { }

    public static boolean showFlagTogglerUi(Context context) {
        return Utilities.IS_DEBUG_DEVICE && Utilities.isDevelopersOptionsEnabled(context);
    }

    public static final boolean IS_DOGFOOD_BUILD = BuildConfig.DEBUG;

    /**
     * Enable moving the QSB on the 0th screen of the workspace. This is not a configuration feature
     * and should be modified at a project level.
     */
    public static final boolean QSB_ON_FIRST_SCREEN = true;


    /**
     * Feature flag to handle define config changes dynamically instead of killing the process.
     *
     *
     * To add a new flag that can be toggled through the flags UI:
     *
     * 1. Declare a new ToggleableFlag below. Give it a unique key (e.g. "QSB_ON_FIRST_SCREEN"),
     *    and set a default value for the flag. This will be the default value on Debug builds.
     *
     * 2. Add your flag to mTogglableFlags.
     *
     * 3. Create a getter method (an 'is' method) for the flag by copying an existing one.
     *
     * 4. Create a getter method with the same name in the release flags copy of FeatureFlags.java.
     *    This should returns a constant (true/false). This will be the value of the flag used on
     *    release builds.
     */
    // When enabled the promise icon is visible in all apps while installation an app.
    public static final TogglableFlag PROMISE_APPS_IN_ALL_APPS = new TogglableFlag(
            "PROMISE_APPS_IN_ALL_APPS", false, "Add promise icon in all-apps");

    // When enabled a promise icon is added to the home screen when install session is active.
    public static final TogglableFlag PROMISE_APPS_NEW_INSTALLS =
            new TogglableFlag("PROMISE_APPS_NEW_INSTALLS", true,
                    "Adds a promise icon to the home screen for new install sessions.");

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

    public static final TogglableFlag APP_SEARCH_IMPROVEMENTS = new TogglableFlag(
            "APP_SEARCH_IMPROVEMENTS", false,
            "Adds localized title and keyword search and ranking");

    public static final TogglableFlag ENABLE_PREDICTION_DISMISS = new TogglableFlag(
            "ENABLE_PREDICTION_DISMISS", false, "Allow option to dimiss apps from predicted list");


    public static void initialize(Context context) {
        // Avoid the disk read for user builds
        if (Utilities.IS_DEBUG_DEVICE) {
            synchronized (sLock) {
                for (BaseTogglableFlag flag : sFlags) {
                    flag.initialize(context);
                }
            }
        }
    }

    static List<TogglableFlag> getTogglableFlags() {
        // By Java Language Spec 12.4.2
        // https://docs.oracle.com/javase/specs/jls/se7/html/jls-12.html#jls-12.4.2, the
        // TogglableFlag instances on FeatureFlags will be created before those on the FeatureFlags
        // subclass. This code handles flags that are redeclared in FeatureFlags, ensuring the
        // FeatureFlags one takes priority.
        SortedMap<String, TogglableFlag> flagsByKey = new TreeMap<>();
        synchronized (sLock) {
            for (TogglableFlag flag : sFlags) {
                flagsByKey.put(flag.getKey(), flag);
            }
        }
        return new ArrayList<>(flagsByKey.values());
    }

    public static abstract class BaseTogglableFlag {
        private final String key;
        // should be value that is hardcoded in client side.
        // Comparatively, getDefaultValue() can be overridden.
        private final boolean defaultValue;
        private final String description;
        private boolean currentValue;

        public BaseTogglableFlag(
                String key,
                boolean defaultValue,
                String description) {
            this.key = checkNotNull(key);
            this.currentValue = this.defaultValue = defaultValue;
            this.description = checkNotNull(description);

            synchronized (sLock) {
                sFlags.add((TogglableFlag)this);
            }
        }

        /** Set the value of this flag. This should only be used in tests. */
        @VisibleForTesting
        void setForTests(boolean value) {
            currentValue = value;
        }

        public String getKey() {
            return key;
        }

        protected void initialize(Context context) {
            currentValue = getFromStorage(context, getDefaultValue());
        }

        protected abstract boolean getOverridenDefaultValue(boolean value);

        protected abstract void addChangeListener(Context context, Runnable r);

        public void updateStorage(Context context, boolean value) {
            SharedPreferences.Editor editor = context.getSharedPreferences(FLAGS_PREF_NAME,
                    Context.MODE_PRIVATE).edit();
            if (value == getDefaultValue()) {
                editor.remove(key).apply();
            } else {
                editor.putBoolean(key, value).apply();
            }
        }

        boolean getFromStorage(Context context, boolean defaultValue) {
            return context.getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE)
                    .getBoolean(key, getDefaultValue());
        }

        boolean getDefaultValue() {
            return getOverridenDefaultValue(defaultValue);
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
                    + "overriddenDefaultValue=" + getOverridenDefaultValue(defaultValue) + ", "
                    + "currentValue=" + currentValue + ", "
                    + "description=" + description
                    + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof TogglableFlag) {
                BaseTogglableFlag that = (BaseTogglableFlag) o;
                return (this.key.equals(that.getKey()))
                        && (this.getDefaultValue() == that.getDefaultValue())
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
            h$ ^= getDefaultValue() ? 1231 : 1237;
            h$ *= 1000003;
            h$ ^= description.hashCode();
            return h$;
        }
    }
}
