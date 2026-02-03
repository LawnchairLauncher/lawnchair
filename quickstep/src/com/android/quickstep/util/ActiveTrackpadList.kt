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

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.android.launcher3.util.Executors
import com.android.launcher3.util.IntSet

/** Utility class to maintain a list of actively connected trackpad devices */
class ActiveTrackpadList(ctx: Context, private val updateCallback: Runnable) :
    IntSet(), InputManager.InputDeviceListener {

    private val inputManager = ctx.getSystemService(InputManager::class.java)!!

    init {
        inputManager.registerInputDeviceListener(this, Executors.UI_HELPER_EXECUTOR.handler)
        inputManager.inputDeviceIds.filter(this::isTrackpadDevice).forEach(this::add)
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        if (isTrackpadDevice(deviceId)) {
            // This updates internal TIS state so it needs to also run on the main
            // thread.
            Executors.MAIN_EXECUTOR.execute {
                val wasEmpty = isEmpty
                add(deviceId)
                if (wasEmpty) update()
            }
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {}

    override fun onInputDeviceRemoved(deviceId: Int) {
        // This updates internal TIS state so it needs to also run on the main thread.
        Executors.MAIN_EXECUTOR.execute {
            remove(deviceId)
            if (isEmpty) update()
        }
    }

    private fun update() {
        updateCallback.run()
    }

    fun destroy() {
        inputManager.unregisterInputDeviceListener(this)
        clear()
    }

    /** This is a blocking binder call that should run on a bg thread. */
    private fun isTrackpadDevice(deviceId: Int) =
        inputManager.getInputDevice(deviceId)?.sources ==
            (InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_TOUCHPAD)
}
