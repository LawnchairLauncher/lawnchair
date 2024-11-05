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

package com.android.wm.shell.pip2.phone;

/**
 * A generic interface for a touch gesture.
 */
public abstract class PipTouchGesture {

    /**
     * Handle the touch down.
     */
    public void onDown(PipTouchState touchState) {}

    /**
     * Handle the touch move, and return whether the event was consumed.
     */
    public boolean onMove(PipTouchState touchState) {
        return false;
    }

    /**
     * Handle the touch up, and return whether the gesture was consumed.
     */
    public boolean onUp(PipTouchState touchState) {
        return false;
    }

    /**
     * Cleans up the high performance hint session if needed.
     */
    public void cleanUpHighPerfSessionMaybe() {}
}
