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

package com.android.app.viewcapture

import android.content.Context
import android.os.Process
import android.tracing.Flags
import android.util.Log

/**
 * Factory to create polymorphic instances of ViewCapture according to build configurations and
 * flags.
 */
object ViewCaptureFactory {
    private val TAG = ViewCaptureFactory::class.java.simpleName
    private val instance: ViewCapture by lazy { createInstance() }
    private lateinit var appContext: Context

    private fun createInstance(): ViewCapture {
        return when {
            !false -> {
                Log.i(TAG, "instantiating ${NoOpViewCapture::class.java.simpleName}")
                NoOpViewCapture()
            }
            !Flags.perfettoViewCaptureTracing() -> {
                Log.i(TAG, "instantiating ${SettingsAwareViewCapture::class.java.simpleName}")
                SettingsAwareViewCapture(
                    appContext,
                    ViewCapture.createAndStartNewLooperExecutor(
                        "SAViewCapture",
                        Process.THREAD_PRIORITY_FOREGROUND,
                    ),
                )
            }
            else -> {
                Log.i(TAG, "instantiating ${PerfettoViewCapture::class.java.simpleName}")
                PerfettoViewCapture(
                    appContext,
                    ViewCapture.createAndStartNewLooperExecutor(
                        "PerfettoViewCapture",
                        Process.THREAD_PRIORITY_FOREGROUND,
                    ),
                )
            }
        }
    }

    /** Returns an instance of [ViewCapture]. */
    @JvmStatic
    fun getInstance(context: Context): ViewCapture {
        if (!this::appContext.isInitialized) {
            synchronized(this) { appContext = context.applicationContext }
        }
        return instance
    }
}
