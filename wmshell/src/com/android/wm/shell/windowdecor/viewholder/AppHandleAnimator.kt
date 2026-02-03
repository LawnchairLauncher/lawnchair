/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.animation.ObjectAnimator
import android.view.View
import android.view.View.Visibility
import android.view.animation.PathInterpolator
import android.widget.ImageButton
import androidx.core.animation.doOnEnd
import com.android.wm.shell.shared.animation.Interpolators

/**
 * Animates the Desktop View's app handle.
 */
class AppHandleAnimator(
    private val appHandleView: View,
    private val captionHandle: ImageButton,
) {
    companion object {
        //  Constants for animating the whole caption
        private const val APP_HANDLE_ALPHA_FADE_IN_ANIMATION_DURATION_MS: Long = 275L
        private const val APP_HANDLE_ALPHA_FADE_OUT_ANIMATION_DURATION_MS: Long = 340
        private val APP_HANDLE_ANIMATION_INTERPOLATOR = PathInterpolator(
            0.4f,
            0f,
            0.2f,
            1f
        )

        // Constants for animating the caption's handle
        private const val HANDLE_ANIMATION_DURATION: Long = 100
        private val HANDLE_ANIMATION_INTERPOLATOR = Interpolators.FAST_OUT_SLOW_IN
    }

    private var animator: ObjectAnimator? = null

    /** Animates the given caption view to the given visibility after a visibility change. */
    fun animateVisibilityChange(@Visibility visible: Int) {
        when (visible) {
            View.VISIBLE -> animateShowAppHandle()
            else -> animateHideAppHandle()
        }
    }

    /** Animate appearance/disappearance of caption's handle. */
    fun animateCaptionHandleAlpha(startValue: Float, endValue: Float) {
        cancel()
        animator = ObjectAnimator.ofFloat(captionHandle, View.ALPHA, startValue, endValue).apply {
            duration = HANDLE_ANIMATION_DURATION
            interpolator = HANDLE_ANIMATION_INTERPOLATOR
            start()
        }
    }

    private fun animateShowAppHandle() {
        cancel()
        appHandleView.alpha = 0f
        appHandleView.visibility = View.VISIBLE
        animator = ObjectAnimator.ofFloat(appHandleView, View.ALPHA, 1f).apply {
            duration = APP_HANDLE_ALPHA_FADE_IN_ANIMATION_DURATION_MS
            interpolator = APP_HANDLE_ANIMATION_INTERPOLATOR
            start()
        }
    }

    private fun animateHideAppHandle() {
        cancel()
        animator = ObjectAnimator.ofFloat(appHandleView, View.ALPHA, 0f).apply {
            duration = APP_HANDLE_ALPHA_FADE_OUT_ANIMATION_DURATION_MS
            interpolator = APP_HANDLE_ANIMATION_INTERPOLATOR
            doOnEnd {
                appHandleView.visibility = View.GONE
            }
            start()
        }
    }

    /**
     * Cancels any active animations.
     */
    fun cancel() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }
}
