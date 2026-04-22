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
import android.media.permission.SafeCloseable
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.WindowManagerImpl

/**
 * [WindowManager] implementation to enable view tracing. Adds [ViewCapture] to associated window
 * when it is added to view hierarchy. Use [ViewCaptureAwareWindowManagerFactory] to create an
 * instance of this class.
 */
internal class ViewCaptureAwareWindowManager(
    private val context: Context,
    private val parent: Window? = null,
    private val windowContextToken: IBinder? = null,
) : WindowManagerImpl(context, parent, windowContextToken) {

    private var viewCaptureCloseableMap: MutableMap<View, SafeCloseable> = mutableMapOf()

    override fun addView(view: View, params: ViewGroup.LayoutParams) {
        super.addView(view, params)
        val viewCaptureCloseable: SafeCloseable =
            ViewCaptureFactory.getInstance(context).startCapture(view, getViewName(view))
        viewCaptureCloseableMap[view] = viewCaptureCloseable
    }

    override fun removeView(view: View?) {
        removeViewFromCloseableMap(view)
        super.removeView(view)
    }

    override fun removeViewImmediate(view: View?) {
        removeViewFromCloseableMap(view)
        super.removeViewImmediate(view)
    }

    private fun getViewName(view: View) = "." + view.javaClass.name

    private fun removeViewFromCloseableMap(view: View?) {
        if (viewCaptureCloseableMap.containsKey(view)) {
            viewCaptureCloseableMap[view]?.close()
            viewCaptureCloseableMap.remove(view)
        }
    }
}
