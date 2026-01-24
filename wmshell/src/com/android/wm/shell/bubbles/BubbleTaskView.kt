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

package com.android.wm.shell.bubbles

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.content.ComponentName
import androidx.annotation.VisibleForTesting
import com.android.wm.shell.bubbles.util.BubbleUtils.isBubbleToFullscreen
import com.android.wm.shell.taskview.TaskView
import java.util.concurrent.Executor

/**
 * A wrapper class around [TaskView] for bubble expanded views.
 *
 * [delegateListener] allows callers to change listeners after a task has been created.
 */
class BubbleTaskView(val taskView: TaskView, executor: Executor) {

    /** Whether the task is already created. */
    var isCreated = false
      private set

    /** The task id. */
    var taskId = INVALID_TASK_ID
      private set

    /** The component name of the application running in the task. */
    var componentName: ComponentName? = null
      private set

    /**
     * Whether the task view is visible and has a surface. Note that this does not check the alpha
     * value of the task view.
     *
     * When this is `true` it is safe to start showing the task view. Otherwise if this is `false`
     * callers should wait for it to be visible which will be indicated either by a call to
     * [TaskView.Listener.onTaskCreated] or [TaskView.Listener.onTaskVisibilityChanged]. */
    var isVisible = false
      private set

    /** [TaskView.Listener] for users of this class. */
    var delegateListener: TaskView.Listener? = null

    /** A [TaskView.Listener] that delegates to [delegateListener]. */
    @get:VisibleForTesting
    val listener = object : TaskView.Listener {
        override fun onInitialized() {
            delegateListener?.onInitialized()
        }

        override fun onSurfaceAlreadyCreated() {
            delegateListener?.onSurfaceAlreadyCreated()
        }

        override fun onReleased() {
            delegateListener?.onReleased()
        }

        override fun onTaskCreated(taskId: Int, name: ComponentName) {
            delegateListener?.onTaskCreated(taskId, name)
            this@BubbleTaskView.taskId = taskId
            isCreated = true
            componentName = name
            // when the task is created it is visible
            isVisible = true
        }

        override fun onTaskVisibilityChanged(taskId: Int, visible: Boolean) {
            this@BubbleTaskView.isVisible = visible
            delegateListener?.onTaskVisibilityChanged(taskId, visible)
        }

        override fun onTaskRemovalStarted(taskId: Int) {
            delegateListener?.onTaskRemovalStarted(taskId)
        }

        override fun onTaskInfoChanged(taskInfo: RunningTaskInfo?) {
            delegateListener?.onTaskInfoChanged(taskInfo)
        }

        override fun onBackPressedOnTaskRoot(taskId: Int) {
            delegateListener?.onBackPressedOnTaskRoot(taskId)
        }
    }

    init {
        taskView.setListener(executor, listener)
    }

    /**
     * Removes the [TaskView] from window manager.
     *
     * This should be called after all other cleanup animations have finished.
     */
    fun cleanup() {
        if (isBubbleToFullscreen(taskView.taskInfo)) {
            taskView.unregisterTask()
        } else {
            taskView.removeTask()
        }
    }
}
