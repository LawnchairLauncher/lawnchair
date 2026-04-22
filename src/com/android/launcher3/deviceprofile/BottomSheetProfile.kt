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

import android.content.res.Resources
import android.graphics.Rect
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.Flags.enableScalingRevealHomeAnimation
import com.android.launcher3.R
import com.android.launcher3.Utilities

data class BottomSheetProfile(
    val bottomSheetTopPadding: Int,
    val bottomSheetOpenDuration: Int,
    val bottomSheetCloseDuration: Int,
    val bottomSheetWorkspaceScale: Float,
    val bottomSheetDepth: Float,
) {
    companion object Factory {

        // Minimum aspect ratio beyond which an extra top padding may be applied to a bottom sheet.
        private const val MIN_ASPECT_RATIO_FOR_EXTRA_TOP_PADDING: Float = 1.5f

        fun createBottomSheetProfile(
            deviceProperties: DeviceProperties,
            insets: Rect,
            res: Resources,
            edgeMarginPx: Int,
            shouldShowAllAppsOnSheet: Boolean,
            workspaceContentScale: Float,
        ): BottomSheetProfile {

            // In large screens, in portrait mode, a bottom sheet can appear too elongated, so, we
            // apply additional padding.
            val applyExtraTopPadding =
                deviceProperties.isTablet &&
                    !deviceProperties.isLandscape &&
                    (deviceProperties.aspectRatio > MIN_ASPECT_RATIO_FOR_EXTRA_TOP_PADDING)
            val derivedTopPadding: Int = deviceProperties.heightPx / 6
            val bottomSheetDepth =
                when {
                    !shouldShowAllAppsOnSheet -> 0f
                    // TODO(b/259893832): Revert to use maxWallpaperScale to calculate
                    // bottomSheetDepth when screen recorder bug is fixed.
                    deviceProperties.isMultiDisplay ->
                        if (enableScalingRevealHomeAnimation()) 0.3f else 1f
                    // The goal is to set wallpaper to zoom at workspaceContentScale when in AllApps
                    // When depth is 0, wallpaper zoom is set to maxWallpaperScale.
                    // When depth is 1, wallpaper zoom is set to 1.
                    // For depth to achieve zoom set to maxWallpaperScale * workspaceContentScale:
                    // TODO(b/420688601) We shouldn't use Interpolator to calculate static variables
                    else -> {
                        val maxWallpaperScale = res.getFloat(R.dimen.config_wallpaperMaxScale)
                        Utilities.mapToRange(
                            maxWallpaperScale * workspaceContentScale,
                            maxWallpaperScale,
                            1f,
                            0f,
                            1f,
                            LINEAR,
                        )
                    }
                }
            val bottomSheetTopPadding =
                insets.top + // statusbar height
                    (if (applyExtraTopPadding) derivedTopPadding else 0) +
                    // phones need edgeMarginPx additional padding
                    (if (deviceProperties.isTablet) 0 else edgeMarginPx).toInt()
            return BottomSheetProfile(
                bottomSheetTopPadding = bottomSheetTopPadding,
                bottomSheetOpenDuration = res.getInteger(R.integer.config_bottomSheetOpenDuration),
                bottomSheetCloseDuration =
                    res.getInteger(R.integer.config_bottomSheetCloseDuration),
                bottomSheetWorkspaceScale =
                    if (shouldShowAllAppsOnSheet) workspaceContentScale else 1f,
                bottomSheetDepth = bottomSheetDepth,
            )
        }
    }
}
