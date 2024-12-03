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
import androidx.annotation.VisibleForTesting
import com.android.app.animation.Interpolators.LINEAR
import com.android.systemui.shared.Flags.returnAnimationFrameworkLibrary
import java.util.concurrent.Executor
import kotlin.math.roundToInt

private const val TAG = "TransitionAnimator"

/** A base class to animate a window (activity or dialog) launch to or return from a view . */
class TransitionAnimator(
    private val mainExecutor: Executor,
    private val timings: Timings,
    private val interpolators: Interpolators,
) {
    companion object {
        internal const val DEBUG = false
        private val SRC_MODE = PorterDuffXfermode(PorterDuff.Mode.SRC)

        /**
         * Given the [linearProgress] of a transition animation, return the linear progress of the
         * sub-animation starting [delay] ms after the transition animation and that lasts
         * [duration].
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

        internal fun checkReturnAnimationFrameworkFlag() {
            check(returnAnimationFrameworkLibrary()) {
                "isLaunching cannot be false when the returnAnimationFrameworkLibrary flag is " +
                    "disabled"
            }
        }
    }

    private val transitionContainerLocation = IntArray(2)
    private val cornerRadii = FloatArray(8)

    /**
     * A controller that takes care of applying the animation to an expanding view.
     *
     * Note that all callbacks (onXXX methods) are all called on the main thread.
     */
    interface Controller {
        /**
         * The container in which the view that started the animation will be animating together
         * with the opening or closing window.
         *
         * This will be used to:
         * - Get the associated [Context].
         * - Compute whether we are expanding to or contracting from fully above the transition
         *   container.
         * - Get the overlay into which we put the window background layer, while the animating
         *   window is not visible (see [openingWindowSyncView]).
         *
         * This container can be changed to force this [Controller] to animate the expanding view
         * inside a different location, for instance to ensure correct layering during the
         * animation.
         */
        var transitionContainer: ViewGroup

        /** Whether the animation being controlled is a launch or a return. */
        val isLaunching: Boolean

        /**
         * If [isLaunching], the [View] with which the opening app window should be synchronized
         * once it starts to be visible. Otherwise, the [View] with which the closing app window
         * should be synchronized until it stops being visible.
         *
         * We will also move the window background layer to this view's overlay once the opening
         * window is visible (if [isLaunching]), or from this view's overlay once the closing window
         * stop being visible (if ![isLaunching]).
         *
         * If null, this will default to [transitionContainer].
         */
        val openingWindowSyncView: View?
            get() = null

        /**
         * Return the [State] of the view that will be animated. We will animate from this state to
         * the final window state.
         *
         * Note: This state will be mutated and passed to [onTransitionAnimationProgress] during the
         * animation.
         */
        fun createAnimatorState(): State

        /**
         * The animation started. This is typically used to initialize any additional resource
         * needed for the animation. [isExpandingFullyAbove] will be true if the window is expanding
         * fully above the [transitionContainer].
         */
        fun onTransitionAnimationStart(isExpandingFullyAbove: Boolean) {}

        /** The animation made progress and the expandable view [state] should be updated. */
        fun onTransitionAnimationProgress(state: State, progress: Float, linearProgress: Float) {}

        /**
         * The animation ended. This will be called *if and only if* [onTransitionAnimationStart]
         * was called previously. This is typically used to clean up the resources initialized when
         * the animation was started.
         */
        fun onTransitionAnimationEnd(isExpandingFullyAbove: Boolean) {}
    }

    /** The state of an expandable view during a [TransitionAnimator] animation. */
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
     * Start a transition animation controlled by [controller] towards [endState]. An intermediary
     * layer with [windowBackgroundColor] will fade in then (optionally) fade out above the
     * expanding view, and should be the same background color as the opening (or closing) window.
     *
     * If [fadeWindowBackgroundLayer] is true, then this intermediary layer will fade out during the
     * second half of the animation (if [Controller.isLaunching] or fade in during the first half of
     * the animation (if ![Controller.isLaunching]), and will have SRC blending mode (ultimately
     * punching a hole in the [transition container][Controller.transitionContainer]) iff [drawHole]
     * is true.
     */
    fun startAnimation(
        controller: Controller,
        endState: State,
        windowBackgroundColor: Int,
        fadeWindowBackgroundLayer: Boolean = true,
        drawHole: Boolean = false,
    ): Animation {
        if (!controller.isLaunching) checkReturnAnimationFrameworkFlag()

        // We add an extra layer with the same color as the dialog/app splash screen background
        // color, which is usually the same color of the app background. We first fade in this layer
        // to hide the expanding view, then we fade it out with SRC mode to draw a hole in the
        // transition container and reveal the opening window.
        val windowBackgroundLayer =
            GradientDrawable().apply {
                setColor(windowBackgroundColor)
                alpha = 0
            }

        val animator =
            createAnimator(
                controller,
                endState,
                windowBackgroundLayer,
                fadeWindowBackgroundLayer,
                drawHole
            )
        animator.start()

        return object : Animation {
            override fun cancel() {
                animator.cancel()
            }
        }
    }

    @VisibleForTesting
    fun createAnimator(
        controller: Controller,
        endState: State,
        windowBackgroundLayer: GradientDrawable,
        fadeWindowBackgroundLayer: Boolean = true,
        drawHole: Boolean = false
    ): ValueAnimator {
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

        val transitionContainer = controller.transitionContainer
        val isExpandingFullyAbove = isExpandingFullyAbove(transitionContainer, endState)

        // Update state.
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = timings.totalDuration
        animator.interpolator = LINEAR

        // Whether we should move the [windowBackgroundLayer] into the overlay of
        // [Controller.openingWindowSyncView] once the opening app window starts to be visible, or
        // from it once the closing app window stops being visible.
        // This is necessary as a one-off sync so we can avoid syncing at every frame, especially
        // in complex interactions like launching an activity from a dialog. See
        // b/214961273#comment2 for more details.
        val openingWindowSyncView = controller.openingWindowSyncView
        val openingWindowSyncViewOverlay = openingWindowSyncView?.overlay
        val moveBackgroundLayerWhenAppVisibilityChanges =
            openingWindowSyncView != null &&
                openingWindowSyncView.viewRootImpl != controller.transitionContainer.viewRootImpl

        val transitionContainerOverlay = transitionContainer.overlay
        var movedBackgroundLayer = false

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator, isReverse: Boolean) {
                    if (DEBUG) {
                        Log.d(TAG, "Animation started")
                    }
                    controller.onTransitionAnimationStart(isExpandingFullyAbove)

                    // Add the drawable to the transition container overlay. Overlays always draw
                    // drawables after views, so we know that it will be drawn above any view added
                    // by the controller.
                    if (controller.isLaunching || openingWindowSyncViewOverlay == null) {
                        transitionContainerOverlay.add(windowBackgroundLayer)
                    } else {
                        openingWindowSyncViewOverlay.add(windowBackgroundLayer)
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (DEBUG) {
                        Log.d(TAG, "Animation ended")
                    }

                    // TODO(b/330672236): Post this to the main thread instead so that it does not
                    // flicker with Flexiglass enabled.
                    controller.onTransitionAnimationEnd(isExpandingFullyAbove)
                    transitionContainerOverlay.remove(windowBackgroundLayer)

                    if (moveBackgroundLayerWhenAppVisibilityChanges && controller.isLaunching) {
                        openingWindowSyncViewOverlay?.remove(windowBackgroundLayer)
                    }
                }
            }
        )

        animator.addUpdateListener { animation ->
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

            state.visible =
                if (controller.isLaunching) {
                    // The expanding view can/should be hidden once it is completely covered by the
                    // opening window.
                    getProgress(
                        timings,
                        linearProgress,
                        timings.contentBeforeFadeOutDelay,
                        timings.contentBeforeFadeOutDuration
                    ) < 1
                } else {
                    getProgress(
                        timings,
                        linearProgress,
                        timings.contentAfterFadeInDelay,
                        timings.contentAfterFadeInDuration
                    ) > 0
                }

            if (
                controller.isLaunching &&
                    moveBackgroundLayerWhenAppVisibilityChanges &&
                    !state.visible &&
                    !movedBackgroundLayer
            ) {
                // The expanding view is not visible, so the opening app is visible. If this is
                // the first frame when it happens, trigger a one-off sync and move the
                // background layer in its new container.
                movedBackgroundLayer = true

                transitionContainerOverlay.remove(windowBackgroundLayer)
                openingWindowSyncViewOverlay!!.add(windowBackgroundLayer)

                ViewRootSync.synchronizeNextDraw(
                    transitionContainer,
                    openingWindowSyncView,
                    then = {}
                )
            } else if (
                !controller.isLaunching &&
                    moveBackgroundLayerWhenAppVisibilityChanges &&
                    state.visible &&
                    !movedBackgroundLayer
            ) {
                // The contracting view is now visible, so the closing app is not. If this is
                // the first frame when it happens, trigger a one-off sync and move the
                // background layer in its new container.
                movedBackgroundLayer = true

                openingWindowSyncViewOverlay!!.remove(windowBackgroundLayer)
                transitionContainerOverlay.add(windowBackgroundLayer)

                ViewRootSync.synchronizeNextDraw(
                    openingWindowSyncView,
                    transitionContainer,
                    then = {}
                )
            }

            val container =
                if (movedBackgroundLayer) {
                    openingWindowSyncView!!
                } else {
                    controller.transitionContainer
                }

            applyStateToWindowBackgroundLayer(
                windowBackgroundLayer,
                state,
                linearProgress,
                container,
                fadeWindowBackgroundLayer,
                drawHole,
                controller.isLaunching
            )
            controller.onTransitionAnimationProgress(state, progress, linearProgress)
        }

        return animator
    }

    /** Return whether we are expanding fully above the [transitionContainer]. */
    internal fun isExpandingFullyAbove(transitionContainer: View, endState: State): Boolean {
        transitionContainer.getLocationOnScreen(transitionContainerLocation)
        return endState.top <= transitionContainerLocation[1] &&
            endState.bottom >= transitionContainerLocation[1] + transitionContainer.height &&
            endState.left <= transitionContainerLocation[0] &&
            endState.right >= transitionContainerLocation[0] + transitionContainer.width
    }

    private fun applyStateToWindowBackgroundLayer(
        drawable: GradientDrawable,
        state: State,
        linearProgress: Float,
        transitionContainer: View,
        fadeWindowBackgroundLayer: Boolean,
        drawHole: Boolean,
        isLaunching: Boolean
    ) {
        // Update position.
        transitionContainer.getLocationOnScreen(transitionContainerLocation)
        drawable.setBounds(
            state.left - transitionContainerLocation[0],
            state.top - transitionContainerLocation[1],
            state.right - transitionContainerLocation[0],
            state.bottom - transitionContainerLocation[1]
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

        if (isLaunching) {
            if (fadeInProgress < 1) {
                val alpha =
                    interpolators.contentBeforeFadeOutInterpolator.getInterpolation(fadeInProgress)
                drawable.alpha = (alpha * 0xFF).roundToInt()
            } else if (fadeWindowBackgroundLayer) {
                val fadeOutProgress =
                    getProgress(
                        timings,
                        linearProgress,
                        timings.contentAfterFadeInDelay,
                        timings.contentAfterFadeInDuration
                    )
                val alpha =
                    1 -
                        interpolators.contentAfterFadeInInterpolator.getInterpolation(
                            fadeOutProgress
                        )
                drawable.alpha = (alpha * 0xFF).roundToInt()

                if (drawHole) {
                    drawable.setXfermode(SRC_MODE)
                }
            } else {
                drawable.alpha = 0xFF
            }
        } else {
            if (fadeInProgress < 1 && fadeWindowBackgroundLayer) {
                val alpha =
                    interpolators.contentBeforeFadeOutInterpolator.getInterpolation(fadeInProgress)
                drawable.alpha = (alpha * 0xFF).roundToInt()

                if (drawHole) {
                    drawable.setXfermode(SRC_MODE)
                }
            } else {
                val fadeOutProgress =
                    getProgress(
                        timings,
                        linearProgress,
                        timings.contentAfterFadeInDelay,
                        timings.contentAfterFadeInDuration
                    )
                val alpha =
                    1 -
                        interpolators.contentAfterFadeInInterpolator.getInterpolation(
                            fadeOutProgress
                        )
                drawable.alpha = (alpha * 0xFF).roundToInt()
                drawable.setXfermode(null)
            }
        }
    }
}
