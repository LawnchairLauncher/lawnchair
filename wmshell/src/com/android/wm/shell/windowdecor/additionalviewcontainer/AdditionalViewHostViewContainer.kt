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

import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import java.util.function.Supplier

/**
 * An [AdditionalViewContainer] that uses a [SurfaceControlViewHost] to show the window.
 * Intended for view containers in freeform tasks that do not extend beyond task bounds.
 */
class AdditionalViewHostViewContainer(
    private val windowSurface: SurfaceControl,
    private val windowViewHost: SurfaceControlViewHost,
    private val transactionSupplier: Supplier<SurfaceControl.Transaction>,
) : AdditionalViewContainer() {

    override val view
        get() = windowViewHost.view

    override fun releaseView() {
        windowViewHost.release()
        val t = transactionSupplier.get()
        t.remove(windowSurface)
        t.apply()
    }

    override fun setPosition(t: SurfaceControl.Transaction, x: Float, y: Float) {
        t.setPosition(windowSurface, x, y)
    }
}
