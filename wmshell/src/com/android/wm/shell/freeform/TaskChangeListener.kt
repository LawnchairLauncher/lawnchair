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

package com.android.wm.shell.freeform

import android.app.ActivityManager.RunningTaskInfo

/**
 * Interface used by [FreeformTaskTransitionObserver] to manage freeform tasks.
 *
 * The implementations are responsible for handle all the task management.
 */
interface TaskChangeListener {
    /** Notifies a task opening in freeform mode. */
    fun onTaskOpening(taskInfo: RunningTaskInfo)

    /** Notifies a task info update on the given task from Shell Transitions framework. */
    fun onTaskChanging(taskInfo: RunningTaskInfo)

    /**
     * Notifies a task info update on the given task from [FreeformTaskListener].
     *
     * This is used to propagate task info changes since not all task changes are propagated from
     * [TransitionObserver] in [onTaskChanging]. It is recommended to use [onTaskChanging] instead
     * of this method where possible.
     */
    fun onNonTransitionTaskChanging(taskInfo: RunningTaskInfo)

    /** Notifies a task moving to the front. */
    fun onTaskMovingToFront(taskInfo: RunningTaskInfo)

    /** Notifies a task moving to the back. */
    fun onTaskMovingToBack(taskInfo: RunningTaskInfo)

    /** Notifies a task is closing. */
    fun onTaskClosing(taskInfo: RunningTaskInfo)
}
