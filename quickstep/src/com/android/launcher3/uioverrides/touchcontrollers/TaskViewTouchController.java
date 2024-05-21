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
package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.launcher3.AbstractFloatingView.TYPE_TOUCH_CONTROLLER_NO_INTERCEPT;
import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_BOTH;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.VibrationEffect;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.touch.BaseSwipeDetector;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.FlingBlockCheck;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.VibrationConstants;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;

/**
 * Touch controller for handling task view card swipes
 */
public abstract class TaskViewTouchController<CONTAINER extends Context & RecentsViewContainer>
        extends AnimatorListenerAdapter implements TouchController,
        SingleAxisSwipeDetector.Listener {

    private static final float ANIMATION_PROGRESS_FRACTION_MIDPOINT = 0.5f;
    private static final long MIN_TASK_DISMISS_ANIMATION_DURATION = 300;
    private static final long MAX_TASK_DISMISS_ANIMATION_DURATION = 600;

    public static final int TASK_DISMISS_VIBRATION_PRIMITIVE =
            VibrationEffect.Composition.PRIMITIVE_TICK;
    public static final float TASK_DISMISS_VIBRATION_PRIMITIVE_SCALE = 1f;
    public static final VibrationEffect TASK_DISMISS_VIBRATION_FALLBACK =
            VibrationConstants.EFFECT_TEXTURE_TICK;

    protected final CONTAINER mContainer;
    private final SingleAxisSwipeDetector mDetector;
    private final RecentsView mRecentsView;
    private final int[] mTempCords = new int[2];
    private final boolean mIsRtl;

    private AnimatorPlaybackController mCurrentAnimation;
    private boolean mCurrentAnimationIsGoingUp;
    private boolean mAllowGoingUp;
    private boolean mAllowGoingDown;

    private boolean mNoIntercept;

    private float mDisplacementShift;
    private float mProgressMultiplier;
    private float mEndDisplacement;
    private boolean mDraggingEnabled = true;
    private FlingBlockCheck mFlingBlockCheck = new FlingBlockCheck();
    private Float mOverrideVelocity = null;

    private TaskView mTaskBeingDragged;

    private boolean mIsDismissHapticRunning = false;

    public TaskViewTouchController(CONTAINER container) {
        mContainer = container;
        mRecentsView = container.getOverviewPanel();
        mIsRtl = Utilities.isRtl(container.getResources());
        SingleAxisSwipeDetector.Direction dir =
                mRecentsView.getPagedOrientationHandler().getUpDownSwipeDirection();
        mDetector = new SingleAxisSwipeDetector(container, this, dir);
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if ((ev.getEdgeFlags() & Utilities.EDGE_NAV_BAR) != 0) {
            // Don't intercept swipes on the nav bar, as user might be trying to go home
            // during a task dismiss animation.
            if (mCurrentAnimation != null) {
                mCurrentAnimation.getAnimationPlayer().end();
            }
            return false;
        }
        if (mCurrentAnimation != null) {
            mCurrentAnimation.forceFinishIfCloseToEnd();
        }
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenViewWithType(
                mContainer, TYPE_TOUCH_CONTROLLER_NO_INTERCEPT) != null) {
            return false;
        }
        return isRecentsInteractive();
    }

    protected abstract boolean isRecentsInteractive();

    /** Is recents view showing a single task in a modal way. */
    protected abstract boolean isRecentsModal();

    protected void onUserControlledAnimationCreated(AnimatorPlaybackController animController) {
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        if (mCurrentAnimation != null && animation == mCurrentAnimation.getTarget()) {
            clearState();
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if ((ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL)
                && mCurrentAnimation == null) {
            clearState();
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !canInterceptTouch(ev);
            if (mNoIntercept) {
                return false;
            }

            // Now figure out which direction scroll events the controller will start
            // calling the callbacks.
            int directionsToDetectScroll = 0;
            boolean ignoreSlopWhenSettling = false;
            if (mCurrentAnimation != null) {
                directionsToDetectScroll = DIRECTION_BOTH;
                ignoreSlopWhenSettling = true;
            } else {
                mTaskBeingDragged = null;

                for (int i = 0; i < mRecentsView.getTaskViewCount(); i++) {
                    TaskView view = mRecentsView.getTaskViewAt(i);

                    if (mRecentsView.isTaskViewVisible(view) && mContainer.getDragLayer()
                            .isEventOverView(view, ev)) {
                        // Disable swiping up and down if the task overlay is modal.
                        if (isRecentsModal()) {
                            mTaskBeingDragged = null;
                            break;
                        }
                        mTaskBeingDragged = view;
                        int upDirection = mRecentsView.getPagedOrientationHandler()
                                .getUpDirection(mIsRtl);

                        // The task can be dragged up to dismiss it
                        mAllowGoingUp = true;

                        // The task can be dragged down to open it if:
                        // - It's the current page
                        // - We support gestures to enter overview
                        // - It's the focused task if in grid view
                        // - The task is snapped
                        mAllowGoingDown = i == mRecentsView.getCurrentPage()
                                && DisplayController.getNavigationMode(mContainer).hasGestures
                                && (!mRecentsView.showAsGrid() || mTaskBeingDragged.isFocusedTask())
                                && mRecentsView.isTaskInExpectedScrollPosition(i);

                        directionsToDetectScroll = mAllowGoingDown ? DIRECTION_BOTH : upDirection;
                        break;
                    }
                }
                if (mTaskBeingDragged == null) {
                    mNoIntercept = true;
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
        if (mCurrentAnimation != null && mCurrentAnimationIsGoingUp == goingUp) {
            // No need to init
            return;
        }
        if ((goingUp && !mAllowGoingUp) || (!goingUp && !mAllowGoingDown)) {
            // Trying to re-init in an unsupported direction.
            return;
        }
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setPlayFraction(0);
            mCurrentAnimation.getTarget().removeListener(this);
            mCurrentAnimation.dispatchOnCancel();
        }

        RecentsPagedOrientationHandler orientationHandler =
                mRecentsView.getPagedOrientationHandler();
        mCurrentAnimationIsGoingUp = goingUp;
        BaseDragLayer dl = mContainer.getDragLayer();
        final int secondaryLayerDimension = orientationHandler.getSecondaryDimension(dl);
        long maxDuration = 2 * secondaryLayerDimension;
        int verticalFactor = orientationHandler.getTaskDragDisplacementFactor(mIsRtl);
        int secondaryTaskDimension = orientationHandler.getSecondaryDimension(mTaskBeingDragged);
        // The interpolator controlling the most prominent visual movement. We use this to determine
        // whether we passed SUCCESS_TRANSITION_PROGRESS.
        final Interpolator currentInterpolator;
        PendingAnimation pa;
        if (goingUp) {
            currentInterpolator = Interpolators.LINEAR;
            pa = new PendingAnimation(maxDuration);
            mRecentsView.createTaskDismissAnimation(pa, mTaskBeingDragged,
                    true /* animateTaskView */, true /* removeTask */, maxDuration,
                    false /* dismissingForSplitSelection*/);

            mEndDisplacement = -secondaryTaskDimension;
        } else {
            currentInterpolator = Interpolators.ZOOM_IN;
            pa = mRecentsView.createTaskLaunchAnimation(
                    mTaskBeingDragged, maxDuration, currentInterpolator);

            // Since the thumbnail is what is filling the screen, based the end displacement on it.
            View thumbnailView = mTaskBeingDragged.getFirstThumbnailViewDeprecated();
            mTempCords[1] = orientationHandler.getSecondaryDimension(thumbnailView);
            dl.getDescendantCoordRelativeToSelf(thumbnailView, mTempCords);
            mEndDisplacement = secondaryLayerDimension - mTempCords[1];
        }
        mEndDisplacement *= verticalFactor;
        mCurrentAnimation = pa.createPlaybackController();

        // Setting this interpolator doesn't affect the visual motion, but is used to determine
        // whether we successfully reached the target state in onDragEnd().
        mCurrentAnimation.getTarget().setInterpolator(currentInterpolator);
        onUserControlledAnimationCreated(mCurrentAnimation);
        mCurrentAnimation.getTarget().addListener(this);
        mCurrentAnimation.dispatchOnStart();
        mProgressMultiplier = 1 / mEndDisplacement;
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        if (!mDraggingEnabled) return;

        RecentsPagedOrientationHandler orientationHandler =
                mRecentsView.getPagedOrientationHandler();
        if (mCurrentAnimation == null) {
            reInitAnimationController(orientationHandler.isGoingUp(startDisplacement, mIsRtl));
            mDisplacementShift = 0;
        } else {
            mDisplacementShift = mCurrentAnimation.getProgressFraction() / mProgressMultiplier;
            mCurrentAnimation.pause();
        }
        mFlingBlockCheck.unblockFling();
        mOverrideVelocity = null;
    }

    @Override
    public boolean onDrag(float displacement) {
        if (!mDraggingEnabled) return true;

        RecentsPagedOrientationHandler orientationHandler =
                mRecentsView.getPagedOrientationHandler();
        float totalDisplacement = displacement + mDisplacementShift;
        boolean isGoingUp = totalDisplacement == 0 ? mCurrentAnimationIsGoingUp :
                orientationHandler.isGoingUp(totalDisplacement, mIsRtl);
        if (isGoingUp != mCurrentAnimationIsGoingUp) {
            reInitAnimationController(isGoingUp);
            mFlingBlockCheck.blockFling();
        } else {
            mFlingBlockCheck.onEvent();
        }

        if (isGoingUp) {
            if (mCurrentAnimation.getProgressFraction() < ANIMATION_PROGRESS_FRACTION_MIDPOINT) {
                // Halve the value when dismissing, as we are animating the drag across the full
                // length for only the first half of the progress
                mCurrentAnimation.setPlayFraction(
                        Utilities.boundToRange(totalDisplacement * mProgressMultiplier / 2, 0, 1));
            } else {
                // Set mOverrideVelocity to control task dismiss velocity in onDragEnd
                int velocityDimenId = R.dimen.default_task_dismiss_drag_velocity;
                if (mRecentsView.showAsGrid()) {
                    if (mTaskBeingDragged.isFocusedTask()) {
                        velocityDimenId =
                                R.dimen.default_task_dismiss_drag_velocity_grid_focus_task;
                    } else {
                        velocityDimenId = R.dimen.default_task_dismiss_drag_velocity_grid;
                    }
                }
                mOverrideVelocity = -mTaskBeingDragged.getResources().getDimension(velocityDimenId);

                // Once halfway through task dismissal interpolation, switch from reversible
                // dragging-task animation to playing the remaining task translation animations,
                // while this is in progress disable dragging.
                mDraggingEnabled = false;
            }
        } else {
            mCurrentAnimation.setPlayFraction(
                    Utilities.boundToRange(totalDisplacement * mProgressMultiplier, 0, 1));
        }

        return true;
    }

    @Override
    public void onDragEnd(float velocity) {
        if (mOverrideVelocity != null) {
            velocity = mOverrideVelocity;
            mOverrideVelocity = null;
        }
        // Limit velocity, as very large scalar values make animations play too quickly
        float maxTaskDismissDragVelocity = mTaskBeingDragged.getResources().getDimension(
                R.dimen.max_task_dismiss_drag_velocity);
        velocity = Utilities.boundToRange(velocity, -maxTaskDismissDragVelocity,
                maxTaskDismissDragVelocity);
        boolean fling = mDraggingEnabled && mDetector.isFling(velocity);
        final boolean goingToEnd;
        boolean blockedFling = fling && mFlingBlockCheck.isBlocked();
        if (blockedFling) {
            fling = false;
        }
        RecentsPagedOrientationHandler orientationHandler =
                mRecentsView.getPagedOrientationHandler();
        boolean goingUp = orientationHandler.isGoingUp(velocity, mIsRtl);
        float progress = mCurrentAnimation.getProgressFraction();
        float interpolatedProgress = mCurrentAnimation.getInterpolatedProgress();
        if (fling) {
            goingToEnd = goingUp == mCurrentAnimationIsGoingUp;
        } else {
            goingToEnd = interpolatedProgress > SUCCESS_TRANSITION_PROGRESS;
        }
        long animationDuration = BaseSwipeDetector.calculateDuration(
                velocity, goingToEnd ? (1 - progress) : progress);
        if (blockedFling && !goingToEnd) {
            animationDuration *= LauncherAnimUtils.blockedFlingDurationFactor(velocity);
        }
        // Due to very high or low velocity dismissals, animation durations can be inconsistently
        // long or short. Bound the duration for animation of task translations for a more
        // standardized feel.
        animationDuration = Utilities.boundToRange(animationDuration,
                MIN_TASK_DISMISS_ANIMATION_DURATION, MAX_TASK_DISMISS_ANIMATION_DURATION);

        mCurrentAnimation.setEndAction(this::clearState);
        mCurrentAnimation.startWithVelocity(mContainer, goingToEnd, Math.abs(velocity),
                mEndDisplacement, animationDuration);
        if (goingUp && goingToEnd && !mIsDismissHapticRunning) {
            VibratorWrapper.INSTANCE.get(mContainer).vibrate(TASK_DISMISS_VIBRATION_PRIMITIVE,
                    TASK_DISMISS_VIBRATION_PRIMITIVE_SCALE, TASK_DISMISS_VIBRATION_FALLBACK);
            mIsDismissHapticRunning = true;
        }

        mDraggingEnabled = true;
    }

    private void clearState() {
        mDetector.finishedScrolling();
        mDetector.setDetectableScrollConditions(0, false);
        mDraggingEnabled = true;
        mTaskBeingDragged = null;
        mCurrentAnimation = null;
        mIsDismissHapticRunning = false;
    }
}
