/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.uioverrides.flags;

import static android.app.ActivityThread.currentApplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.DeviceConfig;

import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags.BooleanFlag;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class to create various flags for system build
 */
public class FlagsFactory {

    public static final String FLAGS_PREF_NAME = "featureFlags";
    public static final String NAMESPACE_LAUNCHER = "launcher";

    private static final List<DebugFlag> sDebugFlags = new ArrayList<>();

    /**
     * Creates a new debug flag
     */
    public static BooleanFlag getDebugFlag(
            int bugId, String key, boolean defaultValue, String description) {
        if (Utilities.IS_DEBUG_DEVICE) {
            SharedPreferences prefs = currentApplication()
                    .getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE);
            boolean currentValue = prefs.getBoolean(key, defaultValue);
            DebugFlag flag = new DebugFlag(key, description, defaultValue, currentValue);
            flag.mHasBeenChangedAtLeastOnce = prefs.contains(key);
            sDebugFlags.add(flag);
            return flag;
        } else {
            return new BooleanFlag(defaultValue);
        }
    }

    /**
     * Creates a new release flag
     */
    public static BooleanFlag getReleaseFlag(
            int bugId, String key, boolean defaultValueInCode, String description) {
        boolean defaultValue = DeviceConfig.getBoolean(NAMESPACE_LAUNCHER, key, defaultValueInCode);
        if (Utilities.IS_DEBUG_DEVICE) {
            SharedPreferences prefs = currentApplication()
                    .getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE);
            boolean currentValue = prefs.getBoolean(key, defaultValue);
            DebugFlag flag = new DeviceFlag(key, description, defaultValue, currentValue,
                    defaultValueInCode);
            flag.mHasBeenChangedAtLeastOnce = prefs.contains(key);
            sDebugFlags.add(flag);
            return flag;
        } else {
            return new BooleanFlag(defaultValue);
        }
    }

    static List<DebugFlag> getDebugFlags() {
        if (!Utilities.IS_DEBUG_DEVICE) {
            return Collections.emptyList();
        }
        List<DebugFlag> flags;
        synchronized (sDebugFlags) {
            flags = new ArrayList<>(sDebugFlags);
        }
        flags.sort((f1, f2) -> {
            // Sort first by any prefs that the user has changed, then alphabetically.
            int changeComparison = Boolean.compare(
                    f2.mHasBeenChangedAtLeastOnce, f1.mHasBeenChangedAtLeastOnce);
            return changeComparison != 0
                    ? changeComparison
                    : f1.key.compareToIgnoreCase(f2.key);
        });
        return flags;
    }

    /**
     * Dumps the current flags state to the print writer
     */
    public static void dump(PrintWriter pw) {
        if (!Utilities.IS_DEBUG_DEVICE) {
            return;
        }
        pw.println("DeviceFlags:");
        synchronized (sDebugFlags) {
            for (DebugFlag flag : sDebugFlags) {
                if (flag instanceof DeviceFlag) {
                    pw.println("  " + flag);
                }
            }
        }
        pw.println("DebugFlags:");
        synchronized (sDebugFlags) {
            for (DebugFlag flag : sDebugFlags) {
                if (!(flag instanceof DeviceFlag)) {
                    pw.println("  " + flag);
                }
            }
        }
    }
}
