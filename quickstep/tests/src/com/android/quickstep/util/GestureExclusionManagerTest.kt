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

package com.android.quickstep.util

import android.graphics.Rect
import android.graphics.Region
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import androidx.test.filters.SmallTest
import com.android.launcher3.util.Executors
import com.android.quickstep.util.GestureExclusionManager.ExclusionListener
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyZeroInteractions

/** Unit test for [GestureExclusionManager]. */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class GestureExclusionManagerTest {

    @Mock private lateinit var windowManager: IWindowManager

    @Mock private lateinit var listener1: ExclusionListener
    @Mock private lateinit var listener2: ExclusionListener

    private val r1 = Region().apply { union(Rect(0, 0, 100, 200)) }
    private val r2 = Region().apply { union(Rect(200, 200, 500, 800)) }

    private lateinit var underTest: GestureExclusionManager

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest = GestureExclusionManager(windowManager)
    }

    @Test
    fun addListener_registers() {
        underTest.addListener(listener1)

        awaitTasksCompleted()
        verify(windowManager)
            .registerSystemGestureExclusionListener(underTest.exclusionListener, DEFAULT_DISPLAY)
    }

    @Test
    fun addListener_again_skips_register() {
        underTest.addListener(listener1)
        awaitTasksCompleted()
        reset(windowManager)

        underTest.addListener(listener2)

        awaitTasksCompleted()
        verifyZeroInteractions(windowManager)
    }

    @Test
    fun removeListener_unregisters() {
        underTest.addListener(listener1)
        awaitTasksCompleted()
        reset(windowManager)

        underTest.removeListener(listener1)

        awaitTasksCompleted()
        verify(windowManager)
            .unregisterSystemGestureExclusionListener(underTest.exclusionListener, DEFAULT_DISPLAY)
    }

    @Test
    fun removeListener_again_skips_unregister() {
        underTest.addListener(listener1)
        underTest.addListener(listener2)
        awaitTasksCompleted()
        reset(windowManager)

        underTest.removeListener(listener1)

        awaitTasksCompleted()
        verifyZeroInteractions(windowManager)
    }

    @Test
    fun onSystemGestureExclusionChanged_dispatches_to_listeners() {
        underTest.addListener(listener1)
        underTest.addListener(listener2)
        awaitTasksCompleted()

        underTest.exclusionListener.onSystemGestureExclusionChanged(DEFAULT_DISPLAY, r1, r2)
        awaitTasksCompleted()
        verify(listener1).onGestureExclusionChanged(r1, r2)
        verify(listener2).onGestureExclusionChanged(r1, r2)
    }

    @Test
    fun addLister_dispatches_second_time() {
        underTest.exclusionListener.onSystemGestureExclusionChanged(DEFAULT_DISPLAY, r1, r2)
        awaitTasksCompleted()
        underTest.addListener(listener1)
        awaitTasksCompleted()
        verifyZeroInteractions(listener1)

        underTest.addListener(listener2)
        awaitTasksCompleted()

        verifyZeroInteractions(listener1)
        verify(listener2).onGestureExclusionChanged(r1, r2)
    }

    private fun awaitTasksCompleted() {
        Executors.UI_HELPER_EXECUTOR.submit<Any> { null }.get()
        Executors.MAIN_EXECUTOR.submit<Any> { null }.get()
    }
}
