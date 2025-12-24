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

package com.android.quickstep.recents.data

import android.view.InputDevice
import android.view.InputDevice.SOURCE_KEYBOARD
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputDevice.SOURCE_TOUCHPAD
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class PointerRepositoryTest {
    private val fakeInputManager = FakeInputDeviceDataSource()
    private val pointerRepository = PointerRepositoryImpl(fakeInputManager)

    @Before
    fun tearDown() {
        fakeInputManager.reset()
    }

    @Test
    fun noConnectedDevices_returnsFalse() {
        fakeInputManager.reset()

        assertFalse(pointerRepository.isAnyPointerDeviceConnected())
    }

    @Test
    fun disabledMouse_returnsFalse() {
        fakeInputManager.addInputDevice(1, false, SOURCE_MOUSE)

        assertFalse(pointerRepository.isAnyPointerDeviceConnected())
    }

    @Test
    fun connectedKeyboard_returnsFalse() {
        fakeInputManager.addInputDevice(1, true, SOURCE_KEYBOARD)

        assertFalse(pointerRepository.isAnyPointerDeviceConnected())
    }

    @Test
    fun connectedMouse_returnsTrue() {
        fakeInputManager.addInputDevice(1, true, SOURCE_MOUSE)

        assertTrue(pointerRepository.isAnyPointerDeviceConnected())
    }

    @Test
    fun connectedTouchpad_returnsTrue() {
        fakeInputManager.addInputDevice(1, true, SOURCE_TOUCHPAD)

        assertTrue(pointerRepository.isAnyPointerDeviceConnected())
    }

    @Test
    fun connectedMouseAndKeyboard_returnsTrue() {
        fakeInputManager.addInputDevice(1, true, SOURCE_MOUSE)
        fakeInputManager.addInputDevice(2, true, SOURCE_KEYBOARD)

        assertTrue(pointerRepository.isAnyPointerDeviceConnected())
    }
}

class FakeInputDeviceDataSource : InputDeviceDataSource {
    private val devices = mutableMapOf<Int, InputDevice>()

    override val inputDeviceIds: IntArray
        get() = devices.keys.toIntArray()

    override fun getInputDevice(deviceId: Int): InputDevice? = devices[deviceId]

    fun addInputDevice(deviceId: Int, isEnabled: Boolean, sourceToSupport: Int? = null) {
        devices[deviceId] = mock {
            on { this.isEnabled } doReturn isEnabled
            on { this.id } doReturn deviceId

            if (sourceToSupport != null) {
                on { supportsSource(sourceToSupport) } doReturn true
            }
        }
    }

    fun reset() {
        devices.clear()
    }
}
