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

package com.android.wm.shell.windowdecor.tiling

import android.os.IBinder
import android.view.SurfaceControl.Transaction
import android.window.TransitionInfo
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.transition.Transitions

class TilingDisplayReconnectEventHandler(
    private val desktopRepository: DesktopRepository,
    private val snapEventHandler: SnapEventHandler,
    private val transitions: Transitions,
    private val displayId: Int,
) : Transitions.TransitionObserver {
    data class TilingDisplayReconnectSession(
        val leftTiledTask: Int?,
        val rightTiledTask: Int?,
        val deskId: Int,
        val isDeskActive: Boolean,
    )

    // This is setup in [DesktopTasksController] whenever there is a restoreDisplay request.
    var activationBinder: IBinder? = null

    init {
        transitions.registerObserver(this)
    }

    val tilingSessions = ArrayList<TilingDisplayReconnectSession>()

    fun addTilingDisplayReconnectSession(tilingSession: TilingDisplayReconnectSession) {
        tilingSessions.add(tilingSession)
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
    ) {
        if (transition != activationBinder) return
        for (tilingSession in tilingSessions) {
            initRepositoryForTilingSession(tilingSession)
            if (tilingSession.isDeskActive) {
                snapEventHandler.onDeskActivated(tilingSession.deskId, displayId)
            }
        }
        transitions.unregisterObserver(this)
    }

    private fun initRepositoryForTilingSession(session: TilingDisplayReconnectSession) {
        if (session.leftTiledTask != null) {
            desktopRepository.addLeftTiledTaskToDesk(
                displayId,
                session.leftTiledTask,
                session.deskId,
            )
        }
        if (session.rightTiledTask != null) {
            desktopRepository.addRightTiledTaskToDesk(
                displayId,
                session.rightTiledTask,
                session.deskId,
            )
        }
    }
}
