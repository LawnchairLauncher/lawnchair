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

package com.android.launcher3.deviceprofile

import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.WindowBounds
import kotlin.math.max
import kotlin.math.min

data class DeviceProperties(
    val windowX: Int,
    val windowY: Int,
    val rotationHint: Int,
    val widthPx: Int,
    val heightPx: Int,
    val availableWidthPx: Int,
    val availableHeightPx: Int,
    val aspectRatio: Float,
    val isTablet: Boolean,
    val isPhone: Boolean,
    val transposeLayoutWithOrientation: Boolean,
    val isMultiDisplay: Boolean,
    val isTwoPanels: Boolean,
    val isLandscape: Boolean,
    val isMultiWindowMode: Boolean,
    val isGestureMode: Boolean,
) {
    companion object Factory {
        // b/419264328 adding here all the improvements/cleanup for this class
        fun createDeviceProperties(
            info: DisplayController.Info,
            windowBounds: WindowBounds,
            transposeLayoutWithOrientation: Boolean,
            isMultiDisplay: Boolean,
            isMultiWindowMode: Boolean,
            isGestureMode: Boolean,
        ): DeviceProperties {
            val isTablet = info.isTablet(windowBounds)
            val windowX = windowBounds.bounds.left
            val windowY = windowBounds.bounds.top
            val rotationHint = windowBounds.rotationHint
            val widthPx = windowBounds.bounds.width()
            val heightPx = windowBounds.bounds.height()
            val availableWidthPx = windowBounds.availableSize.x
            val availableHeightPx = windowBounds.availableSize.y
            return DeviceProperties(
                windowX = windowX,
                windowY = windowY,
                rotationHint = rotationHint,
                widthPx = widthPx,
                heightPx = heightPx,
                availableWidthPx = availableWidthPx,
                availableHeightPx = availableHeightPx,
                aspectRatio = max(widthPx, heightPx).toFloat() / min(widthPx, heightPx).toFloat(),
                isTablet = isTablet,
                isPhone = !isTablet,
                transposeLayoutWithOrientation = transposeLayoutWithOrientation,
                isMultiDisplay = isMultiDisplay,
                isTwoPanels = isTablet && isMultiDisplay,
                isLandscape = windowBounds.isLandscape,
                isMultiWindowMode = isMultiWindowMode,
                isGestureMode = isGestureMode,
            )
        }
    }
}
