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

import android.content.Context
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.TransitionInfo
import com.android.window.flags.Flags.enableDesktopWindowingWallpaperActivity
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.DesktopModeStatus
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.KtProtoLog

/**
 * A [Transitions.TransitionObserver] that observes shell transitions and updates
 * the [DesktopModeTaskRepository] state TODO: b/332682201
 * This observes transitions related to desktop mode
 * and other transitions that originate both within and outside shell.
 */
class DesktopTasksTransitionObserver(
    context: Context,
    private val desktopModeTaskRepository: DesktopModeTaskRepository,
    private val transitions: Transitions,
    shellInit: ShellInit
) : Transitions.TransitionObserver {

    init {
        if (Transitions.ENABLE_SHELL_TRANSITIONS &&
            DesktopModeStatus.canEnterDesktopMode(context)) {
            shellInit.addInitCallback(::onInit, this)
        }
    }

    fun onInit() {
        KtProtoLog.d(WM_SHELL_DESKTOP_MODE, "DesktopTasksTransitionObserver: onInit")
        transitions.registerObserver(this)
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction
    ) {
        // TODO: b/332682201 Update repository state
        updateWallpaperToken(info)
    }

    override fun onTransitionStarting(transition: IBinder) {
        // TODO: b/332682201 Update repository state
    }

    override fun onTransitionMerged(merged: IBinder, playing: IBinder) {
        // TODO: b/332682201 Update repository state
    }

    override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {
        // TODO: b/332682201 Update repository state
    }

    private fun updateWallpaperToken(info: TransitionInfo) {
        if (!enableDesktopWindowingWallpaperActivity()) {
            return
        }
        info.changes.forEach { change ->
            change.taskInfo?.let { taskInfo ->
                if (DesktopWallpaperActivity.isWallpaperTask(taskInfo)) {
                    when (change.mode) {
                        WindowManager.TRANSIT_OPEN ->
                            desktopModeTaskRepository.wallpaperActivityToken = taskInfo.token
                        WindowManager.TRANSIT_CLOSE ->
                            desktopModeTaskRepository.wallpaperActivityToken = null
                        else -> {}
                    }
                }
            }
        }
    }
}
