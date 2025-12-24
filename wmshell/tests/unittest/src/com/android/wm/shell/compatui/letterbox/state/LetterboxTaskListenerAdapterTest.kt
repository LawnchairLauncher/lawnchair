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
import com.android.window.flags2.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.compatui.letterbox.asMode
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.util.testTaskAppearedListener
import com.android.wm.shell.util.testTaskInfoChangedListener
import com.android.wm.shell.util.testTaskVanishedListener
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [LetterboxTaskListenerAdapter].
 *
 * Build/Install/Run: atest WMShellUnitTests:LetterboxTaskListenerAdapterTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxTaskListenerAdapterTest : ShellTestCase() {

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `When the flag is ENABLED the appear and vanish listeners are registered`() {
        runTestScenario { r ->
            r.invokeShellInit()
            r.checkTaskAppearedListenerIsRegistered(expected = true)
            r.checkTaskVanishedListenerIsRegistered(expected = true)
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `When task hierarchy flag is ENABLED the update listener is registered`() {
        runTestScenario { r ->
            r.invokeShellInit()
            r.checkTaskAppearedListenerIsRegistered(expected = true)
            r.checkTaskVanishedListenerIsRegistered(expected = true)
            r.checkTaskInfoChangedListenerIsRegistered(expected = true)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    fun `When the refactoring flag is DISABLED the listeners are NOT registered`() {
        runTestScenario { r ->
            r.invokeShellInit()
            r.checkTaskAppearedListenerIsRegistered(expected = false)
            r.checkTaskVanishedListenerIsRegistered(expected = false)
            r.checkTaskInfoChangedListenerIsRegistered(expected = false)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `When a Task appears the TaskInfo data are persisted`() {
        runTestScenario { r ->
            testTaskAppearedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                val leashTest = SurfaceControl()
                val tokenTest = mock<WindowContainerToken>()
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                    ti.appCompatTaskInfo.setIsLeafTask(true)
                }
                leash { leashTest }
                validateOnTaskAppeared {
                    r.validateItem(10) { item ->
                        assertEquals(leashTest, item?.containerLeash)
                        assertEquals(tokenTest, item?.containerToken)
                    }
                }
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
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
                validateOnTaskAppeared { r.validateItem(10) { item -> assertNotNull(item) } }
            }
            testTaskVanishedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                }
                validateOnTaskVanished { r.validateItem(10) { item -> assertNull(item) } }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `When a leaf Task appears the TaskInfo data are persisted with parentTaskId`() {
        runTestScenario { r ->
            testTaskAppearedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                val leashTest = SurfaceControl()
                val tokenTest = mock<WindowContainerToken>()
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.parentTaskId = 20
                    ti.token = tokenTest
                    ti.appCompatTaskInfo.setIsLeafTask(true)
                }
                leash { leashTest }
                validateOnTaskAppeared {
                    r.validateItem(10) { item ->
                        assertEquals(leashTest, item?.containerLeash)
                        assertEquals(tokenTest, item?.containerToken)
                        assertEquals(10, item?.taskId)
                        assertEquals(20, item?.parentTaskId)
                    }
                }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `When flag enabled and Task is NOT leaf the TaskInfo data are NOT persisted`() {
        runTestScenario { r ->
            testTaskAppearedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                val leashTest = SurfaceControl()
                val tokenTest = mock<WindowContainerToken>()
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.parentTaskId = 20
                    ti.token = tokenTest
                    ti.appCompatTaskInfo.setIsLeafTask(false)
                }
                leash { leashTest }
                validateOnTaskAppeared { r.validateItem(10) { item -> assertNull(item) } }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `When a Task vanishes the TaskInfo data are removed with task hierarchy flag enabled`() {
        runTestScenario { r ->
            val leashTest = SurfaceControl()
            val tokenTest = mock<WindowContainerToken>()
            testTaskAppearedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                    ti.appCompatTaskInfo.setIsLeafTask(true)
                }
                leash { leashTest }
                validateOnTaskAppeared { r.validateItem(10) { item -> assertNotNull(item) } }
            }
            testTaskVanishedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                }
                validateOnTaskVanished { r.validateItem(10) { item -> assertNull(item) } }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `Remove a task from tepository during update when not leaf anymore`() {
        runTestScenario { r ->
            val leashTest = SurfaceControl()
            val tokenTest = mock<WindowContainerToken>()
            testTaskAppearedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                    ti.appCompatTaskInfo.setIsLeafTask(true)
                }
                leash { leashTest }
                validateOnTaskAppeared { r.validateItem(10) { item -> assertNotNull(item) } }
            }
            testTaskInfoChangedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                    ti.appCompatTaskInfo.setIsLeafTask(true)
                }
                validateOnTaskInfoChanged { r.validateItem(10) { item -> assertNotNull(item) } }
            }
            testTaskInfoChangedListener(r.getLetterboxTaskListenerAdapterFactory()) {
                runningTaskInfo { ti ->
                    ti.taskId = 10
                    ti.token = tokenTest
                }
                validateOnTaskInfoChanged { r.validateItem(10) { item -> assertNull(item) } }
            }
        }
    }

    /** Runs a test scenario providing a Robot. */
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
            letterboxTaskListenerAdapter =
                LetterboxTaskListenerAdapter(
                    shellInit,
                    shellTaskOrganizer,
                    letterboxTaskInfoRepository,
                )
        }

        fun getLetterboxTaskListenerAdapterFactory(): () -> LetterboxTaskListenerAdapter = {
            letterboxTaskListenerAdapter
        }

        fun invokeShellInit() = shellInit.init()

        fun checkTaskAppearedListenerIsRegistered(expected: Boolean) {
            verify(shellTaskOrganizer, expected.asMode()).addTaskAppearedListener(any())
        }

        fun checkTaskVanishedListenerIsRegistered(expected: Boolean) {
            verify(shellTaskOrganizer, expected.asMode()).addTaskVanishedListener(any())
        }

        fun checkTaskInfoChangedListenerIsRegistered(expected: Boolean) {
            verify(shellTaskOrganizer, expected.asMode()).addTaskInfoChangedListener(any())
        }

        fun validateItem(taskId: Int, consumer: (LetterboxTaskInfoState?) -> Unit) {
            consumer(letterboxTaskInfoRepository.find(taskId))
        }
    }
}
