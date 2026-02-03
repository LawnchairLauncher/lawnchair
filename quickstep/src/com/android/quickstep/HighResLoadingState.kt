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

package com.android.quickstep

import android.content.res.Resources
import com.android.quickstep.recents.data.HighResLoadingStateNotifier
import java.util.concurrent.CopyOnWriteArrayList

/** Determines when high res or low res thumbnails should be loaded. */
class HighResLoadingState : HighResLoadingStateNotifier {
    // If the device does not support low-res thumbnails, only attempt to load high-res thumbnails
    private val forceHighResThumbnails = !supportsLowResThumbnails()
    var visible: Boolean = false
        set(value) {
            field = value
            updateState()
        }

    var flingingFast = false
        set(value) {
            field = value
            updateState()
        }

    var isEnabled: Boolean = false
        private set

    private val callbacks = CopyOnWriteArrayList<HighResLoadingStateChangedCallback>()

    interface HighResLoadingStateChangedCallback {
        fun onHighResLoadingStateChanged(enabled: Boolean)
    }

    override fun addCallback(callback: HighResLoadingStateChangedCallback) {
        callbacks.add(callback)
    }

    override fun removeCallback(callback: HighResLoadingStateChangedCallback) {
        callbacks.remove(callback)
    }

    private fun updateState() {
        val prevState = isEnabled
        isEnabled = forceHighResThumbnails || (visible && !flingingFast)
        if (prevState != isEnabled) {
            for (callback in callbacks) {
                callback.onHighResLoadingStateChanged(isEnabled)
            }
        }
    }

    /**
     * Returns Whether device supports low-res thumbnails. Low-res files are an optimization for
     * faster load times of snapshots. Devices can optionally disable low-res files so that they
     * only store snapshots at high-res scale. The actual scale can be configured in frameworks/base
     * config overlay.
     */
    private fun supportsLowResThumbnails(): Boolean {
        val res = Resources.getSystem()
        val resId = res.getIdentifier("config_lowResTaskSnapshotScale", "dimen", "android")
        if (resId != 0) {
            return 0 < res.getFloat(resId)
        }
        return true
    }
}
