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

package com.android.wm.shell.shared.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.view.Choreographer
import android.view.SurfaceControl.Transaction
import android.window.TransitionInfo.Change
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_MINIMIZE_WINDOW
import com.android.internal.jank.InteractionJankMonitor

/** Creates minimization animation */
object MinimizeAnimator {

    private const val MINIMIZE_ANIM_ALPHA_DURATION_MS = 100L

    private val minimizeBoundsAnimationDef =
        WindowAnimator.BoundsAnimationParams(
            durationMs = 200,
            endOffsetYDp = 12f,
            endScale = 0.97f,
            interpolator = Interpolators.STANDARD_ACCELERATE,
        )

    /**
     * Creates a minimize animator for given task [Change].
     *
     * @param onAnimFinish finish-callback for the animation, note that this is called on the same
     * thread as the animation itself.
     * @param animationHandler the Handler that the animation is running on.
     */
    @JvmStatic
    fun create(
        context: Context,
        change: Change,
        transaction: Transaction,
        onAnimFinish: (Animator) -> Unit,
        interactionJankMonitor: InteractionJankMonitor,
        animationHandler: Handler,
    ): Animator {
        val boundsAnimator = WindowAnimator.createBoundsAnimator(
            context.resources.displayMetrics,
            minimizeBoundsAnimationDef,
            change,
            transaction,
        )
        val alphaAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = MINIMIZE_ANIM_ALPHA_DURATION_MS
            interpolator = Interpolators.LINEAR
            addUpdateListener { animation ->
                transaction
                    .setAlpha(change.leash, animation.animatedValue as Float)
                    .setFrameTimeline(Choreographer.getInstance().vsyncId)
                    .apply()
            }
        }
        val listener = object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {
                interactionJankMonitor.begin(
                    change.leash,
                    context,
                    animationHandler,
                    CUJ_DESKTOP_MODE_MINIMIZE_WINDOW,
                )
            }
            override fun onAnimationCancel(animator: Animator) {
                interactionJankMonitor.cancel(CUJ_DESKTOP_MODE_MINIMIZE_WINDOW)
            }
            override fun onAnimationRepeat(animator: Animator) = Unit
            override fun onAnimationEnd(animator: Animator) {
                interactionJankMonitor.end(CUJ_DESKTOP_MODE_MINIMIZE_WINDOW)
                onAnimFinish(animator)
            }
        }
        return AnimatorSet().apply {
            playTogether(boundsAnimator, alphaAnimator)
            addListener(listener)
        }
    }
}
