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

// Exports bubble task utilities (e.g., `isBubbleToFullscreen`) for Java interop.
@file:JvmName("BubbleTaskUtils")

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.util.BubbleUtils.getExitBubbleTransaction
import com.android.wm.shell.bubbles.util.BubbleUtils.isBubbleToFullscreen
import com.android.wm.shell.common.TaskStackListenerCallback
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.taskview.TaskViewTaskController
import dagger.Lazy
import java.util.Optional

/**
 * Listens for task stack changes and handles bubble interactions when activities are restarted.
 *
 * This class monitors task stack events to determine how bubbles should behave when their
 * associated activities are restarted. It handles scenarios where bubbles should be expanded
 * or moved to fullscreen based on the task's windowing mode.
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
        ProtoLog.d(
            WM_SHELL_BUBBLES_NOISY,
            "BubbleTaskStackListener.onActivityRestartAttempt(): taskId=%d",
            task.taskId)
        val taskId = task.taskId
        bubbleData.getBubbleInStackWithTaskId(taskId)?.let { bubble ->
            when {
                isBubbleToFullscreen(task) -> moveCollapsedInStackBubbleToFullscreen(bubble, task)
                isBubbleToSplit(task) -> return // skip split task restarts
                !isAppBubbleMovingToFront(task) -> selectAndExpandInStackBubble(bubble, task)
            }
        }
    }

    private fun isBubbleToSplit(task: ActivityManager.RunningTaskInfo): Boolean {
        return task.hasParentTask() && splitScreenController.get()
            .map { it.isTaskRootOrStageRoot(task.parentTaskId) }
            .orElse(false)
    }

    /**
     * Returns whether the given bubble task restart should move the app bubble to front
     * and be handled in DefaultMixedTransition#animateEnterBubblesFromBubble.
     * This occurs when a startActivity call resolves to an existing activity, causing the
     * task to move to front, and the mixed transition will then expand the bubble.
     */
    private fun isAppBubbleMovingToFront(task: ActivityManager.RunningTaskInfo): Boolean {
        return task.activityType == ACTIVITY_TYPE_STANDARD
                && bubbleController.shouldBeAppBubble(task)
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
        collapsedBubbleToFullscreenInternal(bubble, task)
    }

    /** Internal function to move a collapsed bubble to fullscreen task. */
    private fun collapsedBubbleToFullscreenInternal(
        bubble: Bubble,
        task: ActivityManager.RunningTaskInfo,
    ) {
        ProtoLog.d(
            WM_SHELL_BUBBLES_NOISY,
            "BubbleTaskStackListener.collapsedBubbleToFullscreenInternal(): taskId=%d",
            task.taskId)
        val taskViewTaskController: TaskViewTaskController = bubble.taskView.controller
        val taskOrganizer: ShellTaskOrganizer = taskViewTaskController.taskOrganizer

        val wct = getExitBubbleTransaction(task.token, bubble.taskView.captionInsetsOwner)
        taskOrganizer.applyTransaction(wct)

        taskViewTaskController.notifyTaskRemovalStarted(task)
    }
}
