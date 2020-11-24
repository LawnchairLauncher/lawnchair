/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.uioverrides.states;

import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.HINT_STATE;
import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_PEEK;
import static com.android.launcher3.WorkspaceStateTransitionAnimation.getSpringScaleAnimator;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL_1_7;
import static com.android.launcher3.anim.Interpolators.DEACCEL_3;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.FINAL_FRAME;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_2;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_7;
import static com.android.launcher3.anim.Interpolators.clampToProgress;
import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_ACTIONS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_TRANSLATE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCRIM_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_X;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherState.ScaleAndTranslation;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.util.RecentsAtomicAnimationFactory;
import com.android.quickstep.views.RecentsView;

/**
 * Animation factory for quickstep specific transitions
 */
public class QuickstepAtomicAnimationFactory extends
        RecentsAtomicAnimationFactory<Launcher, LauncherState> {

    // Scale recents takes before animating in
    private static final float RECENTS_PREPARE_SCALE = 1.33f;

    public static final int INDEX_SHELF_ANIM = RecentsAtomicAnimationFactory.NEXT_INDEX + 0;
    public static final int INDEX_PAUSE_TO_OVERVIEW_ANIM =
            RecentsAtomicAnimationFactory.NEXT_INDEX + 1;

    private static final int MY_ANIM_COUNT = 2;
    protected static final int NEXT_INDEX = RecentsAtomicAnimationFactory.NEXT_INDEX
            + MY_ANIM_COUNT;

    // Due to use of physics, duration may differ between devices so we need to calculate and
    // cache the value.
    private int mHintToNormalDuration = -1;

    public static final long ATOMIC_DURATION_FROM_PAUSED_TO_OVERVIEW = 300;

    public QuickstepAtomicAnimationFactory(QuickstepLauncher activity) {
        super(activity, MY_ANIM_COUNT);
    }

    @Override
    public Animator createStateElementAnimation(int index, float... values) {
        switch (index) {
            case INDEX_SHELF_ANIM: {
                AllAppsTransitionController aatc = mActivity.getAllAppsController();
                Animator springAnim = aatc.createSpringAnimation(values);

                if ((OVERVIEW.getVisibleElements(mActivity) & HOTSEAT_ICONS) != 0) {
                    // Translate hotseat with the shelf until reaching overview.
                    float overviewProgress = OVERVIEW.getVerticalProgress(mActivity);
                    ScaleAndTranslation sat = OVERVIEW.getHotseatScaleAndTranslation(mActivity);
                    float shiftRange = aatc.getShiftRange();
                    if (values.length == 1) {
                        values = new float[] {aatc.getProgress(), values[0]};
                    }
                    ValueAnimator hotseatAnim = ValueAnimator.ofFloat(values);
                    hotseatAnim.addUpdateListener(anim -> {
                        float progress = (Float) anim.getAnimatedValue();
                        if (progress >= overviewProgress || mActivity.isInState(BACKGROUND_APP)) {
                            float hotseatShift = (progress - overviewProgress) * shiftRange;
                            mActivity.getHotseat().setTranslationY(hotseatShift + sat.translationY);
                        }
                    });
                    hotseatAnim.setInterpolator(LINEAR);
                    hotseatAnim.setDuration(springAnim.getDuration());

                    AnimatorSet anim = new AnimatorSet();
                    anim.play(hotseatAnim);
                    anim.play(springAnim);
                    return anim;
                }

                return springAnim;
            }
            case INDEX_PAUSE_TO_OVERVIEW_ANIM: {
                StateAnimationConfig config = new StateAnimationConfig();
                config.duration = ATOMIC_DURATION_FROM_PAUSED_TO_OVERVIEW;

                config.setInterpolator(ANIM_VERTICAL_PROGRESS, OVERSHOOT_1_2);
                config.setInterpolator(ANIM_ALL_APPS_FADE, DEACCEL_3);
                if ((OVERVIEW.getVisibleElements(mActivity) & HOTSEAT_ICONS) != 0) {
                    config.setInterpolator(ANIM_HOTSEAT_SCALE, OVERSHOOT_1_2);
                    config.setInterpolator(ANIM_HOTSEAT_TRANSLATE, OVERSHOOT_1_2);
                }

                StateManager<LauncherState> stateManager = mActivity.getStateManager();
                return stateManager.createAtomicAnimation(
                        stateManager.getCurrentStableState(), OVERVIEW, config);
            }
            default:
                return super.createStateElementAnimation(index, values);
        }
    }

    @Override
    public void prepareForAtomicAnimation(LauncherState fromState, LauncherState toState,
            StateAnimationConfig config) {
        if (toState == NORMAL && fromState == OVERVIEW) {
            config.setInterpolator(ANIM_WORKSPACE_SCALE, DEACCEL);
            config.setInterpolator(ANIM_WORKSPACE_FADE, ACCEL);
            config.setInterpolator(ANIM_ALL_APPS_FADE, ACCEL);
            config.setInterpolator(ANIM_OVERVIEW_SCALE, clampToProgress(ACCEL, 0, 0.9f));
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, ACCEL_DEACCEL);

            if (SysUINavigationMode.getMode(mActivity) == NO_BUTTON) {
                config.setInterpolator(ANIM_OVERVIEW_FADE, FINAL_FRAME);
            } else {
                config.setInterpolator(ANIM_OVERVIEW_FADE, DEACCEL_1_7);
            }

            Workspace workspace = mActivity.getWorkspace();
            // Start from a higher workspace scale, but only if we're invisible so we don't jump.
            boolean isWorkspaceVisible = workspace.getVisibility() == VISIBLE;
            if (isWorkspaceVisible) {
                CellLayout currentChild = (CellLayout) workspace.getChildAt(
                        workspace.getCurrentPage());
                isWorkspaceVisible = currentChild.getVisibility() == VISIBLE
                        && currentChild.getShortcutsAndWidgets().getAlpha() > 0;
            }
            if (!isWorkspaceVisible) {
                workspace.setScaleX(0.92f);
                workspace.setScaleY(0.92f);
            }
            Hotseat hotseat = mActivity.getHotseat();
            boolean isHotseatVisible = hotseat.getVisibility() == VISIBLE && hotseat.getAlpha() > 0;
            if (!isHotseatVisible) {
                hotseat.setScaleX(0.92f);
                hotseat.setScaleY(0.92f);
                if (ENABLE_OVERVIEW_ACTIONS.get()) {
                    AllAppsContainerView qsbContainer = mActivity.getAppsView();
                    View qsb = qsbContainer.getSearchView();
                    boolean qsbVisible = qsb.getVisibility() == VISIBLE && qsb.getAlpha() > 0;
                    if (!qsbVisible) {
                        qsbContainer.setScaleX(0.92f);
                        qsbContainer.setScaleY(0.92f);
                    }
                }
            }
        } else if (toState == NORMAL && fromState == OVERVIEW_PEEK) {
            // Keep fully visible until the very end (when overview is offscreen) to make invisible.
            config.setInterpolator(ANIM_OVERVIEW_FADE, FINAL_FRAME);
        } else if (toState == OVERVIEW_PEEK && fromState == NORMAL) {
            config.setInterpolator(ANIM_OVERVIEW_FADE, INSTANT);
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, OVERSHOOT_1_7);
            config.setInterpolator(ANIM_OVERVIEW_SCRIM_FADE, FAST_OUT_SLOW_IN);
        } else if ((fromState == NORMAL || fromState == HINT_STATE) && toState == OVERVIEW) {
            if (SysUINavigationMode.getMode(mActivity) == NO_BUTTON) {
                config.setInterpolator(ANIM_WORKSPACE_SCALE,
                        fromState == NORMAL ? ACCEL : OVERSHOOT_1_2);
                config.setInterpolator(ANIM_WORKSPACE_TRANSLATE, ACCEL);
                config.setInterpolator(ANIM_OVERVIEW_FADE, INSTANT);
            } else {
                config.setInterpolator(ANIM_WORKSPACE_SCALE, OVERSHOOT_1_2);
                config.setInterpolator(ANIM_OVERVIEW_FADE, OVERSHOOT_1_2);

                // Scale up the recents, if it is not coming from the side
                RecentsView overview = mActivity.getOverviewPanel();
                if (overview.getVisibility() != VISIBLE || overview.getContentAlpha() == 0) {
                    RECENTS_SCALE_PROPERTY.set(overview, RECENTS_PREPARE_SCALE);
                }
            }
            config.setInterpolator(ANIM_WORKSPACE_FADE, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_ALL_APPS_FADE, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_OVERVIEW_SCALE, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_DEPTH, OVERSHOOT_1_2);
            Interpolator translationInterpolator = ENABLE_OVERVIEW_ACTIONS.get()
                    && removeShelfFromOverview(mActivity)
                    ? OVERSHOOT_1_2
                    : OVERSHOOT_1_7;
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, translationInterpolator);
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, translationInterpolator);
        } else if (fromState == HINT_STATE && toState == NORMAL) {
            config.setInterpolator(ANIM_DEPTH, DEACCEL_3);
            if (mHintToNormalDuration == -1) {
                ValueAnimator va = getSpringScaleAnimator(mActivity, mActivity.getWorkspace(),
                        toState.getWorkspaceScaleAndTranslation(mActivity).scale);
                mHintToNormalDuration = (int) va.getDuration();
            }
            config.duration = Math.max(config.duration, mHintToNormalDuration);
        }
    }
}
