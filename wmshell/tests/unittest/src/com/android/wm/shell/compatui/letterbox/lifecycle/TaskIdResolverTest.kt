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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.res.Configuration
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoRepository
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoState
import com.android.wm.shell.util.testTaskIdResolver
import java.util.function.Consumer
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Tests for [TaskIdResolver].
 *
 * Build/Install/Run: atest WMShellUnitTests:TaskIdResolverTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class TaskIdResolverTest : ShellTestCase() {

    @Test
    fun `Returns the same id when the task is present in the repository`() {
        runTestScenario { r ->
            testTaskIdResolver(r::factory) {
                r.prepareRepository { repo -> repo.insert(key = 10, r.createItem(taskId = 10)) }
                runningTaskInfo { ti -> ti.taskId = 10 }
                verifyLetterboxTaskId { letterboxTaskId -> assertEquals(10, letterboxTaskId) }
            }
        }
    }

    @Test
    fun `InMultiWindow returns id of the task with given task as parent task`() {
        runTestScenario { r ->
            testTaskIdResolver(r::factory) {
                r.prepareRepository { repo ->
                    repo.insert(key = 20, r.createItem(taskId = 20, parentTaskId = 10))
                }
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_MULTI_WINDOW
                }
                verifyLetterboxTaskId { letterboxTaskId -> assertEquals(20, letterboxTaskId) }
            }
        }
    }

    @Test
    fun `InMultiwindow returns same id if there is no task with task id as parent id`() {
        runTestScenario { r ->
            testTaskIdResolver(r::factory) {
                r.prepareRepository { repo ->
                    repo.insert(key = 20, r.createItem(taskId = 20, parentTaskId = 30))
                }
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_MULTI_WINDOW
                }
                verifyLetterboxTaskId { letterboxTaskId -> assertEquals(10, letterboxTaskId) }
            }
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<TaskIdResolverRobotTest>) {
        consumer.accept(TaskIdResolverRobotTest())
    }

    class TaskIdResolverRobotTest {

        companion object {
            @JvmStatic val TEST_TOKEN = mock<WindowContainerToken>()

            @JvmStatic val TEST_LEASH = mock<SurfaceControl>()

            @JvmStatic val TEST_CONFIGURATION = Configuration()
        }

        private val letterboxTaskInfoRepository: LetterboxTaskInfoRepository =
            LetterboxTaskInfoRepository()

        fun factory(): TaskIdResolver = TaskIdResolver(letterboxTaskInfoRepository)

        fun prepareRepository(consumer: (LetterboxTaskInfoRepository) -> Unit) {
            consumer(letterboxTaskInfoRepository)
        }

        fun createItem(taskId: Int = -1, parentTaskId: Int = -1) =
            LetterboxTaskInfoState(
                containerToken = TEST_TOKEN,
                containerLeash = TEST_LEASH,
                taskId = taskId,
                parentTaskId = parentTaskId,
                configuration = TEST_CONFIGURATION,
            )
    }
}
