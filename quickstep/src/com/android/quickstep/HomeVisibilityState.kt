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

package com.android.quickstep

import android.os.RemoteException
import android.util.Log
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.util.Executors
import com.android.wm.shell.shared.IHomeTransitionListener.Stub
import com.android.wm.shell.shared.IShellTransitions

/** Class to track visibility state of Launcher */
class HomeVisibilityState {

    var isHomeVisible = true
        private set

    private var listeners = mutableSetOf<VisibilityChangeListener>()

    fun addListener(l: VisibilityChangeListener) = listeners.add(l)

    fun removeListener(l: VisibilityChangeListener) = listeners.remove(l)

    fun init(transitions: IShellTransitions?) {
        if (!FeatureFlags.enableHomeTransitionListener()) return
        try {
            transitions?.setHomeTransitionListener(
                object : Stub() {
                    override fun onHomeVisibilityChanged(isVisible: Boolean) {
                        Executors.MAIN_EXECUTOR.execute {
                            isHomeVisible = isVisible
                            listeners.forEach { it.onHomeVisibilityChanged(isVisible) }
                        }
                    }
                }
            )
        } catch (e: RemoteException) {
            Log.w(TAG, "Failed call setHomeTransitionListener", e)
        }
    }

    interface VisibilityChangeListener {
        fun onHomeVisibilityChanged(isVisible: Boolean)
    }

    override fun toString() = "{HomeVisibilityState isHomeVisible=$isHomeVisible}"

    companion object {

        private const val TAG = "HomeVisibilityState"
    }
}
