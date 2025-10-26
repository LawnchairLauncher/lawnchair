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

import android.content.Context
import android.util.Log
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.quickstep.dagger.QuickstepBaseAppComponent
import javax.inject.Inject

@LauncherAppSingleton
class SystemDecorationChangeObserver @Inject constructor(@ApplicationContext context: Context) {
    companion object {
        private const val TAG = "SystemDecorationChangeObserver"
        private const val DEBUG = false

        @JvmStatic
        val INSTANCE: DaggerSingletonObject<SystemDecorationChangeObserver> =
            DaggerSingletonObject<SystemDecorationChangeObserver>(
                QuickstepBaseAppComponent::getSystemDecorationChangeObserver
            )
    }

    interface DisplayDecorationListener {
        fun onDisplayAddSystemDecorations(displayId: Int)

        fun onDisplayRemoved(displayId: Int)

        fun onDisplayRemoveSystemDecorations(displayId: Int)
    }

    fun notifyAddSystemDecorations(displayId: Int) {
        if (DEBUG) Log.d(TAG, "SystemDecorationAdded: $displayId")
        for (listener in mDisplayDecorationListeners) {
            MAIN_EXECUTOR.execute { listener.onDisplayAddSystemDecorations(displayId) }
        }
    }

    fun notifyOnDisplayRemoved(displayId: Int) {
        if (DEBUG) Log.d(TAG, "displayRemoved: $displayId")
        for (listener in mDisplayDecorationListeners) {
            MAIN_EXECUTOR.execute { listener.onDisplayRemoved(displayId) }
        }
    }

    fun notifyDisplayRemoveSystemDecorations(displayId: Int) {
        if (DEBUG) Log.d(TAG, "SystemDecorationRemoved: $displayId")
        for (listener in mDisplayDecorationListeners) {
            MAIN_EXECUTOR.execute { listener.onDisplayRemoveSystemDecorations(displayId) }
        }
    }

    private val mDisplayDecorationListeners = ArrayList<DisplayDecorationListener>()

    fun registerDisplayDecorationListener(listener: DisplayDecorationListener) {
        if (DEBUG) Log.d(TAG, "registerDisplayDecorationListener")
        mDisplayDecorationListeners.add(listener)
    }

    fun unregisterDisplayDecorationListener(listener: DisplayDecorationListener) {
        if (DEBUG) Log.d(TAG, "unregisterDisplayDecorationListener")
        mDisplayDecorationListeners.remove(listener)
    }
}
