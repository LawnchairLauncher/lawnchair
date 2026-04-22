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

package com.android.wm.shell.windowdecor.additionalviewcontainer

import android.annotation.LayoutRes
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
import com.android.wm.shell.windowdecor.WindowManagerWrapper

/**
 * An [AdditionalViewContainer] that uses the system [WindowManager] instance. Intended
 * for view containers that should be above the status bar layer.
 */
class AdditionalSystemViewContainer(
    private val windowManagerWrapper: WindowManagerWrapper,
    taskId: Int,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    flags: Int,
    @WindowInsets.Type.InsetsType forciblyShownTypes: Int = 0,
    ignoreCutouts: Boolean = false,
    override val view: View
) : AdditionalViewContainer() {
    val lp: WindowManager.LayoutParams = WindowManager.LayoutParams(
        width, height, x, y,
        WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL,
        flags,
        PixelFormat.TRANSPARENT
    ).apply {
        title = "Additional view container of Task=$taskId"
        gravity = Gravity.LEFT or Gravity.TOP
        setTrustedOverlay()
        this.forciblyShownTypes = forciblyShownTypes
        if (ignoreCutouts) {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    constructor(
        context: Context,
        windowManagerWrapper: WindowManagerWrapper,
        taskId: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        flags: Int,
        @LayoutRes layoutId: Int,
        ignoreCutouts: Boolean = false
    ) : this(
        windowManagerWrapper = windowManagerWrapper,
        taskId = taskId,
        x = x,
        y = y,
        width = width,
        height = height,
        flags = flags,
        view = LayoutInflater.from(context).inflate(layoutId, null /* parent */),
        ignoreCutouts = ignoreCutouts
    )

    constructor(
        context: Context,
        windowManagerWrapper: WindowManagerWrapper,
        taskId: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        flags: Int,
        ignoreCutouts: Boolean = false
    ) : this(
        windowManagerWrapper = windowManagerWrapper,
        taskId = taskId,
        x = x,
        y = y,
        width = width,
        height = height,
        flags = flags,
        view = View(context),
        ignoreCutouts = ignoreCutouts
    )

    init {
        windowManagerWrapper.addView(view, lp)
    }

    override fun releaseView() {
        windowManagerWrapper.removeViewImmediate(view)
    }

    override fun setPosition(t: SurfaceControl.Transaction, x: Float, y: Float) {
        val lp = (view.layoutParams as WindowManager.LayoutParams).apply {
            this.x = x.toInt()
            this.y = y.toInt()
        }
        windowManagerWrapper.updateViewLayout(view, lp)
    }

    class Factory {
        fun create(
            windowManagerWrapper: WindowManagerWrapper,
            taskId: Int,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            flags: Int,
            view: View,
        ): AdditionalSystemViewContainer =
            AdditionalSystemViewContainer(
                windowManagerWrapper = windowManagerWrapper,
                taskId = taskId,
                x = x,
                y = y,
                width = width,
                height = height,
                flags = flags,
                view = view
            )
    }
}
