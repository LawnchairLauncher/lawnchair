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
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_TRANSLATION;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.util.SysuiEventLogger;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

/**
 * Touch controller for handling various state transitions in portrait UI.
 */
public class PortraitStatesTouchController extends AbstractStateChangeTouchController {

    private static final float TOTAL_DISTANCE_MULTIPLIER = 3f;
    private static final float LINEAR_SCALE_LIMIT = 1 / TOTAL_DISTANCE_MULTIPLIER;

    // Must be greater than LINEAR_SCALE_LIMIT;
    private static final float MAXIMUM_DISTANCE_FACTOR = 0.9f;

    // Maximum amount to overshoot.
    private static final float MAX_OVERSHOOT = 0.3f;

    private static final double PI_BY_2 = Math.PI / 2;

    private InterpolatorWrapper mAllAppsInterpolatorWrapper = new InterpolatorWrapper();

    // If true, we will finish the current animation instantly on second touch.
    private boolean mFinishFastOnSecondTouch;

    private final Interpolator mAllAppsDampedInterpolator = new Interpolator() {

        private final double mAngleMultiplier = Math.PI /
                (2 * (MAXIMUM_DISTANCE_FACTOR - LINEAR_SCALE_LIMIT));

        @Override
        public float getInterpolation(float v) {
            if (v <= LINEAR_SCALE_LIMIT) {
                return v * TOTAL_DISTANCE_MULTIPLIER;
            }
            float overshoot = (v - LINEAR_SCALE_LIMIT);
            return (float) (1 + MAX_OVERSHOOT * Math.sin(overshoot * mAngleMultiplier));
        }
    };

    private final Interpolator mOverviewBoundInterpolator = (v) -> {
            if (v >= MAXIMUM_DISTANCE_FACTOR) {
                return 1;
            }
            return FAST_OUT_SLOW_IN.getInterpolation(v / MAXIMUM_DISTANCE_FACTOR);
    };

    public PortraitStatesTouchController(Launcher l) {
        super(l, SwipeDetector.VERTICAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            if (mFinishFastOnSecondTouch) {
                // TODO: Animate to finish instead.
                mCurrentAnimation.getAnimationPlayer().end();
            }

            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (mLauncher.isInState(ALL_APPS)) {
            // In all-apps only listen if the container cannot scroll itself
            if (!mLauncher.getAppsView().shouldContainerScroll(ev)) {
                return false;
            }
        } else {
            // For all other states, only listen if the event originated below the hotseat height
            DeviceProfile dp = mLauncher.getDeviceProfile();
            int hotseatHeight = dp.hotseatBarSizePx + dp.getInsets().bottom;
            if (ev.getY() < (mLauncher.getDragLayer().getHeight() - hotseatHeight)) {
                return false;
            }
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        return true;
    }

    @Override
    protected int getSwipeDirection(MotionEvent ev) {
        final int directionsToDetectScroll;
        if (mLauncher.isInState(ALL_APPS)) {
            directionsToDetectScroll = SwipeDetector.DIRECTION_NEGATIVE;
            mStartContainerType = ContainerType.ALLAPPS;
        } else if (mLauncher.isInState(NORMAL)) {
            directionsToDetectScroll = SwipeDetector.DIRECTION_POSITIVE;
            mStartContainerType = ContainerType.HOTSEAT;
        } else if (mLauncher.isInState(OVERVIEW)) {
            directionsToDetectScroll = SwipeDetector.DIRECTION_BOTH;
            mStartContainerType = ContainerType.TASKSWITCHER;
        } else {
            return 0;
        }
        return directionsToDetectScroll;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (fromState == ALL_APPS) {
            // Should swipe down go to OVERVIEW instead?
            return TouchInteractionService.isConnected() ?
                    mLauncher.getStateManager().getLastState() : NORMAL;
        } else if (fromState == OVERVIEW) {
            return isDragTowardPositive ? ALL_APPS : NORMAL;
        } else if (isDragTowardPositive) {
            return TouchInteractionService.isConnected() ? OVERVIEW : ALL_APPS;
        }
        return fromState;
    }

    private AnimatorSetBuilder getNormalToOverviewAnimation() {
        mAllAppsInterpolatorWrapper.baseInterpolator = mAllAppsDampedInterpolator;

        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        builder.setInterpolator(ANIM_VERTICAL_PROGRESS, mAllAppsInterpolatorWrapper);

        builder.setInterpolator(ANIM_OVERVIEW_TRANSLATION, mOverviewBoundInterpolator);
        return builder;
    }

    @Override
    protected float initCurrentAnimation() {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);

        float startVerticalShift = mFromState.getVerticalProgress(mLauncher) * range;
        float endVerticalShift = mToState.getVerticalProgress(mLauncher) * range;

        float totalShift = endVerticalShift - startVerticalShift;

        final AnimatorSetBuilder builder;

        if (mFromState == NORMAL && mToState == OVERVIEW && totalShift != 0) {
            builder = getNormalToOverviewAnimation();
            totalShift = totalShift * TOTAL_DISTANCE_MULTIPLIER;
        } else {
            builder = new AnimatorSetBuilder();
        }

        if (mPendingAnimation != null) {
            mPendingAnimation.finish(false);
            mPendingAnimation = null;
        }

        RecentsView recentsView = mLauncher.getOverviewPanel();
        TaskView taskView = (TaskView) recentsView.getChildAt(recentsView.getNextPage());
        if (recentsView.shouldSwipeDownLaunchApp() && mFromState == OVERVIEW && mToState == NORMAL
                && taskView != null) {
            mPendingAnimation = recentsView.createTaskLauncherAnimation(taskView, maxAccuracy);
            mPendingAnimation.anim.setInterpolator(Interpolators.ZOOM_IN);

            mCurrentAnimation = AnimatorPlaybackController.wrap(mPendingAnimation.anim, maxAccuracy);
        } else {
            mCurrentAnimation = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(mToState, builder, maxAccuracy);
        }

        if (totalShift == 0) {
            totalShift = Math.signum(mFromState.ordinal - mToState.ordinal)
                    * OverviewState.getDefaultSwipeHeight(mLauncher);
        }
        return 1 / totalShift;
    }

    @Override
    protected void updateSwipeCompleteAnimation(ValueAnimator animator, long expectedDuration,
            LauncherState targetState, float velocity, boolean isFling) {
        handleFirstSwipeToOverview(animator, expectedDuration, targetState, velocity, isFling);
        super.updateSwipeCompleteAnimation(animator, expectedDuration, targetState,
                velocity, isFling);
    }

    private void handleFirstSwipeToOverview(final ValueAnimator animator,
            final long expectedDuration, final LauncherState targetState, final float velocity,
            final boolean isFling) {
        if (mFromState == NORMAL && mToState == OVERVIEW && targetState == OVERVIEW) {
            mFinishFastOnSecondTouch = true;

            // Update all apps interpolator
            float currentFraction = mCurrentAnimation.getProgressFraction();
            float absVelocity = Math.abs(velocity);
            float currentValue = mAllAppsDampedInterpolator.getInterpolation(currentFraction);

            if (isFling && absVelocity > 1 && currentFraction < LINEAR_SCALE_LIMIT) {

                // TODO: Clean up these magic calculations
                // Linearly interpolate the max value based on the velocity.
                float maxValue = Math.max(absVelocity > 4 ? 1 + MAX_OVERSHOOT :
                                1 + (absVelocity - 1) * MAX_OVERSHOOT / 3,
                        currentValue);
                double angleToPeak = PI_BY_2 - Math.asin(currentValue / maxValue);

                if (expectedDuration != 0 && angleToPeak != 0) {

                    float distanceLeft = 1 - currentFraction;
                    mAllAppsInterpolatorWrapper.baseInterpolator = (f) -> {
                        float scaledF = (f - currentFraction) / distanceLeft;

                        if (scaledF < 0.5f) {
                            double angle = PI_BY_2 - angleToPeak + scaledF * angleToPeak / 0.5f;
                            return (float) (maxValue * Math.sin(angle));
                        }

                        scaledF = ((scaledF - .5f) / .5f);
                        double angle = PI_BY_2 + 3 * scaledF * PI_BY_2;
                        float amplitude = (1 - scaledF) * (1 - scaledF) * (maxValue - 1);
                        return 1 + (float) (amplitude * Math.sin(angle));
                    };

                    animator.setDuration(expectedDuration).setInterpolator(LINEAR);
                    return;
                }
            }

            if (currentFraction < LINEAR_SCALE_LIMIT) {
                mAllAppsInterpolatorWrapper.baseInterpolator = LINEAR;
                return;
            }
            float extraValue = mAllAppsDampedInterpolator.getInterpolation(currentFraction) - 1;
            float distanceLeft = 1 - currentFraction;

            animator.setFloatValues(currentFraction, 1);
            mAllAppsInterpolatorWrapper.baseInterpolator = (f) -> {
                float scaledF = (f - currentFraction) / distanceLeft;

                double angle = scaledF * 1.5 * Math.PI;
                float amplitude = (1 - scaledF) * (1 - scaledF) * extraValue;
                return 1 + (float) (amplitude * Math.sin(angle));
            };
            animator.setDuration(200).setInterpolator(LINEAR);
            return;
        }
        mFinishFastOnSecondTouch = false;
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        super.onSwipeInteractionCompleted(targetState, logAction);
        if (mFromState == NORMAL && targetState == OVERVIEW) {
            SysuiEventLogger.writeDummyRecentsTransition(0);
        }
    }

    private static class InterpolatorWrapper implements Interpolator {

        public TimeInterpolator baseInterpolator = LINEAR;

        @Override
        public float getInterpolation(float v) {
            return baseInterpolator.getInterpolation(v);
        }
    }
}
