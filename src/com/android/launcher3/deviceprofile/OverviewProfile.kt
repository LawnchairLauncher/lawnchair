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
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.util.OverviewReleaseFlags.enableOverviewIconMenu

data class OverviewProfile(
    val taskMarginPx: Int,
    val taskIconSizePx: Int,
    val taskIconDrawableSizePx: Int,
    val taskIconDrawableSizeGridPx: Int,
    val taskThumbnailTopMarginPx: Int,
    val actionsHeight: Int,
    val actionsTopMarginPx: Int,
    val pageSpacing: Int,
    val rowSpacing: Int,
    val gridSideMargin: Int,
) {

    companion object Factory {
        fun createOverviewProfile(res: Resources): OverviewProfile {
            val taskIconSizePx =
                if (enableOverviewIconMenu())
                    res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_drawable_touch_size)
                else res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_size)
            val taskMarginPx = res.getDimensionPixelSize(R.dimen.overview_task_margin)
            return OverviewProfile(
                taskMarginPx = taskMarginPx,
                taskIconSizePx = taskIconSizePx,
                taskIconDrawableSizePx =
                    res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_drawable_size),
                taskIconDrawableSizeGridPx =
                    res.getDimensionPixelSize(R.dimen.task_thumbnail_icon_drawable_size_grid),
                taskThumbnailTopMarginPx =
                    if (enableOverviewIconMenu()) 0 else taskIconSizePx + taskMarginPx,
                // Don't add margin with floating search bar to minimize risk of overlapping.
                actionsTopMarginPx =
                    if (Flags.floatingSearchBar()) 0
                    else res.getDimensionPixelSize(R.dimen.overview_actions_top_margin),
                pageSpacing = res.getDimensionPixelSize(R.dimen.overview_page_spacing),
                actionsHeight = res.getDimensionPixelSize(R.dimen.overview_actions_height),
                rowSpacing = res.getDimensionPixelSize(R.dimen.overview_grid_row_spacing),
                gridSideMargin = res.getDimensionPixelSize(R.dimen.overview_grid_side_margin),
            )
        }
    }
}
