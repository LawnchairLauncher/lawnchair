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
import android.util.Size
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputDevice.SOURCE_TOUCHSCREEN
import android.view.MotionEvent
import android.view.MotionEvent.TOOL_TYPE_FINGER
import android.view.MotionEvent.TOOL_TYPE_MOUSE
import android.view.MotionEvent.TOOL_TYPE_STYLUS
import android.window.DesktopModeFlags
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.internal.util.FrameworkStatsLog
import com.android.wm.shell.EventLogTags
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import java.security.SecureRandom
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

/** Event logger for logging desktop mode session events */
class DesktopModeEventLogger {
    private val random: Random = SecureRandom()

    /** The session id for the current desktop mode session */
    @VisibleForTesting val currentSessionId: AtomicInteger = AtomicInteger(NO_SESSION_ID)

    private fun generateSessionId() = 1 + random.nextInt(1 shl 20)

    /** Logs enter into desktop mode with [enterReason] */
    fun logSessionEnter(enterReason: EnterReason) {
        val sessionId = generateSessionId()
        val previousSessionId = currentSessionId.getAndSet(sessionId)
        if (previousSessionId != NO_SESSION_ID) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: Existing desktop mode session id: %s found on desktop " +
                    "mode enter",
                previousSessionId,
            )
        }

        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging session enter, session: %s reason: %s",
            sessionId,
            enterReason.name,
        )
        FrameworkStatsLog.write(
            DESKTOP_MODE_ATOM_ID,
            /* event */ FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EVENT__ENTER,
            /* enterReason */ enterReason.reason,
            /* exitReason */ 0,
            /* session_id */ sessionId,
        )
        EventLogTags.writeWmShellEnterDesktopMode(enterReason.reason, sessionId)
    }

    /** Logs exit from desktop mode session with [exitReason] */
    fun logSessionExit(exitReason: ExitReason) {
        val sessionId = currentSessionId.getAndSet(NO_SESSION_ID)
        if (sessionId == NO_SESSION_ID) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: No session id found for logging exit from desktop mode",
            )
            return
        }

        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging session exit, session: %s reason: %s",
            sessionId,
            exitReason.name,
        )
        FrameworkStatsLog.write(
            DESKTOP_MODE_ATOM_ID,
            /* event */ FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EVENT__EXIT,
            /* enterReason */ 0,
            /* exitReason */ exitReason.reason,
            /* session_id */ sessionId,
        )
        EventLogTags.writeWmShellExitDesktopMode(exitReason.reason, sessionId)
    }

    /** Logs that a task with [taskUpdate] was added in a desktop mode session */
    fun logTaskAdded(taskUpdate: TaskUpdate) {
        val sessionId = currentSessionId.get()
        if (sessionId == NO_SESSION_ID) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: No session id found for logging task added",
            )
            return
        }

        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging task added, session: %s taskId: %s",
            sessionId,
            taskUpdate.instanceId,
        )
        logTaskUpdate(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_ADDED,
            sessionId,
            taskUpdate,
        )
    }

    /** Logs that a task with [taskUpdate] was removed from a desktop mode session */
    fun logTaskRemoved(taskUpdate: TaskUpdate) {
        val sessionId = currentSessionId.get()
        if (sessionId == NO_SESSION_ID) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: No session id found for logging task removed",
            )
            return
        }

        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging task remove, session: %s taskId: %s",
            sessionId,
            taskUpdate.instanceId,
        )
        logTaskUpdate(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_REMOVED,
            sessionId,
            taskUpdate,
        )
    }

    /** Logs that a task with [taskUpdate] had it's info changed in a desktop mode session */
    fun logTaskInfoChanged(taskUpdate: TaskUpdate) {
        val sessionId = currentSessionId.get()
        if (sessionId == NO_SESSION_ID) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: No session id found for logging task info changed",
            )
            return
        }

        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging task info changed, session: %s taskId: %s",
            sessionId,
            taskUpdate.instanceId,
        )
        logTaskUpdate(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED,
            sessionId,
            taskUpdate,
        )
    }

    /**
     * Logs that a task resize event is starting with [taskSizeUpdate] within a Desktop mode
     * session.
     */
    fun logTaskResizingStarted(
        resizeTrigger: ResizeTrigger,
        inputMethod: InputMethod,
        taskInfo: RunningTaskInfo,
        taskWidth: Int? = null,
        taskHeight: Int? = null,
        displayController: DisplayController? = null,
        displayLayoutSize: Size? = null,
    ) {
        if (!DesktopModeFlags.ENABLE_RESIZING_METRICS.isTrue) return

        val sessionId = currentSessionId.get()
        if (sessionId == NO_SESSION_ID) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: No session id found for logging start of task resizing",
            )
            return
        }

        val taskSizeUpdate =
            createTaskSizeUpdate(
                resizeTrigger,
                inputMethod,
                taskInfo,
                taskWidth,
                taskHeight,
                displayController = displayController,
                displayLayoutSize = displayLayoutSize,
            )

        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging task resize is starting, session: %s, taskSizeUpdate: %s",
            sessionId,
            taskSizeUpdate,
        )
        logTaskSizeUpdated(
            FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZING_STAGE__START_RESIZING_STAGE,
            sessionId,
            taskSizeUpdate,
        )
    }

    /**
     * Logs that a task resize event is ending with [taskSizeUpdate] within a Desktop mode session.
     */
    fun logTaskResizingEnded(
        resizeTrigger: ResizeTrigger,
        inputMethod: InputMethod,
        taskInfo: RunningTaskInfo,
        taskWidth: Int? = null,
        taskHeight: Int? = null,
        displayController: DisplayController? = null,
        displayLayoutSize: Size? = null,
    ) {
        if (!DesktopModeFlags.ENABLE_RESIZING_METRICS.isTrue) return

        val sessionId = currentSessionId.get()
        if (sessionId == NO_SESSION_ID) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: No session id found for logging end of task resizing",
            )
            return
        }

        val taskSizeUpdate =
            createTaskSizeUpdate(
                resizeTrigger,
                inputMethod,
                taskInfo,
                taskWidth,
                taskHeight,
                displayController,
                displayLayoutSize,
            )

        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: Logging task resize is ending, session: %s, taskSizeUpdate: %s",
            sessionId,
            taskSizeUpdate,
        )

        logTaskSizeUpdated(
            FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZING_STAGE__END_RESIZING_STAGE,
            sessionId,
            taskSizeUpdate,
        )
    }

    private fun createTaskSizeUpdate(
        resizeTrigger: ResizeTrigger,
        inputMethod: InputMethod,
        taskInfo: RunningTaskInfo,
        taskWidth: Int? = null,
        taskHeight: Int? = null,
        displayController: DisplayController? = null,
        displayLayoutSize: Size? = null,
    ): TaskSizeUpdate {
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds

        val height = taskHeight ?: taskBounds.height()
        val width = taskWidth ?: taskBounds.width()

        val displaySize =
            when {
                displayLayoutSize != null -> displayLayoutSize.height * displayLayoutSize.width
                displayController != null ->
                    displayController.getDisplayLayout(taskInfo.displayId)?.let {
                        it.height() * it.width()
                    }
                else -> null
            }

        return TaskSizeUpdate(
            resizeTrigger,
            inputMethod,
            taskInfo.taskId,
            taskInfo.effectiveUid,
            height,
            width,
            displaySize,
        )
    }

    fun logTaskInfoStateInit() {
        logTaskUpdate(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INIT_STATSD,
            sessionId = 0,
            TaskUpdate(
                visibleTaskCount = 0,
                instanceId = 0,
                uid = 0,
                taskHeight = 0,
                taskWidth = 0,
                taskX = 0,
                taskY = 0,
            ),
        )
    }

    private fun logTaskUpdate(taskEvent: Int, sessionId: Int, taskUpdate: TaskUpdate) {
        FrameworkStatsLog.write(
            DESKTOP_MODE_TASK_UPDATE_ATOM_ID,
            /* task_event */
            taskEvent,
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
            sessionId,
            taskUpdate.minimizeReason?.reason ?: UNSET_MINIMIZE_REASON,
            taskUpdate.unminimizeReason?.reason ?: UNSET_UNMINIMIZE_REASON,
            /* visible_task_count */
            taskUpdate.visibleTaskCount,
            taskUpdate.focusReason?.reason ?: UNSET_FOCUS_REASON,
        )
        EventLogTags.writeWmShellDesktopModeTaskUpdate(
            /* task_event */
            taskEvent,
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
            sessionId,
            taskUpdate.minimizeReason?.reason ?: UNSET_MINIMIZE_REASON,
            taskUpdate.unminimizeReason?.reason ?: UNSET_UNMINIMIZE_REASON,
            /* visible_task_count */
            taskUpdate.visibleTaskCount,
            taskUpdate.focusReason?.reason ?: UNSET_FOCUS_REASON,
        )
    }

    private fun logTaskSizeUpdated(
        resizingStage: Int,
        sessionId: Int,
        taskSizeUpdate: TaskSizeUpdate,
    ) {
        FrameworkStatsLog.write(
            DESKTOP_MODE_TASK_SIZE_UPDATED_ATOM_ID,
            /* resize_trigger */
            taskSizeUpdate.resizeTrigger?.trigger ?: ResizeTrigger.UNKNOWN_RESIZE_TRIGGER.trigger,
            /* resizing_stage */
            resizingStage,
            /* input_method */
            taskSizeUpdate.inputMethod?.method ?: InputMethod.UNKNOWN_INPUT_METHOD.method,
            /* desktop_mode_session_id */
            sessionId,
            /* instance_id */
            taskSizeUpdate.instanceId,
            /* uid */
            taskSizeUpdate.uid,
            /* task_height */
            taskSizeUpdate.taskHeight,
            /* task_width */
            taskSizeUpdate.taskWidth,
            /* display_area */
            taskSizeUpdate.displayArea ?: -1,
        )
    }

    companion object {

        /**
         * Describes a task position and dimensions.
         *
         * @property instanceId instance id of the task
         * @property uid uid of the app associated with the task
         * @property taskHeight height of the task in px
         * @property taskWidth width of the task in px
         * @property taskX x-coordinate of the top-left corner
         * @property taskY y-coordinate of the top-left corner
         * @property minimizeReason the reason the task was minimized
         * @property unminimizeReason the reason the task was unminimized
         * @property visibleTaskCount the number of visible tasks after this update
         * @property focusReason the reason the task was focused
         */
        data class TaskUpdate(
            val instanceId: Int,
            val uid: Int,
            val taskHeight: Int,
            val taskWidth: Int,
            val taskX: Int,
            val taskY: Int,
            val minimizeReason: MinimizeReason? = null,
            val unminimizeReason: UnminimizeReason? = null,
            val visibleTaskCount: Int,
            val focusReason: FocusReason? = null,
        )

        /**
         * Describes a task size update (resizing, snapping or maximizing to stable bounds).
         *
         * @property resizeTrigger the trigger for task resize
         * @property inputMethod the input method for resizing this task
         * @property instanceId instance id of the task
         * @property uid uid of the app associated with the task
         * @property taskHeight height of the task in dp
         * @property taskWidth width of the task in dp
         * @property displayArea the display size of the screen in dp
         */
        data class TaskSizeUpdate(
            val resizeTrigger: ResizeTrigger? = null,
            val inputMethod: InputMethod? = null,
            val instanceId: Int,
            val uid: Int,
            val taskHeight: Int,
            val taskWidth: Int,
            val displayArea: Int?,
        )

        @JvmStatic
        fun getInputMethodFromMotionEvent(e: MotionEvent?): InputMethod {
            if (e == null) return InputMethod.UNKNOWN_INPUT_METHOD

            val toolType = e.getToolType(e.findPointerIndex(e.getPointerId(0)))
            return when {
                toolType == TOOL_TYPE_STYLUS -> InputMethod.STYLUS
                toolType == TOOL_TYPE_MOUSE -> InputMethod.MOUSE
                toolType == TOOL_TYPE_FINGER && e.source == SOURCE_MOUSE -> InputMethod.TOUCHPAD
                toolType == TOOL_TYPE_FINGER && e.source == SOURCE_TOUCHSCREEN -> InputMethod.TOUCH
                else -> InputMethod.UNKNOWN_INPUT_METHOD
            }
        }

        // Default value used when the task was not minimized.
        @VisibleForTesting
        const val UNSET_MINIMIZE_REASON =
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__MINIMIZE_REASON__UNSET_MINIMIZE

        /** The reason a task was minimized. */
        enum class MinimizeReason(val reason: Int) {
            TASK_LIMIT(
                FrameworkStatsLog
                    .DESKTOP_MODE_SESSION_TASK_UPDATE__MINIMIZE_REASON__MINIMIZE_TASK_LIMIT
            ),
            MINIMIZE_BUTTON(
                FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__MINIMIZE_REASON__MINIMIZE_BUTTON
            ),
            KEY_GESTURE(
                FrameworkStatsLog
                    .DESKTOP_MODE_SESSION_TASK_UPDATE__MINIMIZE_REASON__MINIMIZE_KEY_GESTURE
            ),
        }

        // Default value used when the task was not unminimized.
        @VisibleForTesting
        const val UNSET_UNMINIMIZE_REASON =
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__UNMINIMIZE_REASON__UNSET_UNMINIMIZE

        /** The reason a task was unminimized. */
        enum class UnminimizeReason(val reason: Int) {
            UNKNOWN(
                FrameworkStatsLog
                    .DESKTOP_MODE_SESSION_TASK_UPDATE__UNMINIMIZE_REASON__UNMINIMIZE_UNKNOWN
            ),
            TASKBAR_TAP(
                FrameworkStatsLog
                    .DESKTOP_MODE_SESSION_TASK_UPDATE__UNMINIMIZE_REASON__UNMINIMIZE_TASKBAR_TAP
            ),
            ALT_TAB(
                FrameworkStatsLog
                    .DESKTOP_MODE_SESSION_TASK_UPDATE__UNMINIMIZE_REASON__UNMINIMIZE_ALT_TAB
            ),
            TASK_LAUNCH(
                FrameworkStatsLog
                    .DESKTOP_MODE_SESSION_TASK_UPDATE__UNMINIMIZE_REASON__UNMINIMIZE_TASK_LAUNCH
            ),
            APP_HANDLE_MENU_BUTTON(
                FrameworkStatsLog
                    .DESKTOP_MODE_SESSION_TASK_UPDATE__UNMINIMIZE_REASON__UNMINIMIZE_APP_HANDLE_MENU_BUTTON
            ),
            TASKBAR_MANAGE_WINDOW(
                FrameworkStatsLog
                    .DESKTOP_MODE_SESSION_TASK_UPDATE__UNMINIMIZE_REASON__UNMINIMIZE_TASKBAR_MANAGE_WINDOW
            ),
        }

        // Default value used when the task was not unminimized.
        @VisibleForTesting
        const val UNSET_FOCUS_REASON =
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__FOCUS_REASON__UNSET_FOCUS

        /** The reason a task was unminimized. */
        enum class FocusReason(val reason: Int) {
            UNKNOWN(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__FOCUS_REASON__FOCUS_UNKNOWN)
        }

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
            SCREEN_OFF(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__SCREEN_OFF),
            TASK_MINIMIZED(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__TASK_MINIMIZED),
            TASK_MOVED_TO_BACK(
                FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__TASK_MOVED_TO_BACK
            ),
        }

        /**
         * Enum ResizeTrigger mapped to the ResizeTrigger definition in
         * stats/atoms/desktopmode/desktopmode_extensions_atoms.proto
         */
        enum class ResizeTrigger(val trigger: Int) {
            UNKNOWN_RESIZE_TRIGGER(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__UNKNOWN_RESIZE_TRIGGER
            ),
            CORNER(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__CORNER_RESIZE_TRIGGER
            ),
            EDGE(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__EDGE_RESIZE_TRIGGER
            ),
            TILING_DIVIDER(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__TILING_DIVIDER_RESIZE_TRIGGER
            ),
            MAXIMIZE_BUTTON(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__MAXIMIZE_BUTTON_RESIZE_TRIGGER
            ),
            DOUBLE_TAP_APP_HEADER(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__DOUBLE_TAP_APP_HEADER_RESIZE_TRIGGER
            ),
            DRAG_LEFT(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__DRAG_LEFT_RESIZE_TRIGGER
            ),
            DRAG_RIGHT(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__DRAG_RIGHT_RESIZE_TRIGGER
            ),
            SNAP_LEFT_MENU(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__SNAP_LEFT_MENU_RESIZE_TRIGGER
            ),
            SNAP_RIGHT_MENU(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__SNAP_RIGHT_MENU_RESIZE_TRIGGER
            ),
            MAXIMIZE_MENU(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__MAXIMIZE_MENU_RESIZE_TRIGGER
            ),
            DRAG_TO_TOP_RESIZE_TRIGGER(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__DRAG_TO_TOP_RESIZE_TRIGGER
            ),
        }

        /**
         * Enum InputMethod mapped to the InputMethod definition in
         * stats/atoms/desktopmode/desktopmode_extensions_atoms.proto
         */
        enum class InputMethod(val method: Int) {
            UNKNOWN_INPUT_METHOD(
                FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__INPUT_METHOD__UNKNOWN_INPUT_METHOD
            ),
            TOUCH(
                FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__INPUT_METHOD__TOUCH_INPUT_METHOD
            ),
            STYLUS(
                FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__INPUT_METHOD__STYLUS_INPUT_METHOD
            ),
            MOUSE(
                FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__INPUT_METHOD__MOUSE_INPUT_METHOD
            ),
            TOUCHPAD(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__INPUT_METHOD__TOUCHPAD_INPUT_METHOD
            ),
            KEYBOARD(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__INPUT_METHOD__KEYBOARD_INPUT_METHOD
            ),
            ACCESSIBILITY(
                FrameworkStatsLog
                    .DESKTOP_MODE_TASK_SIZE_UPDATED__INPUT_METHOD__ACCESSIBILITY_INPUT_METHOD
            ),
        }

        private const val DESKTOP_MODE_ATOM_ID = FrameworkStatsLog.DESKTOP_MODE_UI_CHANGED
        private const val DESKTOP_MODE_TASK_UPDATE_ATOM_ID =
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE
        private const val DESKTOP_MODE_TASK_SIZE_UPDATED_ATOM_ID =
            FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED
        @VisibleForTesting const val NO_SESSION_ID = 0
    }
}
