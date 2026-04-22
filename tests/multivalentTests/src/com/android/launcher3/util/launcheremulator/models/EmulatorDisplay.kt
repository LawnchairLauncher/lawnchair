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
package com.android.launcher3.util.launcheremulator.models

import android.graphics.Insets
import android.graphics.Point
import android.graphics.Rect
import android.view.DisplayCutout
import com.android.launcher3.util.RotationUtils
import com.android.launcher3.util.window.CachedDisplayInfo

/** Represents the model for the display in DeviceList */
open class EmulatorDisplay(
    @JvmField val width: Int,
    @JvmField val height: Int,
    @JvmField val density: Int,
    val cutout: Rect,
) {

    fun propString(prefix: String) =
        "${prefix}width = $width,${prefix}height = $height,${prefix}density = $density,${prefix}cutout = Rect(${cutout.left}, ${cutout.top}, ${cutout.right}, ${cutout.bottom})"

    /**
     * Returns a CachedDisplayInfo rotated to the given rotation, representing the current emulation
     */
    fun toCachedDisplayInfo(rotation: Int): CachedDisplayInfo {
        val size = Point(width, height)
        RotationUtils.rotateSize(size, rotation)
        val cutoutRotated = Rect(cutout)
        RotationUtils.rotateRect(cutoutRotated, rotation)
        return CachedDisplayInfo(
            size,
            rotation,
            DisplayCutout(Insets.of(cutoutRotated), null, null, null, null),
        )
    }
}
