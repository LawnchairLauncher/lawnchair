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

import android.os.Build
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputDevice.SOURCE_TOUCHPAD
import androidx.annotation.RequiresApi
//import com.android.app.tracing.traceSection

interface PointerRepository {
    fun isAnyPointerDeviceConnected(): Boolean
}

class PointerRepositoryImpl(private val inputManager: InputDeviceDataSource) : PointerRepository {
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun isAnyPointerDeviceConnected(): Boolean {
            return inputManager.inputDeviceIds.any { isPointerDeviceEnabled(it) }
        }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun isPointerDeviceEnabled(deviceId: Int): Boolean {
        val device = inputManager.getInputDevice(deviceId) ?: return false

        return device.isEnabled &&
            (device.supportsSource(SOURCE_MOUSE) || device.supportsSource(SOURCE_TOUCHPAD))
    }
}
