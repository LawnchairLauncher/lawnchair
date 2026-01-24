/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Tests for [MixedLetterboxController].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:MixedLetterboxControllerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class MixedLetterboxControllerTest : ShellTestCase() {

    @Test
    fun `When strategy is SINGLE_SURFACE and a create request is sent multi are destroyed`() {
        runTestScenario { r ->
            r.configureStrategyFor(LetterboxMode.SINGLE_SURFACE)
            r.sendCreateSurfaceRequest()
            r.checkCreateInvokedOnSingleController()
            r.checkDestroyInvokedOnMultiController()
        }
    }

    @Test
    fun `When strategy is MULTIPLE_SURFACES and a create request is sent single is destroyed`() {
        runTestScenario { r ->
            r.configureStrategyFor(LetterboxMode.MULTIPLE_SURFACES)
            r.sendCreateSurfaceRequest()
            r.checkDestroyInvokedOnSingleController()
            r.checkCreateInvokedOnMultiController()
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<MixedLetterboxControllerRobotTest>) {
        consumer.accept(MixedLetterboxControllerRobotTest().apply { initController() })
    }

    class MixedLetterboxControllerRobotTest : LetterboxControllerRobotTest() {
        val singleLetterboxController: SingleSurfaceLetterboxController =
            mock<SingleSurfaceLetterboxController>()
        val multipleLetterboxController: MultiSurfaceLetterboxController =
            mock<MultiSurfaceLetterboxController>()
        val controllerStrategy: LetterboxControllerStrategy = mock<LetterboxControllerStrategy>()
        val inputController: LetterboxInputController = mock<LetterboxInputController>()

        fun configureStrategyFor(letterboxMode: LetterboxMode) {
            doReturn(letterboxMode).`when`(controllerStrategy).getLetterboxImplementationMode()
        }

        fun checkCreateInvokedOnSingleController(times: Int = 1) {
            verify(singleLetterboxController, times(times)).createLetterboxSurface(
                any(),
                any(),
                any(),
                any()
            )
        }

        fun checkCreateInvokedOnMultiController(times: Int = 1) {
            verify(multipleLetterboxController, times(times)).createLetterboxSurface(
                any(),
                any(),
                any(),
                any()
            )
        }

        fun checkDestroyInvokedOnSingleController(times: Int = 1) {
            verify(singleLetterboxController, times(times)).destroyLetterboxSurface(any(), any())
        }

        fun checkDestroyInvokedOnMultiController(times: Int = 1) {
            verify(multipleLetterboxController, times(times)).destroyLetterboxSurface(any(), any())
        }

        override fun buildController(): LetterboxController = MixedLetterboxController(
            singleLetterboxController,
            multipleLetterboxController,
            controllerStrategy,
            inputController
        )
    }
}
