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

import static com.android.launcher3.Utilities.squaredHypot;

import android.content.Context;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

import com.android.launcher3.Utilities;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.quickstep.InputConsumer;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for a long press, and cancels the current gesture if that causes Taskbar to be unstashed.
 */
public class TaskbarStashInputConsumer extends DelegateInputConsumer {

    private final TaskbarActivityContext mTaskbarActivityContext;
    private final GestureDetector mLongPressDetector;
    private final float mSquaredTouchSlop;

    private float mDownX, mDownY;
    private boolean mCanceledUnstashHint;

    public TaskbarStashInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor, TaskbarActivityContext taskbarActivityContext) {
        super(delegate, inputMonitor);
        mTaskbarActivityContext = taskbarActivityContext;
        mSquaredTouchSlop = Utilities.squaredTouchSlop(context);

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

            if (mTaskbarActivityContext != null) {
                final float x = ev.getRawX();
                final float y = ev.getRawY();
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mDownX = x;
                        mDownY = y;
                        mTaskbarActivityContext.startTaskbarUnstashHint(
                                /* animateForward = */ true);
                        mCanceledUnstashHint = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!mCanceledUnstashHint
                                && squaredHypot(mDownX - x, mDownY - y) > mSquaredTouchSlop) {
                            mTaskbarActivityContext.startTaskbarUnstashHint(
                                    /* animateForward = */ false);
                            mCanceledUnstashHint = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!mCanceledUnstashHint) {
                            mTaskbarActivityContext.startTaskbarUnstashHint(
                                    /* animateForward = */ false);
                        }
                        break;
                }
            }
        }
    }

    private void onLongPressDetected(MotionEvent motionEvent) {
        if (mTaskbarActivityContext != null
                && mTaskbarActivityContext.onLongPressToUnstashTaskbar()) {
            setActive(motionEvent);
        }
    }
}
