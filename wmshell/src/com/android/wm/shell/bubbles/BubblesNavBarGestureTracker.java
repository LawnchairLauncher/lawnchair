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

package com.android.wm.shell.bubbles;

import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_BUBBLES;
import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;

import android.content.Context;
import android.hardware.input.InputManager;
import android.view.Choreographer;
import android.view.InputChannel;
import android.view.InputEventReceiver;
import android.view.InputMonitor;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.bubbles.BubblesNavBarMotionEventHandler.MotionEventListener;

/**
 * Set up tracking bubbles gestures that begin in navigation bar
 */
class BubblesNavBarGestureTracker {

    private static final String TAG = TAG_WITH_CLASS_NAME ? "BubblesGestureTracker" : TAG_BUBBLES;

    private static final String GESTURE_MONITOR = "bubbles-gesture";
    private final Context mContext;
    private final BubblePositioner mPositioner;
    private final InputManager mInputManager;

    @Nullable
    private InputMonitor mInputMonitor;
    @Nullable
    private InputEventReceiver mInputEventReceiver;

    BubblesNavBarGestureTracker(Context context, BubblePositioner positioner) {
        mContext = context;
        mInputManager = mContext.getSystemService(InputManager.class);
        mPositioner = positioner;
    }

    /**
     * Start tracking gestures
     *
     * @param listener listener that is notified of touch events
     */
    void start(MotionEventListener listener) {
        ProtoLog.d(WM_SHELL_BUBBLES, "start monitoring bubbles swipe up gesture");

        stopInternal();

        mInputMonitor = mInputManager.monitorGestureInput(GESTURE_MONITOR, mContext.getDisplayId());
        InputChannel inputChannel = mInputMonitor.getInputChannel();

        BubblesNavBarMotionEventHandler motionEventHandler =
                new BubblesNavBarMotionEventHandler(mContext, mPositioner,
                        this::onInterceptTouch, listener);
        mInputEventReceiver = new BubblesNavBarInputEventReceiver(inputChannel,
                Choreographer.getInstance(), motionEventHandler);
    }

    void stop() {
        ProtoLog.d(WM_SHELL_BUBBLES, "stop monitoring bubbles swipe up gesture");
        stopInternal();
    }

    private void stopInternal() {
        if (mInputEventReceiver != null) {
            // Clear the field before calling dispose() to guard against re-entrant calls from it
            InputEventReceiver receiver = mInputEventReceiver;
            mInputEventReceiver = null;
            receiver.dispose();
        }
        if (mInputMonitor != null) {
            // Clear the field before calling dispose() to guard against re-entrant calls from it
            InputMonitor monitor = mInputMonitor;
            mInputMonitor = null;
            monitor.dispose();
        }
    }

    private void onInterceptTouch() {
        ProtoLog.d(WM_SHELL_BUBBLES, "intercept touch event");
        if (mInputEventReceiver != null) {
            mInputManager.pilferPointers(mInputEventReceiver.getToken());
        }
    }
}
