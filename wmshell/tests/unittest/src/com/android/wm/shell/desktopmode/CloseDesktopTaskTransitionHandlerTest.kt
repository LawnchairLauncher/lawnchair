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
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.ShellExecutor
import java.util.function.Supplier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.mock

/**
 * Test class for [CloseDesktopTaskTransitionHandler]
 *
 * Usage: atest WMShellUnitTests:CloseDesktopTaskTransitionHandlerTest
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class CloseDesktopTaskTransitionHandlerTest : ShellTestCase() {

    @Mock lateinit var testExecutor: ShellExecutor
    @Mock lateinit var closingTaskLeash: SurfaceControl
    @Mock lateinit var mockHandler: Handler

    private val transactionSupplier = Supplier { mock<SurfaceControl.Transaction>() }

    private lateinit var handler: CloseDesktopTaskTransitionHandler

    @Before
    fun setUp() {
        handler =
            CloseDesktopTaskTransitionHandler(
                context,
                testExecutor,
                testExecutor,
                transactionSupplier,
                mockHandler,
            )
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
    fun startAnimation_closeTransitionFullscreenTask_returnsFalse() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info = createTransitionInfo(task = createTask(WINDOWING_MODE_FULLSCREEN)),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertFalse("Should not animate fullscreen task close transition", animates)
    }

    @Test
    fun startAnimation_closeTransitionOpeningFreeformTask_returnsFalse() {
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

        assertFalse("Should not animate opening freeform task close transition", animates)
    }

    @Test
    fun startAnimation_closeTransitionClosingFreeformTask_returnsTrue() {
        val animates =
            handler.startAnimation(
                transition = mock(),
                info = createTransitionInfo(task = createTask(WINDOWING_MODE_FREEFORM)),
                startTransaction = mock(),
                finishTransaction = mock(),
                finishCallback = {},
            )

        assertTrue("Should animate closing freeform task close transition", animates)
    }

    private fun createTransitionInfo(
        type: Int = WindowManager.TRANSIT_CLOSE,
        changeMode: Int = WindowManager.TRANSIT_CLOSE,
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
