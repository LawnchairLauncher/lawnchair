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

import static java.lang.Math.abs;

import android.content.Context;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.plugins.OverscrollPlugin;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Input consumer for handling events to pass to an {@code OverscrollPlugin}.
 */
public class OverscrollInputConsumer extends DelegateInputConsumer {
    private static final String TAG = "OverscrollInputConsumer";
    private static final boolean DEBUG_LOGS_ENABLED = false;
    private static void debugPrint(String log) {
        if (DEBUG_LOGS_ENABLED) {
            Log.v(TAG, log);
        }
    }

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final PointF mStartDragPos = new PointF();
    private final int mAngleThreshold;

    private final int mFlingDistanceThresholdPx;
    private final int mFlingVelocityThresholdPx;
    private int mActivePointerId = -1;
    private boolean mPassedSlop = false;
    // True if we set ourselves as active, meaning we no longer pass events to the delegate.
    private boolean mPassedActiveThreshold = false;
    private final float mSquaredActiveThreshold;
    private final float mSquaredSlop;

    private final GestureState mGestureState;
    @Nullable
    private final OverscrollPlugin mPlugin;

    @Nullable
    private RecentsView mRecentsView;

    public OverscrollInputConsumer(Context context, GestureState gestureState,
            InputConsumer delegate, InputMonitorCompat inputMonitor, OverscrollPlugin plugin) {
        super(delegate, inputMonitor);

        mAngleThreshold = context.getResources()
                .getInteger(R.integer.assistant_gesture_corner_deg_threshold);
        mFlingDistanceThresholdPx = (int) context.getResources()
                .getDimension(R.dimen.gestures_overscroll_fling_threshold);
        mFlingVelocityThresholdPx = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        mGestureState = gestureState;
        mPlugin = plugin;

        float slop = ViewConfiguration.get(context).getScaledTouchSlop();

        mSquaredSlop = slop * slop;

        float dragThreshold = (int) context.getResources()
                .getDimension(R.dimen.gestures_overscroll_drag_threshold);
        mSquaredActiveThreshold = dragThreshold * dragThreshold;
    }

    @Override
    public int getType() {
        return TYPE_OVERSCROLL | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (mPlugin == null) {
            return;
        }

        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                if (mPlugin.blockOtherGestures()) {
                    // When an Activity is visible, blocking other gestures prevents the Activity
                    // from disappearing upon ACTION_DOWN in the navigation bar. (it will reappear
                    // on ACTION_MOVE or ACTION_UP)
                    debugPrint("Becoming active on ACTION_DOWN");
                    if (mState != STATE_ACTIVE) {
                        setActive(ev);
                    }
                }
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mPlugin.onTouchEvent(ev, getHorizontalDistancePx(), getVerticalDistancePx(),
                        (int) Math.sqrt(mSquaredActiveThreshold), mFlingDistanceThresholdPx,
                        mFlingVelocityThresholdPx, getDeviceState(), getUnderlyingActivity());
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

                float squaredDist = squaredHypot(mLastPos.x - mDownPos.x, mLastPos.y - mDownPos.y);



                if (!mPassedSlop) {
                    // Normal gesture, ensure we pass the slop before we start tracking the gesture
                    if (squaredDist > mSquaredSlop) {
                        debugPrint("passed slop");
                        mPassedSlop = true;
                        mStartDragPos.set(mLastPos.x, mLastPos.y);
                        if (isOverscrolled()) {
                            debugPrint("setting STATE_OVERSCROLL_WINDOW_CREATED");
                            mGestureState.setState(GestureState.STATE_OVERSCROLL_WINDOW_CREATED);
                            if (!mPlugin.allowsUnderlyingActivityOverscroll()
                                    && (mState != STATE_ACTIVE)) {
                                debugPrint("setting active gesture handler to overscroll to "
                                        + "prevent losing active touch when Activity starts");
                                setActive(ev);
                            }
                        }
                    } else {
                        debugPrint("Not past slop");
                    }
                }

                if (mPassedSlop && !mPassedActiveThreshold && isOverscrolled()) {
                    if ((squaredDist > mSquaredActiveThreshold)) {
                        debugPrint("Past slop and past threshold, set active");

                        mPassedActiveThreshold = true;
                        if (mState != STATE_ACTIVE) {
                            setActive(ev);
                        }
                    }
                }

                if (mPassedSlop && mState != STATE_DELEGATE_ACTIVE && isOverscrolled()) {
                    debugPrint("Relaying touch event");
                    mPlugin.onTouchEvent(ev, getHorizontalDistancePx(), getVerticalDistancePx(),
                            (int) Math.sqrt(mSquaredActiveThreshold), mFlingDistanceThresholdPx,
                            mFlingVelocityThresholdPx, getDeviceState(), getUnderlyingActivity());
                }

                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP:
                if (mPassedSlop && isOverscrolled()) {
                    mPlugin.onTouchEvent(ev, getHorizontalDistancePx(), getVerticalDistancePx(),
                            (int) Math.sqrt(mSquaredActiveThreshold), mFlingDistanceThresholdPx,
                            mFlingVelocityThresholdPx, getDeviceState(), getUnderlyingActivity());
                }

                mPassedSlop = false;
                mPassedActiveThreshold = false;
                mState = STATE_INACTIVE;
                break;
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private boolean isOverscrolled() {
        if (mPlugin.blockOtherGestures()) {
            // When an Activity is visible, this `InputConsumer` immediately becomes
            // the active gesture handler to prevent the Activity from disappearing on TOUCH_DOWN
            // in the navbar.
            //
            // Returning `true` ensures that case will still result in touches being handled,
            // instead of dropping touches until the gesture reaches the thresholds calculated
            // below.
            return true;
        }

        if (mRecentsView == null) {
            BaseDraggingActivity activity = mGestureState.getActivityInterface()
                    .getCreatedActivity();
            if (activity != null) {
                mRecentsView = activity.getOverviewPanel();
            }
        }

        // Make sure there isn't an app to quick switch to on our right
        int maxIndex = 0;
        if (mRecentsView != null && mRecentsView.hasRecentsExtraCard()) {
            maxIndex = 1;
        }

        boolean atRightMostApp = (mRecentsView == null
                || mRecentsView.getRunningTaskIndex() <= maxIndex);

        // Check if the gesture is within our angle threshold of horizontal
        float deltaY = abs(mLastPos.y - mDownPos.y);
        float deltaX = abs(mDownPos.x - mLastPos.x);

        boolean angleInBounds = (Math.toDegrees(Math.atan2(deltaY, deltaX)) < mAngleThreshold);

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

    private int getHorizontalDistancePx() {
        return (int) (mLastPos.x - mDownPos.x);
    }

    private int getVerticalDistancePx() {
        return (int) (mLastPos.y - mDownPos.y);
    }

    private String getUnderlyingActivity() {
        // Overly defensive, got guidance on code review that something in the chain of
        // `mGestureState.getRunningTask().topActivity` can be null and thus cause a null pointer
        // exception to be thrown, but we aren't sure which part can be null.
        if ((mGestureState == null) || (mGestureState.getRunningTask() == null)
                || (mGestureState.getRunningTask().topActivity == null)) {
            return "";
        }
        return mGestureState.getRunningTask().topActivity.flattenToString();
    }
}
