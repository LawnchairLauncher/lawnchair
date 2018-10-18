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

    public static boolean showFlagTogglerUi() {
        return Utilities.IS_DEBUG_DEVICE;
    }

    public static final boolean IS_DOGFOOD_BUILD = false;
    public static final String AUTHORITY = "com.android.launcher3.settings".intern();

    // When enabled the promise icon is visible in all apps while installation an app.
    public static final boolean LAUNCHER3_PROMISE_APPS_IN_ALL_APPS = false;

    public static final TogglableFlag QSB_ON_FIRST_SCREEN = new TogglableFlag("QSB_ON_FIRST_SCREEN",
            true,
            "Enable moving the QSB on the 0th screen of the workspace");

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

    public static void initialize(Context context) {
        // Avoid the disk read for builds without the flags UI.
        if (showFlagTogglerUi()) {
            SharedPreferences sharedPreferences =
                    context.getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE);
            synchronized (sLock) {
                for (TogglableFlag flag : sFlags) {
                    flag.currentValue = sharedPreferences.getBoolean(flag.key, flag.defaultValue);
                }
            }
        } else {
            synchronized (sLock) {
                for (TogglableFlag flag : sFlags) {
                    flag.currentValue = flag.defaultValue;
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

    public static final class TogglableFlag {
        private final String key;
        private final boolean defaultValue;
        private final String description;
        private boolean currentValue;

        TogglableFlag(
                String key,
                boolean defaultValue,
                String description) {
            this.key = checkNotNull(key);
            this.defaultValue = defaultValue;
            this.description = checkNotNull(description);
            synchronized (sLock) {
                sFlags.add(this);
            }
        }

        String getKey() {
            return key;
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

}
