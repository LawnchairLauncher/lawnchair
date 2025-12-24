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

package com.android.wm.shell.desktopmode.data

import android.graphics.Rect
import android.util.ArraySet
import android.window.WindowContainerToken

/**
 * Task data tracked per desk.
 *
 * @property activeTasks task ids of active tasks currently or previously visible in the desk. Tasks
 *   become inactive when task closes or when the desk becomes inactive.
 * @property visibleTasks task ids for active freeform tasks that are currently visible. There might
 *   be other active tasks in a desk that are not visible.
 * @property minimizedTasks task ids for active freeform tasks that are currently minimized.
 * @property closingTasks task ids for tasks that are going to close, but are currently visible.
 * @property freeformTasksInZOrder list of current freeform task ids ordered from top to bottom
 * @property fullImmersiveTaskId the task id of the desk's task that is in full-immersive mode.
 * @property topTransparentFullscreenTaskId the task id of any current top transparent fullscreen
 *   task launched on top of the desk. Cleared when the transparent task is closed or sent to back.
 *   (top is at index 0).
 * @property leftTiledTaskId task id of the task tiled on the left.
 * @property rightTiledTaskId task id of the task tiled on the right.
 */
data class Desk(
    val deskId: Int,
    var displayId: Int,
    // TODO: b/421928445 - Refactor these and boundsByTaskId into a new data class.
    val activeTasks: ArraySet<Int> = ArraySet(),
    val visibleTasks: ArraySet<Int> = ArraySet(),
    val minimizedTasks: ArraySet<Int> = ArraySet(),
    // TODO(b/332682201): Remove when the repository state is updated via TransitionObserver
    val closingTasks: ArraySet<Int> = ArraySet(),
    val freeformTasksInZOrder: ArrayList<Int> = ArrayList(),
    var fullImmersiveTaskId: Int? = null,
    var topTransparentFullscreenTaskData: TopTransparentFullscreenTaskData? = null,
    var leftTiledTaskId: Int? = null,
    var rightTiledTaskId: Int? = null,
    // The display's unique id that will remain the same across reboots.
    var uniqueDisplayId: String? = null,
    // A desk that will only exist briefly; remove it once it is preserved. Used for persistence.
    var transientDesk: Boolean = false,
) {
    // TODO: b/417907552 - Add these variables to persistent repository.
    // Bounds of tasks in this desk mapped to their respective task ids. Used for reconnect.
    var boundsByTaskId: MutableMap<Int, Rect> = mutableMapOf()

    fun deepCopy(): Desk =
        Desk(
                deskId = deskId,
                displayId = displayId,
                activeTasks = ArraySet(activeTasks),
                visibleTasks = ArraySet(visibleTasks),
                minimizedTasks = ArraySet(minimizedTasks),
                closingTasks = ArraySet(closingTasks),
                freeformTasksInZOrder = ArrayList(freeformTasksInZOrder),
                fullImmersiveTaskId = fullImmersiveTaskId,
                topTransparentFullscreenTaskData = topTransparentFullscreenTaskData,
                leftTiledTaskId = leftTiledTaskId,
                rightTiledTaskId = rightTiledTaskId,
            )
            .also {
                it.uniqueDisplayId = uniqueDisplayId
                it.boundsByTaskId = boundsByTaskId.toMutableMap()
            }

    // TODO: b/362720497 - remove when multi-desktops is enabled where instances aren't
    //  reusable.
    fun clear() {
        activeTasks.clear()
        visibleTasks.clear()
        minimizedTasks.clear()
        closingTasks.clear()
        freeformTasksInZOrder.clear()
        fullImmersiveTaskId = null
        topTransparentFullscreenTaskData = null
        leftTiledTaskId = null
        rightTiledTaskId = null
        boundsByTaskId.clear()
    }
}

/** Specific [TaskInfo] data related to top transparent fullscreen task handling. */
data class TopTransparentFullscreenTaskData(val taskId: Int, val token: WindowContainerToken)
