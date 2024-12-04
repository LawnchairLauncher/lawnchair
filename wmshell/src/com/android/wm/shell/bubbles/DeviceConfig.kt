/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.bubbles

import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.Insets
import android.graphics.Rect
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.max

/** Contains device configuration used for positioning bubbles on the screen. */
data class DeviceConfig(
        val isLargeScreen: Boolean,
        val isSmallTablet: Boolean,
        val isLandscape: Boolean,
        val isRtl: Boolean,
        val windowBounds: Rect,
        val insets: Insets
) {
    companion object {

        private const val LARGE_SCREEN_MIN_EDGE_DP = 600
        private const val SMALL_TABLET_MAX_EDGE_DP = 960

        @JvmStatic
        fun create(context: Context, windowManager: WindowManager): DeviceConfig {
            val windowMetrics = windowManager.currentWindowMetrics
            val metricInsets = windowMetrics.windowInsets
            val insets = metricInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()
                    or WindowInsets.Type.statusBars()
                    or WindowInsets.Type.displayCutout())
            val windowBounds = windowMetrics.bounds
            val config: Configuration = context.resources.configuration
            val isLargeScreen = config.smallestScreenWidthDp >= LARGE_SCREEN_MIN_EDGE_DP
            val largestEdgeDp = max(config.screenWidthDp, config.screenHeightDp)
            val isSmallTablet = isLargeScreen && largestEdgeDp < SMALL_TABLET_MAX_EDGE_DP
            val isLandscape = context.resources.configuration.orientation == ORIENTATION_LANDSCAPE
            val isRtl = context.resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL
            return DeviceConfig(
                    isLargeScreen = isLargeScreen,
                    isSmallTablet = isSmallTablet,
                    isLandscape = isLandscape,
                    isRtl = isRtl,
                    windowBounds = windowBounds,
                    insets = insets
            )
        }
    }
}
