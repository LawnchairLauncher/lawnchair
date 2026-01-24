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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceControl
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.transform
import androidx.core.view.isVisible
import com.android.app.animation.Animations
import com.android.app.animation.Interpolators
import com.android.app.animation.Interpolators.EMPHASIZED
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.Flags
import com.android.launcher3.LauncherAnimUtils.HOTSEAT_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_WORKSPACE_STATE
import com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA
import com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.anim.PropertySetter
import com.android.launcher3.states.StateAnimationConfig
import com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER
import com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW
import com.android.launcher3.states.StateAnimationConfig.SKIP_SCRIM
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.quickstep.views.RecentsView

const val TAG = "ScalingWorkspaceRevealAnim"

/**
 * Creates an animation where the workspace and hotseat fade in while revealing from the center of
 * the screen outwards radially. This is used in conjunction with the swipe up to home animation.
 */
class ScalingWorkspaceRevealAnim(
    private val launcher: QuickstepLauncher,
    siblingAnimation: RectFSpringAnim?,
    windowTargetRect: RectF?,
    playAlphaReveal: Boolean = true,
) {
    companion object {
        private const val FADE_DURATION_MS = 200L
        private const val SCALE_DURATION_MS = 1000L
        private const val MAX_ALPHA = 1f
        private const val MIN_ALPHA = 0f
        internal const val MAX_SIZE = 1f
        internal const val MIN_SIZE = 0.85f

        /**
         * Custom interpolator for both the home and wallpaper scaling. Necessary because EMPHASIZED
         * is too aggressive, but EMPHASIZED_DECELERATE is too soft.
         */
        @JvmField
        val SCALE_INTERPOLATOR =
            PathInterpolator(
                Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(0.045f, 0.0356f, 0.0975f, 0.2055f, 0.15f, 0.3952f)
                    cubicTo(0.235f, 0.6855f, 0.235f, 1f, 1f, 1f)
                }
            )

        val BLUR_INTERPOLATOR = Interpolators.clampToProgress(EMPHASIZED, 0f, 0.666f)
    }

    private val animation = PendingAnimation(SCALE_DURATION_MS)
    private var blurLayer: SurfaceControl? = null
    private var surfaceTransactionApplier: SurfaceTransactionApplier =
        SurfaceTransactionApplier(launcher.dragLayer)

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
            setupConfig,
        )
        addBlurLayer()

        val workspace = launcher.workspace
        val hotseat = launcher.hotseat

        // Interrupt the current animation, if any.
        Animations.cancelOngoingAnimation(workspace)
        Animations.cancelOngoingAnimation(hotseat)

        val fromSize =
            if (workspace.scaleX != MAX_SIZE) {
                workspace.scaleX
            } else {
                MIN_SIZE
            }

        // Scale the Workspace and Hotseat around the same pivot.
        workspace.setPivotToScaleWithSelf(hotseat)
        animation.addFloat(
            workspace,
            WORKSPACE_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE],
            fromSize,
            MAX_SIZE,
            SCALE_INTERPOLATOR,
        )
        animation.addFloat(
            hotseat,
            HOTSEAT_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE],
            fromSize,
            MAX_SIZE,
            SCALE_INTERPOLATOR,
        )

        if (playAlphaReveal) {
            // Fade in quickly at the beginning of the animation, so the content doesn't look like
            // it's popping into existence out of nowhere.
            val fadeClamp = FADE_DURATION_MS.toFloat() / SCALE_DURATION_MS
            workspace.alpha = MIN_ALPHA
            animation.setFloat(
                workspace,
                VIEW_ALPHA,
                MAX_ALPHA,
                Interpolators.clampToProgress(LINEAR, 0f, fadeClamp),
            )
            hotseat.alpha = MIN_ALPHA
            // This needs to use setViewAlpha instead of setFloat (like workspace).
            // This is because hotseat visibility can also be changed based off of alpha in
            // WorkspaceRevealAnim which also calls setViewAlpha.
            // b/428257480 Ideally we should be settings MultiValueAlpha with 2 channels instead.
            animation.setViewAlpha(
                hotseat,
                MAX_ALPHA,
                Interpolators.clampToProgress(LINEAR, 0f, fadeClamp),
            )
        }

        val transitionConfig = StateAnimationConfig()
        transitionConfig.duration = SCALE_DURATION_MS

        // Match the Wallpaper depth to the rest of the content.
        val depthController = (launcher as? QuickstepLauncher)?.depthController
        transitionConfig.setInterpolator(StateAnimationConfig.ANIM_DEPTH, SCALE_INTERPOLATOR)
        depthController?.pauseBlursOnWindows(true) // Blurring is handled by the scrim layer.
        depthController?.stateDepth?.value = LauncherState.BACKGROUND_APP.getDepth(launcher)
        depthController?.setStateWithAnimation(LauncherState.NORMAL, transitionConfig, animation)

        // Add a blur animation to the scrim layer.
        var maxBlurRadius =
            launcher.resources.getDimensionPixelSize(
                if (Flags.allAppsBlur() || Flags.enableOverviewBackgroundWallpaperBlur()) {
                    R.dimen.max_depth_blur_radius_enhanced
                } else {
                    R.integer.max_depth_blur_radius
                }
            )
        val blurAnimator = ValueAnimator.ofFloat(1f, 0f)
        blurAnimator.setInterpolator(BLUR_INTERPOLATOR)
        blurAnimator.addUpdateListener {
            applyBlur(maxBlurRadius * blurAnimator.animatedValue as Float)
        }
        animation.add(blurAnimator)

        // Make sure that the contrast scrim animates correctly (alongside the blur) if needed.
        transitionConfig.setInterpolator(StateAnimationConfig.ANIM_SCRIM_FADE, BLUR_INTERPOLATOR)
        launcher.workspace.stateTransitionAnimation.setScrim(
            animation,
            LauncherState.NORMAL,
            transitionConfig,
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
                        transformed.centerY(),
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
            object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    super.onAnimationCancel(animation)
                    Log.d(TAG, "onAnimationCancel")
                }

                override fun onAnimationPause(animation: Animator) {
                    super.onAnimationPause(animation)
                    Log.d(TAG, "onAnimationPause")
                }
            }
        )

        animation.addListener(
            AnimatorListeners.forEndCallback(
                Runnable {
                    Log.d(TAG, "onAnimationEnd, workspace and hotseat are visible")
                    // Ensure that the workspace and the hotseat are visible at the end
                    // of the animation regardless of what happens with this animation
                    // itself.
                    workspace.alpha = MAX_ALPHA
                    hotseat.alpha = MAX_ALPHA
                    if (!hotseat.isVisible || !workspace.isVisible) {
                        Log.e(
                            TAG,
                            "Unexpected invisibility after animation end:" +
                                " workspace.isVisible=${workspace.isVisible}" +
                                ", workspace.alpha=${workspace.alpha}" +
                                ", hotseat.isVisible=${hotseat.isVisible}" +
                                ", hotseat.alpha=${hotseat.alpha}",
                            Exception(),
                        )
                    }

                    workspace.setLayerType(View.LAYER_TYPE_NONE, null)
                    hotseat.setLayerType(View.LAYER_TYPE_NONE, null)

                    // Reset the cached animations.
                    Animations.setOngoingAnimation(workspace, animation = null)
                    Animations.setOngoingAnimation(hotseat, animation = null)
                    removeBlurLayer()
                    depthController?.pauseBlursOnWindows(false)
                }
            )
        )
    }

    fun getAnimators(): AnimatorSet {
        return animation.buildAnim()
    }

    fun start() {
        val animators = getAnimators()
        // Make sure to cache the current animation, so it can be properly interrupted.
        // TODO(b/367591368): ideally these animations would be refactored to be controlled
        //  centrally so each instances doesn't need to care about this coordination.
        Animations.setOngoingAnimation(launcher.workspace, animators)
        Animations.setOngoingAnimation(launcher.hotseat, animators)
        launcher.stateManager.setCurrentAnimation(animators, LauncherState.NORMAL)
        animators.start()
    }

    private fun addBlurLayer() {
        val parent = launcher.dragLayer.viewRootImpl?.surfaceControl ?: return
        if (!parent.isValid) {
            Log.e(TAG, "Parent surface is not ready at the moment. Can't apply blur.")
            return
        }
        val blurLayer =
            SurfaceControl.Builder()
                .setName("Home to launcher blur layer")
                .setCallsite("ScalingWorkspaceRevealAnim")
                .setParent(parent)
                .setOpaque(false)
                .setHidden(false)
                .build()

        // Schedule the initial setup of the blur layer.
        val setupTransaction = SurfaceTransaction()
        setupTransaction.forSurface(blurLayer).setAlpha(0f).setShow()
        surfaceTransactionApplier.scheduleApply(setupTransaction)

        this.blurLayer = blurLayer
    }

    private fun removeBlurLayer() {
        blurLayer?.let {
            if (it.isValid) {
                // Schedule the removal of the blur layer.
                val removalTransaction = SurfaceTransaction()
                removalTransaction.forSurface(it).setRemove()
                surfaceTransactionApplier.scheduleApply(removalTransaction)
            }
        }
        blurLayer = null
    }

    private fun applyBlur(blurRadius: Float) {
        blurLayer?.let {
            if (it.isValid) {
                // Schedule the blur update.
                val blurUpdateTransaction = SurfaceTransaction()
                blurUpdateTransaction.forSurface(it).setBackgroundBlurRadius(blurRadius.toInt())
                surfaceTransactionApplier.scheduleApply(blurUpdateTransaction)
            }
        }
    }
}
