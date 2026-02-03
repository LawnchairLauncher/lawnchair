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

package com.android.wm.shell.desktopmode

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.util.TypedValue
import android.view.Choreographer
import android.view.SurfaceControl.Transaction
import android.view.WindowManager
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.core.animation.addListener
import com.android.app.animation.Interpolators
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_CLOSE_TASK
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.transition.Transitions
import java.util.function.Supplier

/** The [Transitions.TransitionHandler] that handles transitions for closing desktop mode tasks. */
class CloseDesktopTaskTransitionHandler
@JvmOverloads
constructor(
    private val context: Context,
    private val mainExecutor: ShellExecutor,
    private val animExecutor: ShellExecutor,
    private val transactionSupplier: Supplier<Transaction> = Supplier { Transaction() },
    private val animHandler: Handler,
) : Transitions.TransitionHandler {

    private val runningAnimations = mutableMapOf<IBinder, List<Animator>>()
    private val interactionJankMonitor = InteractionJankMonitor.getInstance()

    /** Returns null, as it only handles transitions started from Shell. */
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        if (info.type != WindowManager.TRANSIT_CLOSE) return false
        val animations = mutableListOf<Animator>()
        val onAnimFinish: (Animator) -> Unit = { animator ->
            mainExecutor.execute {
                // Animation completed
                animations.remove(animator)
                if (animations.isEmpty()) {
                    // All animations completed, finish the transition
                    runningAnimations.remove(transition)
                    finishCallback.onTransitionFinished(/* wct= */ null)
                    interactionJankMonitor.end(CUJ_DESKTOP_MODE_CLOSE_TASK)
                }
            }
        }
        val closingChanges =
            info.changes.filter {
                it.mode == WindowManager.TRANSIT_CLOSE &&
                    it.taskInfo?.windowingMode == WINDOWING_MODE_FREEFORM
            }
        animations +=
            closingChanges.map { createCloseAnimation(it, finishTransaction, onAnimFinish) }
        if (animations.isEmpty()) return false
        runningAnimations[transition] = animations
        closingChanges.lastOrNull()?.leash?.let { lastChangeLeash ->
            interactionJankMonitor.begin(
                lastChangeLeash,
                context,
                animHandler,
                CUJ_DESKTOP_MODE_CLOSE_TASK,
            )
        }
        animExecutor.execute { animations.forEach(Animator::start) }
        return true
    }

    private fun createCloseAnimation(
        change: TransitionInfo.Change,
        finishTransaction: Transaction,
        onAnimFinish: (Animator) -> Unit,
    ): Animator {
        finishTransaction.hide(change.leash)
        return AnimatorSet().apply {
            playTogether(createBoundsCloseAnimation(change), createAlphaCloseAnimation(change))
            addListener(onEnd = onAnimFinish)
        }
    }

    private fun createBoundsCloseAnimation(change: TransitionInfo.Change): Animator {
        val startBounds = change.startAbsBounds
        val endBounds =
            Rect(startBounds).apply {
                // Scale the end bounds of the window down with an anchor in the center
                inset(
                    (startBounds.width().toFloat() * (1 - CLOSE_ANIM_SCALE) / 2).toInt(),
                    (startBounds.height().toFloat() * (1 - CLOSE_ANIM_SCALE) / 2).toInt(),
                )
                val offsetY =
                    TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            CLOSE_ANIM_OFFSET_Y,
                            context.resources.displayMetrics,
                        )
                        .toInt()
                offset(/* dx= */ 0, offsetY)
            }
        return ValueAnimator.ofObject(RectEvaluator(), startBounds, endBounds).apply {
            duration = CLOSE_ANIM_DURATION_BOUNDS
            interpolator = Interpolators.STANDARD_ACCELERATE
            addUpdateListener { animation ->
                val animBounds = animation.animatedValue as Rect
                val animScale = 1 - (1 - CLOSE_ANIM_SCALE) * animation.animatedFraction
                transactionSupplier
                    .get()
                    .setPosition(change.leash, animBounds.left.toFloat(), animBounds.top.toFloat())
                    .setScale(change.leash, animScale, animScale)
                    .setFrameTimeline(Choreographer.getInstance().vsyncId)
                    .apply()
            }
        }
    }

    private fun createAlphaCloseAnimation(change: TransitionInfo.Change): Animator =
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = CLOSE_ANIM_DURATION_ALPHA
            interpolator = Interpolators.LINEAR
            addUpdateListener { animation ->
                transactionSupplier
                    .get()
                    .setAlpha(change.leash, animation.animatedValue as Float)
                    .apply()
            }
        }

    private companion object {
        const val CLOSE_ANIM_DURATION_BOUNDS = 200L
        const val CLOSE_ANIM_DURATION_ALPHA = 100L
        const val CLOSE_ANIM_SCALE = 0.95f
        const val CLOSE_ANIM_OFFSET_Y = 36.0f
    }
}
