/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.common

import android.annotation.SuppressLint
import android.app.ActivityTaskManager
import android.app.IActivityTaskManager
import android.content.Context
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.os.IBinder
import android.os.RemoteException
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.DesktopModeKeyGestureHandler
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.transition.FocusTransitionObserver
import java.util.Optional

/** Handles key gesture events to quit currently focused app. */
@SuppressLint("MissingPermission")
class QuitFocusedAppKeyGestureHandler(
    val context: Context,
    inputManager: InputManager,
    val displayController: DisplayController,
    val lockTaskChangeListener: LockTaskChangeListener,
    val desktopModeKeyGestureHandler: Optional<DesktopModeKeyGestureHandler>,
    val activityTaskManagerService: IActivityTaskManager,
    val focusTransitionObserver: FocusTransitionObserver,
    @ShellMainThread val executor: ShellExecutor
) : InputManager.KeyGestureEventHandler {
    init {
        inputManager.registerKeyGestureEventHandler(
            listOf(KeyGestureEvent.KEY_GESTURE_TYPE_QUIT_FOCUSED_TASK),
            this
        )
    }

    override fun handleKeyGestureEvent(
        event: KeyGestureEvent,
        focusedToken: IBinder?
    ) {
        if (event.keyGestureType != KeyGestureEvent.KEY_GESTURE_TYPE_QUIT_FOCUSED_TASK) {
            logW("Unsupported key gesture received $event")
            return
        }
        if (event.action == KeyGestureEvent.ACTION_GESTURE_START || event.isCancelled) {
            logV("Quit focused app gesture not complete or cancelled")
            return
        }
        val handler = desktopModeKeyGestureHandler.orElse(null)
        if (handler != null && handler.quitFocusedDesktopTask()) {
            logV("Closed focused desktop task")
            return
        }
        if (lockTaskChangeListener.isTaskLocked) {
            logW("Device in lock task mode: Unable to quit")
            return
        }
        try {
            val focusedTaskId = focusTransitionObserver.globallyFocusedTaskId
            if (focusedTaskId == ActivityTaskManager.INVALID_TASK_ID) {
                logW("The global focused task not found: Unable to quit")
                return
            }
            activityTaskManagerService.removeTask(focusedTaskId)
        } catch (e: RemoteException) {
            logE("Unable to quit focused task $e")
        }
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL, "%s: $msg", TAG, *arguments)
    }

    private fun logE(msg: String, vararg arguments: Any?) {
        ProtoLog.e(WM_SHELL, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "QuitFocusedAppKeyGestureHandler"
    }
}