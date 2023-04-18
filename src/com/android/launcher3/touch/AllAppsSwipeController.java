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
import static com.android.launcher3.anim.Interpolators.EMPHASIZED;
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

    private static final float ALL_APPS_CONTENT_FADE_MAX_CLAMPING_THRESHOLD = 0.8f;
    private static final float ALL_APPS_CONTENT_FADE_MIN_CLAMPING_THRESHOLD = 0.5f;
    private static final float ALL_APPS_SCRIM_VISIBLE_THRESHOLD = 0.1f;
    private static final float ALL_APPS_STAGGERED_FADE_THRESHOLD = 0.5f;

    public static final Interpolator ALL_APPS_SCRIM_RESPONDER =
            Interpolators.clampToProgress(
                    LINEAR, ALL_APPS_SCRIM_VISIBLE_THRESHOLD, ALL_APPS_STAGGERED_FADE_THRESHOLD);
    public static final Interpolator ALL_APPS_CLAMPING_RESPONDER =
            Interpolators.clampToProgress(
                    LINEAR,
                    1 - ALL_APPS_CONTENT_FADE_MAX_CLAMPING_THRESHOLD,
                    1 - ALL_APPS_CONTENT_FADE_MIN_CLAMPING_THRESHOLD);

    // ---- Custom interpolators for NORMAL -> ALL_APPS on phones only. ----

    public static final float ALL_APPS_STATE_TRANSITION_ATOMIC = 0.3333f;
    public static final float ALL_APPS_STATE_TRANSITION_MANUAL = 0.4f;
    private static final float ALL_APPS_FADE_END_ATOMIC = 0.8333f;
    private static final float ALL_APPS_FADE_END_MANUAL = 0.8f;
    private static final float ALL_APPS_FULL_DEPTH_PROGRESS = 0.5f;
    private static final float SCRIM_FADE_START_ATOMIC = 0.2642f;
    private static final float SCRIM_FADE_START_MANUAL = 0.117f;
    private static final float WORKSPACE_MOTION_START_ATOMIC = 0.1667f;

    private static final Interpolator LINEAR_EARLY_MANUAL =
            Interpolators.clampToProgress(LINEAR, 0f, ALL_APPS_STATE_TRANSITION_MANUAL);
    private static final Interpolator STEP_TRANSITION_ATOMIC =
            Interpolators.clampToProgress(FINAL_FRAME, 0f, ALL_APPS_STATE_TRANSITION_ATOMIC);
    private static final Interpolator STEP_TRANSITION_MANUAL =
            Interpolators.clampToProgress(FINAL_FRAME, 0f, ALL_APPS_STATE_TRANSITION_MANUAL);

    // The blur to All Apps is set to be complete when the interpolator is at 0.5.
    private static final Interpolator BLUR_ADJUSTED =
            Interpolators.mapToProgress(LINEAR, 0f, ALL_APPS_FULL_DEPTH_PROGRESS);
    public static final Interpolator BLUR_ATOMIC =
            Interpolators.clampToProgress(
                    BLUR_ADJUSTED, WORKSPACE_MOTION_START_ATOMIC, ALL_APPS_STATE_TRANSITION_ATOMIC);
    public static final Interpolator BLUR_MANUAL =
            Interpolators.clampToProgress(BLUR_ADJUSTED, 0f, ALL_APPS_STATE_TRANSITION_MANUAL);

    public static final Interpolator WORKSPACE_FADE_ATOMIC = STEP_TRANSITION_ATOMIC;
    public static final Interpolator WORKSPACE_FADE_MANUAL = STEP_TRANSITION_MANUAL;

    public static final Interpolator WORKSPACE_SCALE_ATOMIC =
            Interpolators.clampToProgress(
                    EMPHASIZED_ACCELERATE, WORKSPACE_MOTION_START_ATOMIC,
                    ALL_APPS_STATE_TRANSITION_ATOMIC);
    public static final Interpolator WORKSPACE_SCALE_MANUAL = LINEAR_EARLY_MANUAL;

    public static final Interpolator HOTSEAT_FADE_ATOMIC = STEP_TRANSITION_ATOMIC;
    public static final Interpolator HOTSEAT_FADE_MANUAL = STEP_TRANSITION_MANUAL;

    public static final Interpolator HOTSEAT_SCALE_ATOMIC =
            Interpolators.clampToProgress(
                    EMPHASIZED_ACCELERATE, WORKSPACE_MOTION_START_ATOMIC,
                    ALL_APPS_STATE_TRANSITION_ATOMIC);
    public static final Interpolator HOTSEAT_SCALE_MANUAL = LINEAR_EARLY_MANUAL;

    public static final Interpolator HOTSEAT_TRANSLATE_ATOMIC = STEP_TRANSITION_ATOMIC;
    public static final Interpolator HOTSEAT_TRANSLATE_MANUAL = STEP_TRANSITION_MANUAL;

    public static final Interpolator SCRIM_FADE_ATOMIC =
            Interpolators.clampToProgress(
                    Interpolators.mapToProgress(LINEAR, 0f, 0.8f),
                    SCRIM_FADE_START_ATOMIC, ALL_APPS_STATE_TRANSITION_ATOMIC);
    public static final Interpolator SCRIM_FADE_MANUAL =
            Interpolators.clampToProgress(
                    LINEAR, SCRIM_FADE_START_MANUAL, ALL_APPS_STATE_TRANSITION_MANUAL);

    public static final Interpolator ALL_APPS_FADE_ATOMIC =
            Interpolators.clampToProgress(
                    Interpolators.mapToProgress(EMPHASIZED_DECELERATE, 0.2f, 1f),
                    ALL_APPS_STATE_TRANSITION_ATOMIC, ALL_APPS_FADE_END_ATOMIC);
    public static final Interpolator ALL_APPS_FADE_MANUAL =
            Interpolators.clampToProgress(
                    LINEAR, ALL_APPS_STATE_TRANSITION_MANUAL, ALL_APPS_FADE_END_MANUAL);

    public static final Interpolator ALL_APPS_VERTICAL_PROGRESS_ATOMIC =
            Interpolators.clampToProgress(
                    Interpolators.mapToProgress(EMPHASIZED_DECELERATE, 0.4f, 1f),
                    ALL_APPS_STATE_TRANSITION_ATOMIC, 1f);
    public static final Interpolator ALL_APPS_VERTICAL_PROGRESS_MANUAL = LINEAR;

    // --------

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
        config.userControlled = true;
        if (fromState == NORMAL && toState == ALL_APPS) {
            applyNormalToAllAppsAnimConfig(mLauncher, config);
        } else if (fromState == ALL_APPS && toState == NORMAL) {
            applyAllAppsToNormalConfig(mLauncher, config);
        }
        return config;
    }

    /**
     * Applies Animation config values for transition from all apps to home.
     */
    public static void applyAllAppsToNormalConfig(Launcher launcher, StateAnimationConfig config) {
        if (launcher.getDeviceProfile().isTablet) {
            config.setInterpolator(ANIM_SCRIM_FADE,
                    Interpolators.reverse(ALL_APPS_SCRIM_RESPONDER));
            config.setInterpolator(ANIM_ALL_APPS_FADE, FINAL_FRAME);
            if (!config.userControlled) {
                config.setInterpolator(ANIM_VERTICAL_PROGRESS, EMPHASIZED);
            }
            config.setInterpolator(ANIM_WORKSPACE_SCALE, EMPHASIZED);
            config.setInterpolator(ANIM_DEPTH, EMPHASIZED);
        } else {
            if (config.userControlled) {
                config.setInterpolator(ANIM_DEPTH, Interpolators.reverse(BLUR_MANUAL));
                config.setInterpolator(ANIM_WORKSPACE_FADE,
                        Interpolators.reverse(WORKSPACE_FADE_MANUAL));
                config.setInterpolator(ANIM_WORKSPACE_SCALE,
                        Interpolators.reverse(WORKSPACE_SCALE_MANUAL));
                config.setInterpolator(ANIM_HOTSEAT_FADE,
                        Interpolators.reverse(HOTSEAT_FADE_MANUAL));
                config.setInterpolator(ANIM_HOTSEAT_SCALE,
                        Interpolators.reverse(HOTSEAT_SCALE_MANUAL));
                config.setInterpolator(ANIM_HOTSEAT_TRANSLATE,
                        Interpolators.reverse(HOTSEAT_TRANSLATE_MANUAL));
                config.setInterpolator(ANIM_SCRIM_FADE, Interpolators.reverse(SCRIM_FADE_MANUAL));
                config.setInterpolator(ANIM_ALL_APPS_FADE,
                        Interpolators.reverse(ALL_APPS_FADE_MANUAL));
                config.setInterpolator(ANIM_VERTICAL_PROGRESS,
                        Interpolators.reverse(ALL_APPS_VERTICAL_PROGRESS_MANUAL));
            } else {
                config.setInterpolator(ANIM_SCRIM_FADE,
                        Interpolators.reverse(ALL_APPS_SCRIM_RESPONDER));
                config.setInterpolator(ANIM_ALL_APPS_FADE, ALL_APPS_CLAMPING_RESPONDER);
                config.setInterpolator(ANIM_WORKSPACE_FADE, INSTANT);
                config.setInterpolator(ANIM_VERTICAL_PROGRESS, EMPHASIZED_ACCELERATE);
            }
        }
    }

    /**
     * Applies Animation config values for transition from home to all apps.
     */
    public static void applyNormalToAllAppsAnimConfig(
            Launcher launcher, StateAnimationConfig config) {
        if (launcher.getDeviceProfile().isTablet) {
            config.setInterpolator(ANIM_ALL_APPS_FADE, INSTANT);
            config.setInterpolator(ANIM_SCRIM_FADE, ALL_APPS_SCRIM_RESPONDER);
            if (!config.userControlled) {
                config.setInterpolator(ANIM_VERTICAL_PROGRESS, EMPHASIZED);
            }
            config.setInterpolator(ANIM_WORKSPACE_SCALE, EMPHASIZED);
            config.setInterpolator(ANIM_DEPTH, EMPHASIZED);
        } else {
            config.setInterpolator(ANIM_DEPTH, config.userControlled ? BLUR_MANUAL : BLUR_ATOMIC);
            config.setInterpolator(ANIM_WORKSPACE_FADE,
                    config.userControlled ? WORKSPACE_FADE_MANUAL : WORKSPACE_FADE_ATOMIC);
            config.setInterpolator(ANIM_WORKSPACE_SCALE,
                    config.userControlled ? WORKSPACE_SCALE_MANUAL : WORKSPACE_SCALE_ATOMIC);
            config.setInterpolator(ANIM_HOTSEAT_FADE,
                    config.userControlled ? HOTSEAT_FADE_MANUAL : HOTSEAT_FADE_ATOMIC);
            config.setInterpolator(ANIM_HOTSEAT_SCALE,
                    config.userControlled ? HOTSEAT_SCALE_MANUAL : HOTSEAT_SCALE_ATOMIC);
            config.setInterpolator(ANIM_HOTSEAT_TRANSLATE,
                    config.userControlled ? HOTSEAT_TRANSLATE_MANUAL : HOTSEAT_TRANSLATE_ATOMIC);
            config.setInterpolator(ANIM_SCRIM_FADE,
                    config.userControlled ? SCRIM_FADE_MANUAL : SCRIM_FADE_ATOMIC);
            config.setInterpolator(ANIM_ALL_APPS_FADE,
                    config.userControlled ? ALL_APPS_FADE_MANUAL : ALL_APPS_FADE_ATOMIC);
            config.setInterpolator(ANIM_VERTICAL_PROGRESS,
                    config.userControlled
                            ? ALL_APPS_VERTICAL_PROGRESS_MANUAL
                            : ALL_APPS_VERTICAL_PROGRESS_ATOMIC);
        }
    }
}
