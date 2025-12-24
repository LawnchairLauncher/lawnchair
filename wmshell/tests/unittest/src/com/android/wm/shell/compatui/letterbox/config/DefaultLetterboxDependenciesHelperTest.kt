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

package com.android.wm.shell.compatui.letterbox.config

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.util.testLetterboxDependenciesHelper
import java.util.function.Consumer
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tests for [DefaultLetterboxDependenciesHelper].
 *
 * Build/Install/Run: atest WMShellUnitTests:DefaultLetterboxDependenciesHelperTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class DefaultLetterboxDependenciesHelperTest : ShellTestCase() {

    @Test
    fun `When in Desktop Windowing the input surface should not be created`() {
        runTestScenario { r ->
            testLetterboxDependenciesHelper(r.getLetterboxLifecycleEventFactory()) {
                inputChange {}
                r.configureDesktopRepository(isAnyDeskActive = true)
                validateShouldSupportInputSurface { shouldSupportInputSurface ->
                    assertFalse(shouldSupportInputSurface)
                }
            }
        }
    }

    @Test
    fun `When NOT in Desktop Windowing the input surface should be created`() {
        runTestScenario { r ->
            testLetterboxDependenciesHelper(r.getLetterboxLifecycleEventFactory()) {
                inputChange {}
                r.configureDesktopRepository(isAnyDeskActive = false)
                validateShouldSupportInputSurface { shouldSupportInputSurface ->
                    assertTrue(shouldSupportInputSurface)
                }
            }
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<LetterboxDependenciesHelperRobotTest>) {
        val robot = LetterboxDependenciesHelperRobotTest()
        consumer.accept(robot)
    }

    /** Robot contextual to [LetterboxDependenciesHelper]. */
    class LetterboxDependenciesHelperRobotTest {

        private val desktopRepository = mock<DesktopRepository>()

        fun configureDesktopRepository(isAnyDeskActive: Boolean) {
            doReturn(isAnyDeskActive).`when`(desktopRepository).isAnyDeskActive(any())
        }

        fun getLetterboxLifecycleEventFactory(): () -> LetterboxDependenciesHelper = {
            DefaultLetterboxDependenciesHelper(desktopRepository)
        }
    }
}
