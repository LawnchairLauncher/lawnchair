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
        const val BUBBLE_ANIMATION_TRANSLATION_Y = -50f
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
        val hideAnimation = buildHideAnimation(animator)
        scheduler.post(showAnimation)
        scheduler.postDelayed(FLYOUT_DELAY_MS, hideAnimation)
    }

    /** Returns a lambda that starts the animation that shows the new bubble. */
    private fun buildShowAnimation(
        bubbleView: BubbleView,
        key: String,
        animator: PhysicsAnimator<BubbleView>
    ): () -> Unit = {
        bubbleBarView.prepareForAnimatingBubbleWhileStashed(key)
        animator.setDefaultSpringConfig(springConfig)
        animator
            .spring(DynamicAnimation.ALPHA, 1f)
            .spring(DynamicAnimation.TRANSLATION_Y, BUBBLE_ANIMATION_TRANSLATION_Y)
        bubbleView.alpha = 0f
        bubbleView.visibility = VISIBLE
        animator.start()
    }

    /** Returns a lambda that starts the animation that hides the new bubble. */
    private fun buildHideAnimation(animator: PhysicsAnimator<BubbleView>): () -> Unit = {
        animator.setDefaultSpringConfig(springConfig)
        animator
            .spring(DynamicAnimation.ALPHA, 0f)
            .spring(DynamicAnimation.TRANSLATION_Y, 0f)
            .addEndListener { _, _, _, canceled, _, _, allRelevantPropertyAnimsEnded ->
                if (!canceled && allRelevantPropertyAnimsEnded) {
                    if (bubbleStashController.isStashed) {
                        bubbleBarView.alpha = 0f
                    }
                    bubbleBarView.onAnimatingBubbleCompleted()
                }
            }
        animator.start()
    }
}
