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

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.util.testLetterboxLifecycleEventFactory
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn

import org.mockito.kotlin.mock

/**
 * Tests for [SkipLetterboxLifecycleEventFactory].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:SkipLetterboxLifecycleEventFactoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class SkipLetterboxLifecycleEventFactoryTest : ShellTestCase() {

    @Test
    fun `Factory is active when Change is a DesksOrganizer change`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    // Empty Change
                }
                r.configureDesksOrganizer(isDeskChange = true)
                validateCanHandle { canHandle ->
                    assert(canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event == null)
                }
            }
        }
    }

    @Test
    fun `Factory is skipped when Change is NOT a DesksOrganizer change`() {
        runTestScenario { r ->
            testLetterboxLifecycleEventFactory(r.getLetterboxLifecycleEventFactory()) {
                inputChange {
                    // Empty Change
                }
                r.configureDesksOrganizer(isDeskChange = false)
                validateCanHandle { canHandle ->
                    assert(!canHandle)
                }
                validateCreateLifecycleEvent { event ->
                    assert(event != null)
                    assert(event?.type == LetterboxLifecycleEventType.NONE)
                }
            }
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<DisableLetterboxLifecycleEventFactoryRobotTest>) {
        val robot = DisableLetterboxLifecycleEventFactoryRobotTest()
        consumer.accept(robot)
    }

    /**
     * Robot contextual to [TaskInfoLetterboxLifecycleEventFactory].
     */
    class DisableLetterboxLifecycleEventFactoryRobotTest {

        private val desksOrganizer: DesksOrganizer = mock<DesksOrganizer>()

        fun configureDesksOrganizer(isDeskChange: Boolean) {
            doReturn(isDeskChange).`when`(desksOrganizer).isDeskChange(any())
        }

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxLifecycleEventFactory = {
            SkipLetterboxLifecycleEventFactory(desksOrganizer)
        }
    }
}
