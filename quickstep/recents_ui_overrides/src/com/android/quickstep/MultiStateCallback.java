/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.quickstep;

import android.util.Log;
import android.util.SparseArray;

import com.android.launcher3.config.FeatureFlags;

import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * Utility class to help manage multiple callbacks based on different states.
 */
public class MultiStateCallback {

    private static final String TAG = "MultiStateCallback";
    public static final boolean DEBUG_STATES = false;

    private final SparseArray<Runnable> mCallbacks = new SparseArray<>();
    private final SparseArray<Consumer<Boolean>> mStateChangeHandlers = new SparseArray<>();

    private final String[] mStateNames;

    public MultiStateCallback(String[] stateNames) {
        mStateNames = DEBUG_STATES ? stateNames : null;
    }

    private int mState = 0;

    /**
     * Adds the provided state flags to the global state and executes any callbacks as a result.
     */
    public void setState(int stateFlag) {
        if (DEBUG_STATES) {
            Log.d(TAG, "[" + System.identityHashCode(this) + "] Adding "
                    + convertToFlagNames(stateFlag) + " to " + convertToFlagNames(mState));
        }

        int oldState = mState;
        mState = mState | stateFlag;

        int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            int state = mCallbacks.keyAt(i);

            if ((mState & state) == state) {
                Runnable callback = mCallbacks.valueAt(i);
                if (callback != null) {
                    // Set the callback to null, so that it does not run again.
                    mCallbacks.setValueAt(i, null);
                    callback.run();
                }
            }
        }
        notifyStateChangeHandlers(oldState);
    }

    /**
     * Adds the provided state flags to the global state and executes any change handlers
     * as a result.
     */
    public void clearState(int stateFlag) {
        if (DEBUG_STATES) {
            Log.d(TAG, "[" + System.identityHashCode(this) + "] Removing "
                    + convertToFlagNames(stateFlag) + " from " + convertToFlagNames(mState));
        }

        int oldState = mState;
        mState = mState & ~stateFlag;
        notifyStateChangeHandlers(oldState);
    }

    private void notifyStateChangeHandlers(int oldState) {
        int count = mStateChangeHandlers.size();
        for (int i = 0; i < count; i++) {
            int state = mStateChangeHandlers.keyAt(i);
            boolean wasOn = (state & oldState) == state;
            boolean isOn = (state & mState) == state;

            if (wasOn != isOn) {
                mStateChangeHandlers.valueAt(i).accept(isOn);
            }
        }
    }

    /**
     * Sets the callbacks to be run when the provided states are enabled.
     * The callback is only run once.
     */
    public void addCallback(int stateMask, Runnable callback) {
        if (FeatureFlags.IS_DOGFOOD_BUILD && mCallbacks.get(stateMask) != null) {
            throw new IllegalStateException("Multiple callbacks on same state");
        }
        mCallbacks.put(stateMask, callback);
    }

    /**
     * Sets the handler to be called when the provided states are enabled or disabled.
     */
    public void addChangeHandler(int stateMask, Consumer<Boolean> handler) {
        mStateChangeHandlers.put(stateMask, handler);
    }

    public int getState() {
        return mState;
    }

    public boolean hasStates(int stateMask) {
        return (mState & stateMask) == stateMask;
    }

    private String convertToFlagNames(int flags) {
        StringJoiner joiner = new StringJoiner(", ", "[", " (" + flags + ")]");
        for (int i = 0; i < mStateNames.length; i++) {
            if ((flags & (1 << i)) != 0) {
                joiner.add(mStateNames[i]);
            }
        }
        return joiner.toString();
    }

}
