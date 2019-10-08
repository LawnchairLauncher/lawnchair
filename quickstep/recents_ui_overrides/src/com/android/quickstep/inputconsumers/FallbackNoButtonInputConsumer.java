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

import static com.android.quickstep.WindowTransformSwipeHandler.MAX_SWIPE_DURATION;
import static com.android.quickstep.WindowTransformSwipeHandler.MIN_PROGRESS_FOR_OVERVIEW;
import static com.android.quickstep.WindowTransformSwipeHandler.MIN_SWIPE_DURATION;
import static com.android.quickstep.inputconsumers.OtherActivityInputConsumer.QUICKSTEP_TOUCH_SLOP_RATIO;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.quickstep.ActivityControlHelper;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.SwipeSharedState;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.ClipAnimationHelper.TransformParams;
import com.android.quickstep.util.NavBarPosition;
import com.android.quickstep.util.RecentsAnimationListenerSet;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet.SwipeAnimationListener;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

public class FallbackNoButtonInputConsumer implements InputConsumer, SwipeAnimationListener {

    private static final int STATE_NOT_FINISHED = 0;
    private static final int STATE_FINISHED_TO_HOME = 1;
    private static final int STATE_FINISHED_TO_APP = 2;

    private static final float PROGRESS_TO_END_GESTURE = -2;

    private final ActivityControlHelper mActivityControlHelper;
    private final InputMonitorCompat mInputMonitor;
    private final Context mContext;
    private final NavBarPosition mNavBarPosition;
    private final SwipeSharedState mSwipeSharedState;
    private final OverviewComponentObserver mOverviewComponentObserver;
    private final int mRunningTaskId;

    private final ClipAnimationHelper mClipAnimationHelper;
    private final TransformParams mTransformParams = new TransformParams();
    private final float mTransitionDragLength;
    private final DeviceProfile mDP;

    private final RectF mSwipeTouchRegion;
    private final boolean mDisableHorizontalSwipe;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();

    private int mActivePointerId = -1;
    // Slop used to determine when we say that the gesture has started.
    private boolean mPassedPilferInputSlop;

    private VelocityTracker mVelocityTracker;

    // Distance after which we start dragging the window.
    private final float mTouchSlop;

    // Might be displacement in X or Y, depending on the direction we are swiping from the nav bar.
    private float mStartDisplacement;
    private SwipeAnimationTargetSet mSwipeAnimationTargetSet;
    private float mProgress;

    private int mState = STATE_NOT_FINISHED;

    public FallbackNoButtonInputConsumer(Context context,
            ActivityControlHelper activityControlHelper, InputMonitorCompat inputMonitor,
            SwipeSharedState swipeSharedState, RectF swipeTouchRegion,
            OverviewComponentObserver overviewComponentObserver,
            boolean disableHorizontalSwipe, RunningTaskInfo runningTaskInfo) {
        mContext = context;
        mActivityControlHelper = activityControlHelper;
        mInputMonitor = inputMonitor;
        mOverviewComponentObserver = overviewComponentObserver;
        mRunningTaskId = runningTaskInfo.id;

        mSwipeSharedState = swipeSharedState;
        mSwipeTouchRegion = swipeTouchRegion;
        mDisableHorizontalSwipe = disableHorizontalSwipe;

        mNavBarPosition = new NavBarPosition(context);
        mVelocityTracker = VelocityTracker.obtain();

        mTouchSlop = QUICKSTEP_TOUCH_SLOP_RATIO
                * ViewConfiguration.get(context).getScaledTouchSlop();

        mClipAnimationHelper = new ClipAnimationHelper(context);

        mDP = InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context).copy(context);
        Rect tempRect = new Rect();
        mTransitionDragLength = mActivityControlHelper.getSwipeUpDestinationAndLength(
                mDP, context, tempRect);
        mClipAnimationHelper.updateTargetRect(tempRect);
    }

    @Override
    public int getType() {
        return TYPE_FALLBACK_NO_BUTTON;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            return;
        }

        mVelocityTracker.addMovement(ev);
        if (ev.getActionMasked() == ACTION_POINTER_UP) {
            mVelocityTracker.clear();
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
        BackgroundExecutor.get().submit(
                () -> ActivityManagerWrapper.getInstance().startRecentsActivity(
                        homeIntent, null, listenerSet, null, null));

        ActivityManagerWrapper.getInstance().closeSystemWindows(
                CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
        mInputMonitor.pilferPointers();
    }

    private void updateDisplacement(float displacement) {
        mProgress = displacement / mTransitionDragLength;
        mTransformParams.setProgress(mProgress);

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
            mState = STATE_FINISHED_TO_APP;
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

            boolean goingHome;
            if (!isFling) {
                goingHome = -mProgress >= MIN_PROGRESS_FOR_OVERVIEW;
            } else {
                goingHome = velocity < 0;
            }

            if (goingHome) {
                mState = STATE_FINISHED_TO_HOME;
            } else {
                mState = STATE_FINISHED_TO_APP;
            }
        }

        if (mSwipeAnimationTargetSet != null) {
            finishAnimationTargetSet();
        }
    }

    private void finishAnimationTargetSet() {
        if (mState == STATE_FINISHED_TO_APP) {
            mSwipeAnimationTargetSet.finishController(false, null, false);
        } else {
            if (mProgress < PROGRESS_TO_END_GESTURE) {
                mSwipeAnimationTargetSet.finishController(true, null, true);
            } else {
                long duration = (long) (Math.min(mProgress - PROGRESS_TO_END_GESTURE, 1)
                        * MAX_SWIPE_DURATION / Math.abs(PROGRESS_TO_END_GESTURE));
                if (duration < 0) {
                    duration = MIN_SWIPE_DURATION;
                }

                ValueAnimator anim = ValueAnimator.ofFloat(mProgress, PROGRESS_TO_END_GESTURE);
                anim.addUpdateListener(a -> {
                    float p = (Float) anim.getAnimatedValue();
                    mTransformParams.setProgress(p);
                    mClipAnimationHelper.applyTransform(mSwipeAnimationTargetSet, mTransformParams);
                });
                anim.setDuration(duration);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mSwipeAnimationTargetSet.finishController(true, null, true);
                    }
                });
                anim.start();
            }
        }
    }

    @Override
    public void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet) {
        mSwipeAnimationTargetSet = targetSet;
        Rect overviewStackBounds = new Rect(0, 0, mDP.widthPx, mDP.heightPx);
        RemoteAnimationTargetCompat runningTaskTarget = targetSet.findTask(mRunningTaskId);

        mDP.updateIsSeascape(mContext.getSystemService(WindowManager.class));
        if (runningTaskTarget != null) {
            mClipAnimationHelper.updateSource(overviewStackBounds, runningTaskTarget);
        }
        mClipAnimationHelper.prepareAnimation(mDP, false /* isOpening */);

        overviewStackBounds
                .inset(-overviewStackBounds.width() / 5, -overviewStackBounds.height() / 5);
        mClipAnimationHelper.updateTargetRect(overviewStackBounds);
        mClipAnimationHelper.applyTransform(mSwipeAnimationTargetSet, mTransformParams);

        if (mState != STATE_NOT_FINISHED) {
            finishAnimationTargetSet();
        }
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
