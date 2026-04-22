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
import com.android.launcher3.R

data class DropTargetProfile(
    val barSizePx: Int,
    val barTopMarginPx: Int,
    val barBottomMarginPx: Int,
    val dragPaddingPx: Int,
    val textSizePx: Int,
    val horizontalPaddingPx: Int,
    val verticalPaddingPx: Int,
    val gapPx: Int,
    val buttonWorkspaceEdgeGapPx: Int,
) {
    companion object Factory {
        fun createDropTargetProfile(
            res: Resources,
            shouldApplyWidePortraitDimens: Boolean,
        ): DropTargetProfile {
            return DropTargetProfile(
                barSizePx = res.getDimensionPixelSize(R.dimen.dynamic_grid_drop_target_size),
                barTopMarginPx =
                    if (shouldApplyWidePortraitDimens) 0
                    else res.getDimensionPixelSize(R.dimen.drop_target_top_margin),
                barBottomMarginPx =
                    if (shouldApplyWidePortraitDimens)
                        res.getDimensionPixelSize(R.dimen.drop_target_bottom_margin_wide_portrait)
                    else res.getDimensionPixelSize(R.dimen.drop_target_bottom_margin),
                dragPaddingPx = res.getDimensionPixelSize(R.dimen.drop_target_drag_padding),
                textSizePx = res.getDimensionPixelSize(R.dimen.drop_target_text_size),
                horizontalPaddingPx =
                    res.getDimensionPixelSize(
                        R.dimen.drop_target_button_drawable_horizontal_padding
                    ),
                verticalPaddingPx =
                    res.getDimensionPixelSize(R.dimen.drop_target_button_drawable_vertical_padding),
                gapPx = res.getDimensionPixelSize(R.dimen.drop_target_button_gap),
                buttonWorkspaceEdgeGapPx =
                    res.getDimensionPixelSize(R.dimen.drop_target_button_workspace_edge_gap),
            )
        }
    }
}
