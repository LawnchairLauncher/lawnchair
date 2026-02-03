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

package com.android.wm.shell.windowdecor.tiling

import android.graphics.Rect
import android.os.SystemClock
import android.testing.AndroidTestingRunner
import android.util.Size
import android.view.Display
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SmallTest
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
class TilingDividerViewTest : ShellTestCase() {

    private lateinit var tilingDividerView: TilingDividerView

    private val dividerMoveCallbackMock = mock<DividerMoveCallback>()

    private val viewMock = mock<View>()
    private val display = mock<Display>()
    private val roundedCorner = mock<RoundedCorner>()

    @Before
    @UiThreadTest
    fun setUp() {
        whenever(display.getRoundedCorner(any())).thenReturn(roundedCorner)
        whenever(roundedCorner.radius).thenReturn(CORNER_RADIUS)
        tilingDividerView =
            LayoutInflater.from(mContext).inflate(R.layout.tiling_split_divider, /* root= */ null)
                as TilingDividerView
        tilingDividerView.setup(dividerMoveCallbackMock, DIVIDER_BOUNDS, HANDLE_SIZE, true)
        tilingDividerView.handleY = 0..1500
    }

    @Test
    @UiThreadTest
    fun testCallbackOnTouch() {
        val x = 5
        val y = 5
        val downTime: Long = SystemClock.uptimeMillis()

        val downMotionEvent =
            getMotionEvent(downTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat())
        tilingDividerView.handleMotionEvent(viewMock, downMotionEvent)
        verify(dividerMoveCallbackMock, times(1)).onDividerMoveStart(any(), any())

        whenever(dividerMoveCallbackMock.onDividerMove(any())).thenReturn(true)
        val motionEvent =
            getMotionEvent(downTime, MotionEvent.ACTION_MOVE, x.toFloat(), y.toFloat())
        tilingDividerView.handleMotionEvent(viewMock, motionEvent)
        verify(dividerMoveCallbackMock, times(1)).onDividerMove(any())

        val upMotionEvent =
            getMotionEvent(downTime, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat())
        tilingDividerView.handleMotionEvent(viewMock, upMotionEvent)
        verify(dividerMoveCallbackMock, times(1)).onDividerMovedEnd(any(), any())
    }

    @Test
    @UiThreadTest
    fun testCallbackOnTouch_doesNotHappen_whenNoTouchMove() {
        val x = 5
        val y = 5
        val downTime: Long = SystemClock.uptimeMillis()

        val downMotionEvent =
            getMotionEvent(downTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat())
        tilingDividerView.handleMotionEvent(viewMock, downMotionEvent)
        verify(dividerMoveCallbackMock, times(1)).onDividerMoveStart(any(), any())

        val upMotionEvent =
            getMotionEvent(downTime, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat())
        tilingDividerView.handleMotionEvent(viewMock, upMotionEvent)
        verify(dividerMoveCallbackMock, never()).onDividerMovedEnd(any(), any())
    }

    private fun getMotionEvent(eventTime: Long, action: Int, x: Float, y: Float): MotionEvent {
        val properties = MotionEvent.PointerProperties()
        properties.id = 0
        properties.toolType = MotionEvent.TOOL_TYPE_UNKNOWN

        val coords = MotionEvent.PointerCoords()
        coords.pressure = 1f
        coords.size = 1f
        coords.x = x
        coords.y = y

        return MotionEvent.obtain(
            eventTime,
            eventTime,
            action,
            1,
            arrayOf(properties),
            arrayOf(coords),
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
    }

    companion object {
        private val DIVIDER_BOUNDS = Rect(15, 0, 35, 1500)
        private val HANDLE_SIZE = Size(800, 300)
        private const val CORNER_RADIUS = 15
    }
}
