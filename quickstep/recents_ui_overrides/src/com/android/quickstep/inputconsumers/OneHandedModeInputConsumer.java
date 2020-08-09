/*
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

package com.android.quickstep.inputconsumers;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.Utilities.squaredHypot;

import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.SystemUiProxy;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Touch consumer for handling gesture event to launch one handed
 * One handed gestural in quickstep only active on NO_BUTTON, TWO_BUTTONS, and portrait mode
 */
public class OneHandedModeInputConsumer extends DelegateInputConsumer {

    private static final String TAG = "OneHandedModeInputConsumer";
    private static final int ANGLE_MAX = 150;
    private static final int ANGLE_MIN = 30;

    private final Context mContext;
    private final RecentsAnimationDeviceState mDeviceState;

    private final float mDragDistThreshold;
    private final float mSquaredSlop;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();

    private boolean mPassedSlop;

    public OneHandedModeInputConsumer(Context context, RecentsAnimationDeviceState deviceState,
            InputConsumer delegate, InputMonitorCompat inputMonitor) {
        super(delegate, inputMonitor);
        mContext = context;
        mDeviceState = deviceState;
        mDragDistThreshold = context.getResources().getDimensionPixelSize(
                R.dimen.gestures_onehanded_drag_threshold);
        mSquaredSlop = Utilities.squaredTouchSlop(context);
    }

    @Override
    public int getType() {
        return TYPE_ONE_HANDED | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                break;
            }
            case ACTION_MOVE: {
                if (mState == STATE_DELEGATE_ACTIVE) {
                    break;
                }
                if (!mDelegate.allowInterceptByParent()) {
                    mState = STATE_DELEGATE_ACTIVE;
                    break;
                }

                mLastPos.set(ev.getX(), ev.getY());
                if (!mPassedSlop) {
                    if (squaredHypot(mLastPos.x - mDownPos.x, mLastPos.y - mDownPos.y)
                            > mSquaredSlop) {
                        if ((!mDeviceState.isOneHandedModeActive() && isValidStartAngle(
                                mDownPos.x - mLastPos.x, mDownPos.y - mLastPos.y))
                                || (mDeviceState.isOneHandedModeActive() && isValidExitAngle(
                                mDownPos.x - mLastPos.x, mDownPos.y - mLastPos.y))) {
                            mPassedSlop = true;
                            setActive(ev);
                        } else {
                            mState = STATE_DELEGATE_ACTIVE;
                        }
                    }
                } else {
                    float distance = (float) Math.hypot(mLastPos.x - mDownPos.x,
                            mLastPos.y - mDownPos.y);
                    if (distance > mDragDistThreshold && mPassedSlop) {
                        onStopGestureDetected();
                    }
                }
                break;
            }
            case ACTION_UP: {
                if (mLastPos.y >= mDownPos.y && mPassedSlop) {
                    onStartGestureDetected();
                }

                mPassedSlop = false;
                mState = STATE_INACTIVE;
                break;
            }
            case ACTION_CANCEL:
                mPassedSlop = false;
                mState = STATE_INACTIVE;
                break;
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private void onStartGestureDetected() {
        if (mDeviceState.isOneHandedModeEnabled()) {
            if (!mDeviceState.isOneHandedModeActive()) {
                SystemUiProxy.INSTANCE.get(mContext).startOneHandedMode();
            }
        } else if (mDeviceState.isSwipeToNotificationEnabled()) {
            SystemUiProxy.INSTANCE.get(mContext).expandNotificationPanel();
        }
    }

    private void onStopGestureDetected() {
        if (!mDeviceState.isOneHandedModeEnabled() || !mDeviceState.isOneHandedModeActive()) {
            return;
        }

        SystemUiProxy.INSTANCE.get(mContext).stopOneHandedMode();
    }

    private boolean isValidStartAngle(float deltaX, float deltaY) {
        final float angle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
        return angle > -(ANGLE_MAX) && angle < -(ANGLE_MIN);
    }

    private boolean isValidExitAngle(float deltaX, float deltaY) {
        final float angle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
        return angle > ANGLE_MIN && angle < ANGLE_MAX;
    }
}
