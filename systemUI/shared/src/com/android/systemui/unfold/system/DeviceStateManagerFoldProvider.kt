/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.unfold.system

import android.content.Context
import android.hardware.devicestate.DeviceStateManager
import com.android.systemui.unfold.updates.FoldProvider
import com.android.systemui.unfold.updates.FoldProvider.FoldCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStateManagerFoldProvider
@Inject
constructor(private val deviceStateManager: DeviceStateManager, private val context: Context) :
    FoldProvider {

    private val callbacks =
        ConcurrentHashMap<FoldCallback, DeviceStateManager.DeviceStateCallback>()

    override fun registerCallback(callback: FoldCallback, executor: Executor) {
        val listener = FoldStateListener(context, callback)
        deviceStateManager.registerCallback(executor, listener)
        callbacks[callback] = listener
    }

    override fun unregisterCallback(callback: FoldCallback) {
        val listener = callbacks.remove(callback)
        listener?.let { deviceStateManager.unregisterCallback(it) }
    }

    private inner class FoldStateListener(context: Context, listener: FoldCallback) :
        DeviceStateManager.FoldStateListener(context, { listener.onFoldUpdated(it) })
}
