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

import android.content.Context
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager

/**
 * An [AdditionalViewContainer] that uses the system [WindowManager] instance. Intended
 * for view containers that should be above the status bar layer.
 */
class AdditionalSystemViewContainer(
    private val context: Context,
    layoutId: Int,
    taskId: Int,
    x: Int,
    y: Int,
    width: Int,
    height: Int
) : AdditionalViewContainer() {
    override val view: View

    init {
        view = LayoutInflater.from(context).inflate(layoutId, null)
        val lp = WindowManager.LayoutParams(
            width, height, x, y,
            WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSPARENT
        )
        lp.title = "Additional view container of Task=$taskId"
        lp.setTrustedOverlay()
        val wm: WindowManager? = context.getSystemService(WindowManager::class.java)
        wm?.addView(view, lp)
    }

    override fun releaseView() {
        context.getSystemService(WindowManager::class.java)?.removeViewImmediate(view)
    }

    override fun setPosition(t: SurfaceControl.Transaction, x: Float, y: Float) {
        val lp = (view.layoutParams as WindowManager.LayoutParams).apply {
            this.x = x.toInt()
            this.y = y.toInt()
        }
        view.layoutParams = lp
    }
}
