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

package com.android.launcher3.util.rects

import android.graphics.Rect
import android.view.View
import com.android.launcher3.Utilities

/**
 * Linearly interpolate between two rectangles. The result is stored in the rect the function is
 * called on.
 *
 * @param start the starting rectangle
 * @param end the ending rectangle
 * @param t the interpolation factor, where 0 is the start and 1 is the end
 */
fun Rect.lerpRect(start: Rect, end: Rect, t: Float) {
    set(
        Utilities.mapRange(t, start.left.toFloat(), end.left.toFloat()).toInt(),
        Utilities.mapRange(t, start.top.toFloat(), end.top.toFloat()).toInt(),
        Utilities.mapRange(t, start.right.toFloat(), end.right.toFloat()).toInt(),
        Utilities.mapRange(t, start.bottom.toFloat(), end.bottom.toFloat()).toInt(),
    )
}

/** Copy the coordinates of the [view] relative to its parent into this rectangle. */
fun Rect.set(view: View) {
    set(0, 0, view.width, view.height)
    offset(view.x.toInt(), view.y.toInt())
}
