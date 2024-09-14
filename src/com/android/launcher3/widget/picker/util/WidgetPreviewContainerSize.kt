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

package com.android.launcher3.widget.picker.util

import com.android.launcher3.DeviceProfile
import com.android.launcher3.model.WidgetItem
import kotlin.math.abs

/** Size of a preview container in terms of the grid spans. */
data class WidgetPreviewContainerSize(@JvmField val spanX: Int, @JvmField val spanY: Int) {
    companion object {
        /**
         * Returns the size of the preview container in which the given widget's preview should be
         * displayed (by scaling it if necessary).
         */
        fun forItem(item: WidgetItem, dp: DeviceProfile): WidgetPreviewContainerSize {
            val sizes =
                if (dp.isTablet && !dp.isTwoPanels) {
                    TABLET_WIDGET_PREVIEW_SIZES
                } else {
                    HANDHELD_WIDGET_PREVIEW_SIZES
                }

            for ((index, containerSize) in sizes.withIndex()) {
                if (containerSize.spanX == item.spanX && containerSize.spanY == item.spanY) {
                    return containerSize // Exact match!
                }
                if (containerSize.spanX <= item.spanX && containerSize.spanY <= item.spanY) {
                    return findClosestFittingContainer(
                        containerSizes = sizes.toList(),
                        startIndex = index,
                        item = item
                    )
                }
            }
            // Use largest container if no match found
            return sizes.elementAt(0)
        }

        private fun findClosestFittingContainer(
            containerSizes: List<WidgetPreviewContainerSize>,
            startIndex: Int,
            item: WidgetItem
        ): WidgetPreviewContainerSize {
            // Checks if it's a smaller container, but close enough to keep the down-scale minimal.
            fun hasAcceptableSize(currentIndex: Int): Boolean {
                val container = containerSizes[currentIndex]
                val isSmallerThanItem =
                    container.spanX <= item.spanX && container.spanY <= item.spanY
                val isCloseToItemSize =
                    (item.spanY - container.spanY <= 1) && (item.spanX - container.spanX <= 1)

                return isSmallerThanItem && isCloseToItemSize
            }

            var currentIndex = startIndex
            var match = containerSizes[currentIndex]
            val itemCellSizeRatio = item.spanX.toFloat() / item.spanY
            var lastCellSizeRatioDiff = Float.MAX_VALUE

            // Look for a smaller container (up to an acceptable extent) with closest cell size
            // ratio.
            while (currentIndex <= containerSizes.lastIndex && hasAcceptableSize(currentIndex)) {
                val current = containerSizes[currentIndex]
                val currentCellSizeRatio = current.spanX.toFloat() / current.spanY
                val currentCellSizeRatioDiff = abs(itemCellSizeRatio - currentCellSizeRatio)

                if (currentCellSizeRatioDiff < lastCellSizeRatioDiff) {
                    lastCellSizeRatioDiff = currentCellSizeRatioDiff
                    match = containerSizes[currentIndex]
                }
                currentIndex++
            }
            return match
        }
    }
}
