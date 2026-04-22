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

package com.android.quickstep.util

import android.content.ComponentName
import android.content.Intent
import android.view.Display.DEFAULT_DISPLAY
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.systemui.shared.recents.model.Task
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class DesktopTaskTest {

    @Test
    fun testDesktopTask_sameInstance_isEqual() {
        val task = DesktopTask(deskId = 0, DEFAULT_DISPLAY, createTasks(1))
        assertThat(task).isEqualTo(task)
    }

    @Test
    fun testDesktopTask_identicalConstructor_isEqual() {
        val task1 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, createTasks(1))
        val task2 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, createTasks(1))
        assertThat(task1).isEqualTo(task2)
    }

    @Test
    fun testDesktopTask_copy_isEqual() {
        val task1 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, createTasks(1))
        val task2 = task1.copy()
        assertThat(task1).isEqualTo(task2)
    }

    @Test
    fun testDesktopTask_differentDeskIds_isNotEqual() {
        val task1 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, createTasks(1))
        val task2 = DesktopTask(deskId = 1, DEFAULT_DISPLAY, createTasks(1))
        assertThat(task1).isNotEqualTo(task2)
    }

    @Test
    fun testDesktopTask_differentTaskIds_isNotEqual() {
        val task1 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, createTasks(1))
        val task2 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, createTasks(2))
        assertThat(task1).isNotEqualTo(task2)
    }

    @Test
    fun testDesktopTask_differentLength_isNotEqual() {
        val task1 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, createTasks(1))
        val task2 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, createTasks(1, 2))
        assertThat(task1).isNotEqualTo(task2)
    }

    private fun createTasks(vararg ids: Int): List<Task> {
        return ids.map { Task(Task.TaskKey(it, 0, Intent(), ComponentName("", ""), 0, 0)) }
    }
}
