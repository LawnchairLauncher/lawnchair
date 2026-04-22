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

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.graphics.Rect
import android.os.IBinder
import android.view.DragEvent
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback

/** Transition handler for drag-and-drop (i.e., tab tear) transitions that occur in desktop mode. */
class DesktopModeDragAndDropTransitionHandler(
    private val transitions: Transitions,
    private val animatorHelper: DesktopModeDragAndDropAnimatorHelper,
) : Transitions.TransitionHandler {

    private val pendingTransitionTokens: MutableList<Pair<IBinder, DragEvent>> = mutableListOf()

    /**
     * Begin a transition when a [android.app.PendingIntent] is dropped without a window to accept
     * it.
     */
    fun handleDropEvent(wct: WindowContainerTransaction, dragEvent: DragEvent): IBinder {
        val token = transitions.startTransition(TRANSIT_OPEN, wct, this)
        pendingTransitionTokens.add(Pair(token, dragEvent))
        return token
    }

    /**
     * Starts the animation for a task transition.
     *
     * This function orchestrates the beginning of an animation for a task transition, which
     * involves hiding the task's leash, setting the initial crop, and initiating the animator.
     *
     * @param info The [TransitionInfo] object containing information about the transition.
     * @param dragEvent The [DragEvent] that triggered the transition, used to extract the initial
     *   dragged task bounds.
     * @param startTransaction The [WindowContainerTransaction] used to manipulate the window
     *   hierarchy, such as hiding the task's leash.
     * @param finishCallback The [TransitionFinishCallback] to be called when the animation
     *   finishes.
     * @return true if transition was handled, false if not (falls-back to default).
     */
    fun startAnimation(
        info: TransitionInfo,
        dragEvent: DragEvent,
        startTransaction: Transaction,
        finishCallback: TransitionFinishCallback,
    ): Boolean {
        val draggedTaskBounds = extractBounds(dragEvent)
        val change = findRelevantChange(info)
        val leash = change.leash
        val endBounds = change.endAbsBounds

        startTransaction
            .hide(leash)
            .setWindowCrop(leash, endBounds.width(), endBounds.height())
            .apply()

        val animator = animatorHelper.createAnimator(change, draggedTaskBounds, finishCallback)
        animator.start()
        return true
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: TransitionFinishCallback,
    ): Boolean {
        val dragEvent =
            pendingTransitionTokens.firstOrNull { it.first == transition }?.second ?: return false
        val result = startAnimation(info, dragEvent, startTransaction, finishCallback)
        pendingTransitionTokens.removeIf { it.first == transition }
        return result
    }

    private fun findRelevantChange(info: TransitionInfo): TransitionInfo.Change {
        val matchingChanges =
            info.changes.filter { change ->
                isValidTaskChange(change) && change.mode == TRANSIT_OPEN
            }
        if (matchingChanges.size != 1) {
            throw IllegalStateException(
                "Expected 1 relevant change but found: ${matchingChanges.size}"
            )
        }
        return matchingChanges.first()
    }

    private fun isValidTaskChange(change: TransitionInfo.Change): Boolean =
        change.taskInfo != null &&
            change.taskInfo?.taskId != -1 &&
            change.taskInfo?.windowingMode == WINDOWING_MODE_FREEFORM

    private fun extractBounds(dragEvent: DragEvent): Rect {
        return Rect(
            /* left= */ dragEvent.x.toInt(),
            /* top= */ dragEvent.y.toInt(),
            /* right= */ dragEvent.x.toInt() + dragEvent.dragSurface.width,
            /* bottom= */ dragEvent.y.toInt() + dragEvent.dragSurface.height,
        )
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        return null
    }
}
