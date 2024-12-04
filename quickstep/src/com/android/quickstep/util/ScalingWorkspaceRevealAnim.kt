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

import android.animation.AnimatorSet
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.transform
import com.android.app.animation.Interpolators
import com.android.app.animation.Interpolators.LINEAR
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
class ScalingWorkspaceRevealAnim(
    launcher: QuickstepLauncher,
    siblingAnimation: RectFSpringAnim?,
    windowTargetRect: RectF?
) {
    companion object {
        private const val FADE_DURATION_MS = 200L
        private const val SCALE_DURATION_MS = 1000L
        private const val MAX_ALPHA = 1f
        private const val MIN_ALPHA = 0f
        private const val MAX_SIZE = 1f
        private const val MIN_SIZE = 0.85f

        /**
         * Custom interpolator for both the home and wallpaper scaling. Necessary because EMPHASIZED
         * is too aggressive, but EMPHASIZED_DECELERATE is too soft.
         */
        private val SCALE_INTERPOLATOR =
            PathInterpolator(
                Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(0.045f, 0.0356f, 0.0975f, 0.2055f, 0.15f, 0.3952f)
                    cubicTo(0.235f, 0.6855f, 0.235f, 1f, 1f, 1f)
                }
            )
    }

    private val animation = PendingAnimation(SCALE_DURATION_MS)

    init {
        // Make sure the starting state is right for the animation.
        val setupConfig = StateAnimationConfig()
        setupConfig.animFlags = SKIP_OVERVIEW.or(SKIP_DEPTH_CONTROLLER).or(SKIP_SCRIM)
        setupConfig.duration = 0
        launcher.stateManager
            .createAtomicAnimation(LauncherState.BACKGROUND_APP, LauncherState.NORMAL, setupConfig)
            .start()
        launcher
            .getOverviewPanel<RecentsView<QuickstepLauncher, LauncherState>>()
            .forceFinishScroller()
        launcher.workspace.stateTransitionAnimation.setScrim(
            PropertySetter.NO_ANIM_PROPERTY_SETTER,
            LauncherState.BACKGROUND_APP,
            setupConfig
        )

        val workspace = launcher.workspace
        val hotseat = launcher.hotseat

        // Scale the Workspace and Hotseat around the same pivot.
        workspace.setPivotToScaleWithSelf(hotseat)
        animation.addFloat(
            workspace,
            WORKSPACE_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE],
            MIN_SIZE,
            MAX_SIZE,
            SCALE_INTERPOLATOR,
        )
        animation.addFloat(
            hotseat,
            HOTSEAT_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE],
            MIN_SIZE,
            MAX_SIZE,
            SCALE_INTERPOLATOR,
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

        val transitionConfig = StateAnimationConfig()

        // Match the Wallpaper animation to the rest of the content.
        val depthController = (launcher as? QuickstepLauncher)?.depthController
        transitionConfig.setInterpolator(StateAnimationConfig.ANIM_DEPTH, SCALE_INTERPOLATOR)
        depthController?.setStateWithAnimation(LauncherState.NORMAL, transitionConfig, animation)

        // Make sure that the contrast scrim animates correctly if needed.
        transitionConfig.setInterpolator(StateAnimationConfig.ANIM_SCRIM_FADE, SCALE_INTERPOLATOR)
        launcher.workspace.stateTransitionAnimation.setScrim(
            animation,
            LauncherState.NORMAL,
            transitionConfig
        )

        // To avoid awkward jumps in icon position, we want the sibling animation to always be
        // targeting the current position. Since we can't easily access this, instead we calculate
        // it using the animation of the whole of home.
        // We start by caching the final target position, as this is the base for the transforms.
        val originalTarget = RectF(windowTargetRect)
        animation.addOnFrameListener {
            val transformed = RectF(originalTarget)

            // First we scale down using the same pivot as the workspace scale, so we find the
            // correct position AND size.
            transformed.transform(
                Matrix().apply {
                    setScale(workspace.scaleX, workspace.scaleY, workspace.pivotX, workspace.pivotY)
                }
            )
            // Then we scale back up around the center of the current position. This is because the
            // icon animation behaves poorly if it is given a target that is smaller than the size
            // of the icon.
            transformed.transform(
                Matrix().apply {
                    setScale(
                        1 / workspace.scaleX,
                        1 / workspace.scaleY,
                        transformed.centerX(),
                        transformed.centerY()
                    )
                }
            )

            if (transformed != windowTargetRect) {
                windowTargetRect?.set(transformed)
                siblingAnimation?.onTargetPositionChanged()
            }
        }

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

    fun getAnimators(): AnimatorSet {
        return animation.buildAnim()
    }

    fun start() {
        getAnimators().start()
    }
}
