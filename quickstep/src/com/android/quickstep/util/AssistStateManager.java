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
import com.android.launcher3.util.SafeCloseable;

import java.io.PrintWriter;
import java.util.Optional;

/** Class to manage Assistant states. */
public class AssistStateManager implements ResourceBasedOverride, SafeCloseable {

    public static final MainThreadInitializedObject<AssistStateManager> INSTANCE =
            forOverride(AssistStateManager.class, R.string.assist_state_manager_class);

    public AssistStateManager() {}

    /** Whether search is available. */
    public boolean isSearchAvailable() {
        return false;
    }

    /** Whether search supports showing on the lockscreen. */
    public boolean supportsShowWhenLocked() {
        return false;
    }

    /** Whether CsHelper CtS invocation path is available. */
    public Optional<Boolean> isCsHelperAvailable() {
        return Optional.empty();
    }

    /** Whether VIS CtS invocation path is available. */
    public Optional<Boolean> isVisAvailable() {
        return Optional.empty();
    }

    /** Get the Launcher overridden long press nav handle duration to trigger Assistant. */
    public Optional<Long> getLPNHDurationMillis() {
        return Optional.empty();
    }

    /**
     * Get the Launcher overridden long press nav handle touch slop multiplier to trigger Assistant.
     */
    public Optional<Float> getLPNHCustomSlopMultiplier() {
        return Optional.empty();
    }

    /** Get the Launcher overridden long press home duration to trigger Assistant. */
    public Optional<Long> getLPHDurationMillis() {
        return Optional.empty();
    }

    /** Get the Launcher overridden long press home touch slop multiplier to trigger Assistant. */
    public Optional<Float> getLPHCustomSlopMultiplier() {
        return Optional.empty();
    }

    /** Get the long press duration data source. */
    public int getDurationDataSource() {
        return 0;
    }

    /** Get the long press touch slop multiplier data source. */
    public int getSlopDataSource() {
        return 0;
    }

    /** Get the haptic bit overridden by AGSA. */
    public Optional<Boolean> getShouldPlayHapticOverride() {
        return Optional.empty();
    }

    /** Return {@code true} if the Settings toggle is enabled. */
    public boolean isSettingsAllEntrypointsEnabled() {
        return false;
    }

    /** Dump states. */
    public void dump(String prefix, PrintWriter writer) {}

    @Override
    public void close() {}
}
