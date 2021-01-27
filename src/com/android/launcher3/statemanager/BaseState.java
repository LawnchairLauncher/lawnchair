/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.statemanager;

import android.content.Context;

/**
 * Interface representing a state of a StatefulActivity
 */
public interface BaseState<T extends BaseState> {

    // Flag to indicate that Launcher is non-interactive in this state
    int FLAG_NON_INTERACTIVE = 1 << 0;
    int FLAG_DISABLE_RESTORE = 1 << 1;

    static int getFlag(int index) {
        // reserve few spots to base flags
        return 1 << (index + 2);
    }

    /**
     * @return How long the animation to this state should take (or from this state to NORMAL).
     */
    int getTransitionDuration(Context context);

    /**
     * Returns the state to go back to from this state
     */
    T getHistoryForState(T previousState);

    /**
     * @return true if the state can be persisted across activity restarts.
     */
    default boolean shouldDisableRestore() {
        return hasFlag(FLAG_DISABLE_RESTORE);
    }

    /**
     * Returns if the state has the provided flag
     */
    boolean hasFlag(int flagMask);
}
