/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.onehanded;

import android.annotation.IntDef;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 Represents current OHM state by following steps, a generic CUJ is
 STATE_NONE -> STATE_ENTERING -> STATE_ACTIVE -> STATE_EXITING -> STATE_NONE
 */
public class OneHandedState {
    /** DEFAULT STATE after OHM feature initialized. */
    public static final int STATE_NONE = 0;
    /** The state flag set when user trigger OHM. */
    public static final int STATE_ENTERING = 1;
    /** The state flag set when transitioning */
    public static final int STATE_ACTIVE = 2;
    /** The state flag set when user stop OHM feature. */
    public static final int STATE_EXITING = 3;

    @IntDef(prefix = { "STATE_" }, value =  {
            STATE_NONE,
            STATE_ENTERING,
            STATE_ACTIVE,
            STATE_EXITING
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface State {}

    public OneHandedState() {
        sCurrentState = STATE_NONE;
    }

    @State
    private static int sCurrentState = STATE_NONE;

    private static final String TAG = OneHandedState.class.getSimpleName();

    private List<OnStateChangedListener> mStateChangeListeners = new ArrayList<>();

    /**
     * Adds listener to be called back when one handed state changed.
     * @param listener the listener to be called back
     */
    public void addSListeners(OnStateChangedListener listener) {
        mStateChangeListeners.add(listener);
    }

    /**
     * Gets current transition state of One handed mode.
     * @return The bitwise flags representing current states.
     */
    public @State int getState() {
        return sCurrentState;
    }

    /**
     * Is the One handed mode is in transitioning state.
     * @return true if One handed mode is in transitioning states.
     */
    public boolean isTransitioning() {
        return sCurrentState == STATE_ENTERING || sCurrentState == STATE_EXITING;
    }

    /**
     * Is the One handed mode active state.
     * @return true if One handed mode is active state.
     */
    public boolean isInOneHanded() {
        return sCurrentState == STATE_ACTIVE;
    }

    /**
     * Sets new state for One handed mode feature.
     * @param newState The bitwise value to represent current transition states.
     */
    public void setState(@State int newState) {
        sCurrentState = newState;
        if (!mStateChangeListeners.isEmpty()) {
            mStateChangeListeners.forEach((listener) -> listener.onStateChanged(newState));
        }
    }

    /** Dumps internal state. */
    public void dump(PrintWriter pw) {
        final String innerPrefix = "  ";
        pw.println(TAG);
        pw.println(innerPrefix + "sCurrentState=" + sCurrentState);
    }

    /**
     * Gets notified when one handed state changed
     *
     * @see OneHandedState
     */
    public interface OnStateChangedListener {
        /** Called when one handed state changed */
        default void onStateChanged(@State int newState) {}
    }
}
