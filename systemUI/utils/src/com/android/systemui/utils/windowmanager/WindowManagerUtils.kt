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

package com.android.systemui.utils.windowmanager

import android.content.Context
import android.view.WindowManager
import com.android.systemui.Flags.enableViewCaptureTracing

/**
 * Provides [WindowManager] in SystemUI. Use [WindowManagerProvider] unless [WindowManager] instance
 * needs to be created in a class that is not part of the dagger dependency graph.
 */
object WindowManagerUtils {

    /** Method to return the required [WindowManager]. */
    @JvmStatic
    fun getWindowManager(context: Context): WindowManager {
        return (if (!enableViewCaptureTracing()) {
            context.getSystemService(WindowManager::class.java)
        } else {
            // LC-Ignored
        }) as WindowManager
    }
}
