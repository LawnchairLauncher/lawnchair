/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.util.IntProperty
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import kotlin.math.max
import kotlin.math.min

/**
 * A class that allows changes in bounds within a view hierarchy to animate seamlessly between the
 * start and end state.
 */
class ViewHierarchyAnimator {
    companion object {
        /** Default values for the animation. These can all be overridden at call time. */
        private const val DEFAULT_DURATION = 500L
        private val DEFAULT_INTERPOLATOR = Interpolators.STANDARD
        private val DEFAULT_ADDITION_INTERPOLATOR = Interpolators.STANDARD_DECELERATE
        private val DEFAULT_REMOVAL_INTERPOLATOR = Interpolators.STANDARD_ACCELERATE
        private val DEFAULT_FADE_IN_INTERPOLATOR = Interpolators.ALPHA_IN

        /** The properties used to animate the view bounds. */
        private val PROPERTIES =
            mapOf(
                Bound.LEFT to createViewProperty(Bound.LEFT),
                Bound.TOP to createViewProperty(Bound.TOP),
                Bound.RIGHT to createViewProperty(Bound.RIGHT),
                Bound.BOTTOM to createViewProperty(Bound.BOTTOM)
            )

        private fun createViewProperty(bound: Bound): IntProperty<View> {
            return object : IntProperty<View>(bound.label) {
                override fun setValue(view: View, value: Int) {
                    setBound(view, bound, value)
                }

                override fun get(view: View): Int {
                    return getBound(view, bound) ?: bound.getValue(view)
                }
            }
        }

        /**
         * Instruct the animator to watch for changes to the layout of [rootView] and its children
         * and animate them. It uses the given [interpolator] and [duration].
         *
         * If a new layout change happens while an animation is already in progress, the animation
         * is updated to continue from the current values to the new end state.
         *
         * The animator continues to respond to layout changes until [stopAnimating] is called.
         *
         * Successive calls to this method override the previous settings ([interpolator] and
         * [duration]). The changes take effect on the next animation.
         *
         * Returns true if the [rootView] is already visible and will be animated, false otherwise.
         * To animate the addition of a view, see [animateAddition].
         */
        @JvmOverloads
        fun animate(
            rootView: View,
            interpolator: Interpolator = DEFAULT_INTERPOLATOR,
            duration: Long = DEFAULT_DURATION
        ): Boolean {
            return animate(rootView, interpolator, duration, ephemeral = false)
        }

        /**
         * Like [animate], but only takes effect on the next layout update, then unregisters itself
         * once the first animation is complete.
         */
        @JvmOverloads
        fun animateNextUpdate(
            rootView: View,
            interpolator: Interpolator = DEFAULT_INTERPOLATOR,
            duration: Long = DEFAULT_DURATION
        ): Boolean {
            return animate(rootView, interpolator, duration, ephemeral = true)
        }

        private fun animate(
            rootView: View,
            interpolator: Interpolator,
            duration: Long,
            ephemeral: Boolean
        ): Boolean {
            if (
                !occupiesSpace(
                    rootView.visibility,
                    rootView.left,
                    rootView.top,
                    rootView.right,
                    rootView.bottom
                )
            ) {
                return false
            }

            val listener = createUpdateListener(interpolator, duration, ephemeral)
            addListener(rootView, listener, recursive = true)
            return true
        }

        /**
         * Returns a new [View.OnLayoutChangeListener] that when called triggers a layout animation
         * using [interpolator] and [duration].
         *
         * If [ephemeral] is true, the listener is unregistered after the first animation. Otherwise
         * it keeps listening for further updates.
         */
        private fun createUpdateListener(
            interpolator: Interpolator,
            duration: Long,
            ephemeral: Boolean
        ): View.OnLayoutChangeListener {
            return createListener(interpolator, duration, ephemeral)
        }

        /**
         * Instruct the animator to stop watching for changes to the layout of [rootView] and its
         * children.
         *
         * Any animations already in progress continue until their natural conclusion.
         */
        fun stopAnimating(rootView: View) {
            recursivelyRemoveListener(rootView)
        }

        /**
         * Instruct the animator to watch for changes to the layout of [rootView] and its children,
         * and animate the next time the hierarchy appears after not being visible. It uses the
         * given [interpolator] and [duration].
         *
         * The start state of the animation is controlled by [origin]. This value can be any of the
         * four corners, any of the four edges, or the center of the view. If any margins are added
         * on the side(s) of the origin, the translation of those margins can be included by
         * specifying [includeMargins].
         *
         * Returns true if the [rootView] is invisible and will be animated, false otherwise. To
         * animate an already visible view, see [animate] and [animateNextUpdate].
         *
         * Then animator unregisters itself once the first addition animation is complete.
         *
         * @param includeFadeIn true if the animator should also fade in the view and child views.
         * @param fadeInInterpolator the interpolator to use when fading in the view. Unused if
         *     [includeFadeIn] is false.
         * @param onAnimationEnd an optional runnable that will be run once the animation
         *    finishes successfully. Will not be run if the animation is cancelled.
         */
        @JvmOverloads
        fun animateAddition(
            rootView: View,
            origin: Hotspot = Hotspot.CENTER,
            interpolator: Interpolator = DEFAULT_ADDITION_INTERPOLATOR,
            duration: Long = DEFAULT_DURATION,
            includeMargins: Boolean = false,
            includeFadeIn: Boolean = false,
            fadeInInterpolator: Interpolator = DEFAULT_FADE_IN_INTERPOLATOR,
            onAnimationEnd: Runnable? = null,
        ): Boolean {
            if (
                occupiesSpace(
                    rootView.visibility,
                    rootView.left,
                    rootView.top,
                    rootView.right,
                    rootView.bottom
                )
            ) {
                return false
            }

            val listener =
                createAdditionListener(
                    origin,
                    interpolator,
                    duration,
                    ignorePreviousValues = !includeMargins,
                    onAnimationEnd,
                )
            addListener(rootView, listener, recursive = true)

            if (!includeFadeIn) {
                return true
            }

            if (rootView is ViewGroup) {
                // First, fade in the container view
                val containerDuration = duration / 6
                createAndStartFadeInAnimator(
                    rootView, containerDuration, startDelay = 0, interpolator = fadeInInterpolator
                )

                // Then, fade in the child views
                val childDuration = duration / 3
                for (i in 0 until rootView.childCount) {
                    val view = rootView.getChildAt(i)
                    createAndStartFadeInAnimator(
                        view,
                        childDuration,
                        // Wait until the container fades in before fading in the children
                        startDelay = containerDuration,
                        interpolator = fadeInInterpolator
                    )
                }
                // For now, we don't recursively fade in additional sub views (e.g. grandchild
                // views) since it hasn't been necessary, but we could add that functionality.
            } else {
                // Fade in the view during the first half of the addition
                createAndStartFadeInAnimator(
                    rootView,
                    duration / 2,
                    startDelay = 0,
                    interpolator = fadeInInterpolator
                )
            }

            return true
        }

        /**
         * Returns a new [View.OnLayoutChangeListener] that on the next call triggers a layout
         * addition animation from the given [origin], using [interpolator] and [duration].
         *
         * If [ignorePreviousValues] is true, the animation will only span the area covered by the
         * new bounds. Otherwise it will include the margins between the previous and new bounds.
         */
        private fun createAdditionListener(
            origin: Hotspot,
            interpolator: Interpolator,
            duration: Long,
            ignorePreviousValues: Boolean,
            onAnimationEnd: Runnable? = null,
        ): View.OnLayoutChangeListener {
            return createListener(
                interpolator,
                duration,
                ephemeral = true,
                origin = origin,
                ignorePreviousValues = ignorePreviousValues,
                onAnimationEnd,
            )
        }

        /**
         * Returns a new [View.OnLayoutChangeListener] that when called triggers a layout animation
         * using [interpolator] and [duration].
         *
         * If [ephemeral] is true, the listener is unregistered after the first animation. Otherwise
         * it keeps listening for further updates.
         *
         * [origin] specifies whether the start values should be determined by a hotspot, and
         * [ignorePreviousValues] controls whether the previous values should be taken into account.
         */
        private fun createListener(
            interpolator: Interpolator,
            duration: Long,
            ephemeral: Boolean,
            origin: Hotspot? = null,
            ignorePreviousValues: Boolean = false,
            onAnimationEnd: Runnable? = null,
        ): View.OnLayoutChangeListener {
            return object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    view: View?,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    previousLeft: Int,
                    previousTop: Int,
                    previousRight: Int,
                    previousBottom: Int
                ) {
                    if (view == null) return

                    val startLeft = getBound(view, Bound.LEFT) ?: previousLeft
                    val startTop = getBound(view, Bound.TOP) ?: previousTop
                    val startRight = getBound(view, Bound.RIGHT) ?: previousRight
                    val startBottom = getBound(view, Bound.BOTTOM) ?: previousBottom

                    (view.getTag(R.id.tag_animator) as? ObjectAnimator)?.cancel()

                    if (!occupiesSpace(view.visibility, left, top, right, bottom)) {
                        setBound(view, Bound.LEFT, left)
                        setBound(view, Bound.TOP, top)
                        setBound(view, Bound.RIGHT, right)
                        setBound(view, Bound.BOTTOM, bottom)
                        return
                    }

                    val startValues =
                        processStartValues(
                            origin,
                            left,
                            top,
                            right,
                            bottom,
                            startLeft,
                            startTop,
                            startRight,
                            startBottom,
                            ignorePreviousValues
                        )
                    val endValues =
                        mapOf(
                            Bound.LEFT to left,
                            Bound.TOP to top,
                            Bound.RIGHT to right,
                            Bound.BOTTOM to bottom
                        )

                    val boundsToAnimate = mutableSetOf<Bound>()
                    if (startValues.getValue(Bound.LEFT) != left) boundsToAnimate.add(Bound.LEFT)
                    if (startValues.getValue(Bound.TOP) != top) boundsToAnimate.add(Bound.TOP)
                    if (startValues.getValue(Bound.RIGHT) != right) boundsToAnimate.add(Bound.RIGHT)
                    if (startValues.getValue(Bound.BOTTOM) != bottom) {
                        boundsToAnimate.add(Bound.BOTTOM)
                    }

                    if (boundsToAnimate.isNotEmpty()) {
                        startAnimation(
                            view,
                            boundsToAnimate,
                            startValues,
                            endValues,
                            interpolator,
                            duration,
                            ephemeral,
                            onAnimationEnd,
                        )
                    }
                }
            }
        }

        /**
         * Animates the removal of [rootView] and its children from the hierarchy. It uses the given
         * [interpolator] and [duration].
         *
         * The end state of the animation is controlled by [destination]. This value can be any of
         * the four corners, any of the four edges, or the center of the view. If any margins are
         * added on the side(s) of the [destination], the translation of those margins can be
         * included by specifying [includeMargins].
         *
         * @param onAnimationEnd an optional runnable that will be run once the animation finishes
         *    successfully. Will not be run if the animation is cancelled.
         */
        @JvmOverloads
        fun animateRemoval(
            rootView: View,
            destination: Hotspot = Hotspot.CENTER,
            interpolator: Interpolator = DEFAULT_REMOVAL_INTERPOLATOR,
            duration: Long = DEFAULT_DURATION,
            includeMargins: Boolean = false,
            onAnimationEnd: Runnable? = null,
        ): Boolean {
            if (
                !occupiesSpace(
                    rootView.visibility,
                    rootView.left,
                    rootView.top,
                    rootView.right,
                    rootView.bottom
                )
            ) {
                return false
            }

            val parent = rootView.parent as ViewGroup

            // Ensure that rootView's siblings animate nicely around the removal.
            val listener = createUpdateListener(interpolator, duration, ephemeral = true)
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child == rootView) continue
                addListener(child, listener, recursive = false)
            }

            val viewHasSiblings = parent.childCount > 1
            if (viewHasSiblings) {
                // Remove the view so that a layout update is triggered for the siblings and they
                // animate to their next position while the view's removal is also animating.
                parent.removeView(rootView)
                // By adding the view to the overlay, we can animate it while it isn't part of the
                // view hierarchy. It is correctly positioned because we have its previous bounds,
                // and we set them manually during the animation.
                parent.overlay.add(rootView)
            }
            // If this view has no siblings, the parent view may shrink to (0,0) size and mess
            // up the animation if we immediately remove the view. So instead, we just leave the
            // view in the real hierarchy until the animation finishes.

            val endRunnable = Runnable {
                if (viewHasSiblings) {
                    parent.overlay.remove(rootView)
                } else {
                    parent.removeView(rootView)
                }
                onAnimationEnd?.run()
            }

            val startValues =
                mapOf(
                    Bound.LEFT to rootView.left,
                    Bound.TOP to rootView.top,
                    Bound.RIGHT to rootView.right,
                    Bound.BOTTOM to rootView.bottom
                )
            val endValues =
                processEndValuesForRemoval(
                    destination,
                    rootView,
                    rootView.left,
                    rootView.top,
                    rootView.right,
                    rootView.bottom,
                    includeMargins,
                )

            val boundsToAnimate = mutableSetOf<Bound>()
            if (rootView.left != endValues.getValue(Bound.LEFT)) boundsToAnimate.add(Bound.LEFT)
            if (rootView.top != endValues.getValue(Bound.TOP)) boundsToAnimate.add(Bound.TOP)
            if (rootView.right != endValues.getValue(Bound.RIGHT)) boundsToAnimate.add(Bound.RIGHT)
            if (rootView.bottom != endValues.getValue(Bound.BOTTOM)) {
                boundsToAnimate.add(Bound.BOTTOM)
            }

            startAnimation(
                rootView,
                boundsToAnimate,
                startValues,
                endValues,
                interpolator,
                duration,
                ephemeral = true,
                endRunnable,
            )

            if (rootView is ViewGroup) {
                // Shift the children so they maintain a consistent position within the shrinking
                // view.
                shiftChildrenForRemoval(rootView, destination, endValues, interpolator, duration)

                // Fade out the children during the first half of the removal, so they don't clutter
                // too much once the view becomes very small. Then we fade out the view itself, in
                // case it has its own content and/or background.
                val startAlphas = FloatArray(rootView.childCount)
                for (i in 0 until rootView.childCount) {
                    startAlphas[i] = rootView.getChildAt(i).alpha
                }

                val animator = ValueAnimator.ofFloat(1f, 0f)
                animator.interpolator = Interpolators.ALPHA_OUT
                animator.duration = duration / 2
                animator.addUpdateListener { animation ->
                    for (i in 0 until rootView.childCount) {
                        rootView.getChildAt(i).alpha =
                            (animation.animatedValue as Float) * startAlphas[i]
                    }
                }
                animator.addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            rootView
                                .animate()
                                .alpha(0f)
                                .setInterpolator(Interpolators.ALPHA_OUT)
                                .setDuration(duration / 2)
                                .start()
                        }
                    }
                )
                animator.start()
            } else {
                // Fade out the view during the second half of the removal.
                rootView
                    .animate()
                    .alpha(0f)
                    .setInterpolator(Interpolators.ALPHA_OUT)
                    .setDuration(duration / 2)
                    .setStartDelay(duration / 2)
                    .start()
            }

            return true
        }

        /**
         * Animates the children of [rootView] so that its layout remains internally consistent as
         * it shrinks towards [destination] and changes its bounds to [endValues].
         *
         * Uses [interpolator] and [duration], which should match those of the removal animation.
         */
        private fun shiftChildrenForRemoval(
            rootView: ViewGroup,
            destination: Hotspot,
            endValues: Map<Bound, Int>,
            interpolator: Interpolator,
            duration: Long
        ) {
            for (i in 0 until rootView.childCount) {
                val child = rootView.getChildAt(i)
                val childStartValues =
                    mapOf(
                        Bound.LEFT to child.left,
                        Bound.TOP to child.top,
                        Bound.RIGHT to child.right,
                        Bound.BOTTOM to child.bottom
                    )
                val childEndValues =
                    processChildEndValuesForRemoval(
                        destination,
                        child.left,
                        child.top,
                        child.right,
                        child.bottom,
                        endValues.getValue(Bound.RIGHT) - endValues.getValue(Bound.LEFT),
                        endValues.getValue(Bound.BOTTOM) - endValues.getValue(Bound.TOP)
                    )

                val boundsToAnimate = mutableSetOf<Bound>()
                if (child.left != endValues.getValue(Bound.LEFT)) boundsToAnimate.add(Bound.LEFT)
                if (child.top != endValues.getValue(Bound.TOP)) boundsToAnimate.add(Bound.TOP)
                if (child.right != endValues.getValue(Bound.RIGHT)) boundsToAnimate.add(Bound.RIGHT)
                if (child.bottom != endValues.getValue(Bound.BOTTOM)) {
                    boundsToAnimate.add(Bound.BOTTOM)
                }

                startAnimation(
                    child,
                    boundsToAnimate,
                    childStartValues,
                    childEndValues,
                    interpolator,
                    duration,
                    ephemeral = true
                )
            }
        }

        /**
         * Returns whether the given [visibility] and bounds are consistent with a view being a
         * contributing part of the hierarchy.
         */
        private fun occupiesSpace(
            visibility: Int,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int
        ): Boolean {
            return visibility != View.GONE && left != right && top != bottom
        }

        /**
         * Computes the actual starting values based on the requested [origin] and on
         * [ignorePreviousValues].
         *
         * If [origin] is null, the resolved start values will be the same as those passed in, or
         * the same as the new values if [ignorePreviousValues] is true. If [origin] is not null,
         * the start values are resolved based on it, and [ignorePreviousValues] controls whether or
         * not newly introduced margins are included.
         *
         * Base case
         * ```
         *     1) origin=TOP
         *         x---------x    x---------x    x---------x    x---------x    x---------x
         *                        x---------x    |         |    |         |    |         |
         *                     ->             -> x---------x -> |         | -> |         |
         *                                                      x---------x    |         |
         *                                                                     x---------x
         *     2) origin=BOTTOM_LEFT
         *                                                                     x---------x
         *                                                      x-------x      |         |
         *                     ->             -> x----x      -> |       |   -> |         |
         *                        x--x           |    |         |       |      |         |
         *         x              x--x           x----x         x-------x      x---------x
         *     3) origin=CENTER
         *                                                                     x---------x
         *                                         x-----x       x-------x     |         |
         *              x      ->    x---x    ->   |     |   ->  |       |  -> |         |
         *                                         x-----x       x-------x     |         |
         *                                                                     x---------x
         * ```
         * In case the start and end values differ in the direction of the origin, and
         * [ignorePreviousValues] is false, the previous values are used and a translation is
         * included in addition to the view expansion.
         * ```
         *     origin=TOP_LEFT - (0,0,0,0) -> (30,30,70,70)
         *         x
         *                         x--x
         *                         x--x            x----x
         *                     ->             ->   |    |    ->    x------x
         *                                         x----x          |      |
         *                                                         |      |
         *                                                         x------x
         * ```
         */
        private fun processStartValues(
            origin: Hotspot?,
            newLeft: Int,
            newTop: Int,
            newRight: Int,
            newBottom: Int,
            previousLeft: Int,
            previousTop: Int,
            previousRight: Int,
            previousBottom: Int,
            ignorePreviousValues: Boolean
        ): Map<Bound, Int> {
            val startLeft = if (ignorePreviousValues) newLeft else previousLeft
            val startTop = if (ignorePreviousValues) newTop else previousTop
            val startRight = if (ignorePreviousValues) newRight else previousRight
            val startBottom = if (ignorePreviousValues) newBottom else previousBottom

            var left = startLeft
            var top = startTop
            var right = startRight
            var bottom = startBottom

            if (origin != null) {
                left =
                    when (origin) {
                        Hotspot.CENTER -> (newLeft + newRight) / 2
                        Hotspot.BOTTOM_LEFT,
                        Hotspot.LEFT,
                        Hotspot.TOP_LEFT -> min(startLeft, newLeft)
                        Hotspot.TOP,
                        Hotspot.BOTTOM -> newLeft
                        Hotspot.TOP_RIGHT,
                        Hotspot.RIGHT,
                        Hotspot.BOTTOM_RIGHT -> max(startRight, newRight)
                    }
                top =
                    when (origin) {
                        Hotspot.CENTER -> (newTop + newBottom) / 2
                        Hotspot.TOP_LEFT,
                        Hotspot.TOP,
                        Hotspot.TOP_RIGHT -> min(startTop, newTop)
                        Hotspot.LEFT,
                        Hotspot.RIGHT -> newTop
                        Hotspot.BOTTOM_RIGHT,
                        Hotspot.BOTTOM,
                        Hotspot.BOTTOM_LEFT -> max(startBottom, newBottom)
                    }
                right =
                    when (origin) {
                        Hotspot.CENTER -> (newLeft + newRight) / 2
                        Hotspot.TOP_RIGHT,
                        Hotspot.RIGHT,
                        Hotspot.BOTTOM_RIGHT -> max(startRight, newRight)
                        Hotspot.TOP,
                        Hotspot.BOTTOM -> newRight
                        Hotspot.BOTTOM_LEFT,
                        Hotspot.LEFT,
                        Hotspot.TOP_LEFT -> min(startLeft, newLeft)
                    }
                bottom =
                    when (origin) {
                        Hotspot.CENTER -> (newTop + newBottom) / 2
                        Hotspot.BOTTOM_RIGHT,
                        Hotspot.BOTTOM,
                        Hotspot.BOTTOM_LEFT -> max(startBottom, newBottom)
                        Hotspot.LEFT,
                        Hotspot.RIGHT -> newBottom
                        Hotspot.TOP_LEFT,
                        Hotspot.TOP,
                        Hotspot.TOP_RIGHT -> min(startTop, newTop)
                    }
            }

            return mapOf(
                Bound.LEFT to left,
                Bound.TOP to top,
                Bound.RIGHT to right,
                Bound.BOTTOM to bottom
            )
        }

        /**
         * Computes a removal animation's end values based on the requested [destination] and the
         * view's starting bounds.
         *
         * Examples:
         * ```
         *     1) destination=TOP
         *         x---------x    x---------x    x---------x    x---------x    x---------x
         *         |         |    |         |    |         |    x---------x
         *         |         | -> |         | -> x---------x ->             ->
         *         |         |    x---------x
         *         x---------x
         *      2) destination=BOTTOM_LEFT
         *         x---------x
         *         |         |    x-------x
         *         |         | -> |       |   -> x----x      ->             ->
         *         |         |    |       |      |    |         x--x
         *         x---------x    x-------x      x----x         x--x           x
         *     3) destination=CENTER
         *         x---------x
         *         |         |     x-------x       x-----x
         *         |         | ->  |       |  ->   |     |   ->    x---x    ->      x
         *         |         |     x-------x       x-----x
         *         x---------x
         *     4) destination=TOP, includeMargins=true (and view has large top margin)
         *                                                                     x---------x
         *                                                      x---------x
         *                                       x---------x    x---------x
         *                        x---------x    |         |
         *         x---------x    |         |    x---------x
         *         |         |    |         |
         *         |         | -> x---------x ->             ->             ->
         *         |         |
         *         x---------x
         * ```
         */
        private fun processEndValuesForRemoval(
            destination: Hotspot,
            rootView: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            includeMargins: Boolean = false,
        ): Map<Bound, Int> {
            val marginAdjustment =
                if (includeMargins &&
                    (rootView.layoutParams is ViewGroup.MarginLayoutParams)) {
                    val marginLp = rootView.layoutParams as ViewGroup.MarginLayoutParams
                    DimenHolder(
                        left = marginLp.leftMargin,
                        top = marginLp.topMargin,
                        right = marginLp.rightMargin,
                        bottom = marginLp.bottomMargin
                    )
            } else {
                DimenHolder(0, 0, 0, 0)
            }

            // These are the end values to use *if* this bound is part of the destination.
            val endLeft = left - marginAdjustment.left
            val endTop = top - marginAdjustment.top
            val endRight = right + marginAdjustment.right
            val endBottom = bottom + marginAdjustment.bottom

            // For the below calculations: We need to ensure that the destination bound and the
            // bound *opposite* to the destination bound end at the same value, to ensure that the
            // view has size 0 for that dimension.
            // For example,
            //  - If destination=TOP, then endTop == endBottom. Left and right stay the same.
            //  - If destination=RIGHT, then endRight == endLeft. Top and bottom stay the same.
            //  - If destination=BOTTOM_LEFT, then endBottom == endTop AND endLeft == endRight.

            return when (destination) {
                Hotspot.TOP -> mapOf(
                    Bound.TOP to endTop,
                    Bound.BOTTOM to endTop,
                    Bound.LEFT to left,
                    Bound.RIGHT to right,
                )
                Hotspot.TOP_RIGHT -> mapOf(
                    Bound.TOP to endTop,
                    Bound.BOTTOM to endTop,
                    Bound.RIGHT to endRight,
                    Bound.LEFT to endRight,
                )
                Hotspot.RIGHT -> mapOf(
                    Bound.RIGHT to endRight,
                    Bound.LEFT to endRight,
                    Bound.TOP to top,
                    Bound.BOTTOM to bottom,
                )
                Hotspot.BOTTOM_RIGHT -> mapOf(
                    Bound.BOTTOM to endBottom,
                    Bound.TOP to endBottom,
                    Bound.RIGHT to endRight,
                    Bound.LEFT to endRight,
                )
                Hotspot.BOTTOM -> mapOf(
                    Bound.BOTTOM to endBottom,
                    Bound.TOP to endBottom,
                    Bound.LEFT to left,
                    Bound.RIGHT to right,
                )
                Hotspot.BOTTOM_LEFT -> mapOf(
                    Bound.BOTTOM to endBottom,
                    Bound.TOP to endBottom,
                    Bound.LEFT to endLeft,
                    Bound.RIGHT to endLeft,
                )
                Hotspot.LEFT -> mapOf(
                    Bound.LEFT to endLeft,
                    Bound.RIGHT to endLeft,
                    Bound.TOP to top,
                    Bound.BOTTOM to bottom,
                )
                Hotspot.TOP_LEFT -> mapOf(
                    Bound.TOP to endTop,
                    Bound.BOTTOM to endTop,
                    Bound.LEFT to endLeft,
                    Bound.RIGHT to endLeft,
                )
                Hotspot.CENTER -> mapOf(
                    Bound.LEFT to (endLeft + endRight) / 2,
                    Bound.RIGHT to (endLeft + endRight) / 2,
                    Bound.TOP to (endTop + endBottom) / 2,
                    Bound.BOTTOM to (endTop + endBottom) / 2,
                )
            }
        }

        /**
         * Computes the end values for the child of a view being removed, based on the child's
         * starting bounds, the removal's [destination], and the [parentWidth] and [parentHeight].
         *
         * The end values always represent the child's position after it has been translated so that
         * its center is at the [destination].
         *
         * Examples:
         * ```
         *     1) destination=TOP
         *         The child maintains its left and right positions, but is shifted up so that its
         *         center is on the parent's end top edge.
         *     2) destination=BOTTOM_LEFT
         *         The child shifts so that its center is on the parent's end bottom left corner.
         *     3) destination=CENTER
         *         The child shifts so that its own center is on the parent's end center.
         * ```
         */
        private fun processChildEndValuesForRemoval(
            destination: Hotspot,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            parentWidth: Int,
            parentHeight: Int
        ): Map<Bound, Int> {
            val halfWidth = (right - left) / 2
            val halfHeight = (bottom - top) / 2

            val endLeft =
                when (destination) {
                    Hotspot.CENTER -> (parentWidth / 2) - halfWidth
                    Hotspot.BOTTOM_LEFT,
                    Hotspot.LEFT,
                    Hotspot.TOP_LEFT -> -halfWidth
                    Hotspot.TOP_RIGHT,
                    Hotspot.RIGHT,
                    Hotspot.BOTTOM_RIGHT -> parentWidth - halfWidth
                    Hotspot.TOP,
                    Hotspot.BOTTOM -> left
                }
            val endTop =
                when (destination) {
                    Hotspot.CENTER -> (parentHeight / 2) - halfHeight
                    Hotspot.TOP_LEFT,
                    Hotspot.TOP,
                    Hotspot.TOP_RIGHT -> -halfHeight
                    Hotspot.BOTTOM_RIGHT,
                    Hotspot.BOTTOM,
                    Hotspot.BOTTOM_LEFT -> parentHeight - halfHeight
                    Hotspot.LEFT,
                    Hotspot.RIGHT -> top
                }
            val endRight =
                when (destination) {
                    Hotspot.CENTER -> (parentWidth / 2) + halfWidth
                    Hotspot.TOP_RIGHT,
                    Hotspot.RIGHT,
                    Hotspot.BOTTOM_RIGHT -> parentWidth + halfWidth
                    Hotspot.BOTTOM_LEFT,
                    Hotspot.LEFT,
                    Hotspot.TOP_LEFT -> halfWidth
                    Hotspot.TOP,
                    Hotspot.BOTTOM -> right
                }
            val endBottom =
                when (destination) {
                    Hotspot.CENTER -> (parentHeight / 2) + halfHeight
                    Hotspot.BOTTOM_RIGHT,
                    Hotspot.BOTTOM,
                    Hotspot.BOTTOM_LEFT -> parentHeight + halfHeight
                    Hotspot.TOP_LEFT,
                    Hotspot.TOP,
                    Hotspot.TOP_RIGHT -> halfHeight
                    Hotspot.LEFT,
                    Hotspot.RIGHT -> bottom
                }

            return mapOf(
                Bound.LEFT to endLeft,
                Bound.TOP to endTop,
                Bound.RIGHT to endRight,
                Bound.BOTTOM to endBottom
            )
        }

        private fun addListener(
            view: View,
            listener: View.OnLayoutChangeListener,
            recursive: Boolean = false
        ) {
            // Make sure that only one listener is active at a time.
            val previousListener = view.getTag(R.id.tag_layout_listener)
            if (previousListener != null && previousListener is View.OnLayoutChangeListener) {
                view.removeOnLayoutChangeListener(previousListener)
            }

            view.addOnLayoutChangeListener(listener)
            view.setTag(R.id.tag_layout_listener, listener)
            if (view is ViewGroup && recursive) {
                for (i in 0 until view.childCount) {
                    addListener(view.getChildAt(i), listener, recursive = true)
                }
            }
        }

        private fun recursivelyRemoveListener(view: View) {
            val listener = view.getTag(R.id.tag_layout_listener)
            if (listener != null && listener is View.OnLayoutChangeListener) {
                view.setTag(R.id.tag_layout_listener, null /* tag */)
                view.removeOnLayoutChangeListener(listener)
            }

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    recursivelyRemoveListener(view.getChildAt(i))
                }
            }
        }

        private fun getBound(view: View, bound: Bound): Int? {
            return view.getTag(bound.overrideTag) as? Int
        }

        private fun setBound(view: View, bound: Bound, value: Int) {
            view.setTag(bound.overrideTag, value)
            bound.setValue(view, value)
        }

        /**
         * Initiates the animation of the requested [bounds] between [startValues] and [endValues]
         * by creating the animator, registering it with the [view], and starting it using
         * [interpolator] and [duration].
         *
         * If [ephemeral] is true, the layout change listener is unregistered at the end of the
         * animation, so no more animations happen.
         */
        private fun startAnimation(
            view: View,
            bounds: Set<Bound>,
            startValues: Map<Bound, Int>,
            endValues: Map<Bound, Int>,
            interpolator: Interpolator,
            duration: Long,
            ephemeral: Boolean,
            onAnimationEnd: Runnable? = null,
        ) {
            val propertyValuesHolders =
                buildList {
                        bounds.forEach { bound ->
                            add(
                                PropertyValuesHolder.ofInt(
                                    PROPERTIES[bound],
                                    startValues.getValue(bound),
                                    endValues.getValue(bound)
                                )
                            )
                        }
                    }
                    .toTypedArray()

            (view.getTag(R.id.tag_animator) as? ObjectAnimator)?.cancel()

            val animator = ObjectAnimator.ofPropertyValuesHolder(view, *propertyValuesHolders)
            animator.interpolator = interpolator
            animator.duration = duration
            animator.addListener(
                object : AnimatorListenerAdapter() {
                    var cancelled = false

                    override fun onAnimationEnd(animation: Animator) {
                        view.setTag(R.id.tag_animator, null /* tag */)
                        bounds.forEach { view.setTag(it.overrideTag, null /* tag */) }

                        // When an animation is cancelled, a new one might be taking over. We
                        // shouldn't unregister the listener yet.
                        if (ephemeral && !cancelled) {
                            // The duration is the same for the whole hierarchy, so it's safe to
                            // remove the listener recursively. We do this because some descendant
                            // views might not change bounds, and therefore not animate and leak the
                            // listener.
                            recursivelyRemoveListener(view)
                        }
                        if (!cancelled) {
                            onAnimationEnd?.run()
                        }
                    }

                    override fun onAnimationCancel(animation: Animator?) {
                        cancelled = true
                    }
                }
            )

            bounds.forEach { bound -> setBound(view, bound, startValues.getValue(bound)) }

            view.setTag(R.id.tag_animator, animator)
            animator.start()
        }

        private fun createAndStartFadeInAnimator(
            view: View,
            duration: Long,
            startDelay: Long,
            interpolator: Interpolator
        ) {
            val animator = ObjectAnimator.ofFloat(view, "alpha", 1f)
            animator.startDelay = startDelay
            animator.duration = duration
            animator.interpolator = interpolator
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.setTag(R.id.tag_alpha_animator, null /* tag */)
                }
            })

            (view.getTag(R.id.tag_alpha_animator) as? ObjectAnimator)?.cancel()
            view.setTag(R.id.tag_alpha_animator, animator)
            animator.start()
        }
    }

    /** An enum used to determine the origin of addition animations. */
    enum class Hotspot {
        CENTER,
        LEFT,
        TOP_LEFT,
        TOP,
        TOP_RIGHT,
        RIGHT,
        BOTTOM_RIGHT,
        BOTTOM,
        BOTTOM_LEFT
    }

    private enum class Bound(val label: String, val overrideTag: Int) {
        LEFT("left", R.id.tag_override_left) {
            override fun setValue(view: View, value: Int) {
                view.left = value
            }

            override fun getValue(view: View): Int {
                return view.left
            }
        },
        TOP("top", R.id.tag_override_top) {
            override fun setValue(view: View, value: Int) {
                view.top = value
            }

            override fun getValue(view: View): Int {
                return view.top
            }
        },
        RIGHT("right", R.id.tag_override_right) {
            override fun setValue(view: View, value: Int) {
                view.right = value
            }

            override fun getValue(view: View): Int {
                return view.right
            }
        },
        BOTTOM("bottom", R.id.tag_override_bottom) {
            override fun setValue(view: View, value: Int) {
                view.bottom = value
            }

            override fun getValue(view: View): Int {
                return view.bottom
            }
        };

        abstract fun setValue(view: View, value: Int)
        abstract fun getValue(view: View): Int
    }

    /** Simple data class to hold a set of dimens for left, top, right, bottom. */
    private data class DimenHolder(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
