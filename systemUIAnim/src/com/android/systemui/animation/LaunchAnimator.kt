/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.MathUtils
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import com.android.app.animation.Interpolators.LINEAR
import kotlin.math.roundToInt

private const val TAG = "LaunchAnimator"

/** A base class to animate a window launch (activity or dialog) from a view . */
class LaunchAnimator(private val timings: Timings, private val interpolators: Interpolators) {
    companion object {
        internal const val DEBUG = false
        private val SRC_MODE = PorterDuffXfermode(PorterDuff.Mode.SRC)

        /**
         * Given the [linearProgress] of a launch animation, return the linear progress of the
         * sub-animation starting [delay] ms after the launch animation and that lasts [duration].
         */
        @JvmStatic
        fun getProgress(
            timings: Timings,
            linearProgress: Float,
            delay: Long,
            duration: Long
        ): Float {
            return MathUtils.constrain(
                (linearProgress * timings.totalDuration - delay) / duration,
                0.0f,
                1.0f
            )
        }
    }

    private val launchContainerLocation = IntArray(2)
    private val cornerRadii = FloatArray(8)

    /**
     * A controller that takes care of applying the animation to an expanding view.
     *
     * Note that all callbacks (onXXX methods) are all called on the main thread.
     */
    interface Controller {
        /**
         * The container in which the view that started the animation will be animating together
         * with the opening window.
         *
         * This will be used to:
         * - Get the associated [Context].
         * - Compute whether we are expanding fully above the launch container.
         * - Get to overlay to which we initially put the window background layer, until the opening
         *   window is made visible (see [openingWindowSyncView]).
         *
         * This container can be changed to force this [Controller] to animate the expanding view
         * inside a different location, for instance to ensure correct layering during the
         * animation.
         */
        var launchContainer: ViewGroup

        /**
         * The [View] with which the opening app window should be synchronized with once it starts
         * to be visible.
         *
         * We will also move the window background layer to this view's overlay once the opening
         * window is visible.
         *
         * If null, this will default to [launchContainer].
         */
        val openingWindowSyncView: View?
            get() = null

        /**
         * Return the [State] of the view that will be animated. We will animate from this state to
         * the final window state.
         *
         * Note: This state will be mutated and passed to [onLaunchAnimationProgress] during the
         * animation.
         */
        fun createAnimatorState(): State

        /**
         * The animation started. This is typically used to initialize any additional resource
         * needed for the animation. [isExpandingFullyAbove] will be true if the window is expanding
         * fully above the [launchContainer].
         */
        fun onLaunchAnimationStart(isExpandingFullyAbove: Boolean) {}

        /** The animation made progress and the expandable view [state] should be updated. */
        fun onLaunchAnimationProgress(state: State, progress: Float, linearProgress: Float) {}

        /**
         * The animation ended. This will be called *if and only if* [onLaunchAnimationStart] was
         * called previously. This is typically used to clean up the resources initialized when the
         * animation was started.
         */
        fun onLaunchAnimationEnd(isExpandingFullyAbove: Boolean) {}
    }

    /** The state of an expandable view during a [LaunchAnimator] animation. */
    open class State(
        /** The position of the view in screen space coordinates. */
        var top: Int = 0,
        var bottom: Int = 0,
        var left: Int = 0,
        var right: Int = 0,
        var topCornerRadius: Float = 0f,
        var bottomCornerRadius: Float = 0f
    ) {
        private val startTop = top

        val width: Int
            get() = right - left

        val height: Int
            get() = bottom - top

        open val topChange: Int
            get() = top - startTop

        val centerX: Float
            get() = left + width / 2f

        val centerY: Float
            get() = top + height / 2f

        /** Whether the expanding view should be visible or hidden. */
        var visible: Boolean = true
    }

    interface Animation {
        /** Cancel the animation. */
        fun cancel()
    }

    /** The timings (durations and delays) used by this animator. */
    data class Timings(
        /** The total duration of the animation. */
        val totalDuration: Long,

        /** The time to wait before fading out the expanding content. */
        val contentBeforeFadeOutDelay: Long,

        /** The duration of the expanding content fade out. */
        val contentBeforeFadeOutDuration: Long,

        /**
         * The time to wait before fading in the expanded content (usually an activity or dialog
         * window).
         */
        val contentAfterFadeInDelay: Long,

        /** The duration of the expanded content fade in. */
        val contentAfterFadeInDuration: Long
    )

    /** The interpolators used by this animator. */
    data class Interpolators(
        /** The interpolator used for the Y position, width, height and corner radius. */
        val positionInterpolator: Interpolator,

        /**
         * The interpolator used for the X position. This can be different than
         * [positionInterpolator] to create an arc-path during the animation.
         */
        val positionXInterpolator: Interpolator = positionInterpolator,

        /** The interpolator used when fading out the expanding content. */
        val contentBeforeFadeOutInterpolator: Interpolator,

        /** The interpolator used when fading in the expanded content. */
        val contentAfterFadeInInterpolator: Interpolator
    )

    /**
     * Start a launch animation controlled by [controller] towards [endState]. An intermediary layer
     * with [windowBackgroundColor] will fade in then (optionally) fade out above the expanding
     * view, and should be the same background color as the opening (or closing) window.
     *
     * If [fadeOutWindowBackgroundLayer] is true, then this intermediary layer will fade out during
     * the second half of the animation, and will have SRC blending mode (ultimately punching a hole
     * in the [launch container][Controller.launchContainer]) iff [drawHole] is true.
     */
    fun startAnimation(
        controller: Controller,
        endState: State,
        windowBackgroundColor: Int,
        fadeOutWindowBackgroundLayer: Boolean = true,
        drawHole: Boolean = false,
    ): Animation {
        val state = controller.createAnimatorState()

        // Start state.
        val startTop = state.top
        val startBottom = state.bottom
        val startLeft = state.left
        val startRight = state.right
        val startCenterX = (startLeft + startRight) / 2f
        val startWidth = startRight - startLeft
        val startTopCornerRadius = state.topCornerRadius
        val startBottomCornerRadius = state.bottomCornerRadius

        // End state.
        var endTop = endState.top
        var endBottom = endState.bottom
        var endLeft = endState.left
        var endRight = endState.right
        var endCenterX = (endLeft + endRight) / 2f
        var endWidth = endRight - endLeft
        val endTopCornerRadius = endState.topCornerRadius
        val endBottomCornerRadius = endState.bottomCornerRadius

        fun maybeUpdateEndState() {
            if (
                endTop != endState.top ||
                    endBottom != endState.bottom ||
                    endLeft != endState.left ||
                    endRight != endState.right
            ) {
                endTop = endState.top
                endBottom = endState.bottom
                endLeft = endState.left
                endRight = endState.right
                endCenterX = (endLeft + endRight) / 2f
                endWidth = endRight - endLeft
            }
        }

        val launchContainer = controller.launchContainer
        val isExpandingFullyAbove = isExpandingFullyAbove(launchContainer, endState)

        // We add an extra layer with the same color as the dialog/app splash screen background
        // color, which is usually the same color of the app background. We first fade in this layer
        // to hide the expanding view, then we fade it out with SRC mode to draw a hole in the
        // launch container and reveal the opening window.
        val windowBackgroundLayer =
            GradientDrawable().apply {
                setColor(windowBackgroundColor)
                alpha = 0
            }

        // Update state.
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = timings.totalDuration
        animator.interpolator = LINEAR

        // Whether we should move the [windowBackgroundLayer] into the overlay of
        // [Controller.openingWindowSyncView] once the opening app window starts to be visible.
        val openingWindowSyncView = controller.openingWindowSyncView
        val openingWindowSyncViewOverlay = openingWindowSyncView?.overlay
        val moveBackgroundLayerWhenAppIsVisible =
            openingWindowSyncView != null &&
                openingWindowSyncView.viewRootImpl != controller.launchContainer.viewRootImpl

        val launchContainerOverlay = launchContainer.overlay
        var cancelled = false
        var movedBackgroundLayer = false

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator, isReverse: Boolean) {
                    if (DEBUG) {
                        Log.d(TAG, "Animation started")
                    }
                    controller.onLaunchAnimationStart(isExpandingFullyAbove)

                    // Add the drawable to the launch container overlay. Overlays always draw
                    // drawables after views, so we know that it will be drawn above any view added
                    // by the controller.
                    launchContainerOverlay.add(windowBackgroundLayer)
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (DEBUG) {
                        Log.d(TAG, "Animation ended")
                    }
                    controller.onLaunchAnimationEnd(isExpandingFullyAbove)
                    launchContainerOverlay.remove(windowBackgroundLayer)

                    if (moveBackgroundLayerWhenAppIsVisible) {
                        openingWindowSyncViewOverlay?.remove(windowBackgroundLayer)
                    }
                }
            }
        )

        animator.addUpdateListener { animation ->
            if (cancelled) {
                // TODO(b/184121838): Cancel the animator directly instead of just skipping the
                // update.
                return@addUpdateListener
            }

            maybeUpdateEndState()

            // TODO(b/184121838): Use reverse interpolators to get the same path/arc as the non
            // reversed animation.
            val linearProgress = animation.animatedFraction
            val progress = interpolators.positionInterpolator.getInterpolation(linearProgress)
            val xProgress = interpolators.positionXInterpolator.getInterpolation(linearProgress)

            val xCenter = MathUtils.lerp(startCenterX, endCenterX, xProgress)
            val halfWidth = MathUtils.lerp(startWidth, endWidth, progress) / 2f

            state.top = MathUtils.lerp(startTop, endTop, progress).roundToInt()
            state.bottom = MathUtils.lerp(startBottom, endBottom, progress).roundToInt()
            state.left = (xCenter - halfWidth).roundToInt()
            state.right = (xCenter + halfWidth).roundToInt()

            state.topCornerRadius =
                MathUtils.lerp(startTopCornerRadius, endTopCornerRadius, progress)
            state.bottomCornerRadius =
                MathUtils.lerp(startBottomCornerRadius, endBottomCornerRadius, progress)

            // The expanding view can/should be hidden once it is completely covered by the opening
            // window.
            state.visible =
                getProgress(
                    timings,
                    linearProgress,
                    timings.contentBeforeFadeOutDelay,
                    timings.contentBeforeFadeOutDuration
                ) < 1

            if (moveBackgroundLayerWhenAppIsVisible && !state.visible && !movedBackgroundLayer) {
                // The expanding view is not visible, so the opening app is visible. If this is the
                // first frame when it happens, trigger a one-off sync and move the background layer
                // in its new container.
                movedBackgroundLayer = true

                launchContainerOverlay.remove(windowBackgroundLayer)
                openingWindowSyncViewOverlay!!.add(windowBackgroundLayer)

                ViewRootSync.synchronizeNextDraw(launchContainer, openingWindowSyncView, then = {})
            }

            val container =
                if (movedBackgroundLayer) {
                    openingWindowSyncView!!
                } else {
                    controller.launchContainer
                }

            applyStateToWindowBackgroundLayer(
                windowBackgroundLayer,
                state,
                linearProgress,
                container,
                fadeOutWindowBackgroundLayer,
                drawHole
            )
            controller.onLaunchAnimationProgress(state, progress, linearProgress)
        }

        animator.start()
        return object : Animation {
            override fun cancel() {
                cancelled = true
                animator.cancel()
            }
        }
    }

    /** Return whether we are expanding fully above the [launchContainer]. */
    internal fun isExpandingFullyAbove(launchContainer: View, endState: State): Boolean {
        launchContainer.getLocationOnScreen(launchContainerLocation)
        return endState.top <= launchContainerLocation[1] &&
            endState.bottom >= launchContainerLocation[1] + launchContainer.height &&
            endState.left <= launchContainerLocation[0] &&
            endState.right >= launchContainerLocation[0] + launchContainer.width
    }

    private fun applyStateToWindowBackgroundLayer(
        drawable: GradientDrawable,
        state: State,
        linearProgress: Float,
        launchContainer: View,
        fadeOutWindowBackgroundLayer: Boolean,
        drawHole: Boolean
    ) {
        // Update position.
        launchContainer.getLocationOnScreen(launchContainerLocation)
        drawable.setBounds(
            state.left - launchContainerLocation[0],
            state.top - launchContainerLocation[1],
            state.right - launchContainerLocation[0],
            state.bottom - launchContainerLocation[1]
        )

        // Update radius.
        cornerRadii[0] = state.topCornerRadius
        cornerRadii[1] = state.topCornerRadius
        cornerRadii[2] = state.topCornerRadius
        cornerRadii[3] = state.topCornerRadius
        cornerRadii[4] = state.bottomCornerRadius
        cornerRadii[5] = state.bottomCornerRadius
        cornerRadii[6] = state.bottomCornerRadius
        cornerRadii[7] = state.bottomCornerRadius
        drawable.cornerRadii = cornerRadii

        // We first fade in the background layer to hide the expanding view, then fade it out
        // with SRC mode to draw a hole punch in the status bar and reveal the opening window.
        val fadeInProgress =
            getProgress(
                timings,
                linearProgress,
                timings.contentBeforeFadeOutDelay,
                timings.contentBeforeFadeOutDuration
            )
        if (fadeInProgress < 1) {
            val alpha =
                interpolators.contentBeforeFadeOutInterpolator.getInterpolation(fadeInProgress)
            drawable.alpha = (alpha * 0xFF).roundToInt()
        } else if (fadeOutWindowBackgroundLayer) {
            val fadeOutProgress =
                getProgress(
                    timings,
                    linearProgress,
                    timings.contentAfterFadeInDelay,
                    timings.contentAfterFadeInDuration
                )
            val alpha =
                1 - interpolators.contentAfterFadeInInterpolator.getInterpolation(fadeOutProgress)
            drawable.alpha = (alpha * 0xFF).roundToInt()

            if (drawHole) {
                drawable.setXfermode(SRC_MODE)
            }
        } else {
            drawable.alpha = 0xFF
        }
    }
}
