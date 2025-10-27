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

package com.android.app.viewcapture

import android.content.Context
import android.os.IBinder
import android.view.Window
import android.view.WindowManager
import com.android.app.viewcapture.ViewCaptureAwareWindowManagerFactory.instanceMap
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap


/** Factory to create [Context] specific instances of [ViewCaptureAwareWindowManager]. */
object ViewCaptureAwareWindowManagerFactory {

    /**
     * Keeps track of [ViewCaptureAwareWindowManager] instance for a [Context]. It is a
     * [WeakHashMap] to ensure that if a [Context] mapped in the [instanceMap] is destroyed, the map
     * entry is garbage collected as well.
     */
    private val instanceMap =
        Collections.synchronizedMap(WeakHashMap<Context, WeakReference<WindowManager>>())

    /**
     * Returns the weakly cached [ViewCaptureAwareWindowManager] instance for a given [Context]. If
     * no instance is cached; it creates, caches and returns a new instance.
     */
    @JvmStatic
    fun getInstance(
        context: Context,
        parent: Window? = null,
        windowContextToken: IBinder? = null,
    ): WindowManager {

        val cachedWindowManager = instanceMap[context]?.get()
        if (cachedWindowManager != null) {
            return cachedWindowManager
        } else {
            val windowManager = ViewCaptureAwareWindowManager(context, parent, windowContextToken)
            instanceMap[context] = WeakReference(windowManager)
            return windowManager
        }
    }
}
