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

package com.android.wm.shell.recents;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** The listener for the events from {@link RecentsTransitionHandler}. */
public interface RecentsTransitionStateListener {

    @IntDef(prefix = { "TRANSITION_STATE_" }, value = {
            TRANSITION_STATE_NOT_RUNNING,
            TRANSITION_STATE_REQUESTED,
            TRANSITION_STATE_ANIMATING,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface RecentsTransitionState {}

    int TRANSITION_STATE_NOT_RUNNING = 1;
    int TRANSITION_STATE_REQUESTED = 2;
    int TRANSITION_STATE_ANIMATING = 3;

    /** Notifies whether the recents transition state changes. */
    default void onTransitionStateChanged(@RecentsTransitionState int state) {
    }

    /** Returns whether the recents transition is running. */
    static boolean isRunning(@RecentsTransitionState int state) {
        return state >= TRANSITION_STATE_REQUESTED;
    }

    /** Returns whether the recents transition is animating. */
    static boolean isAnimating(@RecentsTransitionState int state) {
        return state >= TRANSITION_STATE_ANIMATING;
    }

    /** Returns a string representation of the given state. */
    static String stateToString(@RecentsTransitionState int state) {
        return switch (state) {
            case TRANSITION_STATE_NOT_RUNNING -> "TRANSITION_STATE_NOT_RUNNING";
            case TRANSITION_STATE_REQUESTED -> "TRANSITION_STATE_REQUESTED";
            case TRANSITION_STATE_ANIMATING -> "TRANSITION_STATE_ANIMATING";
            default -> "UNKNOWN";
        };
    }
}
