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
import android.os.Trace
import androidx.annotation.AnyThread
import com.android.systemui.unfold.dagger.UnfoldBg
import com.android.systemui.unfold.util.CallbackController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private const val INVALID_ROTATION = -1

/**
 * Allows to subscribe to rotation changes. Updates are provided for the display associated to
 * [context].
 */
class RotationChangeProvider
@AssistedInject
constructor(
    private val displayManager: DisplayManager,
    private val context: Context,
    @UnfoldBg private val bgHandler: Handler,
    @Assisted private val callbackHandler: Handler,
) : CallbackController<RotationChangeProvider.RotationListener> {

    private val listeners = CopyOnWriteArrayList<RotationListener>()

    private val displayListener = RotationDisplayListener()
    private val lastRotation = AtomicInteger(INVALID_ROTATION)

    override fun addCallback(listener: RotationListener) {
        bgHandler.post {
            if (listeners.isEmpty()) {
                subscribeToRotation()
            }
            listeners += listener
        }
    }

    override fun removeCallback(listener: RotationListener) {
        bgHandler.post {
            listeners -= listener
            if (listeners.isEmpty()) {
                unsubscribeToRotation()
                lastRotation.set(INVALID_ROTATION)
            }
        }
    }

    private fun subscribeToRotation() {
        try {
            displayManager.registerDisplayListener(displayListener, callbackHandler)
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
        /**
         * Called once rotation changes. This callback is called on the handler provided to
         * [RotationChangeProvider.Factory.create].
         */
        @AnyThread fun onRotationChanged(newRotation: Int)
    }

    private inner class RotationDisplayListener : DisplayManager.DisplayListener {

        override fun onDisplayChanged(displayId: Int) {
            Trace.beginSection("RotationChangeProvider.RotationDisplayListener#onDisplayChanged")
            try {
                val display = context.display ?: return

                if (displayId == display.displayId) {
                    val currentRotation = display.rotation
                    if (lastRotation.compareAndSet(lastRotation.get(), currentRotation)) {
                        listeners.forEach { it.onRotationChanged(currentRotation) }
                    }
                }
            } finally {
                Trace.endSection()
            }
        }

        override fun onDisplayAdded(displayId: Int) {}

        override fun onDisplayRemoved(displayId: Int) {}
    }

    @AssistedFactory
    interface Factory {
        /** Creates a new [RotationChangeProvider] that provides updated using [callbackHandler]. */
        fun create(callbackHandler: Handler): RotationChangeProvider
    }
}
