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
import android.graphics.PixelFormat
import android.graphics.Region
//import android.os.Trace.TRACE_TAG_WINDOW_MANAGER
import android.view.Display
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION
import android.widget.FrameLayout
//import androidx.tracing.Trace
//import com.android.app.tracing.traceSection
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.windowdecor.extension.identityHashCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * An implementation of [WindowDecorViewHost] that supports:
 * 1) Replacing the root [View], meaning [WindowDecorViewHost.updateView] maybe be called with
 *    different [View] instances. This is useful when reusing [WindowDecorViewHost]s instances for
 *    vastly different view hierarchies, such as Desktop Windowing's App Handles and App Headers.
 * 2) Pre-warming of the underlying [SurfaceControlViewHostAdapter]s. Useful because their creation
 *    and first root view assignment are expensive, which is undesirable in latency-sensitive code
 *    paths like during a shell transition.
 */
class ReusableWindowDecorViewHost(
    private val context: Context,
    @ShellMainThread private val mainScope: CoroutineScope,
    private val display: Display,
    val id: Int,
    @VisibleForTesting
    val viewHostAdapter: SurfaceControlViewHostAdapter =
        SurfaceControlViewHostAdapter(context, display),
    private val rootView: FrameLayout = FrameLayout(context),
) : WindowDecorViewHost, Warmable {

    @VisibleForTesting
    val defaultLayoutParams =
        WindowManager.LayoutParams(
                0 /* width*/,
                0 /* height */,
                TYPE_APPLICATION,
                FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT,
            )
            .apply {
                setTitle("View root of $TAG#$id")
                setTrustedOverlay()
            }

    private var currentUpdateJob: Job? = null

    override val surfaceControl: SurfaceControl
        get() = viewHostAdapter.rootSurface

    override val displayId: Int
        get() = display.displayId

    override fun warmUp() {
        if (viewHostAdapter.isInitialized()) {
            // Already warmed up.
            return
        }
        reset()
    }

    override fun updateView(
        view: View,
        attrs: WindowManager.LayoutParams,
        configuration: Configuration,
        touchableRegion: Region?,
        onDrawTransaction: SurfaceControl.Transaction?,
    ) {
//        Trace.beginSection("ReusableWindowDecorViewHost#updateView")
        clearCurrentUpdateJob()
        updateViewHost(view, attrs, configuration, touchableRegion, onDrawTransaction)
//        Trace.endSection()
    }

    override fun updateViewAsync(
        view: View,
        attrs: WindowManager.LayoutParams,
        configuration: Configuration,
        touchableRegion: Region?,
    ) {
//        Trace.beginSection("ReusableWindowDecorViewHost#updateViewAsync")
        clearCurrentUpdateJob()
        currentUpdateJob =
            mainScope.launch {
                updateViewHost(
                    view,
                    attrs,
                    configuration,
                    touchableRegion,
                    onDrawTransaction = null,
                )
            }
//        Trace.endSection()
    }

    override fun reset() {
            clearCurrentUpdateJob()
            updateViewHost(
                view = null,
                defaultLayoutParams,
                context.resources.configuration,
                touchableRegion = null,
                onDrawTransaction = null,
            )
        }

    override fun release(t: SurfaceControl.Transaction) {
        clearCurrentUpdateJob()
        viewHostAdapter.release(t)
    }

    private fun updateViewHost(
        view: View?,
        attrs: WindowManager.LayoutParams,
        configuration: Configuration,
        touchableRegion: Region?,
        onDrawTransaction: SurfaceControl.Transaction?,
    ) {
//        Trace.beginSection("ReusableWindowDecorViewHost#updateViewHost")
        rootView.layoutDirection = configuration.layoutDirection
        viewHostAdapter.prepareViewHost(configuration, touchableRegion)
        onDrawTransaction?.let { viewHostAdapter.applyTransactionOnDraw(it) }
        when {
            view == null -> rootView.removeAllViews()
            view.parent != rootView -> {
                rootView.removeAllViews()
                rootView.addView(view)
            }
        }
        viewHostAdapter.updateView(rootView, attrs)
//        Trace.endSection()
    }

    private fun clearCurrentUpdateJob() {
        currentUpdateJob?.cancel()
        currentUpdateJob = null
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return "ReusableWindowDecorViewHost(" +
            "rootView=${rootView.identityHashCode.toHexString()}" +
            ")"
    }

    companion object {
        private const val TAG = "ReusableWindowDecorViewHost"
    }
}
