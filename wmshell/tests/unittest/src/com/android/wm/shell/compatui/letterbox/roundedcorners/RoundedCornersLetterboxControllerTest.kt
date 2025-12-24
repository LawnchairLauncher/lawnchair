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

package com.android.wm.shell.compatui.letterbox.roundedcorners

import android.content.res.Configuration
import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.window.flags2.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.compatui.letterbox.LetterboxController
import com.android.wm.shell.compatui.letterbox.LetterboxControllerRobotTest
import com.android.wm.shell.compatui.letterbox.LetterboxControllerRobotTest.Companion.ANOTHER_TASK_ID
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoRepository
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoState
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Tests for [RoundedCornersLetterboxController].
 *
 * Build/Install/Run: atest WMShellUnitTests:RoundedCornersLetterboxControllerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class RoundedCornersLetterboxControllerTest : ShellTestCase() {

    @Test
    fun `Rounded corners are NOT created if configuration is missing from the repository`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()

            r.checkSurfaceBuilderInvoked(times = 0)
        }
    }

    @Test
    fun `Rounded corners are created if configuration is missing from the repository`() {
        runTestScenario { r ->
            r.configureRepository()
            r.sendCreateSurfaceRequest()

            r.checkSurfaceBuilderInvoked(times = 1)
        }
    }

    @Test
    fun `When creation is requested multiple times rounded corners are created once`() {
        runTestScenario { r ->
            r.configureRepository()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()

            r.checkSurfaceBuilderInvoked(times = 1)
        }
    }

    @Test
    fun `A different surface is created for every key`() {
        runTestScenario { r ->
            r.configureRepository()
            r.configureRepository(taskId = ANOTHER_TASK_ID)
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest(taskId = ANOTHER_TASK_ID)
            r.sendCreateSurfaceRequest(taskId = ANOTHER_TASK_ID)

            r.checkSurfaceBuilderInvoked(times = 2)
        }
    }

    @Test
    fun `Created surface is removed once`() {
        runTestScenario { r ->
            r.configureRepository()
            r.sendCreateSurfaceRequest()
            r.checkSurfaceBuilderInvoked()

            r.sendDestroySurfaceRequest()
            r.sendDestroySurfaceRequest()
            r.sendDestroySurfaceRequest()

            r.checkRoundedCornersSurfaceReleased()
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS_ANIMATION)
    fun `Only existing surfaces receive visibility update with animation`() {
        runTestScenario { r ->
            r.configureRepository()
            r.configureRepository(taskId = ANOTHER_TASK_ID)
            r.sendCreateSurfaceRequest()
            r.sendUpdateSurfaceVisibilityRequest(visible = true)
            r.sendUpdateSurfaceVisibilityRequest(visible = true, taskId = ANOTHER_TASK_ID)

            r.checkRoundedCornersVisibilityUpdated(
                times = 1,
                expectedVisibility = true,
                immediate = false,
            )
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS_ANIMATION)
    fun `Only existing surfaces receive visibility update without animation`() {
        runTestScenario { r ->
            r.configureRepository()
            r.configureRepository(taskId = ANOTHER_TASK_ID)
            r.sendCreateSurfaceRequest()
            r.sendUpdateSurfaceVisibilityRequest(visible = true)
            r.sendUpdateSurfaceVisibilityRequest(visible = true, taskId = ANOTHER_TASK_ID)

            r.checkRoundedCornersVisibilityUpdated(times = 1, expectedVisibility = true)
        }
    }

    @Test
    fun `Only existing surfaces receive taskBounds update`() {
        runTestScenario { r ->
            r.configureRepository()
            r.sendUpdateSurfaceBoundsRequest(
                taskBounds = Rect(0, 0, 2000, 1000),
                activityBounds = Rect(500, 0, 1500, 1000),
            )

            r.checkSurfacePositionUpdated(times = 0)
            r.checkSurfaceSizeUpdated(times = 0)

            r.resetTransitionTest()

            r.sendCreateSurfaceRequest()
            r.sendUpdateSurfaceBoundsRequest(
                taskBounds = Rect(0, 0, 2000, 1000),
                activityBounds = Rect(500, 0, 1500, 1000),
            )
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<RoundedCornersControllerRobotTest>) {
        consumer.accept(RoundedCornersControllerRobotTest().apply { initController() })
    }

    class RoundedCornersControllerRobotTest : LetterboxControllerRobotTest() {

        private val letterboxRepository: LetterboxTaskInfoRepository
        private val roundedCornersSurfaceBuilder: RoundedCornersSurfaceBuilder
        private val roundedCornersSurface: RoundedCornersSurface
        private val executor: TestShellExecutor
        private val testConfiguration: Configuration

        init {
            testConfiguration = Configuration()
            executor = TestShellExecutor()
            letterboxRepository = LetterboxTaskInfoRepository()
            roundedCornersSurface = mock<RoundedCornersSurface>()
            roundedCornersSurfaceBuilder = mock<RoundedCornersSurfaceBuilder>()
            doReturn(roundedCornersSurface)
                .`when`(roundedCornersSurfaceBuilder)
                .create(any(), any())
        }

        override fun buildController(): LetterboxController =
            RoundedCornersLetterboxController(
                executor,
                roundedCornersSurfaceBuilder,
                letterboxRepository,
            )

        fun configureRepository(taskId: Int = TASK_ID) {
            letterboxRepository.insert(
                taskId,
                LetterboxTaskInfoState(TOKEN, parentLeash, configuration = testConfiguration),
            )
        }

        fun checkSurfaceBuilderInvoked(times: Int = 1) {
            verify(roundedCornersSurfaceBuilder, times(times))
                .create(eq(testConfiguration), eq(parentLeash))
        }

        fun checkRoundedCornersVisibilityUpdated(
            times: Int = 1,
            expectedVisibility: Boolean,
            immediate: Boolean = true,
        ) {
            verify(roundedCornersSurface, times(times))
                .setCornersVisibility(any(), eq(expectedVisibility), eq(immediate))
        }

        fun checkRoundedCornersSurfaceReleased(times: Int = 1) {
            verify(roundedCornersSurface, times(times)).release()
        }
    }
}
