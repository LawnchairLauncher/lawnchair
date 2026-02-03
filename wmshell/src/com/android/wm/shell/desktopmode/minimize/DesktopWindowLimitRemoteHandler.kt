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

package com.android.wm.shell.desktopmode.minimize

import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.RemoteTransition
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.transition.OneShotRemoteHandler
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionHandler

/**
 * Handles transitions that result in hitting the Desktop window limit, by performing some
 * preparation work and then delegating to [remoteTransition].
 *
 * This transition handler prepares the leash of the minimizing change referenced by
 * [taskIdToMinimize], and then delegates to [remoteTransition] to perform the actual transition.
 */
class DesktopWindowLimitRemoteHandler(
    mainExecutor: ShellExecutor,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    remoteTransition: RemoteTransition,
    private val taskIdToMinimize: Int,
) : TransitionHandler {

    private val oneShotRemoteHandler = OneShotRemoteHandler(mainExecutor, remoteTransition)
    private var transition: IBinder? = null

    /** Sets the transition that will be handled - this must be called before [startAnimation]. */
    fun setTransition(transition: IBinder) {
        this.transition = transition
        oneShotRemoteHandler.setTransition(transition)
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        this.transition = transition
        return oneShotRemoteHandler.handleRequest(transition, request)
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        if (transition != this.transition) return false
        val minimizeChange = findMinimizeChange(info, taskIdToMinimize) ?: return false
        // Reparent the minimize change back to the root task display area, so the minimizing window
        // isn't shown in front of other windows. We do this here in Shell since Launcher doesn't
        // have access to RootTaskDisplayAreaOrganizer.
        applyMinimizeChangeReparenting(info, minimizeChange, startTransaction)
        return oneShotRemoteHandler.startAnimation(
            transition,
            info,
            startTransaction,
            finishTransaction,
            finishCallback,
        )
    }

    private fun applyMinimizeChangeReparenting(
        info: TransitionInfo,
        minimizeChange: Change,
        startTransaction: SurfaceControl.Transaction,
    ) {
        val taskInfo = minimizeChange.taskInfo ?: return
        if (taskInfo.isFreeform && TransitionUtil.isOpeningMode(info.type)) {
            rootTaskDisplayAreaOrganizer.reparentToDisplayArea(
                taskInfo.displayId,
                minimizeChange.leash,
                startTransaction,
            )
        }
    }

    private fun findMinimizeChange(info: TransitionInfo, taskIdToMinimize: Int): Change? =
        info.changes.find { change ->
            change.taskInfo?.taskId == taskIdToMinimize && change.mode == TRANSIT_TO_BACK
        }
}
