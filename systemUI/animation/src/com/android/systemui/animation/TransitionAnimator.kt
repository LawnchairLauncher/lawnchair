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
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.GradientDrawable
import android.util.FloatProperty
import android.util.Log
import android.util.MathUtils
import android.util.TimeUtils
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroupOverlay
import android.view.ViewOverlay
import android.view.animation.Interpolator
import android.window.WindowAnimationState
import com.android.app.animation.Interpolators.LINEAR
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.dynamicanimation.animation.SpringAnimation
import com.android.internal.dynamicanimation.animation.SpringForce
import com.android.systemui.Flags
import com.android.systemui.Flags.moveTransitionAnimationLayer
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "TransitionAnimator"

/** A base class to animate a window (activity or dialog) launch to or return from a view . */
class TransitionAnimator(
    private val mainExecutor: Executor,
    private val timings: Timings,
    private val interpolators: Interpolators,

    /** [springTimings] and [springInterpolators] must either both be null or both not null. */
    private val springTimings: SpringTimings? = null,
    private val springInterpolators: Interpolators? = null,
    private val springParams: SpringParams = DEFAULT_SPRING_PARAMS,
) {
    companion object {
        internal const val DEBUG = false
        private val SRC_MODE = PorterDuffXfermode(PorterDuff.Mode.SRC)

        /** Default parameters for the multi-spring animator. */
        private val DEFAULT_SPRING_PARAMS =
            SpringParams(
                centerXStiffness = 450f,
                centerXDampingRatio = 0.965f,
                centerYStiffness = 400f,
                centerYDampingRatio = 0.95f,
                scaleStiffness = 500f,
                scaleDampingRatio = 0.99f,
            )

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
            duration: Long,
        ): Float {
            return getProgressInternal(
                timings.totalDuration.toFloat(),
                linearProgress,
                delay.toFloat(),
                duration.toFloat(),
            )
        }

        /**
         * Similar to [getProgress] above, bug the delay and duration are expressed as percentages
         * of the animation duration (between 0f and 1f).
         */
        internal fun getProgress(linearProgress: Float, delay: Float, duration: Float): Float {
            return getProgressInternal(totalDuration = 1f, linearProgress, delay, duration)
        }

        private fun getProgressInternal(
            totalDuration: Float,
            linearProgress: Float,
            delay: Float,
            duration: Float,
        ): Float {
            return MathUtils.constrain(
                (linearProgress * totalDuration - delay) / duration,
                0.0f,
                1.0f,
            )
        }

        internal fun WindowAnimationState.toTransitionState() =
            State().also {
                bounds?.let { b ->
                    it.top = b.top.roundToInt()
                    it.left = b.left.roundToInt()
                    it.bottom = b.bottom.roundToInt()
                    it.right = b.right.roundToInt()
                }
                it.bottomCornerRadius = (bottomLeftRadius + bottomRightRadius) / 2
                it.topCornerRadius = (topLeftRadius + topRightRadius) / 2
            }

        /** Builds a [FloatProperty] for updating the defined [property] using a spring. */
        private fun buildProperty(
            property: SpringProperty,
            updateProgress: (SpringState) -> Unit,
        ): FloatProperty<SpringState> {
            return object : FloatProperty<SpringState>(property.name) {
                override fun get(state: SpringState): Float {
                    return property.get(state)
                }

                override fun setValue(state: SpringState, value: Float) {
                    property.setValue(state, value)
                    updateProgress(state)
                }
            }
        }
    }

    private val transitionContainerLocation = IntArray(2)
    private val cornerRadii = FloatArray(8)

    init {
        check((springTimings == null) == (springInterpolators == null))
    }

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
         * Window state for the animation. If [isLaunching], it would correspond to the end state
         * otherwise the start state.
         *
         * If null, the state is inferred from the window targets
         */
        val windowAnimatorState: WindowAnimationState?
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
        var bottomCornerRadius: Float = 0f,
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

    /** Encapsulated the state of a multi-spring animation. */
    internal class SpringState(
        // Animated values.
        var centerX: Float,
        var centerY: Float,
        var scale: Float = 0f,

        // Update flags (used to decide whether it's time to update the transition state).
        var isCenterXUpdated: Boolean = false,
        var isCenterYUpdated: Boolean = false,
        var isScaleUpdated: Boolean = false,

        // Completion flags.
        var isCenterXDone: Boolean = false,
        var isCenterYDone: Boolean = false,
        var isScaleDone: Boolean = false,
    ) {
        /** Whether all springs composing the animation have settled in the final position. */
        val isDone
            get() = isCenterXDone && isCenterYDone && isScaleDone
    }

    /** Supported [SpringState] properties with getters and setters to update them. */
    private enum class SpringProperty {
        CENTER_X {
            override fun get(state: SpringState): Float {
                return state.centerX
            }

            override fun setValue(state: SpringState, value: Float) {
                state.centerX = value
                state.isCenterXUpdated = true
            }
        },
        CENTER_Y {
            override fun get(state: SpringState): Float {
                return state.centerY
            }

            override fun setValue(state: SpringState, value: Float) {
                state.centerY = value
                state.isCenterYUpdated = true
            }
        },
        SCALE {
            override fun get(state: SpringState): Float {
                return state.scale
            }

            override fun setValue(state: SpringState, value: Float) {
                state.scale = value
                state.isScaleUpdated = true
            }
        };

        /** Extracts the current value of the underlying property from [state]. */
        abstract fun get(state: SpringState): Float

        /** Update's the [value] of the underlying property inside [state]. */
        abstract fun setValue(state: SpringState, value: Float)
    }

    interface Animation {
        /** Start the animation. */
        fun start()

        /** Cancel the animation. */
        fun cancel()
    }

    @VisibleForTesting
    class InterpolatedAnimation(@get:VisibleForTesting val animator: Animator) : Animation {
        override fun start() {
            animator.start()
        }

        override fun cancel() {
            animator.cancel()
        }
    }

    @VisibleForTesting
    class MultiSpringAnimation
    internal constructor(
        @get:VisibleForTesting val springX: SpringAnimation,
        @get:VisibleForTesting val springY: SpringAnimation,
        @get:VisibleForTesting val springScale: SpringAnimation,
        private val springState: SpringState,
        private val startFrameTime: Long,
        private val onAnimationStart: Runnable,
    ) : Animation {
        @get:VisibleForTesting
        val isDone
            get() = springState.isDone

        override fun start() {
            onAnimationStart.run()

            // If no start frame time is provided, we start the springs normally.
            if (startFrameTime < 0) {
                startSprings()
                return
            }

            // This function is not guaranteed to be called inside a frame. We try to access the
            // frame time immediately, but if we're not inside a frame this will throw an exception.
            // We must then post a callback to be run at the beginning of the next frame.
            try {
                initAndStartSprings(Choreographer.getInstance().frameTime)
            } catch (_: IllegalStateException) {
                Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
                    initAndStartSprings(frameTimeNanos / TimeUtils.NANOS_PER_MS)
                }
            }
        }

        private fun initAndStartSprings(frameTime: Long) {
            // Initialize the spring as if it had started at the time that its start state
            // was created.
            springX.doAnimationFrame(startFrameTime)
            springY.doAnimationFrame(startFrameTime)
            springScale.doAnimationFrame(startFrameTime)
            // Move the spring time forward to the current frame, so it updates its internal state
            // following the initial momentum over the elapsed time.
            springX.doAnimationFrame(frameTime)
            springY.doAnimationFrame(frameTime)
            springScale.doAnimationFrame(frameTime)
            // Actually start the spring. We do this after the previous calls because the framework
            // doesn't like it when you call doAnimationFrame() after start() with an earlier time.
            startSprings()
        }

        private fun startSprings() {
            springX.start()
            springY.start()
            springScale.start()
        }

        override fun cancel() {
            springX.cancel()
            springY.cancel()
            springScale.cancel()
        }
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
        val contentAfterFadeInDuration: Long,
    )

    /**
     * The timings (durations and delays) used by the multi-spring animator. These are expressed as
     * fractions of 1, similar to how the progress of an animator can be expressed as a float value
     * between 0 and 1.
     */
    class SpringTimings(
        /** The portion of animation to wait before fading out the expanding content. */
        val contentBeforeFadeOutDelay: Float,

        /** The portion of animation during which the expanding content fades out. */
        val contentBeforeFadeOutDuration: Float,

        /** The portion of animation to wait before fading in the expanded content. */
        val contentAfterFadeInDelay: Float,

        /** The portion of animation during which the expanded content fades in. */
        val contentAfterFadeInDuration: Float,
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
        val contentAfterFadeInInterpolator: Interpolator,
    )

    /** The parameters (stiffnesses and damping ratios) used by the multi-spring animator. */
    data class SpringParams(
        // Parameters for the X position spring.
        val centerXStiffness: Float,
        val centerXDampingRatio: Float,

        // Parameters for the Y position spring.
        val centerYStiffness: Float,
        val centerYDampingRatio: Float,

        // Parameters for the scale spring.
        val scaleStiffness: Float,
        val scaleDampingRatio: Float,
    )

    /**
     * Start a transition animation controlled by [controller] towards [endState]. An intermediary
     * layer with [windowBackgroundColor] will fade in then (optionally) fade out above the
     * expanding view, and should be the same background color as the opening (or closing) window.
     *
     * If [shouldFadeWindowBackgroundLayer] returns true, then this intermediary layer will fade out
     * during the second half of the animation (if [Controller.isLaunching] or fade in during the
     * first half of the animation (if ![Controller.isLaunching]), and will have SRC blending mode
     * (ultimately punching a hole in the [transition container][Controller.transitionContainer])
     * iff [drawHole] is true.
     *
     * TODO(b/397646693): remove drawHole altogether.
     *
     * If [startVelocity] (expressed in pixels per second) is not null, a multi-spring animation
     * using it for the initial momentum will be used instead of the default interpolators. In this
     * case, [startFrameTime] (if non-negative) represents the frame time at which the springs
     * should be started.
     */
    fun startAnimation(
        controller: Controller,
        calculateEndState: () -> State,
        windowBackgroundColor: Int,
        shouldFadeWindowBackgroundLayer: () -> Boolean = { true },
        drawHole: Boolean = false,
        startVelocity: PointF? = null,
        startFrameTime: Long = -1,
    ): Animation {
        // We add an extra layer with the same color as the dialog/app splash screen background
        // color, which is usually the same color of the app background. We first fade in this layer
        // to hide the expanding view, then we fade it out with SRC mode to draw a hole in the
        // transition container and reveal the opening window.
        val windowBackgroundLayer =
            GradientDrawable().apply {
                setColor(windowBackgroundColor)
                alpha = 0
            }

        return createAnimation(
                controller,
                controller.createAnimatorState(),
                calculateEndState,
                windowBackgroundLayer,
                shouldFadeWindowBackgroundLayer,
                drawHole,
                startVelocity,
                startFrameTime,
            )
            .apply { start() }
    }

    @VisibleForTesting
    fun createAnimation(
        controller: Controller,
        startState: State,
        calculateEndState: () -> State,
        windowBackgroundLayer: GradientDrawable,
        shouldFadeWindowBackgroundLayer: () -> Boolean = { true },
        drawHole: Boolean = false,
        startVelocity: PointF? = null,
        startFrameTime: Long = -1,
    ): Animation {
        val transitionContainer = controller.transitionContainer
        val transitionContainerOverlay = transitionContainer.overlay
        val openingWindowSyncView = controller.openingWindowSyncView
        val openingWindowSyncViewOverlay = openingWindowSyncView?.overlay

        // Whether we should move the [windowBackgroundLayer] into the overlay of
        // [Controller.openingWindowSyncView] once the opening app window starts to be visible, or
        // from it once the closing app window stops being visible.
        // This is necessary as a one-off sync so we can avoid syncing at every frame, especially
        // in complex interactions like launching an activity from a dialog. See
        // b/214961273#comment2 for more details.
        val moveBackgroundLayerWhenAppVisibilityChanges =
            openingWindowSyncView != null &&
                openingWindowSyncView.viewRootImpl != controller.transitionContainer.viewRootImpl

        return if (startVelocity != null && springTimings != null && springInterpolators != null) {
            createSpringAnimation(
                controller,
                startState,
                calculateEndState,
                startVelocity,
                startFrameTime,
                windowBackgroundLayer,
                transitionContainer,
                transitionContainerOverlay,
                openingWindowSyncView,
                openingWindowSyncViewOverlay,
                shouldFadeWindowBackgroundLayer,
                drawHole,
                moveBackgroundLayerWhenAppVisibilityChanges,
            )
        } else {
            createInterpolatedAnimation(
                controller,
                startState,
                calculateEndState,
                windowBackgroundLayer,
                transitionContainer,
                transitionContainerOverlay,
                openingWindowSyncView,
                openingWindowSyncViewOverlay,
                shouldFadeWindowBackgroundLayer,
                drawHole,
                moveBackgroundLayerWhenAppVisibilityChanges,
            )
        }
    }

    /**
     * Creates an interpolator-based animator that uses [timings] and [interpolators] to calculate
     * the new bounds and corner radiuses at each frame.
     */
    private fun createInterpolatedAnimation(
        controller: Controller,
        state: State,
        calculateEndState: () -> State,
        windowBackgroundLayer: GradientDrawable,
        transitionContainer: View,
        transitionContainerOverlay: ViewGroupOverlay,
        openingWindowSyncView: View? = null,
        openingWindowSyncViewOverlay: ViewOverlay? = null,
        shouldFadeWindowBackgroundLayer: () -> Boolean = { true },
        drawHole: Boolean = false,
        moveBackgroundLayerWhenAppVisibilityChanges: Boolean = false,
    ): Animation {
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
        var endState = calculateEndState()
        var endTop = endState.top
        var endBottom = endState.bottom
        var endLeft = endState.left
        var endRight = endState.right
        var endCenterX = (endLeft + endRight) / 2f
        var endWidth = endRight - endLeft
        var endTopCornerRadius = endState.topCornerRadius
        var endBottomCornerRadius = endState.bottomCornerRadius

        fun maybeUpdateEndState() {
            if (Flags.dialogAnimEndStateUpdate()) {
                endState = calculateEndState()
            }
            if (
                endTop != endState.top ||
                    endBottom != endState.bottom ||
                    endLeft != endState.left ||
                    endRight != endState.right ||
                    endTopCornerRadius != endState.topCornerRadius ||
                    endBottomCornerRadius != endState.bottomCornerRadius
            ) {
                endTop = endState.top
                endBottom = endState.bottom
                endLeft = endState.left
                endRight = endState.right
                endCenterX = (endLeft + endRight) / 2f
                endWidth = endRight - endLeft
                if (Flags.dialogAnimEndStateUpdate()) {
                    endTopCornerRadius = endState.topCornerRadius
                    endBottomCornerRadius = endState.bottomCornerRadius
                }
            }
        }

        val isExpandingFullyAbove = isExpandingFullyAbove(transitionContainer, endState)
        var movedBackgroundLayer = false

        // Update state.
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = timings.totalDuration
        animator.interpolator = LINEAR

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator, isReverse: Boolean) {
                    onAnimationStart(
                        controller,
                        isExpandingFullyAbove,
                        windowBackgroundLayer,
                        transitionContainerOverlay,
                        openingWindowSyncViewOverlay,
                    )
                }

                override fun onAnimationEnd(animation: Animator) {
                    onAnimationEnd(
                        controller,
                        isExpandingFullyAbove,
                        windowBackgroundLayer,
                        transitionContainerOverlay,
                        openingWindowSyncViewOverlay,
                        moveBackgroundLayerWhenAppVisibilityChanges,
                    )
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

            state.visible = checkVisibility(timings, linearProgress, controller.isLaunching)

            if (!movedBackgroundLayer) {
                movedBackgroundLayer =
                    maybeMoveBackgroundLayer(
                        controller,
                        state,
                        windowBackgroundLayer,
                        transitionContainer,
                        transitionContainerOverlay,
                        openingWindowSyncView,
                        openingWindowSyncViewOverlay,
                        moveBackgroundLayerWhenAppVisibilityChanges,
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
                shouldFadeWindowBackgroundLayer,
                drawHole,
                controller.isLaunching,
                useSpring = false,
            )

            controller.onTransitionAnimationProgress(state, progress, linearProgress)
        }

        return InterpolatedAnimation(animator)
    }

    /**
     * Creates a compound animator made up of three springs: one for the center x position, one for
     * the center-y position, and one for the overall scale.
     *
     * This animator uses [springTimings] and [springInterpolators] for opacity, based on the scale
     * progress.
     */
    private fun createSpringAnimation(
        controller: Controller,
        startState: State,
        calculateEndState: () -> State,
        startVelocity: PointF,
        startFrameTime: Long,
        windowBackgroundLayer: GradientDrawable,
        transitionContainer: View,
        transitionContainerOverlay: ViewGroupOverlay,
        openingWindowSyncView: View?,
        openingWindowSyncViewOverlay: ViewOverlay?,
        shouldFadeWindowBackgroundLayer: () -> Boolean = { true },
        drawHole: Boolean = false,
        moveBackgroundLayerWhenAppVisibilityChanges: Boolean = false,
    ): Animation {

        var endState = calculateEndState()
        var springX: SpringAnimation? = null
        var springY: SpringAnimation? = null
        var targetX = endState.centerX
        var targetY = endState.centerY

        var movedBackgroundLayer = false

        fun maybeUpdateEndState() {
            if (Flags.dialogAnimEndStateUpdate()) {
                endState = calculateEndState()
            }
            if (endState.centerX != targetX && endState.centerY != targetY) {
                targetX = endState.centerX
                targetY = endState.centerY

                springX?.animateToFinalPosition(targetX)
                springY?.animateToFinalPosition(targetY)
            }
        }

        fun updateProgress(state: SpringState) {
            if (
                !(state.isCenterXUpdated || state.isCenterXDone) ||
                    !(state.isCenterYUpdated || state.isCenterYDone) ||
                    !(state.isScaleUpdated || state.isScaleDone)
            ) {
                // Because all three springs use the same update method, we only actually update
                // when all properties have received their new value (which could be unchanged from
                // the previous one), avoiding two redundant calls per frame.
                return
            }

            // Reset the update flags.
            state.isCenterXUpdated = false
            state.isCenterYUpdated = false
            state.isScaleUpdated = false

            // Current scale-based values, that will be used to find the new animation bounds.
            val width =
                MathUtils.lerp(startState.width.toFloat(), endState.width.toFloat(), state.scale)
            val height =
                MathUtils.lerp(startState.height.toFloat(), endState.height.toFloat(), state.scale)

            val newState =
                State(
                        left = (state.centerX - width / 2).toInt(),
                        top = (state.centerY - height / 2).toInt(),
                        right = (state.centerX + width / 2).toInt(),
                        bottom = (state.centerY + height / 2).toInt(),
                        topCornerRadius =
                            MathUtils.lerp(
                                startState.topCornerRadius,
                                endState.topCornerRadius,
                                state.scale,
                            ),
                        bottomCornerRadius =
                            MathUtils.lerp(
                                startState.bottomCornerRadius,
                                endState.bottomCornerRadius,
                                state.scale,
                            ),
                    )
                    .apply {
                        visible = checkVisibility(timings, state.scale, controller.isLaunching)
                    }

            if (!movedBackgroundLayer) {
                movedBackgroundLayer =
                    maybeMoveBackgroundLayer(
                        controller,
                        newState,
                        windowBackgroundLayer,
                        transitionContainer,
                        transitionContainerOverlay,
                        openingWindowSyncView,
                        openingWindowSyncViewOverlay,
                        moveBackgroundLayerWhenAppVisibilityChanges,
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
                newState,
                state.scale,
                container,
                shouldFadeWindowBackgroundLayer,
                drawHole,
                isLaunching = false,
                useSpring = true,
            )

            controller.onTransitionAnimationProgress(newState, state.scale, state.scale)

            maybeUpdateEndState()
        }

        val springState = SpringState(centerX = startState.centerX, centerY = startState.centerY)
        val isExpandingFullyAbove = isExpandingFullyAbove(transitionContainer, endState)

        /** End listener for each spring, which only does the end work if all springs are done. */
        fun onAnimationEnd() {
            if (!springState.isDone) return
            onAnimationEnd(
                controller,
                isExpandingFullyAbove,
                windowBackgroundLayer,
                transitionContainerOverlay,
                openingWindowSyncViewOverlay,
                moveBackgroundLayerWhenAppVisibilityChanges,
            )
        }

        springX =
            SpringAnimation(
                    springState,
                    buildProperty(SpringProperty.CENTER_X) { state -> updateProgress(state) },
                )
                .apply {
                    spring =
                        SpringForce(endState.centerX).apply {
                            stiffness = springParams.centerXStiffness
                            dampingRatio = springParams.centerXDampingRatio
                        }

                    setStartValue(startState.centerX)
                    setStartVelocity(startVelocity.x)
                    setMinValue(min(startState.centerX, endState.centerX))
                    setMaxValue(max(startState.centerX, endState.centerX))

                    addEndListener { _, _, _, _ ->
                        springState.isCenterXDone = true
                        onAnimationEnd()
                    }
                }
        springY =
            SpringAnimation(
                    springState,
                    buildProperty(SpringProperty.CENTER_Y) { state -> updateProgress(state) },
                )
                .apply {
                    spring =
                        SpringForce(endState.centerY).apply {
                            stiffness = springParams.centerYStiffness
                            dampingRatio = springParams.centerYDampingRatio
                        }

                    setStartValue(startState.centerY)
                    setStartVelocity(startVelocity.y)
                    setMinValue(min(startState.centerY, endState.centerY))
                    setMaxValue(max(startState.centerY, endState.centerY))

                    addEndListener { _, _, _, _ ->
                        springState.isCenterYDone = true
                        onAnimationEnd()
                    }
                }
        val springScale =
            SpringAnimation(
                    springState,
                    buildProperty(SpringProperty.SCALE) { state -> updateProgress(state) },
                )
                .apply {
                    spring =
                        SpringForce(1f).apply {
                            stiffness = springParams.scaleStiffness
                            dampingRatio = springParams.scaleDampingRatio
                        }

                    setStartValue(0f)
                    setMaxValue(1f)
                    setMinimumVisibleChange(abs(1f / startState.height))

                    addEndListener { _, _, _, _ ->
                        springState.isScaleDone = true
                        onAnimationEnd()
                    }
                }

        return MultiSpringAnimation(springX, springY, springScale, springState, startFrameTime) {
            onAnimationStart(
                controller,
                isExpandingFullyAbove,
                windowBackgroundLayer,
                transitionContainerOverlay,
                openingWindowSyncViewOverlay,
            )
        }
    }

    private fun onAnimationStart(
        controller: Controller,
        isExpandingFullyAbove: Boolean,
        windowBackgroundLayer: GradientDrawable,
        transitionContainerOverlay: ViewGroupOverlay,
        openingWindowSyncViewOverlay: ViewOverlay?,
    ) {
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

    private fun onAnimationEnd(
        controller: Controller,
        isExpandingFullyAbove: Boolean,
        windowBackgroundLayer: GradientDrawable,
        transitionContainerOverlay: ViewGroupOverlay,
        openingWindowSyncViewOverlay: ViewOverlay?,
        moveBackgroundLayerWhenAppVisibilityChanges: Boolean,
    ) {
        if (DEBUG) {
            Log.d(TAG, "Animation ended")
        }

        val onEnd = {
            controller.onTransitionAnimationEnd(isExpandingFullyAbove)
            transitionContainerOverlay.remove(windowBackgroundLayer)

            if (moveBackgroundLayerWhenAppVisibilityChanges && controller.isLaunching) {
                openingWindowSyncViewOverlay?.remove(windowBackgroundLayer)
            }
        }
        if (Flags.sceneContainer() || !controller.isLaunching) {
            // onAnimationEnd is called at the end of the animation, on a Choreographer animation
            // tick. During dialog launches, the following calls will move the animated content from
            // the dialog overlay back to its original position, and this change must be reflected
            // in the next frame given that we then sync the next frame of both the content and
            // dialog ViewRoots. During SysUI activity launches, we will instantly collapse the
            // shade at the end of the transition. However, if those are rendered by Compose, whose
            // compositions are also scheduled on a Choreographer frame, any state change made
            // *right now* won't be reflected in the next frame given that a Choreographer frame
            // can't schedule another and have it happen in the same frame. So we post the forwarded
            // calls to [Controller.onLaunchAnimationEnd] in the main executor, leaving this
            // Choreographer frame, ensuring that any state change applied by
            // onTransitionAnimationEnd() will be reflected in the same frame.
            mainExecutor.execute { onEnd() }
        } else {
            onEnd()
        }
    }

    /** Returns whether is the controller's view should be visible with the given [timings]. */
    private fun checkVisibility(timings: Timings, progress: Float, isLaunching: Boolean): Boolean {
        return if (isLaunching) {
            // The expanding view can/should be hidden once it is completely covered by the opening
            // window.
            getProgress(
                timings,
                progress,
                timings.contentBeforeFadeOutDelay,
                timings.contentBeforeFadeOutDuration,
            ) < 1
        } else {
            // The shrinking view can/should be hidden while it is completely covered by the closing
            // window.
            getProgress(
                timings,
                progress,
                timings.contentAfterFadeInDelay,
                timings.contentAfterFadeInDuration,
            ) > 0
        }
    }

    /**
     * If necessary, moves the background layer from the view container's overlay to the window sync
     * view overlay, or vice versa.
     *
     * @return true if the background layer vwas moved, false otherwise.
     */
    private fun maybeMoveBackgroundLayer(
        controller: Controller,
        state: State,
        windowBackgroundLayer: GradientDrawable,
        transitionContainer: View,
        transitionContainerOverlay: ViewGroupOverlay,
        openingWindowSyncView: View?,
        openingWindowSyncViewOverlay: ViewOverlay?,
        moveBackgroundLayerWhenAppVisibilityChanges: Boolean,
    ): Boolean {
        if (
            controller.isLaunching && moveBackgroundLayerWhenAppVisibilityChanges && !state.visible
        ) {
            // The expanding view is not visible, so the opening app is visible. If this is the
            // first frame when it happens, trigger a one-off sync and move the background layer
            // in its new container.
            transitionContainerOverlay.remove(windowBackgroundLayer)
            openingWindowSyncViewOverlay!!.add(windowBackgroundLayer)

            ViewRootSync.synchronizeNextDraw(
                transitionContainer,
                openingWindowSyncView!!,
                then = {},
            )

            return true
        } else if (
            !controller.isLaunching && moveBackgroundLayerWhenAppVisibilityChanges && state.visible
        ) {
            // The contracting view is now visible, so the closing app is not. If this is the first
            // frame when it happens, trigger a one-off sync and move the background layer in its
            // new container.
            openingWindowSyncViewOverlay!!.remove(windowBackgroundLayer)
            transitionContainerOverlay.add(windowBackgroundLayer)

            ViewRootSync.synchronizeNextDraw(
                openingWindowSyncView!!,
                transitionContainer,
                then = {},
            )

            return true
        }

        return false
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
        shouldFadeWindowBackgroundLayer: () -> Boolean,
        drawHole: Boolean,
        isLaunching: Boolean,
        useSpring: Boolean,
    ) {
        // Update position.
        transitionContainer.getLocationOnScreen(transitionContainerLocation)
        drawable.setBounds(
            state.left - transitionContainerLocation[0],
            state.top - transitionContainerLocation[1],
            state.right - transitionContainerLocation[0],
            state.bottom - transitionContainerLocation[1],
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

        val interpolators: Interpolators
        val fadeInProgress: Float
        val fadeOutProgress: Float
        if (useSpring) {
            interpolators = springInterpolators!!
            val timings = springTimings!!
            fadeInProgress =
                getProgress(
                    linearProgress,
                    timings.contentBeforeFadeOutDelay,
                    timings.contentBeforeFadeOutDuration,
                )
            fadeOutProgress =
                getProgress(
                    linearProgress,
                    timings.contentAfterFadeInDelay,
                    timings.contentAfterFadeInDuration,
                )
        } else {
            interpolators = this.interpolators
            fadeInProgress =
                getProgress(
                    timings,
                    linearProgress,
                    timings.contentBeforeFadeOutDelay,
                    timings.contentBeforeFadeOutDuration,
                )
            fadeOutProgress =
                getProgress(
                    timings,
                    linearProgress,
                    timings.contentAfterFadeInDelay,
                    timings.contentAfterFadeInDuration,
                )
        }

        // We first fade in the background layer to hide the expanding view, then fade it out with
        // SRC mode to draw a hole punch in the status bar and reveal the opening window (if
        // needed). If !isLaunching, the reverse happens.
        if (isLaunching) {
            if (fadeInProgress < 1) {
                val alpha =
                    interpolators.contentBeforeFadeOutInterpolator.getInterpolation(fadeInProgress)
                drawable.alpha = (alpha * 0xFF).roundToInt()
            } else if (shouldFadeWindowBackgroundLayer()) {
                val alpha =
                    1 -
                        interpolators.contentAfterFadeInInterpolator.getInterpolation(
                            fadeOutProgress
                        )
                drawable.alpha = (alpha * 0xFF).roundToInt()

                if (drawHole) {
                    drawable.setXfermode(SRC_MODE)
                }
            } else if (moveTransitionAnimationLayer() && fadeOutProgress >= 1 && drawHole) {
                // If [drawHole] is true, draw it once the opening content is done fading in.
                drawable.alpha = 0x00
                drawable.setXfermode(SRC_MODE)
            } else {
                drawable.alpha = 0xFF
            }
        } else {
            if (fadeInProgress < 1 && shouldFadeWindowBackgroundLayer()) {
                val alpha =
                    interpolators.contentBeforeFadeOutInterpolator.getInterpolation(fadeInProgress)
                drawable.alpha = (alpha * 0xFF).roundToInt()

                if (drawHole) {
                    drawable.setXfermode(SRC_MODE)
                }
            } else {
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
