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

import static com.android.launcher3.config.FeatureFlags.FlagState.ENABLED;

import androidx.annotation.Nullable;

import com.android.launcher3.ConstantItem;
import com.android.launcher3.config.FeatureFlags.BooleanFlag;
import com.android.launcher3.config.FeatureFlags.FlagState;
import com.android.launcher3.config.FeatureFlags.IntFlag;

import java.io.PrintWriter;

/**
 * Helper class to create various flags for launcher build. The base implementation does
 * not provide any flagging system, and simply replies with the default value.
 */
public class FlagsFactory {

    /**
     * Creates a new debug flag
     */
    public static BooleanFlag getDebugFlag(
            int bugId, String key, FlagState flagState, String description) {
        return new BooleanFlag(flagState == ENABLED);
    }

    /**
     * Creates a new debug flag
     */
    public static BooleanFlag getReleaseFlag(
            int bugId, String key, FlagState flagState, String description) {
        return new BooleanFlag(flagState == ENABLED);
    }

    /**
     * Creates a new integer flag. Integer flags are always release flags
     */
    public static IntFlag getIntFlag(
            int bugId, String key, int defaultValueInCode, String description) {
        return new IntFlag(defaultValueInCode);
    }

    /**
     * Creates a new debug integer flag and it is saved in LauncherPrefs.
     */
    public static IntFlag getIntFlag(
            int bugId, String key, int defaultValueInCode, String description,
            @Nullable ConstantItem<Integer> launcherPrefFlag) {
        return new IntFlag(defaultValueInCode);
    }

    /**
     * Dumps the current flags state to the print writer
     */
    public static void dump(PrintWriter pw) { }
}
