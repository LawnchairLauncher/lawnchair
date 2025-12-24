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

import android.os.IBinder
import android.view.SurfaceControl
import android.window.DesktopExperienceFlags
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.transition.Transitions

/** Handles the transition to drag a window to another display by dragging the caption. */
class WindowDragTransitionHandler(
    private val multiDisplayDragMoveIndicatorController: MultiDisplayDragMoveIndicatorController
) : Transitions.TransitionHandler {
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        return null
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        for (change in info.changes) {
            val sc = change.leash
            val endBounds = change.endAbsBounds
            val endPosition = change.endRelOffset
            startTransaction
                .setWindowCrop(sc, endBounds.width(), endBounds.height())
                .setPosition(sc, endPosition.x.toFloat(), endPosition.y.toFloat())
            finishTransaction
                .setWindowCrop(sc, endBounds.width(), endBounds.height())
                .setPosition(sc, endPosition.x.toFloat(), endPosition.y.toFloat())
            if (DesktopExperienceFlags.ENABLE_WINDOW_DROP_SMOOTH_TRANSITION.isTrue) {
                change.taskInfo?.let { taskInfo ->
                    multiDisplayDragMoveIndicatorController.onDragEnd(
                        taskInfo.taskId,
                        finishTransaction,
                    )
                }
            }
        }

        startTransaction.apply()
        finishCallback.onTransitionFinished(null)
        return true
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        // Cleans up drag indicators when a transition is consumed or aborted.
        // TODO: b/444066236 - Track pending transitions to clear indicators for the correct task.
        // The taskId is not available in this callback, so we must dispose of all indicators
        // across all displays. This has a known side effect: in the rare edge case that a user is
        // dragging multiple windows simultaneously and one drag transition aborts, the indicators
        // for all dragged windows will be removed. This is an acceptable trade-off due to the low
        // probability of this scenario.
        if (DesktopExperienceFlags.ENABLE_WINDOW_DROP_SMOOTH_TRANSITION.isTrue) {
            finishTransaction?.let {
                multiDisplayDragMoveIndicatorController.disposeAllIndicators(finishTransaction)
            }
        }
    }
}
