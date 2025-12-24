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

package com.android.wm.shell.pinnedlayer.phone

import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * A pinned layer [Transitions.TransitionHandler] that handles [Transitions] and can start new
 * [Transitions] on the Shell request. It's responsible for managing PINNED layer that has PiP
 * policies like single window and always-on-top.
 */
class PinnedLayerController(shellInit: ShellInit, private val transitions: Transitions) :
    Transitions.TransitionHandler {

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    private fun onInit() {
        transitions.addHandler(this)
    }

    /** @inheritDoc */
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        return null
    }

    /** @inheritDoc */
    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        return false
    }

    /**
     * Checks whether a task with [taskId] is pinned.
     *
     * @param taskId - an id of task to check for pinned state.
     * @return true - when the task is pinned, false otherwise.
     */
    fun isPinned(taskId: Int): Boolean = false
}
