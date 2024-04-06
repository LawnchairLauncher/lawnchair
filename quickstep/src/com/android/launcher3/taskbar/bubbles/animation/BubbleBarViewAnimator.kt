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
import androidx.core.animation.AnimatorSet
import androidx.core.animation.ObjectAnimator
import androidx.core.animation.doOnEnd
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.taskbar.bubbles.BubbleBarBubble
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.taskbar.bubbles.BubbleStashController
import com.android.launcher3.taskbar.bubbles.BubbleView
import com.android.systemui.util.doOnEnd
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
        const val BUBBLE_ANIMATION_FINAL_TRANSLATION_Y = -50f
        /** The initial translation Y value the new bubble is set to before the animation starts. */
        // TODO(liranb): get rid of this and calculate this based on the y-distance between the
        // bubble and the stash handle.
        const val BUBBLE_ANIMATION_INITIAL_TRANSLATION_Y = 50f
        /** The initial scale Y value that the new bubble is set to before the animation starts. */
        const val BUBBLE_ANIMATION_INITIAL_SCALE_Y = 0.3f
        /** The initial alpha value that the new bubble is set to before the animation starts. */
        const val BUBBLE_ANIMATION_INITIAL_ALPHA = 0.5f
        /** The duration of the hide bubble animation. */
        const val HIDE_BUBBLE_ANIMATION_DURATION_MS = 250L
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
        val showAnimation = buildShowAnimation(bubbleView, b.key, animator)
        val hideAnimation = buildHideAnimation(bubbleView)
        scheduler.post(showAnimation)
        scheduler.postDelayed(FLYOUT_DELAY_MS, hideAnimation)
    }

    /**
     * Returns a lambda that starts the animation that shows the new bubble.
     *
     * The animation is divided into 2 parts. First the stash handle starts animating up and fades
     * out. When it ends the bubble starts fading in. The bubble and stashed handle are aligned to
     * give the impression of the stash handle morphing into the bubble.
     */
    private fun buildShowAnimation(
        bubbleView: BubbleView,
        key: String,
        bubbleAnimator: PhysicsAnimator<BubbleView>
    ): () -> Unit = {
        // calculate the initial translation x the bubble should have in order to align it with the
        // stash handle.
        val initialTranslationX =
            bubbleStashController.stashedHandleCenterX - bubbleView.centerXOnScreen
        bubbleBarView.prepareForAnimatingBubbleWhileStashed(key)
        bubbleAnimator.setDefaultSpringConfig(springConfig)
        bubbleAnimator
            .spring(DynamicAnimation.ALPHA, 1f)
            .spring(DynamicAnimation.TRANSLATION_Y, BUBBLE_ANIMATION_FINAL_TRANSLATION_Y)
            .spring(DynamicAnimation.SCALE_Y, 1f)
        // prepare the bubble for the animation
        bubbleView.alpha = 0f
        bubbleView.translationX = initialTranslationX
        bubbleView.translationY = BUBBLE_ANIMATION_INITIAL_TRANSLATION_Y
        bubbleView.scaleY = BUBBLE_ANIMATION_INITIAL_SCALE_Y
        bubbleView.visibility = VISIBLE
        // start the stashed handle animation. when it ends, start the bubble animation.
        val stashedHandleAnimation = bubbleStashController.buildHideHandleAnimationForNewBubble()
        stashedHandleAnimation.doOnEnd {
            bubbleView.alpha = BUBBLE_ANIMATION_INITIAL_ALPHA
            bubbleAnimator.start()
            bubbleStashController.setStashAlpha(0f)
        }
        stashedHandleAnimation.start()
    }

    /**
     * Returns a lambda that starts the animation that hides the new bubble.
     *
     * Similarly to the show animation, this is divided into 2 parts. We first animate the bubble
     * out, and then animate the stash handle in. At the end of the animation we reset the values of
     * the bubble.
     */
    private fun buildHideAnimation(bubbleView: BubbleView): () -> Unit = {
        val stashAnimation = bubbleStashController.buildShowHandleAnimationForNewBubble()
        val alphaAnimator =
            ObjectAnimator.ofFloat(bubbleView, View.ALPHA, BUBBLE_ANIMATION_INITIAL_ALPHA)
        val translationYAnimator =
            ObjectAnimator.ofFloat(
                bubbleView,
                View.TRANSLATION_Y,
                BUBBLE_ANIMATION_INITIAL_TRANSLATION_Y
            )
        val scaleYAnimator =
            ObjectAnimator.ofFloat(bubbleView, View.SCALE_Y, BUBBLE_ANIMATION_INITIAL_SCALE_Y)
        val hideBubbleAnimation = AnimatorSet()
        hideBubbleAnimation.playTogether(alphaAnimator, translationYAnimator, scaleYAnimator)
        hideBubbleAnimation.duration = HIDE_BUBBLE_ANIMATION_DURATION_MS
        hideBubbleAnimation.doOnEnd {
            // the bubble is now hidden, start the stash handle animation and reset bubble
            // properties
            bubbleStashController.setStashAlpha(
                BubbleStashController.NEW_BUBBLE_HIDE_HANDLE_ANIMATION_ALPHA
            )
            bubbleView.alpha = 0f
            stashAnimation.start()
            bubbleView.translationY = 0f
            bubbleView.scaleY = 1f
            if (bubbleStashController.isStashed) {
                bubbleBarView.alpha = 0f
            }
            bubbleBarView.onAnimatingBubbleCompleted()
        }
        hideBubbleAnimation.start()
    }
}

/** The X position in screen coordinates of the center of the bubble. */
private val BubbleView.centerXOnScreen: Float
    get() {
        val screenCoordinates = IntArray(2)
        getLocationOnScreen(screenCoordinates)
        return screenCoordinates[0] + width / 2f
    }
