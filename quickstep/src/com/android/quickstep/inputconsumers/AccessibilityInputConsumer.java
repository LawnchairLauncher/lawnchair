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
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import android.content.Context;
import android.view.Display;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import com.android.launcher3.R;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Input consumer for two finger swipe actions for accessibility actions
 */
public class AccessibilityInputConsumer extends DelegateInputConsumer {

    private static final String TAG = "A11yInputConsumer";

    private final Context mContext;
    private final VelocityTracker mVelocityTracker;
    private final MotionPauseDetector mMotionPauseDetector;
    private final RecentsAnimationDeviceState mDeviceState;
    private final GestureState mGestureState;

    private final float mMinGestureDistance;
    private final float mMinFlingVelocity;

    private int mActivePointerId = -1;
    private float mDownY;
    private float mTotalY;

    public AccessibilityInputConsumer(Context context, RecentsAnimationDeviceState deviceState,
            GestureState gestureState, InputConsumer delegate, InputMonitorCompat inputMonitor) {
        super(delegate, inputMonitor);
        mContext = context;
        mVelocityTracker = VelocityTracker.obtain();
        mMinGestureDistance = context.getResources()
                .getDimension(R.dimen.accessibility_gesture_min_swipe_distance);
        mMinFlingVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        mDeviceState = deviceState;
        mGestureState = gestureState;

        mMotionPauseDetector = new MotionPauseDetector(context);
    }

    @Override
    public int getType() {
        return TYPE_ACCESSIBILITY | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (mState != STATE_DELEGATE_ACTIVE) {
            mVelocityTracker.addMovement(ev);
        }

        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                break;
            }
            case ACTION_POINTER_UP: {
                if (mState == STATE_ACTIVE) {
                    int pointerIndex = ev.getActionIndex();
                    int pointerId = ev.getPointerId(pointerIndex);
                    if (pointerId == mActivePointerId) {
                        final int newPointerIdx = pointerIndex == 0 ? 1 : 0;

                        mTotalY += (ev.getY(pointerIndex) - mDownY);
                        mDownY = ev.getY(newPointerIdx);
                        mActivePointerId = ev.getPointerId(newPointerIdx);
                    }
                }
                break;
            }
            case ACTION_POINTER_DOWN: {
                if (mState == STATE_INACTIVE) {
                    int pointerIndex = ev.getActionIndex();
                    if (mDeviceState.getRotationTouchHelper().isInSwipeUpTouchRegion(ev,
                            pointerIndex) && mDelegate.allowInterceptByParent()) {
                        setActive(ev);

                        mActivePointerId = ev.getPointerId(pointerIndex);
                        mDownY = ev.getY(pointerIndex);
                    } else {
                        mState = STATE_DELEGATE_ACTIVE;
                    }
                }
                break;
            }
            case ACTION_MOVE: {
                if (mState == STATE_ACTIVE && mDeviceState.isAccessibilityMenuShortcutAvailable()) {
                    int pointerIndex = ev.findPointerIndex(mActivePointerId);
                    if (pointerIndex == -1) {
                        break;
                    }
                    mMotionPauseDetector.addPosition(ev, pointerIndex);
                }
                break;
            }
            case ACTION_UP:
                if (mState == STATE_ACTIVE) {
                    if (mDeviceState.isAccessibilityMenuShortcutAvailable()
                            && mMotionPauseDetector.isPaused()) {
                        SystemUiProxy.INSTANCE.get(mContext).notifyAccessibilityButtonLongClicked();
                    } else {
                        mTotalY += (ev.getY() - mDownY);
                        mVelocityTracker.computeCurrentVelocity(1000);

                        if ((-mTotalY) > mMinGestureDistance
                                || (-mVelocityTracker.getYVelocity()) > mMinFlingVelocity) {
                            SystemUiProxy.INSTANCE.get(mContext).notifyAccessibilityButtonClicked(
                                    Display.DEFAULT_DISPLAY);
                        }
                    }
                }
                // Follow through
            case ACTION_CANCEL: {
                mVelocityTracker.recycle();
                mMotionPauseDetector.clear();
                break;
            }
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    @Override
    protected String getDelegatorName() {
        return "AccessibilityInputConsumer";
    }
}
