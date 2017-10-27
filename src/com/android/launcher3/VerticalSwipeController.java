/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.anim.SpringAnimationHandler.Y_DIRECTION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.support.animation.SpringAnimation;
import android.util.Log;
import android.view.MotionEvent;

import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.TouchController;

import java.util.ArrayList;

/**
 * Handles vertical touch gesture on the DragLayer
 */
public class VerticalSwipeController extends AnimatorListenerAdapter
        implements TouchController, SwipeDetector.Listener {

    private static final String TAG = "VerticalSwipeController";

    private static final float RECATCH_REJECTION_FRACTION = .0875f;
    private static final int SINGLE_FRAME_MS = 16;

    // Progress after which the transition is assumed to be a success in case user does not fling
    private static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;

    private final Launcher mLauncher;
    private final SwipeDetector mDetector;

    private boolean mNoIntercept;
    private int mStartContainerType;

    private AnimatorPlaybackController mCurrentAnimation;
    private LauncherState mToState;

    private float mStartProgress;
    // Ratio of transition process [0, 1] to drag displacement (px)
    private float mProgressMultiplier;

    private SpringAnimationHandler[] mSpringHandlers;

    public VerticalSwipeController(Launcher l) {
        mLauncher = l;
        mDetector = new SwipeDetector(l, this, SwipeDetector.VERTICAL);
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if (!mLauncher.isInState(NORMAL) && !mLauncher.isInState(ALL_APPS)) {
            // Don't listen for the pinch gesture if on all apps, widget picker, -1, etc.
            return false;
        }
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (mLauncher.isInState(ALL_APPS) && !mLauncher.getAppsView().shouldContainerScroll(ev)) {
            return false;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }

        return true;
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        if (mCurrentAnimation != null && animation == mCurrentAnimation.getTarget()) {
            Log.e(TAG, "Who dare cancel the animation when I am in control", new Exception());
            mDetector.finishedScrolling();
            mCurrentAnimation = null;
        }
    }

    private void initSprings() {
        AllAppsContainerView appsView = mLauncher.getAppsView();

        SpringAnimationHandler handler = appsView.getSpringAnimationHandler();
        if (handler == null) {
            mSpringHandlers = new SpringAnimationHandler[0];
            return;
        }

        ArrayList<SpringAnimationHandler> handlers = new ArrayList<>();
        handlers.add(handler);

        SpringAnimation searchSpring = appsView.getSearchUiManager().getSpringForFling();
        if (searchSpring != null) {
            SpringAnimationHandler searchHandler =
                    new SpringAnimationHandler(Y_DIRECTION, handler.getFactory());
            searchHandler.add(searchSpring, true /* setDefaultValues */);
            handlers.add(searchHandler);
        }

        mSpringHandlers = handlers.toArray(new SpringAnimationHandler[handlers.size()]);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !canInterceptTouch(ev);
            if (mNoIntercept) {
                return false;
            }

            // Now figure out which direction scroll events the controller will start
            // calling the callbacks.
            final int directionsToDetectScroll;
            boolean ignoreSlopWhenSettling = false;

            if (mCurrentAnimation != null) {
                if (mCurrentAnimation.getProgressFraction() > 1 - RECATCH_REJECTION_FRACTION) {
                    directionsToDetectScroll = SwipeDetector.DIRECTION_POSITIVE;
                } else if (mCurrentAnimation.getProgressFraction() < RECATCH_REJECTION_FRACTION ) {
                    directionsToDetectScroll = SwipeDetector.DIRECTION_NEGATIVE;
                } else {
                    directionsToDetectScroll = SwipeDetector.DIRECTION_BOTH;
                    ignoreSlopWhenSettling = true;
                }
            } else {
                if (mLauncher.isInState(ALL_APPS)) {
                    directionsToDetectScroll = SwipeDetector.DIRECTION_NEGATIVE;
                    mStartContainerType = ContainerType.ALLAPPS;
                } else {
                    directionsToDetectScroll = SwipeDetector.DIRECTION_POSITIVE;
                    mStartContainerType = mLauncher.getDragLayer().isEventOverHotseat(ev) ?
                            ContainerType.HOTSEAT : ContainerType.WORKSPACE;
                }
            }

            mDetector.setDetectableScrollConditions(
                    directionsToDetectScroll, ignoreSlopWhenSettling);

            if (mSpringHandlers == null) {
                initSprings();
            }
        }

        if (mNoIntercept) {
            return false;
        }

        onControllerTouchEvent(ev);
        return mDetector.isDraggingOrSettling();
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        for (SpringAnimationHandler h : mSpringHandlers) {
            h.addMovement(ev);
        }
        return mDetector.onTouchEvent(ev);
    }

    @Override
    public void onDragStart(boolean start) {
        if (mCurrentAnimation == null) {
            float range = getShiftRange();
            long maxAccuracy = (long) (2 * range);

            // Build current animation
            mToState = mLauncher.isInState(ALL_APPS) ? NORMAL : ALL_APPS;
            mCurrentAnimation = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(mToState, maxAccuracy);
            mCurrentAnimation.getTarget().addListener(this);
            mStartProgress = 0;
            mProgressMultiplier = (mLauncher.isInState(ALL_APPS) ? 1 : -1) / range;
            mCurrentAnimation.dispatchOnStart();
        } else {
            mCurrentAnimation.pause();
            mStartProgress = mCurrentAnimation.getProgressFraction();
        }

        for (SpringAnimationHandler h : mSpringHandlers) {
            h.skipToEnd();
        }
    }

    private float getShiftRange() {
        return mLauncher.mAllAppsController.getShiftRange();
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        float deltaProgress = mProgressMultiplier * displacement;
        mCurrentAnimation.setPlayFraction(deltaProgress + mStartProgress);
        return true;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        final long animationDuration;
        final int logAction;
        final LauncherState targetState;
        final float progress = mCurrentAnimation.getProgressFraction();

        if (fling) {
            logAction = Touch.FLING;
            if (velocity < 0) {
                targetState = ALL_APPS;
                animationDuration = SwipeDetector.calculateDuration(velocity,
                        mToState == ALL_APPS ? (1 - progress) : progress);
            } else {
                targetState = NORMAL;
                animationDuration = SwipeDetector.calculateDuration(velocity,
                        mToState == ALL_APPS ? progress : (1 - progress));
            }
            // snap to top or bottom using the release velocity
        } else {
            logAction = Touch.SWIPE;
            if (progress > SUCCESS_TRANSITION_PROGRESS) {
                targetState = mToState;
                animationDuration = SwipeDetector.calculateDuration(velocity, 1 - progress);
            } else {
                targetState = mToState == ALL_APPS ? NORMAL : ALL_APPS;
                animationDuration = SwipeDetector.calculateDuration(velocity, progress);
            }
        }

        if (fling && targetState == ALL_APPS) {
            for (SpringAnimationHandler h : mSpringHandlers) {
                // The icons are moving upwards, so we go to 0 from 1. (y-axis 1 is below 0.)
                h.animateToFinalPosition(0 /* pos */, 1 /* startValue */);
            }
        }

        mCurrentAnimation.setEndAction(new Runnable() {
            @Override
            public void run() {
                if (targetState == mToState) {
                    // Transition complete. log the action
                    mLauncher.getUserEventDispatcher().logActionOnContainer(logAction,
                            mToState == ALL_APPS ? Direction.UP : Direction.DOWN,
                            mStartContainerType, mLauncher.getWorkspace().getCurrentPage());
                } else {
                    mLauncher.getStateManager().goToState(
                            mToState == ALL_APPS ? NORMAL : ALL_APPS, false);
                }
                mDetector.finishedScrolling();
                mCurrentAnimation = null;
            }
        });

        float nextFrameProgress = Utilities.boundToRange(
                progress + velocity * SINGLE_FRAME_MS / getShiftRange(), 0f, 1f);

        ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
        anim.setFloatValues(nextFrameProgress, targetState == mToState ? 1f : 0f);
        anim.setDuration(animationDuration);
        anim.setInterpolator(scrollInterpolatorForVelocity(velocity));
        anim.start();
    }
}
