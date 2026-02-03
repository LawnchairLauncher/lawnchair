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
package com.android.wm.shell.windowdecor.common.viewhost

import android.content.Context
import android.content.res.Configuration
import android.graphics.Region
import android.view.AttachedSurfaceControl
import android.view.Display
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import android.view.WindowlessWindowManager
import androidx.tracing.Trace
import com.android.internal.annotations.VisibleForTesting

typealias SurfaceControlViewHostFactory =
    (Context, Display, WindowlessWindowManager, String) -> SurfaceControlViewHost

/**
 * Adapter for a [SurfaceControlViewHost] and its backing [SurfaceControl].
 *
 * It does not support swapping the root view added to the VRI of the [SurfaceControlViewHost], and
 * any attempts to do will throw, which means that once a [View] is added using [updateView], only
 * its properties and binding may be changed, children views may be added, removed or changed
 * and its [WindowManager.LayoutParams] may be changed.
 */
class SurfaceControlViewHostAdapter(
    private val context: Context,
    private val display: Display,
    private val surfaceControlViewHostFactory: SurfaceControlViewHostFactory = { c, d, wwm, s ->
        SurfaceControlViewHost(c, d, wwm, s)
    },
) {
    val rootSurface: SurfaceControl =
        SurfaceControl.Builder()
            .setName("SurfaceControlViewHostAdapter surface")
            .setContainerLayer()
            .setCallsite("SurfaceControlViewHostAdapter#init")
            .build()

    private var wwm: WindowDecorWindowlessWindowManager? = null
    @VisibleForTesting var viewHost: SurfaceControlViewHost? = null

    /**
     * Initialize or updates the [SurfaceControlViewHost].
     */
    fun prepareViewHost(
        configuration: Configuration,
        touchableRegion: Region?
    ) {
        if (wwm == null) {
            wwm = WindowDecorWindowlessWindowManager(configuration, rootSurface)
        }
        if (viewHost == null) {
            viewHost =
                surfaceControlViewHostFactory.invoke(
                    context,
                    display,
                    requireWindowlessWindowManager(),
                    "SurfaceControlViewHostAdapter#prepareViewHost",
                )
        }
        requireWindowlessWindowManager().setConfiguration(configuration)
        requireWindowlessWindowManager().setTouchRegion(requireViewHost(), touchableRegion)
    }

    /**
     * Request to apply the transaction atomically with the next draw of the view hierarchy. See
     * [AttachedSurfaceControl.applyTransactionOnDraw].
     */
    fun applyTransactionOnDraw(t: SurfaceControl.Transaction) {
        requireViewHost().rootSurfaceControl.applyTransactionOnDraw(t)
    }

    /** Update the view hierarchy of the view host. */
    fun updateView(view: View, attrs: WindowManager.LayoutParams) {
        if (requireViewHost().view == null) {
            Trace.beginSection("SurfaceControlViewHostAdapter#updateView-setView")
            requireViewHost().setView(view, attrs)
            Trace.endSection()
        } else {
            check(requireViewHost().view == view) { "Changing view is not allowed" }
            Trace.beginSection("SurfaceControlViewHostAdapter#updateView-relayout")
            requireViewHost().relayout(attrs)
            Trace.endSection()
        }
    }

    /** Release the view host and remove the backing surface. */
    fun release(t: SurfaceControl.Transaction) {
        viewHost?.release()
        t.remove(rootSurface)
    }

    /** Whether the view host has had a view hierarchy set. */
    fun isInitialized(): Boolean = viewHost?.view != null

    private fun requireWindowlessWindowManager(): WindowDecorWindowlessWindowManager {
        return wwm ?: error("Expected non-null windowless window manager")
    }

    private fun requireViewHost(): SurfaceControlViewHost {
        return viewHost ?: error("Expected non-null view host")
    }
}
