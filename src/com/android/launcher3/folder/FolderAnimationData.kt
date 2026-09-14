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

import android.graphics.Rect
import android.view.View
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.folder.FolderAnimationSpringBuilderManager.Companion.getBubbleTextView
import com.android.launcher3.folder.FolderAnimationSpringBuilderManager.Companion.getPreviewIconsOnPage
import com.android.launcher3.views.BaseDragLayer

/** Position and Scale values for animating opened/closed [Folder] */
data class FolderAnimationData(
    /** is folder opening or closing */
    val isOpening: Boolean,
    /** scale to set folder to */
    val startScale: Float,
    /** ratio of folder scale to drag layer scale */
    val folderScale: Float,
    /** x distance to translate folder */
    val xDistance: Float,
    /** y distance to translate folder */
    val yDistance: Float,
    /** change in content area height from scaling */
    val contentHeightDifference: Float,
    /** change in preview background radius from scaling */
    val folderRadiusDifference: Int,
    /** initial scale of folder icon before animation */
    val initialFolderScale: Float,
    /** initial width of the preview plate before animation */
    val initialFolderWidth: Float,
    /** initial height of that plate, equal to its width only on a one-cell folder */
    val initialFolderHeight: Float,
    /** initial x offset of folder content */
    val previewOffsetX: Int,
    /** scaled offset X for folder preview */
    val scaledPreviewOffsetX: Int,
    /** initial y padding of folder content */
    val contentOffsetY: Int,
    /** default duration */
    val defaultDuration: Int,
) {

    companion object Factory {
        fun Folder.getAnimationData(isOpening: Boolean): FolderAnimationData {
            /** Calculates all values required for Folder Animators. */
            // Position and Scale values
            val layoutParams = layoutParams as BaseDragLayer.LayoutParams
            val previewBackground = folderIcon.mBackground

            // Get items in Preview and their scaling
            val itemsInPreview: List<View> = getPreviewIconsOnPage(this, 0)
            // The size the preview actually draws its first app at. scaleForItem
            // knows only the huddle's two sizes, so on a plate that lays its apps
            // out on a grid the folder's contents began at the wrong scale and
            // snapped to the right one on arrival.
            val previewParams = PreviewItemDrawingParams(0f, 0f, 0f)
            folderIcon.layoutRule.computePreviewItemDrawingParams(
                0,
                itemsInPreview.size,
                previewParams,
            )
            val previewSize: Float = folderIcon.layoutRule.iconSize * previewParams.scale

            // Get scale and position of FolderIcon relative to DragLayer
            val folderIconWorkspacePosition = Rect()
            val scaleRelativeToDragLayer: Float =
                mActivityContext.dragLayer.getDescendantRectRelativeToSelf(
                    folderIcon,
                    folderIconWorkspacePosition,
                )
            val baseIconSize: Float = getBubbleTextView(itemsInPreview[0]).iconSize.toFloat()
            // The plate as it actually is, rather than twice its radius. One
            // number can only describe a square, and the square it described was
            // one cell pinned to the plate's top-left corner -- so a folder given
            // more cells opened out of its own corner instead of out of itself.
            val initialFolderWidth = previewBackground.plateWidth * scaleRelativeToDragLayer
            val initialFolderHeight = previewBackground.plateHeight * scaleRelativeToDragLayer

            // Scaled to match the preview's app size -- and then, if that leaves
            // it smaller than the plate, scaled up until it covers it.
            //
            // The second half is what a folder larger than one cell needs. Its
            // preview puts apps out at the plate's far edges, and an app can only
            // fly out of a place that exists inside the open folder: unless the
            // folder covers the plate, the far slots map past the end of it and
            // the app that lands there is cut along that edge. Covering it gives
            // every slot somewhere to come from, and each app is brought back
            // down to the size the preview drew it at by its own scale -- the
            // same mechanism the block of four already rides on.
            //
            // What has to cover the plate is the grid of cells, never the folder
            // around it. The plate is pinned to that grid's own top-left corner,
            // so every pixel of padding between the folder and the grid sits
            // outside the plate and buys it no coverage at all. Counting it is
            // what left the last slot with nowhere to come from: a plate two
            // cells tall is 474 against a content of 588, which asks for 0.806,
            // but the cells inside that content are only 522 -- the rest is the
            // content's own top padding -- and 474 over 522 is 0.908. At 0.806
            // the bottom slot mapped some forty pixels below the last row of
            // cells, and the app the preview put there came out cut off there.
            //
            // It showed on a folder of three and not on one of four because four
            // gives the last slot to the block of small icons, which sits in the
            // top half of that slot and stays inside. And it showed on a folder
            // of two cells and not on one of four, because on four the scale that
            // matches the preview's app size is the larger of the two and wins
            // the max below, which left the coverage term doing nothing.
            //
            // Asked of the folder's own numbers rather than measured off the
            // views: this runs before the folder is laid out, so the content
            // still reports zero and a measured value would quietly fall back to
            // covering nothing.
            val gridWidth =
                layoutParams.width - paddingLeft - paddingRight -
                    content.paddingLeft - content.paddingRight
            val gridHeight =
                contentAreaHeight - content.paddingTop - content.paddingBottom
            val coverScale =
                if (gridWidth <= 0 || gridHeight <= 0) {
                    0f
                } else {
                    maxOf(
                        previewBackground.plateWidth / gridWidth.toFloat(),
                        previewBackground.plateHeight / gridHeight.toFloat(),
                    )
                }
            val initialFolderScale =
                maxOf(previewSize / baseIconSize, coverScale) * scaleRelativeToDragLayer

            // Get offsets for Previews and Content
            val initialPreviewItemOffsetX =
                if (Utilities.isRtl(context.resources)) {
                    (layoutParams.width * initialFolderScale - initialFolderWidth).toInt()
                } else 0
            val contentOffsetX = (content.paddingLeft * initialFolderScale).toInt()
            val contentOffsetY = (content.paddingTop * initialFolderScale).toInt()

            // Get initial position of folder
            val initialX =
                ((folderIconWorkspacePosition.left +
                    paddingLeft +
                    Math.round(previewBackground.offsetX * scaleRelativeToDragLayer)) -
                    contentOffsetX -
                    initialPreviewItemOffsetX)
            val cardTop = cardTop
            val initialY =
                ((folderIconWorkspacePosition.top +
                    paddingTop +
                    Math.round(previewBackground.offsetY * scaleRelativeToDragLayer)) -
                    cardTop -
                    contentOffsetY)

            // Get scaled height of content and radius of background
            val scaledContentHeight = contentAreaHeight.toFloat() * initialFolderScale
            val contentHeightDifference = contentAreaHeight.toFloat() - scaledContentHeight
            val folderRadiusDifference = previewBackground.scaledRadius - previewBackground.radius
            /**
             * Background can have a scaled radius in drag and drop mode, so we need to add the
             * difference to keep the preview items centered.
             */
            return FolderAnimationData(
                isOpening = isOpening,
                startScale = if (isOpening) initialFolderScale else 1f,
                folderScale = initialFolderScale / scaleRelativeToDragLayer,
                xDistance = (initialX - layoutParams.x).toFloat(),
                yDistance = (initialY - layoutParams.y).toFloat(),
                contentHeightDifference = contentHeightDifference,
                folderRadiusDifference = folderRadiusDifference,
                initialFolderScale = initialFolderScale,
                initialFolderWidth = initialFolderWidth,
                initialFolderHeight = initialFolderHeight,
                previewOffsetX = initialPreviewItemOffsetX + contentOffsetX,
                scaledPreviewOffsetX =
                    (initialPreviewItemOffsetX / scaleRelativeToDragLayer).toInt() +
                        folderRadiusDifference,
                contentOffsetY = contentOffsetY,
                defaultDuration =
                    content.resources.getInteger(R.integer.config_materialFolderExpandDuration),
            )
        }
    }
}
