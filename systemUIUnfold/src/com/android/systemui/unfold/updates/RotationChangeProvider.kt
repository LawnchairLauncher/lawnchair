/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.unfold.updates

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.RemoteException
import com.android.systemui.unfold.dagger.UnfoldMain
import com.android.systemui.unfold.util.CallbackController
import javax.inject.Inject

/**
 * Allows to subscribe to rotation changes. Updates are provided for the display associated
 * to [context].
 */
class RotationChangeProvider
@Inject
constructor(
    private val displayManager: DisplayManager,
    private val context: Context,
    @UnfoldMain private val mainHandler: Handler,
) : CallbackController<RotationChangeProvider.RotationListener> {

    private val listeners = mutableListOf<RotationListener>()

    private val displayListener = RotationDisplayListener()
    private var lastRotation: Int? = null

    override fun addCallback(listener: RotationListener) {
        mainHandler.post {
            if (listeners.isEmpty()) {
                subscribeToRotation()
            }
            listeners += listener
        }
    }

    override fun removeCallback(listener: RotationListener) {
        mainHandler.post {
            listeners -= listener
            if (listeners.isEmpty()) {
                unsubscribeToRotation()
                lastRotation = null
            }
        }
    }

    private fun subscribeToRotation() {
        try {
            displayManager.registerDisplayListener(displayListener, mainHandler)
        } catch (e: RemoteException) {
            throw e.rethrowFromSystemServer()
        }
    }

    private fun unsubscribeToRotation() {
        try {
            displayManager.unregisterDisplayListener(displayListener)
        } catch (e: RemoteException) {
            throw e.rethrowFromSystemServer()
        }
    }

    /** Gets notified of rotation changes. */
    fun interface RotationListener {
        /** Called once rotation changes. */
        fun onRotationChanged(newRotation: Int)
    }

    private inner class RotationDisplayListener : DisplayManager.DisplayListener {

        override fun onDisplayChanged(displayId: Int) {
            val display = context.display ?: return

            if (displayId == display.displayId) {
                val currentRotation = display.rotation
                if (lastRotation == null || lastRotation != currentRotation) {
                    listeners.forEach { it.onRotationChanged(currentRotation) }
                    lastRotation = currentRotation
                }
            }
        }

        override fun onDisplayAdded(displayId: Int) {}

        override fun onDisplayRemoved(displayId: Int) {}
    }
}
