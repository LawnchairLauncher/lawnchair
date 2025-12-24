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

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.Context
import android.hardware.input.InputManager
import android.hardware.input.InputManager.KeyGestureEventHandler
import android.hardware.input.KeyGestureEvent
import android.os.IBinder
import android.view.Display.DEFAULT_DISPLAY
import android.window.DesktopExperienceFlags
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel
import java.util.Optional

/** Handles key gesture events (keyboard shortcuts) in Desktop Mode. */
class DesktopModeKeyGestureHandler(
    private val context: Context,
    private val desktopModeWindowDecorViewModel: Optional<DesktopModeWindowDecorViewModel>,
    private val desktopTasksController: Optional<DesktopTasksController>,
    private val desktopUserRepositories: DesktopUserRepositories,
    inputManager: InputManager,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val focusTransitionObserver: FocusTransitionObserver,
    @ShellMainThread private val mainExecutor: ShellExecutor,
    private val displayController: DisplayController,
    private val desktopState: DesktopState,
    private val splitScreenController: Optional<SplitScreenController>,
) : KeyGestureEventHandler {

    init {
        if (desktopTasksController.isPresent && desktopModeWindowDecorViewModel.isPresent) {
            val supportedGestures =
                listOf(
                    KeyGestureEvent.KEY_GESTURE_TYPE_MOVE_TO_NEXT_DISPLAY,
                    KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_LEFT_FREEFORM_WINDOW,
                    KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_RIGHT_FREEFORM_WINDOW,
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAXIMIZE_FREEFORM_WINDOW,
                    KeyGestureEvent.KEY_GESTURE_TYPE_MINIMIZE_FREEFORM_WINDOW,
                    KeyGestureEvent.KEY_GESTURE_TYPE_SWITCH_TO_PREVIOUS_DESK,
                    KeyGestureEvent.KEY_GESTURE_TYPE_SWITCH_TO_NEXT_DESK,
                    KeyGestureEvent.KEY_GESTURE_TYPE_QUIT_FOCUSED_DESKTOP_TASK,
                    KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_FULLSCREEN,
                )
            inputManager.registerKeyGestureEventHandler(supportedGestures, this)
        }
    }

    override fun handleKeyGestureEvent(event: KeyGestureEvent, focusedToken: IBinder?) {
        when (event.keyGestureType) {
            KeyGestureEvent.KEY_GESTURE_TYPE_MOVE_TO_NEXT_DISPLAY -> {
                logV("Key gesture MOVE_TO_NEXT_DISPLAY is handled")
                getGloballyFocusedTaskToMoveToNextDisplay()?.let {
                    mainExecutor.execute {
                        desktopTasksController
                            .get()
                            .moveToNextDesktopDisplay(
                                taskId = it.taskId,
                                userId = desktopUserRepositories.current.userId,
                                enterReason = EnterReason.KEYBOARD_SHORTCUT_ENTER,
                            )
                    }
                }
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_SWITCH_TO_PREVIOUS_DESK -> {
                logV("Key gesture SWITCH_TO_PREVIOUS_DESK is handled")
                mainExecutor.execute {
                    desktopTasksController
                        .get()
                        .activatePreviousDesk(
                            displayId = focusTransitionObserver.globallyFocusedDisplayId,
                            userId = desktopUserRepositories.current.userId,
                            enterReason = EnterReason.KEYBOARD_SHORTCUT_ENTER,
                        )
                }
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_SWITCH_TO_NEXT_DESK -> {
                logV("Key gesture SWITCH_TO_NEXT_DESK is handled")
                mainExecutor.execute {
                    desktopTasksController
                        .get()
                        .activateNextDesk(
                            displayId = focusTransitionObserver.globallyFocusedDisplayId,
                            userId = desktopUserRepositories.current.userId,
                            enterReason = EnterReason.KEYBOARD_SHORTCUT_ENTER,
                        )
                }
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_LEFT_FREEFORM_WINDOW -> {
                logV("Key gesture SNAP_LEFT_FREEFORM_WINDOW is handled")
                getGloballyFocusedDesktopTask()?.let {
                    mainExecutor.execute {
                        desktopModeWindowDecorViewModel
                            .get()
                            .onSnapResize(
                                it.taskId,
                                true,
                                DesktopModeEventLogger.Companion.InputMethod.KEYBOARD,
                                /* fromMenu= */ false,
                            )
                    }
                }
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_RIGHT_FREEFORM_WINDOW -> {
                logV("Key gesture SNAP_RIGHT_FREEFORM_WINDOW is handled")
                getGloballyFocusedDesktopTask()?.let {
                    mainExecutor.execute {
                        desktopModeWindowDecorViewModel
                            .get()
                            .onSnapResize(
                                it.taskId,
                                false,
                                DesktopModeEventLogger.Companion.InputMethod.KEYBOARD,
                                /* fromMenu= */ false,
                            )
                    }
                }
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAXIMIZE_FREEFORM_WINDOW -> {
                logV("Key gesture TOGGLE_MAXIMIZE_FREEFORM_WINDOW is handled")
                getGloballyFocusedDesktopTask()?.let { taskInfo ->
                    val displayId = taskInfo.displayId
                    val displayLayout = displayController.getDisplayLayout(displayId)
                    if (displayLayout == null) {
                        logW("Display %d is not found, task displayId might be stale", displayId)
                        return
                    }
                    mainExecutor.execute {
                        desktopTasksController
                            .get()
                            .toggleDesktopTaskSize(
                                taskInfo,
                                ToggleTaskSizeInteraction(
                                    isMaximized = isTaskMaximized(taskInfo, displayLayout),
                                    source = ToggleTaskSizeInteraction.Source.KEYBOARD_SHORTCUT,
                                    inputMethod =
                                        DesktopModeEventLogger.Companion.InputMethod.KEYBOARD,
                                ),
                            )
                    }
                }
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_MINIMIZE_FREEFORM_WINDOW -> {
                logV("Key gesture MINIMIZE_FREEFORM_WINDOW is handled")
                getGloballyFocusedDesktopTask()?.let {
                    mainExecutor.execute {
                        desktopTasksController.get().minimizeTask(it, MinimizeReason.KEY_GESTURE)
                    }
                }
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_QUIT_FOCUSED_DESKTOP_TASK -> {
                logV("Key gesture KEY_GESTURE_TYPE_QUIT_FOCUSED_DESKTOP_TASK is handled")
                val focusedTask =
                    if (
                        DesktopExperienceFlags.CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT
                            .isTrue
                    ) {
                        getGloballyFocusedTaskToClose()
                    } else {
                        getGloballyFocusedDesktopTask().also { task ->
                            if (task != null) {
                                logV("Found focused desktop task %d to close", task.taskId)
                            } else {
                                logV(
                                    "Globally focused desktop task is not found to close. focusedDisplay=%d",
                                    focusTransitionObserver.globallyFocusedDisplayId,
                                )
                            }
                        }
                    } ?: return
                mainExecutor.execute {
                    desktopModeWindowDecorViewModel.get().closeTask(focusedTask)
                }
            }
            KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_FULLSCREEN -> {
                logV("Key gesture TOGGLE_FULLSCREEN is handled")
                mainExecutor.execute {
                    desktopTasksController
                        .get()
                        .toggleFocusedTaskFullscreenState(
                            displayId = focusTransitionObserver.globallyFocusedDisplayId,
                            userId = desktopUserRepositories.current.userId,
                            transitionSource = DesktopModeTransitionSource.KEYBOARD_SHORTCUT,
                        )
                }
            }
        }
    }

    /** Quits the focussed task in desktop mode */
    public fun quitFocusedDesktopTask(): Boolean {
        val focusedTask = getGloballyFocusedDesktopTask()
        if (focusedTask == null) {
            logV(
                "Globally focused desktop task is not found to close. focusedDisplayId=%d",
                focusTransitionObserver.globallyFocusedDisplayId,
            )
            return false
        }
        logV("Found focused desktop task %d to close", focusedTask.taskId)
        mainExecutor.execute { desktopModeWindowDecorViewModel.get().closeTask(focusedTask) }
        return true
    }

    //  TODO: b/364154795 - wait for the completion of moveToNextDisplay transition, otherwise it
    //  will pick a wrong task when a user quickly perform other actions with keyboard shortcuts
    //  after moveToNextDisplay, and move this to FocusTransitionObserver class.
    private fun getGloballyFocusedDesktopTask(): RunningTaskInfo? {
        if (
            !DesktopExperienceFlags.EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS.isTrue ||
                !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            return shellTaskOrganizer.getRunningTasks().find { taskInfo ->
                taskInfo.windowingMode == WINDOWING_MODE_FREEFORM &&
                    focusTransitionObserver.hasGlobalFocus(taskInfo)
            }
        }
        val repository = desktopUserRepositories.current
        return shellTaskOrganizer.getRunningTasks().find { taskInfo ->
            repository.isActiveTask(taskInfo.taskId) &&
                focusTransitionObserver.hasGlobalFocus(taskInfo)
        }
    }

    private fun getGloballyFocusedTaskToMoveToNextDisplay(): RunningTaskInfo? {
        getGloballyFocusedDesktopTask()?.let { desktopTask ->
            logV(
                "getGloballyFocusedTaskToMoveToNextDisplay: Found globally focused desktop task to move: %d",
                desktopTask.taskId,
            )
            return@getGloballyFocusedTaskToMoveToNextDisplay desktopTask
        }

        if (!DesktopExperienceFlags.MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE.isTrue) {
            logV(
                "getGloballyFocusedTaskToMoveToNextDisplay: Skip focusing fullscreen task because " +
                    "MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE is disabled"
            )
            return null
        }

        if (!desktopState.isProjectedMode()) {
            logV(
                "getGloballyFocusedTaskToMoveToNextDisplay: Skip focusing fullscreen task because the device is not " +
                    "in the projected mode"
            )
            return null
        }

        if (focusTransitionObserver.globallyFocusedDisplayId != DEFAULT_DISPLAY) {
            logV(
                "getGloballyFocusedTaskToMoveToNextDisplay: Skip focusing fullscreen task because the focused " +
                    "display is not default display"
            )
            return null
        }

        desktopTasksController
            .get()
            .getFocusedNonDesktopTasks(
                displayId = focusTransitionObserver.globallyFocusedDisplayId,
                userId = desktopUserRepositories.current.userId,
            )
            .find { it.windowingMode == WINDOWING_MODE_FULLSCREEN }
            ?.let { fullscreenTask ->
                logV(
                    "getGloballyFocusedTaskToMoveToNextDisplay: Found globally focused fullscreen task to move: %d",
                    fullscreenTask.taskId,
                )
                return@getGloballyFocusedTaskToMoveToNextDisplay fullscreenTask
            }

        logW(
            "No globally focused task to move: globallyFocusedTaskId=%d globallyFocusedDisplayId=%d",
            focusTransitionObserver.globallyFocusedTaskId,
            focusTransitionObserver.globallyFocusedDisplayId,
        )
        return null
    }

    private fun getGloballyFocusedTaskToClose(): RunningTaskInfo? {
        getGloballyFocusedDesktopTask()?.let { desktopTask ->
            logV("getGloballyFocusedTaskToClose: Found desktop task: %d", desktopTask.taskId)
            return@getGloballyFocusedTaskToClose desktopTask
        }
        val tasks =
            desktopTasksController
                .get()
                .getFocusedNonDesktopTasks(
                    displayId = focusTransitionObserver.globallyFocusedDisplayId,
                    userId = desktopUserRepositories.current.userId,
                )
        return when (tasks.size) {
            0 -> {
                logW(
                    "getGloballyFocusedTaskToClose: Task not found to close: " +
                        "globallyFocusedTaskId=%d globallyFocusedDisplayId=%d",
                    focusTransitionObserver.globallyFocusedTaskId,
                    focusTransitionObserver.globallyFocusedDisplayId,
                )
                null
            }
            1 -> {
                val task = tasks.single()
                if (task.windowingMode == WINDOWING_MODE_FULLSCREEN) {
                    logV("getGloballyFocusedTaskToClose: Found fullscreen task: %d", task.taskId)
                    task
                } else {
                    logW(
                        "getGloballyFocusedTaskToClose: Ignored focused single non-fullscreen " +
                            "task."
                    )
                    null
                }
            }
            2 -> {
                val task = DesktopTasksController.getSplitFocusedTask(tasks[0], tasks[1])
                if (task.windowingMode == WINDOWING_MODE_MULTI_WINDOW) {
                    logV("getGloballyFocusedTaskToClose: Found split screen task: %d", task.taskId)
                    task
                } else {
                    logW(
                        "getGloballyFocusedTaskToClose: Ignored focused pair non-split-screen " +
                            "tasks."
                    )
                    null
                }
            }
            else -> {
                logW("getGloballyFocusedTaskToClose: Ignored focused 3+ tasks.")
                null
            }
        }
    }

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopModeKeyGestureHandler"
    }
}
