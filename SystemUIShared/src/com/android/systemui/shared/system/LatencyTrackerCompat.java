/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.content.Context;

import com.android.internal.util.LatencyTracker;

/**
 * @see LatencyTracker
 */
public class LatencyTrackerCompat {
    /**
     * @see LatencyTracker
     * @deprecated Please use {@link LatencyTrackerCompat#logToggleRecents(Context, int)} instead.
     */
    @Deprecated
    public static void logToggleRecents(int duration) {
        LatencyTracker.logActionDeprecated(LatencyTracker.ACTION_TOGGLE_RECENTS, duration, false);
    }

    /** @see LatencyTracker */
    public static void logToggleRecents(Context context, int duration) {
        LatencyTracker.getInstance(context).logAction(LatencyTracker.ACTION_TOGGLE_RECENTS,
                duration);
    }
}