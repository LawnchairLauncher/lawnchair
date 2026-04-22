/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.quickstep.util

import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.split.SplitBounds
import java.util.Objects

/**
 * An abstract class for creating [Task] containers that can be [SingleTask]s, [SplitTask]s, or
 * [DesktopTask]s in the recent tasks list.
 */
abstract class GroupTask(
    val tasks: List<Task>,
    val displayId: Int,
    @JvmField val taskViewType: TaskViewType,
) {

    fun containsTask(taskId: Int) = tasks.any { it.key.id == taskId }

    /**
     * Returns true if a task in this group has a package name that matches the given `packageName`.
     */
    fun containsPackage(packageName: String?) = tasks.any { it.key.packageName == packageName }

    /** Returns true if a task in this group has the given displayId. */
    fun matchesDisplayId(displayId: Int) = displayId == this.displayId.safeDisplayId

    /**
     * Returns true if a task in this group has a package name that matches the given `packageName`,
     * and its user ID matches the given `userId`.
     */
    fun containsPackage(packageName: String?, userId: Int) =
        tasks.any { it.key.packageName == packageName && it.key.userId == userId }

    fun isEmpty() = tasks.isEmpty()

    /** Creates a copy of this instance */
    abstract fun copy(): GroupTask

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is GroupTask) return false
        return taskViewType == o.taskViewType && tasks == o.tasks
    }

    override fun hashCode() = Objects.hash(tasks, taskViewType)
}

/** A [Task] container that must contain exactly one task in the recent tasks list. */
class SingleTask(task: Task) : GroupTask(listOf(task), task.key.displayId, TaskViewType.SINGLE) {

    val task: Task
        get() = tasks[0]

    override fun copy() = SingleTask(task)

    override fun toString() = "type=$taskViewType task=$task"

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is SingleTask) return false
        return super.equals(o)
    }

    companion object {
        /** Creates a [TaskItemInfo] using the information of the SingleTask */
        fun createTaskItemInfo(task: SingleTask, wif: WorkspaceItemInfo): TaskItemInfo {
            // TODO: b/344657629 - Support GroupTask in addition to SingleTask.
            return TaskItemInfo(task.task.key.id, wif)
        }
    }

    override fun hashCode() = super.hashCode()
}

/**
 * A [Task] container that must contain exactly two tasks and split bounds to represent an app-pair
 * in the recent tasks list.
 */
class SplitTask(task1: Task, task2: Task, val splitBounds: SplitBounds?) :
    GroupTask(listOf(task1, task2), task1.key.displayId, TaskViewType.GROUPED) {

    val topLeftTask: Task
        get() =
            when {
                splitBounds == null -> tasks[0]
                splitBounds.leftTopTaskId == tasks[0].key.id -> tasks[0]
                else -> tasks[1]
            }

    val bottomRightTask: Task
        get() = if (topLeftTask == tasks[0]) tasks[1] else tasks[0]

    override fun copy() = SplitTask(tasks[0], tasks[1], splitBounds)

    override fun toString() =
        "type=$taskViewType topLeftTask=$topLeftTask bottomRightTask=$bottomRightTask"

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is SplitTask) return false
        if (splitBounds != o.splitBounds) return false
        return super.equals(o)
    }

    override fun hashCode() = Objects.hash(super.hashCode(), splitBounds)
}
