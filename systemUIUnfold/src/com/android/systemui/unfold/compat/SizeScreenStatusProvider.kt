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
package com.android.systemui.unfold.compat

import com.android.systemui.unfold.updates.FoldProvider
import com.android.systemui.unfold.updates.screen.ScreenStatusProvider
import com.android.systemui.unfold.updates.screen.ScreenStatusProvider.ScreenListener
import java.util.concurrent.Executor

class SizeScreenStatusProvider(
    private val foldProvider: FoldProvider,
    private val executor: Executor
) : ScreenStatusProvider {

    private val listeners: MutableList<ScreenListener> = arrayListOf()
    private val callback = object : FoldProvider.FoldCallback {
        override fun onFoldUpdated(isFolded: Boolean) {
            if (!isFolded) {
                listeners.forEach { it.onScreenTurnedOn() }
            }
        }
    }

    fun start() {
        foldProvider.registerCallback(
            callback,
            executor
        )
    }

    fun stop() {
        foldProvider.unregisterCallback(callback)
    }

    override fun addCallback(listener: ScreenListener) {
        listeners.add(listener)
    }

    override fun removeCallback(listener: ScreenListener) {
        listeners.remove(listener)
    }
}
