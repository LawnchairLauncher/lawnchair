/*
 * Copyright 2025 The Android Open Source Project
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
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.testing.AndroidTestingRunner
import android.view.IWindowSession
import android.view.InputChannel
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.suppliers.InputChannelSupplier
import com.android.wm.shell.common.suppliers.WindowSessionSupplier
import com.android.wm.shell.compatui.letterbox.LetterboxControllerRobotTest.Companion.ANOTHER_TASK_ID
import com.android.wm.shell.compatui.letterbox.LetterboxMatchers.asAnyMode
import com.android.wm.shell.compatui.letterbox.events.ReachabilityGestureListener
import com.android.wm.shell.compatui.letterbox.events.ReachabilityGestureListenerFactory
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModelTestsBase.Companion.TAG
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Tests for [LetterboxInputController].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:LetterboxInputControllerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxInputControllerTest : ShellTestCase() {

    @Test
    fun `When creation is requested the surface is created if not present`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()

            r.checkInputSurfaceBuilderInvoked()
        }
    }

    @Test
    fun `When creation is requested multiple times the input surface is created once`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()

            r.checkInputSurfaceBuilderInvoked(times = 1)
        }
    }

    @Test
    fun `A different input surface is created for every key`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest()
            r.sendCreateSurfaceRequest(taskId = ANOTHER_TASK_ID)
            r.sendCreateSurfaceRequest(taskId = ANOTHER_TASK_ID)

            r.checkInputSurfaceBuilderInvoked(times = 2)
        }
    }

    @Test
    fun `Created spy surface is removed once`() {
        runTestScenario { r ->
            r.sendCreateSurfaceRequest()
            r.checkInputSurfaceBuilderInvoked()

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
            r.sendUpdateSurfaceVisibilityRequest(visible = true, taskId = ANOTHER_TASK_ID)

            r.checkVisibilityUpdated(expectedVisibility = true)
        }
    }

    @Test
    fun `Only existing surfaces receive taskBounds update`() {
        runTestScenario { r ->
            r.sendUpdateSurfaceBoundsRequest(
                taskBounds = Rect(0, 0, 2000, 1000),
                activityBounds = Rect(500, 0, 1500, 1000)
            )

            r.checkUpdateSessionRegion(times = 0, region = Region(0, 0, 2000, 1000))
            r.checkSurfaceSizeUpdated(times = 0, expectedWidth = 2000, expectedHeight = 1000)

            r.resetTransitionTest()

            r.sendCreateSurfaceRequest()
            r.sendUpdateSurfaceBoundsRequest(
                taskBounds = Rect(0, 0, 2000, 1000),
                activityBounds = Rect(500, 0, 1500, 1000)
            )
            r.checkUpdateSessionRegion(region = Region(0, 0, 2000, 1000))
            r.checkSurfaceSizeUpdated(expectedWidth = 2000, expectedHeight = 1000)
        }
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    fun runTestScenario(consumer: Consumer<InputLetterboxControllerRobotTest>) {
        consumer.accept(InputLetterboxControllerRobotTest(mContext).apply { initController() })
    }

    class InputLetterboxControllerRobotTest(private val context: Context) :
        LetterboxControllerRobotTest() {

        private val inputSurfaceBuilder: LetterboxInputSurfaceBuilder
        private val handler = Handler(Looper.getMainLooper())
        private val reachabilityListener: ReachabilityGestureListener
        private val reachabilityListenerFactory: ReachabilityGestureListenerFactory
        private val windowSessionSupplier: WindowSessionSupplier
        private val windowSession: IWindowSession
        private val inputChannelSupplier: InputChannelSupplier

        init {
            inputSurfaceBuilder = getLetterboxInputSurfaceBuilderMock()
            reachabilityListener = mock<ReachabilityGestureListener>()
            reachabilityListenerFactory = mock<ReachabilityGestureListenerFactory>()
            doReturn(reachabilityListener).`when`(reachabilityListenerFactory)
                .createReachabilityGestureListener(any(), anyOrNull())
            windowSessionSupplier = mock<WindowSessionSupplier>()
            windowSession = mock<IWindowSession>()
            doReturn(windowSession).`when`(windowSessionSupplier).get()
            inputChannelSupplier = mock<InputChannelSupplier>()
            val inputChannels = InputChannel.openInputChannelPair(TAG)
            inputChannels.first().dispose()
            doReturn(inputChannels[1]).`when`(inputChannelSupplier).get()
        }

        override fun buildController(): LetterboxController =
            LetterboxInputController(
                context,
                handler,
                inputSurfaceBuilder,
                reachabilityListenerFactory,
                windowSessionSupplier,
                inputChannelSupplier
            )

        fun checkInputSurfaceBuilderInvoked(
            times: Int = 1,
            name: String = "",
            callSite: String = ""
        ) {
            verify(inputSurfaceBuilder, times(times)).createInputSurface(
                eq(transaction),
                eq(parentLeash),
                name.asAnyMode(),
                callSite.asAnyMode()
            )
        }

        fun checkUpdateSessionRegion(times: Int = 1, displayId: Int = DISPLAY_ID, region: Region) {
            verify(windowSession, times(times)).updateInputChannel(
                any(),
                anyOrNull(),
                eq(displayId),
                any(),
                any(),
                any(),
                any(),
                eq(region)
            )
        }
    }
}
