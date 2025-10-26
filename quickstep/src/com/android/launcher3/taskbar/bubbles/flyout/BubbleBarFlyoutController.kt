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

package com.android.launcher3.taskbar.bubbles.flyout

import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.animation.addListener
import com.android.app.animation.Interpolators
import com.android.launcher3.R

/** Creates and manages the visibility of the [BubbleBarFlyoutView]. */
class BubbleBarFlyoutController
@JvmOverloads
constructor(
    private val container: FrameLayout,
    private val positioner: BubbleBarFlyoutPositioner,
    private val callbacks: FlyoutCallbacks,
    private val flyoutScheduler: FlyoutScheduler = HandlerScheduler(container),
) {

    val maximumFlyoutHeight: Int = BubbleBarFlyoutView.getMaximumViewHeight(container.context)

    private companion object {
        const val EXPAND_ANIMATION_DURATION_MS = 400L
        const val COLLAPSE_ANIMATION_DURATION_MS = 350L
    }

    private var flyout: BubbleBarFlyoutView? = null
    private var animator: ValueAnimator? = null
    private val horizontalMargin =
        container.context.resources.getDimensionPixelSize(R.dimen.transient_taskbar_bottom_margin)

    private enum class AnimationType {
        /** Morphs the flyout between a dot and a rounded rectangle. */
        MORPH,
        /** Fades the flyout in or out. */
        FADE,
    }

    /** The bounds of the flyout. */
    val flyoutBounds: Rect?
        get() {
            val flyout = this.flyout ?: return null
            val rect = Rect(flyout.bounds)
            rect.offset(0, flyout.translationY.toInt())
            return rect
        }

    fun setUpAndShowFlyout(message: BubbleBarFlyoutMessage, onInit: () -> Unit, onEnd: () -> Unit) {
        flyout?.let(container::removeView)
        val flyout = BubbleBarFlyoutView(container.context, positioner, flyoutScheduler)

        flyout.translationY = positioner.targetTy

        val lp =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or if (positioner.isOnLeft) Gravity.LEFT else Gravity.RIGHT,
            )
        lp.marginStart = horizontalMargin
        lp.marginEnd = horizontalMargin
        container.addView(flyout, lp)

        this.flyout = flyout
        flyout.showFromCollapsed(message) {
            flyout.updateExpansionProgress(0f)
            onInit()
            showFlyout(AnimationType.MORPH, onEnd)
        }
    }

    private fun showFlyout(animationType: AnimationType, endAction: () -> Unit) {
        val flyout = this.flyout ?: return
        val startValue = getCurrentAnimatedValueIfRunning() ?: 0f
        val duration = (EXPAND_ANIMATION_DURATION_MS * (1f - startValue)).toLong()
        animator?.cancel()
        val animator = ValueAnimator.ofFloat(startValue, 1f).setDuration(duration)
        animator.interpolator = Interpolators.EMPHASIZED
        this.animator = animator
        when (animationType) {
            AnimationType.FADE ->
                animator.addUpdateListener { _ -> flyout.alpha = animator.animatedValue as Float }
            AnimationType.MORPH ->
                animator.addUpdateListener { _ ->
                    flyout.updateExpansionProgress(animator.animatedValue as Float)
                }
        }
        animator.addListener(
            onEnd = {
                endAction()
                flyout.setOnClickListener { callbacks.flyoutClicked() }
            }
        )
        animator.start()
    }

    fun updateFlyoutFullyExpanded(message: BubbleBarFlyoutMessage, onEnd: () -> Unit) {
        val flyout = flyout ?: return
        hideFlyout(AnimationType.FADE) {
            flyout.updateData(message) { showFlyout(AnimationType.FADE, onEnd) }
        }
    }

    fun updateFlyoutWhileExpanding(message: BubbleBarFlyoutMessage) {
        val flyout = flyout ?: return
        flyout.updateData(message) {}
    }

    fun updateFlyoutWhileCollapsing(message: BubbleBarFlyoutMessage, onEnd: () -> Unit) {
        val flyout = flyout ?: return
        animator?.pause()
        animator?.removeAllListeners()
        flyout.updateData(message) { showFlyout(AnimationType.MORPH, onEnd) }
    }

    fun cancelFlyout(endAction: () -> Unit) {
        hideFlyout(AnimationType.FADE) {
            cleanupFlyoutView()
            endAction()
        }
    }

    fun collapseFlyout(endAction: () -> Unit) {
        hideFlyout(AnimationType.MORPH) {
            cleanupFlyoutView()
            endAction()
        }
    }

    private fun hideFlyout(animationType: AnimationType, endAction: () -> Unit) {
        val flyout = this.flyout ?: return
        val startValue = getCurrentAnimatedValueIfRunning() ?: 1f
        val duration = (COLLAPSE_ANIMATION_DURATION_MS * startValue).toLong()
        animator?.cancel()
        val animator = ValueAnimator.ofFloat(startValue, 0f).setDuration(duration)
        animator.interpolator = Interpolators.EMPHASIZED
        this.animator = animator
        when (animationType) {
            AnimationType.FADE ->
                animator.addUpdateListener { _ -> flyout.alpha = animator.animatedValue as Float }
            AnimationType.MORPH ->
                animator.addUpdateListener { _ ->
                    flyout.updateExpansionProgress(animator.animatedValue as Float)
                }
        }
        animator.addListener(
            onStart = {
                flyout.setOnClickListener(null)
                if (animationType == AnimationType.MORPH) {
                    flyout.updateTranslationToCollapsedPosition()
                }
            },
            onEnd = { endAction() },
        )
        animator.start()
    }

    private fun cleanupFlyoutView() {
        container.removeView(flyout)
        this@BubbleBarFlyoutController.flyout = null
    }

    fun hasFlyout() = flyout != null

    private fun getCurrentAnimatedValueIfRunning(): Float? {
        val animator = animator ?: return null
        return if (animator.isRunning) animator.animatedValue as Float else null
    }
}
