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

import android.content.Context
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.internal.hidden_from_bootclasspath.com.android.window.flags2.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode
import com.android.wm.shell.compatui.letterbox.roundedcorners.RoundedCornersLetterboxController
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
 * Build/Install/Run: atest WMShellUnitTests:MixedLetterboxControllerTest
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

    @Test
    fun `When strategy shouldSupportInputSurface is true input surface is created`() {
        runTestScenario { r ->
            r.configureStrategyFor(LetterboxMode.SINGLE_SURFACE)
            r.configureStrategyFor(shouldSupportInputSurface = true)
            r.sendCreateSurfaceRequest()
            r.checkCreateInvokedOnInputController()
        }
    }

    @Test
    fun `When strategy shouldSupportInputSurface is false input surface is destroyed`() {
        runTestScenario { r ->
            r.configureStrategyFor(LetterboxMode.SINGLE_SURFACE)
            r.configureStrategyFor(shouldSupportInputSurface = false)
            r.sendCreateSurfaceRequest()
            r.checkDestroyInvokedOnInputController()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS)
    fun `Corners are created when enabled with radius more than zero`() {
        runTestScenario { r ->
            r.configureStrategyFor(LetterboxMode.SINGLE_SURFACE)
            r.configureStrategyFor { configuration ->
                configuration.setLetterboxActivityCornersRadius(10)
            }
            r.sendCreateSurfaceRequest()
            r.checkCreateInvokedOnRoundedCornersController(times = 1)
            r.checkDestroyInvokedOnRoundedCornersController(times = 0)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS)
    fun `Corners are destroyed when enabled with radius less or equals to zero`() {
        runTestScenario { r ->
            r.configureStrategyFor(LetterboxMode.SINGLE_SURFACE)
            r.configureStrategyFor { configuration ->
                configuration.setLetterboxActivityCornersRadius(0)
            }
            r.sendCreateSurfaceRequest()
            r.checkCreateInvokedOnRoundedCornersController(times = 0)
            r.checkDestroyInvokedOnRoundedCornersController(times = 1)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS)
    fun `Corners are not created when disabled even with radius more than zero`() {
        runTestScenario { r ->
            r.configureStrategyFor(LetterboxMode.SINGLE_SURFACE)
            r.configureStrategyFor { configuration ->
                configuration.setLetterboxActivityCornersRadius(10)
            }
            r.sendCreateSurfaceRequest()
            r.checkCreateInvokedOnRoundedCornersController(times = 0)
            r.checkDestroyInvokedOnRoundedCornersController(times = 0)
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<MixedLetterboxControllerRobotTest>) {
        consumer.accept(MixedLetterboxControllerRobotTest(mContext).apply { initController() })
    }

    class MixedLetterboxControllerRobotTest(ctx: Context) : LetterboxControllerRobotTest() {
        val letterboxConfiguration: LetterboxConfiguration = LetterboxConfiguration(ctx)
        val singleLetterboxController: SingleSurfaceLetterboxController =
            mock<SingleSurfaceLetterboxController>()
        val multipleLetterboxController: MultiSurfaceLetterboxController =
            mock<MultiSurfaceLetterboxController>()
        val controllerStrategy: LetterboxControllerStrategy = mock<LetterboxControllerStrategy>()
        val inputController: LetterboxInputController = mock<LetterboxInputController>()
        val roundedCornersController: RoundedCornersLetterboxController =
            mock<RoundedCornersLetterboxController>()

        fun configureStrategyFor(letterboxMode: LetterboxMode) {
            doReturn(letterboxMode).`when`(controllerStrategy).getLetterboxImplementationMode()
        }

        fun configureStrategyFor(shouldSupportInputSurface: Boolean) {
            doReturn(shouldSupportInputSurface)
                .`when`(controllerStrategy)
                .shouldSupportInputSurface()
        }

        fun configureStrategyFor(consumer: (LetterboxConfiguration) -> Unit) {
            consumer(letterboxConfiguration)
        }

        fun checkCreateInvokedOnSingleController(times: Int = 1) {
            verify(singleLetterboxController, times(times))
                .createLetterboxSurface(any(), any(), any(), any())
        }

        fun checkCreateInvokedOnMultiController(times: Int = 1) {
            verify(multipleLetterboxController, times(times))
                .createLetterboxSurface(any(), any(), any(), any())
        }

        fun checkCreateInvokedOnInputController(times: Int = 1) {
            verify(inputController, times(times)).createLetterboxSurface(any(), any(), any(), any())
        }

        fun checkCreateInvokedOnRoundedCornersController(times: Int = 1) {
            verify(roundedCornersController, times(times))
                .createLetterboxSurface(any(), any(), any(), any())
        }

        fun checkDestroyInvokedOnSingleController(times: Int = 1) {
            verify(singleLetterboxController, times(times)).destroyLetterboxSurface(any(), any())
        }

        fun checkDestroyInvokedOnMultiController(times: Int = 1) {
            verify(multipleLetterboxController, times(times)).destroyLetterboxSurface(any(), any())
        }

        fun checkDestroyInvokedOnInputController(times: Int = 1) {
            verify(multipleLetterboxController, times(times)).destroyLetterboxSurface(any(), any())
        }

        fun checkDestroyInvokedOnRoundedCornersController(times: Int = 1) {
            verify(roundedCornersController, times(times)).destroyLetterboxSurface(any(), any())
        }

        override fun buildController(): LetterboxController =
            MixedLetterboxController(
                letterboxConfiguration,
                singleLetterboxController,
                multipleLetterboxController,
                controllerStrategy,
                inputController,
                roundedCornersController,
            )
    }
}
