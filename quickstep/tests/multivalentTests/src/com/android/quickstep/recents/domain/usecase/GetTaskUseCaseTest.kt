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
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.quickstep.recents.data.FakeAppTimersRepository
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.quickstep.recents.domain.model.TaskModel
import com.android.systemui.shared.recents.model.Task
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyString
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GetTaskUseCaseTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(unconfinedTestDispatcher)

    private val tasksRepository = FakeTasksRepository()
    private val timersRepository = FakeAppTimersRepository()
    private val getRemainingAppTimerDurationUseCase =
        spy(GetRemainingAppTimerDurationUseCase(timersRepository))
    private val sut =
        GetTaskUseCase(
            tasksRepository = tasksRepository,
            getRemainingAppTimerDurationUseCase = getRemainingAppTimerDurationUseCase,
        )

    @Before
    fun setUp() {
        tasksRepository.seedTasks(listOf(TASK_1))
        timersRepository.setTimer(PACKAGE_1, UserHandle(USER_1), REMAINING_APP_DURATION)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REFACTOR_DIGITAL_WELLBEING_TOAST)
    fun taskNotSeeded_returnsNull() =
        testScope.runTest {
            val result = sut.invoke(NOT_FOUND_TASK_ID).firstOrNull()

            assertThat(result).isNull()
            verify(getRemainingAppTimerDurationUseCase, times(0))
                .invoke(anyString(), any<UserHandle>())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REFACTOR_DIGITAL_WELLBEING_TOAST)
    fun taskNotVisible_returnsNull() =
        testScope.runTest {
            val result = sut.invoke(TASK_1_ID).firstOrNull()

            assertThat(result).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REFACTOR_DIGITAL_WELLBEING_TOAST)
    fun taskVisible_returnsData() =
        testScope.runTest {
            tasksRepository.setVisibleTasks(DEFAULT_DISPLAY, setOf(TASK_1_ID))

            val result = sut.invoke(TASK_1_ID).firstOrNull()

            assertThat(result)
                .isEqualTo(
                    TaskModel(
                        id = TASK_1_ID,
                        packageName = PACKAGE_1,
                        title = "Title $TASK_1_ID",
                        titleDescription = "Content Description $TASK_1_ID",
                        icon = TASK_1_ICON,
                        thumbnail = null,
                        backgroundColor = Color.BLACK,
                        isLocked = false,
                        isMinimized = false,
                        remainingAppDuration = ROUNDED_REMAINING_APP_DURATION,
                    )
                )
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REFACTOR_DIGITAL_WELLBEING_TOAST)
    fun taskVisible_noAppTimer_returnsDataWithoutTimer() =
        testScope.runTest {
            tasksRepository.setVisibleTasks(DEFAULT_DISPLAY, setOf(TASK_1_ID))
            timersRepository.resetTimer(PACKAGE_1, UserHandle(USER_1))

            val result = sut.invoke(TASK_1_ID).firstOrNull()

            assertThat(result)
                .isEqualTo(
                    TaskModel(
                        id = TASK_1_ID,
                        packageName = PACKAGE_1,
                        title = "Title $TASK_1_ID",
                        titleDescription = "Content Description $TASK_1_ID",
                        icon = TASK_1_ICON,
                        thumbnail = null,
                        backgroundColor = Color.BLACK,
                        isLocked = false,
                        isMinimized = false,
                        remainingAppDuration = null,
                    )
                )
            verify(getRemainingAppTimerDurationUseCase, times(1))
                .invoke(anyString(), any<UserHandle>())
        }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_REFACTOR_DIGITAL_WELLBEING_TOAST)
    fun taskVisible_dwbFlagOff_doesNotFetchTimer() =
        testScope.runTest {
            tasksRepository.setVisibleTasks(DEFAULT_DISPLAY, setOf(TASK_1_ID))

            val result = sut.invoke(TASK_1_ID).firstOrNull()

            assertThat(result)
                .isEqualTo(
                    TaskModel(
                        id = TASK_1_ID,
                        packageName = PACKAGE_1,
                        title = "Title $TASK_1_ID",
                        titleDescription = "Content Description $TASK_1_ID",
                        icon = TASK_1_ICON,
                        thumbnail = null,
                        backgroundColor = Color.BLACK,
                        isLocked = false,
                        isMinimized = false,
                        remainingAppDuration = null,
                    )
                )
            verify(getRemainingAppTimerDurationUseCase, times(0))
                .invoke(anyString(), any<UserHandle>())
        }

    private companion object {
        const val NOT_FOUND_TASK_ID = 404
        private const val TASK_1_ID = 1
        private const val PACKAGE_1 = "com.test.1"
        private const val USER_1 = 1
        private val TASK_1_ICON = ShapeDrawable()
        private val REMAINING_APP_DURATION = Duration.ofHours(2).plusMillis(10)
        private val ROUNDED_REMAINING_APP_DURATION = Duration.ofHours(2).plusMinutes(1)
        private val TASK_1 =
            Task(
                    Task.TaskKey(
                        /* id = */ TASK_1_ID,
                        /* windowingMode = */ 0,
                        /* intent = */ Intent(),
                        /* sourceComponent = */ ComponentName("", ""),
                        /* userId = */ USER_1,
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
                    isMinimized = false
                    topActivity = ComponentName(PACKAGE_1, "SomeClass")
                }
    }
}
