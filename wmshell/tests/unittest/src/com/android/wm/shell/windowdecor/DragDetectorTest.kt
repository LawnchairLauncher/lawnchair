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

/**
 * Tests for [DragDetector].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:DragDetectorTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DragDetectorTest {
    private val motionEvents = mutableListOf<MotionEvent>()

    @Mock
    private lateinit var eventHandler: DragDetector.MotionEventHandler

    private lateinit var dragDetector: DragDetector

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        `when`(eventHandler.handleMotionEvent(any(), any())).thenReturn(true)

        dragDetector = DragDetector(eventHandler)
        dragDetector.setTouchSlop(SLOP)
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
    fun testMoveInSlop_touch_passesDownAndUp() {
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
    fun testPassesHoverEnter() {
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
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_HOVER_MOVE)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_HOVER_MOVE && it.x == X && it.y == Y
        })
    }

    @Test
    fun testPassesHoverExit() {
        assertTrue(dragDetector.onMotionEvent(createMotionEvent(MotionEvent.ACTION_HOVER_EXIT)))
        verify(eventHandler).handleMotionEvent(any(), argThat {
            return@argThat it.action == MotionEvent.ACTION_HOVER_EXIT && it.x == X && it.y == Y
        })
    }

    private fun createMotionEvent(action: Int, x: Float = X, y: Float = Y, isTouch: Boolean = true):
            MotionEvent {
        val time = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(time, time, action, x, y, 0)
        ev.source = if (isTouch) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_MOUSE
        motionEvents.add(ev)
        return ev
    }

    companion object {
        private const val SLOP = 10
        private const val X = 123f
        private const val Y = 234f
    }
}