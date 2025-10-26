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

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.util.SparseArray
import android.view.Display
import androidx.core.util.valueIterator
import com.android.quickstep.DisplayModel.DisplayResource
import com.android.quickstep.SystemDecorationChangeObserver.Companion.INSTANCE
import com.android.quickstep.SystemDecorationChangeObserver.DisplayDecorationListener
import java.io.PrintWriter

/** data model for managing resources with lifecycles that match that of the connected display */
abstract class DisplayModel<RESOURCE_TYPE : DisplayResource>(val context: Context) :
    DisplayDecorationListener {

    companion object {
        private const val TAG = "DisplayModel"
        private const val DEBUG = false
    }

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var systemDecorationChangeObserver: SystemDecorationChangeObserver? = null
    protected val displayResourceArray = SparseArray<RESOURCE_TYPE>()

    override fun onDisplayAddSystemDecorations(displayId: Int) {
        if (DEBUG) Log.d(TAG, "onDisplayAdded: displayId=$displayId")
        storeDisplayResource(displayId)
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (DEBUG) Log.d(TAG, "onDisplayRemoved: displayId=$displayId")
        deleteDisplayResource(displayId)
    }

    override fun onDisplayRemoveSystemDecorations(displayId: Int) {
        if (DEBUG) Log.d(TAG, "onDisplayRemoveSystemDecorations: displayId=$displayId")
        deleteDisplayResource(displayId)
    }

    protected abstract fun createDisplayResource(display: Display): RESOURCE_TYPE

    protected fun initializeDisplays() {
        systemDecorationChangeObserver = INSTANCE[context]
        systemDecorationChangeObserver?.registerDisplayDecorationListener(this)
        displayManager.displays
            .filter { getDisplayResource(it.displayId) == null }
            .forEach { storeDisplayResource(it.displayId) }
    }

    fun destroy() {
        systemDecorationChangeObserver?.unregisterDisplayDecorationListener(this)
        systemDecorationChangeObserver = null
        displayResourceArray.valueIterator().forEach { displayResource ->
            displayResource.cleanup()
        }
        displayResourceArray.clear()
    }

    fun getDisplayResource(displayId: Int): RESOURCE_TYPE? {
        if (DEBUG) Log.d(TAG, Log.getStackTraceString(Throwable("get: displayId=$displayId")))
        return displayResourceArray[displayId]
    }

    fun deleteDisplayResource(displayId: Int) {
        if (DEBUG) Log.d(TAG, "delete: displayId=$displayId")
        getDisplayResource(displayId)?.let {
            it.cleanup()
            displayResourceArray.remove(displayId)
        }
    }

    fun storeDisplayResource(displayId: Int) {
        if (DEBUG) Log.d(TAG, "store: displayId=$displayId")
        getDisplayResource(displayId)?.let {
            return
        }
        val display = displayManager.getDisplay(displayId)
        if (display == null) {
            if (DEBUG)
                Log.w(
                    TAG,
                    "storeDisplayResource: could not create display for displayId=$displayId",
                    Exception(),
                )
            return
        }
        displayResourceArray[displayId] = createDisplayResource(display)
    }

    fun dump(prefix: String, writer: PrintWriter) {
        writer.println("${prefix}${this::class.simpleName}: display resources=[")

        displayResourceArray.valueIterator().forEach { displayResource ->
            displayResource.dump("${prefix}\t", writer)
        }
        writer.println("${prefix}]")
    }

    abstract class DisplayResource {
        abstract fun cleanup()

        abstract fun dump(prefix: String, writer: PrintWriter)
    }
}
