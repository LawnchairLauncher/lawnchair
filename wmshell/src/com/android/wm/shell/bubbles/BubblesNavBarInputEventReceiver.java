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

import android.os.Looper;
import android.view.BatchedInputEventReceiver;
import android.view.Choreographer;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.MotionEvent;

/**
 * Bubbles {@link BatchedInputEventReceiver} for monitoring touches from navbar gesture area
 */
class BubblesNavBarInputEventReceiver extends BatchedInputEventReceiver {

    private final BubblesNavBarMotionEventHandler mMotionEventHandler;

    BubblesNavBarInputEventReceiver(InputChannel inputChannel,
            Choreographer choreographer, BubblesNavBarMotionEventHandler motionEventHandler) {
        super(inputChannel, Looper.myLooper(), choreographer);
        mMotionEventHandler = motionEventHandler;
    }

    @Override
    public void onInputEvent(InputEvent event) {
        boolean handled = false;
        try {
            if (!(event instanceof MotionEvent)) {
                return;
            }
            handled = mMotionEventHandler.onMotionEvent((MotionEvent) event);
        } finally {
            finishInputEvent(event, handled);
        }
    }
}
