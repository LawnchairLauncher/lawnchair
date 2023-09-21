/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.app.viewcapture

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.Choreographer
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import java.util.concurrent.Executor

/**
 * ViewCapture that listens to system updates and enables / disables attached ViewCapture
 * WindowListeners accordingly. The Settings toggle is currently controlled by the Winscope
 * developer tile in the System developer options.
 */
class SettingsAwareViewCapture
@VisibleForTesting
internal constructor(private val context: Context, choreographer: Choreographer, executor: Executor)
    : ViewCapture(DEFAULT_MEMORY_SIZE, DEFAULT_INIT_POOL_SIZE, choreographer, executor) {

    init {
        enableOrDisableWindowListeners()
        context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(VIEW_CAPTURE_ENABLED),
                false,
                object : ContentObserver(Handler()) {
                    override fun onChange(selfChange: Boolean) {
                        enableOrDisableWindowListeners()
                    }
                })
    }

    @AnyThread
    private fun enableOrDisableWindowListeners() {
        mBgExecutor.execute {
            val isEnabled = Settings.Global.getInt(context.contentResolver, VIEW_CAPTURE_ENABLED,
                    0) != 0
            MAIN_EXECUTOR.execute {
                enableOrDisableWindowListeners(isEnabled)
            }
        }
    }

    companion object {
        @VisibleForTesting internal const val VIEW_CAPTURE_ENABLED = "view_capture_enabled"

        private var INSTANCE: ViewCapture? = null

        @JvmStatic
        fun getInstance(context: Context): ViewCapture = when {
            INSTANCE != null -> INSTANCE!!
            Looper.myLooper() == Looper.getMainLooper() -> SettingsAwareViewCapture(
                    context.applicationContext, Choreographer.getInstance(),
                    createAndStartNewLooperExecutor("SAViewCapture",
                    Process.THREAD_PRIORITY_FOREGROUND)).also { INSTANCE = it }
            else -> try {
                MAIN_EXECUTOR.submit { getInstance(context) }.get()
            } catch (e: Exception) {
                throw e
            }
        }

    }
}