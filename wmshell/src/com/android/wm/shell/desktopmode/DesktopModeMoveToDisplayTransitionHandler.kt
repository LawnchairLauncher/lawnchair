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

package com.android.wm.shell.desktopmode

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.os.Handler
import android.os.IBinder
import android.view.Choreographer
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_MOVE_WINDOW_TO_DISPLAY
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.animation.Interpolators
import com.android.wm.shell.transition.Transitions
import kotlin.time.Duration.Companion.milliseconds

/** Transition handler for moving a window to a different display. */
class DesktopModeMoveToDisplayTransitionHandler(
    private val animationTransaction: SurfaceControl.Transaction,
    private val interactionJankMonitor: InteractionJankMonitor,
    private val shellMainHandler: Handler,
    private val displayController: DisplayController,
) : Transitions.TransitionHandler {

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val changes = info.changes.filter { it.startDisplayId != it.endDisplayId }
        if (changes.isEmpty()) return false
        for (change in changes) {
            val endBounds = change.endAbsBounds
            // The position should be relative to the parent. For example, in ActivityEmbedding, the
            // leash surface for the embedded Activity is parented to the container.
            val endPosition = change.endRelOffset
            startTransaction
                .setPosition(change.leash, endPosition.x.toFloat(), endPosition.y.toFloat())
                .setWindowCrop(change.leash, endBounds.width(), endBounds.height())
        }
        startTransaction.apply()

        val animator = AnimatorSet()
        animator.playTogether(
            changes.map {
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = ANIM_DURATION.inWholeMilliseconds
                    interpolator = Interpolators.LINEAR
                    addUpdateListener { animation ->
                        animationTransaction
                            .setAlpha(it.leash, animation.animatedValue as Float)
                            .setFrameTimeline(Choreographer.getInstance().vsyncId)
                            .apply()
                    }
                }
            }
        )

        animator.addListener(
            object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    val displayContext =
                        displayController.getDisplayContext(changes[0].endDisplayId)
                    if (displayContext == null) return
                    interactionJankMonitor.begin(
                        changes[0].leash,
                        displayContext,
                        shellMainHandler,
                        CUJ_DESKTOP_MODE_MOVE_WINDOW_TO_DISPLAY,
                    )
                }

                override fun onAnimationEnd(animation: Animator) {
                    finishTransaction.apply()
                    finishCallback.onTransitionFinished(null)
                    interactionJankMonitor.end(CUJ_DESKTOP_MODE_MOVE_WINDOW_TO_DISPLAY)
                }

                override fun onAnimationCancel(animation: Animator) {
                    finishTransaction.apply()
                    finishCallback.onTransitionFinished(null)
                    interactionJankMonitor.cancel(CUJ_DESKTOP_MODE_MOVE_WINDOW_TO_DISPLAY)
                }

                override fun onAnimationRepeat(animation: Animator) = Unit
            }
        )
        animator.start()
        return true
    }

    private companion object {
        val ANIM_DURATION = 100.milliseconds
    }
}
