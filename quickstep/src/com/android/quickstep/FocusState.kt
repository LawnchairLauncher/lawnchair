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
import android.view.Display.DEFAULT_DISPLAY
import com.android.launcher3.util.Executors
import com.android.wm.shell.shared.IFocusTransitionListener.Stub
import com.android.wm.shell.shared.IShellTransitions

/** Class to track focus state of displays and windows */
class FocusState {

    var focusedDisplayId = DEFAULT_DISPLAY
        private set(value) {
            field = value
            listeners.forEach { it.onFocusedDisplayChanged(value) }
        }

    private var listeners = mutableSetOf<FocusChangeListener>()

    fun addListener(l: FocusChangeListener) = listeners.add(l)

    fun removeListener(l: FocusChangeListener) = listeners.remove(l)

    fun init(transitions: IShellTransitions?) {
        try {
            transitions?.setFocusTransitionListener(
                object : Stub() {
                    override fun onFocusedDisplayChanged(displayId: Int) {
                        Executors.MAIN_EXECUTOR.execute { focusedDisplayId = displayId }
                    }
                }
            )
        } catch (e: RemoteException) {
            Log.w(TAG, "Failed call setFocusTransitionListener", e)
        }
    }

    interface FocusChangeListener {
        fun onFocusedDisplayChanged(displayId: Int)
    }

    override fun toString() = "{FocusState focusedDisplayId=$focusedDisplayId}"

    companion object {
        private const val TAG = "FocusState"
    }
}
