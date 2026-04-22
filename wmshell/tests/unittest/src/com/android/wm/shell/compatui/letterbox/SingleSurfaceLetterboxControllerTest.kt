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
 * Tests for [SingleSurfaceLetterboxController].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:SingleSurfaceLetterboxControllerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class SingleSurfaceLetterboxControllerTest : ShellTestCase() {

    @Test
    fun `When creation is requested the surface is created if not present`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()

            r.checkSurfaceBuilderInvoked()
        }
    }

    @Test
    fun `When creation is requested multiple times the surface is created once`() {
        runTestScenario { r ->
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
            r.sendCreateSurfaceRequest()
            r.checkSurfaceBuilderInvoked()

            r.sendDestroySurfaceRequest()
            r.sendDestroySurfaceRequest()
            r.sendDestroySurfaceRequest()

            r.checkTransactionRemovedInvoked()
        }
    }

    @Test
    fun `Only existing surfaces receive visibility update`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()
            r.sendUpdateSurfaceVisibilityRequest(visible = true)
            r.sendUpdateSurfaceVisibilityRequest(visible = true, displayId = ANOTHER_TASK_ID)

            r.checkVisibilityUpdated(times = 2, expectedVisibility = true)
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

            r.resetTransitionTest()

            r.sendCreateSurfaceRequest()
            r.sendUpdateSurfaceBoundsRequest(
                taskBounds = Rect(0, 0, 2000, 1000),
                activityBounds = Rect(500, 0, 1500, 1000)
            )
            r.checkSurfacePositionUpdated(times = 1, expectedX = 0f, expectedY = 0f)
            r.checkSurfaceSizeUpdated(times = 1, expectedWidth = 2000, expectedHeight = 1000)
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<SingleLetterboxControllerRobotTest>) {
        consumer.accept(SingleLetterboxControllerRobotTest(mContext).apply { initController() })
    }

    class SingleLetterboxControllerRobotTest(context: Context) :
        LetterboxControllerRobotTest() {

        private val letterboxConfiguration: LetterboxConfiguration
        private val surfaceBuilder: LetterboxSurfaceBuilder

        init {
            letterboxConfiguration = LetterboxConfiguration(context)
            surfaceBuilder = LetterboxSurfaceBuilder(letterboxConfiguration)
            spyOn(surfaceBuilder)
        }

        override fun buildController(): LetterboxController =
            SingleSurfaceLetterboxController(surfaceBuilder)

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
