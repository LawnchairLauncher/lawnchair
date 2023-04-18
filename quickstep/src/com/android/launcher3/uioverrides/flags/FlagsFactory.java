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

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;
import android.util.Log;

import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags.BooleanFlag;
import com.android.launcher3.config.FeatureFlags.IntFlag;
import com.android.launcher3.util.ScreenOnTracker;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class to create various flags for system build
 */
public class FlagsFactory {

    private static final String TAG = "FlagsFactory";

    private static final FlagsFactory INSTANCE = new FlagsFactory();
    private static final boolean FLAG_AUTO_APPLY_ENABLED = false;

    public static final String FLAGS_PREF_NAME = "featureFlags";
    public static final String NAMESPACE_LAUNCHER = "launcher";

    private static final List<DebugFlag> sDebugFlags = new ArrayList<>();

    private final Set<String> mKeySet = new HashSet<>();
    private boolean mRestartRequested = false;

    private FlagsFactory() {
        if (!FLAG_AUTO_APPLY_ENABLED) {
            return;
        }
        DeviceConfig.addOnPropertiesChangedListener(
                NAMESPACE_LAUNCHER, UI_HELPER_EXECUTOR, this::onPropertiesChanged);
    }

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
        INSTANCE.mKeySet.add(key);
        boolean defaultValue = DeviceConfig.getBoolean(NAMESPACE_LAUNCHER, key, defaultValueInCode);
        if (Utilities.IS_DEBUG_DEVICE) {
            SharedPreferences prefs = currentApplication()
                    .getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE);
            boolean currentValue = prefs.getBoolean(key, defaultValue);
            DebugFlag flag = new DeviceFlag(key, description, defaultValue, currentValue,
                    defaultValueInCode);
            sDebugFlags.add(flag);
            return flag;
        } else {
            return new BooleanFlag(defaultValue);
        }
    }

    /**
     * Creates a new integer flag. Integer flags are always release flags
     */
    public static IntFlag getIntFlag(
            int bugId, String key, int defaultValueInCode, String description) {
        INSTANCE.mKeySet.add(key);
        return new IntFlag(DeviceConfig.getInt(NAMESPACE_LAUNCHER, key, defaultValueInCode));
    }

    static List<DebugFlag> getDebugFlags() {
        if (!Utilities.IS_DEBUG_DEVICE) {
            return Collections.emptyList();
        }
        synchronized (sDebugFlags) {
            return new ArrayList<>(sDebugFlags);
        }
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

    private void onPropertiesChanged(Properties properties) {
        if (!Collections.disjoint(properties.getKeyset(), mKeySet)) {
            // Schedule a restart
            if (mRestartRequested) {
                return;
            }
            Log.e(TAG, "Flag changed, scheduling restart");
            mRestartRequested = true;
            ScreenOnTracker sot = ScreenOnTracker.INSTANCE.get(currentApplication());
            if (sot.isScreenOn()) {
                sot.addListener(this::onScreenOnChanged);
            } else {
                onScreenOnChanged(false);
            }
        }
    }

    private void onScreenOnChanged(boolean isOn) {
        if (mRestartRequested && !isOn) {
            Log.e(TAG, "Restart requested, killing process");
            System.exit(0);
        }
    }
}
