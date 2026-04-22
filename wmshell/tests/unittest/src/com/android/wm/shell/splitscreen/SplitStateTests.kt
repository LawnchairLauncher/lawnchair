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

package com.android.wm.shell.splitscreen

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.wm.shell.common.split.SplitState
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.android.wm.shell.shared.split.SplitScreenConstants.NOT_IN_SPLIT
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_10_45_45
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SplitStateTests {

    val DEFAULT_VALUE = -4

    private lateinit var splitter: SplitState
    private lateinit var listener1: TestListener
    private lateinit var listener2: TestListener

    @Before
    fun setUp() {
        splitter = SplitState()
        listener1 = TestListener()
        listener2 = TestListener()
    }

    @Test
    fun testRegisterAndNotify() {
        splitter.registerSplitStateChangeListener(listener1)
        splitter.set(SNAP_TO_2_50_50)
        assertEquals(SNAP_TO_2_50_50, listener1.lastState)

        splitter.set(SNAP_TO_3_10_45_45)
        assertEquals(SNAP_TO_3_10_45_45, listener1.lastState)
    }

    @Test
    fun testMultipleListeners() {
        splitter.registerSplitStateChangeListener(listener1)
        splitter.registerSplitStateChangeListener(listener2)

        splitter.set(SNAP_TO_3_10_45_45)
        assertEquals(SNAP_TO_3_10_45_45, listener1.lastState)
        assertEquals(SNAP_TO_3_10_45_45, listener2.lastState)
    }

    @Test
    fun testUnregisterListener() {
        splitter.registerSplitStateChangeListener(listener1)
        splitter.registerSplitStateChangeListener(listener2)
        splitter.unregisterSplitStateChangeListener(listener1)

        splitter.set(SNAP_TO_2_50_50)
        assertEquals(DEFAULT_VALUE, listener1.lastState) // Listener 1 should not be notified
        assertEquals(SNAP_TO_2_50_50, listener2.lastState)
    }

    @Test
    fun testNoListeners() {
        splitter.set(SNAP_TO_2_50_50)
        // No listeners registered, so no exceptions should be thrown.
    }

    @Test
    fun testRegisterSameListenerMultipleTimes() {
        splitter.registerSplitStateChangeListener(listener1)
        splitter.registerSplitStateChangeListener(listener1)

        splitter.set(SNAP_TO_2_50_50)
        assertEquals(SNAP_TO_2_50_50, listener1.lastState)
        assertEquals(1, listener1.callCount) // should only be called once.
    }

    @Test
    fun testExit() {
        splitter.registerSplitStateChangeListener(listener1)
        splitter.exit()

        assertEquals(NOT_IN_SPLIT, listener1.lastState)
    }

    // Helper class for testing
    inner class TestListener : SplitState.SplitStateChangeListener {
        var lastState = DEFAULT_VALUE
        var callCount = 0

        override fun onSplitStateChanged(@SplitScreenConstants.SplitScreenState splitState: Int) {
            this.lastState = splitState
            callCount++
        }
    }
}