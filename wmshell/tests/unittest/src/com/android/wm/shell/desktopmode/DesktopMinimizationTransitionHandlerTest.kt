/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WindowingMode
import android.os.Handler
import android.platform.test.annotations.DisableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.SurfaceControl
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CLOSE
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.transition.Transitions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DesktopMinimizationTransitionHandlerTest : ShellTestCase() {

    private val testExecutor = mock<ShellExecutor>()
    private val closingTaskLeash = mock<SurfaceControl>()
    private val displayController = mock<DisplayController>()

    private lateinit var handler: DesktopMinimizationTransitionHandler

    @Before
    fun setUp() {
        handler =
            DesktopMinimizationTransitionHandler(
                testExecutor,
                testExecutor,
                displayController,
                mock<Handler>(),
            )
        whenever(displayController.getDisplayContext(any())).thenReturn(mContext)
    }

    @Test
    fun handleRequest_returnsNull() {
        assertNull(handler.handleRequest(mock(), mock()))
    }

    @Test
    fun startAnimation_openTransition_returnsFalse() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info =
                    createTransitionInfo(
                        type = WindowManager.TRANSIT_OPEN,
                        task = createTask(WINDOWING_MODE_FREEFORM),
                    ),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertFalse("Should not animate open transition", animates)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun startAnimation_toBackTransitionFullscreenTask_returnsFalse() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info = createTransitionInfo(task = createTask(WINDOWING_MODE_FULLSCREEN)),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertFalse("Should not animate fullscreen task to back transition", animates)
    }

    @Test
    fun startAnimation_toBackTransitionOpeningFreeformTask_returnsFalse() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info =
                    createTransitionInfo(
                        changeMode = WindowManager.TRANSIT_OPEN,
                        task = createTask(WINDOWING_MODE_FREEFORM),
                    ),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertFalse("Should not animate opening freeform task to back transition", animates)
    }

    @Test
    fun startAnimation_toBackTransitionToBackFreeformTask_returnsTrue() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info = createTransitionInfo(task = createTask(WINDOWING_MODE_FREEFORM)),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should animate going to back freeform task close transition", animates)
    }

    @Test
    fun startAnimation_closeTransitionClosingFreeformTask_returnsTrue() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info =
                    createTransitionInfo(
                        type = TRANSIT_CLOSE,
                        changeMode = TRANSIT_CLOSE,
                        task = createTask(WINDOWING_MODE_FREEFORM),
                    ),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should animate going to back freeform task close transition", animates)
    }

    @Test
    fun startAnimation_minimizeTransitionToBackFreeformTask_returnsTrue() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info =
                    createTransitionInfo(
                        type = Transitions.TRANSIT_MINIMIZE,
                        task = createTask(WINDOWING_MODE_FREEFORM),
                    ),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should animate going to back freeform task minimize transition", animates)
    }

    private fun createTransitionInfo(
        type: Int = WindowManager.TRANSIT_TO_BACK,
        changeMode: Int = WindowManager.TRANSIT_TO_BACK,
        task: RunningTaskInfo,
    ): TransitionInfo =
        TransitionInfo(type, /* flags= */ 0).apply {
            addChange(
                TransitionInfo.Change(mock(), closingTaskLeash).apply {
                    mode = changeMode
                    parent = null
                    taskInfo = task
                }
            )
        }

    private fun createTask(@WindowingMode windowingMode: Int): RunningTaskInfo =
        TestRunningTaskInfoBuilder()
            .setActivityType(ACTIVITY_TYPE_STANDARD)
            .setWindowingMode(windowingMode)
            .build()
}
