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

import android.graphics.Region
import android.os.RemoteException
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.ISystemGestureExclusionListener
import android.view.IWindowManager
import android.view.WindowManagerGlobal
import androidx.annotation.BinderThread
import androidx.annotation.VisibleForTesting
import com.android.launcher3.util.Executors

/** Wrapper over system gesture exclusion listener to optimize for multiple RPCs */
class GestureExclusionManager(private val windowManager: IWindowManager) {

    private val listeners = mutableListOf<ExclusionListener>()

    private var lastExclusionRegion: Region? = null
    private var lastUnrestrictedOrNull: Region? = null

    @VisibleForTesting
    val exclusionListener =
        object : ISystemGestureExclusionListener.Stub() {
            @BinderThread
            override fun onSystemGestureExclusionChanged(
                displayId: Int,
                exclusionRegion: Region?,
                unrestrictedOrNull: Region?
            ) {
                if (displayId != DEFAULT_DISPLAY) {
                    return
                }
                Executors.MAIN_EXECUTOR.execute {
                    lastExclusionRegion = exclusionRegion
                    lastUnrestrictedOrNull = unrestrictedOrNull
                    listeners.forEach {
                        it.onGestureExclusionChanged(exclusionRegion, unrestrictedOrNull)
                    }
                }
            }
        }

    /** Adds a listener for receiving gesture exclusion regions */
    fun addListener(listener: ExclusionListener) {
        val wasEmpty = listeners.isEmpty()
        listeners.add(listener)
        if (wasEmpty) {
            Executors.UI_HELPER_EXECUTOR.execute {
                try {
                    windowManager.registerSystemGestureExclusionListener(
                        exclusionListener,
                        DEFAULT_DISPLAY
                    )
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to register gesture exclusion listener", e)
                }
            }
        } else {
            // If we had already registered before, dispatch the last known value,
            // otherwise registering the listener will initiate a dispatch
            listener.onGestureExclusionChanged(lastExclusionRegion, lastUnrestrictedOrNull)
        }
    }

    /** Removes a previously added exclusion listener */
    fun removeListener(listener: ExclusionListener) {
        if (listeners.remove(listener) && listeners.isEmpty()) {
            Executors.UI_HELPER_EXECUTOR.execute {
                try {
                    windowManager.unregisterSystemGestureExclusionListener(
                        exclusionListener,
                        DEFAULT_DISPLAY
                    )
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to unregister gesture exclusion listener", e)
                }
            }
        }
    }

    interface ExclusionListener {
        fun onGestureExclusionChanged(exclusionRegion: Region?, unrestrictedOrNull: Region?)
    }

    companion object {

        private const val TAG = "GestureExclusionManager"

        @JvmField
        val INSTANCE = GestureExclusionManager(WindowManagerGlobal.getWindowManagerService()!!)

        @JvmField val EMPTY_REGION = Region()
    }
}
