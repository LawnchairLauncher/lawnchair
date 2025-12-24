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

package com.android.wm.shell.common

import android.app.ActivityManager.LOCK_TASK_MODE_NONE
import com.android.wm.shell.sysui.ShellInit

/**
 * A component for observing changes to the system's LockTask mode.
 *
 * This class listens for changes to LockTaskMode from [TaskStackListenerImpl] and keeps a cache of
 * the current LockTask mode (LOCK_TASK_MODE_NONE, LOCK_TASK_MODE_LOCKED, LOCK_TASK_MODE_PINNED).
 * It provides a listener interface for other components to subscribe to these changes,
 * allowing them to react when a task becomes locked or unlocked.
 *
 * To use this class, obtain an instance and register a [LockTaskModeChangedListener] via
 * the [addListener] method.
 */
class LockTaskChangeListener(
    shellInit: ShellInit,
    private val taskStackListenerImpl: TaskStackListenerImpl,
) : TaskStackListenerCallback {

    /** Listener for lock task mode changes. */
    fun interface LockTaskModeChangedListener {
        /** Called when the lock task mode changes. */
        fun onLockTaskModeChanged(mode: Int)
    }

    private var lockTaskMode = LOCK_TASK_MODE_NONE

    val isTaskLocked
        get() = lockTaskMode != LOCK_TASK_MODE_NONE

    private val listeners = linkedSetOf<LockTaskModeChangedListener>()

    init {
        shellInit.addInitCallback(::onInit, this)
    }

    private fun onInit() {
        taskStackListenerImpl.addListener(this)
    }

    /** Adds a listener for lock task mode changes. */
    fun addListener(listener: LockTaskModeChangedListener) {
        listeners.add(listener)
    }

    /** Removes a listener for lock task mode changes. */
    fun removeListener(listener: LockTaskModeChangedListener) {
        listeners.remove(listener)
    }

    override fun onLockTaskModeChanged(mode: Int) {
        lockTaskMode = mode

        // Notify all registered listeners
        listeners.forEach { it.onLockTaskModeChanged(lockTaskMode) }
    }
}
