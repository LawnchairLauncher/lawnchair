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

import android.graphics.Rect
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.lifecycle.FakeLetterboxLifecycleEventFactory.Companion.FAKE_EVENT
import com.android.wm.shell.util.testLetterboxLifecycleEventFactory
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [MultiLetterboxLifecycleEventFactory].
 *
 * Build/Install/Run: atest WMShellUnitTests:MultiLetterboxLifecycleEventFactoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class MultiLetterboxLifecycleEventFactoryTest : ShellTestCase() {

    @Test
    fun `canHandle is invoked until a first true is found`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                r.addCandidate(canHandleReturn = false)
                r.addCandidate(canHandleReturn = true)
                r.addCandidate(canHandleReturn = false)
                inputChange {
                    // No specific Change initialization required.
                }
                validateCanHandle { canHandler ->
                    assertTrue(canHandler)
                    r.assertOnCandidate(0) { f -> assertEquals(1, f.canHandleInvokeTimes) }
                    r.assertOnCandidate(1) { f -> assertEquals(1, f.canHandleInvokeTimes) }
                    r.assertOnCandidate(2) { f -> assertEquals(0, f.canHandleInvokeTimes) }
                }
            }
        }
    }

    @Test
    fun `canHandle returns false if no one can handle`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                r.addCandidate(canHandleReturn = false)
                r.addCandidate(canHandleReturn = false)
                r.addCandidate(canHandleReturn = false)
                inputChange {
                    // No specific Change initialization required.
                }
                validateCanHandle { canHandler -> assertFalse(canHandler) }
            }
        }
    }

    @Test
    fun `No LetterboxLifecycleEventFactory used if no one can handle`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                r.addCandidate(canHandleReturn = false)
                r.addCandidate(canHandleReturn = false)
                r.addCandidate(canHandleReturn = false)
                inputChange {
                    // No specific Change initialization required.
                }
                validateCreateLifecycleEvent { event ->
                    assertNull(event)
                    for (pos in 0..2) {
                        r.assertOnCandidate(pos) { f ->
                            assertEquals(1, f.canHandleInvokeTimes)
                            assertEquals(0, f.createLifecycleEventInvokeTimes)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Only the one which can handle creates the LetterboxLifecycleEvent`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                r.addCandidate(canHandleReturn = false)
                r.addCandidate(
                    canHandleReturn = true,
                    eventToReturn =
                        LetterboxLifecycleEvent(taskId = 30, taskBounds = Rect(1, 2, 3, 4)),
                )
                r.addCandidate(canHandleReturn = false)
                inputChange {
                    // No specific Change initialization required.
                }
                validateCreateLifecycleEvent { event ->
                    assertNotNull(event)
                    r.assertOnCandidate(0) { f ->
                        assertEquals(1, f.canHandleInvokeTimes)
                        assertEquals(0, f.createLifecycleEventInvokeTimes)
                    }
                    r.assertOnCandidate(1) { f ->
                        assertEquals(1, f.canHandleInvokeTimes)
                        assertEquals(1, f.createLifecycleEventInvokeTimes)
                    }
                    r.assertOnCandidate(2) { f ->
                        assertEquals(0, f.canHandleInvokeTimes)
                        assertEquals(0, f.createLifecycleEventInvokeTimes)
                    }
                    assertEquals(30, event.taskId)
                    assertEquals(Rect(1, 2, 3, 4), event.taskBounds)
                }
            }
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<LetterboxLifecycleControllerImplRobotTest>) {
        val robot = LetterboxLifecycleControllerImplRobotTest()
        consumer.accept(robot)
    }

    /** Robot contextual to [MultiLetterboxLifecycleEventFactory]. */
    class LetterboxLifecycleControllerImplRobotTest {

        private val candidates = mutableListOf<FakeLetterboxLifecycleEventFactory>()

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxLifecycleEventFactory = {
            MultiLetterboxLifecycleEventFactory(candidates)
        }

        fun addCandidate(
            canHandleReturn: Boolean = true,
            eventToReturn: LetterboxLifecycleEvent = FAKE_EVENT,
        ) {
            candidates.add(FakeLetterboxLifecycleEventFactory(canHandleReturn, eventToReturn))
        }

        fun assertOnCandidate(
            position: Int,
            consumer: (FakeLetterboxLifecycleEventFactory) -> Unit,
        ) {
            consumer(candidates[position])
        }
    }
}
