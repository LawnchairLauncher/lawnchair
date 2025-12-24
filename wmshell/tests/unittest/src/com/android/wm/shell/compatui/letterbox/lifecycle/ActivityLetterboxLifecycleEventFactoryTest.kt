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

import android.content.res.Configuration
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
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
 * Tests for [ActivityLetterboxLifecycleEventFactory].
 *
 * Build/Install/Run: atest WMShellUnitTests:ActivityLetterboxLifecycleEventFactoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class ActivityLetterboxLifecycleEventFactoryTest : ShellTestCase() {

    @Test
    fun `Change without ActivityTransitionInfo cannot create the event`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    // Empty Change
                }
                validateCanHandle { canHandle -> assertFalse(canHandle) }
            }
        }
    }

    @Test
    fun `Read Task bounds from endAbsBounds in Change`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val testLeash = mock<SurfaceControl>()
                val testToken = mock<WindowContainerToken>()
                inputChange {
                    activityTransitionInfo { taskId = 10 }
                    endAbsBounds = Rect(100, 50, 2000, 1500)
                }
                r.addToTaskRepository(
                    10,
                    LetterboxTaskInfoState(testToken, testLeash, configuration = Configuration()),
                )
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertEquals(Rect(0, 0, 1900, 1450), event?.taskBounds)
                }
            }
        }
    }

    @Test
    fun `Read Letterbox bounds from activityTransitionInfo and endAbsBounds in Change`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val testLeash = mock<SurfaceControl>()
                val testToken = mock<WindowContainerToken>()
                inputChange {
                    endAbsBounds = Rect(100, 50, 2000, 1500)
                    activityTransitionInfo {
                        taskId = 10
                        appCompatTransitionInfo { letterboxBounds = Rect(500, 50, 1500, 800) }
                    }
                }
                r.addToTaskRepository(
                    10,
                    LetterboxTaskInfoState(testToken, testLeash, configuration = Configuration()),
                )
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertEquals(Rect(0, 0, 1900, 1450), event?.taskBounds)
                    assertEquals(Rect(400, 0, 1400, 750), event?.letterboxBounds)
                }
            }
        }
    }

    @Test
    fun `Uses leash and token from the repository`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val testLeash = mock<SurfaceControl>()
                val testToken = mock<WindowContainerToken>()
                r.addToTaskRepository(
                    10,
                    LetterboxTaskInfoState(testToken, testLeash, configuration = Configuration()),
                )
                inputChange { activityTransitionInfo { taskId = 10 } }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event ->
                    assertEquals(testLeash, event?.taskLeash)
                    assertEquals(testToken, event?.containerToken)
                }
            }
        }
    }

    @Test
    fun `supportsInput comes from LetterboxDependencyHelper`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange { activityTransitionInfo { taskId = 10 } }
                val testLeash = mock<SurfaceControl>()
                val testToken = mock<WindowContainerToken>()
                r.addToTaskRepository(
                    10,
                    LetterboxTaskInfoState(testToken, testLeash, configuration = Configuration()),
                )
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
    fun `Event is null if repository has no task data`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange { activityTransitionInfo { taskId = 10 } }
                validateCanHandle { canHandle -> assertTrue(canHandle) }
                validateCreateLifecycleEvent { event -> assertNull(event) }
            }
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<ActivityLetterboxLifecycleEventFactoryRobotTest>) {
        val robot = ActivityLetterboxLifecycleEventFactoryRobotTest()
        consumer.accept(robot)
    }

    /** Robot contextual to [ActivityLetterboxLifecycleEventFactory]. */
    class ActivityLetterboxLifecycleEventFactoryRobotTest {

        private val letterboxTaskInfoRepository: LetterboxTaskInfoRepository =
            LetterboxTaskInfoRepository()

        private val dependencyHelper: LetterboxDependenciesHelper =
            mock<LetterboxDependenciesHelper>()

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxLifecycleEventFactory = {
            ActivityLetterboxLifecycleEventFactory(letterboxTaskInfoRepository, dependencyHelper)
        }

        fun shouldSupportInputSurface(shouldSupportInputSurface: Boolean) {
            doReturn(shouldSupportInputSurface)
                .`when`(dependencyHelper)
                .shouldSupportInputSurface(any())
        }

        fun addToTaskRepository(key: Int, state: LetterboxTaskInfoState) {
            letterboxTaskInfoRepository.insert(key = key, item = state, overrideIfPresent = true)
        }
    }
}
