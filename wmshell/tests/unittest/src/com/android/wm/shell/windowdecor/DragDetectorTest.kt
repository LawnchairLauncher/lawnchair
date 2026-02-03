/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.os.SystemClock
import android.testing.AndroidTestingRunner
import android.view.MotionEvent
import android.view.InputDevice
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.argThat
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.times

/**
 * Tests for [DragDetector].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:DragDetectorTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DragDetectorTest : ShellTestCase() {
    private val motionEvents = mutableListOf<MotionEvent>()

    @Mock
    private lateinit var eventHandler: DragDetector.MotionEventHandler

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        `when`(eventHandler.handleMotionEvent(any(), any())).thenReturn(true)
    }

    @After
    fun tearDown() {
        motionEvents.forEach {
            it.recycle()
        }
        motionEvents.clear()
    }

    @Test
    fun testNoMove_passesDownAndUp() {
        val dragDetector = createDragDetector()
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_DOWN && it.x == X && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })

        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_UP)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_UP && it.x == X && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })
    }

    @Test
    fun testNoMove_mouse_passesDownAndUp() {
        val dragDetector = createDragDetector()
        assertTrue(dragDetector.onMotionEvent(
            createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_DOWN && it.x == X && it.y == Y &&
                    it.source == InputDevice.SOURCE_MOUSE
        })

        assertTrue(dragDetector.onMotionEvent(
            createMotionEvent(MotionEvent.ACTION_UP, isTouch = false)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_UP && it.x == X && it.y == Y &&
                    it.source == InputDevice.SOURCE_MOUSE
        })
    }

    @Test
    fun testMoveInSlop_touch_passesDownAndUp() {
        val dragDetector = createDragDetector()
        `when`(eventHandler.handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_DOWN
        })).thenReturn(false)

        assertFalse(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_DOWN && it.x == X && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })

        val newX = X + SLOP - 1
        assertFalse(
                dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y)))
        verify(eventHandler, never()).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_MOVE
        })

        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_UP, newX, Y)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_UP && it.x == newX && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })
    }

    @Test
    fun testMoveInSlop_mouse_passesDownMoveAndUp() {
        val dragDetector = createDragDetector()
        `when`(eventHandler.handleMotionEvent(any(), argThat {
            it.action == MotionEvent.ACTION_DOWN
        })).thenReturn(false)

        assertFalse(dragDetector.onMotionEvent(
                createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_DOWN && it.x == X && it.y == Y &&
                    it.source == InputDevice.SOURCE_MOUSE
        })

        val newX = X + SLOP - 1
        assertTrue(dragDetector.onMotionEvent(
                createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y, isTouch = false)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_MOVE && it.x == newX && it.y == Y &&
                    it.source == InputDevice.SOURCE_MOUSE
        })

        assertTrue(dragDetector.onMotionEvent(
                createMotionEvent(MotionEvent.ACTION_UP, newX, Y, isTouch = false)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_UP && it.x == newX && it.y == Y &&
                    it.source == InputDevice.SOURCE_MOUSE
        })
    }

    @Test
    fun testMoveBeyondSlop_passesDownMoveAndUp() {
        val dragDetector = createDragDetector()
        `when`(eventHandler.handleMotionEvent(any(), argThat {
            it.action == MotionEvent.ACTION_DOWN
        })).thenReturn(false)

        assertFalse(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_DOWN && it.x == X && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })

        val newX = X + SLOP + 1
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_MOVE && it.x == newX && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })

        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_UP, newX, Y)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_UP && it.x == newX && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })
    }

    @Test
    fun testDownMoveDown_shouldIgnoreTheSecondDownMotion() {
        val dragDetector = createDragDetector()
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_DOWN && it.x == X && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })

        val newX = X + SLOP + 1
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_MOVE && it.x == newX && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })

        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_MOVE && it.x == newX && it.y == Y &&
                    it.source == InputDevice.SOURCE_TOUCHSCREEN
        })
    }

    @Test
    fun testDownMouseMoveDownTouch_shouldIgnoreTheTouchDownMotion() {
        val dragDetector = createDragDetector()
        assertTrue(dragDetector.onMotionEvent(
            createMotionEvent(MotionEvent.ACTION_DOWN, isTouch = false)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_DOWN && it.x == X && it.y == Y &&
                    it.source == InputDevice.SOURCE_MOUSE
        })

        val newX = X + SLOP + 1
        assertTrue(dragDetector.onMotionEvent(
            createMotionEvent(MotionEvent.ACTION_MOVE, newX, Y, isTouch = false)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_MOVE && it.x == newX && it.y == Y &&
                    it.source == InputDevice.SOURCE_MOUSE
        })

        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_DOWN)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_MOVE && it.x == newX && it.y == Y &&
                    it.source == InputDevice.SOURCE_MOUSE
        })
    }

    @Test
    fun testPassesHoverEnter() {
        val dragDetector = createDragDetector()
        `when`(eventHandler.handleMotionEvent(any(), argThat {
            it.action == MotionEvent.ACTION_HOVER_ENTER
        })).thenReturn(false)

        assertFalse(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_HOVER_ENTER)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_HOVER_ENTER && it.x == X && it.y == Y
        })
    }

    @Test
    fun testPassesHoverMove() {
        val dragDetector = createDragDetector()
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_HOVER_MOVE)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_HOVER_MOVE && it.x == X && it.y == Y
        })
    }

    @Test
    fun testPassesHoverExit() {
        val dragDetector = createDragDetector()
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_HOVER_EXIT)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_HOVER_EXIT && it.x == X && it.y == Y
        })
    }

    @Test
    fun testHoldToDrag_holdsWithMovementWithinSlop_passesDragMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 500f,
            y = 10f,
            isTouch = true,
            downTime = downTime,
            eventTime = downTime
        ))

        // Couple of movements within the slop, still counting as "holding"
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f + 10f, // within slop
            y = 10f + 10f, // within slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 30
        ))
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f - 10f, // within slop
            y = 10f - 5f, // within slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 70
        ))
        // Now go beyond slop, but after the required holding period.
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f + 50f, // beyond slop
            y = 10f + 50f, // beyond slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 101 // after hold period
        ))

        // Had a valid hold, so there should be 1 "move".
        verify(eventHandler, times(1))
            .handleMotionEvent(any(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_holdsWithoutAnyMovement_passesMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 500f,
            y = 10f,
            isTouch = true,
            downTime = downTime,
            eventTime = downTime
        ))

        // First |move| is already beyond slop and after holding period.
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f + 50f, // beyond slop
            y = 10f + 50f, // beyond slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 101 // after hold period
        ))

        // Considered a valid hold, so there should be 1 "move".
        verify(eventHandler, times(1))
            .handleMotionEvent(any(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_returnsWithinSlopAfterHoldPeriod_passesDragMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 500f,
            y = 10f,
            isTouch = true,
            downTime = downTime,
            eventTime = downTime
        ))
        // Go beyond slop after the required holding period.
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f + 50f, // beyond slop
            y = 10f + 50f, // beyond slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 101 // after hold period
        ))

        // Return to original coordinates after holding period.
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f, // within slop
            y = 10f, // within slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 102 // after hold period
        ))

        // Both |moves| should be passed, even the one in the slop region since it was after the
        // holding period. (e.g. after you drag the handle you may return to its original position).
        verify(eventHandler, times(2))
            .handleMotionEvent(any(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_straysDuringHoldPeriod_skipsMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 500f,
            y = 10f,
            isTouch = true,
            downTime = downTime,
            eventTime = downTime
        ))

        // Go beyond slop before the required holding period.
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f + 50f, // beyond slop
            y = 10f + 50f, // beyond slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 30 // during hold period
        ))

        // The |move| was too quick and did not held, do not pass it to the handler.
        verify(eventHandler, never())
            .handleMotionEvent(any(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_straysDuringHoldPeriodAndReturnsWithinSlop_skipsMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 100, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 500f,
            y = 10f,
            isTouch = true,
            downTime = downTime,
            eventTime = downTime
        ))
        // Go beyond slop before the required holding period.
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f + 50f, // beyond slop
            y = 10f + 50f, // beyond slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 30 // during hold period
        ))

        // Return to slop area during holding period.
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f + 10f, // within slop
            y = 10f + 10f, // within slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 50 // during hold period
        ))

        // The first |move| invalidates the drag even if you return within the hold period, so the
        // |move| should not be passed to the handler.
        verify(eventHandler, never())
            .handleMotionEvent(any(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    @Test
    fun testHoldToDrag_noHoldRequired_passesMoveEvents() {
        val dragDetector = createDragDetector(holdToDragMinDurationMs = 0, slop = 20)
        val downTime = SystemClock.uptimeMillis()
        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_DOWN,
            x = 500f,
            y = 10f,
            isTouch = true,
            downTime = downTime,
            eventTime = downTime
        ))

        dragDetector.onMotionEvent(createMotionEvent(
            action = MotionEvent.ACTION_MOVE,
            x = 500f + 50f, // beyond slop
            y = 10f + 50f, // beyond slop
            isTouch = true,
            downTime = downTime,
            eventTime = downTime + 1
        ))

        // The |move| should be passed to the handler as no hold period was needed.
        verify(eventHandler, times(1))
            .handleMotionEvent(any(), argThat { ev -> ev.action == MotionEvent.ACTION_MOVE })
    }

    private fun createMotionEvent(
        action: Int,
        x: Float = X,
        y: Float = Y,
        isTouch: Boolean = true,
        downTime: Long = SystemClock.uptimeMillis(),
        eventTime: Long = SystemClock.uptimeMillis()
    ): MotionEvent {
        val ev = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        ev.source = if (isTouch) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_MOUSE
        motionEvents.add(ev)
        return ev
    }

    private fun createDragDetector(
        holdToDragMinDurationMs: Long = 0,
        slop: Int = SLOP
    ) = DragDetector(
        eventHandler,
        holdToDragMinDurationMs,
        slop
    )

    companion object {
        private const val SLOP = 10
        private const val X = 123f
        private const val Y = 234f
    }
}