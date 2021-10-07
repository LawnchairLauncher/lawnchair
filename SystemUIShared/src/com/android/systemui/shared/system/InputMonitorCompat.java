/**
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.systemui.shared.system;

import android.hardware.input.InputManager;
import android.os.Looper;
import android.view.Choreographer;
import android.view.InputMonitor;

import com.android.systemui.shared.system.InputChannelCompat.InputEventListener;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;

/**
 * @see android.view.InputMonitor
 */
public class InputMonitorCompat {
    private final InputMonitor mInputMonitor;

    /**
     * Monitor input on the specified display for gestures.
     */
    public InputMonitorCompat(String name, int displayId) {
        mInputMonitor = InputManager.getInstance().monitorGestureInput(name, displayId);
    }

    /**
     * @see InputMonitor#pilferPointers()
     */
    public void pilferPointers() {
        mInputMonitor.pilferPointers();
    }

    /**
     * @see InputMonitor#dispose()
     */
    public void dispose() {
        mInputMonitor.dispose();
    }

    /**
     * @see InputMonitor#getInputChannel()
     */
    public InputEventReceiver getInputReceiver(Looper looper, Choreographer choreographer,
            InputEventListener listener) {
        return new InputEventReceiver(mInputMonitor.getInputChannel(), looper, choreographer,
                listener);
    }
}
