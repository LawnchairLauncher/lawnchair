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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Point
import android.view.Choreographer
import android.view.SurfaceControl
import android.window.TransitionInfo
import androidx.core.animation.addListener
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.transition.Transitions

/**
 * A animator util to animate a task moving from desktop that interpolates between the start and end
 * bounds.
 */
class DesktopToFullscreenTaskAnimator(
    private val context: Context,
    private val transactionSupplier: () -> SurfaceControl.Transaction,
    private val displayController: DisplayController,
) {
    /** Starts the animation of [change]. */
    fun animate(
        change: TransitionInfo.Change,
        startTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
        onAnimationEnd: (() -> Unit)? = null,
        overrideStartPosition: Point? = null,
    ) {
        val task = change.taskInfo ?: error("Expected non-null task info")
        val context = displayController.getDisplayContext(task.displayId) ?: this.context
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val leash = change.leash
        val startBounds = change.startAbsBounds
        val endBounds = change.endAbsBounds
        val startPosition = overrideStartPosition ?: Point(startBounds.left, startBounds.top)
        val scaleX = startBounds.width().toFloat() / screenWidth
        val scaleY = startBounds.height().toFloat() / screenHeight

        // Hide the first (fullscreen) frame because the animation will start from the freeform
        // size.
        startTransaction
            .hide(leash)
            .setWindowCrop(leash, endBounds.width(), endBounds.height())
            .apply()

        val updateTransaction = transactionSupplier()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val currentPositionX = startPosition.x * (1 - fraction)
                val currentPositionY = startPosition.y * (1 - fraction)
                val currentScaleX = scaleX + ((1 - scaleX) * fraction)
                val currentScaleY = scaleY + ((1 - scaleY) * fraction)
                updateTransaction
                    .setPosition(leash, currentPositionX, currentPositionY)
                    .setScale(leash, currentScaleX, currentScaleY)
                    .show(leash)
                    .setFrameTimeline(Choreographer.getInstance().getVsyncId())
                    .apply()
            }
            addListener(
                onEnd = {
                    onAnimationEnd?.invoke()
                    finishCallback.onTransitionFinished(/* wct= */ null)
                }
            )
            start()
        }
    }

    companion object {
        private const val ANIMATION_DURATION_MS = 336L
    }
}
