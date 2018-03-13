/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.DEACCEL_1_5;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.RecentsView;
import com.android.quickstep.TaskView;

/**
 * Touch controller for swipe interaction in Overview state
 */
public class OverviewSwipeController extends AnimatorListenerAdapter
        implements TouchController, SwipeDetector.Listener {

    private static final String TAG = "OverviewSwipeController";

    private static final float ALLOWED_FLING_DIRECTION_CHANGE_PROGRESS = 0.1f;
    private static final int SINGLE_FRAME_MS = 16;

    // Progress after which the transition is assumed to be a success in case user does not fling
    private static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;

    private final Launcher mLauncher;
    private final SwipeDetector mDetector;
    private final RecentsView mRecentsView;
    private final int[] mTempCords = new int[2];

    private AnimatorPlaybackController mCurrentAnimation;
    private boolean mCurrentAnimationIsGoingUp;

    private boolean mNoIntercept;
    private boolean mSwipeDownEnabled;

    private float mDisplacementShift;
    private float mProgressMultiplier;
    private float mEndDisplacement;
    private int mStartingTarget;

    private TaskView mTaskBeingDragged;

    public OverviewSwipeController(Launcher launcher) {
        mLauncher = launcher;
        mRecentsView = launcher.getOverviewPanel();
        mDetector = new SwipeDetector(launcher, this, SwipeDetector.VERTICAL);
    }

    private boolean canInterceptTouch() {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        return mLauncher.isInState(OVERVIEW);
    }

    private boolean isEventOverHotseat(MotionEvent ev) {
        if (mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            return ev.getY() >
                    mLauncher.getDragLayer().getHeight() * OVERVIEW.getVerticalProgress(mLauncher);
        } else {
            return mLauncher.getDragLayer().isEventOverHotseat(ev);
        }
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        if (mCurrentAnimation != null && animation == mCurrentAnimation.getTarget()) {
            Log.e(TAG, "Who dare cancel the animation when I am in control", new Exception());
            mDetector.finishedScrolling();
            mCurrentAnimation = null;
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !canInterceptTouch();
            if (mNoIntercept) {
                return false;
            }

            // Now figure out which direction scroll events the controller will start
            // calling the callbacks.
            final int directionsToDetectScroll;
            boolean ignoreSlopWhenSettling = false;
            if (mCurrentAnimation != null) {
                directionsToDetectScroll = SwipeDetector.DIRECTION_BOTH;
                ignoreSlopWhenSettling = true;
            } else {
                mTaskBeingDragged = null;
                mSwipeDownEnabled = true;

                View view = mRecentsView.getChildAt(mRecentsView.getCurrentPage());
                if (view instanceof TaskView && mLauncher.getDragLayer().isEventOverView(view, ev)) {
                    // The tile can be dragged down to open the task.
                    mTaskBeingDragged = (TaskView) view;
                    directionsToDetectScroll = SwipeDetector.DIRECTION_BOTH;
                    mStartingTarget = LauncherLogProto.ItemType.TASK;
                } else if (isEventOverHotseat(ev)) {
                    // The hotseat is being dragged
                    directionsToDetectScroll = SwipeDetector.DIRECTION_POSITIVE;
                    mSwipeDownEnabled = false;
                    mStartingTarget = ContainerType.HOTSEAT;
                } else {
                    mNoIntercept = true;
                    mStartingTarget = ContainerType.WORKSPACE;
                    return false;
                }
            }

            mDetector.setDetectableScrollConditions(
                    directionsToDetectScroll, ignoreSlopWhenSettling);
        }

        if (mNoIntercept) {
            return false;
        }

        onControllerTouchEvent(ev);
        return mDetector.isDraggingOrSettling();
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mDetector.onTouchEvent(ev);
    }

    private void reInitAnimationController(boolean goingUp) {
        if (!goingUp && !mSwipeDownEnabled) {
            goingUp = true;
        }
        if (mCurrentAnimation != null && mCurrentAnimationIsGoingUp == goingUp) {
            // No need to init
            return;
        }
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setPlayFraction(0);
        }
        mCurrentAnimationIsGoingUp = goingUp;
        float range = mLauncher.getAllAppsController().getShiftRange();
        long maxDuration = (long) (2 * range);
        DragLayer dl = mLauncher.getDragLayer();

        if (mTaskBeingDragged == null) {
            // User is either going to all apps or home
            mCurrentAnimation = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(goingUp ? ALL_APPS : NORMAL, maxDuration);
            if (goingUp) {
                mEndDisplacement = -range;
            } else {
                mEndDisplacement = EdgeSwipeController.getShiftRange(mLauncher);
            }
        } else {
            if (goingUp) {
                AnimatorSet anim = new AnimatorSet();
                ObjectAnimator translate = ObjectAnimator.ofFloat(
                        mTaskBeingDragged, View.TRANSLATION_Y, -mTaskBeingDragged.getBottom());
                translate.setInterpolator(LINEAR);
                translate.setDuration(maxDuration);
                anim.play(translate);

                ObjectAnimator alpha = ObjectAnimator.ofFloat(mTaskBeingDragged, View.ALPHA, 0);
                alpha.setInterpolator(DEACCEL_1_5);
                alpha.setDuration(maxDuration);
                anim.play(alpha);
                mCurrentAnimation = AnimatorPlaybackController.wrap(anim, maxDuration);
                mEndDisplacement = -mTaskBeingDragged.getBottom();
            } else {
                AnimatorSet anim = new AnimatorSet();
                // TODO: Setup a zoom animation
                mCurrentAnimation = AnimatorPlaybackController.wrap(anim, maxDuration);

                mTempCords[1] = mTaskBeingDragged.getHeight();
                dl.getDescendantCoordRelativeToSelf(mTaskBeingDragged, mTempCords);
                mEndDisplacement = dl.getHeight() - mTempCords[1];
            }
        }

        mCurrentAnimation.getTarget().addListener(this);
        mCurrentAnimation.dispatchOnStart();
        mProgressMultiplier = 1 / mEndDisplacement;
    }

    @Override
    public void onDragStart(boolean start) {
        if (mCurrentAnimation == null) {
            reInitAnimationController(mDetector.wasInitialTouchPositive());
            mDisplacementShift = 0;
        } else {
            mDisplacementShift = mCurrentAnimation.getProgressFraction() / mProgressMultiplier;
            mCurrentAnimation.pause();
        }
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        float totalDisplacement = displacement + mDisplacementShift;
        boolean isGoingUp =
                totalDisplacement == 0 ? mCurrentAnimationIsGoingUp : totalDisplacement < 0;
        if (isGoingUp != mCurrentAnimationIsGoingUp) {
            reInitAnimationController(isGoingUp);
        }
        mCurrentAnimation.setPlayFraction(totalDisplacement * mProgressMultiplier);
        return true;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        final boolean goingToEnd;
        final int logAction;
        if (fling) {
            logAction = Touch.FLING;
            boolean goingUp = velocity < 0;
            if (!goingUp && !mSwipeDownEnabled) {
                goingToEnd = false;
            } else if (goingUp != mCurrentAnimationIsGoingUp) {
                // In case the fling is in opposite direction, make sure if is close enough
                // from the start position
                if (mCurrentAnimation.getProgressFraction()
                        >= ALLOWED_FLING_DIRECTION_CHANGE_PROGRESS) {
                    // Not allowed
                    goingToEnd = false;
                } else {
                    reInitAnimationController(goingUp);
                    goingToEnd = true;
                }
            } else {
                goingToEnd = true;
            }
        } else {
            logAction = Touch.SWIPE;
            goingToEnd = mCurrentAnimation.getProgressFraction() > SUCCESS_TRANSITION_PROGRESS;
        }

        float progress = mCurrentAnimation.getProgressFraction();
        long animationDuration = SwipeDetector.calculateDuration(
                velocity, goingToEnd ? (1 - progress) : progress);

        float nextFrameProgress = Utilities.boundToRange(
                progress + velocity * SINGLE_FRAME_MS / Math.abs(mEndDisplacement), 0f, 1f);


        mCurrentAnimation.setEndAction(() -> onCurrentAnimationEnd(goingToEnd, logAction));

        ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
        anim.setFloatValues(nextFrameProgress, goingToEnd ? 1f : 0f);
        anim.setDuration(animationDuration);
        anim.setInterpolator(scrollInterpolatorForVelocity(velocity));
        anim.start();
    }

    private void onCurrentAnimationEnd(boolean wasSuccess, int logAction) {
        if (mTaskBeingDragged == null) {
            LauncherState state = wasSuccess ?
                    (mCurrentAnimationIsGoingUp ? ALL_APPS : NORMAL) : OVERVIEW;
            mLauncher.getStateManager().goToState(state, false);

        } else if (wasSuccess) {
            if (mCurrentAnimationIsGoingUp) {
                mRecentsView.onTaskDismissed(mTaskBeingDragged);
            } else {
                mTaskBeingDragged.launchTask(false);
                mLauncher.getUserEventDispatcher().logTaskLaunch(logAction,
                        Direction.DOWN, mTaskBeingDragged.getTask().getTopComponent());
            }
        }
        if (mTaskBeingDragged == null || (wasSuccess && mCurrentAnimationIsGoingUp)) {
            mLauncher.getUserEventDispatcher().logStateChangeAction(logAction,
                    mCurrentAnimationIsGoingUp ? Direction.UP : Direction.DOWN,
                    mStartingTarget, ContainerType.TASKSWITCHER,
                    mLauncher.getStateManager().getState().containerType,
                    mRecentsView.getCurrentPage());
        }
        mDetector.finishedScrolling();
        mTaskBeingDragged = null;
        mCurrentAnimation = null;
    }
}
