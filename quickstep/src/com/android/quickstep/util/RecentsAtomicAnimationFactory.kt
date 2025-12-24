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
package com.android.quickstep.util

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import com.android.app.animation.Interpolators.ACCELERATE
import com.android.app.animation.Interpolators.ACCELERATE_DECELERATE
import com.android.app.animation.Interpolators.DECELERATE
import com.android.app.animation.Interpolators.DECELERATE_1_7
import com.android.app.animation.Interpolators.DECELERATE_3
import com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE
import com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE
import com.android.app.animation.Interpolators.FAST_OUT_SLOW_IN
import com.android.app.animation.Interpolators.FINAL_FRAME
import com.android.app.animation.Interpolators.INSTANT
import com.android.app.animation.Interpolators.LINEAR
import com.android.app.animation.Interpolators.OVERSHOOT_0_75
import com.android.app.animation.Interpolators.OVERSHOOT_1_2
import com.android.app.animation.Interpolators.clampToProgress
import com.android.launcher3.LauncherState.ALL_APPS
import com.android.launcher3.LauncherState.HINT_STATE
import com.android.launcher3.LauncherState.HINT_STATE_TWO_BUTTON
import com.android.launcher3.LauncherState.NORMAL
import com.android.launcher3.LauncherState.OVERVIEW
import com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT
import com.android.launcher3.QuickstepTransitionManager
import com.android.launcher3.anim.SpringAnimationBuilder
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory
import com.android.launcher3.statemanager.StatefulContainer
import com.android.launcher3.states.StateAnimationConfig
import com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE
import com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_ACTIONS_FADE
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SPLIT_SELECT_FLOATING_TASK_TRANSLATE_OFFSCREEN
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_X
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y
import com.android.launcher3.states.StateAnimationConfig.ANIM_SCRIM_FADE
import com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE
import com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE
import com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.NavigationMode
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import kotlin.math.max
import kotlin.math.min

open class RecentsAtomicAnimationFactory<CONTAINER, STATE_TYPE : BaseState<STATE_TYPE>>(
    protected val container: CONTAINER
) : AtomicAnimationFactory<STATE_TYPE>(MY_ANIM_COUNT) where
CONTAINER : Context,
CONTAINER : RecentsViewContainer,
CONTAINER : StatefulContainer<STATE_TYPE> {

    override fun createStateElementAnimation(index: Int, vararg values: Float): Animator =
        when (index) {
            INDEX_RECENTS_FADE_ANIM -> {
                ObjectAnimator.ofFloat(
                    container.getOverviewPanel(),
                    RecentsView.CONTENT_ALPHA,
                    *values,
                )
            }
            INDEX_RECENTS_ATTACHED_ALPHA_ANIM,
            INDEX_RECENTS_TRANSLATE_X_ANIM -> {
                SpringAnimationBuilder(container)
                    .setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
                    .setDampingRatio(0.8f)
                    .setStiffness(250f)
                    .setValues(*values)
                    .build(
                        container.getOverviewPanel(),
                        if (index == INDEX_RECENTS_ATTACHED_ALPHA_ANIM)
                            RecentsView.RUNNING_TASK_ATTACH_ALPHA
                        else RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET,
                    )
            }
            else -> super.createStateElementAnimation(index, *values)
        }

    protected open fun applyOverviewToHomeAnimConfig(
        fromState: STATE_TYPE,
        config: StateAnimationConfig,
        overview: RecentsView<CONTAINER, STATE_TYPE>,
        isPinnedTaskbar: Boolean,
        isThreeButton: Boolean,
    ) {
        overview.switchToScreenshot {
            overview.finishRecentsAnimation(/* toRecents= */ true, /* onFinishComplete= */ null)
        }

        if (fromState == OVERVIEW_SPLIT_SELECT) {
            config.setInterpolator(
                ANIM_OVERVIEW_SPLIT_SELECT_FLOATING_TASK_TRANSLATE_OFFSCREEN,
                clampToProgress(EMPHASIZED_ACCELERATE, 0f, 0.4f),
            )
            config.setInterpolator(
                ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE,
                clampToProgress(LINEAR, 0f, 0.33f),
            )
        }

        // We sync the scrim fade with the taskbar animation duration to avoid any flickers for
        // taskbar icons disappearing before hotseat icons show up.
        val isPersistentTaskbarAndNotInDesktopMode =
            (isThreeButton || isPinnedTaskbar) &&
                !DesktopVisibilityController.INSTANCE.get(container)
                    .isInDesktopMode(container.displayId)
        val scrimUpperBoundFromSplit =
            (QuickstepTransitionManager.getTaskbarToHomeDuration(
                    isPersistentTaskbarAndNotInDesktopMode
                ) / config.duration.toFloat())
                .coerceAtMost(1f)
        config.setInterpolator(ANIM_OVERVIEW_ACTIONS_FADE, clampToProgress(LINEAR, 0f, 0.25f))
        config.setInterpolator(
            ANIM_SCRIM_FADE,
            if (fromState == OVERVIEW_SPLIT_SELECT)
                clampToProgress(LINEAR, 0.33f, scrimUpperBoundFromSplit)
            else LINEAR,
        )
        config.setInterpolator(ANIM_WORKSPACE_SCALE, DECELERATE)
        config.setInterpolator(ANIM_WORKSPACE_FADE, ACCELERATE)

        if (DisplayController.getNavigationMode(container).hasGestures && overview.hasTaskViews()) {
            // Overview is going offscreen, so keep it at its current scale and opacity.
            config.setInterpolator(ANIM_OVERVIEW_SCALE, FINAL_FRAME)
            config.setInterpolator(ANIM_OVERVIEW_FADE, FINAL_FRAME)
            config.setInterpolator(
                ANIM_OVERVIEW_TRANSLATE_X,
                if (fromState == OVERVIEW_SPLIT_SELECT) EMPHASIZED_DECELERATE
                else clampToProgress(FAST_OUT_SLOW_IN, 0f, 0.75f),
            )
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, FINAL_FRAME)

            // Scroll RecentsView to page 0 as it goes offscreen, if necessary.
            val numPagesToScroll = overview.nextPage - DEFAULT_PAGE
            val scrollDuration =
                min(MAX_PAGE_SCROLL_DURATION, (numPagesToScroll * PER_PAGE_SCROLL_DURATION))
            config.duration = max(config.duration, scrollDuration.toLong())

            // Sync scroll so that it ends before or at the same time as the taskbar animation.
            if (container.deviceProfile.isTaskbarPresent) {
                config.duration =
                    min(
                        config.duration,
                        QuickstepTransitionManager.getTaskbarToHomeDuration(
                                isPersistentTaskbarAndNotInDesktopMode
                            )
                            .toLong(),
                    )
            }
            overview.snapToPage(DEFAULT_PAGE, Math.toIntExact(config.duration))
        } else {
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, ACCELERATE_DECELERATE)
            config.setInterpolator(ANIM_OVERVIEW_SCALE, clampToProgress(ACCELERATE, 0f, 0.9f))
            config.setInterpolator(ANIM_OVERVIEW_FADE, DECELERATE_1_7)
        }
    }

    protected open fun getHintToNormalAnimationDuration(toState: STATE_TYPE) = -1

    protected open fun applyAllAppsToNormalConfig(config: StateAnimationConfig) {}

    protected open fun applyNormalToAllAppsAnimConfig(config: StateAnimationConfig) {}

    override fun prepareForAtomicAnimation(
        fromState: STATE_TYPE,
        toState: STATE_TYPE,
        config: StateAnimationConfig,
    ) {
        val overview = container.getOverviewPanel<RecentsView<CONTAINER, STATE_TYPE>>()
        val isPinnedTaskbar = TaskbarFeatureEvaluator.INSTANCE.get(container).isPinned
        val isThreeButton =
            (DisplayController.getNavigationMode(container) == NavigationMode.THREE_BUTTONS)
        if ((fromState == OVERVIEW || fromState == OVERVIEW_SPLIT_SELECT) && toState == NORMAL) {
            applyOverviewToHomeAnimConfig(
                fromState,
                config,
                overview,
                isPinnedTaskbar,
                isThreeButton,
            )
        } else if (
            (fromState == NORMAL ||
                fromState == HINT_STATE ||
                fromState == HINT_STATE_TWO_BUTTON) && toState == OVERVIEW
        ) {
            if (DisplayController.getNavigationMode(container).hasGestures) {
                config.setInterpolator(
                    ANIM_WORKSPACE_SCALE,
                    if (fromState == NORMAL) ACCELERATE else OVERSHOOT_1_2,
                )
                config.setInterpolator(ANIM_WORKSPACE_TRANSLATE, ACCELERATE)

                // Scrolling in tasks, so show straight away
                config.setInterpolator(
                    ANIM_OVERVIEW_FADE,
                    if (overview.hasTaskViews()) INSTANT else OVERSHOOT_1_2,
                )
            } else {
                config.setInterpolator(ANIM_WORKSPACE_SCALE, OVERSHOOT_1_2)
                config.setInterpolator(ANIM_OVERVIEW_FADE, OVERSHOOT_1_2)

                // Scale up the recents, if it is not coming from the side
                if (overview.visibility != View.VISIBLE || overview.contentAlpha == 0f) {
                    RecentsView.RECENTS_SCALE_PROPERTY[overview] = RECENTS_PREPARE_SCALE
                }
            }
            config.setInterpolator(ANIM_WORKSPACE_FADE, OVERSHOOT_1_2)
            config.setInterpolator(ANIM_ALL_APPS_FADE, OVERSHOOT_1_2)
            config.setInterpolator(ANIM_OVERVIEW_SCALE, OVERSHOOT_1_2)
            config.setInterpolator(ANIM_DEPTH, OVERSHOOT_1_2)
            config.setInterpolator(ANIM_SCRIM_FADE) { t: Float ->
                OVERSHOOT_1_2.getInterpolation(t).coerceAtMost(1f)
            }
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, OVERSHOOT_1_2)
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, OVERSHOOT_1_2)
        } else if (fromState == HINT_STATE && toState == NORMAL) {
            config.setInterpolator(ANIM_DEPTH, DECELERATE_3)
            config.duration =
                max(config.duration, getHintToNormalAnimationDuration(toState).toLong())
        } else if (fromState == ALL_APPS && toState == NORMAL) {
            applyAllAppsToNormalConfig(config)
        } else if (fromState == NORMAL && toState == ALL_APPS) {
            applyNormalToAllAppsAnimConfig(config)
        } else if (fromState == OVERVIEW && toState == OVERVIEW_SPLIT_SELECT) {
            val timings =
                if (container.deviceProfile.deviceProperties.isTablet)
                    SplitAnimationTimings.TABLET_OVERVIEW_TO_SPLIT
                else SplitAnimationTimings.PHONE_OVERVIEW_TO_SPLIT
            config.setInterpolator(
                ANIM_OVERVIEW_ACTIONS_FADE,
                clampToProgress(
                    LINEAR,
                    timings.actionsFadeStartOffset,
                    timings.actionsFadeEndOffset,
                ),
            )
        } else if (
            (fromState == NORMAL || fromState == ALL_APPS) && toState == OVERVIEW_SPLIT_SELECT
        ) {
            // Splitting from Home is currently only available on tablets
            val timings = SplitAnimationTimings.TABLET_HOME_TO_SPLIT
            config.setInterpolator(
                ANIM_SCRIM_FADE,
                clampToProgress(
                    LINEAR,
                    timings.scrimFadeInStartOffset,
                    timings.scrimFadeInEndOffset,
                ),
            )
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, OVERSHOOT_0_75)
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, OVERSHOOT_0_75)
        }
    }

    companion object {
        const val INDEX_RECENTS_FADE_ANIM = NEXT_INDEX + 0
        const val INDEX_RECENTS_TRANSLATE_X_ANIM = NEXT_INDEX + 1
        const val INDEX_RECENTS_ATTACHED_ALPHA_ANIM = NEXT_INDEX + 2

        private const val MY_ANIM_COUNT = 3

        // Scale recents takes before animating in
        private const val RECENTS_PREPARE_SCALE = 1.33f

        // Constants to specify how to scroll RecentsView to the default page if it's not already
        // there.
        private const val DEFAULT_PAGE = 0
        private const val PER_PAGE_SCROLL_DURATION = 150
        private const val MAX_PAGE_SCROLL_DURATION = 750
    }
}
