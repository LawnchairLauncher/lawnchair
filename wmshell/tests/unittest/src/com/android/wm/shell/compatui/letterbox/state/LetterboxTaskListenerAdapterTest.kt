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

package com.android.wm.shell.compatui.letterbox.state

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.compatui.letterbox.asMode
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.util.testTaskAppearedListener
import com.android.wm.shell.util.testTaskVanishedListener
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [LetterboxTaskListenerAdapter].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:LetterboxTaskListenerAdapterTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxTaskListenerAdapterTest : ShellTestCase() {

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `When the flag is ENABLED the listener is registered`() {
        runTestScenario { r ->
            r.invokeShellInit()
            r.checkListenerIsRegistered(expected = true)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `When the flag is DISABLED the listener is NOT registered`() {
        runTestScenario { r ->
            r.invokeShellInit()
            r.checkListenerIsRegistered(expected = false)
        }
    }

    @Test
    fun `When a Task appears the TaskInfo data are persisted`() {
        runTestScenario { r ->
            testTaskAppearedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                val leashTest = SurfaceControl()
                val tokenTest = mock<WindowContainerToken>()
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                }
                leash { leashTest }
                validateOnTaskAppeared {
                    r.validateItem(10) { item ->
                        assert(item?.containerLeash == leashTest)
                        assert(item?.containerToken == tokenTest)
                    }
                }
            }
        }
    }

    @Test
    fun `When a Task vanishes the TaskInfo data are removed`() {
        runTestScenario { r ->
            val leashTest = SurfaceControl()
            val tokenTest = mock<WindowContainerToken>()
            testTaskAppearedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                }
                leash { leashTest }
                validateOnTaskAppeared {
                    r.validateItem(10) { item ->
                        assert(item != null)
                    }
                }
            }
            testTaskVanishedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                }
                validateOnTaskVanished {
                    r.validateItem(10) { item ->
                        assert(item == null)
                    }
                }
            }
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<LetterboxTaskListenerAdapterRobotTest>) {
        val robot = LetterboxTaskListenerAdapterRobotTest()
        consumer.accept(robot)
    }

    class LetterboxTaskListenerAdapterRobotTest {

        private val executor: ShellExecutor
        private val shellInit: ShellInit
        private val shellTaskOrganizer: ShellTaskOrganizer
        private val letterboxTaskListenerAdapter: LetterboxTaskListenerAdapter
        private val letterboxTaskInfoRepository: LetterboxTaskInfoRepository

        init {
            executor = mock<ShellExecutor>()
            shellInit = ShellInit(executor)
            shellTaskOrganizer = mock<ShellTaskOrganizer>()
            letterboxTaskInfoRepository = LetterboxTaskInfoRepository()
            letterboxTaskListenerAdapter = LetterboxTaskListenerAdapter(
                shellInit,
                shellTaskOrganizer,
                letterboxTaskInfoRepository
            )
        }

        fun getLetterboxTaskListenerAdapterFactory(): () -> LetterboxTaskListenerAdapter = {
            letterboxTaskListenerAdapter
        }

        fun invokeShellInit() = shellInit.init()

        fun checkListenerIsRegistered(expected: Boolean) {
            verify(shellTaskOrganizer, expected.asMode()).addTaskAppearedListener(any())
            verify(shellTaskOrganizer, expected.asMode()).addTaskVanishedListener(any())
        }

        fun validateItem(taskId: Int, consumer: (LetterboxTaskInfoState?) -> Unit) {
            consumer(letterboxTaskInfoRepository.find(taskId))
        }
    }
}
