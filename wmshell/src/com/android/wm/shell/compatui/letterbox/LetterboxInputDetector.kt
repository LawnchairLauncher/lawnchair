/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.content.Context
import android.graphics.Region
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.RemoteException
import android.view.GestureDetector
import android.view.IWindowSession
import android.view.InputChannel
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager
import android.window.InputTransferToken
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.suppliers.WindowSessionSupplier
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT

/** This is responsible for detecting events on a given [SurfaceControl]. */
class LetterboxInputDetector(
    private val context: Context,
    private val handler: Handler,
    private val letterboxListener: GestureDetector.SimpleOnGestureListener,
    private val inputSurfaceBuilder: LetterboxInputSurfaceBuilder,
    private val windowSessionSupplier: WindowSessionSupplier,
) {

    companion object {
        @JvmStatic private val TAG = "LetterboxInputDetector"
    }

    private var state: InputDetectorState? = null

    fun start(tx: Transaction, source: SurfaceControl, key: LetterboxKey) {
        if (!isRunning()) {
            val tmpState =
                InputDetectorState(
                    context,
                    handler,
                    source,
                    key.displayId,
                    letterboxListener,
                    inputSurfaceBuilder,
                    windowSessionSupplier.get(),
                )
            if (tmpState.start(tx)) {
                state = tmpState
            } else {
                ProtoLog.v(
                    WM_SHELL_APP_COMPAT,
                    "%s not started for %s on %s",
                    TAG,
                    "$source",
                    "$key",
                )
            }
        }
    }

    fun updateTouchableRegion(tx: Transaction, region: Region) {
        if (isRunning()) {
            state?.setTouchableRegion(tx, region)
        }
    }

    fun isRunning() = state != null

    fun updateVisibility(tx: Transaction, visible: Boolean) {
        if (isRunning()) {
            state?.updateVisibility(tx, visible)
        }
    }

    fun stop(tx: Transaction) {
        if (isRunning()) {
            state!!.stop(tx)
            state = null
        }
    }

    /** The state for a {@link SurfaceControl} for a given displayId. */
    private class InputDetectorState(
        val context: Context,
        val handler: Handler,
        val source: SurfaceControl,
        val displayId: Int,
        val letterboxListener: GestureDetector.SimpleOnGestureListener,
        val inputSurfaceBuilder: LetterboxInputSurfaceBuilder,
        val windowSession: IWindowSession,
    ) {

        private val inputToken: IBinder
        private var receiver: EventReceiver? = null
        private var inputSurface: SurfaceControl? = null

        init {
            inputToken = Binder()
        }

        fun start(tx: Transaction): Boolean {
            val inputTransferToken = InputTransferToken()
            try {
                inputSurface =
                    inputSurfaceBuilder.createInputSurface(
                        tx,
                        source,
                        "ShellLetterboxInputSurface $source",
                        "$TAG creation",
                    )
                val inputChannel =
                    windowSession.grantInputChannel(
                        displayId,
                        inputSurface,
                        inputToken,
                        null,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY,
                        WindowManager.LayoutParams.INPUT_FEATURE_SPY,
                        WindowManager.LayoutParams.TYPE_INPUT_CONSUMER,
                        null,
                        inputTransferToken,
                        "$TAG of $source",
                    )
                receiver = EventReceiver(context, inputChannel, handler, letterboxListener)
                return true
            } catch (e: RemoteException) {
                e.rethrowFromSystemServer()
            }
            return false
        }

        fun setTouchableRegion(tx: Transaction, region: Region) {
            try {
                tx.setWindowCrop(inputSurface, region.bounds.width(), region.bounds.height())

                windowSession.updateInputChannel(
                    receiver?.token,
                    null /* hostInputTransferToken */,
                    displayId,
                    inputSurface,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY,
                    WindowManager.LayoutParams.INPUT_FEATURE_SPY,
                    region,
                )
            } catch (e: RemoteException) {
                e.rethrowFromSystemServer()
            }
        }

        fun updateVisibility(tx: Transaction, visible: Boolean) {
            inputSurface?.let { tx.setVisibility(it, visible) }
        }

        fun stop(tx: Transaction) {
            handler.post(::resetInputState)
            inputSurface?.let { s -> tx.remove(s) }
        }

        private fun resetInputState() {
            receiver?.dispose()
            receiver = null
            windowSession.removeToken(inputToken)
        }

        // Removes the provided token
        private fun IWindowSession.removeToken(token: IBinder) {
            try {
                remove(token)
            } catch (e: RemoteException) {
                e.rethrowFromSystemServer()
            }
        }
    }

    /** Reads from the provided {@link InputChannel} and identifies a specific event. */
    private class EventReceiver(
        context: Context,
        inputChannel: InputChannel,
        uiHandler: Handler,
        listener: GestureDetector.SimpleOnGestureListener,
    ) : InputEventReceiver(inputChannel, uiHandler.looper) {
        private val eventDetector: GestureDetector

        init {
            eventDetector = GestureDetector(context, listener, uiHandler)
        }

        override fun onInputEvent(event: InputEvent) {
            finishInputEvent(event, eventDetector.onTouchEvent(event as MotionEvent))
        }
    }
}
