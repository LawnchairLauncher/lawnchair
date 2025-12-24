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
package com.android.wm.shell.desktopmode.animation

import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.SurfaceControl
import android.window.TransitionInfo
import androidx.core.animation.addListener
import com.android.wm.shell.shared.R
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener

/**
 * A animator util to animate a task moving to desktop that interpolates between the start and end
 * bounds.
 */
class EnterDesktopTaskAnimator(
    context: Context,
    private val transactionSupplier: () -> SurfaceControl.Transaction,
    private val onTaskResizeAnimationListener: OnTaskResizeAnimationListener?,
) {
    private val rectEvaluator = RectEvaluator()
    private val animationDuration =
        context.resources.getInteger(R.integer.to_desktop_animation_duration_ms)

    /** Starts the animation of [change]. */
    fun animate(
        change: TransitionInfo.Change,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
        onAnimationEnd: (() -> Unit)? = null,
    ) {
        val taskId = change.taskInfo?.taskId ?: error("Expected non-null taskId")
        val leash = change.leash
        val startBounds = change.startAbsBounds
        val endBounds = change.endAbsBounds
        val endOffset = change.endRelOffset

        startTransaction
            .setPosition(leash, startBounds.left.toFloat(), startBounds.top.toFloat())
            .setWindowCrop(leash, startBounds.width(), startBounds.height())
        onTaskResizeAnimationListener?.onAnimationStart(
            taskId = taskId,
            t = startTransaction,
            bounds = startBounds,
        ) ?: startTransaction.apply()

        val updateTransaction = transactionSupplier()
        ValueAnimator.ofObject(rectEvaluator, startBounds, endBounds).apply {
            duration = animationDuration.toLong()
            addListener(
                onEnd = {
                    finishTransaction
                        .setPosition(leash, endOffset.x.toFloat(), endOffset.y.toFloat())
                        .setWindowCrop(leash, endBounds.width(), endBounds.height())
                        .apply()
                    onTaskResizeAnimationListener?.onAnimationEnd(taskId)
                    finishCallback.onTransitionFinished(/* wct= */ null)
                    onAnimationEnd?.invoke()
                }
            )
            addUpdateListener { animation ->
                val rect = animation.animatedValue as Rect
                updateTransaction
                    .setPosition(leash, rect.left.toFloat(), rect.top.toFloat())
                    .setWindowCrop(leash, rect.width(), rect.height())
                    .apply()
                onTaskResizeAnimationListener?.onBoundsChange(
                    taskId = taskId,
                    t = updateTransaction,
                    bounds = rect,
                ) ?: updateTransaction.apply()
            }
            start()
        }
    }
}
