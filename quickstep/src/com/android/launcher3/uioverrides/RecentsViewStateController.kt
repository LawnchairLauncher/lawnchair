/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.launcher3.uioverrides

import com.android.app.animation.Interpolators.ACCELERATE_DECELERATE
import com.android.app.animation.Interpolators.AGGRESSIVE_EASE_IN_OUT
import com.android.app.animation.Interpolators.FINAL_FRAME
import com.android.app.animation.Interpolators.INSTANT
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.Flags.enableDesktopExplodedView
import com.android.launcher3.Flags.enableGridOnlyOverview
import com.android.launcher3.Flags.enableLargeDesktopWindowingTile
import com.android.launcher3.LauncherState
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.anim.AnimatorListeners.forSuccessCallback
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.anim.PropertySetter
import com.android.launcher3.statemanager.StateManager.StateHandler
import com.android.launcher3.states.StateAnimationConfig
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_ACTIONS_FADE
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_MODAL
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_X
import com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y
import com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW
import com.android.quickstep.util.AnimUtils
import com.android.quickstep.views.AddDesktopButton
import com.android.quickstep.views.ClearAllButton
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET
import com.android.quickstep.views.RecentsView.CONTENT_ALPHA
import com.android.quickstep.views.RecentsView.DESKTOP_CAROUSEL_DETACH_PROGRESS
import com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS
import com.android.quickstep.views.RecentsView.RECENTS_GRID_PROGRESS
import com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY
import com.android.quickstep.views.RecentsView.TASK_MODALNESS
import com.android.quickstep.views.RecentsView.TASK_PRIMARY_SPLIT_TRANSLATION
import com.android.quickstep.views.RecentsView.TASK_SECONDARY_SPLIT_TRANSLATION
import com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION
import com.android.quickstep.views.RecentsView.TASK_THUMBNAIL_SPLASH_ALPHA
import com.android.quickstep.views.RecentsViewUtils.Companion.DESK_EXPLODE_PROGRESS
import com.android.quickstep.views.TaskView.Companion.FLAG_UPDATE_ALL

/**
 * State handler for handling UI changes for [com.android.quickstep.views.LauncherRecentsView]. In
 * addition to managing the basic view properties, this class also manages changes in the task
 * visuals.
 */
class RecentsViewStateController(private val launcher: QuickstepLauncher) :
    StateHandler<LauncherState> {
    private val recentsView: RecentsView<*, *> = launcher.getOverviewPanel()

    override fun setState(state: LauncherState) {
        val scaleAndOffset = state.getOverviewScaleAndOffset(launcher)
        RECENTS_SCALE_PROPERTY.set(recentsView, scaleAndOffset[0])
        ADJACENT_PAGE_HORIZONTAL_OFFSET.set(recentsView, scaleAndOffset[1])
        TASK_SECONDARY_TRANSLATION.set(recentsView, 0f)

        CONTENT_ALPHA.set(recentsView, if (state.isRecentsViewVisible) 1f else 0f)
        TASK_MODALNESS.set(recentsView, state.overviewModalness)
        RECENTS_GRID_PROGRESS.set(
            recentsView,
            if (state.displayOverviewTasksAsGrid(launcher.deviceProfile)) 1f else 0f,
        )
        if (enableDesktopExplodedView()) {
            DESK_EXPLODE_PROGRESS.set(recentsView, if (state.showExplodedDesktopView()) 1f else 0f)
        }

        TASK_THUMBNAIL_SPLASH_ALPHA.set(
            recentsView,
            if (state.showTaskThumbnailSplash()) 1f else 0f,
        )
        if (enableLargeDesktopWindowingTile()) {
            DESKTOP_CAROUSEL_DETACH_PROGRESS.set(
                recentsView,
                if (state.detachDesktopCarousel()) 1f else 0f,
            )
        }

        if (state.isRecentsViewVisible) {
            recentsView.updateEmptyMessage()
        } else {
            recentsView.resetTaskVisuals()
        }
        setAlphas(PropertySetter.NO_ANIM_PROPERTY_SETTER, StateAnimationConfig(), state)
        recentsView.setFullscreenProgress(state.overviewFullscreenProgress)
        // In Overview, we may be layering app surfaces behind Launcher, so we need to notify
        // DepthController to prevent optimizations which might occlude the layers behind
        launcher.depthController.setHasContentBehindLauncher(state.isRecentsViewVisible)

        val builder = PendingAnimation(state.getTransitionDuration(launcher, true).toLong())
        handleSplitSelectionState(state, builder, animate = false)
    }

    override fun setStateWithAnimation(
        toState: LauncherState,
        config: StateAnimationConfig,
        builder: PendingAnimation,
    ) {
        if (config.hasAnimationFlag(SKIP_OVERVIEW)) return

        val scaleAndOffset = toState.getOverviewScaleAndOffset(launcher)
        builder.setFloat(
            recentsView,
            RECENTS_SCALE_PROPERTY,
            scaleAndOffset[0],
            config.getInterpolator(ANIM_OVERVIEW_SCALE, LINEAR),
        )
        builder.setFloat(
            recentsView,
            ADJACENT_PAGE_HORIZONTAL_OFFSET,
            scaleAndOffset[1],
            config.getInterpolator(ANIM_OVERVIEW_TRANSLATE_X, LINEAR),
        )
        builder.setFloat(
            recentsView,
            TASK_SECONDARY_TRANSLATION,
            0f,
            config.getInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, LINEAR),
        )

        builder.setFloat(
            recentsView,
            CONTENT_ALPHA,
            if (toState.isRecentsViewVisible) 1f else 0f,
            config.getInterpolator(ANIM_OVERVIEW_FADE, AGGRESSIVE_EASE_IN_OUT),
        )

        builder.setFloat(
            recentsView,
            TASK_MODALNESS,
            toState.overviewModalness,
            config.getInterpolator(
                ANIM_OVERVIEW_MODAL,
                if (enableGridOnlyOverview() && !toState.isRecentsViewVisible) FINAL_FRAME
                else LINEAR,
            ),
        )

        val fromState = launcher.stateManager.state
        builder.setFloat(
            recentsView,
            TASK_THUMBNAIL_SPLASH_ALPHA,
            if (toState.showTaskThumbnailSplash()) 1f else 0f,
            getOverviewInterpolator(fromState, toState),
        )

        builder.setFloat(
            recentsView,
            RECENTS_GRID_PROGRESS,
            if (toState.displayOverviewTasksAsGrid(launcher.deviceProfile)) 1f else 0f,
            getOverviewInterpolator(fromState, toState),
        )

        if (enableDesktopExplodedView()) {
            builder.setFloat(
                recentsView,
                DESK_EXPLODE_PROGRESS,
                if (toState.showExplodedDesktopView()) 1f else 0f,
                getOverviewInterpolator(fromState, toState),
            )
        }

        if (enableLargeDesktopWindowingTile()) {
            builder.setFloat(
                recentsView,
                DESKTOP_CAROUSEL_DETACH_PROGRESS,
                if (toState.detachDesktopCarousel()) 1f else 0f,
                getOverviewInterpolator(fromState, toState),
            )
        }

        if (toState.isRecentsViewVisible) {
            // While animating into recents, update the visible task data as needed
            builder.addOnFrameCallback { recentsView.loadVisibleTaskData(FLAG_UPDATE_ALL) }
            recentsView.updateEmptyMessage()
        } else {
            builder.addListener(forSuccessCallback { recentsView.resetTaskVisuals() })
        }
        // In Overview, we may be layering app surfaces behind Launcher, so we need to notify
        // DepthController to prevent optimizations which might occlude the layers behind
        builder.addListener(
            forSuccessCallback {
                launcher.depthController.setHasContentBehindLauncher(toState.isRecentsViewVisible)
            }
        )

        handleSplitSelectionState(toState, builder, animate = true)

        setAlphas(builder, config, toState)
        builder.setFloat(
            recentsView,
            FULLSCREEN_PROGRESS,
            toState.overviewFullscreenProgress,
            LINEAR,
        )

        builder.addEndListener { success: Boolean ->
            if (!success && !toState.isRecentsViewVisible) {
                recentsView.reset()
            }
        }
    }

    /**
     * Create or dismiss split screen select animations.
     *
     * @param builder if null then this will run the split select animations right away, otherwise
     *   will add animations to builder.
     */
    private fun handleSplitSelectionState(
        toState: LauncherState,
        builder: PendingAnimation,
        animate: Boolean,
    ) {
        val goingToOverviewFromWorkspaceContextual =
            toState == LauncherState.OVERVIEW && launcher.isSplitSelectionActive
        if (
            toState != LauncherState.OVERVIEW_SPLIT_SELECT &&
                !goingToOverviewFromWorkspaceContextual
        ) {
            // Not going to split
            return
        }

        // Create transition animations to split select
        val orientationHandler = recentsView.pagedOrientationHandler
        val taskViewsFloat =
            orientationHandler.getSplitSelectTaskOffset(
                TASK_PRIMARY_SPLIT_TRANSLATION,
                TASK_SECONDARY_SPLIT_TRANSLATION,
                launcher.deviceProfile,
            )

        val timings = AnimUtils.getDeviceOverviewToSplitTimings(launcher.deviceProfile.isTablet)
        if (!goingToOverviewFromWorkspaceContextual) {
            // This animation is already done for the contextual case, don't redo it
            recentsView.createSplitSelectInitAnimation(
                builder,
                toState.getTransitionDuration(launcher, true),
            )
        }
        // Shift tasks vertically downward to get out of placeholder view
        builder.setFloat(
            recentsView,
            taskViewsFloat.first,
            toState.getSplitSelectTranslation(launcher),
            timings.gridSlidePrimaryInterpolator,
        )
        // Zero out horizontal translation
        builder.setFloat(
            recentsView,
            taskViewsFloat.second,
            0f,
            timings.gridSlideSecondaryInterpolator,
        )

        recentsView.handleDesktopTaskInSplitSelectState(
            builder,
            timings.desktopTaskFadeInterpolator,
        )

        if (!animate) {
            builder.buildAnim().apply {
                start()
                end()
            }
        }
    }

    private fun setAlphas(
        propertySetter: PropertySetter,
        config: StateAnimationConfig,
        state: LauncherState,
    ) {
        val clearAllButtonAlpha =
            if (state.areElementsVisible(launcher, LauncherState.CLEAR_ALL_BUTTON)) 1f else 0f
        propertySetter.setFloat(
            recentsView.clearAllButton,
            ClearAllButton.VISIBILITY_ALPHA,
            clearAllButtonAlpha,
            LINEAR,
        )
        val overviewButtonAlpha =
            if (state.areElementsVisible(launcher, LauncherState.OVERVIEW_ACTIONS)) 1f else 0f
        propertySetter.setFloat(
            launcher.actionsView.visibilityAlpha,
            AnimatedFloat.VALUE,
            overviewButtonAlpha,
            config.getInterpolator(ANIM_OVERVIEW_ACTIONS_FADE, LINEAR),
        )
        recentsView.addDeskButton?.let {
            propertySetter.setFloat(
                it,
                AddDesktopButton.VISIBILITY_ALPHA,
                if (state.areElementsVisible(launcher, LauncherState.ADD_DESK_BUTTON)) 1f else 0f,
                LINEAR,
            )
        }
    }

    private fun getOverviewInterpolator(fromState: LauncherState, toState: LauncherState) =
        when {
            fromState == LauncherState.QUICK_SWITCH_FROM_HOME -> ACCELERATE_DECELERATE
            toState.isRecentsViewVisible -> INSTANT
            else -> FINAL_FRAME
        }
}
