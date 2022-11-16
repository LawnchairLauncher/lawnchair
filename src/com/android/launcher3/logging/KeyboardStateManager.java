/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.logging;

import static com.android.launcher3.logging.KeyboardStateManager.KeyboardState.NO_IME_ACTION;

import android.os.SystemClock;

/**
 * Class to maintain keyboard states.
 */
public class KeyboardStateManager {
    private long mUpdatedTime;
    private int mImeHeight;

    public enum KeyboardState {
        NO_IME_ACTION,
        SHOW,
        HIDE,
    }

    private KeyboardState mKeyboardState;

    public KeyboardStateManager() {
        mKeyboardState = NO_IME_ACTION;
    }

    /**
     * Returns time when keyboard state was updated.
     */
    public long getLastUpdatedTime() {
        return mUpdatedTime;
    }

    /**
     * Returns current keyboard state.
     */
    public KeyboardState getKeyboardState() {
        return mKeyboardState;
    }

    /**
     * Setter method to set keyboard state.
     */
    public void setKeyboardState(KeyboardState keyboardState) {
        mUpdatedTime = SystemClock.elapsedRealtime();
        mKeyboardState = keyboardState;
    }

    /**
     * Returns keyboard's current height.
     */
    public int getImeHeight() {
        return mImeHeight;
    }

    /**
     * Setter method to set keyboard height.
     */
    public void setImeHeight(int imeHeight) {
        mImeHeight = imeHeight;
    }
}
