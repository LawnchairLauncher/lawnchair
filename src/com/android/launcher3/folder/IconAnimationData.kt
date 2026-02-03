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

package com.android.launcher3.folder

import android.view.View
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW
import com.android.launcher3.folder.FolderAnimationSpringBuilderManager.Companion.getBubbleTextView
import com.android.launcher3.folder.FolderAnimationSpringBuilderManager.Companion.getPreviewIconsOnPage

/** Animation Values for animating icons inside Folder Content */
data class IconAnimationData(
    /** icon in folder content to animate */
    val icon: View,
    /** delay for animating this icon */
    val iconDelay: Int,
    /** icons to display in Folder Preview */
    val itemsInPreview: List<View>,
    /** x distance to translate this icon */
    val xDistance: Float,
    /** y distance to translate this icon */
    val yDistance: Float,
    /** initial scale of this icon when folder is closed */
    val initialIconScale: Float,
    /** if folder is opening or closing */
    val isOpening: Boolean,
) {

    companion object Factory {
        private const val ICON_DELAY_INCREMENT = 5

        /**
         * Animates the icons within the folder. Icons start at the Preview Icon scale and then are
         * translated and scaled to the final folder content icon size. Their animations are also
         * delayed one by one from top left to bottom right.
         */
        fun Folder.getIconAnimationDataList(
            folderAnimationData: FolderAnimationData
        ): List<IconAnimationData> {
            val isOpening = folderAnimationData.isOpening
            // current page data
            val page = content.currentPage
            val folderItemsOnPage = getItemsOnPage(page)
            val numItemsOnPage = folderItemsOnPage.size
            // layout and preview data
            val itemsInPreview = getPreviewIconsOnPage(this, page)
            val shortcutsAndWidgets = content.getPageAt(0)?.shortcutsAndWidgets
            val mTmpParams = PreviewItemDrawingParams(0f, 0f, 0f)
            val layoutRule = folderIcon.layoutRule

            // We delay the animation of each icon from top left to bottom right
            var iconDelay = if (isOpening) 0 else (numItemsOnPage * ICON_DELAY_INCREMENT)
            val iconDataList = mutableListOf<IconAnimationData>()

            for (i in 0..<numItemsOnPage) {
                val currentIcon = folderItemsOnPage[i]
                // Calculate the final values in the LayoutParams.
                val iconLayoutParams = currentIcon.layoutParams as CellLayoutLayoutParams
                iconLayoutParams.isLockedToGrid = true
                shortcutsAndWidgets?.setupLp(currentIcon)

                // Match scale of icons in the preview of the items on the first page.
                val previewIconScale = layoutRule.scaleForItem(numItemsOnPage, page)
                val previewIconSize = layoutRule.iconSize * previewIconScale
                val baseIconSize = getBubbleTextView(currentIcon).iconSize.toFloat()
                val iconScale = previewIconSize / baseIconSize

                // Scale when folder closed
                val initialIconScale = iconScale / folderAnimationData.folderScale
                // Scale when folder open
                val finalIconScale = 1f
                // Scale to start with in Animation
                val startScale = if (isOpening) initialIconScale else finalIconScale
                currentIcon.scaleX = startScale
                currentIcon.scaleY = startScale

                val pageLayoutCount =
                    if (numItemsOnPage < MAX_NUM_ITEMS_IN_PREVIEW && page > 0) {
                        // If not on first page, we don't want preview items to position in a
                        // circle.
                        MAX_NUM_ITEMS_IN_PREVIEW
                    } else {
                        numItemsOnPage
                    }
                // Match positions of the icons in the folder with their positions in the preview
                layoutRule.computeSpringAnimationItemParams(i, pageLayoutCount, page, mTmpParams)

                // The PreviewLayoutRule assumes that the icon size takes up the entire width so we
                // offset by the actual size.
                val iconOffsetX = ((iconLayoutParams.width - baseIconSize) * iconScale).toInt() / 2

                // Calculate positions for each icon
                val iconPositionX =
                    ((mTmpParams.transX - iconOffsetX + folderAnimationData.scaledPreviewOffsetX) /
                            folderAnimationData.folderScale)
                        .toInt()
                val paddingTop = currentIcon.paddingTop * iconScale
                val iconPositionY =
                    ((mTmpParams.transY + folderAnimationData.folderRadiusDifference - paddingTop) /
                            folderAnimationData.folderScale)
                        .toInt()
                val xDistance = (iconPositionX - iconLayoutParams.x).toFloat()
                val yDistance = (iconPositionY - iconLayoutParams.y).toFloat()

                iconDataList.add(
                    IconAnimationData(
                        icon = currentIcon,
                        iconDelay = iconDelay,
                        itemsInPreview = itemsInPreview,
                        xDistance = xDistance,
                        yDistance = yDistance,
                        initialIconScale = initialIconScale,
                        isOpening = isOpening,
                    )
                )
                if (isOpening) {
                    iconDelay += ICON_DELAY_INCREMENT
                } else {
                    iconDelay -= ICON_DELAY_INCREMENT
                }
            }
            return iconDataList
        }
    }
}
