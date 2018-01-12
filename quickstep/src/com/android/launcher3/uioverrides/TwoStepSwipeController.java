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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.anim.SpringAnimationHandler.Y_DIRECTION;
import static com.android.quickstep.TouchInteractionService.EDGE_NAV_BAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.support.animation.SpringAnimation;
import android.util.Log;
import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.FloatRange;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RecentsView;
import com.android.quickstep.TouchInteractionService;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;

import java.util.ArrayList;

/**
 * Handles vertical touch gesture on the DragLayer
 */
public class TwoStepSwipeController extends AnimatorListenerAdapter
        implements TouchController, SwipeDetector.Listener {

    private static final String TAG = "TwoStepSwipeController";

    private static final float RECATCH_REJECTION_FRACTION = .0875f;
    private static final int SINGLE_FRAME_MS = 16;
    private static final long QUICK_SNAP_TO_OVERVIEW_DURATION = 250;

    // Progress after which the transition is assumed to be a success in case user does not fling
    private static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;

    /**
     * Index of the vertical swipe handles in {@link LauncherStateManager#getStateHandlers()}.
     */
    private static final int SWIPE_HANDLER_INDEX = 0;

    /**
     * Index of various UI handlers in {@link LauncherStateManager#getStateHandlers()} not related
     * to vertical swipe.
     */
    private static final int OTHER_HANDLERS_START_INDEX = SWIPE_HANDLER_INDEX + 1;

    // Swipe progress range (when starting from NORMAL state) where OVERVIEW state is allowed
    private static final float MIN_PROGRESS_TO_OVERVIEW = 0.1f;
    private static final float MAX_PROGRESS_TO_OVERVIEW = 0.4f;

    private static final int FLAG_OVERVIEW_DISABLED_OUT_OF_RANGE = 1 << 0;
    private static final int FLAG_OVERVIEW_DISABLED_FLING = 1 << 1;
    private static final int FLAG_OVERVIEW_DISABLED_CANCEL_STATE = 1 << 2;
    private static final int FLAG_RECENTS_PLAN_LOADING = 1 << 3;
    private static final int FLAG_OVERVIEW_DISABLED = 1 << 4;
    private static final int FLAG_DISABLED_TWO_TARGETS = 1 << 5;
    private static final int FLAG_DISABLED_BACK_TARGET = 1 << 6;

    private final Launcher mLauncher;
    private final SwipeDetector mDetector;

    private boolean mNoIntercept;
    private int mStartContainerType;

    private DragPauseDetector mDragPauseDetector;
    private FloatRange mOverviewProgressRange;
    private TaggedAnimatorSetBuilder mTaggedAnimatorSetBuilder;
    private AnimatorSet mQuickOverviewAnimation;
    private boolean mAnimatingToOverview;
    private CroppedAnimationController mCroppedAnimationController;

    private AnimatorPlaybackController mCurrentAnimation;
    private LauncherState mFromState;
    private LauncherState mToState;

    private float mStartProgress;
    // Ratio of transition process [0, 1] to drag displacement (px)
    private float mProgressMultiplier;

    private SpringAnimationHandler[] mSpringHandlers;

    public TwoStepSwipeController(Launcher l) {
        mLauncher = l;
        mDetector = new SwipeDetector(l, this, SwipeDetector.VERTICAL);
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (mLauncher.isInState(NORMAL)) {
            if ((ev.getEdgeFlags() & EDGE_NAV_BAR) != 0 &&
                    !mLauncher.getDeviceProfile().isVerticalBarLayout()) {
                // On normal swipes ignore edge swipes
                return false;
            }
        } else if (mLauncher.isInState(ALL_APPS)) {
            if (!mLauncher.getAppsView().shouldContainerScroll(ev)) {
                return false;
            }
        } else {
            // Don't listen for the swipe gesture if we are already in some other state.
            return false;
        }
        if (mAnimatingToOverview) {
            return false;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }

        return true;
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        if (mCurrentAnimation != null && animation == mCurrentAnimation.getOriginalTarget()) {
            Log.e(TAG, "Who dare cancel the animation when I am in control", new Exception());
            clearState();
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

            mDragPauseDetector = new DragPauseDetector(this::onDragPauseDetected);
            mDragPauseDetector.addDisabledFlags(FLAG_OVERVIEW_DISABLED_OUT_OF_RANGE);
            if (FeatureFlags.ENABLE_TWO_SWIPE_TARGETS) {
                mDragPauseDetector.addDisabledFlags(FLAG_DISABLED_TWO_TARGETS);
            }

            mOverviewProgressRange = new FloatRange();
            mOverviewProgressRange.start = mLauncher.isInState(NORMAL)
                    ? MIN_PROGRESS_TO_OVERVIEW
                    : 1 - MAX_PROGRESS_TO_OVERVIEW;
            mOverviewProgressRange.end = mOverviewProgressRange.start
                    + MAX_PROGRESS_TO_OVERVIEW - MIN_PROGRESS_TO_OVERVIEW;

            // Build current animation
            mFromState = mLauncher.getStateManager().getState();
            mToState = mLauncher.isInState(ALL_APPS) ? NORMAL : ALL_APPS;

            if (mToState == NORMAL && mLauncher.getStateManager().getLastState() == OVERVIEW) {
                mToState = OVERVIEW;
                mDragPauseDetector.addDisabledFlags(FLAG_DISABLED_BACK_TARGET);
            }

            mTaggedAnimatorSetBuilder = new TaggedAnimatorSetBuilder();
            mCurrentAnimation = mLauncher.getStateManager().createAnimationToNewWorkspace(
                    mToState, mTaggedAnimatorSetBuilder, maxAccuracy);

            if (TouchInteractionService.isConnected()) {
                // Load recents plan
                RecentsModel recentsModel = RecentsModel.getInstance(mLauncher);
                if (recentsModel.getLastLoadPlan() != null) {
                    onRecentsPlanLoaded(recentsModel.getLastLoadPlan());
                } else {
                    mDragPauseDetector.addDisabledFlags(FLAG_RECENTS_PLAN_LOADING);
                    recentsModel.loadTasks(-1, this::onRecentsPlanLoaded);
                }
            } else {
                mDragPauseDetector.addDisabledFlags(FLAG_OVERVIEW_DISABLED);
            }

            mCurrentAnimation.getTarget().addListener(this);
            mStartProgress = 0;
            mProgressMultiplier = (mLauncher.isInState(ALL_APPS) ? 1 : -1) / range;
            mCurrentAnimation.dispatchOnStart();
        } else {
            mCurrentAnimation.pause();
            mStartProgress = mCurrentAnimation.getProgressFraction();

            mDragPauseDetector.clearDisabledFlags(FLAG_OVERVIEW_DISABLED_FLING);
            updatePauseDetectorRangeFlag();
        }

        for (SpringAnimationHandler h : mSpringHandlers) {
            h.skipToEnd();
        }
    }

    private void onRecentsPlanLoaded(RecentsTaskLoadPlan plan) {
        RecentsView recentsView = mLauncher.getOverviewPanel();
        recentsView.update(plan);
        recentsView.initToPage(0);

        mDragPauseDetector.clearDisabledFlags(FLAG_RECENTS_PLAN_LOADING);
    }

    private float getShiftRange() {
        return mLauncher.getAllAppsController().getShiftRange();
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        float deltaProgress = mProgressMultiplier * displacement;
        mCurrentAnimation.setPlayFraction(deltaProgress + mStartProgress);

        updatePauseDetectorRangeFlag();
        mDragPauseDetector.onDrag(velocity);

        return true;
    }

    private void updatePauseDetectorRangeFlag() {
        if (mOverviewProgressRange.contains(mCurrentAnimation.getProgressFraction())) {
            mDragPauseDetector.clearDisabledFlags(FLAG_OVERVIEW_DISABLED_OUT_OF_RANGE);
        } else {
            mDragPauseDetector.addDisabledFlags(FLAG_OVERVIEW_DISABLED_OUT_OF_RANGE);
        }
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        mDragPauseDetector.addDisabledFlags(FLAG_OVERVIEW_DISABLED_FLING);

        final int logAction;
        LauncherState targetState;
        final float progress = mCurrentAnimation.getProgressFraction();

        if (fling) {
            logAction = Touch.FLING;
            targetState = velocity < 0 ? ALL_APPS : mLauncher.getStateManager().getLastState();
            // snap to top or bottom using the release velocity
        } else {
            logAction = Touch.SWIPE;
            targetState = (progress > SUCCESS_TRANSITION_PROGRESS) ? mToState : mFromState;
        }

        if (fling && targetState == ALL_APPS) {
            for (SpringAnimationHandler h : mSpringHandlers) {
                // The icons are moving upwards, so we go to 0 from 1. (y-axis 1 is below 0.)
                h.animateToFinalPosition(0 /* pos */, 1 /* startValue */);
            }
        }

        float endProgress;

        if (mDragPauseDetector.isTriggered() && targetState == NORMAL) {
            targetState = OVERVIEW;
            endProgress = OVERVIEW.getVerticalProgress(mLauncher);
            if (mFromState == NORMAL) {
                endProgress = 1 - endProgress;
            }
        } else if (targetState == mToState) {
            endProgress = 1;
        } else {
            endProgress = 0;
        }

        LauncherState targetStateFinal = targetState;
        mCurrentAnimation.setEndAction(() ->
                onSwipeInteractionCompleted(targetStateFinal, logAction));

        float nextFrameProgress = Utilities.boundToRange(
                progress + velocity * SINGLE_FRAME_MS / getShiftRange(), 0f, 1f);

        ValueAnimator anim = mCurrentAnimation.getAnimationPlayer();
        anim.setFloatValues(nextFrameProgress, endProgress);
        anim.setDuration(
                SwipeDetector.calculateDuration(velocity, Math.abs(endProgress - progress)));
        anim.setInterpolator(scrollInterpolatorForVelocity(velocity));
        anim.start();
    }

    private void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        if (targetState != mFromState) {
            // Transition complete. log the action
            mLauncher.getUserEventDispatcher().logActionOnContainer(logAction,
                    mToState == ALL_APPS ? Direction.UP : Direction.DOWN,
                    mStartContainerType, mLauncher.getWorkspace().getCurrentPage());
        }
        clearState();

        // TODO: mQuickOverviewAnimation might still be running in which changing a state instantly
        // may cause a jump. Animate the state change with a short duration in this case?
        mLauncher.getStateManager().goToState(targetState, false /* animated */);
    }

    private void onDragPauseDetected() {
        final ValueAnimator twoStepAnimator = ValueAnimator.ofFloat(0, 1);
        twoStepAnimator.setDuration(mCurrentAnimation.getDuration());
        StateHandler[] handlers = mLauncher.getStateManager().getStateHandlers();

        // Change the current animation to only play the vertical handle
        AnimatorSet anim = new AnimatorSet();
        anim.playTogether(mTaggedAnimatorSetBuilder.getAnimationsForTag(
                handlers[SWIPE_HANDLER_INDEX]));
        anim.play(twoStepAnimator);
        mCurrentAnimation = mCurrentAnimation.cloneFor(anim);

        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        AnimationConfig config = new AnimationConfig();
        config.duration = QUICK_SNAP_TO_OVERVIEW_DURATION;
        for (int i = OTHER_HANDLERS_START_INDEX; i < handlers.length; i++) {
            handlers[i].setStateWithAnimation(OVERVIEW, builder, config);
        }
        mQuickOverviewAnimation = builder.build();
        mQuickOverviewAnimation.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                onQuickOverviewAnimationComplete(twoStepAnimator);
            }
        });
        mQuickOverviewAnimation.start();
    }

    private void onQuickOverviewAnimationComplete(ValueAnimator animator) {
        if (mAnimatingToOverview) {
            return;
        }

        // For the remainder to the interaction, the user can either go to the ALL_APPS state or
        // the OVERVIEW state.
        // The remaining state handlers are on the OVERVIEW state. Create one animation towards the
        // ALL_APPS state and only call it when the user moved above the current range.
        AnimationConfig config = new AnimationConfig();
        config.duration = (long) (2 * getShiftRange());
        config.userControlled = true;

        AnimatorSetBuilder builderToAllAppsState = new AnimatorSetBuilder();
        StateHandler[] handlers = mLauncher.getStateManager().getStateHandlers();
        for (int i = OTHER_HANDLERS_START_INDEX; i < handlers.length; i++) {
            handlers[i].setStateWithAnimation(ALL_APPS, builderToAllAppsState, config);
        }

        mCroppedAnimationController = new CroppedAnimationController(
                AnimatorPlaybackController.wrap(builderToAllAppsState.build(), config.duration),
                new FloatRange(animator.getAnimatedFraction(), mToState == ALL_APPS ? 1 : 0));
        animator.addUpdateListener(mCroppedAnimationController);
    }

    private void clearState() {
        mCurrentAnimation = null;
        mTaggedAnimatorSetBuilder = null;
        if (mDragPauseDetector != null) {
            mDragPauseDetector.addDisabledFlags(FLAG_OVERVIEW_DISABLED_CANCEL_STATE);
        }
        mDragPauseDetector = null;

        if (mQuickOverviewAnimation != null) {
            mQuickOverviewAnimation.cancel();
            mQuickOverviewAnimation = null;
        }
        mCroppedAnimationController = null;
        mAnimatingToOverview = false;

        mDetector.finishedScrolling();
    }

    /**
     * {@link AnimatorUpdateListener} which controls another animation for a fraction of range
     */
    private static class CroppedAnimationController implements AnimatorUpdateListener {

        private final AnimatorPlaybackController mTarget;
        private final FloatRange mRange;

        CroppedAnimationController(AnimatorPlaybackController target, FloatRange range) {
            mTarget = target;
            mRange = range;
        }


        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            float fraction = valueAnimator.getAnimatedFraction();

            if (mRange.start < mRange.end) {
                if (fraction <= mRange.start) {
                    mTarget.setPlayFraction(0);
                } else if (fraction >= mRange.end) {
                    mTarget.setPlayFraction(1);
                } else {
                    mTarget.setPlayFraction((fraction - mRange.start) / (mRange.end - mRange.start));
                }
            } else if (mRange.start > mRange.end) {
                if (fraction >= mRange.start) {
                    mTarget.setPlayFraction(0);
                } else if (fraction <= mRange.end) {
                    mTarget.setPlayFraction(1);
                } else {
                    mTarget.setPlayFraction((fraction - mRange.start) / (mRange.end - mRange.start));
                }
            } else {
                // mRange.start == mRange.end
                mTarget.setPlayFraction(0);
            }
        }
    }
}
