/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.windowdecor

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.View.ALPHA
import android.view.View.SCALE_X
import android.view.View.SCALE_Y
import android.view.View.TRANSLATION_Y
import android.view.View.TRANSLATION_Z
import android.view.ViewGroup
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import com.android.wm.shell.R
import com.android.wm.shell.animation.Interpolators

/** Animates the Handle Menu opening. */
class HandleMenuAnimator(
    private val handleMenu: View,
    private val menuWidth: Int,
    private val captionHeight: Float
) {
    companion object {
        // Open animation constants
        private const val MENU_Y_TRANSLATION_OPEN_DURATION: Long = 150
        private const val HEADER_NONFREEFORM_SCALE_OPEN_DURATION: Long = 150
        private const val HEADER_FREEFORM_SCALE_OPEN_DURATION: Long = 217
        private const val HEADER_ELEVATION_OPEN_DURATION: Long = 83
        private const val HEADER_CONTENT_ALPHA_OPEN_DURATION: Long = 100
        private const val BODY_SCALE_OPEN_DURATION: Long = 180
        private const val BODY_ALPHA_OPEN_DURATION: Long = 150
        private const val BODY_ELEVATION_OPEN_DURATION: Long = 83
        private const val BODY_CONTENT_ALPHA_OPEN_DURATION: Long = 167

        private const val ELEVATION_OPEN_DELAY: Long = 33
        private const val HEADER_CONTENT_ALPHA_OPEN_DELAY: Long = 67
        private const val BODY_SCALE_OPEN_DELAY: Long = 50
        private const val BODY_ALPHA_OPEN_DELAY: Long = 133

        private const val HALF_INITIAL_SCALE: Float = 0.5f
        private const val NONFREEFORM_HEADER_INITIAL_SCALE_X: Float = 0.6f
        private const val NONFREEFORM_HEADER_INITIAL_SCALE_Y: Float = 0.05f

        // Close animation constants
        private const val HEADER_CLOSE_DELAY: Long = 20
        private const val HEADER_CLOSE_DURATION: Long = 50
        private const val HEADER_CONTENT_OPACITY_CLOSE_DELAY: Long = 25
        private const val HEADER_CONTENT_OPACITY_CLOSE_DURATION: Long = 25
        private const val BODY_CLOSE_DURATION: Long = 50
    }

    private val animators: MutableList<Animator> = mutableListOf()
    private var runningAnimation: AnimatorSet? = null

    private val appInfoPill: ViewGroup = handleMenu.requireViewById(R.id.app_info_pill)
    private val windowingPill: ViewGroup = handleMenu.requireViewById(R.id.windowing_pill)
    private val moreActionsPill: ViewGroup = handleMenu.requireViewById(R.id.more_actions_pill)

    /** Animates the opening of the handle menu. */
    fun animateOpen() {
        prepareMenuForAnimation()
        appInfoPillExpand()
        animateAppInfoPillOpen()
        animateWindowingPillOpen()
        animateMoreActionsPillOpen()
        runAnimations()
    }

    /**
     * Animates the opening of the handle menu. The caption handle in full screen and split screen
     * will expand until it assumes the shape of the app info pill. Then, the other two pills will
     * appear.
     */
    fun animateCaptionHandleExpandToOpen() {
        prepareMenuForAnimation()
        captionHandleExpandIntoAppInfoPill()
        animateAppInfoPillOpen()
        animateWindowingPillOpen()
        animateMoreActionsPillOpen()
        runAnimations()
    }

    /**
     * Animates the closing of the handle menu. The windowing and more actions pill vanish. Then,
     * the app info pill will collapse into the shape of the caption handle in full screen and split
     * screen.
     *
     * @param after runs after the animation finishes.
     */
    fun animateCollapseIntoHandleClose(after: Runnable) {
        appInfoCollapseToHandle()
        animateAppInfoPillFadeOut()
        windowingPillClose()
        moreActionsPillClose()
        runAnimations(after)
    }

    /**
     * Animates the closing of the handle menu. The windowing and more actions pill vanish. Then,
     * the app info pill will collapse into the shape of the caption handle in full screen and split
     * screen.
     *
     * @param after runs after animation finishes.
     *
     */
    fun animateClose(after: Runnable) {
        appInfoPillCollapse()
        animateAppInfoPillFadeOut()
        windowingPillClose()
        moreActionsPillClose()
        runAnimations(after)
    }

    /**
     * Prepares the handle menu for animation. Presets the opacity of necessary menu components.
     * Presets pivots of handle menu and body pills for scaling animation.
     */
    private fun prepareMenuForAnimation() {
        // Preset opacity
        appInfoPill.children.forEach { it.alpha = 0f }
        windowingPill.alpha = 0f
        moreActionsPill.alpha = 0f

        // Setup pivots.
        handleMenu.pivotX = menuWidth / 2f
        handleMenu.pivotY = 0f

        windowingPill.pivotX = menuWidth / 2f
        windowingPill.pivotY = appInfoPill.measuredHeight.toFloat()

        moreActionsPill.pivotX = menuWidth / 2f
        moreActionsPill.pivotY = appInfoPill.measuredHeight.toFloat()
    }

    private fun animateAppInfoPillOpen() {
        // Header Elevation Animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, TRANSLATION_Z, 1f).apply {
                startDelay = ELEVATION_OPEN_DELAY
                duration = HEADER_ELEVATION_OPEN_DURATION
            }

        // Content Opacity Animation
        appInfoPill.children.forEach {
            animators +=
                ObjectAnimator.ofFloat(it, ALPHA, 1f).apply {
                    startDelay = HEADER_CONTENT_ALPHA_OPEN_DELAY
                    duration = HEADER_CONTENT_ALPHA_OPEN_DURATION
                }
        }
    }

    private fun captionHandleExpandIntoAppInfoPill() {
        // Header scaling animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_X, NONFREEFORM_HEADER_INITIAL_SCALE_X, 1f)
                .apply { duration = HEADER_NONFREEFORM_SCALE_OPEN_DURATION }

        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_Y, NONFREEFORM_HEADER_INITIAL_SCALE_Y, 1f)
                .apply { duration = HEADER_NONFREEFORM_SCALE_OPEN_DURATION }

        // Downward y-translation animation
        val yStart: Float = -captionHeight / 2
        animators +=
            ObjectAnimator.ofFloat(handleMenu, TRANSLATION_Y, yStart, 0f).apply {
                duration = MENU_Y_TRANSLATION_OPEN_DURATION
            }
    }

    private fun appInfoPillExpand() {
        // Header scaling animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_X, HALF_INITIAL_SCALE, 1f).apply {
                duration = HEADER_FREEFORM_SCALE_OPEN_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_Y, HALF_INITIAL_SCALE, 1f).apply {
                duration = HEADER_FREEFORM_SCALE_OPEN_DURATION
            }
    }

    private fun animateWindowingPillOpen() {
        // Windowing X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, SCALE_X, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(windowingPill, SCALE_Y, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        // Windowing Opacity Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, ALPHA, 1f).apply {
                startDelay = BODY_ALPHA_OPEN_DELAY
                duration = BODY_ALPHA_OPEN_DURATION
            }

        // Windowing Elevation Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, TRANSLATION_Z, 1f).apply {
                startDelay = ELEVATION_OPEN_DELAY
                duration = BODY_ELEVATION_OPEN_DURATION
            }

        // Windowing Content Opacity Animation
        windowingPill.children.forEach {
            animators +=
                ObjectAnimator.ofFloat(it, ALPHA, 1f).apply {
                    startDelay = BODY_ALPHA_OPEN_DELAY
                    duration = BODY_CONTENT_ALPHA_OPEN_DURATION
                    interpolator = Interpolators.FAST_OUT_SLOW_IN
                }
        }
    }

    private fun animateMoreActionsPillOpen() {
        // More Actions X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, SCALE_X, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, SCALE_Y, HALF_INITIAL_SCALE, 1f).apply {
                startDelay = BODY_SCALE_OPEN_DELAY
                duration = BODY_SCALE_OPEN_DURATION
            }

        // More Actions Opacity Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, ALPHA, 1f).apply {
                startDelay = BODY_ALPHA_OPEN_DELAY
                duration = BODY_ALPHA_OPEN_DURATION
            }

        // More Actions Elevation Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, TRANSLATION_Z, 1f).apply {
                startDelay = ELEVATION_OPEN_DELAY
                duration = BODY_ELEVATION_OPEN_DURATION
            }

        // More Actions Content Opacity Animation
        moreActionsPill.children.forEach {
            animators +=
                ObjectAnimator.ofFloat(it, ALPHA, 1f).apply {
                    startDelay = BODY_ALPHA_OPEN_DELAY
                    duration = BODY_CONTENT_ALPHA_OPEN_DURATION
                    interpolator = Interpolators.FAST_OUT_SLOW_IN
                }
        }
    }

    private fun appInfoPillCollapse() {
        // Header scaling animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_X, 0f).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_Y, 0f).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }
    }

    private fun appInfoCollapseToHandle() {
        // Header X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_X, NONFREEFORM_HEADER_INITIAL_SCALE_X).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(appInfoPill, SCALE_Y, NONFREEFORM_HEADER_INITIAL_SCALE_Y).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }
        // Upward y-translation animation
        val yStart: Float = -captionHeight / 2
        animators +=
            ObjectAnimator.ofFloat(appInfoPill, TRANSLATION_Y, yStart).apply {
                startDelay = HEADER_CLOSE_DELAY
                duration = HEADER_CLOSE_DURATION
            }
    }

    private fun animateAppInfoPillFadeOut() {
        // Header Content Opacity Animation
        appInfoPill.children.forEach {
            animators +=
                ObjectAnimator.ofFloat(it, ALPHA, 0f).apply {
                    startDelay = HEADER_CONTENT_OPACITY_CLOSE_DELAY
                    duration = HEADER_CONTENT_OPACITY_CLOSE_DURATION
                }
        }
    }

    private fun windowingPillClose() {
        // Windowing X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, SCALE_X, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(windowingPill, SCALE_Y, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        // windowing Animation
        animators +=
            ObjectAnimator.ofFloat(windowingPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(windowingPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }
    }

    private fun moreActionsPillClose() {
        // More Actions X & Y Scaling Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, SCALE_X, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, SCALE_Y, HALF_INITIAL_SCALE).apply {
                duration = BODY_CLOSE_DURATION
            }

        // More Actions Opacity Animation
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }

        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, ALPHA, 0f).apply {
                duration = BODY_CLOSE_DURATION
            }

        // upward more actions pill y-translation animation
        val yStart: Float = -captionHeight / 2
        animators +=
            ObjectAnimator.ofFloat(moreActionsPill, TRANSLATION_Y, yStart).apply {
                duration = BODY_CLOSE_DURATION
            }
    }

    /**
     * Runs the list of hide animators concurrently.
     *
     * @param after runs after animation finishes.
     */
    private fun runAnimations(after: Runnable? = null) {
        runningAnimation?.apply {
            // Remove all listeners, so that after runnable isn't triggered upon cancel.
            removeAllListeners()
            // If an animation runs while running animation is triggered, gracefully cancel.
            cancel()
        }

        runningAnimation = AnimatorSet().apply {
            playTogether(animators)
            animators.clear()
            doOnEnd {
                after?.run()
                runningAnimation = null
            }
            start()
        }
    }
}
