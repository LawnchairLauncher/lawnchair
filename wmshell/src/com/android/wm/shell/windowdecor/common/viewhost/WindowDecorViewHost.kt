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

import android.content.res.Configuration
import android.graphics.Region
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager
import com.android.wm.shell.windowdecor.WindowDecoration

/**
 * An interface for a utility that hosts a [WindowDecoration]'s [View] hierarchy under a
 * [SurfaceControl].
 */
interface WindowDecorViewHost {
    /** The surface where the underlying [View] hierarchy is being rendered. */
    val surfaceControl: SurfaceControl

    /** Synchronously update the view hierarchy of this view host. */
    fun updateView(
        view: View,
        attrs: WindowManager.LayoutParams,
        configuration: Configuration,
        touchableRegion: Region? = null,
        onDrawTransaction: SurfaceControl.Transaction? = null,
    )

    /** Asynchronously update the view hierarchy of this view host. */
    fun updateViewAsync(
        view: View,
        attrs: WindowManager.LayoutParams,
        configuration: Configuration,
        touchableRegion: Region? = null,
    )

    /** Releases the underlying [View] hierarchy and removes the backing [SurfaceControl]. */
    fun release(t: SurfaceControl.Transaction)
}
