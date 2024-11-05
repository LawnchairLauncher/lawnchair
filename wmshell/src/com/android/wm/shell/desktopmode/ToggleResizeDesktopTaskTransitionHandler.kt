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

package com.android.wm.shell.desktopmode

import android.animation.Animator
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.core.animation.addListener
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_DESKTOP_MODE_TOGGLE_RESIZE
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener
import java.util.function.Supplier

/** Handles the animation of quick resizing of desktop tasks. */
class ToggleResizeDesktopTaskTransitionHandler(
    private val transitions: Transitions,
    private val transactionSupplier: Supplier<SurfaceControl.Transaction>
) : Transitions.TransitionHandler {

    private val rectEvaluator = RectEvaluator(Rect())
    private lateinit var onTaskResizeAnimationListener: OnTaskResizeAnimationListener

    private var boundsAnimator: Animator? = null

    constructor(
        transitions: Transitions
    ) : this(transitions, Supplier { SurfaceControl.Transaction() })

    /** Starts a quick resize transition. */
    fun startTransition(wct: WindowContainerTransaction) {
        transitions.startTransition(TRANSIT_DESKTOP_MODE_TOGGLE_RESIZE, wct, this)
    }

    fun setOnTaskResizeAnimationListener(listener: OnTaskResizeAnimationListener) {
        onTaskResizeAnimationListener = listener
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback
    ): Boolean {
        val change = findRelevantChange(info)
        val leash = change.leash
        val taskId = checkNotNull(change.taskInfo).taskId
        val startBounds = change.startAbsBounds
        val endBounds = change.endAbsBounds

        val tx = transactionSupplier.get()
        boundsAnimator?.cancel()
        boundsAnimator =
            ValueAnimator.ofObject(rectEvaluator, startBounds, endBounds)
                .setDuration(RESIZE_DURATION_MS)
                .apply {
                    addListener(
                        onStart = {
                            startTransaction
                                .setPosition(
                                    leash,
                                    startBounds.left.toFloat(),
                                    startBounds.top.toFloat()
                                )
                                .setWindowCrop(leash, startBounds.width(), startBounds.height())
                                .show(leash)
                            onTaskResizeAnimationListener.onAnimationStart(
                                taskId,
                                startTransaction,
                                startBounds
                            )
                        },
                        onEnd = {
                            finishTransaction
                                .setPosition(
                                    leash,
                                    endBounds.left.toFloat(),
                                    endBounds.top.toFloat()
                                )
                                .setWindowCrop(leash, endBounds.width(), endBounds.height())
                                .show(leash)
                            onTaskResizeAnimationListener.onAnimationEnd(taskId)
                            finishCallback.onTransitionFinished(null)
                            boundsAnimator = null
                        }
                    )
                    addUpdateListener { anim ->
                        val rect = anim.animatedValue as Rect
                        tx.setPosition(leash, rect.left.toFloat(), rect.top.toFloat())
                            .setWindowCrop(leash, rect.width(), rect.height())
                            .show(leash)
                        onTaskResizeAnimationListener.onBoundsChange(taskId, tx, rect)
                    }
                    start()
                }
        return true
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo
    ): WindowContainerTransaction? {
        return null
    }

    private fun findRelevantChange(info: TransitionInfo): TransitionInfo.Change {
        val matchingChanges =
            info.changes.filter { c ->
                !isWallpaper(c) && isValidTaskChange(c) && c.mode == TRANSIT_CHANGE
            }
        if (matchingChanges.size != 1) {
            throw IllegalStateException(
                "Expected 1 relevant change but found: ${matchingChanges.size}"
            )
        }
        return matchingChanges.first()
    }

    private fun isWallpaper(change: TransitionInfo.Change): Boolean {
        return (change.flags and TransitionInfo.FLAG_IS_WALLPAPER) != 0
    }

    private fun isValidTaskChange(change: TransitionInfo.Change): Boolean {
        return change.taskInfo != null && change.taskInfo?.taskId != -1
    }

    companion object {
        private const val RESIZE_DURATION_MS = 300L
    }
}
