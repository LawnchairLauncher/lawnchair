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
import android.graphics.Rect
import android.util.DisplayMetrics
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.getIconSizeWithOverlap
import com.android.launcher3.Utilities.getNormalizedIconDrawablePadding
import com.android.launcher3.Utilities.pxFromSp
import com.android.launcher3.deviceprofile.WorkspaceProfile.Factory.calculateCellSize
import com.android.launcher3.deviceprofile.WorkspaceProfile.Factory.calculateHotseatBarSizePx
import com.android.launcher3.deviceprofile.WorkspaceProfile.Factory.insetPadding
import com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE
import com.android.launcher3.testing.shared.ResourceUtils.pxFromDp
import kotlin.math.max
import kotlin.math.min

@Deprecated(
    "Non responsive grids are deprecated, please refer to responsive grid for a " +
        "replacement. Nexus Launcher doesn't use Non Scalable grids anymore."
)
/**
 * This factory creates WorkspaceProfile when the grid is Scalable and Non-Scalable. Calculating
 * this two variants is completely different from calculating the responsive grid mainly because
 * both of them require the WorkspaceProfile to be calculated once and then recalculated again if
 * the scaleY is less than 1.
 *
 * For reference please look at {@code WorkspaceProfile#Factory}.
 */
object WorkspaceProfileNonResponsiveFactory {

    fun createWorkspacePadding(
        isVerticalLayout: Boolean,
        isSeascape: Boolean,
        isFixedLandscape: Boolean,
        isScalableGrid: Boolean,
        iconSize: Int,
        desiredWorkspaceHorizontalMarginPx: Int,
        insets: Rect,
        edgeMarginPx: Int,
        workspacePageIndicatorHeight: Int,
        workspacePageIndicatorOverlapWorkspace: Int,
        workspaceTopPadding: Int,
        workspaceBottomPadding: Int,
        hotseatProfile: HotseatProfile,
        hotseatBarBottomSpacePx: Int,
        hotseatQsbSpace: Int,
    ): Rect {
        // TODO : This is to update updateHotseatSizes, we need a better way to do
        // this
        val hotseatBarSizePx =
            calculateHotseatBarSizePx(
                iconSizePx = iconSize,
                isVerticalLayout = isVerticalLayout,
                hotseatProfile = hotseatProfile,
                hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                hotseatQsbSpace = hotseatQsbSpace,
                isQsbInline = isFixedLandscape,
            )
        if (isVerticalLayout) {
            return Rect(
                /* left */ if (isSeascape) hotseatBarSizePx else hotseatProfile.barEdgePaddingPx,
                /* top */ 0,
                /* right */ if (isSeascape) hotseatProfile.barEdgePaddingPx else hotseatBarSizePx,
                /* bottom */ edgeMarginPx,
            )
        } else {
            // Pad the bottom of the workspace with hotseat bar
            // and leave a bit of space in case a widget go all the way down
            val padding =
                Rect(
                    /*Left */
                    desiredWorkspaceHorizontalMarginPx,
                    /*Top */
                    workspaceTopPadding + (if (isScalableGrid) 0 else edgeMarginPx),
                    /*Right */
                    desiredWorkspaceHorizontalMarginPx,
                    /*Bottom */
                    (hotseatBarSizePx - insets.bottom) +
                        workspaceBottomPadding +
                        (workspacePageIndicatorHeight - workspacePageIndicatorOverlapWorkspace),
                )

            // In fixed Landscape we don't need padding on the side next to the cutout because
            // the cutout is already adding padding to all of Launcher, we only need on the other
            // side
            if (isFixedLandscape) {
                return Rect(
                    if (isSeascape) padding.left else 0,
                    padding.top,
                    if (isSeascape) 0 else padding.right,
                    padding.bottom,
                )
            }
            return padding
        }
    }

    fun hideWorkspaceLabelsIfNotEnoughSpace(
        isVerticalLayout: Boolean,
        workspaceProfile: WorkspaceProfile,
        inv: InvariantDeviceProfile,
    ): WorkspaceProfile {
        if (!isVerticalLayout) return workspaceProfile
        val iconTextHeight =
            Utilities.calculateTextHeight(workspaceProfile.iconTextSizePx.toFloat()).toFloat()
        val workspaceCellPaddingY: Float =
            (workspaceProfile.cellSize.y -
                workspaceProfile.iconSizePx -
                workspaceProfile.iconDrawablePaddingPx -
                iconTextHeight)

        if (workspaceCellPaddingY >= iconTextHeight) return workspaceProfile

        val cellHeightPx = getIconSizeWithOverlap(workspaceProfile.iconSizePx)

        // We want enough space so that the text is closer to its corresponding icon.
        return workspaceProfile.copy(
            iconTextSizePx = 0,
            iconDrawablePaddingPx = 0,
            cellHeightPx = cellHeightPx,
            maxIconTextLineCount = 0,
            isLabelHidden = true,
            cellLayoutHeightSpecification =
                ((cellHeightPx * inv.numRows) +
                    (workspaceProfile.cellLayoutBorderSpacePx.y * (inv.numRows - 1)) +
                    workspaceProfile.cellLayoutPaddingPx.top +
                    workspaceProfile.cellLayoutPaddingPx.bottom),
        )
    }

    fun createWorkspaceProfileNonScalable(
        res: Resources,
        deviceProperties: DeviceProperties,
        inv: InvariantDeviceProfile,
        cellScaleToFit: Float,
        iconScale: Float,
        iconSizePx: Int,
        iconTextSizePx: Int,
        isVerticalLayout: Boolean,
        iconDrawablePaddingOriginalPx: Int,
        isFirstPass: Boolean,
        insets: Rect,
        isSeascape: Boolean,
        hotseatProfile: HotseatProfile,
        hotseatBarBottomSpacePx: Int,
        hotseatQsbSpace: Int,
        panelCount: Int,
        scale: Float,
    ): WorkspaceProfile {
        val cellLayoutBorderSpacePx = Point(0, 0)
        var cellSize =
            calculateCellSize(
                cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
                panelCount = panelCount,
                deviceProperties = deviceProperties,
                numColumns = inv.numColumns,
                numRows = inv.numRows,
                cellLayoutPadding = Rect(0, 0, 0, 0),
                totalWorkspacePadding = Point(0, 0),
            )
        val desiredWorkspaceHorizontalMarginOriginalPx =
            when {
                isVerticalLayout -> 0
                else -> res.getDimensionPixelSize(R.dimen.dynamic_grid_left_right_margin)
            }
        var iconDrawablePaddingPx =
            (getNormalizedIconDrawablePadding(iconSizePx, iconDrawablePaddingOriginalPx) *
                    iconScale)
                .toInt()
        val cellWidthPx = iconSizePx + iconDrawablePaddingPx
        var cellHeightPx =
            (getIconSizeWithOverlap(iconSizePx) +
                iconDrawablePaddingPx +
                Utilities.calculateTextHeight(iconTextSizePx.toFloat()))
        val cellPaddingY: Int = (cellSize.y - cellHeightPx) / 2
        if (
            iconDrawablePaddingPx > cellPaddingY &&
                !isVerticalLayout &&
                !deviceProperties.isExternalDisplay
        ) {
            // Ensures that the label is closer to its corresponding icon. This is not an issue
            // with vertical bar layout or external display mode since the issue is handled
            // separately with their calls to {@link #adjustToHideWorkspaceLabels}.
            cellHeightPx -= (iconDrawablePaddingPx - cellPaddingY)
            iconDrawablePaddingPx = cellPaddingY
        }
        val edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin)
        val desiredWorkspaceHorizontalMarginPx = desiredWorkspaceHorizontalMarginOriginalPx
        val workspacePageIndicatorHeight =
            res.getDimensionPixelSize(R.dimen.workspace_page_indicator_height)
        val workspacePageIndicatorOverlapWorkspace =
            res.getDimensionPixelSize(R.dimen.workspace_page_indicator_overlap_workspace)
        val noInsetWorkspacePadding =
            createWorkspacePadding(
                isVerticalLayout = isVerticalLayout,
                isSeascape = isSeascape,
                isFixedLandscape = inv.isFixedLandscape,
                isScalableGrid = false,
                hotseatProfile = hotseatProfile,
                desiredWorkspaceHorizontalMarginPx = desiredWorkspaceHorizontalMarginPx,
                insets = insets,
                edgeMarginPx = edgeMarginPx,
                workspacePageIndicatorHeight = workspacePageIndicatorHeight,
                workspacePageIndicatorOverlapWorkspace = workspacePageIndicatorOverlapWorkspace,
                workspaceTopPadding = 0,
                workspaceBottomPadding = 0,
                iconSize = iconSizePx,
                hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                hotseatQsbSpace = hotseatQsbSpace,
            )
        val cellLayoutPadding =
            when {
                isFirstPass -> 0
                deviceProperties.isTwoPanels -> 0
                else -> res.getDimensionPixelSize(R.dimen.cell_layout_padding)
            }
        val (workspacePadding, cellLayoutPaddingPx) =
            insetPadding(
                noInsetWorkspacePadding,
                Rect(cellLayoutPadding, cellLayoutPadding, cellLayoutPadding, cellLayoutPadding),
            )

        val numColumns: Int = panelCount * inv.numColumns

        cellSize =
            calculateCellSize(
                cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
                panelCount = panelCount,
                deviceProperties = deviceProperties,
                numColumns = inv.numColumns,
                numRows = inv.numRows,
                cellLayoutPadding = cellLayoutPaddingPx,
                totalWorkspacePadding =
                    Point(
                        workspacePadding.left + workspacePadding.right,
                        workspacePadding.bottom + workspacePadding.top,
                    ),
            )
        return WorkspaceProfile(
            // Workspace icons
            iconScale = iconScale,
            iconSizePx = iconSizePx,
            iconTextSizePx = iconTextSizePx,
            iconDrawablePaddingPx = iconDrawablePaddingPx,
            cellScaleToFit = cellScaleToFit,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
            desiredWorkspaceHorizontalMarginPx = desiredWorkspaceHorizontalMarginPx,
            cellYPaddingPx = -1,
            maxIconTextLineCount = 1,
            iconCenterVertically = false,
            gridVisualizationPaddingX =
                res.getDimensionPixelSize(R.dimen.grid_visualization_horizontal_cell_spacing),
            gridVisualizationPaddingY =
                res.getDimensionPixelSize(R.dimen.grid_visualization_vertical_cell_spacing),
            workspacePageIndicatorHeight = workspacePageIndicatorHeight,
            workspacePageIndicatorOverlapWorkspace = workspacePageIndicatorOverlapWorkspace,
            iconDrawablePaddingOriginalPx = iconDrawablePaddingOriginalPx,
            desiredWorkspaceHorizontalMarginOriginalPx = desiredWorkspaceHorizontalMarginOriginalPx,
            workspaceContentScale = res.getFloat(R.dimen.workspace_content_scale),
            workspaceSpringLoadedMinNextPageVisiblePx =
                res.getDimensionPixelSize(
                    R.dimen.dynamic_grid_spring_loaded_min_next_space_visible
                ),
            workspaceCellPaddingXPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_padding_x),
            workspaceTopPadding = 0,
            workspaceBottomPadding = 0,
            maxEmptySpace = 0,
            workspacePadding = workspacePadding,
            cellLayoutPaddingPx = cellLayoutPaddingPx,
            edgeMarginPx = edgeMarginPx,
            cellLayoutHeightSpecification =
                ((cellHeightPx * inv.numRows) +
                    (cellLayoutBorderSpacePx.y * (inv.numRows - 1)) +
                    cellLayoutPaddingPx.top +
                    cellLayoutPaddingPx.bottom),
            cellLayoutWidthSpecification =
                ((cellWidthPx * numColumns) +
                    (cellLayoutBorderSpacePx.x * (numColumns - 1)) +
                    cellLayoutPaddingPx.left +
                    cellLayoutPaddingPx.right),
            panelCount = panelCount,
            cellSize = cellSize,
            scale = scale,
        )
    }

    fun createWorkspaceProfileScalable(
        res: Resources,
        scale: Float,
        inv: InvariantDeviceProfile,
        typeIndex: Int,
        metrics: DisplayMetrics,
        iconSizePxParam: Int,
        iconTextSizePxParam: Int,
        iconScale: Float,
        iconDrawablePaddingOriginalPx: Int,
        cellScaleToFit: Float,
        panelCount: Int,
        isVerticalLayout: Boolean,
        isFirstPass: Boolean,
        deviceProperties: DeviceProperties,
        isSeascape: Boolean,
        insets: Rect,
        hotseatProfile: HotseatProfile,
        hotseatBarBottomSpacePx: Int,
        hotseatQsbSpace: Int,
    ): WorkspaceProfile {
        val cellLayoutBorderSpacePx =
            Point(
                pxFromDp(inv.borderSpaces[typeIndex].x, metrics, scale),
                pxFromDp(inv.borderSpaces[typeIndex].y, metrics, scale),
            )

        val desiredWorkspaceHorizontalMarginOriginalPx =
            when {
                isVerticalLayout -> 0
                else -> pxFromDp(inv.horizontalMargin[typeIndex], metrics)
            }
        var iconTextSizePx = iconTextSizePxParam
        var iconSizePx = iconSizePxParam
        var iconDrawablePaddingPx =
            (getNormalizedIconDrawablePadding(iconSizePx, iconDrawablePaddingOriginalPx) *
                    iconScale)
                .toInt()
        var cellWidthPx = pxFromDp(inv.minCellSize.get(typeIndex).x, metrics, scale)
        var cellHeightPx = pxFromDp(inv.minCellSize.get(typeIndex).y, metrics, scale)

        if (cellWidthPx < iconSizePx) {
            // If cellWidth no longer fit iconSize, reduce borderSpace to make cellWidth bigger.
            val numColumns: Int = panelCount * inv.numColumns
            val numBorders = numColumns - 1
            val extraWidthRequired: Int = (iconSizePx - cellWidthPx) * numColumns
            if (cellLayoutBorderSpacePx.x * numBorders >= extraWidthRequired) {
                cellWidthPx = iconSizePx
                cellLayoutBorderSpacePx.x -= extraWidthRequired / numBorders
            } else {
                // If it still doesn't fit, set borderSpace to 0 and distribute the space for
                // cellWidth, and reduce iconSize.
                cellWidthPx =
                    (cellWidthPx * numColumns + cellLayoutBorderSpacePx.x * numBorders) / numColumns
                iconSizePx = min(iconSizePx.toDouble(), cellWidthPx.toDouble()).toInt()
                cellLayoutBorderSpacePx.x = 0
            }
        }

        var cellTextAndPaddingHeight: Int =
            iconDrawablePaddingPx + Utilities.calculateTextHeight(iconTextSizePx.toFloat())
        var cellContentHeight: Int = iconSizePx + cellTextAndPaddingHeight
        if (cellHeightPx < cellContentHeight) {
            // If cellHeight no longer fit iconSize, reduce borderSpace to make cellHeight
            // bigger.
            val numBorders: Int = inv.numRows - 1
            val extraHeightRequired: Int = (cellContentHeight - cellHeightPx) * inv.numRows
            if (cellLayoutBorderSpacePx.y * numBorders >= extraHeightRequired) {
                cellHeightPx = cellContentHeight
                cellLayoutBorderSpacePx.y -= extraHeightRequired / numBorders
            } else {
                // If it still doesn't fit, set borderSpace to 0 to recover space.
                cellHeightPx =
                    (cellHeightPx * inv.numRows + cellLayoutBorderSpacePx.y * numBorders) /
                        inv.numRows
                cellLayoutBorderSpacePx.y = 0
                // Reduce iconDrawablePaddingPx to make cellContentHeight smaller.
                val cellContentWithoutPadding: Int = cellContentHeight - iconDrawablePaddingPx
                if (cellContentWithoutPadding <= cellHeightPx) {
                    iconDrawablePaddingPx = cellContentHeight - cellHeightPx
                } else {
                    // If it still doesn't fit, set iconDrawablePaddingPx to 0 to recover space,
                    // then proportional reduce iconSizePx and iconTextSizePx to fit.
                    iconDrawablePaddingPx = 0
                    val ratio: Float = cellHeightPx / cellContentWithoutPadding.toFloat()
                    iconSizePx = (iconSizePx * ratio).toInt()
                    iconTextSizePx = (iconTextSizePx * ratio).toInt()
                }
                cellTextAndPaddingHeight =
                    iconDrawablePaddingPx + Utilities.calculateTextHeight(iconTextSizePx.toFloat())
            }
            cellContentHeight = iconSizePx + cellTextAndPaddingHeight
        }

        val edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin)
        val desiredWorkspaceHorizontalMarginPx =
            (desiredWorkspaceHorizontalMarginOriginalPx * scale).toInt()
        val workspacePageIndicatorHeight =
            res.getDimensionPixelSize(R.dimen.workspace_page_indicator_height)
        val workspacePageIndicatorOverlapWorkspace =
            res.getDimensionPixelSize(R.dimen.workspace_page_indicator_overlap_workspace)
        val noInsetWorkspacePadding =
            createWorkspacePadding(
                isVerticalLayout = isVerticalLayout,
                isSeascape = isSeascape,
                isFixedLandscape = inv.isFixedLandscape,
                isScalableGrid = true,
                hotseatProfile = hotseatProfile,
                desiredWorkspaceHorizontalMarginPx = desiredWorkspaceHorizontalMarginPx,
                insets = insets,
                edgeMarginPx = edgeMarginPx,
                workspacePageIndicatorHeight = workspacePageIndicatorHeight,
                workspacePageIndicatorOverlapWorkspace = workspacePageIndicatorOverlapWorkspace,
                workspaceTopPadding = 0,
                workspaceBottomPadding = 0,
                iconSize = iconSizePx,
                hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                hotseatQsbSpace = hotseatQsbSpace,
            )
        val cellLayoutPadding =
            when {
                isFirstPass -> 0
                deviceProperties.isTwoPanels -> cellLayoutBorderSpacePx.x / 2
                else -> res.getDimensionPixelSize(R.dimen.cell_layout_padding)
            }
        val (workspacePadding, cellLayoutPaddingPx) =
            insetPadding(
                noInsetWorkspacePadding,
                Rect(cellLayoutPadding, cellLayoutPadding, cellLayoutPadding, cellLayoutPadding),
            )

        val numColumns: Int = panelCount * inv.numColumns
        val cellSize =
            calculateCellSize(
                cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
                panelCount = panelCount,
                deviceProperties = deviceProperties,
                numColumns = inv.numColumns,
                numRows = inv.numRows,
                cellLayoutPadding = cellLayoutPaddingPx,
                totalWorkspacePadding =
                    Point(
                        workspacePadding.left + workspacePadding.right,
                        workspacePadding.bottom + workspacePadding.top,
                    ),
            )
        return WorkspaceProfile(
            // Workspace icons
            iconScale = iconScale,
            iconSizePx = iconSizePx,
            iconTextSizePx = iconTextSizePx,
            iconDrawablePaddingPx = iconDrawablePaddingPx,
            cellScaleToFit = cellScaleToFit,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
            desiredWorkspaceHorizontalMarginPx = desiredWorkspaceHorizontalMarginPx,
            cellYPaddingPx = max(0, (cellHeightPx - cellContentHeight)) / 2,
            maxIconTextLineCount = 1,
            iconCenterVertically = isVerticalLayout,
            gridVisualizationPaddingX =
                res.getDimensionPixelSize(R.dimen.grid_visualization_horizontal_cell_spacing),
            gridVisualizationPaddingY =
                res.getDimensionPixelSize(R.dimen.grid_visualization_vertical_cell_spacing),
            workspacePageIndicatorHeight = workspacePageIndicatorHeight,
            workspacePageIndicatorOverlapWorkspace = workspacePageIndicatorOverlapWorkspace,
            iconDrawablePaddingOriginalPx = iconDrawablePaddingOriginalPx,
            desiredWorkspaceHorizontalMarginOriginalPx = desiredWorkspaceHorizontalMarginOriginalPx,
            workspaceContentScale = res.getFloat(R.dimen.workspace_content_scale),
            workspaceSpringLoadedMinNextPageVisiblePx =
                res.getDimensionPixelSize(
                    R.dimen.dynamic_grid_spring_loaded_min_next_space_visible
                ),
            workspaceCellPaddingXPx =
                res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_padding_x),
            workspaceTopPadding = 0,
            workspaceBottomPadding = 0,
            maxEmptySpace = 0,
            workspacePadding = workspacePadding,
            cellLayoutPaddingPx = cellLayoutPaddingPx,
            edgeMarginPx = edgeMarginPx,
            cellLayoutHeightSpecification =
                ((cellHeightPx * inv.numRows) +
                    (cellLayoutBorderSpacePx.y * (inv.numRows - 1)) +
                    cellLayoutPaddingPx.top +
                    cellLayoutPaddingPx.bottom),
            cellLayoutWidthSpecification =
                ((cellWidthPx * numColumns) +
                    (cellLayoutBorderSpacePx.x * (numColumns - 1)) +
                    cellLayoutPaddingPx.left +
                    cellLayoutPaddingPx.right),
            panelCount = panelCount,
            cellSize = cellSize,
            scale = scale,
        )
    }

    private fun internalCreateWorkspaceProfileNonResponsive(
        context: Context,
        res: Resources,
        deviceProperties: DeviceProperties,
        scale: Float,
        inv: InvariantDeviceProfile,
        isVerticalLayout: Boolean,
        isScalableGrid: Boolean,
        typeIndex: Int,
        metrics: DisplayMetrics,
        panelCount: Int,
        iconSizePx: Int,
        isFirstPass: Boolean,
        insets: Rect,
        isSeascape: Boolean,
        hotseatProfile: HotseatProfile,
        hotseatBarBottomSpacePx: Int,
        hotseatQsbSpace: Int,
    ): WorkspaceProfile {
        // Icon scale should never exceed 1, otherwise pixellation may occur.
        val iconScale = min(1f, scale)
        val cellScaleToFit = scale
        val iconTextSizePx = pxFromSp(inv.iconTextSize[typeIndex], metrics)

        val cellStyle: TypedArray =
            when {
                inv.cellStyle != INVALID_RESOURCE_HANDLE ->
                    context.obtainStyledAttributes(inv.cellStyle, R.styleable.CellStyle)

                else ->
                    context.obtainStyledAttributes(R.style.CellStyleDefault, R.styleable.CellStyle)
            }
        val iconDrawablePaddingOriginalPx =
            cellStyle.getDimensionPixelSize(R.styleable.CellStyle_iconDrawablePadding, 0)
        cellStyle.recycle()
        // Workspace
        return when {
            isScalableGrid ->
                createWorkspaceProfileScalable(
                        res = res,
                        scale = scale,
                        inv = inv,
                        typeIndex = typeIndex,
                        metrics = metrics,
                        iconSizePxParam = iconSizePx,
                        iconTextSizePxParam = iconTextSizePx,
                        iconScale = iconScale,
                        iconDrawablePaddingOriginalPx = iconDrawablePaddingOriginalPx,
                        cellScaleToFit = cellScaleToFit,
                        panelCount = panelCount,
                        isVerticalLayout = isVerticalLayout,
                        isFirstPass = isFirstPass,
                        insets = insets,
                        isSeascape = isSeascape,
                        hotseatProfile = hotseatProfile,
                        deviceProperties = deviceProperties,
                        hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                        hotseatQsbSpace = hotseatQsbSpace,
                    )
                    .let { hideWorkspaceLabelsIfNotEnoughSpace(isVerticalLayout, it, inv) }

            else ->
                createWorkspaceProfileNonScalable(
                        res = res,
                        deviceProperties = deviceProperties,
                        cellScaleToFit = cellScaleToFit,
                        iconScale = iconScale,
                        iconSizePx = iconSizePx,
                        iconTextSizePx = iconTextSizePx,
                        isVerticalLayout = isVerticalLayout,
                        iconDrawablePaddingOriginalPx = iconDrawablePaddingOriginalPx,
                        inv = inv,
                        isFirstPass = isFirstPass,
                        insets = insets,
                        isSeascape = isSeascape,
                        hotseatProfile = hotseatProfile,
                        hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                        hotseatQsbSpace = hotseatQsbSpace,
                        panelCount = panelCount,
                        scale = scale,
                    )
                    .let { hideWorkspaceLabelsIfNotEnoughSpace(isVerticalLayout, it, inv) }
        }
    }

    fun createWorkspaceProfileNonResponsive(
        context: Context,
        res: Resources,
        deviceProperties: DeviceProperties,
        scale: Float,
        inv: InvariantDeviceProfile,
        isVerticalLayout: Boolean,
        isScalableGrid: Boolean,
        typeIndex: Int,
        metrics: DisplayMetrics,
        panelCount: Int,
        iconSizePx: Int,
        isFirstPass: Boolean,
        insets: Rect,
        isSeascape: Boolean,
        hotseatProfile: HotseatProfile,
        hotseatBarBottomSpacePx: Int,
        hotseatQsbSpace: Int,
        hotseatBarSizePx: Int,
    ): WorkspaceProfile {
        var workspaceProfile =
            internalCreateWorkspaceProfileNonResponsive(
                context = context,
                res = res,
                deviceProperties = deviceProperties,
                scale = scale,
                inv = inv,
                isVerticalLayout = isVerticalLayout,
                isScalableGrid = isScalableGrid,
                typeIndex = typeIndex,
                metrics = metrics,
                panelCount = panelCount,
                iconSizePx = iconSizePx,
                isFirstPass = isFirstPass,
                insets = insets,
                isSeascape = isSeascape,
                hotseatProfile = hotseatProfile,
                hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                hotseatQsbSpace = hotseatQsbSpace,
            )

        // Check to see if the icons fit within the available height.
        val usedHeight: Float = workspaceProfile.cellLayoutHeightSpecification.toFloat()
        val maxHeight: Int =
            (deviceProperties.availableHeightPx - workspaceProfile.getTotalWorkspacePadding().y)
        var extraHeight = max(0f, maxHeight - usedHeight)
        val scaleY = maxHeight / usedHeight
        var shouldScale = scaleY < 1f

        var scaleX = 1f
        if (isScalableGrid) {
            // We scale to fit the cellWidth and cellHeight in the available space.
            // The benefit of scalable grids is that we can get consistent aspect ratios between
            // devices.
            val usedWidth: Float =
                (workspaceProfile.cellLayoutWidthSpecification +
                        (workspaceProfile.desiredWorkspaceHorizontalMarginPx * 2))
                    .toFloat()
            // We do not subtract padding here, as we also scale the workspace padding if needed.
            scaleX = deviceProperties.availableWidthPx / usedWidth
            shouldScale = true
        }

        if (shouldScale) {
            workspaceProfile =
                internalCreateWorkspaceProfileNonResponsive(
                    context = context,
                    res = res,
                    deviceProperties = deviceProperties,
                    scale = min(scaleX.toDouble(), scaleY.toDouble()).toFloat(),
                    inv = inv,
                    isVerticalLayout = isVerticalLayout,
                    isScalableGrid = isScalableGrid,
                    typeIndex = typeIndex,
                    metrics = metrics,
                    panelCount = panelCount,
                    iconSizePx = iconSizePx,
                    isFirstPass = isFirstPass,
                    insets = insets,
                    isSeascape = isSeascape,
                    hotseatProfile = hotseatProfile,
                    hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                    hotseatQsbSpace = hotseatQsbSpace,
                )
            extraHeight =
                max(0, (maxHeight - workspaceProfile.cellLayoutHeightSpecification)).toFloat()
        }
        workspaceProfile = workspaceProfile.copy(extraSpace = Math.round(extraHeight))

        if (isScalableGrid) {
            workspaceProfile =
                workspaceProfile.calculateAndSetWorkspaceVerticalPadding(context, inv)
        }

        // We also need to update WorkspacePadding and CellLayoutPadding, keeping it in a
        // different method to make it easier to keep track
        return workspaceProfile.recalculateWorkspacePadding(
            isVerticalLayout,
            isSeascape,
            inv.isFixedLandscape,
            isScalableGrid,
            hotseatProfile,
            hotseatBarSizePx,
            insets,
            deviceProperties,
            res,
            hotseatBarBottomSpacePx,
            hotseatQsbSpace,
            inv,
        )
    }
}
