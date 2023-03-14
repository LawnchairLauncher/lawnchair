/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.interaction;

import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.ASSISTANT_COMPLETED;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.ASSISTANT_NOT_STARTED_BAD_ANGLE;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.ASSISTANT_NOT_STARTED_SWIPE_TOO_SHORT;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.HOME_GESTURE_COMPLETED;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.HOME_NOT_STARTED_TOO_FAR_FROM_EDGE;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.HOME_OR_OVERVIEW_CANCELLED;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.OVERVIEW_GESTURE_COMPLETED;
import static com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult.OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.util.NavBarPosition;
import com.android.quickstep.util.TriggerSwipeUpTouchTracker;
import com.android.systemui.shared.system.QuickStepContract;

/** Utility class to handle Home and Assistant gestures. */
public class NavBarGestureHandler implements OnTouchListener,
        TriggerSwipeUpTouchTracker.OnSwipeUpListener, MotionPauseDetector.OnMotionPauseListener {

    private static final String LOG_TAG = "NavBarGestureHandler";
    private static final long RETRACT_GESTURE_ANIMATION_DURATION_MS = 300;

    private final Context mContext;
    private final Point mDisplaySize = new Point();
    private final TriggerSwipeUpTouchTracker mSwipeUpTouchTracker;
    private final int mBottomGestureHeight;
    private final GestureDetector mAssistantGestureDetector;
    private final int mAssistantAngleThreshold;
    private final RectF mAssistantLeftRegion = new RectF();
    private final RectF mAssistantRightRegion = new RectF();
    private final float mAssistantDragDistThreshold;
    private final float mAssistantFlingDistThreshold;
    private final long mAssistantTimeThreshold;
    private final float mAssistantSquaredSlop;
    private final PointF mAssistantStartDragPos = new PointF();
    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final MotionPauseDetector mMotionPauseDetector;
    private boolean mTouchCameFromAssistantCorner;
    private boolean mTouchCameFromNavBar;
    private boolean mPassedAssistantSlop;
    private boolean mAssistantGestureActive;
    private boolean mLaunchedAssistant;
    private long mAssistantDragStartTime;
    private float mAssistantDistance;
    private float mAssistantTimeFraction;
    private float mAssistantLastProgress;
    @Nullable
    private NavBarGestureAttemptCallback mGestureCallback;

    NavBarGestureHandler(Context context) {
        mContext = context;
        final Display display = mContext.getDisplay();
        DisplayController.Info displayInfo = DisplayController.INSTANCE.get(mContext).getInfo();
        final int displayRotation = displayInfo.rotation;
        Point currentSize = displayInfo.currentSize;
        mDisplaySize.set(currentSize.x, currentSize.y);
        mSwipeUpTouchTracker =
                new TriggerSwipeUpTouchTracker(context, true /*disableHorizontalSwipe*/,
                        new NavBarPosition(NavigationMode.NO_BUTTON, displayRotation),
                        null /*onInterceptTouch*/, this);
        mMotionPauseDetector = new MotionPauseDetector(context);

        final Resources resources = context.getResources();
        mBottomGestureHeight =
                ResourceUtils.getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE, resources);
        mAssistantDragDistThreshold =
                resources.getDimension(R.dimen.gestures_assistant_drag_threshold);
        mAssistantFlingDistThreshold =
                resources.getDimension(R.dimen.gestures_assistant_fling_threshold);
        mAssistantTimeThreshold =
                resources.getInteger(R.integer.assistant_gesture_min_time_threshold);
        mAssistantAngleThreshold =
                resources.getInteger(R.integer.assistant_gesture_corner_deg_threshold);

        mAssistantGestureDetector = new GestureDetector(context, new AssistantGestureListener());
        int assistantWidth = resources.getDimensionPixelSize(R.dimen.gestures_assistant_width);
        final float assistantHeight = Math.max(mBottomGestureHeight,
                QuickStepContract.getWindowCornerRadius(context));
        mAssistantLeftRegion.bottom = mAssistantRightRegion.bottom = mDisplaySize.y;
        mAssistantLeftRegion.top = mAssistantRightRegion.top = mDisplaySize.y - assistantHeight;
        mAssistantLeftRegion.left = 0;
        mAssistantLeftRegion.right = assistantWidth;
        mAssistantRightRegion.right = mDisplaySize.x;
        mAssistantRightRegion.left = mDisplaySize.x - assistantWidth;
        float slop = ViewConfiguration.get(context).getScaledTouchSlop();
        mAssistantSquaredSlop = slop * slop;
    }

    void registerNavBarGestureAttemptCallback(NavBarGestureAttemptCallback callback) {
        mGestureCallback = callback;
    }

    void unregisterNavBarGestureAttemptCallback() {
        mGestureCallback = null;
    }

    @Override
    public void onSwipeUp(boolean wasFling, PointF finalVelocity) {
        if (mGestureCallback == null || mAssistantGestureActive) {
            return;
        }
        if (mTouchCameFromNavBar) {
            mGestureCallback.onNavBarGestureAttempted(wasFling
                    ? HOME_GESTURE_COMPLETED : OVERVIEW_GESTURE_COMPLETED, finalVelocity);
        } else {
            mGestureCallback.onNavBarGestureAttempted(wasFling
                    ? HOME_NOT_STARTED_TOO_FAR_FROM_EDGE : OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE,
                    finalVelocity);
        }
    }

    @Override
    public void onSwipeUpCancelled() {
        if (mGestureCallback != null && !mAssistantGestureActive) {
            mGestureCallback.onNavBarGestureAttempted(HOME_OR_OVERVIEW_CANCELLED, new PointF());
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        int action = event.getAction();
        boolean intercepted = mSwipeUpTouchTracker.interceptedTouch();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownPos.set(event.getX(), event.getY());
                mLastPos.set(mDownPos);
                mTouchCameFromAssistantCorner =
                        mAssistantLeftRegion.contains(event.getX(), event.getY())
                                || mAssistantRightRegion.contains(event.getX(), event.getY());
                mAssistantGestureActive = mTouchCameFromAssistantCorner;
                mTouchCameFromNavBar = !mTouchCameFromAssistantCorner
                        && mDownPos.y >= mDisplaySize.y - mBottomGestureHeight;
                if (!mTouchCameFromNavBar && mGestureCallback != null) {
                    mGestureCallback.setNavBarGestureProgress(null);
                }
                mLaunchedAssistant = false;
                mSwipeUpTouchTracker.init();
                mMotionPauseDetector.clear();
                mMotionPauseDetector.setOnMotionPauseListener(this);
                break;
            case MotionEvent.ACTION_MOVE:
                mLastPos.set(event.getX(), event.getY());
                if (!mAssistantGestureActive) {
                    break;
                }

                if (!mPassedAssistantSlop) {
                    // Normal gesture, ensure we pass the slop before we start tracking the gesture
                    if (squaredHypot(mLastPos.x - mDownPos.x, mLastPos.y - mDownPos.y)
                            > mAssistantSquaredSlop) {

                        mPassedAssistantSlop = true;
                        mAssistantStartDragPos.set(mLastPos.x, mLastPos.y);
                        mAssistantDragStartTime = SystemClock.uptimeMillis();

                        mAssistantGestureActive = isValidAssistantGestureAngle(
                                mDownPos.x - mLastPos.x, mDownPos.y - mLastPos.y);
                        if (!mAssistantGestureActive && mGestureCallback != null) {
                            mGestureCallback.onNavBarGestureAttempted(
                                    ASSISTANT_NOT_STARTED_BAD_ANGLE, new PointF());
                        }
                    }
                } else {
                    // Movement
                    mAssistantDistance = (float) Math.hypot(mLastPos.x - mAssistantStartDragPos.x,
                            mLastPos.y - mAssistantStartDragPos.y);
                    if (mAssistantDistance >= 0) {
                        final long diff = SystemClock.uptimeMillis() - mAssistantDragStartTime;
                        mAssistantTimeFraction = Math.min(diff * 1f / mAssistantTimeThreshold, 1);
                        updateAssistantProgress();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mMotionPauseDetector.clear();
                if (mGestureCallback != null && !intercepted && mTouchCameFromNavBar) {
                    mGestureCallback.onNavBarGestureAttempted(
                            HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION, new PointF());
                    intercepted = true;
                    break;
                }
                if (mAssistantGestureActive && !mLaunchedAssistant && mGestureCallback != null) {
                    mGestureCallback.onNavBarGestureAttempted(
                            ASSISTANT_NOT_STARTED_SWIPE_TOO_SHORT, new PointF());
                    ValueAnimator animator = ValueAnimator.ofFloat(mAssistantLastProgress, 0)
                            .setDuration(RETRACT_GESTURE_ANIMATION_DURATION_MS);
                    animator.addUpdateListener(valueAnimator -> {
                        float progress = (float) valueAnimator.getAnimatedValue();
                        mGestureCallback.setAssistantProgress(progress);
                    });
                    animator.setInterpolator(Interpolators.DEACCEL_2);
                    animator.start();
                }
                mPassedAssistantSlop = false;
                break;
        }
        if (mTouchCameFromNavBar && mGestureCallback != null) {
            mGestureCallback.setNavBarGestureProgress(event.getY() - mDownPos.y);
        }
        mSwipeUpTouchTracker.onMotionEvent(event);
        mAssistantGestureDetector.onTouchEvent(event);
        mMotionPauseDetector.addPosition(event);
        mMotionPauseDetector.setDisallowPause(mLastPos.y >= mDisplaySize.y - mBottomGestureHeight);
        return intercepted;
    }

    boolean onInterceptTouch(MotionEvent event) {
        return mAssistantLeftRegion.contains(event.getX(), event.getY())
                || mAssistantRightRegion.contains(event.getX(), event.getY())
                || event.getY() >= mDisplaySize.y - mBottomGestureHeight;
    }

    @Override
    public void onMotionPauseChanged(boolean isPaused) {
        mGestureCallback.onMotionPaused(isPaused);
    }

    @Override
    public void onMotionPauseDetected() {
        VibratorWrapper.INSTANCE.get(mContext).vibrate(OVERVIEW_HAPTIC);
    }

    /**
     * Determine if angle is larger than threshold for assistant detection
     */
    private boolean isValidAssistantGestureAngle(float deltaX, float deltaY) {
        float angle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));

        // normalize so that angle is measured clockwise from horizontal in the bottom right corner
        // and counterclockwise from horizontal in the bottom left corner
        angle = angle > 90 ? 180 - angle : angle;
        return (angle > mAssistantAngleThreshold && angle < 90);
    }

    private void updateAssistantProgress() {
        if (!mLaunchedAssistant) {
            mAssistantLastProgress =
                    Math.min(mAssistantDistance * 1f / mAssistantDragDistThreshold, 1)
                            * mAssistantTimeFraction;
            if (mAssistantDistance >= mAssistantDragDistThreshold && mAssistantTimeFraction >= 1) {
                startAssistant(new PointF());
            } else if (mGestureCallback != null) {
                mGestureCallback.setAssistantProgress(mAssistantLastProgress);
            }
        }
    }

    private void startAssistant(PointF velocity) {
        if (mGestureCallback != null) {
            mGestureCallback.onNavBarGestureAttempted(ASSISTANT_COMPLETED, velocity);
        }
        VibratorWrapper.INSTANCE.get(mContext).vibrate(VibratorWrapper.EFFECT_CLICK);
        mLaunchedAssistant = true;
    }

    enum NavBarGestureResult {
        UNKNOWN,
        HOME_GESTURE_COMPLETED,
        OVERVIEW_GESTURE_COMPLETED,
        HOME_NOT_STARTED_TOO_FAR_FROM_EDGE,
        OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE,
        HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION,  // Side swipe on nav bar.
        HOME_OR_OVERVIEW_CANCELLED,
        ASSISTANT_COMPLETED,
        ASSISTANT_NOT_STARTED_BAD_ANGLE,
        ASSISTANT_NOT_STARTED_SWIPE_TOO_SHORT,
    }

    /** Callback to let the UI react to attempted nav bar gestures. */
    interface NavBarGestureAttemptCallback {
        /** Called whenever any touch is completed. */
        void onNavBarGestureAttempted(NavBarGestureResult result, PointF finalVelocity);

        /** Called when a motion stops or resumes */
        default void onMotionPaused(boolean isPaused) {}

        /** Indicates how far a touch originating in the nav bar has moved from the nav bar. */
        default void setNavBarGestureProgress(@Nullable Float displacement) {}

        /** Indicates the progress of an Assistant gesture. */
        default void setAssistantProgress(float progress) {}
    }

    private class AssistantGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (!mLaunchedAssistant && mTouchCameFromAssistantCorner) {
                PointF velocity = new PointF(velocityX, velocityY);
                if (!isValidAssistantGestureAngle(velocityX, -velocityY)) {
                    if (mGestureCallback != null) {
                        mGestureCallback.onNavBarGestureAttempted(ASSISTANT_NOT_STARTED_BAD_ANGLE,
                                velocity);
                    }
                } else if (mAssistantDistance >= mAssistantFlingDistThreshold) {
                    mAssistantLastProgress = 1;
                    startAssistant(velocity);
                }
            }
            return true;
        }
    }
}
