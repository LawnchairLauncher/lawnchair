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

package com.android.wm.shell.windowdecor.viewholder

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.ColorInt
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.SystemProperties
import android.view.View
import android.view.View.Visibility
import android.view.animation.PathInterpolator
import android.widget.ImageButton
import android.window.DesktopExperienceFlags
import androidx.core.animation.doOnEnd
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_WINDOW_DECORATION
import com.android.wm.shell.shared.animation.Interpolators

/** Animates the Desktop View's app handle. */
class AppHandleAnimator(appHandleView: View, private val captionHandle: ImageButton) {
    companion object {
        private val DEBUG_ANIMATOR_STEPS =
            SystemProperties.getBoolean(
                "persist.wm.debug.window_decoration_app_handle_visibility_anim_debug_steps",
                false,
            )

        //  Constants for animating the whole caption
        @VisibleForTesting
        val APP_HANDLE_ALPHA_FADE_IN_ANIMATION_DURATION_MS =
            SystemProperties.getLong(
                "persist.wm.debug.window_decoration_app_handle_fade_in_duration_ms",
                275L,
            )
        @VisibleForTesting
        val APP_HANDLE_ALPHA_FADE_OUT_ANIMATION_DURATION_MS =
            SystemProperties.getLong(
                "persist.wm.debug.window_decoration_app_handle_fade_out_duration_ms",
                340L,
            )
        private val APP_HANDLE_COLOR_ANIMATION_DURATION_MS =
            SystemProperties.getLong(
                "persist.wm.debug.window_decoration_app_handle_color_duration_ms",
                275L,
            )
        @VisibleForTesting
        val APP_HANDLE_FADE_ANIMATION_INTERPOLATOR = PathInterpolator(0.4f, 0f, 0.2f, 1f)

        // Constants for animating the caption's handle
        private const val HANDLE_ANIMATION_DURATION: Long = 100
        private val HANDLE_ANIMATION_INTERPOLATOR = Interpolators.FAST_OUT_SLOW_IN
    }

    private val visibilityAnimator = VisibilityAnimator(appHandleView)
    private val colorAnimator = ColorAnimator(captionHandle)
    private var animator: ObjectAnimator? = null

    /** Animates the app handle to the given visibility after a visibility change. */
    fun animateVisibilityChange(visible: Boolean) {
        cancelCaptionHandleAlphaAnimation()
        visibilityAnimator.animate(visible)
    }

    /** Animates the app handle to the given color. */
    fun animateColorChange(@ColorInt color: Int) {
        colorAnimator.animate(color)
    }

    /** Animate appearance/disappearance of caption's handle. */
    fun animateCaptionHandleAlpha(startValue: Float, endValue: Float) {
        // DrawingHandle's alpha change is handled by HandleMenuAnimator.
        if (DesktopExperienceFlags.ENABLE_DRAWING_APP_HANDLE.isTrue) {
            return
        }

        cancelCaptionHandleAlphaAnimation()
        visibilityAnimator.cancel()
        animator =
            ObjectAnimator.ofFloat(captionHandle, View.ALPHA, startValue, endValue).apply {
                duration = HANDLE_ANIMATION_DURATION
                interpolator = HANDLE_ANIMATION_INTERPOLATOR
                start()
            }
    }

    private fun cancelCaptionHandleAlphaAnimation() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }

    /** Cancels any active animations. */
    fun cancel() {
        if (DesktopExperienceFlags.ENABLE_REENABLE_APP_HANDLE_ANIMATIONS.isTrue) {
            visibilityAnimator.cancel()
            colorAnimator.cancel()
        }
        cancelCaptionHandleAlphaAnimation()
    }

    /** Returns the current visibility animator. */
    @VisibleForTesting fun getAnimator(): ValueAnimator? = visibilityAnimator.animator

    private class VisibilityAnimator(private val targetView: View) {
        private enum class Target(
            val start: Float,
            val end: Float,
            @Visibility val viewVisibility: Int,
            val duration: Long,
        ) {
            VISIBLE(0f, 1f, View.VISIBLE, APP_HANDLE_ALPHA_FADE_IN_ANIMATION_DURATION_MS),
            INVISIBLE(1f, 0f, View.GONE, APP_HANDLE_ALPHA_FADE_OUT_ANIMATION_DURATION_MS),
        }

        private var currentAnimator: ObjectAnimator? = null
        private var currentTarget: Target? = null
        private val doOnEnd = DoOnEnd(targetView)

        /** The current animator. */
        @VisibleForTesting
        val animator: ValueAnimator?
            get() = currentAnimator

        /** Animates the target view. */
        fun animate(visible: Boolean) {
            animate(if (visible) Target.VISIBLE else Target.INVISIBLE)
        }

        /** Cancels the ongoing animation. */
        fun cancel() {
            currentAnimator?.removeAllListeners()
            currentAnimator?.cancel()
            reset()
        }

        private fun animate(target: Target) {
            val inProgress = currentTarget
            logD("animate from=%s to=%s", inProgress?.name, target.name)
            when {
                inProgress == null -> {
                    // Not animating, animate from start if needed.
                    if (targetView.visibility == target.viewVisibility) {
                        logD("skipping animation, already at target")
                        return
                    } else {
                        logD("animating from start")
                        targetView.visibility = View.VISIBLE
                        targetView.alpha = target.start
                        doOnEnd.target = target
                        currentAnimator = newAnimator(target).apply { start() }
                    }
                }
                target == inProgress -> {
                    logD("skipping animation, already animating to target")
                    return
                }
                else -> {
                    logD("was animating to opposite target, reversing")
                    doOnEnd.target = target
                    currentAnimator?.reverse()
                }
            }
            currentTarget = target
        }

        @OptIn(ExperimentalStdlibApi::class)
        private fun newAnimator(target: Target): ObjectAnimator =
            ObjectAnimator.ofFloat(targetView, View.ALPHA, target.end).apply {
                duration = target.duration
                interpolator = APP_HANDLE_FADE_ANIMATION_INTERPOLATOR
                if (DEBUG_ANIMATOR_STEPS) {
                    addUpdateListener { animator ->
                        logD(
                            "update: animator=ObjectAnimator@%s f=%f alpha=%f",
                            animator.hashCode().toHexString(),
                            animator.animatedFraction,
                            animator.animatedValue,
                        )
                    }
                }
                doOnEnd(doOnEnd)
            }

        private fun reset() {
            currentAnimator = null
            currentTarget = null
        }

        private inner class DoOnEnd(private val targetView: View) : (Animator) -> Unit {
            var target: Target? = null

            override fun invoke(animator: Animator) {
                if (DEBUG_ANIMATOR_STEPS) {
                    logD("end: target=%s", target)
                }
                target?.let { targetView.visibility = it.viewVisibility }
                target = null
                reset()
            }
        }

        private fun logD(msg: String, vararg arguments: Any?) {
            ProtoLog.d(WM_SHELL_WINDOW_DECORATION, "%s: $msg", TAG, *arguments)
        }

        companion object {
            private const val TAG = "AppHandleVisibilityAnimator"
        }
    }

    private class ColorAnimator(private val targetView: ImageButton) {
        private var currentAnimator: ValueAnimator? = null
        @ColorInt private var currentTarget: Int? = null

        /** Animates the target view. */
        fun animate(@ColorInt color: Int) {
            val inProgress = currentTarget
            when {
                color == inProgress -> {
                    logD("skipping animation, already animating to target")
                    return
                }
                else -> {
                    if (inProgress != null) {
                        logD("was animating to other target, cancelling first")
                        currentAnimator?.apply {
                            removeAllListeners()
                            cancel()
                        }
                        currentAnimator = null
                    }
                    val fromColor = getCurrentColor()
                    if (fromColor == color) {
                        logD("skipping animation, already at target")
                        return
                    }
                    if (fromColor == null) {
                        logD("skipping animation, no current color to animate from")
                        targetView.imageTintList = ColorStateList.valueOf(color)
                        return
                    }
                    logD("animate from=%s to=%s", fromColor.toArgbString(), color.toArgbString())
                    currentAnimator = newAnimator(fromColor, color).apply { start() }
                }
            }
            currentTarget = color
        }

        /** Cancels the ongoing animation. */
        fun cancel() {
            currentAnimator?.removeAllListeners()
            currentAnimator?.cancel()
            reset()
        }

        private fun getCurrentColor() = targetView.imageTintList?.defaultColor

        private fun reset() {
            currentAnimator = null
            currentTarget = null
        }

        @OptIn(ExperimentalStdlibApi::class)
        private fun newAnimator(@ColorInt from: Int, @ColorInt to: Int): ValueAnimator =
            ValueAnimator.ofArgb(from, to)
                .setDuration(APP_HANDLE_COLOR_ANIMATION_DURATION_MS)
                .apply {
                    addUpdateListener { animator ->
                        targetView.imageTintList =
                            ColorStateList.valueOf(animator.animatedValue as Int)
                        if (DEBUG_ANIMATOR_STEPS) {
                            logD(
                                "update: animator=ValueAnimator@%s f=%f color=%f",
                                animator.hashCode().toHexString(),
                                animator.animatedFraction,
                                (animator.animatedValue as Int).toArgbString(),
                            )
                        }
                    }
                    doOnEnd { animator ->
                        if (DEBUG_ANIMATOR_STEPS) {
                            logD(
                                "end: animator=ValueAnimator@%s",
                                animator.hashCode().toHexString(),
                            )
                        }
                        reset()
                    }
                }

        private fun logD(msg: String, vararg arguments: Any?) {
            ProtoLog.d(WM_SHELL_WINDOW_DECORATION, "%s: $msg", TAG, *arguments)
        }

        private fun @receiver:ColorInt Int.toArgbString() =
            String.format(
                "#%02X%02X%02X%02X",
                Color.alpha(this),
                Color.red(this),
                Color.green(this),
                Color.blue(this),
            )

        companion object {
            private const val TAG = "AppHandleColorAnimator"
        }
    }
}
