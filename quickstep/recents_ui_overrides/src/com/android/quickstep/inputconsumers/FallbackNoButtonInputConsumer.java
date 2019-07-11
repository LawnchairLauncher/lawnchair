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
import static android.view.MotionEvent.INVALID_POINTER_ID;

import static com.android.quickstep.RecentsActivity.EXTRA_TASK_ID;
import static com.android.quickstep.RecentsActivity.EXTRA_THUMBNAIL;
import static com.android.quickstep.WindowTransformSwipeHandler.MIN_PROGRESS_FOR_OVERVIEW;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.HOME;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.inputconsumers.FallbackNoButtonInputConsumer.GestureEndTarget.RECENTS;
import static com.android.quickstep.inputconsumers.OtherActivityInputConsumer.QUICKSTEP_TOUCH_SLOP_RATIO;
import static com.android.quickstep.TouchInteractionService.startRecentsActivityAsync;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.quickstep.ActivityControlHelper;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.BaseSwipeUpHandler;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.SwipeSharedState;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.util.NavBarPosition;
import com.android.quickstep.util.ObjectWrapper;
import com.android.quickstep.util.RecentsAnimationListenerSet;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet.SwipeAnimationListener;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

public class FallbackNoButtonInputConsumer extends BaseSwipeUpHandler
        implements InputConsumer, SwipeAnimationListener {

    public enum GestureEndTarget {
        HOME(3, 100, 1),
        RECENTS(1, 300, 0),
        LAST_TASK(0, 150, 1);

        private final float mEndProgress;
        private final long mDurationMultiplier;
        private final float mLauncherAlpha;
        GestureEndTarget(float endProgress, long durationMultiplier, float launcherAlpha) {
            mEndProgress = endProgress;
            mDurationMultiplier = durationMultiplier;
            mLauncherAlpha = launcherAlpha;
        }
    }

    private final ActivityControlHelper mActivityControlHelper;
    private final InputMonitorCompat mInputMonitor;
    private final NavBarPosition mNavBarPosition;
    private final SwipeSharedState mSwipeSharedState;
    private final OverviewComponentObserver mOverviewComponentObserver;
    private final int mRunningTaskId;

    private final DeviceProfile mDP;
    private final MotionPauseDetector mMotionPauseDetector;
    private final float mMotionPauseMinDisplacement;
    private final Rect mTargetRect = new Rect();

    private final RectF mSwipeTouchRegion;
    private final boolean mDisableHorizontalSwipe;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();

    private final AnimatedFloat mLauncherAlpha = new AnimatedFloat(this::onLauncherAlphaChanged);
    private final AnimatedFloat mProgress = new AnimatedFloat(this::onProgressChanged);

    private int mActivePointerId = -1;
    // Slop used to determine when we say that the gesture has started.
    private boolean mPassedPilferInputSlop;

    private VelocityTracker mVelocityTracker;

    // Distance after which we start dragging the window.
    private final float mTouchSlop;

    // Might be displacement in X or Y, depending on the direction we are swiping from the nav bar.
    private float mStartDisplacement;
    private SwipeAnimationTargetSet mSwipeAnimationTargetSet;

    private boolean mIsMotionPaused = false;
    private GestureEndTarget mEndTarget;

    public FallbackNoButtonInputConsumer(Context context,
            ActivityControlHelper activityControlHelper, InputMonitorCompat inputMonitor,
            SwipeSharedState swipeSharedState, RectF swipeTouchRegion,
            OverviewComponentObserver overviewComponentObserver,
            boolean disableHorizontalSwipe, RunningTaskInfo runningTaskInfo) {
        super(context);
        mActivityControlHelper = activityControlHelper;
        mInputMonitor = inputMonitor;
        mOverviewComponentObserver = overviewComponentObserver;
        mRunningTaskId = runningTaskInfo.id;

        mMotionPauseDetector = new MotionPauseDetector(context);
        mMotionPauseMinDisplacement = context.getResources().getDimension(
                R.dimen.motion_pause_detector_min_displacement_from_app);
        mMotionPauseDetector.setOnMotionPauseListener(this::onMotionPauseChanged);

        mSwipeSharedState = swipeSharedState;
        mSwipeTouchRegion = swipeTouchRegion;
        mDisableHorizontalSwipe = disableHorizontalSwipe;

        mNavBarPosition = new NavBarPosition(context);
        mVelocityTracker = VelocityTracker.obtain();

        mTouchSlop = QUICKSTEP_TOUCH_SLOP_RATIO
                * ViewConfiguration.get(context).getScaledTouchSlop();

        mDP = InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context).copy(context);
        mLauncherAlpha.value = 1;

        mClipAnimationHelper.setBaseAlphaCallback((t, a) -> mLauncherAlpha.value);
        initTransitionTarget();
    }

    @Override
    public int getType() {
        return TYPE_FALLBACK_NO_BUTTON;
    }

    private void onLauncherAlphaChanged() {
        if (mSwipeAnimationTargetSet != null && mEndTarget == null) {
            mClipAnimationHelper.applyTransform(mSwipeAnimationTargetSet, mTransformParams);
        }
    }

    private void onMotionPauseChanged(boolean isPaused) {
        mIsMotionPaused = isPaused;
        mLauncherAlpha.animateToValue(mLauncherAlpha.value, isPaused ? 0 : 1)
                .setDuration(150).start();
        performHapticFeedback();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            return;
        }

        mVelocityTracker.addMovement(ev);
        if (ev.getActionMasked() == ACTION_POINTER_UP) {
            mVelocityTracker.clear();
            mMotionPauseDetector.clear();
        }

        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                break;
            }
            case ACTION_POINTER_DOWN: {
                if (!mPassedPilferInputSlop) {
                    // Cancel interaction in case of multi-touch interaction
                    int ptrIdx = ev.getActionIndex();
                    if (!mSwipeTouchRegion.contains(ev.getX(ptrIdx), ev.getY(ptrIdx))) {
                        forceCancelGesture(ev);
                    }
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
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    break;
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));
                float displacement = getDisplacement(ev);

                if (!mPassedPilferInputSlop) {
                    if (mDisableHorizontalSwipe && Math.abs(mLastPos.x - mDownPos.x)
                            > Math.abs(mLastPos.y - mDownPos.y)) {
                        // Horizontal gesture is not allowed in this region
                        forceCancelGesture(ev);
                        break;
                    }

                    if (Math.abs(displacement) >= mTouchSlop) {
                        mPassedPilferInputSlop = true;

                        // Deferred gesture, start the animation and gesture tracking once
                        // we pass the actual touch slop
                        startTouchTrackingForWindowAnimation(displacement);
                    }
                } else {
                    updateDisplacement(displacement - mStartDisplacement);
                    mMotionPauseDetector.setDisallowPause(
                            -displacement < mMotionPauseMinDisplacement);
                    mMotionPauseDetector.addPosition(displacement, ev.getEventTime());
                }
                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP: {
                finishTouchTracking(ev);
                break;
            }
        }
    }

    private void startTouchTrackingForWindowAnimation(float displacement) {
        mStartDisplacement = Math.min(displacement, -mTouchSlop);

        RecentsAnimationListenerSet listenerSet =
                mSwipeSharedState.newRecentsAnimationListenerSet();
        listenerSet.addListener(this);
        Intent homeIntent = mOverviewComponentObserver.getHomeIntent();
        startRecentsActivityAsync(homeIntent, listenerSet);

        ActivityManagerWrapper.getInstance().closeSystemWindows(
                CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
        mInputMonitor.pilferPointers();
    }

    private void updateDisplacement(float displacement) {
        mProgress.updateValue(getShiftForDisplacement(displacement));
    }

    private void onProgressChanged() {
        mTransformParams.setProgress(mProgress.value);
        if (mSwipeAnimationTargetSet != null) {
            mClipAnimationHelper.applyTransform(mSwipeAnimationTargetSet, mTransformParams);
        }
    }

    private void forceCancelGesture(MotionEvent ev) {
        int action = ev.getAction();
        ev.setAction(ACTION_CANCEL);
        finishTouchTracking(ev);
        ev.setAction(action);
    }

    /**
     * Called when the gesture has ended. Does not correlate to the completion of the interaction as
     * the animation can still be running.
     */
    private void finishTouchTracking(MotionEvent ev) {
        if (ev.getAction() == ACTION_CANCEL) {
            mEndTarget = LAST_TASK;
        } else {
            mVelocityTracker.computeCurrentVelocity(1000,
                    ViewConfiguration.get(mContext).getScaledMaximumFlingVelocity());
            float velocityX = mVelocityTracker.getXVelocity(mActivePointerId);
            float velocityY = mVelocityTracker.getYVelocity(mActivePointerId);
            float velocity = mNavBarPosition.isRightEdge() ? velocityX
                    : mNavBarPosition.isLeftEdge() ? -velocityX
                            : velocityY;
            float flingThreshold = mContext.getResources()
                    .getDimension(R.dimen.quickstep_fling_threshold_velocity);
            boolean isFling = Math.abs(velocity) > flingThreshold;

            if (isFling) {
                mEndTarget = velocity < 0 ? HOME : LAST_TASK;
            } else if (mIsMotionPaused) {
                mEndTarget = RECENTS;
            } else {
                mEndTarget = mProgress.value >= MIN_PROGRESS_FOR_OVERVIEW ? HOME : LAST_TASK;
            }
        }

        if (mSwipeAnimationTargetSet != null) {
            finishAnimationTargetSet();
        }
        mMotionPauseDetector.clear();
    }

    private void finishAnimationTargetSetAnimationComplete() {
        switch (mEndTarget) {
            case HOME:
                mSwipeAnimationTargetSet.finishController(true, null, true);
                break;
            case LAST_TASK:
                mSwipeAnimationTargetSet.finishController(false, null, false);
                break;
            case RECENTS: {
                ThumbnailData thumbnail =
                        mSwipeAnimationTargetSet.controller.screenshotTask(mRunningTaskId);
                mSwipeAnimationTargetSet.controller.setCancelWithDeferredScreenshot(true);

                ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
                Bundle extras = new Bundle();
                extras.putBinder(EXTRA_THUMBNAIL, new ObjectWrapper<>(thumbnail));
                extras.putInt(EXTRA_TASK_ID, mRunningTaskId);

                Intent intent = new Intent(mOverviewComponentObserver.getOverviewIntent())
                        .putExtras(extras);
                mContext.startActivity(intent, options.toBundle());
                mSwipeAnimationTargetSet.controller.cleanupScreenshot();
                break;
            }
        }
    }

    private void finishAnimationTargetSet() {
        float endProgress = mEndTarget.mEndProgress;

        if (mProgress.value != endProgress) {
            AnimatorSet anim = new AnimatorSet();
            anim.play(mLauncherAlpha.animateToValue(
                    mLauncherAlpha.value, mEndTarget.mLauncherAlpha));
            anim.play(mProgress.animateToValue(mProgress.value, endProgress));

            anim.setDuration((long) (mEndTarget.mDurationMultiplier *
                    Math.abs(endProgress - mProgress.value)));
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    finishAnimationTargetSetAnimationComplete();
                }
            });
            anim.start();
        } else {
            finishAnimationTargetSetAnimationComplete();
        }
    }

    @Override
    public void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet) {
        mSwipeAnimationTargetSet = targetSet;
        Rect overviewStackBounds = new Rect(0, 0, mDP.widthPx, mDP.heightPx);
        RemoteAnimationTargetCompat runningTaskTarget = targetSet.findTask(mRunningTaskId);

        mDP.updateIsSeascape(mContext.getSystemService(WindowManager.class));
        if (targetSet.homeContentInsets != null) {
            mDP.updateInsets(targetSet.homeContentInsets);
        }

        if (runningTaskTarget != null) {
            mClipAnimationHelper.updateSource(overviewStackBounds, runningTaskTarget);
        }
        mClipAnimationHelper.prepareAnimation(mDP, false /* isOpening */);
        initTransitionTarget();
        mClipAnimationHelper.applyTransform(mSwipeAnimationTargetSet, mTransformParams);

        if (mEndTarget != null) {
            finishAnimationTargetSet();
        }
    }

    private void initTransitionTarget() {
        mTransitionDragLength = mActivityControlHelper.getSwipeUpDestinationAndLength(
                mDP, mContext, mTargetRect);
        mDragLengthFactor = (float) mDP.heightPx / mTransitionDragLength;
        mClipAnimationHelper.updateTargetRect(mTargetRect);
    }

    @Override
    public void onRecentsAnimationCanceled() { }

    private float getDisplacement(MotionEvent ev) {
        if (mNavBarPosition.isRightEdge()) {
            return ev.getX() - mDownPos.x;
        } else if (mNavBarPosition.isLeftEdge()) {
            return mDownPos.x - ev.getX();
        } else {
            return ev.getY() - mDownPos.y;
        }
    }

    @Override
    public boolean allowInterceptByParent() {
        return !mPassedPilferInputSlop;
    }
}
