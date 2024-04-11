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

    private companion object {
        /** The time to show the flyout. */
        const val FLYOUT_DELAY_MS: Long = 2500
        /** The translation Y the new bubble will animate to. */
        const val BUBBLE_ANIMATION_BUBBLE_TRANSLATION_Y = -50f
        /** The initial translation Y value the new bubble is set to before the animation starts. */
        // TODO(liranb): get rid of this and calculate this based on the y-distance between the
        // bubble and the stash handle.
        const val BUBBLE_ANIMATION_TRANSLATION_Y_OFFSET = 50f
        /** The initial scale Y value that the new bubble is set to before the animation starts. */
        const val BUBBLE_ANIMATION_INITIAL_SCALE_Y = 0.3f
        /**
         * The distance the stashed handle will travel as it gets hidden as part of the new bubble
         * animation.
         */
        // TODO(liranb): calculate this based on the position of the views
        const val BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y = -20f
    }

    /** An interface for scheduling jobs. */
    interface Scheduler {

        /** Schedule the given [block] to run. */
        fun post(block: () -> Unit)

        /** Schedule the given [block] to start with a delay of [delayMillis]. */
        fun postDelayed(delayMillis: Long, block: () -> Unit)
    }

    /** A [Scheduler] that uses a Handler to run jobs. */
    private class HandlerScheduler(private val view: View) : Scheduler {

        override fun post(block: () -> Unit) {
            view.post(block)
        }

        override fun postDelayed(delayMillis: Long, block: () -> Unit) {
            view.postDelayed(block, delayMillis)
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
        val showAnimation = buildShowAnimation(bubbleView, b.key)
        val hideAnimation = buildHideAnimation(bubbleView)
        scheduler.post(showAnimation)
        scheduler.postDelayed(FLYOUT_DELAY_MS, hideAnimation)
    }

    /**
     * Returns a lambda that starts the animation that shows the new bubble.
     *
     * Visually, the animation is divided into 2 parts. The stash handle starts animating up and
     * fading out and then the bubble starts animating up and fading in.
     *
     * To make the transition from the handle to the bubble smooth, the positions and movement of
     * the 2 views must be synchronized. To do that we use a single spring path along the Y axis,
     * starting from the handle's position to the eventual bubble's position. The path is split into
     * 3 parts.
     * 1. In the first part, we only animate the handle.
     * 1. In the second part the handle is fully hidden, and the bubble is animating in.
     * 1. The third part is the overshoot of the spring animation, where we make the bubble fully
     *    visible which helps avoiding further updates when we re-enter the second part.
     */
    private fun buildShowAnimation(
        bubbleView: BubbleView,
        key: String,
    ): () -> Unit = {
        bubbleBarView.prepareForAnimatingBubbleWhileStashed(key)
        // calculate the initial translation x the bubble should have in order to align it with the
        // stash handle.
        val initialTranslationX =
            bubbleStashController.stashedHandleCenterX - bubbleView.centerXOnScreen
        // prepare the bubble for the animation
        bubbleView.alpha = 0f
        bubbleView.translationX = initialTranslationX
        bubbleView.scaleY = BUBBLE_ANIMATION_INITIAL_SCALE_Y
        bubbleView.visibility = VISIBLE

        // this is the total distance that both the stashed handle and the bubble will be traveling
        val totalTranslationY =
            BUBBLE_ANIMATION_BUBBLE_TRANSLATION_Y + BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y
        val animator = bubbleStashController.stashedHandlePhysicsAnimator
        animator.setDefaultSpringConfig(springConfig)
        animator.spring(DynamicAnimation.TRANSLATION_Y, totalTranslationY)
        animator.addUpdateListener { target, values ->
            val ty = values[DynamicAnimation.TRANSLATION_Y]?.value ?: return@addUpdateListener
            when {
                ty >= BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y -> {
                    // we're in the first leg of the animation. only animate the handle. the bubble
                    // remains hidden during this part of the animation

                    // map the path [0, BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y] to [0,1]
                    val fraction = ty / BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y
                    target.alpha = 1 - fraction / 2
                }
                ty >= totalTranslationY -> {
                    // this is the second leg of the animation. the handle should be completely
                    // hidden and the bubble should start animating in.
                    // it's possible that we're re-entering this leg because this is a spring
                    // animation, so only set the alpha and scale for the bubble if we didn't
                    // already fully animate in.
                    target.alpha = 0f
                    bubbleView.translationY = ty + BUBBLE_ANIMATION_TRANSLATION_Y_OFFSET
                    if (bubbleView.alpha != 1f) {
                        // map the path
                        // [BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y, totalTranslationY]
                        // to [0, 1]
                        val fraction =
                            (ty - BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y) /
                                BUBBLE_ANIMATION_BUBBLE_TRANSLATION_Y
                        bubbleView.alpha = fraction
                        bubbleView.scaleY =
                            BUBBLE_ANIMATION_INITIAL_SCALE_Y +
                                (1 - BUBBLE_ANIMATION_INITIAL_SCALE_Y) * fraction
                    }
                }
                else -> {
                    // we're past the target animated value, set the alpha and scale for the bubble
                    // so that it's fully visible and no longer changing, but keep moving it along
                    // the animation path
                    bubbleView.alpha = 1f
                    bubbleView.scaleY = 1f
                    bubbleView.translationY = ty + BUBBLE_ANIMATION_TRANSLATION_Y_OFFSET
                }
            }
        }
        animator.start()
    }

    /**
     * Returns a lambda that starts the animation that hides the new bubble.
     *
     * Similarly to the show animation, this is visually divided into 2 parts. We first animate the
     * bubble out, and then animate the stash handle in. At the end of the animation we reset the
     * values of the bubble.
     *
     * This is a spring animation that goes along the same path of the show animation in the
     * opposite order, and is split into 3 parts:
     * 1. In the first part the bubble animates out.
     * 1. In the second part the bubble is fully hidden and the handle animates in.
     * 1. The third part is the overshoot. The handle is made fully visible.
     */
    private fun buildHideAnimation(bubbleView: BubbleView): () -> Unit = {
        // this is the total distance that both the stashed handle and the bubble will be traveling
        val totalTranslationY =
            BUBBLE_ANIMATION_BUBBLE_TRANSLATION_Y + BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y
        val animator = bubbleStashController.stashedHandlePhysicsAnimator
        animator.setDefaultSpringConfig(springConfig)
        animator.spring(DynamicAnimation.TRANSLATION_Y, 0f)
        animator.addUpdateListener { target, values ->
            val ty = values[DynamicAnimation.TRANSLATION_Y]?.value ?: return@addUpdateListener
            when {
                ty <= BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y -> {
                    // this is the first leg of the animation. only animate the bubble. the handle
                    // is hidden during this part
                    bubbleView.translationY = ty + BUBBLE_ANIMATION_TRANSLATION_Y_OFFSET
                    // map the path
                    // [totalTranslationY, BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y]
                    // to [0, 1]
                    val fraction = (totalTranslationY - ty) / BUBBLE_ANIMATION_BUBBLE_TRANSLATION_Y
                    bubbleView.alpha = 1 - fraction / 2
                    bubbleView.scaleY = 1 - (1 - BUBBLE_ANIMATION_INITIAL_SCALE_Y) * fraction
                }
                ty <= 0 -> {
                    // this is the second part of the animation. make the bubble invisible and
                    // start fading in the handle, but don't update the alpha if it's already fully
                    // visible
                    bubbleView.alpha = 0f
                    if (target.alpha != 1f) {
                        // map the path [BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y, 0] to [0, 1]
                        val fraction =
                            (BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y - ty) /
                                BUBBLE_ANIMATION_STASH_HANDLE_TRANSLATION_Y
                        target.alpha = fraction
                    }
                }
                else -> {
                    // we reached the target value. set the alpha of the handle to 1
                    target.alpha = 1f
                }
            }
        }
        animator.addEndListener { _, _, _, _, _, _, _ ->
            bubbleView.alpha = 0f
            bubbleView.translationY = 0f
            bubbleView.scaleY = 1f
            if (bubbleStashController.isStashed) {
                bubbleBarView.alpha = 0f
            }
            bubbleBarView.onAnimatingBubbleCompleted()
        }
        animator.start()
    }
}

/** The X position in screen coordinates of the center of the bubble. */
private val BubbleView.centerXOnScreen: Float
    get() {
        val screenCoordinates = IntArray(2)
        getLocationOnScreen(screenCoordinates)
        return screenCoordinates[0] + width / 2f
    }
