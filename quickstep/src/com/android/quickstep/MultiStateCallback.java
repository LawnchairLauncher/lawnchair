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

import android.util.SparseArray;

/**
 * Utility class to help manage multiple callbacks based on different states.
 */
public class MultiStateCallback {

    private final SparseArray<Runnable> mCallbacks = new SparseArray<>();

    private int mState = 0;

    /**
     * Adds the provided state flags to the global state and executes any callbacks as a result.
     * @param stateFlag
     */
    public void setState(int stateFlag) {
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
    }

    /**
     * Sets the callbacks to be run when the provided states are enabled.
     * The callback is only run once.
     */
    public void addCallback(int stateMask, Runnable callback) {
        mCallbacks.put(stateMask, callback);
    }

    public int getState() {
        return mState;
    }

    public boolean hasStates(int stateMask) {
        return (mState & stateMask) == stateMask;
    }
}
