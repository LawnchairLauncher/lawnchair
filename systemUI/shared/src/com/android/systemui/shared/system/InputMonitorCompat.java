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

import android.hardware.input.InputManagerGlobal;
import android.os.Looper;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import android.view.InputMonitor;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;

import com.android.systemui.shared.system.InputChannelCompat.InputEventListener;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;

/**
 * @see android.view.InputMonitor
 */
public class InputMonitorCompat {
    static final String TAG = "InputMonitorCompat";
    private final InputMonitor mInputMonitor;
    private final String mName;

    /**
     * Monitor input on the specified display for gestures.
     */
    public InputMonitorCompat(@NonNull String name, int displayId) {
        mName = name + "-disp" + displayId;
        mInputMonitor = InputManagerGlobal.getInstance()
                .monitorGestureInput(name, displayId);
        Trace.instant(Trace.TRACE_TAG_INPUT, "InputMonitorCompat-" + mName + " created");
        Log.d(TAG, "Input monitor (" + mName + ") created");

    }

    /**
     * @see InputMonitor#pilferPointers()
     */
    public void pilferPointers() {
        mInputMonitor.pilferPointers();
    }

    /**
     * @see InputMonitor#getSurface()
     */
    public SurfaceControl getSurface() {
        return mInputMonitor.getSurface();
    }

    /**
     * @see InputMonitor#dispose()
     */
    public void dispose() {
        mInputMonitor.dispose();
        Trace.instant(Trace.TRACE_TAG_INPUT, "InputMonitorCompat-" + mName + " disposed");
        Log.d(TAG, "Input monitor (" + mName + ") disposed");
    }

    /**
     * @see InputMonitor#getInputChannel()
     */
    public InputEventReceiver getInputReceiver(Looper looper, Choreographer choreographer,
            InputEventListener listener) {
        Trace.instant(Trace.TRACE_TAG_INPUT, "InputMonitorCompat-" + mName + " receiver created");
        Log.d(TAG, "Input event receiver for monitor (" + mName + ") created");
        return new InputEventReceiver(mName, mInputMonitor.getInputChannel(), looper, choreographer,
                listener);
    }
}
