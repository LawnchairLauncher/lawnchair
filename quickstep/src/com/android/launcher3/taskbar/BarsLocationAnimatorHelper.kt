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

package com.android.launcher3.taskbar

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import androidx.dynamicanimation.animation.SpringForce
import com.android.app.animation.Interpolators
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X
import com.android.launcher3.anim.SpringAnimationBuilder
import com.android.wm.shell.shared.bubbles.BubbleBarLocation

/** Animator helper that creates bars animators. */
object BarsLocationAnimatorHelper {
    const val FADE_OUT_ANIM_ALPHA_DURATION_MS: Long = 50L
    const val FADE_OUT_ANIM_ALPHA_DELAY_MS: Long = 50L
    const val FADE_OUT_ANIM_POSITION_DURATION_MS: Long = 100L
    const val FADE_IN_ANIM_ALPHA_DURATION_MS: Long = 100L

    // Use STIFFNESS_MEDIUMLOW which is not defined in the API constants
    private const val FADE_IN_ANIM_POSITION_SPRING_STIFFNESS: Float = 400f

    // During fade out animation we shift the bubble bar 1/80th of the screen width
    private const val FADE_OUT_ANIM_POSITION_SHIFT: Float = 1 / 80f

    // During fade in animation we shift the bubble bar 1/60th of the screen width
    private const val FADE_IN_ANIM_POSITION_SHIFT: Float = 1 / 60f

    private val Context.screenWidth: Int
        get() = resources.displayMetrics.widthPixels

    val Context.outShift: Float
        get() = screenWidth * FADE_OUT_ANIM_POSITION_SHIFT

    val Context.inShiftX: Float
        get() = screenWidth * FADE_IN_ANIM_POSITION_SHIFT

    /**
     * Creates out animation for targetView that animates it finalTx and plays targetViewAlphaAnim
     * to its final value.
     */
    private fun createLocationOutAnimator(
        finalTx: Float,
        targetViewAlphaAnim: ObjectAnimator,
        targetView: View,
    ): Animator {
        val positionAnim =
            ObjectAnimator.ofFloat(targetView, VIEW_TRANSLATE_X, finalTx)
                .setDuration(FADE_OUT_ANIM_POSITION_DURATION_MS)
        positionAnim.interpolator = Interpolators.EMPHASIZED_ACCELERATE

        targetViewAlphaAnim.setDuration(FADE_OUT_ANIM_ALPHA_DURATION_MS)
        targetViewAlphaAnim.startDelay = FADE_OUT_ANIM_ALPHA_DELAY_MS

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(positionAnim, targetViewAlphaAnim)
        return animatorSet
    }

    /**
     * Creates in animation for targetView that animates it from startTx to finalTx and plays
     * targetViewAlphaAnim to its final value.
     */
    private fun createLocationInAnimator(
        startTx: Float,
        finalTx: Float,
        targetViewAlphaAnim: ObjectAnimator,
        targetView: View,
    ): Animator {
        targetViewAlphaAnim.setDuration(FADE_IN_ANIM_ALPHA_DURATION_MS)
        val positionAnim: ValueAnimator =
            SpringAnimationBuilder(targetView.context)
                .setStartValue(startTx)
                .setEndValue(finalTx)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                .setStiffness(FADE_IN_ANIM_POSITION_SPRING_STIFFNESS)
                .build(targetView, VIEW_TRANSLATE_X)
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(positionAnim, targetViewAlphaAnim)
        return animatorSet
    }

    /** Creates an animator for the bubble bar view in part. */
    @JvmStatic
    fun getBubbleBarLocationInAnimator(
        newLocation: BubbleBarLocation,
        currentLocation: BubbleBarLocation,
        distanceFromOtherSide: Float,
        targetViewAlphaAnim: ObjectAnimator,
        bubbleBarView: View,
    ): Animator {
        val shift: Float = bubbleBarView.context.outShift

        val onLeft = newLocation.isOnLeft(bubbleBarView.isLayoutRtl)
        val startTx: Float
        val finalTx =
            if (newLocation == currentLocation) {
                // Animated location matches layout location.
                0f
            } else {
                // We are animating in to a transient location, need to move the bar
                // accordingly.
                distanceFromOtherSide * (if (onLeft) -1 else 1)
            }
        startTx =
            if (onLeft) {
                // Bar will be shown on the left side. Start point is shifted right.
                finalTx + shift
            } else {
                // Bar will be shown on the right side. Start point is shifted left.
                finalTx - shift
            }
        return createLocationInAnimator(startTx, finalTx, targetViewAlphaAnim, bubbleBarView)
    }

    /**
     * Creates an animator for the bubble bar view out part.
     *
     * @param targetLocation the location bubble bar should animate to.
     */
    @JvmStatic
    fun getBubbleBarLocationOutAnimator(
        bubbleBarView: View,
        targetLocation: BubbleBarLocation,
        targetViewAlphaAnim: ObjectAnimator,
    ): Animator {
        val onLeft = targetLocation.isOnLeft(bubbleBarView.isLayoutRtl)
        val shift = bubbleBarView.context.outShift
        val finalTx = bubbleBarView.translationX + (if (onLeft) -shift else shift)
        return this.createLocationOutAnimator(finalTx, targetViewAlphaAnim, bubbleBarView)
    }

    /** Creates a teleport animator for the navigation buttons view. */
    @JvmStatic
    fun getTeleportAnimatorForNavButtons(
        location: BubbleBarLocation,
        navButtonsView: View,
        navBarTargetTranslationX: Float,
    ): Animator {
        val outShift: Float = navButtonsView.context.outShift
        val isNavBarOnRight: Boolean = location.isOnLeft(navButtonsView.isLayoutRtl)
        val finalOutTx =
            navButtonsView.translationX + (if (isNavBarOnRight) outShift else -outShift)
        val fadeout: Animator =
            createLocationOutAnimator(
                finalOutTx,
                ObjectAnimator.ofFloat(navButtonsView, LauncherAnimUtils.VIEW_ALPHA, 0f),
                navButtonsView,
            )
        val inShift: Float = navButtonsView.context.inShiftX
        val inStartX = navBarTargetTranslationX + (if (isNavBarOnRight) -inShift else inShift)
        val fadeIn: Animator =
            createLocationInAnimator(
                inStartX,
                navBarTargetTranslationX,
                ObjectAnimator.ofFloat(navButtonsView, LauncherAnimUtils.VIEW_ALPHA, 1f),
                navButtonsView,
            )
        val teleportAnimator = AnimatorSet()
        teleportAnimator.play(fadeout).before(fadeIn)
        return teleportAnimator
    }
}
