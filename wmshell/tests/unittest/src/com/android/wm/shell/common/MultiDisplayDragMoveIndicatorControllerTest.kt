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
package com.android.wm.shell.common

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.platform.test.annotations.EnableFlags
import android.testing.TestableResources
import android.view.SurfaceControl
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.window.flags2.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.MultiDisplayTestUtil.TestDisplay
import com.android.wm.shell.desktopmode.FakeShellDesktopState
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import java.util.function.Supplier
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [MultiDisplayDragMoveIndicatorController].
 *
 * Build/Install/Run: atest WMShellUnitTests:MultiDisplayDragMoveIndicatorControllerTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class MultiDisplayDragMoveIndicatorControllerTest : ShellTestCase() {
    private val displayController = mock<DisplayController>()
    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val indicatorSurfaceFactory = mock<MultiDisplayDragMoveIndicatorSurface.Factory>()
    private val shellDesktopState = FakeShellDesktopState(FakeDesktopState())
    private val indicatorSurface0 = mock<MultiDisplayDragMoveIndicatorSurface>()
    private val indicatorSurface1 = mock<MultiDisplayDragMoveIndicatorSurface>()
    private val indicatorSurface2 = mock<MultiDisplayDragMoveIndicatorSurface>()
    private val transaction = mock<SurfaceControl.Transaction>()
    private val transactionSupplier = mock<Supplier<SurfaceControl.Transaction>>()
    private val taskInfo = mock<RunningTaskInfo>()
    private val taskInfo2 = mock<RunningTaskInfo>()
    private val taskLeash = mock<SurfaceControl>()
    private val taskLeash2 = mock<SurfaceControl>()
    private val displayContext0 = mock<Context>()
    private val displayContext1 = mock<Context>()
    private lateinit var spyDisplayLayout0: DisplayLayout
    private lateinit var spyDisplayLayout1: DisplayLayout

    private lateinit var resources: TestableResources

    private lateinit var controller: MultiDisplayDragMoveIndicatorController

    @Before
    fun setUp() {
        resources = mContext.getOrCreateTestableResources()
        val resourceConfiguration = Configuration()
        resourceConfiguration.uiMode = 0
        resources.overrideConfiguration(resourceConfiguration)

        controller =
            MultiDisplayDragMoveIndicatorController(
                displayController,
                rootTaskDisplayAreaOrganizer,
                indicatorSurfaceFactory,
                shellDesktopState,
            )

        TestDisplay.DISPLAY_0.getSpyDisplayLayout(resources.resources)
        spyDisplayLayout0 = TestDisplay.DISPLAY_0.getSpyDisplayLayout(resources.resources)
        spyDisplayLayout1 = TestDisplay.DISPLAY_1.getSpyDisplayLayout(resources.resources)

        taskInfo.taskId = TASK_ID
        taskInfo2.taskId = TASK_ID_2
        whenever(displayController.getDisplayLayout(0)).thenReturn(spyDisplayLayout0)
        whenever(displayController.getDisplayLayout(1)).thenReturn(spyDisplayLayout1)
        whenever(displayController.getDisplayContext(0)).thenReturn(displayContext0)
        whenever(displayController.getDisplayContext(1)).thenReturn(displayContext1)
        whenever(indicatorSurfaceFactory.create(eq(displayContext0), eq(taskLeash)))
            .thenReturn(indicatorSurface0)
        whenever(indicatorSurfaceFactory.create(eq(displayContext1), eq(taskLeash)))
            .thenReturn(indicatorSurface1)
        whenever(indicatorSurfaceFactory.create(eq(displayContext0), eq(taskLeash2)))
            .thenReturn(indicatorSurface2)
        whenever(transactionSupplier.get()).thenReturn(transaction)
        shellDesktopState.canBeWindowDropTarget = true
    }

    @Test
    fun onDrag_boundsNotIntersectWithDisplay_noIndicator() {
        controller.onDragMove(
            RectF(2000f, 2000f, 2100f, 2200f), // not intersect with any display
            currentDisplayId = 0,
            startDisplayId = 0,
            taskLeash,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }

        verify(indicatorSurfaceFactory, never()).create(any(), any())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DROP_SMOOTH_TRANSITION,
        Flags.FLAG_ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX,
    )
    fun onDrag_boundsIntersectWithStartDisplay_showIndicator() {
        controller.onDragMove(
            RectF(100f, 100f, 200f, 200f), // intersect with display 0
            currentDisplayId = 0,
            startDisplayId = 0,
            taskLeash,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }

        verify(indicatorSurfaceFactory).create(eq(displayContext0), eq(taskLeash))
        verify(indicatorSurface0)
            .show(
                transaction,
                taskInfo,
                rootTaskDisplayAreaOrganizer,
                0,
                Rect(100, 100, 200, 200),
                MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
                1.0f,
            )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DROP_SMOOTH_TRANSITION,
        Flags.FLAG_ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX,
    )
    fun onDrag_boundsIntersectWithDesktopModeUnsupportedDisplay_noIndicatorOnThatDisplay() {
        shellDesktopState.overrideWindowDropTargetEligibility[1] = false

        controller.onDragMove(
            RectF(100f, -100f, 200f, 200f), // intersect with display 0 and 1
            currentDisplayId = 1,
            startDisplayId = 0,
            taskLeash,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }

        verify(indicatorSurfaceFactory, never()).create(eq(displayContext1), any())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DROP_SMOOTH_TRANSITION,
        Flags.FLAG_ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX,
    )
    fun onDrag_boundsIntersectWithNonStartDisplayAndMoveAway_showHideAndDisposeIndicator() {
        controller.onDragMove(
            RectF(100f, -100f, 200f, 200f), // intersect with display 0 and 1
            currentDisplayId = 1,
            startDisplayId = 0,
            taskLeash,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }

        verify(indicatorSurfaceFactory).create(eq(displayContext0), eq(taskLeash))
        verify(indicatorSurfaceFactory).create(eq(displayContext1), eq(taskLeash))
        verify(indicatorSurface0)
            .show(
                transaction,
                taskInfo,
                rootTaskDisplayAreaOrganizer,
                0,
                Rect(100, -100, 200, 200),
                MultiDisplayDragMoveIndicatorSurface.Visibility.TRANSLUCENT,
                1f,
            )
        verify(indicatorSurface1)
            .show(
                transaction,
                taskInfo,
                rootTaskDisplayAreaOrganizer,
                1,
                Rect(0, 1800, 200, 2400),
                MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
                spyDisplayLayout1.densityDpi().toFloat() / spyDisplayLayout0.densityDpi().toFloat(),
            )

        controller.onDragMove(
            RectF(100f, 0f, 200f, 300f), // intersect with only display 0
            currentDisplayId = 0,
            startDisplayId = 0,
            taskLeash,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }

        verify(indicatorSurface0)
            .relayout(
                any(),
                eq(transaction),
                eq(MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE),
            )
        verify(indicatorSurface1)
            .relayout(
                any(),
                eq(transaction),
                eq(MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE),
            )

        controller.onDragEnd(TASK_ID, transaction)

        verify(indicatorSurface0).dispose(transaction)
        verify(indicatorSurface1).dispose(transaction)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_WINDOW_DROP_SMOOTH_TRANSITION,
        Flags.FLAG_ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX,
    )
    fun disposeAllIndicators_verifyAllIndicatorsDisposed() {
        // Drag a first task to create indicators on two displays
        controller.onDragMove(
            RectF(100f, -100f, 200f, 200f), // intersect with display 0 and 1
            currentDisplayId = 1,
            startDisplayId = 0,
            taskLeash,
            taskInfo,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }

        // Drag the second task to create an indicator on one display
        controller.onDragMove(
            RectF(150f, 150f, 250f, 250f), // intersect with display 0 only
            currentDisplayId = 0,
            startDisplayId = 0,
            taskLeash2,
            taskInfo2,
            displayIds = setOf(0, 1),
        ) {
            transaction
        }

        // Verify indicators for both tasks are created
        verify(indicatorSurfaceFactory).create(eq(displayContext0), eq(taskLeash))
        verify(indicatorSurfaceFactory).create(eq(displayContext1), eq(taskLeash))
        verify(indicatorSurfaceFactory).create(eq(displayContext0), eq(taskLeash2))

        // Dispose all indicators
        controller.disposeAllIndicators(transaction)

        // Verify indicators for both tasks are disposed
        verify(indicatorSurface0).dispose(transaction)
        verify(indicatorSurface1).dispose(transaction)
        verify(indicatorSurface2).dispose(transaction)
    }

    companion object {
        private const val TASK_ID = 10
        private const val TASK_ID_2 = 11
    }
}
