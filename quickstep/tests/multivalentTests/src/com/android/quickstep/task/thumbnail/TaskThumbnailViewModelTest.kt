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

package com.android.quickstep.task.thumbnail

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.systemui.shared.recents.model.Task
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskThumbnailViewModelTest {
    private val systemUnderTest = TaskThumbnailViewModel()

    @Test
    fun initialStateIsUninitialized() {
        assertThat(systemUnderTest.uiState.value).isEqualTo(TaskThumbnailUiState.Uninitialized)
    }

    @Test
    fun bindRunningTask_thenStateIs_LiveTile() {
        val taskThumbnail = TaskThumbnail(Task(), isRunning = true)
        systemUnderTest.bind(taskThumbnail)

        assertThat(systemUnderTest.uiState.value).isEqualTo(TaskThumbnailUiState.LiveTile)
    }

    @Test
    fun bindRunningTaskThenStoppedTask_thenStateIs_Uninitialized() {
        // TODO(b/334825222): Change the expectation here when snapshot state is implemented
        val task = Task()
        val runningTask = TaskThumbnail(task, isRunning = true)
        val stoppedTask = TaskThumbnail(task, isRunning = false)
        systemUnderTest.bind(runningTask)
        assertThat(systemUnderTest.uiState.value).isEqualTo(TaskThumbnailUiState.LiveTile)

        systemUnderTest.bind(stoppedTask)
        assertThat(systemUnderTest.uiState.value).isEqualTo(TaskThumbnailUiState.Uninitialized)
    }
}
