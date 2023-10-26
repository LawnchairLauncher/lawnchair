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
package com.android.launcher3.util

import com.android.launcher3.Utilities
import kotlin.math.max

class CellContentDimensions(
    var iconSizePx: Int,
    var iconDrawablePaddingPx: Int,
    var iconTextSizePx: Int
) {
    /**
     * This method goes through some steps to reduce the padding between icon and label, icon size
     * and then label size, until it can fit in the [cellHeightPx].
     *
     * @return the height of the content after being sized down.
     */
    fun resizeToFitCellHeight(cellHeightPx: Int, iconSizeSteps: IconSizeSteps): Int {
        var cellContentHeight = getCellContentHeight()

        // Step 1. Decrease drawable padding
        if (cellContentHeight > cellHeightPx) {
            val diff = cellContentHeight - cellHeightPx
            iconDrawablePaddingPx = max(0, iconDrawablePaddingPx - diff)
            cellContentHeight = getCellContentHeight()
        }

        while (
            (iconTextSizePx > iconSizeSteps.minimumIconLabelSize ||
                iconSizePx > iconSizeSteps.minimumIconSize()) && cellContentHeight > cellHeightPx
        ) {
            // Step 2. Decrease icon size
            iconSizePx = iconSizeSteps.getNextLowerIconSize(iconSizePx)
            cellContentHeight = getCellContentHeight()

            // Step 3. Decrease label size
            if (cellContentHeight > cellHeightPx) {
                iconTextSizePx =
                    max(
                        iconSizeSteps.minimumIconLabelSize,
                        iconTextSizePx - IconSizeSteps.TEXT_STEP
                    )
                cellContentHeight = getCellContentHeight()
            }
        }

        return cellContentHeight
    }

    /** Calculate new cellContentHeight */
    fun getCellContentHeight(): Int {
        val iconTextHeight = Utilities.calculateTextHeight(iconTextSizePx.toFloat())
        return iconSizePx + iconDrawablePaddingPx + iconTextHeight
    }
}
