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
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.viewmodel.TaskViewData
import com.android.systemui.shared.recents.model.Task
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskThumbnailViewModelTest {
    private val recentsViewData = RecentsViewData()
    private val taskViewData = TaskViewData()
    private val systemUnderTest = TaskThumbnailViewModel(recentsViewData, taskViewData)

    @Test
    fun initialStateIsUninitialized() = runTest {
        assertThat(systemUnderTest.uiState.first()).isEqualTo(TaskThumbnailUiState.Uninitialized)
    }

    @Test
    fun bindRunningTask_thenStateIs_LiveTile() = runTest {
        val taskThumbnail = TaskThumbnail(Task(), isRunning = true)
        systemUnderTest.bind(taskThumbnail)

        assertThat(systemUnderTest.uiState.first()).isEqualTo(TaskThumbnailUiState.LiveTile)
    }

    @Test
    fun setRecentsFullscreenProgress_thenProgressIsPassedThrough() = runTest {
        recentsViewData.fullscreenProgress.value = 0.5f

        assertThat(systemUnderTest.recentsFullscreenProgress.first()).isEqualTo(0.5f)

        recentsViewData.fullscreenProgress.value = 0.6f

        assertThat(systemUnderTest.recentsFullscreenProgress.first()).isEqualTo(0.6f)
    }

    @Test
    fun setAncestorScales_thenScaleIsCalculated() = runTest {
        recentsViewData.scale.value = 0.5f
        taskViewData.scale.value = 0.6f

        assertThat(systemUnderTest.inheritedScale.first()).isEqualTo(0.3f)
    }

    @Test
    fun bindRunningTaskThenStoppedTask_thenStateIs_Uninitialized() = runTest {
        // TODO(b/334825222): Change the expectation here when snapshot state is implemented
        val task = Task()
        val runningTask = TaskThumbnail(task, isRunning = true)
        val stoppedTask = TaskThumbnail(task, isRunning = false)
        systemUnderTest.bind(runningTask)
        assertThat(systemUnderTest.uiState.first()).isEqualTo(TaskThumbnailUiState.LiveTile)

        systemUnderTest.bind(stoppedTask)
        assertThat(systemUnderTest.uiState.first()).isEqualTo(TaskThumbnailUiState.Uninitialized)
    }
}
