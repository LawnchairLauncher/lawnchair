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
package com.android.quickstep.inputconsumers;

import android.content.Context;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

import com.android.quickstep.BaseActivityInterface;
import com.android.quickstep.InputConsumer;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for a long press, and cancels the current gesture if that causes Taskbar to be unstashed.
 */
public class TaskbarStashInputConsumer extends DelegateInputConsumer {

    private final BaseActivityInterface mActivityInterface;
    private final GestureDetector mLongPressDetector;

    public TaskbarStashInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor, BaseActivityInterface activityInterface) {
        super(delegate, inputMonitor);
        mActivityInterface = activityInterface;

        mLongPressDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent motionEvent) {
                onLongPressDetected(motionEvent);
            }
        });
    }

    @Override
    public int getType() {
        return TYPE_TASKBAR_STASH | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        mLongPressDetector.onTouchEvent(ev);
        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private void onLongPressDetected(MotionEvent motionEvent) {
        if (mActivityInterface.onLongPressToUnstashTaskbar()) {
            setActive(motionEvent);
        }
    }
}
