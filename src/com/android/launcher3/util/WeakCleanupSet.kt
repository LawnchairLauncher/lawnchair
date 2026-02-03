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

package com.android.launcher3.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import java.util.Collections
import java.util.WeakHashMap

/**
 * Utility class which maintains a list of cleanup callbacks using weak-references. These callbacks
 * are called when the [owner] is destroyed, but can also be cleared when the caller is GCed
 */
class WeakCleanupSet(owner: LifecycleOwner) {

    private val callbacks = Collections.newSetFromMap<OnOwnerDestroyedCallback>(WeakHashMap())
    private var destroyed = false

    init {
        MAIN_EXECUTOR.execute {
            owner.lifecycle.addObserver(
                object : DefaultLifecycleObserver {

                    override fun onDestroy(owner: LifecycleOwner) {
                        destroyed = true
                        callbacks.forEach { it.onOwnerDestroyed() }
                    }
                }
            )
        }
    }

    fun addOnOwnerDestroyedCallback(callback: OnOwnerDestroyedCallback) {
        if (destroyed) callback.onOwnerDestroyed() else callbacks.add(callback)
    }

    /** Callback when the owner is destroyed */
    interface OnOwnerDestroyedCallback {
        fun onOwnerDestroyed()
    }
}
