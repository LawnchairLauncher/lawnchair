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

package com.android.launcher3.taskbar.bubbles.animation

import android.view.View
import android.view.View.VISIBLE
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.taskbar.bubbles.BubbleBarBubble
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.taskbar.bubbles.BubbleStashController
import com.android.launcher3.taskbar.bubbles.BubbleView
import com.android.wm.shell.shared.animation.PhysicsAnimator

/** Handles animations for bubble bar bubbles. */
class BubbleBarViewAnimator
@JvmOverloads
constructor(
    private val bubbleBarView: BubbleBarView,
    private val bubbleStashController: BubbleStashController,
    private val scheduler: Scheduler = HandlerScheduler(bubbleBarView)
) {

    private var animatingBubble: AnimatingBubble? = null

    private companion object {
        /** The time to show the flyout. */
        const val FLYOUT_DELAY_MS: Long = 2500
        /** The initial scale Y value that the new bubble is set to before the animation starts. */
        const val BUBBLE_ANIMATION_INITIAL_SCALE_Y = 0.3f
        /** The minimum alpha value to make the bubble bar touchable. */
        const val MIN_ALPHA_FOR_TOUCHABLE = 0.5f
    }

    /** Wrapper around the animating bubble with its show and hide animations. */
    private data class AnimatingBubble(
        val bubbleView: BubbleView,
        val showAnimation: Runnable,
        val hideAnimation: Runnable
    )

    /** An interface for scheduling jobs. */
    interface Scheduler {

        /** Schedule the given [block] to run. */
        fun post(block: Runnable)

        /** Schedule the given [block] to start with a delay of [delayMillis]. */
        fun postDelayed(delayMillis: Long, block: Runnable)

        /** Cancel the given [block] if it hasn't started yet. */
        fun cancel(block: Runnable)
    }

    /** A [Scheduler] that uses a Handler to run jobs. */
    private class HandlerScheduler(private val view: View) : Scheduler {

        override fun post(block: Runnable) {
            view.post(block)
        }

        override fun postDelayed(delayMillis: Long, block: Runnable) {
            view.postDelayed(block, delayMillis)
        }

        override fun cancel(block: Runnable) {
            view.removeCallbacks(block)
        }
    }

    private val springConfig =
        PhysicsAnimator.SpringConfig(
            stiffness = SpringForce.STIFFNESS_LOW,
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        )

    /** Animates a bubble for the state where the bubble bar is stashed. */
    fun animateBubbleInForStashed(b: BubbleBarBubble) {
        val bubbleView = b.view
        val animator = PhysicsAnimator.getInstance(bubbleView)
        if (animator.isRunning()) animator.cancel()
        // the animation of a new bubble is divided into 2 parts. The first part shows the bubble
        // and the second part hides it after a delay.
        val showAnimation = buildHandleToBubbleBarAnimation()
        val hideAnimation = buildBubbleBarToHandleAnimation()
        animatingBubble = AnimatingBubble(bubbleView, showAnimation, hideAnimation)
        scheduler.post(showAnimation)
        scheduler.postDelayed(FLYOUT_DELAY_MS, hideAnimation)
    }

    /**
     * Returns a [Runnable] that starts the animation that morphs the handle to the bubble bar.
     *
     * Visually, the animation is divided into 2 parts. The stash handle starts animating up and
     * fading out and then the bubble bar starts animating up and fading in.
     *
     * To make the transition from the handle to the bar smooth, the positions and movement of the 2
     * views must be synchronized. To do that we use a single spring path along the Y axis, starting
     * from the handle's position to the eventual bar's position. The path is split into 3 parts.
     * 1. In the first part, we only animate the handle.
     * 2. In the second part the handle is fully hidden, and the bubble bar is animating in.
     * 3. The third part is the overshoot of the spring animation, where we make the bubble fully
     *    visible which helps avoiding further updates when we re-enter the second part.
     */
    private fun buildHandleToBubbleBarAnimation() = Runnable {
        // prepare the bubble bar for the animation
        bubbleBarView.onAnimatingBubbleStarted()
        bubbleBarView.visibility = VISIBLE
        bubbleBarView.alpha = 0f
        bubbleBarView.translationY = 0f
        bubbleBarView.scaleX = 1f
        bubbleBarView.scaleY = BUBBLE_ANIMATION_INITIAL_SCALE_Y
        bubbleBarView.relativePivotY = 0.5f

        // this is the offset between the center of the bubble bar and the center of the stash
        // handle. when the handle becomes invisible and we start animating in the bubble bar,
        // the translation y is offset by this value to make the transition from the handle to the
        // bar smooth.
        val offset = bubbleStashController.diffBetweenHandleAndBarCenters
        val stashedHandleTranslationY =
            bubbleStashController.stashedHandleTranslationForNewBubbleAnimation

        // this is the total distance that both the stashed handle and the bubble will be traveling
        // at the end of the animation the bubble bar will be positioned in the same place when it
        // shows while we're in an app.
        val totalTranslationY = bubbleStashController.bubbleBarTranslationYForTaskbar + offset
        val animator = bubbleStashController.stashedHandlePhysicsAnimator
        animator.setDefaultSpringConfig(springConfig)
        animator.spring(DynamicAnimation.TRANSLATION_Y, totalTranslationY)
        animator.addUpdateListener { handle, values ->
            val ty = values[DynamicAnimation.TRANSLATION_Y]?.value ?: return@addUpdateListener
            when {
                ty >= stashedHandleTranslationY -> {
                    // we're in the first leg of the animation. only animate the handle. the bubble
                    // bar remains hidden during this part of the animation

                    // map the path [0, stashedHandleTranslationY] to [0,1]
                    val fraction = ty / stashedHandleTranslationY
                    handle.alpha = 1 - fraction
                }
                ty >= totalTranslationY -> {
                    // this is the second leg of the animation. the handle should be completely
                    // hidden and the bubble bar should start animating in.
                    // it's possible that we're re-entering this leg because this is a spring
                    // animation, so only set the alpha and scale for the bubble bar if we didn't
                    // already fully animate in.
                    handle.alpha = 0f
                    bubbleBarView.translationY = ty - offset
                    if (bubbleBarView.alpha != 1f) {
                        // map the path [stashedHandleTranslationY, totalTranslationY] to [0, 1]
                        val fraction =
                            (ty - stashedHandleTranslationY) /
                                (totalTranslationY - stashedHandleTranslationY)
                        bubbleBarView.alpha = fraction
                        bubbleBarView.scaleY =
                            BUBBLE_ANIMATION_INITIAL_SCALE_Y +
                                (1 - BUBBLE_ANIMATION_INITIAL_SCALE_Y) * fraction
                        if (bubbleBarView.alpha > MIN_ALPHA_FOR_TOUCHABLE) {
                            bubbleStashController.updateTaskbarTouchRegion()
                        }
                    }
                }
                else -> {
                    // we're past the target animated value, set the alpha and scale for the bubble
                    // bar so that it's fully visible and no longer changing, but keep moving it
                    // along the animation path
                    bubbleBarView.alpha = 1f
                    bubbleBarView.scaleY = 1f
                    bubbleBarView.translationY = ty - offset
                    bubbleStashController.updateTaskbarTouchRegion()
                }
            }
        }
        animator.addEndListener { _, _, _, canceled, _, _, _ ->
            // if the show animation was canceled, also cancel the hide animation. this is typically
            // canceled in this class, but could potentially be canceled elsewhere.
            if (canceled) {
                val hideAnimation = animatingBubble?.hideAnimation ?: return@addEndListener
                scheduler.cancel(hideAnimation)
                animatingBubble = null
                bubbleBarView.onAnimatingBubbleCompleted()
                bubbleBarView.relativePivotY = 1f
                return@addEndListener
            }
            // the bubble bar is now fully settled in. update taskbar touch region so it's touchable
            bubbleStashController.updateTaskbarTouchRegion()
        }
        animator.start()
    }

    /**
     * Returns a [Runnable] that starts the animation that hides the bubble bar and morphs it into
     * the stashed handle.
     *
     * Similarly to the show animation, this is visually divided into 2 parts. We first animate the
     * bubble bar out, and then animate the stash handle in. At the end of the animation we reset
     * values of the bubble bar.
     *
     * This is a spring animation that goes along the same path of the show animation in the
     * opposite order, and is split into 3 parts:
     * 1. In the first part the bubble animates out.
     * 2. In the second part the bubble bar is fully hidden and the handle animates in.
     * 3. The third part is the overshoot. The handle is made fully visible.
     */
    private fun buildBubbleBarToHandleAnimation() = Runnable {
        if (animatingBubble == null) return@Runnable
        val offset = bubbleStashController.diffBetweenHandleAndBarCenters
        val stashedHandleTranslationY =
            bubbleStashController.stashedHandleTranslationForNewBubbleAnimation
        // this is the total distance that both the stashed handle and the bar will be traveling
        val totalTranslationY = bubbleStashController.bubbleBarTranslationYForTaskbar + offset
        bubbleStashController.setHandleTranslationY(totalTranslationY)
        val animator = bubbleStashController.stashedHandlePhysicsAnimator
        animator.setDefaultSpringConfig(springConfig)
        animator.spring(DynamicAnimation.TRANSLATION_Y, 0f)
        animator.addUpdateListener { handle, values ->
            val ty = values[DynamicAnimation.TRANSLATION_Y]?.value ?: return@addUpdateListener
            when {
                ty <= stashedHandleTranslationY -> {
                    // this is the first leg of the animation. only animate the bubble bar. the
                    // handle is hidden during this part
                    bubbleBarView.translationY = ty - offset
                    // map the path [totalTranslationY, stashedHandleTranslationY] to [0, 1]
                    val fraction =
                        (totalTranslationY - ty) / (totalTranslationY - stashedHandleTranslationY)
                    bubbleBarView.alpha = 1 - fraction
                    bubbleBarView.scaleY = 1 - (1 - BUBBLE_ANIMATION_INITIAL_SCALE_Y) * fraction
                    if (bubbleBarView.alpha > MIN_ALPHA_FOR_TOUCHABLE) {
                        bubbleStashController.updateTaskbarTouchRegion()
                    }
                }
                ty <= 0 -> {
                    // this is the second part of the animation. make the bubble bar invisible and
                    // start fading in the handle, but don't update the alpha if it's already fully
                    // visible
                    bubbleBarView.alpha = 0f
                    if (handle.alpha != 1f) {
                        // map the path [stashedHandleTranslationY, 0] to [0, 1]
                        val fraction = (stashedHandleTranslationY - ty) / stashedHandleTranslationY
                        handle.alpha = fraction
                    }
                }
                else -> {
                    // we reached the target value. set the alpha of the handle to 1
                    handle.alpha = 1f
                }
            }
        }
        animator.addEndListener { _, _, _, canceled, _, _, _ ->
            animatingBubble = null
            if (!canceled) bubbleStashController.stashBubbleBarImmediate()
            bubbleBarView.onAnimatingBubbleCompleted()
            bubbleBarView.relativePivotY = 1f
            bubbleStashController.updateTaskbarTouchRegion()
        }
        animator.start()
    }

    /** Animates to the initial state of the bubble bar, when there are no previous bubbles. */
    fun animateToInitialState(b: BubbleBarBubble, isInApp: Boolean, isExpanding: Boolean) {
        val bubbleView = b.view
        val animator = PhysicsAnimator.getInstance(bubbleView)
        if (animator.isRunning()) animator.cancel()
        // the animation of a new bubble is divided into 2 parts. The first part shows the bubble
        // and the second part hides it after a delay if we are in an app.
        val showAnimation = buildBubbleBarBounceAnimation()
        val hideAnimation =
            if (isInApp && !isExpanding) {
                buildBubbleBarToHandleAnimation()
            } else {
                // in this case the bubble bar remains visible so not much to do. once we implement
                // the flyout we'll update this runnable to hide it.
                Runnable {
                    animatingBubble = null
                    bubbleStashController.showBubbleBarImmediate()
                    bubbleBarView.onAnimatingBubbleCompleted()
                    bubbleStashController.updateTaskbarTouchRegion()
                }
            }
        animatingBubble = AnimatingBubble(bubbleView, showAnimation, hideAnimation)
        scheduler.post(showAnimation)
        scheduler.postDelayed(FLYOUT_DELAY_MS, hideAnimation)
    }

    private fun buildBubbleBarBounceAnimation() = Runnable {
        // prepare the bubble bar for the animation
        bubbleBarView.onAnimatingBubbleStarted()
        bubbleBarView.translationY = bubbleBarView.height.toFloat()
        bubbleBarView.visibility = VISIBLE
        bubbleBarView.alpha = 1f
        bubbleBarView.scaleX = 1f
        bubbleBarView.scaleY = 1f

        val animator = PhysicsAnimator.getInstance(bubbleBarView)
        animator.setDefaultSpringConfig(springConfig)
        animator.spring(DynamicAnimation.TRANSLATION_Y, bubbleStashController.bubbleBarTranslationY)
        animator.addUpdateListener { _, _ -> bubbleStashController.updateTaskbarTouchRegion() }
        animator.addEndListener { _, _, _, _, _, _, _ ->
            // the bubble bar is now fully settled in. update taskbar touch region so it's touchable
            bubbleStashController.updateTaskbarTouchRegion()
        }
        animator.start()
    }

    /** Handles touching the animating bubble bar. */
    fun onBubbleBarTouchedWhileAnimating() {
        PhysicsAnimator.getInstance(bubbleBarView).cancelIfRunning()
        bubbleStashController.stashedHandlePhysicsAnimator.cancelIfRunning()
        val hideAnimation = animatingBubble?.hideAnimation ?: return
        scheduler.cancel(hideAnimation)
        bubbleBarView.onAnimatingBubbleCompleted()
        bubbleBarView.relativePivotY = 1f
        animatingBubble = null
    }

    /** Notifies the animator that the taskbar area was touched during an animation. */
    fun onStashStateChangingWhileAnimating() {
        val hideAnimation = animatingBubble?.hideAnimation ?: return
        scheduler.cancel(hideAnimation)
        animatingBubble = null
        bubbleStashController.stashedHandlePhysicsAnimator.cancel()
        bubbleBarView.onAnimatingBubbleCompleted()
        bubbleBarView.relativePivotY = 1f
        bubbleStashController.onNewBubbleAnimationInterrupted(
            /* isStashed= */ bubbleBarView.alpha == 0f,
            bubbleBarView.translationY
        )
    }

    private fun <T> PhysicsAnimator<T>.cancelIfRunning() {
        if (isRunning()) cancel()
    }
}
