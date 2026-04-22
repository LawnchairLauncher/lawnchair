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
import android.graphics.Rect
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.systemui.shared.recents.model.Task
import com.android.wm.shell.shared.split.SplitBounds
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class GroupTaskTest {

    @Test
    fun testGroupTask_sameInstance_isEqual() {
        val task = SingleTask(createTask(1))
        assertThat(task).isEqualTo(task)
    }

    @Test
    fun testGroupTask_identicalConstructor_isEqual() {
        val task1 = SingleTask(createTask(1))
        val task2 = SingleTask(createTask(1))
        assertThat(task1).isEqualTo(task2)
    }

    @Test
    fun testGroupTask_copy_isEqual() {
        val task1 = SingleTask(createTask(1))
        val task2 = task1.copy()
        assertThat(task1).isEqualTo(task2)
    }

    @Test
    fun testGroupTask_differentId_isNotEqual() {
        val task1 = SingleTask(createTask(1))
        val task2 = SingleTask(createTask(2))
        assertThat(task1).isNotEqualTo(task2)
    }

    @Test
    fun testGroupTask_equalSplitTasks_isEqual() {
        val splitBounds =
            SplitBounds(
                Rect(),
                Rect(),
                1,
                2,
                SplitScreenConstants.SNAP_TO_2_50_50,
            )
        val task1 = SplitTask(createTask(1), createTask(2), splitBounds)
        val task2 = SplitTask(createTask(1), createTask(2), splitBounds)
        assertThat(task1).isEqualTo(task2)
    }

    @Test
    fun testGroupTask_differentSplitTasks_isNotEqual() {
        val splitBounds1 =
            SplitBounds(
                Rect(),
                Rect(),
                1,
                2,
                SplitScreenConstants.SNAP_TO_2_50_50,
            )
        val splitBounds2 =
            SplitBounds(
                Rect(),
                Rect(),
                1,
                2,
                SplitScreenConstants.SNAP_TO_2_33_66,
            )
        val task1 = SplitTask(createTask(1), createTask(2), splitBounds1)
        val task2 = SplitTask(createTask(1), createTask(2), splitBounds2)
        assertThat(task1).isNotEqualTo(task2)
    }

    @Test
    fun testGroupTask_differentType_isNotEqual() {
        val task1 = SingleTask(createTask(1))
        val task2 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, listOf(createTask(1)))
        assertThat(task1).isNotEqualTo(task2)
    }

    @Test
    fun testDesktopTask_matchesDisplayId() {
        val task1 = DesktopTask(deskId = 0, DEFAULT_DISPLAY, listOf(createTask(1, INVALID_DISPLAY)))
        assertThat(task1.matchesDisplayId(DEFAULT_DISPLAY)).isTrue()
        assertThat(task1.matchesDisplayId(DISPLAY_2)).isFalse()
        val task2 = DesktopTask(deskId = 0, DISPLAY_2, listOf(createTask(1, DISPLAY_2)))
        assertThat(task2.matchesDisplayId(DEFAULT_DISPLAY)).isFalse()
        assertThat(task2.matchesDisplayId(DISPLAY_2)).isTrue()
        val task3 = DesktopTask(deskId = 0, DISPLAY_2, listOf(createTask(1, INVALID_DISPLAY)))
        assertThat(task3.matchesDisplayId(DEFAULT_DISPLAY)).isFalse()
        assertThat(task3.matchesDisplayId(DISPLAY_2)).isTrue()
    }

    @Test
    fun testSingleTask_matchesDisplayId() {
        val task1 = SingleTask(createTask(1, INVALID_DISPLAY))
        assertThat(task1.matchesDisplayId(DEFAULT_DISPLAY)).isTrue()
        assertThat(task1.matchesDisplayId(DISPLAY_2)).isFalse()
        val task2 = SingleTask(createTask(1, DISPLAY_2))
        assertThat(task2.matchesDisplayId(DEFAULT_DISPLAY)).isFalse()
        assertThat(task2.matchesDisplayId(DISPLAY_2)).isTrue()
        val task3 = SingleTask(createTask(1, DEFAULT_DISPLAY))
        assertThat(task3.matchesDisplayId(DEFAULT_DISPLAY)).isTrue()
        assertThat(task3.matchesDisplayId(DISPLAY_2)).isFalse()
    }

    @Test
    fun testSplitTask_matchesDisplayId() {
        val splitBounds =
            SplitBounds(
                Rect(),
                Rect(),
                1,
                2,
                SplitScreenConstants.SNAP_TO_2_50_50,
            )
        val task1 =
            SplitTask(createTask(1, INVALID_DISPLAY), createTask(2, INVALID_DISPLAY), splitBounds)
        assertThat(task1.matchesDisplayId(DEFAULT_DISPLAY)).isTrue()
        assertThat(task1.matchesDisplayId(DISPLAY_2)).isFalse()
        val task2 = SplitTask(createTask(1, INVALID_DISPLAY), createTask(2, DISPLAY_2), splitBounds)
        assertThat(task2.matchesDisplayId(DEFAULT_DISPLAY)).isTrue()
        assertThat(task2.matchesDisplayId(DISPLAY_2)).isFalse()
        val task3 = SplitTask(createTask(1, DISPLAY_2), createTask(2, INVALID_DISPLAY), splitBounds)
        assertThat(task3.matchesDisplayId(DEFAULT_DISPLAY)).isFalse()
        assertThat(task3.matchesDisplayId(DISPLAY_2)).isTrue()
        val task4 =
            SplitTask(createTask(1, DEFAULT_DISPLAY), createTask(2, DEFAULT_DISPLAY), splitBounds)
        assertThat(task4.matchesDisplayId(DEFAULT_DISPLAY)).isTrue()
        assertThat(task4.matchesDisplayId(DISPLAY_2)).isFalse()
        val task5 = SplitTask(createTask(1, DEFAULT_DISPLAY), createTask(2, DISPLAY_2), splitBounds)
        assertThat(task5.matchesDisplayId(DEFAULT_DISPLAY)).isTrue()
        assertThat(task5.matchesDisplayId(DISPLAY_2)).isFalse()
        val task6 = SplitTask(createTask(1, DISPLAY_2), createTask(2, DEFAULT_DISPLAY), splitBounds)
        assertThat(task6.matchesDisplayId(DEFAULT_DISPLAY)).isFalse()
        assertThat(task6.matchesDisplayId(DISPLAY_2)).isTrue()
    }

    private fun createTask(id: Int, displayId: Int = INVALID_DISPLAY, pkg: String? = null): Task {
        val intent = Intent()
        pkg.let { intent.setPackage(it) }
        return Task(
            Task.TaskKey(
                id,
                0,
                intent,
                ComponentName(pkg ?: "", ""),
                0,
                0,
                displayId,
                null,
                0,
                false,
                false,
            )
        )
    }

    companion object {
        const val DISPLAY_2 = 2
        const val PACKAGE = "com.android.launcher3"
    }
}
