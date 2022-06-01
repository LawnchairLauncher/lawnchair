/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.touch;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.Interpolators.DECELERATED_EASE;
import static com.android.launcher3.anim.Interpolators.EMPHASIZED_ACCELERATE;
import static com.android.launcher3.anim.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.launcher3.anim.Interpolators.FINAL_FRAME;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_TRANSLATE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_SCRIM_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE;

import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.states.StateAnimationConfig;

/**
 * TouchController to switch between NORMAL and ALL_APPS state.
 */
public class AllAppsSwipeController extends AbstractStateChangeTouchController {

    private static final float ALLAPPS_STAGGERED_FADE_THRESHOLD = 0.5f;

    // Custom timing for NORMAL -> ALL_APPS on phones only.
    private static final float WORKSPACE_MOTION_START = 0.1667f;
    private static final float ALL_APPS_STATE_TRANSITION = 0.305f;
    private static final float ALL_APPS_FADE_END = 0.4717f;
    private static final float ALL_APPS_FULL_DEPTH_PROGRESS = 0.5f;

    public static final Interpolator ALLAPPS_STAGGERED_FADE_EARLY_RESPONDER =
            Interpolators.clampToProgress(LINEAR, 0, ALLAPPS_STAGGERED_FADE_THRESHOLD);
    public static final Interpolator ALLAPPS_STAGGERED_FADE_LATE_RESPONDER =
            Interpolators.clampToProgress(LINEAR, ALLAPPS_STAGGERED_FADE_THRESHOLD, 1f);

    // Custom interpolators for NORMAL -> ALL_APPS on phones only.
    // The blur to All Apps is set to be complete when the interpolator is at 0.5.
    public static final Interpolator BLUR =
            Interpolators.clampToProgress(
                    Interpolators.mapToProgress(
                            LINEAR, 0f, ALL_APPS_FULL_DEPTH_PROGRESS),
                    WORKSPACE_MOTION_START, ALL_APPS_STATE_TRANSITION);
    public static final Interpolator WORKSPACE_FADE =
            Interpolators.clampToProgress(FINAL_FRAME, 0f, ALL_APPS_STATE_TRANSITION);
    public static final Interpolator WORKSPACE_SCALE =
            Interpolators.clampToProgress(
                    EMPHASIZED_ACCELERATE, WORKSPACE_MOTION_START, ALL_APPS_STATE_TRANSITION);
    public static final Interpolator HOTSEAT_FADE = WORKSPACE_FADE;
    public static final Interpolator HOTSEAT_SCALE = HOTSEAT_FADE;
    public static final Interpolator HOTSEAT_TRANSLATE =
            Interpolators.clampToProgress(
                    EMPHASIZED_ACCELERATE, WORKSPACE_MOTION_START, ALL_APPS_STATE_TRANSITION);
    public static final Interpolator SCRIM_FADE =
            Interpolators.clampToProgress(
                    Interpolators.mapToProgress(LINEAR, 0f, 0.8f),
                    WORKSPACE_MOTION_START, ALL_APPS_STATE_TRANSITION);
    public static final Interpolator ALL_APPS_FADE =
            Interpolators.clampToProgress(
                    Interpolators.mapToProgress(DECELERATED_EASE, 0.2f, 1.0f),
                    ALL_APPS_STATE_TRANSITION, ALL_APPS_FADE_END);
    public static final Interpolator ALL_APPS_VERTICAL_PROGRESS =
            Interpolators.clampToProgress(
                    Interpolators.mapToProgress(EMPHASIZED_DECELERATE, 0.4f, 1.0f),
                    ALL_APPS_STATE_TRANSITION, 1.0f);

    public AllAppsSwipeController(Launcher l) {
        super(l, SingleAxisSwipeDetector.VERTICAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        if (!mLauncher.isInState(NORMAL) && !mLauncher.isInState(ALL_APPS)) {
            // Don't listen for the swipe gesture if we are already in some other state.
            return false;
        }
        if (mLauncher.isInState(ALL_APPS) && !mLauncher.getAppsView().shouldContainerScroll(ev)) {
            return false;
        }
        return true;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (fromState == NORMAL && isDragTowardPositive) {
            return ALL_APPS;
        } else if (fromState == ALL_APPS && !isDragTowardPositive) {
            return NORMAL;
        }
        return fromState;
    }

    @Override
    protected float initCurrentAnimation() {
        float range = getShiftRange();
        StateAnimationConfig config = getConfigForStates(mFromState, mToState);
        config.duration = (long) (2 * range);

        mCurrentAnimation = mLauncher.getStateManager()
                .createAnimationToNewWorkspace(mToState, config);
        float startVerticalShift = mFromState.getVerticalProgress(mLauncher) * range;
        float endVerticalShift = mToState.getVerticalProgress(mLauncher) * range;
        float totalShift = endVerticalShift - startVerticalShift;
        return 1 / totalShift;
    }

    @Override
    protected StateAnimationConfig getConfigForStates(LauncherState fromState,
            LauncherState toState) {
        StateAnimationConfig config = super.getConfigForStates(fromState, toState);
        if (fromState == NORMAL && toState == ALL_APPS) {
            applyNormalToAllAppsAnimConfig(mLauncher, config);
        } else if (fromState == ALL_APPS && toState == NORMAL) {
            applyAllAppsToNormalConfig(mLauncher, config);
        }
        return config;
    }

    /**
     * Applies Animation config values for transition from all apps to home
     */
    public static void applyAllAppsToNormalConfig(Launcher launcher, StateAnimationConfig config) {
        boolean isTablet = launcher.getDeviceProfile().isTablet;
        config.setInterpolator(ANIM_SCRIM_FADE, ALLAPPS_STAGGERED_FADE_LATE_RESPONDER);
        config.setInterpolator(ANIM_ALL_APPS_FADE, isTablet
                ? FINAL_FRAME : ALLAPPS_STAGGERED_FADE_EARLY_RESPONDER);
        if (!isTablet) {
            config.setInterpolator(ANIM_WORKSPACE_FADE, INSTANT);
        }
    }

    /**
     * Applies Animation config values for transition from home to all apps
     */
    public static void applyNormalToAllAppsAnimConfig(Launcher launcher,
            StateAnimationConfig config) {
        if (launcher.getDeviceProfile().isTablet) {
            config.setInterpolator(ANIM_SCRIM_FADE, ALLAPPS_STAGGERED_FADE_EARLY_RESPONDER);
            config.setInterpolator(ANIM_ALL_APPS_FADE, INSTANT);
        } else {
            config.setInterpolator(ANIM_DEPTH, BLUR);
            config.setInterpolator(ANIM_WORKSPACE_FADE, WORKSPACE_FADE);
            config.setInterpolator(ANIM_WORKSPACE_SCALE, WORKSPACE_SCALE);
            config.setInterpolator(ANIM_HOTSEAT_FADE, HOTSEAT_FADE);
            config.setInterpolator(ANIM_HOTSEAT_SCALE, HOTSEAT_SCALE);
            config.setInterpolator(ANIM_HOTSEAT_TRANSLATE, HOTSEAT_TRANSLATE);
            config.setInterpolator(ANIM_SCRIM_FADE, SCRIM_FADE);
            config.setInterpolator(ANIM_ALL_APPS_FADE, ALL_APPS_FADE);
            config.setInterpolator(ANIM_VERTICAL_PROGRESS, ALL_APPS_VERTICAL_PROGRESS);
        }
    }
}
