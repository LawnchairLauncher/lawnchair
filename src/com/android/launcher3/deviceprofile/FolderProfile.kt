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
import android.content.res.TypedArray
import android.graphics.Point
import android.util.DisplayMetrics
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.Utilities.calculateTextHeight
import com.android.launcher3.Utilities.getIconVisibleSizePx
import com.android.launcher3.Utilities.pxFromSp
import com.android.launcher3.responsive.CalculatedCellSpec
import com.android.launcher3.responsive.CalculatedResponsiveSpec
import com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE
import com.android.launcher3.testing.shared.ResourceUtils.pxFromDp
import com.android.launcher3.testing.shared.ResourceUtils.roundPxValueFromFloat
import com.android.launcher3.util.CellContentDimensions
import com.android.launcher3.util.IconSizeSteps
import kotlin.math.max

data class FolderProfile(
    val labelTextSizePx: Int,
    val childIconSizePx: Int,
    val childTextSizePx: Int,
    val maxChildTextLineCount: Int,
    val childDrawablePaddingPx: Int,
    val cellLayoutBorderSpacePx: Point,
    val contentPaddingLeftRight: Int,
    val contentPaddingTop: Int,
    val footerHeightPx: Int,
    val cellWidthPx: Int,
    val cellHeightPx: Int,
    val labelTextScale: Float,
    val numRows: Int,
    val numColumns: Int,
) {
    companion object Factory {

        const val MIN_FOLDER_TEXT_SIZE_SP: Float = 16f

        fun createFolderProfileResponsive(
            scale: Float,
            metrics: DisplayMetrics,
            inv: InvariantDeviceProfile,
            typeIndex: Int,
            res: Resources,
            responsiveFolderHeightSpec: CalculatedResponsiveSpec,
            responsiveWorkspaceCellSpec: CalculatedCellSpec,
            responsiveFolderWidthSpec: CalculatedResponsiveSpec,
            iconSizeSteps: IconSizeSteps,
        ): FolderProfile {
            val folderLabelTextScale = res.getFloat(R.dimen.folder_label_text_scale)
            val minLabelTextSize: Int = pxFromSp(MIN_FOLDER_TEXT_SIZE_SP, metrics, scale)
            var folderChildIconSizePx = responsiveWorkspaceCellSpec.iconSize
            var folderChildTextSizePx = responsiveWorkspaceCellSpec.iconTextSize
            val folderCellWidthPx = responsiveFolderWidthSpec.cellSizePx
            // Reduce icon width if it's wider than the expected folder cell width
            if (folderCellWidthPx < folderChildIconSizePx) {
                folderChildIconSizePx = iconSizeSteps.getIconSmallerThan(folderCellWidthPx)
            }
            // Recalculating padding and cell height
            var folderChildDrawablePaddingPx = responsiveWorkspaceCellSpec.iconDrawablePadding
            val cellContentDimensions =
                CellContentDimensions(
                    folderChildIconSizePx,
                    folderChildDrawablePaddingPx,
                    folderChildTextSizePx,
                    responsiveWorkspaceCellSpec.iconTextMaxLineCount,
                )
            val folderCellHeightPx = responsiveFolderHeightSpec.cellSizePx
            cellContentDimensions.resizeToFitCellHeight(folderCellHeightPx, iconSizeSteps)
            folderChildIconSizePx = cellContentDimensions.iconSizePx
            folderChildDrawablePaddingPx = cellContentDimensions.iconDrawablePaddingPx
            folderChildTextSizePx = cellContentDimensions.iconTextSizePx
            return FolderProfile(
                labelTextSizePx =
                    max(minLabelTextSize, (folderChildTextSizePx * folderLabelTextScale).toInt()),
                childIconSizePx = folderChildIconSizePx,
                childTextSizePx = folderChildTextSizePx,
                maxChildTextLineCount = cellContentDimensions.maxLineCount,
                childDrawablePaddingPx = folderChildDrawablePaddingPx,
                cellLayoutBorderSpacePx =
                    Point(responsiveFolderWidthSpec.gutterPx, responsiveFolderHeightSpec.gutterPx),
                contentPaddingLeftRight = responsiveFolderWidthSpec.startPaddingPx,
                contentPaddingTop = responsiveFolderHeightSpec.startPaddingPx,
                footerHeightPx = responsiveFolderHeightSpec.endPaddingPx,
                cellWidthPx = folderCellWidthPx,
                cellHeightPx = folderCellHeightPx,
                labelTextScale = folderLabelTextScale,
                numRows = inv.numFolderRows[typeIndex],
                numColumns = inv.numFolderColumns[typeIndex],
            )
        }

        private fun getNormalizedFolderChildDrawablePaddingPx(
            textHeight: Int,
            folderCellHeightPx: Int,
            folderChildIconSizePx: Int,
        ): Int {
            // TODO(b/235886078): workaround needed because of this bug
            // Icons are 10% larger on XML than their visual size,
            // so remove that extra space to get labels closer to the correct padding
            val drawablePadding: Int =
                ((folderCellHeightPx - folderChildIconSizePx - textHeight)) / 3
            val iconSizeDiff: Int =
                folderChildIconSizePx - getIconVisibleSizePx(folderChildIconSizePx)
            return max(0.0, (drawablePadding - iconSizeDiff / 2).toDouble()).toInt()
        }

        private fun getDimensionPixelSizeFromFolderStyle(
            folderStyle: TypedArray?,
            resId: Int,
            defValue: Int = 0,
        ): Int = folderStyle?.getDimensionPixelSize(resId, defValue) ?: defValue

        fun createFolderProfileScalable(
            scale: Float,
            context: Context,
            metrics: DisplayMetrics,
            inv: InvariantDeviceProfile,
            typeIndex: Int,
            cellSize: Point,
            res: Resources,
            iconSizeSteps: IconSizeSteps,
        ): FolderProfile {
            val minLabelTextSize: Int = pxFromSp(MIN_FOLDER_TEXT_SIZE_SP, metrics, scale)
            val folderLabelTextScale = res.getFloat(R.dimen.folder_label_text_scale)

            val folderStyle: TypedArray? =
                if (inv.folderStyle == INVALID_RESOURCE_HANDLE) null
                else context.obtainStyledAttributes(inv.folderStyle, R.styleable.FolderStyle)
            // These are re-set in #updateFolderCellSize if the grid is not scalable
            var folderCellHeightPx =
                getDimensionPixelSizeFromFolderStyle(
                    folderStyle,
                    R.styleable.FolderStyle_folderCellHeight,
                )
            var folderCellWidthPx =
                getDimensionPixelSizeFromFolderStyle(
                    folderStyle,
                    R.styleable.FolderStyle_folderCellWidth,
                )

            var folderContentPaddingTop =
                getDimensionPixelSizeFromFolderStyle(
                    folderStyle,
                    R.styleable.FolderStyle_folderTopPadding,
                    res.getDimensionPixelSize(R.dimen.folder_top_padding_default),
                )

            val gutter =
                getDimensionPixelSizeFromFolderStyle(
                    folderStyle,
                    R.styleable.FolderStyle_folderBorderSpace,
                )
            val folderCellLayoutBorderSpacePx = Point(gutter, gutter)
            val folderFooterHeightPx =
                getDimensionPixelSizeFromFolderStyle(
                    folderStyle,
                    R.styleable.FolderStyle_folderFooterHeight,
                    res.getDimensionPixelSize(R.dimen.folder_footer_height_default),
                )
            folderStyle?.recycle()

            val invIconSizeDp = inv.iconSize[typeIndex]
            val invIconTextSizeDp = inv.iconTextSize[typeIndex]
            var folderChildIconSizePx = max(1, pxFromDp(invIconSizeDp, metrics, scale))
            var folderChildTextSizePx = pxFromSp(invIconTextSizeDp, metrics, scale)
            val folderLabelTextSizePx =
                max(minLabelTextSize, (folderChildTextSizePx * folderLabelTextScale).toInt())
            var maxFolderChildTextLineCount = 1

            folderCellWidthPx =
                if (inv.folderStyle == INVALID_RESOURCE_HANDLE)
                    roundPxValueFromFloat(cellSize.x * scale)
                else roundPxValueFromFloat(folderCellWidthPx * scale)

            folderCellHeightPx =
                if (inv.folderStyle == INVALID_RESOURCE_HANDLE)
                    roundPxValueFromFloat(cellSize.y * scale)
                else roundPxValueFromFloat(folderCellHeightPx * scale)

            // Recalculating padding and cell height
            var folderChildDrawablePaddingPx =
                getNormalizedFolderChildDrawablePaddingPx(
                    calculateTextHeight(folderChildTextSizePx.toFloat()),
                    folderCellHeightPx,
                    folderChildIconSizePx,
                )
            val cellContentDimensions =
                CellContentDimensions(
                    folderChildIconSizePx,
                    folderChildDrawablePaddingPx,
                    folderChildTextSizePx,
                    maxFolderChildTextLineCount,
                )
            cellContentDimensions.resizeToFitCellHeight(folderCellHeightPx, iconSizeSteps)
            val cellLayoutBorderSpacePx =
                Point(
                    roundPxValueFromFloat(folderCellLayoutBorderSpacePx.x * scale),
                    roundPxValueFromFloat(folderCellLayoutBorderSpacePx.y * scale),
                )

            folderChildIconSizePx = cellContentDimensions.iconSizePx
            folderChildDrawablePaddingPx = cellContentDimensions.iconDrawablePaddingPx
            folderChildTextSizePx = cellContentDimensions.iconTextSizePx
            maxFolderChildTextLineCount = cellContentDimensions.maxLineCount

            return FolderProfile(
                labelTextSizePx = folderLabelTextSizePx,
                childIconSizePx = folderChildIconSizePx,
                childTextSizePx = folderChildTextSizePx,
                maxChildTextLineCount = maxFolderChildTextLineCount,
                childDrawablePaddingPx = folderChildDrawablePaddingPx,
                cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
                contentPaddingLeftRight = cellLayoutBorderSpacePx.x,
                contentPaddingTop = roundPxValueFromFloat(folderContentPaddingTop * scale),
                footerHeightPx = roundPxValueFromFloat(folderFooterHeightPx * scale),
                cellWidthPx = folderCellWidthPx,
                cellHeightPx = folderCellHeightPx,
                labelTextScale = folderLabelTextScale,
                numRows = inv.numFolderRows[typeIndex],
                numColumns = inv.numFolderColumns[typeIndex],
            )
        }

        fun createFolderProfileNonScalable(
            scale: Float,
            metrics: DisplayMetrics,
            inv: InvariantDeviceProfile,
            typeIndex: Int,
            res: Resources,
        ): FolderProfile {
            val folderLabelTextScale = res.getFloat(R.dimen.folder_label_text_scale)
            val minLabelTextSize: Int = pxFromSp(MIN_FOLDER_TEXT_SIZE_SP, metrics, scale)
            val invIconSizeDp = inv.iconSize[typeIndex]
            val invIconTextSizeDp = inv.iconTextSize[typeIndex]
            val folderChildIconSizePx = max(1, pxFromDp(invIconSizeDp, metrics, scale))
            val folderChildTextSizePx = pxFromSp(invIconTextSizeDp, metrics, scale)
            val textHeight: Int = calculateTextHeight(folderChildTextSizePx.toFloat())
            val cellPaddingX =
                (res.getDimensionPixelSize(R.dimen.folder_cell_x_padding) * scale).toInt()
            val cellPaddingY =
                (res.getDimensionPixelSize(R.dimen.folder_cell_y_padding) * scale).toInt()
            val folderCellHeightPx = folderChildIconSizePx + 2 * cellPaddingY + textHeight
            return FolderProfile(
                labelTextSizePx =
                    max(minLabelTextSize, (folderChildTextSizePx * folderLabelTextScale).toInt()),
                childIconSizePx = folderChildIconSizePx,
                childTextSizePx = folderChildTextSizePx,
                maxChildTextLineCount = 1,
                childDrawablePaddingPx =
                    getNormalizedFolderChildDrawablePaddingPx(
                        textHeight,
                        folderCellHeightPx,
                        folderChildIconSizePx,
                    ),
                cellLayoutBorderSpacePx = Point(0, 0),
                contentPaddingLeftRight =
                    res.getDimensionPixelSize(R.dimen.folder_content_padding_left_right),
                contentPaddingTop =
                    roundPxValueFromFloat(
                        res.getDimensionPixelSize(R.dimen.folder_top_padding_default) * scale
                    ),
                footerHeightPx =
                    roundPxValueFromFloat(
                        res.getDimensionPixelSize(R.dimen.folder_footer_height_default) * scale
                    ),
                cellWidthPx = folderChildIconSizePx + 2 * cellPaddingX,
                cellHeightPx = folderCellHeightPx,
                labelTextScale = folderLabelTextScale,
                numRows = inv.numFolderRows[typeIndex],
                numColumns = inv.numFolderColumns[typeIndex],
            )
        }

        fun createFolderProfile(
            context: Context,
            isResponsive: Boolean,
            isScalable: Boolean,
            scale: Float,
            metrics: DisplayMetrics,
            inv: InvariantDeviceProfile,
            typeIndex: Int,
            res: Resources,
            responsiveFolderHeightSpec: CalculatedResponsiveSpec?,
            responsiveWorkspaceCellSpec: CalculatedCellSpec?,
            responsiveFolderWidthSpec: CalculatedResponsiveSpec?,
            iconSizeSteps: IconSizeSteps,
            cellSize: Point,
        ): FolderProfile {
            return when {
                (isResponsive &&
                    responsiveFolderHeightSpec != null &&
                    responsiveWorkspaceCellSpec != null &&
                    responsiveFolderWidthSpec != null) -> {
                    createFolderProfileResponsive(
                        scale,
                        metrics,
                        inv,
                        typeIndex,
                        res,
                        responsiveFolderHeightSpec,
                        responsiveWorkspaceCellSpec,
                        responsiveFolderWidthSpec,
                        iconSizeSteps,
                    )
                }
                isScalable -> {
                    createFolderProfileScalable(
                        scale,
                        context,
                        metrics,
                        inv,
                        typeIndex,
                        cellSize,
                        res,
                        iconSizeSteps,
                    )
                }
                else -> {
                    createFolderProfileNonScalable(scale, metrics, inv, typeIndex, res)
                }
            }
        }
    }
}
