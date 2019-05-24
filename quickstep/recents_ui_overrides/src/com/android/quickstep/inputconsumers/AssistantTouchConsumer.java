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
import static com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction.UPLEFT;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction.UPRIGHT;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.FLING;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.SWIPE;
import static com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.SWIPE_NOOP;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.NAVBAR;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.touch.SwipeDetector;
import com.android.quickstep.ActivityControlHelper;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.shared.system.QuickStepContract;

/**
 * Touch consumer for handling events to launch assistant from launcher
 */
public class AssistantTouchConsumer extends DelegateInputConsumer
    implements SwipeDetector.Listener {

    private static final String TAG = "AssistantTouchConsumer";
    private static final long RETRACT_ANIMATION_DURATION_MS = 300;

    private static final String INVOCATION_TYPE_KEY = "invocation_type";
    private static final int INVOCATION_TYPE_GESTURE = 1;
    private static final int INVOCATION_TYPE_FLING = 6;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final PointF mStartDragPos = new PointF();

    private int mActivePointerId = -1;
    private boolean mPassedSlop;
    private boolean mLaunchedAssistant;
    private float mDistance;
    private float mTimeFraction;
    private long mDragTime;
    private float mLastProgress;
    private int mDirection;
    private ActivityControlHelper mActivityControlHelper;

    private final float mDistThreshold;
    private final long mTimeThreshold;
    private final int mAngleThreshold;
    private final float mSquaredSlop;
    private final ISystemUiProxy mSysUiProxy;
    private final Context mContext;
    private final SwipeDetector mSwipeDetector;

    public AssistantTouchConsumer(Context context, ISystemUiProxy systemUiProxy,
            ActivityControlHelper activityControlHelper, InputConsumer delegate,
            InputMonitorCompat inputMonitor) {
        super(delegate, inputMonitor);
        final Resources res = context.getResources();
        mContext = context;
        mSysUiProxy = systemUiProxy;
        mDistThreshold = res.getDimension(R.dimen.gestures_assistant_drag_threshold);
        mTimeThreshold = res.getInteger(R.integer.assistant_gesture_min_time_threshold);
        mAngleThreshold = res.getInteger(R.integer.assistant_gesture_corner_deg_threshold);

        float slop = ViewConfiguration.get(context).getScaledTouchSlop();

        mSquaredSlop = slop * slop;
        mActivityControlHelper = activityControlHelper;
        mSwipeDetector = new SwipeDetector(mContext, this, SwipeDetector.VERTICAL);
        mSwipeDetector.setDetectableScrollConditions(SwipeDetector.DIRECTION_POSITIVE, false);
    }

    @Override
    public int getType() {
        return TYPE_ASSISTANT | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        // TODO add logging
        mSwipeDetector.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mTimeFraction = 0;
                break;
            }
            case ACTION_POINTER_DOWN: {
                if (mState != STATE_ACTIVE) {
                    mState = STATE_DELEGATE_ACTIVE;
                    break;
                }
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
                        mDragTime = SystemClock.uptimeMillis();

                        // Determine if angle is larger than threshold for assistant detection
                        float angle = (float) Math.toDegrees(
                            Math.atan2(mDownPos.y - mLastPos.y, mDownPos.x - mLastPos.x));
                        mDirection = angle > 90 ? UPLEFT : UPRIGHT;
                        angle = angle > 90 ? 180 - angle : angle;

                        if (angle > mAngleThreshold && angle < 90) {
                            setActive(ev);
                        } else {
                            mState = STATE_DELEGATE_ACTIVE;
                        }
                    }
                } else {
                    // Movement
                    mDistance = (float) Math.hypot(mLastPos.x - mStartDragPos.x,
                        mLastPos.y - mStartDragPos.y);
                    if (mDistance >= 0) {
                        final long diff = SystemClock.uptimeMillis() - mDragTime;
                        mTimeFraction = Math.min(diff * 1f / mTimeThreshold, 1);
                        updateAssistantProgress();
                    }
                }
                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP:
                if (mState != STATE_DELEGATE_ACTIVE && !mLaunchedAssistant) {
                    ValueAnimator animator = ValueAnimator.ofFloat(mLastProgress, 0)
                        .setDuration(RETRACT_ANIMATION_DURATION_MS);
                    UserEventDispatcher.newInstance(mContext).logActionOnContainer(
                        SWIPE_NOOP, mDirection, NAVBAR);
                    animator.addUpdateListener(valueAnimator -> {
                        float progress = (float) valueAnimator.getAnimatedValue();
                        try {

                            mSysUiProxy.onAssistantProgress(progress);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Failed to send SysUI start/send assistant progress: "
                                + progress, e);
                        }
                    });
                    animator.setInterpolator(Interpolators.DEACCEL_2);
                    animator.start();
                }
                mPassedSlop = false;
                mState = STATE_INACTIVE;
                break;
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private void updateAssistantProgress() {
        if (!mLaunchedAssistant) {
            mLastProgress = Math.min(mDistance * 1f / mDistThreshold, 1) * mTimeFraction;
            try {
                if (mDistance >= mDistThreshold && mTimeFraction >= 1) {
                    mSysUiProxy.onAssistantGestureCompletion(0);
                    startAssistantInternal(SWIPE);

                    Bundle args = new Bundle();
                    args.putInt(INVOCATION_TYPE_KEY, INVOCATION_TYPE_GESTURE);
                    mSysUiProxy.startAssistant(args);
                    mLaunchedAssistant = true;
                } else {
                    mSysUiProxy.onAssistantProgress(mLastProgress);
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send SysUI start/send assistant progress: " + mLastProgress,
                    e);
            }
        }
    }

    private void startAssistantInternal(int gestureType) {
        UserEventDispatcher.newInstance(mContext)
            .logActionOnContainer(gestureType, mDirection, NAVBAR);

        BaseDraggingActivity launcherActivity = mActivityControlHelper
            .getCreatedActivity();
        if (launcherActivity != null) {
            launcherActivity.getRootView().performHapticFeedback(
                13, // HapticFeedbackConstants.GESTURE_END
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }

    public static boolean withinTouchRegion(Context context, MotionEvent ev) {
        final Resources res = context.getResources();
        final int width = res.getDisplayMetrics().widthPixels;
        final int height = res.getDisplayMetrics().heightPixels;
        final int size = res.getDimensionPixelSize(R.dimen.gestures_assistant_size);
        return (ev.getX() > width - size || ev.getX() < size) && ev.getY() > height - size;
    }

    @Override
    public void onDragStart(boolean start) {
        // do nothing
    }

    @Override
    public boolean onDrag(float displacement) {
        return false;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        if (fling && !mLaunchedAssistant && mState != STATE_DELEGATE_ACTIVE) {
            mLastProgress = 1;
            try {
                mSysUiProxy.onAssistantGestureCompletion(velocity);
                startAssistantInternal(FLING);

                Bundle args = new Bundle();
                args.putInt(INVOCATION_TYPE_KEY, INVOCATION_TYPE_GESTURE);
                mSysUiProxy.startAssistant(args);
                mLaunchedAssistant = true;
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send SysUI start/send assistant progress: " + mLastProgress,
                    e);
            }
        }
    }
}
