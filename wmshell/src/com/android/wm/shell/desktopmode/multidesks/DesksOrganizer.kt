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
package com.android.wm.shell.desktopmode.multidesks

import android.app.ActivityManager
import android.window.TransitionInfo
import android.window.WindowContainerTransaction

/** An organizer of desk containers in which to host child desktop windows. */
interface DesksOrganizer {
    /** Creates a new desk for the given user if none exist. */
    fun warmUpDefaultDesk(displayId: Int, userId: Int)

    /** Creates a new desk container to use in the given display for the given user. */
    fun createDesk(displayId: Int, userId: Int, callback: OnCreateCallback)

    /**
     * Creates and returns the id of a new desk container to use in the given display for the given
     * user if it can be created synchronously, or null if it cannot.
     */
    @Deprecated("Use createDesk() instead.", ReplaceWith("createDesk()"))
    fun createDeskImmediate(displayId: Int, userId: Int): Int?

    /** Activates the given desk, making it visible in its display. */
    fun activateDesk(wct: WindowContainerTransaction, deskId: Int, skipReorder: Boolean = false)

    /** Deactivates the given desk, removing it as the default launch container for new tasks. */
    fun deactivateDesk(wct: WindowContainerTransaction, deskId: Int, skipReorder: Boolean = false)

    /** Removes the given desk of the given user. */
    fun removeDesk(wct: WindowContainerTransaction, deskId: Int, userId: Int)

    /** Moves the given task to the given desk. */
    fun moveTaskToDesk(
        wct: WindowContainerTransaction,
        deskId: Int,
        task: ActivityManager.RunningTaskInfo,
        minimized: Boolean = false,
    )

    /** Reorders a desk's task to the front. */
    fun reorderTaskToFront(
        wct: WindowContainerTransaction,
        deskId: Int,
        task: ActivityManager.RunningTaskInfo,
    )

    /** Minimizes the given task of the given deskId. */
    fun minimizeTask(
        wct: WindowContainerTransaction,
        deskId: Int,
        task: ActivityManager.RunningTaskInfo,
    )

    /** Unminimize the given task of the given desk. */
    fun unminimizeTask(
        wct: WindowContainerTransaction,
        deskId: Int,
        task: ActivityManager.RunningTaskInfo,
    )

    /** Whether the change is for the given desk id. */
    fun isDeskChange(change: TransitionInfo.Change, deskId: Int): Boolean

    /** Whether the change is for a known desk. */
    fun isDeskChange(change: TransitionInfo.Change): Boolean

    /** The desk for a given change, if any. */
    fun getDeskIdFromChange(change: TransitionInfo.Change): Int?

    /**
     * Returns the desk id in which the task in the given change is located at the end of a
     * transition, if any.
     */
    fun getDeskAtEnd(change: TransitionInfo.Change): Int?

    /** Whether the task in [change] is minimized in a desk. */
    fun isMinimizedInDeskAtEnd(change: TransitionInfo.Change): Boolean

    /** Whether the desk is activate according to the given change at the end of a transition. */
    fun isDeskActiveAtEnd(change: TransitionInfo.Change, deskId: Int): Boolean

    /** Allows for other classes to respond to task changes this organizer receives. */
    fun setOnDesktopTaskInfoChangedListener(listener: (ActivityManager.RunningTaskInfo) -> Unit)

    /** Move a desk to the given display area. */
    fun moveDeskToDisplay(
        wct: WindowContainerTransaction,
        deskId: Int,
        displayId: Int,
        onTop: Boolean,
    )

    /** A callback that is invoked when the desk container is created. */
    fun interface OnCreateCallback {
        /** Calls back when the [deskId] has been created. */
        fun onCreated(deskId: Int)
    }
}
