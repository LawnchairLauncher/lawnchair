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
import android.os.Looper
import android.os.Process
import android.tracing.Flags
import android.util.Log

/**
 * Factory to create polymorphic instances of ViewCapture according to build configurations and
 * flags.
 */
class ViewCaptureFactory {
    companion object {
        private val TAG = ViewCaptureFactory::class.java.simpleName
        private var instance: ViewCapture? = null

        @JvmStatic
        fun getInstance(context: Context): ViewCapture {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                return ViewCapture.MAIN_EXECUTOR.submit { getInstance(context) }.get()
            }

            if (instance != null) {
                return instance!!
            }

            return when {
                !android.os.Build.IS_DEBUGGABLE -> {
                    Log.i(TAG, "instantiating ${NoOpViewCapture::class.java.simpleName}")
                    NoOpViewCapture()
                }
                !Flags.perfettoViewCaptureTracing() -> {
                    Log.i(TAG, "instantiating ${SettingsAwareViewCapture::class.java.simpleName}")
                    SettingsAwareViewCapture(
                            context.applicationContext,
                            ViewCapture.createAndStartNewLooperExecutor(
                                    "SAViewCapture",
                                    Process.THREAD_PRIORITY_FOREGROUND
                            )
                    )
                }
                else -> {
                    Log.i(TAG, "instantiating ${PerfettoViewCapture::class.java.simpleName}")
                    PerfettoViewCapture(
                            context.applicationContext,
                            ViewCapture.createAndStartNewLooperExecutor(
                                    "PerfettoViewCapture",
                                    Process.THREAD_PRIORITY_FOREGROUND
                            )
                    )
                }
            }.also { instance = it }
        }
    }
}
