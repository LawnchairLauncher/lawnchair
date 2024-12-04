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

import com.android.internal.util.FrameworkStatsLog
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.util.KtProtoLog

/** Event logger for logging desktop mode session events */
class DesktopModeEventLogger {
    /**
     * Logs the enter of desktop mode having session id [sessionId] and the reason [enterReason] for
     * entering desktop mode
     */
    fun logSessionEnter(sessionId: Int, enterReason: EnterReason) {
        KtProtoLog.v(
            ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging session enter, session: %s reason: %s",
            sessionId,
            enterReason.name
        )
        FrameworkStatsLog.write(
            DESKTOP_MODE_ATOM_ID,
            /* event */ FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EVENT__ENTER,
            /* enterReason */ enterReason.reason,
            /* exitReason */ 0,
            /* session_id */ sessionId
        )
    }

    /**
     * Logs the exit of desktop mode having session id [sessionId] and the reason [exitReason] for
     * exiting desktop mode
     */
    fun logSessionExit(sessionId: Int, exitReason: ExitReason) {
        KtProtoLog.v(
            ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging session exit, session: %s reason: %s",
            sessionId,
            exitReason.name
        )
        FrameworkStatsLog.write(
            DESKTOP_MODE_ATOM_ID,
            /* event */ FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EVENT__EXIT,
            /* enterReason */ 0,
            /* exitReason */ exitReason.reason,
            /* session_id */ sessionId
        )
    }

    /**
     * Logs that the task with update [taskUpdate] was added in the desktop mode session having
     * session id [sessionId]
     */
    fun logTaskAdded(sessionId: Int, taskUpdate: TaskUpdate) {
        KtProtoLog.v(
            ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging task added, session: %s taskId: %s",
            sessionId,
            taskUpdate.instanceId
        )
        FrameworkStatsLog.write(
            DESKTOP_MODE_TASK_UPDATE_ATOM_ID,
            /* task_event */
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_ADDED,
            /* instance_id */
            taskUpdate.instanceId,
            /* uid */
            taskUpdate.uid,
            /* task_height */
            taskUpdate.taskHeight,
            /* task_width */
            taskUpdate.taskWidth,
            /* task_x */
            taskUpdate.taskX,
            /* task_y */
            taskUpdate.taskY,
            /* session_id */
            sessionId
        )
    }

    /**
     * Logs that the task with update [taskUpdate] was removed in the desktop mode session having
     * session id [sessionId]
     */
    fun logTaskRemoved(sessionId: Int, taskUpdate: TaskUpdate) {
        KtProtoLog.v(
            ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging task remove, session: %s taskId: %s",
            sessionId,
            taskUpdate.instanceId
        )
        FrameworkStatsLog.write(
            DESKTOP_MODE_TASK_UPDATE_ATOM_ID,
            /* task_event */
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_REMOVED,
            /* instance_id */
            taskUpdate.instanceId,
            /* uid */
            taskUpdate.uid,
            /* task_height */
            taskUpdate.taskHeight,
            /* task_width */
            taskUpdate.taskWidth,
            /* task_x */
            taskUpdate.taskX,
            /* task_y */
            taskUpdate.taskY,
            /* session_id */
            sessionId
        )
    }

    /**
     * Logs that the task with update [taskUpdate] had it's info changed in the desktop mode session
     * having session id [sessionId]
     */
    fun logTaskInfoChanged(sessionId: Int, taskUpdate: TaskUpdate) {
        KtProtoLog.v(
            ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging task info changed, session: %s taskId: %s",
            sessionId,
            taskUpdate.instanceId
        )
        FrameworkStatsLog.write(
            DESKTOP_MODE_TASK_UPDATE_ATOM_ID,
            /* task_event */
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED,
            /* instance_id */
            taskUpdate.instanceId,
            /* uid */
            taskUpdate.uid,
            /* task_height */
            taskUpdate.taskHeight,
            /* task_width */
            taskUpdate.taskWidth,
            /* task_x */
            taskUpdate.taskX,
            /* task_y */
            taskUpdate.taskY,
            /* session_id */
            sessionId
        )
    }

    companion object {
        data class TaskUpdate(
            val instanceId: Int,
            val uid: Int,
            val taskHeight: Int = Int.MIN_VALUE,
            val taskWidth: Int = Int.MIN_VALUE,
            val taskX: Int = Int.MIN_VALUE,
            val taskY: Int = Int.MIN_VALUE,
        )

        /**
         * Enum EnterReason mapped to the EnterReason definition in
         * stats/atoms/desktopmode/desktopmode_extensions_atoms.proto
         */
        enum class EnterReason(val reason: Int) {
            UNKNOWN_ENTER(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__UNKNOWN_ENTER),
            OVERVIEW(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__OVERVIEW),
            APP_HANDLE_DRAG(
                FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__APP_HANDLE_DRAG
            ),
            APP_HANDLE_MENU_BUTTON(
                FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__APP_HANDLE_MENU_BUTTON
            ),
            APP_FREEFORM_INTENT(
                FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__APP_FREEFORM_INTENT
            ),
            KEYBOARD_SHORTCUT_ENTER(
                FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__KEYBOARD_SHORTCUT_ENTER
            ),
            SCREEN_ON(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__SCREEN_ON),
            APP_FROM_OVERVIEW(
                FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__APP_FROM_OVERVIEW
            ),
        }

        /**
         * Enum ExitReason mapped to the ExitReason definition in
         * stats/atoms/desktopmode/desktopmode_extensions_atoms.proto
         */
        enum class ExitReason(val reason: Int) {
            UNKNOWN_EXIT(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__UNKNOWN_EXIT),
            DRAG_TO_EXIT(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__DRAG_TO_EXIT),
            APP_HANDLE_MENU_BUTTON_EXIT(
                FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__APP_HANDLE_MENU_BUTTON_EXIT
            ),
            KEYBOARD_SHORTCUT_EXIT(
                FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__KEYBOARD_SHORTCUT_EXIT
            ),
            RETURN_HOME_OR_OVERVIEW(
                FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__RETURN_HOME_OR_OVERVIEW
            ),
            TASK_FINISHED(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__TASK_FINISHED),
            SCREEN_OFF(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__SCREEN_OFF)
        }

        private const val DESKTOP_MODE_ATOM_ID = FrameworkStatsLog.DESKTOP_MODE_UI_CHANGED
        private const val DESKTOP_MODE_TASK_UPDATE_ATOM_ID =
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE
    }
}
