
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

import android.app.ActivityManager.LOCK_TASK_MODE_LOCKED
import android.app.ActivityManager.LOCK_TASK_MODE_NONE
import android.testing.AndroidTestingRunner
import com.android.server.testutils.mock
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * Tests for [LockTaskChangeListener], making sure listeners are correctly called
 * and the member variables are correctly updated when a change occurs.
 */
@RunWith(AndroidTestingRunner::class)
class LockTaskChangeListenerTest : ShellTestCase() {

    private val taskStackListenerImpl = mock<TaskStackListenerImpl>()
    private val shellInit = mock<ShellInit>()
    private val listener = mock<LockTaskChangeListener.LockTaskModeChangedListener>()

    private val lockTaskChangeListener: LockTaskChangeListener =
        LockTaskChangeListener(shellInit, taskStackListenerImpl)

    @Before
    fun setUp() {
        lockTaskChangeListener.addListener(listener)
    }

    @Test
    fun onLockTaskModeChanged_notifiesListenersAndUpdatesState() {
        // WHEN the lock task mode changes
        lockTaskChangeListener.onLockTaskModeChanged(LOCK_TASK_MODE_LOCKED)

        // THEN all listeners should be notified
        verify(listener).onLockTaskModeChanged(LOCK_TASK_MODE_LOCKED)
        assertTrue(lockTaskChangeListener.isTaskLocked)
    }

    @Test
    fun onLockTaskModeChanged_toNone_notifiesListenersAndUpdatesState() {
        // GIVEN the task is initially locked
        lockTaskChangeListener.onLockTaskModeChanged(LOCK_TASK_MODE_LOCKED)

        // WHEN the lock task mode is removed
        lockTaskChangeListener.onLockTaskModeChanged(LOCK_TASK_MODE_NONE)

        // THEN all listeners should be notified
        verify(listener).onLockTaskModeChanged(LOCK_TASK_MODE_NONE)
        assertFalse(lockTaskChangeListener.isTaskLocked)
    }

    @Test
    fun removeListener_stopsNotifying() {
        // GIVEN a listener is removed
        lockTaskChangeListener.removeListener(listener)

        // WHEN the lock task mode changes
        lockTaskChangeListener.onLockTaskModeChanged(LOCK_TASK_MODE_LOCKED)

        // THEN the removed listener should not be notified
        verify(listener, never()).onLockTaskModeChanged(LOCK_TASK_MODE_LOCKED)
    }

    @Test
    fun isTaskLocked_reflectsCurrentState() {
        // Initially, no task is locked
        lockTaskChangeListener.onLockTaskModeChanged(LOCK_TASK_MODE_NONE)
        assertThat(lockTaskChangeListener.isTaskLocked).isFalse()

        // When a task is locked
        lockTaskChangeListener.onLockTaskModeChanged(LOCK_TASK_MODE_LOCKED)
        assertThat(lockTaskChangeListener.isTaskLocked).isTrue()

        // When the task is unlocked
        lockTaskChangeListener.onLockTaskModeChanged(LOCK_TASK_MODE_NONE)
        assertThat(lockTaskChangeListener.isTaskLocked).isFalse()
    }
}
