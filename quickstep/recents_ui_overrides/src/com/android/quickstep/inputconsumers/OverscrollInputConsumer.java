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

import static com.android.launcher3.Utilities.squaredHypot;

import android.content.Context;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseDraggingActivity;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.plugins.OverscrollPlugin;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Input consumer for handling events to pass to an {@code OverscrollPlugin}.
 *
 * @param <T> Draggable activity subclass used by RecentsView
 */
public class OverscrollInputConsumer<T extends BaseDraggingActivity> extends DelegateInputConsumer {

    private static final String TAG = "OverscrollInputConsumer";

    private static final int ANGLE_THRESHOLD = 35; // Degrees

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final PointF mStartDragPos = new PointF();

    private int mActivePointerId = -1;
    private boolean mPassedSlop = false;

    private final float mSquaredSlop;

    private final Context mContext;
    private final GestureState mGestureState;
    @Nullable private final OverscrollPlugin mPlugin;

    private RecentsView mRecentsView;

    public OverscrollInputConsumer(Context context, GestureState gestureState,
            InputConsumer delegate, InputMonitorCompat inputMonitor, OverscrollPlugin plugin) {
        super(delegate, inputMonitor);
        mContext = context;
        mGestureState = gestureState;
        mPlugin = plugin;

        float slop = ViewConfiguration.get(context).getScaledTouchSlop();
        mSquaredSlop = slop * slop;

        gestureState.getActivityInterface().createActivityInitListener(this::onActivityInit)
                .register();
    }

    @Override
    public int getType() {
        return TYPE_OVERSCROLL | mDelegate.getType();
    }

    private boolean onActivityInit(Boolean alreadyOnHome) {
        mRecentsView = mGestureState.getActivityInterface().getCreatedActivity().getOverviewPanel();

        return true;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);

                break;
            }
            case ACTION_POINTER_DOWN: {
                if (mState != STATE_ACTIVE) {
                    mState = STATE_DELEGATE_ACTIVE;
                }
                break;
            }
            case ACTION_POINTER_UP: {
                int ptrIdx = ev.getActionIndex();
                int ptrId = ev.getPointerId(ptrIdx);
                if (ptrId == mActivePointerId) {
                    final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
                    mDownPos.set(
                            ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x),
                            ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
                    mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));
                    mActivePointerId = ev.getPointerId(newPointerIdx);
                }
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
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == -1) {
                    break;
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                if (!mPassedSlop) {
                    // Normal gesture, ensure we pass the slop before we start tracking the gesture
                    if (squaredHypot(mLastPos.x - mDownPos.x, mLastPos.y - mDownPos.y)
                            > mSquaredSlop) {

                        mPassedSlop = true;
                        mStartDragPos.set(mLastPos.x, mLastPos.y);

                        if (isOverscrolled()) {
                            setActive(ev);
                        } else {
                            mState = STATE_DELEGATE_ACTIVE;
                        }
                    }
                }

                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP:
                if (mState != STATE_DELEGATE_ACTIVE && mPassedSlop && mPlugin != null) {
                    mPlugin.onOverscroll(getDeviceState());
                }

                mPassedSlop = false;
                mState = STATE_INACTIVE;
                break;
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private boolean isOverscrolled() {
        // Make sure there isn't an app to quick switch to on our right
        boolean atRightMostApp = (mRecentsView == null || mRecentsView.getRunningTaskIndex() <= 0);

        // Check if the gesture is within our angle threshold of horizontal
        float deltaY = Math.abs(mLastPos.y - mDownPos.y);
        float deltaX = mDownPos.x - mLastPos.x; // Positive if this is a gesture to the left
        boolean angleInBounds = Math.toDegrees(Math.atan2(deltaY, deltaX)) < ANGLE_THRESHOLD;

        return atRightMostApp && angleInBounds;
    }

    private String getDeviceState() {
        String deviceState = OverscrollPlugin.DEVICE_STATE_UNKNOWN;
        int consumerType = mDelegate.getType();
        if (((consumerType & InputConsumer.TYPE_OVERVIEW) > 0)
                || ((consumerType & InputConsumer.TYPE_OVERVIEW_WITHOUT_FOCUS)) > 0) {
            deviceState = OverscrollPlugin.DEVICE_STATE_LAUNCHER;
        } else if ((consumerType & InputConsumer.TYPE_OTHER_ACTIVITY) > 0) {
            deviceState = OverscrollPlugin.DEVICE_STATE_APP;
        } else if (((consumerType & InputConsumer.TYPE_RESET_GESTURE) > 0)
                || ((consumerType & InputConsumer.TYPE_DEVICE_LOCKED) > 0)) {
            deviceState = OverscrollPlugin.DEVICE_STATE_LOCKED;
        }

        return deviceState;
    }
}
