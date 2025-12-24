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

package com.android.wm.shell.windowdecor

import android.app.ActivityManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Looper
import android.os.SystemClock
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.Display.DEFAULT_DISPLAY
import android.view.MotionEvent
import androidx.test.filters.SmallTest
import com.android.window.flags2.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestHandler
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiDisplayTestUtil.TestDisplay.DISPLAY_0
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper.TestWindowDecoration
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper.createAppHeaderTask
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [DesktopModeTouchEventListener].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopModeTouchEventListenerTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class DesktopModeTouchEventListenerTest : ShellTestCase() {
    private val mockSplitScreenController = mock<SplitScreenController>()
    private val mockDesktopTasksController = mock<DesktopTasksController>()
    private val mockTaskOperations = mock<TaskOperations>()
    private val mockWindowDecorationActions = mock<WindowDecorationActions>()
    private val mockDisplayController = mock<DisplayController>()

    private val testDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val testHandler = TestHandler(Looper.getMainLooper())
    private val testExecutor = TestShellExecutor()
    private val testScope = TestScope(testDispatcher)

    private val shellInit = ShellInit(testExecutor)
    private val desktopState = FakeDesktopState()
    private val desktopConfig = FakeDesktopConfig()

    private val windowDecorations = mutableMapOf<Int, WindowDecorationWrapper>()
    private lateinit var userRepositories: DesktopUserRepositories

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val display =
            context.getSystemService(DisplayManager::class.java).getDisplay(DEFAULT_DISPLAY)
        val displayLayout = DISPLAY_0.getSpyDisplayLayout(mContext.resources)
        whenever(mockDisplayController.getDisplay(DEFAULT_DISPLAY)).thenReturn(display)
        whenever(mockDisplayController.getDisplayLayout(DEFAULT_DISPLAY)).thenReturn(displayLayout)
        userRepositories =
            DesktopUserRepositories(
                shellInit,
                mock(),
                mock(),
                mock(),
                testScope.backgroundScope,
                testScope.backgroundScope,
                mock(),
                desktopState,
                desktopConfig,
            )
    }

    @After
    fun tearDown() {
        windowDecorations.forEach { (_, decor) -> decor.close() }
        windowDecorations.clear()
        testScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
        Flags.FLAG_FIX_LEAKING_VISUAL_INDICATOR,
    )
    fun testAppHeaderClick_closesTask() =
        testScope.runTest {
            val decor = setUpWindowDecoration(createAppHeaderTask(TASK_BOUNDS))
            val closeBtn =
                assertNotNull(
                    decor.findViewById(com.android.wm.shell.R.id.close_window),
                    "Expected decoration to have a close button",
                )
            val x = closeBtn.x + 1f
            val y = closeBtn.y + 1f

            val startTime = SystemClock.uptimeMillis()
            closeBtn.dispatchTouchEvent(
                MotionEvent.obtain(
                    /* downTime = */ startTime,
                    /* eventTime = */ startTime,
                    /* action = */ MotionEvent.ACTION_DOWN,
                    /* x = */ x,
                    /* y = */ y,
                    /* metaState = */ 0,
                )
            )
            closeBtn.dispatchTouchEvent(
                MotionEvent.obtain(
                    /* downTime = */ startTime,
                    /* eventTime = */ startTime + 20,
                    /* action = */ MotionEvent.ACTION_UP,
                    /* x = */ x,
                    /* y = */ y,
                    /* metaState = */ 0,
                )
            )
            flushAll()

            verify(mockWindowDecorationActions)
                .onClose(taskId = decor.defaultWindowDecoration.taskInfo.taskId)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
        Flags.FLAG_FIX_LEAKING_VISUAL_INDICATOR,
    )
    fun testAppHeaderClick_withActionMoveButNoBoundsChange_closesTask() =
        testScope.runTest {
            val decor = setUpWindowDecoration(createAppHeaderTask(TASK_BOUNDS))
            val closeBtn =
                assertNotNull(
                    decor.findViewById(com.android.wm.shell.R.id.close_window),
                    "Expected decoration to have a close button",
                )
            val x = closeBtn.x + 1f
            val y = closeBtn.y + 1f

            val startTime = SystemClock.uptimeMillis()
            closeBtn.dispatchTouchEvent(
                MotionEvent.obtain(
                    /* downTime = */ startTime,
                    /* eventTime = */ startTime,
                    /* action = */ MotionEvent.ACTION_DOWN,
                    /* x = */ x,
                    /* y = */ y,
                    /* metaState = */ 0,
                )
            )
            // Add an ACTION_MOVE event without actual position/bounds change, to make sure the
            // handler doesn't get stuck thinking this view is being dragged. See b/364990718.
            closeBtn.dispatchTouchEvent(
                MotionEvent.obtain(
                    /* downTime = */ startTime,
                    /* eventTime = */ startTime + 10,
                    /* action = */ MotionEvent.ACTION_MOVE,
                    /* x = */ x,
                    /* y = */ y,
                    /* metaState = */ 0,
                )
            )
            closeBtn.dispatchTouchEvent(
                MotionEvent.obtain(
                    /* downTime = */ startTime,
                    /* eventTime = */ startTime + 20,
                    /* action = */ MotionEvent.ACTION_UP,
                    /* x = */ x,
                    /* y = */ y,
                    /* metaState = */ 0,
                )
            )
            flushAll()

            verify(mockWindowDecorationActions)
                .onClose(taskId = decor.defaultWindowDecoration.taskInfo.taskId)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
        Flags.FLAG_FIX_LEAKING_VISUAL_INDICATOR,
    )
    fun testAppHeaderClick_withActionMoveButNoBoundsChange_invokesDragPositioningEndCallback() =
        testScope.runTest {
            val decor = setUpWindowDecoration(createAppHeaderTask(TASK_BOUNDS))
            val appHeader =
                assertNotNull(
                    decor.findViewById(com.android.wm.shell.R.id.desktop_mode_caption),
                    "Expected decoration to have an app header view",
                )
            val x = 300f
            val y = 50f

            // Simulate a click on the app header that includes an ACTION_MOVE event with no bounds
            // change.
            val startTime = SystemClock.uptimeMillis()
            appHeader.dispatchTouchEvent(
                MotionEvent.obtain(
                    /* downTime = */ startTime,
                    /* eventTime = */ startTime,
                    /* action = */ MotionEvent.ACTION_DOWN,
                    /* x = */ x,
                    /* y = */ y,
                    /* metaState = */ 0,
                )
            )
            appHeader.dispatchTouchEvent(
                MotionEvent.obtain(
                    /* downTime = */ startTime,
                    /* eventTime = */ startTime + 10,
                    /* action = */ MotionEvent.ACTION_MOVE,
                    /* x = */ x,
                    /* y = */ y,
                    /* metaState = */ 0,
                )
            )
            val upEvent =
                MotionEvent.obtain(
                    /* downTime = */ startTime,
                    /* eventTime = */ startTime + 20,
                    /* action = */ MotionEvent.ACTION_UP,
                    /* x = */ x,
                    /* y = */ y,
                    /* metaState = */ 0,
                )
            appHeader.dispatchTouchEvent(upEvent)
            flushAll()

            // Verify that the drag positioning end callback was invoked even if not actually
            // drag-moved, to allow the controller to know about the gesture completing and to
            // clean up its state. See b/418225224.
            verify(mockDesktopTasksController)
                .onDragPositioningEnd(
                    taskInfo = eq(decor.defaultWindowDecoration.taskInfo),
                    taskSurface = any(),
                    displayId = eq(decor.defaultWindowDecoration.taskInfo.displayId),
                    inputCoordinate = any(),
                    currentDragBounds = any(),
                    validDragArea = any(),
                    dragStartBounds = eq(TASK_BOUNDS),
                    motionEvent = eq(upEvent),
                )
        }

    private fun setUpWindowDecoration(
        taskInfo: ActivityManager.RunningTaskInfo
    ): TestWindowDecoration =
        WindowDecorationTestHelper.createWindowDecoration(
                context = context,
                taskInfo = taskInfo,
                windowDecorationFinder = { taskId: Int -> windowDecorations[taskId] },
                desktopState = desktopState,
                desktopConfig = desktopConfig,
                scope = testScope.backgroundScope,
                handler = testHandler,
                executor = testExecutor,
                displayController = mockDisplayController,
                desktopUserRepositories = userRepositories,
                splitScreenController = mockSplitScreenController,
                desktopTasksController = mockDesktopTasksController,
                taskOperations = mockTaskOperations,
                windowDecorationActions = mockWindowDecorationActions,
            )
            .also {
                assertThat(windowDecorations[taskInfo.taskId]).isNull()
                windowDecorations[taskInfo.taskId] = it.wrapped
                it.relayout(taskInfo)
            }

    private fun flushAll() {
        testScope.advanceUntilIdle()
        testExecutor.flushAll()
        TestableLooper.get(this).processAllMessages()
    }

    private companion object {
        private val TASK_BOUNDS = Rect(200, 200, 800, 600)
    }
}
