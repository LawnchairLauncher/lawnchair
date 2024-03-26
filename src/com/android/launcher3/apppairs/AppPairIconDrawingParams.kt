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

package com.android.launcher3.apppairs

import android.content.Context
import com.android.launcher3.BubbleTextView.DISPLAY_FOLDER
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.views.ActivityContext

class AppPairIconDrawingParams(val context: Context, container: Int) {
    companion object {
        // Design specs -- the below ratios are in relation to the size of a standard app icon.
        // Note: The standard app icon has two sizes. One is the full size of the drawable (returned
        // by dp.iconSizePx), and one is the visual size of the icon on-screen (11/12 of that).
        // Hence the calculations below.
        const val STANDARD_ICON_PADDING = 1 / 24f
        const val STANDARD_ICON_SHRINK = 1 - STANDARD_ICON_PADDING * 2
        // App pairs are slightly smaller than the *visual* size of a standard icon, so all ratios
        // are calculated with that in mind.
        const val OUTER_PADDING_SCALE = 1 / 30f * STANDARD_ICON_SHRINK
        const val INNER_PADDING_SCALE = 1 / 24f * STANDARD_ICON_SHRINK
        const val CENTER_CHANNEL_SCALE = 1 / 30f * STANDARD_ICON_SHRINK
        const val BIG_RADIUS_SCALE = 1 / 5f * STANDARD_ICON_SHRINK
        const val SMALL_RADIUS_SCALE = 1 / 15f * STANDARD_ICON_SHRINK
        const val MEMBER_ICON_SCALE = 11 / 30f * STANDARD_ICON_SHRINK
    }

    // The size at which this graphic will be drawn.
    val iconSize: Int
    // Standard app icons are padded by this amount on each side.
    val standardIconPadding: Float
    // App pair icons are slightly smaller than regular icons, so we pad the icon by this much on
    // each side.
    val outerPadding: Float
    // The colored background (two rectangles in a square area) is this big.
    val backgroundSize: Float
    // The size of the channel between the two halves of the app pair icon.
    val centerChannelSize: Float
    // The corner radius of the outside corners.
    val bigRadius: Float
    // The corner radius of the inside corners, touching the center channel.
    val smallRadius: Float
    // Inside of the icon, the two member apps are padded by this much.
    val innerPadding: Float
    // The two member apps have icons that are this big (in diameter).
    val memberIconSize: Float
    // The app pair icon appears differently in portrait and landscape.
    var isLeftRightSplit: Boolean = true
    // The background paint color (based on container).
    val bgColor: Int

    init {
        val activity: ActivityContext = ActivityContext.lookupContext(context)
        val dp = activity.deviceProfile
        iconSize = if (container == DISPLAY_FOLDER) dp.folderChildIconSizePx else dp.iconSizePx
        standardIconPadding = iconSize * STANDARD_ICON_PADDING
        outerPadding = iconSize * OUTER_PADDING_SCALE
        backgroundSize = iconSize * STANDARD_ICON_SHRINK - (outerPadding * 2)
        centerChannelSize = iconSize * CENTER_CHANNEL_SCALE
        bigRadius = iconSize * BIG_RADIUS_SCALE
        smallRadius = iconSize * SMALL_RADIUS_SCALE
        innerPadding = iconSize * INNER_PADDING_SCALE
        memberIconSize = iconSize * MEMBER_ICON_SCALE
        updateOrientation(dp)
        if (container == DISPLAY_FOLDER) {
            val ta =
                context.theme.obtainStyledAttributes(
                    intArrayOf(R.attr.materialColorSurfaceContainerLowest)
                )
            bgColor = ta.getColor(0, 0)
            ta.recycle()
        } else {
            val ta = context.theme.obtainStyledAttributes(R.styleable.FolderIconPreview)
            bgColor = ta.getColor(R.styleable.FolderIconPreview_folderPreviewColor, 0)
            ta.recycle()
        }
    }

    /** Checks the device orientation and updates isLeftRightSplit accordingly. */
    fun updateOrientation(dp: DeviceProfile) {
        isLeftRightSplit = dp.isLeftRightSplit
    }
}
