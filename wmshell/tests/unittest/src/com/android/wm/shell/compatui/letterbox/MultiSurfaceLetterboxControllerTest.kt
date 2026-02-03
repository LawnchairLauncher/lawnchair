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
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.LetterboxControllerRobotTest.Companion.ANOTHER_TASK_ID
import com.android.wm.shell.compatui.letterbox.LetterboxMatchers.asAnyMode
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Tests for [MultiSurfaceLetterboxController].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:MultiSurfaceLetterboxControllerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class MultiSurfaceLetterboxControllerTest : ShellTestCase() {

    @Test
    fun `When creation is requested the surfaces are created if not present`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()

            r.checkSurfaceBuilderInvoked(times = 4)
        }
    }

    @Test
    fun `When creation is requested multiple times the surfaces are created once`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()

            r.checkSurfaceBuilderInvoked(times = 4)
        }
    }

    @Test
    fun `Different surfaces are created for every key`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest(taskId = ANOTHER_TASK_ID)
            r.sendCreateSurfaceRequest(taskId = ANOTHER_TASK_ID)

            r.checkSurfaceBuilderInvoked(times = 8)
        }
    }

    @Test
    fun `Created surface are removed once`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()
            r.checkSurfaceBuilderInvoked(times = 4)

            r.sendDestroySurfaceRequest()
            r.sendDestroySurfaceRequest()
            r.sendDestroySurfaceRequest()

            r.checkTransactionRemovedInvoked(times = 4)
        }
    }

    @Test
    fun `Only existing surfaces receive visibility update`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()
            r.sendUpdateSurfaceVisibilityRequest(visible = true)
            r.sendUpdateSurfaceVisibilityRequest(visible = true, taskId = ANOTHER_TASK_ID)

            r.checkVisibilityUpdated(times = 4, expectedVisibility = true)
        }
    }

    @Test
    fun `Only existing surfaces receive taskBounds update`() {
        runTestScenario { r ->
            r.sendUpdateSurfaceBoundsRequest(
                taskBounds = Rect(0, 0, 2000, 1000),
                activityBounds = Rect(500, 0, 1500, 1000)
            )

            r.checkSurfacePositionUpdated(times = 0)
            r.checkSurfaceSizeUpdated(times = 0)

            r.sendCreateSurfaceRequest()

            // Pillarbox.
            r.sendUpdateSurfaceBoundsRequest(
                taskBounds = Rect(0, 0, 2000, 1000),
                activityBounds = Rect(500, 0, 1500, 1000)
            )
            // The Left and Top surfaces.
            r.checkSurfacePositionUpdated(times = 2, expectedX = 0f, expectedY = 0f)
            // The Right surface.
            r.checkSurfacePositionUpdated(times = 1, expectedX = 1500f, expectedY = 0f)
            // The Bottom surface.
            r.checkSurfacePositionUpdated(times = 1, expectedX = 0f, expectedY = 1000f)
            // Left and Right surface.
            r.checkSurfaceSizeUpdated(times = 2, expectedWidth = 500, expectedHeight = 1000)
            // Top and Button.
            r.checkSurfaceSizeUpdated(times = 2, expectedWidth = 2000, expectedHeight = 0)

            r.resetTransitionTest()

            // Letterbox.
            r.sendUpdateSurfaceBoundsRequest(
                taskBounds = Rect(0, 0, 1000, 2000),
                activityBounds = Rect(0, 500, 1000, 1500)
            )
            // Top and Left surfaces.
            r.checkSurfacePositionUpdated(times = 2, expectedX = 0f, expectedY = 0f)
            // Bottom surface.
            r.checkSurfacePositionUpdated(times = 1, expectedX = 0f, expectedY = 1500f)
            // Right surface.
            r.checkSurfacePositionUpdated(times = 1, expectedX = 1000f, expectedY = 0f)

            // Left and Right surfaces.
            r.checkSurfaceSizeUpdated(times = 2, expectedWidth = 0, expectedHeight = 2000)
            // Top and Button surfaces,
            r.checkSurfaceSizeUpdated(times = 2, expectedWidth = 1000, expectedHeight = 500)
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<MultiLetterboxControllerRobotTest>) {
        consumer.accept(MultiLetterboxControllerRobotTest(mContext).apply { initController() })
    }

    class MultiLetterboxControllerRobotTest(context: Context) :
        LetterboxControllerRobotTest() {

        private val letterboxConfiguration: LetterboxConfiguration
        private val surfaceBuilder: LetterboxSurfaceBuilder

        init {
            letterboxConfiguration = LetterboxConfiguration(context)
            surfaceBuilder = LetterboxSurfaceBuilder(letterboxConfiguration)
            spyOn(surfaceBuilder)
        }

        override fun buildController(): LetterboxController =
            MultiSurfaceLetterboxController(surfaceBuilder)

        fun checkSurfaceBuilderInvoked(times: Int = 1, name: String = "", callSite: String = "") {
            verify(surfaceBuilder, times(times)).createSurface(
                eq(transaction),
                eq(parentLeash),
                name.asAnyMode(),
                callSite.asAnyMode(),
                any()
            )
        }
    }
}
