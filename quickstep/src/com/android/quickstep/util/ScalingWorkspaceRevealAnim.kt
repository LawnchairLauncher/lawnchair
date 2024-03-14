/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.view.View
import com.android.app.animation.Interpolators
import com.android.app.animation.Interpolators.EMPHASIZED
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAnimUtils.HOTSEAT_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_WORKSPACE_STATE
import com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherState
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.anim.PropertySetter
import com.android.launcher3.states.StateAnimationConfig
import com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER
import com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW
import com.android.launcher3.states.StateAnimationConfig.SKIP_SCRIM
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.quickstep.views.RecentsView

/**
 * Creates an animation where the workspace and hotseat fade in while revealing from the center of
 * the screen outwards radially. This is used in conjunction with the swipe up to home animation.
 */
class ScalingWorkspaceRevealAnim(launcher: Launcher) {
    companion object {
        private const val FADE_DURATION_MS = 200L
        private const val SCALE_DURATION_MS = 1000L
        private const val MAX_ALPHA = 1f
        private const val MIN_ALPHA = 0f
        private const val MAX_SIZE = 1f
        private const val MIN_SIZE = 0.85f
    }

    private val animation = PendingAnimation(SCALE_DURATION_MS)

    init {
        // Make sure the starting state is right for the animation.
        val config = StateAnimationConfig()
        config.animFlags = SKIP_OVERVIEW.or(SKIP_DEPTH_CONTROLLER).or(SKIP_SCRIM)
        config.duration = 0
        launcher.stateManager
            .createAtomicAnimation(LauncherState.BACKGROUND_APP, LauncherState.NORMAL, config)
            .start()
        launcher
            .getOverviewPanel<RecentsView<QuickstepLauncher, LauncherState>>()
            .forceFinishScroller()
        launcher.workspace.stateTransitionAnimation.setScrim(
            PropertySetter.NO_ANIM_PROPERTY_SETTER,
            LauncherState.BACKGROUND_APP,
            config
        )

        val workspace = launcher.workspace
        val hotseat = launcher.hotseat

        // Scale the Workspace and Hotseat around the same pivot.
        animation.addFloat(
            workspace,
            WORKSPACE_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE],
            MIN_SIZE,
            MAX_SIZE,
            EMPHASIZED,
        )
        workspace.setPivotToScaleWithSelf(hotseat)
        animation.addFloat(
            hotseat,
            HOTSEAT_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE],
            MIN_SIZE,
            MAX_SIZE,
            EMPHASIZED,
        )

        // Fade in quickly at the beginning of the animation, so the content doesn't look like it's
        // popping into existence out of nowhere.
        val fadeClamp = FADE_DURATION_MS.toFloat() / SCALE_DURATION_MS
        workspace.alpha = MIN_ALPHA
        animation.setViewAlpha(
            workspace,
            MAX_ALPHA,
            Interpolators.clampToProgress(LINEAR, 0f, fadeClamp)
        )
        hotseat.alpha = MIN_ALPHA
        animation.setViewAlpha(
            hotseat,
            MAX_ALPHA,
            Interpolators.clampToProgress(LINEAR, 0f, fadeClamp)
        )

        // Match the Wallpaper animation to the rest of the content.
        val depthController = (launcher as? QuickstepLauncher)?.depthController
        val depthConfig = StateAnimationConfig()
        depthConfig.setInterpolator(StateAnimationConfig.ANIM_DEPTH, EMPHASIZED)
        depthController?.setStateWithAnimation(LauncherState.NORMAL, depthConfig, animation)

        // Needed to avoid text artefacts during the scale animation.
        workspace.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        hotseat.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        animation.addListener(
            AnimatorListeners.forEndCallback(
                Runnable {
                    workspace.setLayerType(View.LAYER_TYPE_NONE, null)
                    hotseat.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            )
        )
    }

    fun start() {
        animation.buildAnim().start()
    }
}
