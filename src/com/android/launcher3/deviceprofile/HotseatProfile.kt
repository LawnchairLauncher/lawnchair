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

import android.content.Context
import android.content.res.Resources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.hotseat.HotseatMode
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT
import com.android.launcher3.R
import com.android.launcher3.responsive.CalculatedHotseatSpec
import com.patrykmichalik.opto.core.firstBlocking

// Remaining hotseat properties
//    int numShownHotseatIcons - updates multiple times
//    int hotseatCellHeightPx - updates multiple times
//    int mHotseatColumnSpan - updates multiple times
//    int mHotseatWidthPx - updates multiple times
//    int hotseatBarSizePx - updates multiple times
//    int hotseatBarBottomSpacePx - relies on var
//    int hotseatQsbSpace - relies on var
//    int hotseatQsbWidth - updates multiple times
//    int hotseatBorderSpace - updates multiple times

data class HotseatProfile(
    val areNavButtonsInline: Boolean,
    val navButtonsLayoutWidthPx: Int,
    val inlineNavButtonsEndSpacingPx: Int,
    val barEndOffset: Int,
    val springLoadedBarTopMarginPx: Int,
    val barEdgePaddingPx: Int,
    val barWorkspaceSpacePx: Int,
    val qsbHeight: Int,
    val qsbShadowHeight: Int,
    val qsbVisualHeight: Int,
    val minIconSpacePx: Int,
    val minQsbWidthPx: Int,
    val maxIconSpacePx: Int,
) {

    companion object Factory {
        fun createHotseatProfile(
            deviceProperties: DeviceProperties,
            res: Resources,
            inv: InvariantDeviceProfile,
            isTaskbarPresent: Boolean,
            shouldApplyWidePortraitDimens: Boolean,
            isVerticalBarLayout: Boolean,
            responsiveHotseatSpec: CalculatedHotseatSpec?,
            workspacePageIndicatorHeight: Int,
            hotseatMode: HotseatMode,
        ): HotseatProfile {
            val areNavButtonsInline = isTaskbarPresent && !deviceProperties.isGestureMode
            var inlineNavButtonsEndSpacingPx = 0
            var navButtonsLayoutWidthPx = 0
            var barEndOffset = 0
            // 3 nav buttons + Spacing between nav buttons
            if (areNavButtonsInline && !deviceProperties.isPhone) {
                inlineNavButtonsEndSpacingPx =
                    res.getDimensionPixelSize(inv.inlineNavButtonsEndSpacing)
                /* 3 nav buttons + Spacing between nav buttons */
                navButtonsLayoutWidthPx =
                    3 * res.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size) +
                        2 * res.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween)
                barEndOffset = navButtonsLayoutWidthPx + inlineNavButtonsEndSpacingPx
            }
            val springLoadedHotseatBarTopMarginPx =
                if (shouldApplyWidePortraitDimens)
                    res.getDimensionPixelSize(
                        R.dimen.spring_loaded_hotseat_top_margin_wide_portrait,
                    )
                else res.getDimensionPixelSize(R.dimen.spring_loaded_hotseat_top_margin)
            val hotseatBarEdgePaddingPx =
                when {
                    !isVerticalBarLayout -> 0
                    responsiveHotseatSpec != null -> responsiveHotseatSpec.edgePadding
                    else -> workspacePageIndicatorHeight
                }
            val isQsbEnable = hotseatMode.layoutResourceId != R.layout.empty_view

            val hotseatBarWorkspaceSpacePx =
                if (responsiveHotseatSpec != null) 0
                else res.getDimensionPixelSize(R.dimen.dynamic_grid_hotseat_side_padding)
            val hotseatQsbHeight =
                if (isQsbEnable) res.getDimensionPixelSize(R.dimen.qsb_widget_height) else 0
            val hotseatQsbShadowHeight = if (inv.inlineQsb[INDEX_DEFAULT] && !deviceProperties.isPhone) {
                res.getDimensionPixelSize(R.dimen.taskbar_size)
            } else {
                res.getDimensionPixelSize(R.dimen.qsb_shadow_height)
            }
            val hotseatQsbVisualHeight =
                if (isQsbEnable) hotseatQsbHeight - 2 * hotseatQsbShadowHeight else 0

            return HotseatProfile(
                areNavButtonsInline = areNavButtonsInline,
                navButtonsLayoutWidthPx = navButtonsLayoutWidthPx,
                inlineNavButtonsEndSpacingPx = inlineNavButtonsEndSpacingPx,
                barEndOffset = barEndOffset,
                springLoadedBarTopMarginPx = springLoadedHotseatBarTopMarginPx,
                barEdgePaddingPx = hotseatBarEdgePaddingPx,
                barWorkspaceSpacePx = hotseatBarWorkspaceSpacePx,
                qsbHeight = hotseatQsbHeight,
                qsbShadowHeight = hotseatQsbShadowHeight,
                qsbVisualHeight = hotseatQsbVisualHeight,
                minIconSpacePx = res.getDimensionPixelSize(R.dimen.min_hotseat_icon_space),
                minQsbWidthPx = res.getDimensionPixelSize(R.dimen.min_hotseat_qsb_width),
                maxIconSpacePx =
                    if (areNavButtonsInline)
                        res.getDimensionPixelSize(R.dimen.max_hotseat_icon_space)
                    else Int.MAX_VALUE,
            )
        }
    }
}
