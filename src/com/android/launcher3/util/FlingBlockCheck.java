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
 * limitations under the License.
 */

package com.android.launcher3.util;

import android.os.SystemClock;

/**
 * Determines whether a fling should be blocked. Currently we block flings when crossing thresholds
 * to new states, and unblock after a short duration.
 */
public class FlingBlockCheck {
    // Allow flinging to a new state after waiting this many milliseconds.
    private static final long UNBLOCK_FLING_PAUSE_DURATION = 200;

    private boolean mBlockFling;
    private long mBlockFlingTime;

    public void blockFling() {
        mBlockFling = true;
        mBlockFlingTime = SystemClock.uptimeMillis();
    }

    public void unblockFling() {
        mBlockFling = false;
        mBlockFlingTime = 0;
    }

    public void onEvent() {
        // We prevent flinging after passing a state, but allow it if the user pauses briefly.
        if (SystemClock.uptimeMillis() - mBlockFlingTime >= UNBLOCK_FLING_PAUSE_DURATION) {
            mBlockFling = false;
        }
    }

    public boolean isBlocked() {
        return mBlockFling;
    }
}
