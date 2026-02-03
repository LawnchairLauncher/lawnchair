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
import android.view.Choreographer
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import androidx.core.animation.addListener
import com.android.internal.jank.Cuj
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_TOGGLE_RESIZE
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener
import java.util.function.Supplier

/** Handles the animation of quick resizing of desktop tasks. */
class ToggleResizeDesktopTaskTransitionHandler(
    private val transitions: Transitions,
    private val transactionSupplier: Supplier<SurfaceControl.Transaction>,
    private val interactionJankMonitor: InteractionJankMonitor,
) : Transitions.TransitionHandler {

    private val rectEvaluator = RectEvaluator(Rect())
    private lateinit var onTaskResizeAnimationListener: OnTaskResizeAnimationListener

    private var boundsAnimator: Animator? = null
    private var initialBounds: Rect? = null
    private var callback: (() -> Unit)? = null

    constructor(
        transitions: Transitions,
        interactionJankMonitor: InteractionJankMonitor,
    ) : this(transitions, Supplier { SurfaceControl.Transaction() }, interactionJankMonitor)

    /**
     * Starts a quick resize transition.
     *
     * @param wct WindowContainerTransaction that will update core about the task changes applied
     * @param taskLeashBounds current bounds of the task leash (Note: not guaranteed to be the
     *   bounds of the actual task). This is provided so that the animation resizing can begin where
     *   the task leash currently is for smoother UX.
     */
    fun startTransition(
        wct: WindowContainerTransaction,
        taskLeashBounds: Rect? = null,
        callback: (() -> Unit)? = null,
    ) {
        transitions.startTransition(TRANSIT_DESKTOP_MODE_TOGGLE_RESIZE, wct, this)
        initialBounds = taskLeashBounds
        this.callback = callback
    }

    fun setOnTaskResizeAnimationListener(listener: OnTaskResizeAnimationListener) {
        onTaskResizeAnimationListener = listener
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val change = findRelevantChange(info)
        val leash = change.leash
        val taskId = checkNotNull(change.taskInfo).taskId
        val startBounds = initialBounds ?: change.startAbsBounds
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
                                    startBounds.top.toFloat(),
                                )
                                .setWindowCrop(leash, startBounds.width(), startBounds.height())
                                .show(leash)
                            onTaskResizeAnimationListener.onAnimationStart(
                                taskId,
                                startTransaction,
                                startBounds,
                            )
                        },
                        onEnd = {
                            finishTransaction
                                .setPosition(
                                    leash,
                                    endBounds.left.toFloat(),
                                    endBounds.top.toFloat(),
                                )
                                .setWindowCrop(leash, endBounds.width(), endBounds.height())
                                .show(leash)
                            onTaskResizeAnimationListener.onAnimationEnd(taskId)
                            finishCallback.onTransitionFinished(null)
                            initialBounds = null
                            boundsAnimator = null
                            interactionJankMonitor.end(Cuj.CUJ_DESKTOP_MODE_MAXIMIZE_WINDOW)
                            interactionJankMonitor.end(Cuj.CUJ_DESKTOP_MODE_UNMAXIMIZE_WINDOW)
                            interactionJankMonitor.end(Cuj.CUJ_DESKTOP_MODE_SNAP_RESIZE)
                            callback?.invoke()
                            callback = null
                        },
                    )
                    addUpdateListener { anim ->
                        val rect = anim.animatedValue as Rect
                        tx.setPosition(leash, rect.left.toFloat(), rect.top.toFloat())
                            .setWindowCrop(leash, rect.width(), rect.height())
                            .show(leash)
                            .setFrameTimeline(Choreographer.getInstance().getVsyncId())
                        onTaskResizeAnimationListener.onBoundsChange(taskId, tx, rect)
                    }
                    start()
                }
        return true
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
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

    private fun isWallpaper(change: TransitionInfo.Change): Boolean =
        (change.flags and TransitionInfo.FLAG_IS_WALLPAPER) != 0

    private fun isValidTaskChange(change: TransitionInfo.Change): Boolean =
        change.taskInfo != null && change.taskInfo?.taskId != -1

    companion object {
        private const val RESIZE_DURATION_MS = 300L
    }
}
