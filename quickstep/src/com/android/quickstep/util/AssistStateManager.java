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
package com.android.quickstep.util;

import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import com.android.launcher3.R;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;

import java.io.PrintWriter;

/** Class to manage Assistant states. */
public class AssistStateManager implements ResourceBasedOverride {

    public static final MainThreadInitializedObject<AssistStateManager> INSTANCE =
            forOverride(AssistStateManager.class, R.string.assist_state_manager_class);

    public AssistStateManager() {}

    /** Whether search supports haptic on invocation. */
    public boolean supportsCommitHaptic() {
        return false;
    }

    /** Whether search is available. */
    public boolean isSearchAvailable() {
        return false;
    }

    /** Whether search recovery is available. */
    public boolean isVisRecoveryEnabled() {
        return false;
    }

    /** Whether search recovery is available. */
    public boolean isOseRecoveryEnabled() {
        return false;
    }

    /** Whether search recovery is available. */
    public boolean isOseShowSessionEnabled() {
        return false;
    }

    /** Return {@code true} if the Settings toggle is enabled. */
    public boolean isSettingsNavHandleEnabled() {
        return false;
    }

    /** Return {@code true} if the Settings toggle is enabled. */
    public boolean isSettingsHomeButtonEnabled() {
        return false;
    }

    /** Dump states. */
    public void dump(String prefix, PrintWriter writer) {}
}
