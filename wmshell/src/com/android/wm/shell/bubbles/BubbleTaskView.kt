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

import android.app.ActivityTaskManager
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.content.ComponentName
import android.os.RemoteException
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.transition.Transitions.ENABLE_SHELL_TRANSITIONS
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

    /** [TaskView.Listener] for users of this class. */
    var delegateListener: TaskView.Listener? = null

    /** A [TaskView.Listener] that delegates to [delegateListener]. */
    @get:VisibleForTesting
    val listener = object : TaskView.Listener {
        override fun onInitialized() {
            delegateListener?.onInitialized()
        }

        override fun onReleased() {
            delegateListener?.onReleased()
        }

        override fun onTaskCreated(taskId: Int, name: ComponentName) {
            delegateListener?.onTaskCreated(taskId, name)
            this@BubbleTaskView.taskId = taskId
            isCreated = true
            componentName = name
        }

        override fun onTaskVisibilityChanged(taskId: Int, visible: Boolean) {
            delegateListener?.onTaskVisibilityChanged(taskId, visible)
        }

        override fun onTaskRemovalStarted(taskId: Int) {
            delegateListener?.onTaskRemovalStarted(taskId)
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
        if (taskId != INVALID_TASK_ID) {
            // Ensure the task is removed from WM
            if (ENABLE_SHELL_TRANSITIONS) {
                taskView.removeTask()
            } else {
                try {
                    ActivityTaskManager.getService().removeTask(taskId)
                } catch (e: RemoteException) {
                    Log.w(TAG, e.message ?: "")
                }
            }
        }
    }

    private companion object {
        const val TAG = "BubbleTaskView"
    }
}
