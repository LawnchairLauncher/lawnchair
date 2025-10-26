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

package com.android.quickstep.recents.domain.usecase

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.quickstep.recents.domain.model.TaskModel
import com.android.systemui.shared.recents.model.Task
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GetTaskUseCaseTest {
    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(unconfinedTestDispatcher)

    private val tasksRepository = FakeTasksRepository()
    private val sut = GetTaskUseCase(repository = tasksRepository)

    @Before
    fun setUp() {
        tasksRepository.seedTasks(listOf(TASK_1))
    }

    @Test
    fun taskNotSeeded_returnsNull() =
        testScope.runTest {
            val result = sut.invoke(NOT_FOUND_TASK_ID).firstOrNull()
            assertThat(result).isNull()
        }

    @Test
    fun taskNotVisible_returnsNull() =
        testScope.runTest {
            val result = sut.invoke(TASK_1_ID).firstOrNull()
            assertThat(result).isNull()
        }

    @Test
    fun taskVisible_returnsData() =
        testScope.runTest {
            tasksRepository.setVisibleTasks(setOf(TASK_1_ID))
            val expectedResult =
                TaskModel(
                    id = TASK_1_ID,
                    title = "Title $TASK_1_ID",
                    titleDescription = "Content Description $TASK_1_ID",
                    icon = TASK_1_ICON,
                    thumbnail = null,
                    backgroundColor = Color.BLACK,
                    isLocked = false,
                )
            val result = sut.invoke(TASK_1_ID).firstOrNull()
            assertThat(result).isEqualTo(expectedResult)
        }

    private companion object {
        const val NOT_FOUND_TASK_ID = 404
        private const val TASK_1_ID = 1
        private val TASK_1_ICON = ShapeDrawable()
        private val TASK_1 =
            Task(
                    Task.TaskKey(
                        /* id = */ TASK_1_ID,
                        /* windowingMode = */ 0,
                        /* intent = */ Intent(),
                        /* sourceComponent = */ ComponentName("", ""),
                        /* userId = */ 0,
                        /* lastActiveTime = */ 2000,
                    )
                )
                .apply {
                    title = "Title 1"
                    titleDescription = "Content Description 1"
                    colorBackground = Color.BLACK
                    icon = TASK_1_ICON
                    thumbnail = null
                    isLocked = false
                }
    }
}
