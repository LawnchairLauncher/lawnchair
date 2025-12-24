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

import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputDevice.SOURCE_TOUCHPAD
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ActiveTrackpadListTest {

    @get:Rule var mockitoRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule val context = SandboxApplication()

    private val inputDeviceIds = IntArray()
    private lateinit var inputManager: InputManager

    @Before
    fun setup() {
        inputManager = context.spyService(InputManager::class.java)
        doAnswer { inputDeviceIds.toArray() }.whenever(inputManager).inputDeviceIds

        doReturn(null).whenever(inputManager).getInputDevice(eq(1))
        doReturn(mockDevice(SOURCE_MOUSE or SOURCE_TOUCHPAD))
            .whenever(inputManager)
            .getInputDevice(eq(2))
        doReturn(mockDevice(SOURCE_MOUSE or SOURCE_TOUCHPAD))
            .whenever(inputManager)
            .getInputDevice(eq(3))
        doReturn(mockDevice(SOURCE_MOUSE)).whenever(inputManager).getInputDevice(eq(4))
    }

    @Test
    fun `initialize correct devices`() {
        inputDeviceIds.addAll(IntArray.wrap(1, 2, 3, 4))

        val list = ActiveTrackpadList(context) {}
        assertEquals(2, list.size())
        assertTrue(list.contains(2))
        assertTrue(list.contains(3))
    }

    @Test
    fun `update callback not called in constructor`() {
        inputDeviceIds.addAll(IntArray.wrap(2, 3))

        var updateCalled = false
        val list = ActiveTrackpadList(context) { updateCalled = true }
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        assertEquals(2, list.size())
        assertFalse(updateCalled)
    }

    @Test
    fun `update called on add only once`() {
        var updateCalled = 0
        val list = ActiveTrackpadList(context) { updateCalled++ }
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        assertEquals(0, updateCalled)
        assertEquals(0, list.size())

        list.onInputDeviceAdded(1)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(0, updateCalled)
        assertEquals(0, list.size())

        list.onInputDeviceAdded(2)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(1, updateCalled)
        assertEquals(1, list.size())

        list.onInputDeviceAdded(3)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(1, updateCalled)
        assertEquals(2, list.size())
    }

    @Test
    fun `update called on remove only once`() {
        var updateCalled = 0
        inputDeviceIds.addAll(IntArray.wrap(1, 2, 3, 4))
        val list = ActiveTrackpadList(context) { updateCalled++ }
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(2, list.size())

        list.onInputDeviceRemoved(2)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(1, list.size())
        assertEquals(0, updateCalled)

        list.onInputDeviceRemoved(3)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(0, list.size())
        assertEquals(1, updateCalled)

        // Removing non-trackpad device should have no effect.
        list.onInputDeviceRemoved(4)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(0, list.size())
        assertEquals(1, updateCalled)
    }

    @Test
    fun `update not called on add and remove non-trackpad device`() {
        var updateCalled = 0
        val list = ActiveTrackpadList(context) { updateCalled++ }
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        assertEquals(0, updateCalled)
        assertEquals(0, list.size())

        list.onInputDeviceAdded(1)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(0, updateCalled)
        assertEquals(0, list.size())

        list.onInputDeviceRemoved(1)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(0, updateCalled)
        assertEquals(0, list.size())
    }

    private fun mockDevice(sources: Int) =
        mock(InputDevice::class.java).apply { doReturn(sources).whenever(this).sources }
}
