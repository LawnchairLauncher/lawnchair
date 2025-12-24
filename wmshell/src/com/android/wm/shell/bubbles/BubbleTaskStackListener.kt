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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.util.BubbleUtils.getExitBubbleTransaction
import com.android.wm.shell.bubbles.util.BubbleUtils.isBubbleToFullscreen
import com.android.wm.shell.bubbles.util.BubbleUtils.isBubbleToSplit
import com.android.wm.shell.common.TaskStackListenerCallback
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.taskview.TaskViewTaskController
import dagger.Lazy
import java.util.Optional

/**
 * Listens for task stack changes to manage associated bubble interactions.
 *
 * This class monitors task stack events, including task restarts and movements to the front,
 * to determine how bubbles should behave. It handles scenarios where bubbles should be expanded
 * or moved to fullscreen based on the task's windowing mode. This includes skipping split
 * task restarts, as they are handled by the split screen controller.
 *
 * @property bubbleController The [BubbleController] to manage bubble promotions and expansions.
 * @property bubbleData The [BubbleData] to access and update bubble information.
 */
class BubbleTaskStackListener(
    private val bubbleController: BubbleController,
    private val bubbleData: BubbleData,
    private val splitScreenController: Lazy<Optional<SplitScreenController>>
) : TaskStackListenerCallback {

    override fun onActivityRestartAttempt(
        task: ActivityManager.RunningTaskInfo,
        homeTaskVisible: Boolean,
        clearedTask: Boolean,
        wasVisible: Boolean,
    ) {
        val taskId = task.taskId
        ProtoLog.d(
            WM_SHELL_BUBBLES_NOISY,
            "BubbleTaskStackListener.onActivityRestartAttempt(): taskId=%d",
            taskId)
        bubbleData.getBubbleInStackWithTaskId(taskId)?.let { bubble ->
            when {
                task.isBubbleToFullscreen() -> moveCollapsedInStackBubbleToFullscreen(bubble, task)
                task.isBubbleToSplit(splitScreenController) -> return // skip split task restarts
                !task.isAppBubbleMovingToFront() -> selectAndExpandInStackBubble(bubble, task)
            }
        }
    }

    override fun onTaskMovedToFront(task: ActivityManager.RunningTaskInfo) {
        val taskId = task.taskId
        ProtoLog.d(
            WM_SHELL_BUBBLES_NOISY,
            "BubbleTaskStackListener.onTaskMovedToFront(): taskId=%d",
            taskId)
        bubbleData.getBubbleInStackWithTaskId(taskId)?.let { bubble ->
            when {
                task.isBubbleToFullscreen() -> moveCollapsedInStackBubbleToFullscreen(bubble, task)
            }
        }
    }

    /**
     * Returns whether the given bubble task restart should move the app bubble to front
     * and be handled in DefaultMixedTransition#animateEnterBubblesFromBubble.
     * This occurs when a startActivity call resolves to an existing activity, causing the
     * task to move to front, and the mixed transition will then expand the bubble.
     */
    private fun ActivityManager.RunningTaskInfo?.isAppBubbleMovingToFront(): Boolean {
        return this?.activityType == ACTIVITY_TYPE_STANDARD
                && bubbleController.shouldBeAppBubble(this)
    }

    /** Selects and expands a bubble that is currently in the stack. */
    private fun selectAndExpandInStackBubble(
        bubble: Bubble,
        task: ActivityManager.RunningTaskInfo,
    ) {
        ProtoLog.d(
            WM_SHELL_BUBBLES,
            "selectAndExpandInStackBubble - taskId=%d selecting matching bubble=%s",
            task.taskId,
            bubble.key,
        )
        bubbleData.setSelectedBubbleAndExpandStack(bubble)
    }

    /** Moves a collapsed bubble that is currently in the stack to fullscreen. */
    private fun moveCollapsedInStackBubbleToFullscreen(
        bubble: Bubble,
        task: ActivityManager.RunningTaskInfo,
    ) {
        ProtoLog.d(
            WM_SHELL_BUBBLES,
            "moveCollapsedInStackBubbleToFullscreen - taskId=%d " +
                    "moving matching bubble=%s to fullscreen",
            task.taskId,
            bubble.key
        )
        val taskViewTaskController: TaskViewTaskController = bubble.taskView.controller
        val taskOrganizer: ShellTaskOrganizer = taskViewTaskController.taskOrganizer

        val wct = getExitBubbleTransaction(task.token, bubble.taskView.captionInsetsOwner)
        taskOrganizer.applyTransaction(wct)

        taskViewTaskController.notifyTaskRemovalStarted(task)
    }
}
