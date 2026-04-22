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
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.SurfaceControl
import androidx.core.animation.addListener
import com.android.internal.jank.Cuj
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.windowdecor.OnTaskRepositionAnimationListener
import java.util.function.Supplier

/** Animates the task surface moving from its current drag position to its pre-drag position. */
class ReturnToDragStartAnimator(
    private val transactionSupplier: Supplier<SurfaceControl.Transaction>,
    private val interactionJankMonitor: InteractionJankMonitor,
) {
    private var boundsAnimator: Animator? = null
    private lateinit var taskRepositionAnimationListener: OnTaskRepositionAnimationListener

    constructor(
        interactionJankMonitor: InteractionJankMonitor
    ) : this(Supplier { SurfaceControl.Transaction() }, interactionJankMonitor)

    /** Sets a listener for the start and end of the reposition animation. */
    fun setTaskRepositionAnimationListener(listener: OnTaskRepositionAnimationListener) {
        taskRepositionAnimationListener = listener
    }

    /** Builds new animator and starts animation of task leash reposition. */
    fun start(
        taskId: Int,
        taskSurface: SurfaceControl,
        startBounds: Rect,
        endBounds: Rect,
        doOnEnd: (() -> Unit)? = null,
    ) {
        val tx = transactionSupplier.get()

        boundsAnimator?.cancel()
        boundsAnimator =
            ValueAnimator.ofObject(RectEvaluator(), startBounds, endBounds)
                .setDuration(RETURN_TO_DRAG_START_ANIMATION_MS)
                .apply {
                    addListener(
                        onStart = {
                            val startTransaction = transactionSupplier.get()
                            startTransaction
                                .setPosition(
                                    taskSurface,
                                    startBounds.left.toFloat(),
                                    startBounds.top.toFloat(),
                                )
                                .show(taskSurface)
                                .apply()
                            taskRepositionAnimationListener.onAnimationStart(taskId)
                        },
                        onEnd = {
                            val finishTransaction = transactionSupplier.get()
                            finishTransaction
                                .setPosition(
                                    taskSurface,
                                    endBounds.left.toFloat(),
                                    endBounds.top.toFloat(),
                                )
                                .show(taskSurface)
                                .apply()
                            taskRepositionAnimationListener.onAnimationEnd(taskId)
                            boundsAnimator = null
                            doOnEnd?.invoke()
                            interactionJankMonitor.end(Cuj.CUJ_DESKTOP_MODE_SNAP_RESIZE)
                        },
                    )
                    addUpdateListener { anim ->
                        val rect = anim.animatedValue as Rect
                        tx.setPosition(taskSurface, rect.left.toFloat(), rect.top.toFloat())
                            .show(taskSurface)
                            .apply()
                    }
                }
                .also(ValueAnimator::start)
    }

    companion object {
        const val RETURN_TO_DRAG_START_ANIMATION_MS = 300L
    }
}
