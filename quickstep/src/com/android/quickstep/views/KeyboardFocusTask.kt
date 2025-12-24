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

package com.android.quickstep.views

/**
 * Helper class representing which TaskView should be focused when entering Overview with keyboard.
 */
sealed class KeyboardFocusTask {

    // No TaskView should be focused, No-Op
    data object Unfocused : KeyboardFocusTask()

    // Focus the current page TaskView
    data object CurrentPageTaskView : KeyboardFocusTask()

    // Focus the task that is expected to be the current task.
    // See comments in RecentsViewUtils#getExpectedCurrentTask() for more details.
    data object ExpectedCurrentTask : KeyboardFocusTask()

    // Focus the TaskView with task ids matching a given set
    data class TaskViewWithIds(val taskIds: Set<Int>) : KeyboardFocusTask()

    override fun toString(): String {
        return when (this) {
            is Unfocused -> "Unfocused"
            is CurrentPageTaskView -> "CurrentPageTaskView"
            is ExpectedCurrentTask -> "ExpectedCurrentTask"
            is TaskViewWithIds -> "TaskViewWithIds( $taskIds )"
        }
    }
}
