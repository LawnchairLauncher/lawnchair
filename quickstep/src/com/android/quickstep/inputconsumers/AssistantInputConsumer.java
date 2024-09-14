
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

import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_GESTURE;
import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_KEY;
import static com.android.launcher3.Utilities.squaredHypot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.system.InputMonitorCompat;

import java.util.function.Consumer;

/**
 * Touch consumer for handling events to launch assistant from launcher
 */
public class AssistantInputConsumer extends DelegateInputConsumer {

    private static final String TAG = "AssistantInputConsumer";
    private static final long RETRACT_ANIMATION_DURATION_MS = 300;

    // From //java/com/google/android/apps/gsa/search/shared/util/OpaContract.java.
    private static final String OPA_BUNDLE_TRIGGER = "triggered_by";
    // From //java/com/google/android/apps/gsa/assistant/shared/proto/opa_trigger.proto.
    private static final int OPA_BUNDLE_TRIGGER_DIAG_SWIPE_GESTURE = 83;

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
    private BaseContainerInterface mContainerInterface;

    private final float mDragDistThreshold;
    private final float mFlingDistThreshold;
    private final long mTimeThreshold;
    private final int mAngleThreshold;
    private final float mSquaredSlop;
    private final Context mContext;
    private final Consumer<MotionEvent> mGestureDetector;

    public AssistantInputConsumer(
            Context context,
            GestureState gestureState,
            InputConsumer delegate,
            InputMonitorCompat inputMonitor,
            RecentsAnimationDeviceState deviceState,
            MotionEvent startEvent) {
        super(delegate, inputMonitor);
        final Resources res = context.getResources();
        mContext = context;
        mDragDistThreshold = res.getDimension(R.dimen.gestures_assistant_drag_threshold);
        mFlingDistThreshold = res.getDimension(R.dimen.gestures_assistant_fling_threshold);
        mTimeThreshold = res.getInteger(R.integer.assistant_gesture_min_time_threshold);
        mAngleThreshold = res.getInteger(R.integer.assistant_gesture_corner_deg_threshold);

        float slop = ViewConfiguration.get(context).getScaledTouchSlop();

        mSquaredSlop = slop * slop;
        mContainerInterface = gestureState.getContainerInterface();

        boolean flingDisabled = deviceState.isAssistantGestureIsConstrained()
                || deviceState.isInDeferredGestureRegion(startEvent);
        mGestureDetector = flingDisabled
                ? ev -> { }
                : new GestureDetector(context, new AssistantGestureListener())::onTouchEvent;
    }

    @Override
    public int getType() {
        return TYPE_ASSISTANT | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        // TODO add logging
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
                        mDragTime = SystemClock.uptimeMillis();

                        if (isValidAssistantGestureAngle(
                            mDownPos.x - mLastPos.x, mDownPos.y - mLastPos.y)) {
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
                    animator.addUpdateListener(valueAnimator -> {
                        float progress = (float) valueAnimator.getAnimatedValue();
                        SystemUiProxy.INSTANCE.get(mContext).onAssistantProgress(progress);
                    });
                    // Ensure that we always send a zero at the end to clear the invocation state.
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            SystemUiProxy.INSTANCE.get(mContext).onAssistantProgress(0f);
                        }
                    });
                    animator.setInterpolator(Interpolators.DECELERATE_2);
                    animator.start();
                }
                mPassedSlop = false;
                mState = STATE_INACTIVE;
                break;
        }

        mGestureDetector.accept(ev);

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private void updateAssistantProgress() {
        if (!mLaunchedAssistant) {
            mLastProgress = Math.min(mDistance * 1f / mDragDistThreshold, 1) * mTimeFraction;
            if (mDistance >= mDragDistThreshold && mTimeFraction >= 1) {
                SystemUiProxy.INSTANCE.get(mContext).onAssistantGestureCompletion(0);
                startAssistantInternal();
            } else {
                SystemUiProxy.INSTANCE.get(mContext).onAssistantProgress(mLastProgress);
            }
        }
    }

    private void startAssistantInternal() {
        RecentsViewContainer container = mContainerInterface.getCreatedContainer();
        if (container != null) {
            container.getRootView().performHapticFeedback(
                13, // HapticFeedbackConstants.GESTURE_END
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }

        Bundle args = new Bundle();
        args.putInt(OPA_BUNDLE_TRIGGER, OPA_BUNDLE_TRIGGER_DIAG_SWIPE_GESTURE);
        args.putInt(INVOCATION_TYPE_KEY, INVOCATION_TYPE_GESTURE);
        SystemUiProxy.INSTANCE.get(mContext).startAssistant(args);
        mLaunchedAssistant = true;
    }

    /**
     * Determine if angle is larger than threshold for assistant detection
     */
    private boolean isValidAssistantGestureAngle(float deltaX, float deltaY) {
        float angle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));

        // normalize so that angle is measured clockwise from horizontal in the bottom right corner
        // and counterclockwise from horizontal in the bottom left corner
        angle = angle > 90 ? 180 - angle : angle;
        return (angle > mAngleThreshold && angle < 90);
    }

    private class AssistantGestureListener extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (isValidAssistantGestureAngle(velocityX, -velocityY)
                    && mDistance >= mFlingDistThreshold
                    && !mLaunchedAssistant
                    && mState != STATE_DELEGATE_ACTIVE) {
                mLastProgress = 1;
                SystemUiProxy.INSTANCE.get(mContext).onAssistantGestureCompletion(
                    (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY));
                startAssistantInternal();
            }
            return true;
        }
    }

    @Override
    protected String getDelegatorName() {
        return "AssistantInputConsumer";
    }
}
