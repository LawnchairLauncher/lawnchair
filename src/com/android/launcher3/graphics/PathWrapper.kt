/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.graphics

import android.graphics.Path
import android.graphics.RectF

/** A wrapper class of [Path] that also tracks the [bounds] and corner radii. */
class PathWrapper {

    val path: Path = Path()

    private val bounds: RectF = RectF()
    private var cornerRadius = 0f

    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        bounds.set(left, top, right, bottom)
    }

    fun setCornerRadius(radius: Float) {
        cornerRadius = radius
    }

    fun estimateBoundsFromPath() {
        path.computeBounds(bounds, true /* exact */)
    }

    fun reset() {
        path.reset()
    }
}
