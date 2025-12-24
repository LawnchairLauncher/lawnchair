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
import android.graphics.Point
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.window.flags2.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.config.LetterboxDependenciesHelper
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoRepository
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoState
import com.android.wm.shell.util.testLetterboxLifecycleEventFactory
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tests for [TaskInfoLetterboxLifecycleEventFactory].
 *
 * Build/Install/Run: atest WMShellUnitTests:TaskInfoLetterboxLifecycleEventFactoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class TaskInfoLetterboxLifecycleEventFactoryTest : ShellTestCase() {

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `Change without TaskInfo cannot create the event and returns null`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    // Empty Change
                }
                validateCanHandle { canHandle -> assertFalse(canHandle) }
                validateCreateLifecycleEvent { event -> assertNull(event) }
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `With TaskInfo for Bubble a bubble event is returned`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    endAbsBounds = Rect(100, 200, 2000, 1000)
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.isAppBubble = true
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertTrue(event.isBubble)
                }
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `With TaskInfo taskBounds are calculated from endAbsBounds`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    runningTaskInfo { ti -> ti.taskId = 10 }
                    endAbsBounds = Rect(100, 200, 2000, 1000)
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(Rect(0, 0, 1900, 800), event.taskBounds)
                }
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `With TaskInfo letterboxBounds are null when Activity is not letterboxed`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.appCompatTaskInfo.isTopActivityLetterboxed = false
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertNull(event.letterboxBounds)
                }
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `With TaskInfo letterboxBounds from appCompatTaskInfo when Activity is letterboxed`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.appCompatTaskInfo.isTopActivityLetterboxed = true
                        ti.appCompatTaskInfo.topActivityLetterboxBounds = Rect(300, 200, 2300, 1200)
                    }
                    endAbsBounds = Rect(100, 50, 2500, 1500)
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(Rect(200, 150, 2200, 1150), event.letterboxBounds)
                }
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `With TaskInfo leash from Change`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputLeash = mock<SurfaceControl>()
                inputChange {
                    runningTaskInfo { ti -> ti.taskId = 10 }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                    leash { inputLeash }
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(inputLeash, event.taskLeash)
                }
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `With TaskInfo token from TaskInfo`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputToken = mock<WindowContainerToken>()
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.token = inputToken
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(inputToken, event.containerToken)
                }
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `supportsInput comes from LetterboxDependencyHelper`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange { runningTaskInfo { ti -> ti.taskId = 10 } }

                r.shouldSupportInputSurface(shouldSupportInputSurface = true)
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertTrue(event.supportsInput)
                }

                r.shouldSupportInputSurface(shouldSupportInputSurface = false)
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertFalse(event.supportsInput)
                }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS,
    )
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY)
    fun `With TaskInfo token leash and configuration are persistend with no hierarchy flag`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputToken = mock<WindowContainerToken>()
                val inputLeash = mock<SurfaceControl>()
                val inputConfiguration = Configuration()
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.token = inputToken
                        ti.configuration.setTo(inputConfiguration)
                    }
                    leash { inputLeash }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(inputToken, event.containerToken)
                }
                r.useRepository { repository ->
                    val item = repository.find(10)
                    assertNotNull(item)
                    assertEquals(item.containerToken, inputToken)
                    assertEquals(item.containerLeash, inputLeash)
                    assertEquals(0, item.configuration.compareTo(inputConfiguration))
                }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `Change without TaskInfo cannot create the event and returns null with task hierarchy`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    // Empty Change
                }
                validateCanHandle { canHandle -> assertFalse(canHandle) }
                validateCreateLifecycleEvent { event -> assertNull(event) }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `With TaskInfo but for a not leaf Task event is null`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputLeash = mock<SurfaceControl>()
                val inputToken = mock<WindowContainerToken>()
                inputChange {
                    endAbsBounds = Rect(100, 200, 2000, 1000)
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.token = inputToken
                        ti.appCompatTaskInfo.setIsLeafTask(false)
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    leash { inputLeash }
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event -> assertNull(event) }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `With TaskInfo for Bubble a bubble event is returned with task hierarchy enabled`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputLeash = mock<SurfaceControl>()
                val inputToken = mock<WindowContainerToken>()
                inputChange {
                    endAbsBounds = Rect(100, 200, 2000, 1000)
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.token = inputToken
                        ti.isAppBubble = true
                        ti.appCompatTaskInfo.setIsLeafTask(true)
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    leash { inputLeash }
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertTrue(event.isBubble)
                }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `With TaskInfo taskBounds are calculated from endAbsBounds with task hierarchy enabled`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputLeash = mock<SurfaceControl>()
                val inputToken = mock<WindowContainerToken>()
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.token = inputToken
                        ti.appCompatTaskInfo.setIsLeafTask(true)
                    }
                    endAbsBounds = Rect(100, 200, 2000, 1000)
                    leash { inputLeash }
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(Rect(0, 0, 1900, 800), event.taskBounds)
                }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `With TaskInfo letterboxBounds are null when not letterboxed and hierarchy flag enabled`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputLeash = mock<SurfaceControl>()
                val inputToken = mock<WindowContainerToken>()
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.token = inputToken
                        ti.appCompatTaskInfo.isTopActivityLetterboxed = false
                        ti.appCompatTaskInfo.setIsLeafTask(true)
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    leash { inputLeash }
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertNull(event.letterboxBounds)
                }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `With letterboxBounds from appCompatTaskInfo when letterboxed with hierarchy flag`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputLeash = mock<SurfaceControl>()
                val inputToken = mock<WindowContainerToken>()
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.token = inputToken
                        ti.appCompatTaskInfo.isTopActivityLetterboxed = true
                        ti.appCompatTaskInfo.topActivityLetterboxBounds = Rect(300, 200, 2300, 1200)
                        ti.appCompatTaskInfo.setIsLeafTask(true)
                    }
                    endAbsBounds = Rect(100, 50, 2500, 1500)
                    leash { inputLeash }
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(Rect(200, 150, 2200, 1150), event.letterboxBounds)
                }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `With TaskInfo leash and token from Repository when flag enabled and parentId is used`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val parentLeash = mock<SurfaceControl>()
                val parentToken = mock<WindowContainerToken>()
                val inputLeash = mock<SurfaceControl>()
                val inputToken = mock<WindowContainerToken>()
                val inputConfiguration = Configuration()
                // Insert data into the repository about a legitimate task which was a leaf task.
                r.useRepository { repo ->
                    repo.insert(
                        key = 10,
                        LetterboxTaskInfoState(
                            containerToken = inputToken,
                            containerLeash = inputLeash,
                            taskId = 10,
                            parentTaskId = 20,
                            configuration = inputConfiguration,
                        ),
                        overrideIfPresent = true,
                    )
                }
                // The request is for a NO leaf Task with taskId as the parent of the existing one.
                // The data about this Task should not be inserted in the Repository and the
                // related event should use id, leash and token of the previous one.
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 20
                        ti.token = parentToken
                        ti.appCompatTaskInfo.setIsLeafTask(false)
                        ti.configuration.windowConfiguration.windowingMode =
                            WINDOWING_MODE_MULTI_WINDOW
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                    leash { parentLeash }
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(10, event.taskId)
                    assertEquals(inputLeash, event.taskLeash)
                    assertEquals(inputToken, event.containerToken)
                }
                r.useRepository { repo -> assertNull(repo.find(20)) }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `supportsInput comes from LetterboxDependencyHelper with hierarchy flag enabled`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputLeash = mock<SurfaceControl>()
                val inputToken = mock<WindowContainerToken>()
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.token = inputToken
                        ti.appCompatTaskInfo.setIsLeafTask(true)
                    }
                    leash { inputLeash }
                }

                r.shouldSupportInputSurface(shouldSupportInputSurface = true)
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertTrue(event.supportsInput)
                }

                r.shouldSupportInputSurface(shouldSupportInputSurface = false)
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertFalse(event.supportsInput)
                }
            }
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_APP_COMPAT_REFACTORING,
        Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS,
        Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
    )
    fun `With TaskInfo token leash and configuration are persisted with hierarchy enabled`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputToken = mock<WindowContainerToken>()
                val inputLeash = mock<SurfaceControl>()
                val inputConfiguration = Configuration()
                inputChange {
                    runningTaskInfo { ti ->
                        ti.taskId = 10
                        ti.token = inputToken
                        ti.appCompatTaskInfo.setIsLeafTask(true)
                        ti.configuration.setTo(inputConfiguration)
                    }
                    leash { inputLeash }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    assertEquals(inputToken, event.containerToken)
                }
                r.useRepository { repository ->
                    val item = repository.find(10)
                    assertNotNull(item)
                    assertEquals(item.containerToken, inputToken)
                    assertEquals(item.containerLeash, inputLeash)
                    assertEquals(0, item.configuration.compareTo(inputConfiguration))
                }
            }
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<TaskInfoLetterboxLifecycleEventFactoryRobotTest>) {
        val robot = TaskInfoLetterboxLifecycleEventFactoryRobotTest()
        consumer.accept(robot)
    }

    /** Robot contextual to [TaskInfoLetterboxLifecycleEventFactory]. */
    class TaskInfoLetterboxLifecycleEventFactoryRobotTest {

        private val dependencyHelper: LetterboxDependenciesHelper =
            mock<LetterboxDependenciesHelper>()

        val letterboxTaskInfoRepository = LetterboxTaskInfoRepository()

        val taskIdResolver = TaskIdResolver(letterboxTaskInfoRepository)

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxLifecycleEventFactory = {
            TaskInfoLetterboxLifecycleEventFactory(
                dependencyHelper,
                letterboxTaskInfoRepository,
                taskIdResolver,
            )
        }

        fun shouldSupportInputSurface(shouldSupportInputSurface: Boolean) {
            doReturn(shouldSupportInputSurface)
                .`when`(dependencyHelper)
                .shouldSupportInputSurface(any())
        }

        fun useRepository(consumer: (LetterboxTaskInfoRepository) -> Unit) {
            consumer(letterboxTaskInfoRepository)
        }
    }
}
