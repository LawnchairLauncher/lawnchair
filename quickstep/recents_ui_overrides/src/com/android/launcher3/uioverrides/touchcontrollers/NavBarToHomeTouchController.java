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
package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.anim.Interpolators.DEACCEL_3;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.quickstep.views.RecentsView;

/**
 * Handles swiping up on the nav bar to go home from overview or all apps.
 */
public class NavBarToHomeTouchController extends AbstractStateChangeTouchController {

    private static final Interpolator PULLBACK_INTERPOLATOR = DEACCEL_3;

    public NavBarToHomeTouchController(Launcher launcher) {
        super(launcher, SwipeDetector.VERTICAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        boolean cameFromNavBar = (ev.getEdgeFlags() & Utilities.EDGE_NAV_BAR) != 0;
        return cameFromNavBar && (mLauncher.isInState(OVERVIEW) || mLauncher.isInState(ALL_APPS));
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        return isDragTowardPositive ? NORMAL : fromState;
    }

    @Override
    protected float initCurrentAnimation(int animComponents) {
        long accuracy = (long) (getShiftRange() * 2);
        final AnimatorSet anim;
        if (mFromState == OVERVIEW) {
            anim = new AnimatorSet();
            RecentsView recentsView = mLauncher.getOverviewPanel();
            float pullbackDistance = recentsView.getPaddingStart() / 2;
            if (!recentsView.isRtl()) {
                pullbackDistance = -pullbackDistance;
            }
            anim.play(ObjectAnimator.ofFloat(recentsView, View.TRANSLATION_X, pullbackDistance));
            anim.setInterpolator(PULLBACK_INTERPOLATOR);
        } else { // if (mFromState == ALL_APPS)
            AnimatorSetBuilder builder = new AnimatorSetBuilder();
            AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
            final float pullbackDistance = mLauncher.getDeviceProfile().allAppsIconSizePx / 2;
            Animator allAppsProgress = ObjectAnimator.ofFloat(allAppsController, ALL_APPS_PROGRESS,
                    -pullbackDistance / allAppsController.getShiftRange());
            allAppsProgress.setInterpolator(PULLBACK_INTERPOLATOR);
            builder.play(allAppsProgress);
            // Slightly fade out all apps content to further distinguish from scrolling.
            builder.setInterpolator(AnimatorSetBuilder.ANIM_ALL_APPS_FADE, Interpolators
                    .mapToProgress(PULLBACK_INTERPOLATOR, 0, 0.5f));
            AnimationConfig config = new AnimationConfig();
            config.duration = accuracy;
            allAppsController.setAlphas(mToState.getVisibleElements(mLauncher), config, builder);
            anim = builder.build();
        }
        anim.setDuration(accuracy);
        mCurrentAnimation = AnimatorPlaybackController.wrap(anim, accuracy, this::clearState);
        return -1 / getShiftRange();
    }

    @Override
    public void onDragStart(boolean start) {
        super.onDragStart(start);
        mStartContainerType = LauncherLogProto.ContainerType.NAVBAR;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        final int logAction = fling ? Touch.FLING : Touch.SWIPE;
        float interpolatedProgress = PULLBACK_INTERPOLATOR.getInterpolation(
                mCurrentAnimation.getProgressFraction());
        if (interpolatedProgress >= SUCCESS_TRANSITION_PROGRESS || velocity < 0 && fling) {
            mLauncher.getStateManager().goToState(mToState, true,
                    () -> onSwipeInteractionCompleted(mToState, logAction));
        } else {
            // Quickly return to the state we came from (we didn't move far).
            AnimatorPlaybackController anim = mLauncher.getStateManager()
                    .createAnimationToNewWorkspace(mFromState, 80);
            anim.setEndAction(() -> onSwipeInteractionCompleted(mFromState, logAction));
            anim.start();
        }
        mCurrentAnimation.dispatchOnCancel();
    }

    @Override
    protected int getDirectionForLog() {
        return LauncherLogProto.Action.Direction.UP;
    }

    @Override
    protected boolean goingBetweenNormalAndOverview(LauncherState fromState,
            LauncherState toState) {
        // We don't want to create an atomic animation to/from overview.
        return false;
    }

    @Override
    protected int getLogContainerTypeForNormalState() {
        return LauncherLogProto.ContainerType.NAVBAR;
    }
}
