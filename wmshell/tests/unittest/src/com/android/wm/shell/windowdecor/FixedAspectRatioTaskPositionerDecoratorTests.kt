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

package com.android.wm.shell.windowdecor

import android.app.ActivityManager
import android.graphics.PointF
import android.graphics.Rect
import android.util.MathUtils.abs
import android.util.MathUtils.max
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_UNDEFINED
import com.android.wm.shell.windowdecor.DragPositioningCallback.CtrlType
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlin.math.min
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never

/**
 * Tests for the [FixedAspectRatioTaskPositionerDecorator], written in parameterized form to check
 * decorators behaviour for different variations of drag actions.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:FixedAspectRatioTaskPositionerDecoratorTests
 */
@SmallTest
@RunWith(TestParameterInjector::class)
class FixedAspectRatioTaskPositionerDecoratorTests : ShellTestCase(){
    @Mock
    private lateinit var mockDesktopWindowDecoration: DesktopModeWindowDecoration
    @Mock
    private lateinit var mockTaskPositioner: VeiledResizeTaskPositioner

    private lateinit var decoratedTaskPositioner: FixedAspectRatioTaskPositionerDecorator

    @Before
    fun setUp() {
        mockDesktopWindowDecoration.mTaskInfo = ActivityManager.RunningTaskInfo().apply {
            isResizeable = false
            configuration.windowConfiguration.setBounds(PORTRAIT_BOUNDS)
        }
        doReturn(PORTRAIT_BOUNDS).`when`(mockTaskPositioner).onDragPositioningStart(
            any(), any(), any(), any())
        doReturn(Rect()).`when`(mockTaskPositioner).onDragPositioningMove(any(), any(), any())
        doReturn(Rect()).`when`(mockTaskPositioner).onDragPositioningEnd(any(), any(), any())
        decoratedTaskPositioner = spy(
            FixedAspectRatioTaskPositionerDecorator(
            mockDesktopWindowDecoration, mockTaskPositioner)
        )
    }

    @Test
    fun testOnDragPositioningStart_noAdjustment(
        @TestParameter testCase: ResizeableOrNotResizingTestCases
    ) {
        val originalX = 0f
        val originalY = 0f
        mockDesktopWindowDecoration.mTaskInfo = ActivityManager.RunningTaskInfo().apply {
            isResizeable = testCase.isResizeable
        }

        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, originalX, originalY)

        val capturedValues = getLatestOnStartArguments()
        assertThat(capturedValues.ctrlType).isEqualTo(testCase.ctrlType)
        assertThat(capturedValues.x).isEqualTo(originalX)
        assertThat(capturedValues.y).isEqualTo(originalY)
    }

    @Test
    fun testOnDragPositioningStart_cornerResize_noAdjustment(
        @TestParameter testCase: CornerResizeStartTestCases
    ) {
        val originalX = 0f
        val originalY = 0f

        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, originalX, originalY)

        val capturedValues = getLatestOnStartArguments()
        assertThat(capturedValues.ctrlType).isEqualTo(testCase.ctrlType)
        assertThat(capturedValues.x).isEqualTo(originalX)
        assertThat(capturedValues.y).isEqualTo(originalY)
    }

    @Test
    fun testOnDragPositioningStart_edgeResize_ctrlTypeAdjusted(
        @TestParameter testCase: EdgeResizeStartTestCases, @TestParameter orientation: Orientation
    ) {
        val startingBounds = getAndMockBounds(orientation)
        val startingPoint = getEdgeStartingPoint(
            testCase.ctrlType, testCase.additionalEdgeCtrlType, startingBounds)

        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, startingPoint.x, startingPoint.y)

        val adjustedCtrlType = testCase.ctrlType + testCase.additionalEdgeCtrlType
        val capturedValues = getLatestOnStartArguments()
        assertThat(capturedValues.ctrlType).isEqualTo(adjustedCtrlType)
        assertThat(capturedValues.x).isEqualTo(startingPoint.x)
        assertThat(capturedValues.y).isEqualTo(startingPoint.y)
    }

    @Test
    fun testOnDragPositioningMove_noAdjustment(
        @TestParameter testCase: ResizeableOrNotResizingTestCases
    ) {
        val originalX = 0f
        val originalY = 0f
        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, originalX, originalX)
        mockDesktopWindowDecoration.mTaskInfo = ActivityManager.RunningTaskInfo().apply {
            isResizeable = testCase.isResizeable
        }

        decoratedTaskPositioner.onDragPositioningMove(
            DISPLAY_ID, originalX + SMALL_DELTA, originalY + SMALL_DELTA)

        val capturedValues = getLatestOnMoveArguments()
        assertThat(capturedValues.x).isEqualTo(originalX + SMALL_DELTA)
        assertThat(capturedValues.y).isEqualTo(originalY + SMALL_DELTA)
    }

    @Test
    fun testOnDragPositioningMove_cornerResize_invalidRegion_noResize(
        @TestParameter testCase: InvalidCornerResizeTestCases,
        @TestParameter orientation: Orientation
    ) {
        val startingBounds = getAndMockBounds(orientation)
        val startingPoint = getCornerStartingPoint(testCase.ctrlType, startingBounds)

        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, startingPoint.x, startingPoint.y)

        val updatedBounds = decoratedTaskPositioner.onDragPositioningMove(
            DISPLAY_ID,
            startingPoint.x + testCase.dragDelta.x,
            startingPoint.y + testCase.dragDelta.y)

        verify(mockTaskPositioner, never()).onDragPositioningMove(any(), any(), any())
        assertThat(updatedBounds).isEqualTo(startingBounds)
    }


    @Test
    fun testOnDragPositioningMove_cornerResize_validRegion_resizeToAdjustedCoordinates(
        @TestParameter testCase: ValidCornerResizeTestCases,
        @TestParameter orientation: Orientation
    ) {
        val startingBounds = getAndMockBounds(orientation)
        val startingPoint = getCornerStartingPoint(testCase.ctrlType, startingBounds)

        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, startingPoint.x, startingPoint.y)

        decoratedTaskPositioner.onDragPositioningMove(
            DISPLAY_ID,
            startingPoint.x + testCase.dragDelta.x,
            startingPoint.y + testCase.dragDelta.y)

        val adjustedDragDelta = calculateAdjustedDelta(
            testCase.ctrlType, testCase.dragDelta, orientation)
        val capturedValues = getLatestOnMoveArguments()
        val absChangeX = abs(capturedValues.x - startingPoint.x)
        val absChangeY = abs(capturedValues.y - startingPoint.y)
        val resultAspectRatio = max(absChangeX, absChangeY) / min(absChangeX, absChangeY)
        assertThat(capturedValues.x).isEqualTo(startingPoint.x + adjustedDragDelta.x)
        assertThat(capturedValues.y).isEqualTo(startingPoint.y + adjustedDragDelta.y)
        assertThat(resultAspectRatio).isEqualTo(STARTING_ASPECT_RATIO)
    }

    @Test
    fun testOnDragPositioningMove_edgeResize_resizeToAdjustedCoordinates(
        @TestParameter testCase: EdgeResizeTestCases,
        @TestParameter orientation: Orientation
    ) {
        val startingBounds = getAndMockBounds(orientation)
        val startingPoint = getEdgeStartingPoint(
            testCase.ctrlType, testCase.additionalEdgeCtrlType, startingBounds)

        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, startingPoint.x, startingPoint.y)

        decoratedTaskPositioner.onDragPositioningMove(
            DISPLAY_ID,
            startingPoint.x + testCase.dragDelta.x,
            startingPoint.y + testCase.dragDelta.y)

        val adjustedDragDelta = calculateAdjustedDelta(
            testCase.ctrlType + testCase.additionalEdgeCtrlType,
            testCase.dragDelta,
            orientation)
        val capturedValues = getLatestOnMoveArguments()
        val absChangeX = abs(capturedValues.x - startingPoint.x)
        val absChangeY = abs(capturedValues.y - startingPoint.y)
        val resultAspectRatio = max(absChangeX, absChangeY) / min(absChangeX, absChangeY)
        assertThat(capturedValues.x).isEqualTo(startingPoint.x + adjustedDragDelta.x)
        assertThat(capturedValues.y).isEqualTo(startingPoint.y + adjustedDragDelta.y)
        assertThat(resultAspectRatio).isEqualTo(STARTING_ASPECT_RATIO)
    }

    @Test
    fun testOnDragPositioningEnd_noAdjustment(
        @TestParameter testCase: ResizeableOrNotResizingTestCases
    ) {
        val originalX = 0f
        val originalY = 0f
        decoratedTaskPositioner.onDragPositioningStart(testCase.ctrlType, DISPLAY_ID,
            originalX, originalX)
        mockDesktopWindowDecoration.mTaskInfo = ActivityManager.RunningTaskInfo().apply {
            isResizeable = testCase.isResizeable
        }

        decoratedTaskPositioner.onDragPositioningEnd(
            DISPLAY_ID, originalX + SMALL_DELTA, originalY + SMALL_DELTA)

        val capturedValues = getLatestOnEndArguments()
        assertThat(capturedValues.x).isEqualTo(originalX + SMALL_DELTA)
        assertThat(capturedValues.y).isEqualTo(originalY + SMALL_DELTA)
    }

    @Test
    fun testOnDragPositioningEnd_cornerResize_invalidRegion_endsAtPreviousValidPoint(
        @TestParameter testCase: InvalidCornerResizeTestCases,
        @TestParameter orientation: Orientation
    ) {
        val startingBounds = getAndMockBounds(orientation)
        val startingPoint = getCornerStartingPoint(testCase.ctrlType, startingBounds)

        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, startingPoint.x, startingPoint.y)

        decoratedTaskPositioner.onDragPositioningEnd(
            DISPLAY_ID,
            startingPoint.x + testCase.dragDelta.x,
            startingPoint.y + testCase.dragDelta.y)

        val capturedValues = getLatestOnEndArguments()
        assertThat(capturedValues.x).isEqualTo(startingPoint.x)
        assertThat(capturedValues.y).isEqualTo(startingPoint.y)
    }

    @Test
    fun testOnDragPositioningEnd_cornerResize_validRegion_endAtAdjustedCoordinates(
        @TestParameter testCase: ValidCornerResizeTestCases,
        @TestParameter orientation: Orientation
    ) {
        val startingBounds = getAndMockBounds(orientation)
        val startingPoint = getCornerStartingPoint(testCase.ctrlType, startingBounds)

        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, startingPoint.x, startingPoint.y)

        decoratedTaskPositioner.onDragPositioningEnd(
            DISPLAY_ID,
            startingPoint.x + testCase.dragDelta.x,
            startingPoint.y + testCase.dragDelta.y)

        val adjustedDragDelta = calculateAdjustedDelta(
            testCase.ctrlType, testCase.dragDelta, orientation)
        val capturedValues = getLatestOnEndArguments()
        val absChangeX = abs(capturedValues.x - startingPoint.x)
        val absChangeY = abs(capturedValues.y - startingPoint.y)
        val resultAspectRatio = max(absChangeX, absChangeY) / min(absChangeX, absChangeY)
        assertThat(capturedValues.x).isEqualTo(startingPoint.x + adjustedDragDelta.x)
        assertThat(capturedValues.y).isEqualTo(startingPoint.y + adjustedDragDelta.y)
        assertThat(resultAspectRatio).isEqualTo(STARTING_ASPECT_RATIO)
    }

    @Test
    fun testOnDragPositioningEnd_edgeResize_endAtAdjustedCoordinates(
        @TestParameter testCase: EdgeResizeTestCases,
        @TestParameter orientation: Orientation
    ) {
        val startingBounds = getAndMockBounds(orientation)
        val startingPoint = getEdgeStartingPoint(
            testCase.ctrlType, testCase.additionalEdgeCtrlType, startingBounds)

        decoratedTaskPositioner.onDragPositioningStart(
            testCase.ctrlType, DISPLAY_ID, startingPoint.x, startingPoint.y)

        decoratedTaskPositioner.onDragPositioningEnd(
            DISPLAY_ID,
            startingPoint.x + testCase.dragDelta.x,
            startingPoint.y + testCase.dragDelta.y)

        val adjustedDragDelta = calculateAdjustedDelta(
            testCase.ctrlType + testCase.additionalEdgeCtrlType,
            testCase.dragDelta,
            orientation)
        val capturedValues = getLatestOnEndArguments()
        val absChangeX = abs(capturedValues.x - startingPoint.x)
        val absChangeY = abs(capturedValues.y - startingPoint.y)
        val resultAspectRatio = max(absChangeX, absChangeY) / min(absChangeX, absChangeY)
        assertThat(capturedValues.x).isEqualTo(startingPoint.x + adjustedDragDelta.x)
        assertThat(capturedValues.y).isEqualTo(startingPoint.y + adjustedDragDelta.y)
        assertThat(resultAspectRatio).isEqualTo(STARTING_ASPECT_RATIO)
    }

    /**
     * Returns the most recent arguments passed to the `.onPositioningStart()` of the
     * [mockTaskPositioner].
     */
    private fun getLatestOnStartArguments(): CtrlCoordinateCapture {
        val captorCtrlType = argumentCaptor<Int>()
        val captorCoordinates = argumentCaptor<Float>()
        verify(mockTaskPositioner).onDragPositioningStart(
            captorCtrlType.capture(), any(), captorCoordinates.capture(), captorCoordinates.capture())

        return CtrlCoordinateCapture(captorCtrlType.firstValue, captorCoordinates.firstValue,
            captorCoordinates.secondValue)
    }

    /**
     * Returns the most recent arguments passed to the `.onPositioningMove()` of the
     * [mockTaskPositioner].
     */
    private fun getLatestOnMoveArguments(): PointF {
        val captorCoordinates = argumentCaptor<Float>()
        verify(mockTaskPositioner).onDragPositioningMove(
            any(), captorCoordinates.capture(), captorCoordinates.capture())

        return PointF(captorCoordinates.firstValue, captorCoordinates.secondValue)
    }

    /**
     * Returns the most recent arguments passed to the `.onPositioningEnd()` of the
     * [mockTaskPositioner].
     */
    private fun getLatestOnEndArguments(): PointF {
        val captorCoordinates = argumentCaptor<Float>()
        verify(mockTaskPositioner).onDragPositioningEnd(
            any(), captorCoordinates.capture(), captorCoordinates.capture())

        return PointF(captorCoordinates.firstValue, captorCoordinates.secondValue)
    }

    /**
     * Mocks the app bounds to correspond with a given orientation and returns the mocked bounds.
     */
    private fun getAndMockBounds(orientation: Orientation): Rect {
        val mockBounds = if (orientation.isPortrait) PORTRAIT_BOUNDS else LANDSCAPE_BOUNDS
        doReturn(mockBounds).`when`(mockTaskPositioner).onDragPositioningStart(
            any(), any(), any(), any())
        doReturn(mockBounds).`when`(decoratedTaskPositioner).getBounds(any())
        return mockBounds
    }

    /**
     * Calculates the corner point a given drag action should start from, based on the [ctrlType],
     * given the [startingBounds].
     */
    private fun getCornerStartingPoint(@CtrlType ctrlType: Int, startingBounds: Rect): PointF {
        return when (ctrlType) {
            CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT ->
                PointF(startingBounds.right.toFloat(), startingBounds.bottom.toFloat())

            CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT ->
                PointF(startingBounds.left.toFloat(), startingBounds.bottom.toFloat())

            CTRL_TYPE_TOP + CTRL_TYPE_RIGHT ->
                PointF(startingBounds.right.toFloat(), startingBounds.top.toFloat())
            // CTRL_TYPE_TOP + CTRL_TYPE_LEFT
            else ->
                PointF(startingBounds.left.toFloat(), startingBounds.top.toFloat())
        }
    }

    /**
     * Calculates the point along an edge the edge resize should start from, based on the starting
     * edge ([edgeCtrlType]) and the additional edge we expect to resize ([additionalEdgeCtrlType]),
     * given the [startingBounds].
     */
    private fun getEdgeStartingPoint(
        @CtrlType edgeCtrlType: Int, @CtrlType additionalEdgeCtrlType: Int, startingBounds: Rect
    ): PointF {
        val simulatedCorner = getCornerStartingPoint(
            edgeCtrlType + additionalEdgeCtrlType, startingBounds)
        when (additionalEdgeCtrlType) {
            CTRL_TYPE_TOP -> {
                simulatedCorner.offset(0f, -SMALL_DELTA)
                return simulatedCorner
            }
            CTRL_TYPE_BOTTOM -> {
                simulatedCorner.offset(0f, SMALL_DELTA)
                return simulatedCorner
            }
            CTRL_TYPE_LEFT -> {
                simulatedCorner.offset(SMALL_DELTA, 0f)
                return simulatedCorner
            }
            // CTRL_TYPE_RIGHT
            else -> {
                simulatedCorner.offset(-SMALL_DELTA, 0f)
                return simulatedCorner
            }
        }
    }

    /**
     * Calculates the adjustments to the drag delta we expect for a given action and orientation.
     */
    private fun calculateAdjustedDelta(
        @CtrlType ctrlType: Int, delta: PointF, orientation: Orientation
    ): PointF {
        if ((abs(delta.x) < abs(delta.y) && delta.x != 0f) || delta.y == 0f) {
            // Only respect x delta if it's less than y delta but non-zero (i.e there is a change
            // in x to be applied), or if the y delta is zero (i.e there is no change in y to be
            // applied).
            val adjustedY = if (orientation.isPortrait)
                delta.x * STARTING_ASPECT_RATIO else
                delta.x / STARTING_ASPECT_RATIO
            if (ctrlType.isBottomRightOrTopLeftCorner()) {
                return PointF(delta.x, adjustedY)
            }
            return PointF(delta.x, -adjustedY)
        }
        // Respect y delta.
        val adjustedX = if (orientation.isPortrait)
            delta.y / STARTING_ASPECT_RATIO else
            delta.y * STARTING_ASPECT_RATIO
        if (ctrlType.isBottomRightOrTopLeftCorner()) {
            return PointF(adjustedX, delta.y)
        }
        return PointF(-adjustedX, delta.y)
    }

    private fun @receiver:CtrlType Int.isBottomRightOrTopLeftCorner(): Boolean {
        return this == CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT || this == CTRL_TYPE_TOP + CTRL_TYPE_LEFT
    }

    private inner class CtrlCoordinateCapture(ctrl: Int, xValue: Float, yValue: Float) {
        var ctrlType = ctrl
        var x = xValue
        var y = yValue
    }

    companion object {
        private val PORTRAIT_BOUNDS = Rect(100, 100, 200, 400)
        private val LANDSCAPE_BOUNDS = Rect(100, 100, 400, 200)
        private val STARTING_ASPECT_RATIO = PORTRAIT_BOUNDS.height() / PORTRAIT_BOUNDS.width()
        private const val LARGE_DELTA = 50f
        private const val SMALL_DELTA = 30f
        private const val DISPLAY_ID = 1

        enum class Orientation(
            val isPortrait: Boolean
        ) {
            PORTRAIT (true),
            LANDSCAPE (false)
        }

        enum class ResizeableOrNotResizingTestCases(
            val ctrlType: Int,
            val isResizeable: Boolean
        ) {
            NotResizing (CTRL_TYPE_UNDEFINED, false),
            Resizeable (CTRL_TYPE_RIGHT, true)
        }

        /**
         * Tests cases for the start of a corner resize.
         * @param ctrlType the control type of the corner the resize is initiated on.
         */
        enum class CornerResizeStartTestCases(
            val ctrlType: Int
        ) {
            BottomRightCorner (CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT),
            BottomLeftCorner (CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT),
            TopRightCorner (CTRL_TYPE_TOP + CTRL_TYPE_RIGHT),
            TopLeftCorner (CTRL_TYPE_TOP + CTRL_TYPE_LEFT)
        }

        /**
         * Tests cases for the moving and ending of a invalid corner resize. Where the compass point
         * (e.g `SouthEast`) represents the direction of the drag.
         * @param ctrlType the control type of the corner the resize is initiated on.
         * @param dragDelta the delta of the attempted drag action, from the [ctrlType]'s
         * corresponding corner point. Represented as a combination a different signed small and
         * large deltas which correspond to the direction/angle of drag.
         */
        enum class InvalidCornerResizeTestCases(
            val ctrlType: Int,
            val dragDelta: PointF
        ) {
            BottomRightCornerNorthEastDrag (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT,
                PointF(LARGE_DELTA, -LARGE_DELTA)),
            BottomRightCornerSouthWestDrag (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT,
                PointF(-LARGE_DELTA, LARGE_DELTA)),
            TopLeftCornerNorthEastDrag (
                CTRL_TYPE_TOP + CTRL_TYPE_LEFT,
                PointF(LARGE_DELTA, -LARGE_DELTA)),
            TopLeftCornerSouthWestDrag (
                CTRL_TYPE_TOP + CTRL_TYPE_LEFT,
                PointF(-LARGE_DELTA, LARGE_DELTA)),
            BottomLeftCornerSouthEastDrag (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT,
                PointF(LARGE_DELTA, LARGE_DELTA)),
            BottomLeftCornerNorthWestDrag (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT,
                PointF(-LARGE_DELTA, -LARGE_DELTA)),
            TopRightCornerSouthEastDrag (
                CTRL_TYPE_TOP + CTRL_TYPE_RIGHT,
                PointF(LARGE_DELTA, LARGE_DELTA)),
            TopRightCornerNorthWestDrag (
                CTRL_TYPE_TOP + CTRL_TYPE_RIGHT,
                PointF(-LARGE_DELTA, -LARGE_DELTA)),
        }

        /**
         * Tests cases for the moving and ending of a valid corner resize. Where the compass point
         * (e.g `SouthEast`) represents the direction of the drag, followed by the expected
         * behaviour in that direction (i.e `RespectY` means the y delta will be respected whereas
         * `RespectX` means the x delta will be respected).
         * @param ctrlType the control type of the corner the resize is initiated on.
         * @param dragDelta the delta of the attempted drag action, from the [ctrlType]'s
         * corresponding corner point. Represented as a combination a different signed small and
         * large deltas which correspond to the direction/angle of drag.
         */
        enum class ValidCornerResizeTestCases(
            val ctrlType: Int,
            val dragDelta: PointF,
        ) {
            BottomRightCornerSouthEastDragRespectY (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT,
                PointF(+LARGE_DELTA, SMALL_DELTA)),
            BottomRightCornerSouthEastDragRespectX (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT,
                PointF(SMALL_DELTA, LARGE_DELTA)),
            BottomRightCornerNorthWestDragRespectY (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT,
                PointF(-LARGE_DELTA, -SMALL_DELTA)),
            BottomRightCornerNorthWestDragRespectX (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_RIGHT,
                PointF(-SMALL_DELTA, -LARGE_DELTA)),
            TopLeftCornerSouthEastDragRespectY (
                CTRL_TYPE_TOP + CTRL_TYPE_LEFT,
                PointF(LARGE_DELTA, SMALL_DELTA)),
            TopLeftCornerSouthEastDragRespectX (
                CTRL_TYPE_TOP + CTRL_TYPE_LEFT,
                PointF(SMALL_DELTA, LARGE_DELTA)),
            TopLeftCornerNorthWestDragRespectY (
                CTRL_TYPE_TOP + CTRL_TYPE_LEFT,
                PointF(-LARGE_DELTA, -SMALL_DELTA)),
            TopLeftCornerNorthWestDragRespectX (
                CTRL_TYPE_TOP + CTRL_TYPE_LEFT,
                PointF(-SMALL_DELTA, -LARGE_DELTA)),
            BottomLeftCornerSouthWestDragRespectY (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT,
                PointF(-LARGE_DELTA, SMALL_DELTA)),
            BottomLeftCornerSouthWestDragRespectX (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT,
                PointF(-SMALL_DELTA, LARGE_DELTA)),
            BottomLeftCornerNorthEastDragRespectY (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT,
                PointF(LARGE_DELTA, -SMALL_DELTA)),
            BottomLeftCornerNorthEastDragRespectX (
                CTRL_TYPE_BOTTOM + CTRL_TYPE_LEFT,
                PointF(SMALL_DELTA, -LARGE_DELTA)),
            TopRightCornerSouthWestDragRespectY (
                CTRL_TYPE_TOP + CTRL_TYPE_RIGHT,
                PointF(-LARGE_DELTA, SMALL_DELTA)),
            TopRightCornerSouthWestDragRespectX (
                CTRL_TYPE_TOP + CTRL_TYPE_RIGHT,
                PointF(-SMALL_DELTA, LARGE_DELTA)),
            TopRightCornerNorthEastDragRespectY (
                CTRL_TYPE_TOP + CTRL_TYPE_RIGHT,
                PointF(LARGE_DELTA, -SMALL_DELTA)),
            TopRightCornerNorthEastDragRespectX (
                CTRL_TYPE_TOP + CTRL_TYPE_RIGHT,
                PointF(+SMALL_DELTA, -LARGE_DELTA))
        }

        /**
         * Tests cases for the start of an edge resize.
         * @param ctrlType the control type of the edge the resize is initiated on.
         * @param additionalEdgeCtrlType the expected additional edge to be included in the ctrl
         * type.
         */
        enum class EdgeResizeStartTestCases(
            val ctrlType: Int,
            val additionalEdgeCtrlType: Int
        ) {
            BottomOfLeftEdgeResize (CTRL_TYPE_LEFT, CTRL_TYPE_BOTTOM),
            TopOfLeftEdgeResize (CTRL_TYPE_LEFT, CTRL_TYPE_TOP),
            BottomOfRightEdgeResize (CTRL_TYPE_RIGHT, CTRL_TYPE_BOTTOM),
            TopOfRightEdgeResize (CTRL_TYPE_RIGHT, CTRL_TYPE_TOP),
            RightOfTopEdgeResize (CTRL_TYPE_TOP, CTRL_TYPE_RIGHT),
            LeftOfTopEdgeResize (CTRL_TYPE_TOP, CTRL_TYPE_LEFT),
            RightOfBottomEdgeResize (CTRL_TYPE_BOTTOM, CTRL_TYPE_RIGHT),
            LeftOfBottomEdgeResize (CTRL_TYPE_BOTTOM, CTRL_TYPE_LEFT)
        }

        /**
         * Tests cases for the moving and ending of an edge resize.
         * @param ctrlType the control type of the edge the resize is initiated on.
         * @param additionalEdgeCtrlType the expected additional edge to be included in the ctrl
         * type.
         * @param dragDelta the delta of the attempted drag action, from the [ctrlType]'s
         * corresponding edge point. Represented as a combination a different signed small and
         * large deltas which correspond to the direction/angle of drag.
         */
        enum class EdgeResizeTestCases(
            val ctrlType: Int,
            val additionalEdgeCtrlType: Int,
            val dragDelta: PointF
        ) {
            BottomOfLeftEdgeResize (CTRL_TYPE_LEFT, CTRL_TYPE_BOTTOM, PointF(-SMALL_DELTA, 0f)),
            TopOfLeftEdgeResize (CTRL_TYPE_LEFT, CTRL_TYPE_TOP, PointF(-SMALL_DELTA, 0f)),
            BottomOfRightEdgeResize (CTRL_TYPE_RIGHT, CTRL_TYPE_BOTTOM, PointF(SMALL_DELTA, 0f)),
            TopOfRightEdgeResize (CTRL_TYPE_RIGHT, CTRL_TYPE_TOP, PointF(SMALL_DELTA, 0f)),
            RightOfTopEdgeResize (CTRL_TYPE_TOP, CTRL_TYPE_RIGHT, PointF(0f, -SMALL_DELTA)),
            LeftOfTopEdgeResize (CTRL_TYPE_TOP, CTRL_TYPE_LEFT, PointF(0f, -SMALL_DELTA)),
            RightOfBottomEdgeResize (CTRL_TYPE_BOTTOM, CTRL_TYPE_RIGHT, PointF(0f, SMALL_DELTA)),
            LeftOfBottomEdgeResize (CTRL_TYPE_BOTTOM, CTRL_TYPE_LEFT, PointF(0f, SMALL_DELTA))
        }
    }
}
