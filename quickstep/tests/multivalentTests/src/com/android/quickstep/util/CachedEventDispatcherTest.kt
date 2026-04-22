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

package com.android.quickstep.util

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.function.Consumer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class CachedEventDispatcherTest {

    private lateinit var underTest: CachedEventDispatcher

    @Mock private lateinit var consumer: Consumer<MotionEvent>
    @Captor private lateinit var motionEventCaptor: ArgumentCaptor<MotionEvent>

    private lateinit var motionEvent: MotionEvent

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        motionEvent =
            MotionEvent.obtain(
                0L,
                SystemClock.elapsedRealtime(),
                MotionEvent.ACTION_DOWN,
                0f,
                0f,
                0,
            )
        underTest = CachedEventDispatcher()
    }

    @Test
    fun testInitialState() {
        assertFalse(underTest.hasConsumer())
    }

    @Test
    fun dispatchMotionEvents() {
        underTest.setConsumer(consumer)

        underTest.dispatchEvent(motionEvent)

        assertTrue(underTest.hasConsumer())
        verify(consumer).accept(motionEventCaptor.capture())
        assertTrue(isMotionEventSame(motionEventCaptor.value, motionEvent))
    }

    @Test
    fun dispatchMotionEvents_after_settingConsumer() {
        underTest.dispatchEvent(motionEvent)

        underTest.setConsumer(consumer)

        verify(consumer).accept(motionEventCaptor.capture())
        assertTrue(isMotionEventSame(motionEventCaptor.value, motionEvent))
    }

    @Test
    fun clearConsumer_notDispatchToConsumer() {
        underTest.setConsumer(consumer)
        underTest.dispatchEvent(motionEvent)
        reset(consumer)

        underTest.clearConsumer()

        assertFalse(underTest.hasConsumer())
        underTest.dispatchEvent(motionEvent)
        verifyNoMoreInteractions(consumer)
    }

    private fun isMotionEventSame(e1: MotionEvent, e2: MotionEvent): Boolean {
        return e1.action == e2.action &&
            e1.eventTime == e2.eventTime &&
            e1.x == e2.x &&
            e1.y == e2.y
    }
}
