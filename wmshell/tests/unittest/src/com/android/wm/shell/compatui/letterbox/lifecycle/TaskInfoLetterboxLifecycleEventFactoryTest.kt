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

import android.graphics.Point
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.util.testLetterboxLifecycleEventFactory
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Tests for [TaskInfoLetterboxLifecycleEventFactory].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:TaskInfoLetterboxLifecycleEventFactoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class TaskInfoLetterboxLifecycleEventFactoryTest {

    @Test
    fun `Change without TaskInfo cannot create the event and returns null`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    // Empty Change
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == false)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event == null)
                }
            }
        }
    }

    @Test
    fun `With TaskInfo taskBounds are calculated from endAbsBounds and endRelOffset`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == true)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event != null)
                    assert(event?.taskBounds == Rect(100, 200, 500, 1000))
                }
            }
        }
    }

    @Test
    fun `With TaskInfo letterboxBounds are null when Activity is not letterboxed`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    runningTaskInfo { ti ->
                        ti.appCompatTaskInfo.isTopActivityLetterboxed = false
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == true)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event != null)
                    assert(event?.letterboxBounds == null)
                }
            }
        }
    }

    @Test
    fun `With TaskInfo letterboxBounds from appCompatTaskInfo when Activity is letterboxed`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    runningTaskInfo { ti ->
                        ti.appCompatTaskInfo.isTopActivityLetterboxed = true
                        ti.appCompatTaskInfo.topActivityLetterboxBounds = Rect(1, 2, 3, 4)
                    }
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == true)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event != null)
                    assert(event?.letterboxBounds == Rect(1, 2, 3, 4))
                }
            }
        }
    }

    @Test
    fun `With TaskInfo leash from Change`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputLeash = mock<SurfaceControl>()
                inputChange {
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                    leash { inputLeash }
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == true)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event != null)
                    assert(event?.taskLeash == inputLeash)
                }
            }
        }
    }

    @Test
    fun `With TaskInfo token from TaskInfo`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                val inputToken = mock<WindowContainerToken>()
                inputChange {
                    endAbsBounds = Rect(0, 0, 500, 1000)
                    endRelOffset = Point(100, 200)
                    token { inputToken }
                }
                validateCanHandle { canHandle ->
                    assert(canHandle == true)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event != null)
                    assert(event?.containerToken == inputToken)
                }
            }
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<TaskInfoLetterboxLifecycleEventFactoryRobotTest>) {
        val robot = TaskInfoLetterboxLifecycleEventFactoryRobotTest()
        consumer.accept(robot)
    }

    /**
     * Robot contextual to [TaskInfoLetterboxLifecycleEventFactory].
     */
    class TaskInfoLetterboxLifecycleEventFactoryRobotTest {

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxLifecycleEventFactory = {
            TaskInfoLetterboxLifecycleEventFactory()
        }
    }
}
