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

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.app.animation.Interpolators.DECELERATE;
import static com.android.app.animation.Interpolators.DECELERATE_1_7;
import static com.android.app.animation.Interpolators.DECELERATE_3;
import static com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.app.animation.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.app.animation.Interpolators.FINAL_FRAME;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.OVERSHOOT_0_75;
import static com.android.app.animation.Interpolators.OVERSHOOT_1_2;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.HINT_STATE;
import static com.android.launcher3.LauncherState.HINT_STATE_TWO_BUTTON;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.QuickstepTransitionManager.TASKBAR_TO_HOME_DURATION;
import static com.android.launcher3.WorkspaceStateTransitionAnimation.getWorkspaceSpringScaleAnimator;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_ACTIONS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SPLIT_SELECT_FLOATING_TASK_TRANSLATE_OFFSCREEN;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_X;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_SCRIM_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;

import android.animation.ValueAnimator;
import android.util.Log;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Workspace;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.AllAppsSwipeController;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.util.RecentsAtomicAnimationFactory;
import com.android.quickstep.util.SplitAnimationTimings;
import com.android.quickstep.views.RecentsView;

/**
 * Animation factory for quickstep specific transitions
 */
public class QuickstepAtomicAnimationFactory extends
        RecentsAtomicAnimationFactory<QuickstepLauncher, LauncherState> {

    // Scale recents takes before animating in
    private static final float RECENTS_PREPARE_SCALE = 1.33f;
    // Scale workspace takes before animating in
    private static final float WORKSPACE_PREPARE_SCALE = 0.92f;
    // Constants to specify how to scroll RecentsView to the default page if it's not already there.
    private static final int DEFAULT_PAGE = 0;
    private static final int PER_PAGE_SCROLL_DURATION = 150;
    private static final int MAX_PAGE_SCROLL_DURATION = 750;

    // Due to use of physics, duration may differ between devices so we need to calculate and
    // cache the value.
    private int mHintToNormalDuration = -1;

    public QuickstepAtomicAnimationFactory(QuickstepLauncher activity) {
        super(activity);
    }

    @Override
    public void prepareForAtomicAnimation(LauncherState fromState, LauncherState toState,
            StateAnimationConfig config) {
        Log.d(TestProtocol.OVERVIEW_OVER_HOME, "creating animation fromState: "
                + fromState + " toState: " + toState);
        RecentsView overview = mActivity.getOverviewPanel();
        if ((fromState == OVERVIEW || fromState == OVERVIEW_SPLIT_SELECT) && toState == NORMAL) {
            overview.switchToScreenshot(() ->
                    overview.finishRecentsAnimation(true /* toRecents */, null));

            if (fromState == OVERVIEW_SPLIT_SELECT) {
                config.setInterpolator(ANIM_OVERVIEW_SPLIT_SELECT_FLOATING_TASK_TRANSLATE_OFFSCREEN,
                        clampToProgress(EMPHASIZED_ACCELERATE, 0, 0.4f));
                config.setInterpolator(ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE,
                        clampToProgress(LINEAR, 0, 0.33f));
            }

            config.setInterpolator(ANIM_OVERVIEW_ACTIONS_FADE, clampToProgress(LINEAR, 0, 0.25f));
            config.setInterpolator(ANIM_SCRIM_FADE,
                    fromState == OVERVIEW_SPLIT_SELECT
                            ? clampToProgress(LINEAR, 0.33f, 1)
                            : LINEAR);
            config.setInterpolator(ANIM_WORKSPACE_SCALE, DECELERATE);
            config.setInterpolator(ANIM_WORKSPACE_FADE, ACCELERATE);

            if (DisplayController.getNavigationMode(mActivity).hasGestures
                    && overview.getTaskViewCount() > 0) {
                // Overview is going offscreen, so keep it at its current scale and opacity.
                config.setInterpolator(ANIM_OVERVIEW_SCALE, FINAL_FRAME);
                config.setInterpolator(ANIM_OVERVIEW_FADE, FINAL_FRAME);
                config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X,
                        fromState == OVERVIEW_SPLIT_SELECT
                                ? EMPHASIZED_DECELERATE
                                : clampToProgress(FAST_OUT_SLOW_IN, 0, 0.75f));
                config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, FINAL_FRAME);

                // Scroll RecentsView to page 0 as it goes offscreen, if necessary.
                int numPagesToScroll = overview.getNextPage() - DEFAULT_PAGE;
                long scrollDuration = Math.min(MAX_PAGE_SCROLL_DURATION,
                        numPagesToScroll * PER_PAGE_SCROLL_DURATION);
                config.duration = Math.max(config.duration, scrollDuration);

                // Sync scroll so that it ends before or at the same time as the taskbar animation.
                if (DisplayController.isTransientTaskbar(mActivity)
                        && mActivity.getDeviceProfile().isTaskbarPresent) {
                    config.duration = Math.min(config.duration, TASKBAR_TO_HOME_DURATION);
                }
                overview.snapToPage(DEFAULT_PAGE, Math.toIntExact(config.duration));
            } else {
                config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, ACCELERATE_DECELERATE);
                config.setInterpolator(ANIM_OVERVIEW_SCALE, clampToProgress(ACCELERATE, 0, 0.9f));
                config.setInterpolator(ANIM_OVERVIEW_FADE, DECELERATE_1_7);
            }

            Workspace<?> workspace = mActivity.getWorkspace();
            // Start from a higher workspace scale, but only if we're invisible so we don't jump.
            boolean isWorkspaceVisible = workspace.getVisibility() == VISIBLE;
            if (isWorkspaceVisible) {
                CellLayout currentChild = (CellLayout) workspace.getChildAt(
                        workspace.getCurrentPage());
                isWorkspaceVisible = currentChild.getVisibility() == VISIBLE
                        && currentChild.getShortcutsAndWidgets().getAlpha() > 0;
            }
            if (!isWorkspaceVisible) {
                workspace.setScaleX(WORKSPACE_PREPARE_SCALE);
                workspace.setScaleY(WORKSPACE_PREPARE_SCALE);
            }
            Hotseat hotseat = mActivity.getHotseat();
            boolean isHotseatVisible = hotseat.getVisibility() == VISIBLE && hotseat.getAlpha() > 0;
            if (!isHotseatVisible) {
                hotseat.setScaleX(WORKSPACE_PREPARE_SCALE);
                hotseat.setScaleY(WORKSPACE_PREPARE_SCALE);
            }
        } else if ((fromState == NORMAL || fromState == HINT_STATE
                || fromState == HINT_STATE_TWO_BUTTON) && toState == OVERVIEW) {
            if (DisplayController.getNavigationMode(mActivity).hasGestures) {
                config.setInterpolator(ANIM_WORKSPACE_SCALE,
                        fromState == NORMAL ? ACCELERATE : OVERSHOOT_1_2);
                config.setInterpolator(ANIM_WORKSPACE_TRANSLATE, ACCELERATE);

                // Scrolling in tasks, so show straight away
                if (overview.getTaskViewCount() > 0) {
                    config.setInterpolator(ANIM_OVERVIEW_FADE, INSTANT);
                } else {
                    config.setInterpolator(ANIM_OVERVIEW_FADE, OVERSHOOT_1_2);
                }
            } else {
                config.setInterpolator(ANIM_WORKSPACE_SCALE, OVERSHOOT_1_2);
                config.setInterpolator(ANIM_OVERVIEW_FADE, OVERSHOOT_1_2);

                // Scale up the recents, if it is not coming from the side
                if (overview.getVisibility() != VISIBLE || overview.getContentAlpha() == 0) {
                    RECENTS_SCALE_PROPERTY.set(overview, RECENTS_PREPARE_SCALE);
                }
            }
            config.setInterpolator(ANIM_WORKSPACE_FADE, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_ALL_APPS_FADE, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_OVERVIEW_SCALE, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_DEPTH, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_SCRIM_FADE, t -> {
                // Animate at the same rate until reaching progress 1, and skip the overshoot.
                return Math.min(1, OVERSHOOT_1_2.getInterpolation(t));
            });
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, OVERSHOOT_1_2);
        } else if (fromState == HINT_STATE && toState == NORMAL) {
            config.setInterpolator(ANIM_DEPTH, DECELERATE_3);
            if (mHintToNormalDuration == -1) {
                ValueAnimator va = getWorkspaceSpringScaleAnimator(mActivity,
                        mActivity.getWorkspace(),
                        toState.getWorkspaceScaleAndTranslation(mActivity).scale);
                mHintToNormalDuration = (int) va.getDuration();
            }
            config.duration = Math.max(config.duration, mHintToNormalDuration);
        } else if (fromState == ALL_APPS && toState == NORMAL) {
            AllAppsSwipeController.applyAllAppsToNormalConfig(mActivity, config);
        } else if (fromState == NORMAL && toState == ALL_APPS) {
            AllAppsSwipeController.applyNormalToAllAppsAnimConfig(mActivity, config);
        } else if (fromState == OVERVIEW && toState == OVERVIEW_SPLIT_SELECT) {
            SplitAnimationTimings timings = mActivity.getDeviceProfile().isTablet
                    ? SplitAnimationTimings.TABLET_OVERVIEW_TO_SPLIT
                    : SplitAnimationTimings.PHONE_OVERVIEW_TO_SPLIT;
            config.setInterpolator(ANIM_OVERVIEW_ACTIONS_FADE, clampToProgress(LINEAR,
                    timings.getActionsFadeStartOffset(),
                    timings.getActionsFadeEndOffset()));
        } else if ((fromState == NORMAL || fromState == ALL_APPS)
                && toState == OVERVIEW_SPLIT_SELECT) {
            // Splitting from Home is currently only available on tablets
            SplitAnimationTimings timings = SplitAnimationTimings.TABLET_HOME_TO_SPLIT;
            config.setInterpolator(ANIM_SCRIM_FADE, clampToProgress(LINEAR,
                    timings.getScrimFadeInStartOffset(),
                    timings.getScrimFadeInEndOffset()));
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, OVERSHOOT_0_75);
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, OVERSHOOT_0_75);
        }
    }
}
