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
package com.android.launcher3.preview

import android.content.ContentValues
import android.os.Bundle
import android.os.Handler.Callback
import android.os.IBinder.DeathRecipient
import android.os.Message
import android.util.Log
import com.android.launcher3.graphics.GridCustomizationsProxy.ICON_THEMED
import com.android.launcher3.graphics.GridCustomizationsProxy.KEY_DEFAULT_GRID
import com.android.launcher3.graphics.GridCustomizationsProxy.KEY_HIDE_BOTTOM_ROW
import com.android.launcher3.graphics.GridCustomizationsProxy.KEY_UPDATE_METHOD
import com.android.launcher3.graphics.GridCustomizationsProxy.SET_SHAPE
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import com.android.systemui.shared.Flags

class PreviewLifecycleObserver(
    @JvmField val lifeCycleTracker: RunnableList,
    @JvmField val renderer: PreviewSurfaceRenderer,
) : Callback, DeathRecipient {
    var destroyed: Boolean = false
        private set

    init {
        lifeCycleTracker.add { destroyed = true }
    }

    private fun executeUpdate(command: String, values: Bundle) {
        renderer.customizationDelegate.handleUpdate(
            command,
            ContentValues().apply {
                valueSet()
                    .addAll(
                        values
                            .keySet()
                            .associateBy(keySelector = { it }, valueTransform = { values.get(it) })
                            .entries
                    )
            },
        )
    }

    override fun handleMessage(message: Message): Boolean {
        if (destroyed) {
            return true
        }

        when (message.what) {
            MESSAGE_ID_UPDATE_PREVIEW ->
                renderer.hideBottomRow(message.data.getBoolean(KEY_HIDE_BOTTOM_ROW))

            MESSAGE_ID_UPDATE_COLOR ->
                if (Flags.newCustomizationPickerUi()) renderer.previewColor(message.data)

            MESSAGE_ID_UPDATE_SHAPE -> executeUpdate(SET_SHAPE, message.data)

            MESSAGE_ID_UPDATE_GRID -> executeUpdate(KEY_DEFAULT_GRID, message.data)

            MESSAGE_ID_UPDATE_ICON_THEMED -> executeUpdate(ICON_THEMED, message.data)

            MESSAGE_ID_UPDATE_COMMAND ->
                executeUpdate(message.data.getString(KEY_UPDATE_METHOD) ?: "", message.data)

            else -> {
                // Unknown command, destroy lifecycle
                Log.d(TAG, "Unknown preview command: " + message.what + ", destroying preview")
                Executors.MAIN_EXECUTOR.execute { lifeCycleTracker.executeAllAndDestroy() }
            }
        }

        return true
    }

    override fun binderDied() =
        Executors.MAIN_EXECUTOR.execute { lifeCycleTracker.executeAllAndDestroy() }

    /** Two renderers are considered same if they have the same host token and display Id */
    fun isSameRenderer(plo: PreviewLifecycleObserver?): Boolean =
        plo != null &&
            plo.renderer.hostToken == renderer.hostToken &&
            plo.renderer.displayId == renderer.displayId

    companion object {
        const val TAG: String = "PreviewLifecycleObserver"
        private const val MESSAGE_ID_UPDATE_PREVIEW = 1337
        private const val MESSAGE_ID_UPDATE_COLOR = 856

        @Deprecated("Use [MESSAGE_ID_UPDATE_COMMAND] instead")
        private const val MESSAGE_ID_UPDATE_SHAPE = 2586
        @Deprecated("Use [MESSAGE_ID_UPDATE_COMMAND] instead")
        private const val MESSAGE_ID_UPDATE_GRID = 7414
        @Deprecated("Use [MESSAGE_ID_UPDATE_COMMAND] instead")
        private const val MESSAGE_ID_UPDATE_ICON_THEMED = 311

        /**
         * A generic message which delegates the actual update to the customization API The method
         * should be keyed using [KEY_UPDATE_METHOD] along with any additional parameters
         */
        private const val MESSAGE_ID_UPDATE_COMMAND = 512
    }
}
