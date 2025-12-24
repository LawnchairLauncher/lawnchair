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
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import com.android.launcher3.DevicePaddings
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.Utilities.getIconSizeWithOverlap
import com.android.launcher3.Utilities.getNormalizedIconDrawablePadding
import com.android.launcher3.deviceprofile.WorkspaceProfileNonResponsiveFactory.createWorkspaceProfileNonResponsive
import com.android.launcher3.responsive.CalculatedCellSpec
import com.android.launcher3.responsive.CalculatedResponsiveSpec
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.CellContentDimensions
import com.android.launcher3.util.IconSizeSteps
import kotlin.math.max
import kotlin.math.min

/**
 * All the variables that visually define the Workspace and are dependant on the device
 * configuration.
 */
data class WorkspaceProfile(
    // Workspace icons
    val iconScale: Float,
    val iconSizePx: Int,
    val iconTextSizePx: Int,
    val iconDrawablePaddingPx: Int,
    val cellScaleToFit: Float,
    val cellWidthPx: Int,
    val cellHeightPx: Int,
    val cellLayoutBorderSpacePx: Point,
    val desiredWorkspaceHorizontalMarginPx: Int,
    val cellYPaddingPx: Int = -1,
    val maxIconTextLineCount: Int,
    val iconCenterVertically: Boolean,
    val desiredWorkspaceHorizontalMarginOriginalPx: Int,
    val workspaceContentScale: Float,
    val workspaceSpringLoadedMinNextPageVisiblePx: Int,
    val maxEmptySpace: Int,
    val workspaceTopPadding: Int,
    val workspaceBottomPadding: Int,
    val workspaceCellPaddingXPx: Int,
    val edgeMarginPx: Int,
    val cellLayoutPaddingPx: Rect,
    val workspacePadding: Rect,
    val panelCount: Int,

    // Visualization
    val gridVisualizationPaddingX: Int,
    val gridVisualizationPaddingY: Int,

    // Workspace page indicator
    val workspacePageIndicatorHeight: Int,
    val workspacePageIndicatorOverlapWorkspace: Int,
    val isLabelHidden: Boolean = false,
    val iconDrawablePaddingOriginalPx: Int,
    val cellLayoutHeightSpecification: Int,
    val cellLayoutWidthSpecification: Int,
    val cellSize: Point,
    val scale: Float,
    val extraSpace: Int = 0,
) {

    fun getTotalWorkspacePadding(): Point =
        Point(
            workspacePadding.left + workspacePadding.right,
            workspacePadding.top + workspacePadding.bottom,
        )

    // TODO(b/432070502)
    @Deprecated(
        "This is only used for scalable which is deprecated. This should also go away once " +
            "we add extraSpace into the WorkspaceProfile"
    )
    fun calculateAndSetWorkspaceVerticalPadding(
        context: Context,
        inv: InvariantDeviceProfile,
    ): WorkspaceProfile {
        if (inv.devicePaddingId != ResourceUtils.INVALID_RESOURCE_HANDLE) {
            // Paddings were created assuming no scaling, so we first unscale the extra space.
            val unscaledExtraSpace: Int = (extraSpace / cellScaleToFit).toInt()
            val devicePaddings = DevicePaddings(context, inv.devicePaddingId)
            val padding = devicePaddings.getDevicePadding(unscaledExtraSpace)
            return copy(
                maxEmptySpace = padding.maxEmptySpacePx,
                workspaceTopPadding =
                    Math.round(padding.getWorkspaceTopPadding(unscaledExtraSpace) * cellScaleToFit),
                workspaceBottomPadding =
                    Math.round(
                        padding.getWorkspaceBottomPadding(unscaledExtraSpace) * cellScaleToFit
                    ),
            )
        }
        return this
    }

    // TODO(b/430382569)
    @Deprecated(
        "This classes should be treated as immutable, in order to change it we" +
            "should use a factory and create a new one."
    )
    fun recalculateWorkspacePadding(
        isVerticalLayout: Boolean,
        isSeascape: Boolean,
        isFixedLandscape: Boolean,
        isScalableGrid: Boolean,
        hotseatProfile: HotseatProfile,
        hotseatBarSizePx: Int,
        insets: Rect,
        deviceProperties: DeviceProperties,
        res: Resources,
        hotseatBarBottomSpacePx: Int,
        hotseatQsbSpace: Int,
        inv: InvariantDeviceProfile,
    ): WorkspaceProfile {
        val noInsetWorkspacePadding =
            WorkspaceProfileNonResponsiveFactory.createWorkspacePadding(
                isVerticalLayout = isVerticalLayout,
                isSeascape = isSeascape,
                isFixedLandscape = isFixedLandscape,
                isScalableGrid = isScalableGrid,
                hotseatProfile = hotseatProfile,
                insets = insets,
                desiredWorkspaceHorizontalMarginPx = desiredWorkspaceHorizontalMarginPx,
                edgeMarginPx = edgeMarginPx,
                workspacePageIndicatorHeight = workspacePageIndicatorHeight,
                workspacePageIndicatorOverlapWorkspace = workspacePageIndicatorOverlapWorkspace,
                workspaceTopPadding = workspaceTopPadding,
                workspaceBottomPadding = workspaceBottomPadding,
                hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                hotseatQsbSpace = hotseatQsbSpace,
                iconSize = iconSizePx,
            )
        val cellLayoutPadding =
            when {
                deviceProperties.isTwoPanels -> cellLayoutBorderSpacePx.x / 2
                else -> res.getDimensionPixelSize(R.dimen.cell_layout_padding)
            }
        val (workspacePadding, cellLayoutPaddingPx) =
            insetPadding(
                noInsetWorkspacePadding,
                Rect(cellLayoutPadding, cellLayoutPadding, cellLayoutPadding, cellLayoutPadding),
            )
        val cellSize =
            calculateCellSize(
                cellLayoutBorderSpacePx = this.cellLayoutBorderSpacePx,
                panelCount = this.panelCount,
                deviceProperties = deviceProperties,
                numColumns = inv.numColumns,
                numRows = inv.numRows,
                cellLayoutPadding = cellLayoutPaddingPx,
                totalWorkspacePadding =
                    Point(
                        workspacePadding.left + workspacePadding.right,
                        workspacePadding.top + workspacePadding.bottom,
                    ),
            )
        return copy(
            workspacePadding = workspacePadding,
            cellLayoutPaddingPx = cellLayoutPaddingPx,
            cellSize = cellSize,
        )
    }

    // TODO(b/430382569)
    @Deprecated(
        "This classes should be treated as immutable, in order to change it we" +
            "should use a factory and create a new one."
    )
    fun changeIconSize(iconSizePx: Int): WorkspaceProfile {
        return copy(iconSizePx = iconSizePx)
    }

    companion object Factory {

        // TODO: the first time we use this, cellLayoutPadding and totalWorkspacePadding are zero
        // but it doesn't make sense to do that, and other variables depend on those values, we need
        // to
        // fix the order
        fun calculateCellSize(
            cellLayoutBorderSpacePx: Point,
            panelCount: Int,
            deviceProperties: DeviceProperties,
            numColumns: Int,
            numRows: Int,
            cellLayoutPadding: Rect,
            totalWorkspacePadding: Point,
        ): Point {

            val cellLayoutWith =
                ((deviceProperties.availableWidthPx - totalWorkspacePadding.x) / panelCount)
            val cellLayoutHeight = deviceProperties.availableHeightPx - totalWorkspacePadding.y
            return Point(
                DeviceProfile.calculateCellWidth(
                    // shortcutAndWidgetContainerWidth
                    cellLayoutWith - (cellLayoutPadding.left + cellLayoutPadding.right),
                    cellLayoutBorderSpacePx.x,
                    numColumns,
                ),
                DeviceProfile.calculateCellHeight(
                    // shortcutAndWidgetContainerHeight
                    cellLayoutHeight - (cellLayoutPadding.top + cellLayoutPadding.bottom),
                    cellLayoutBorderSpacePx.y,
                    numRows,
                ),
            )
        }

        fun insetPadding(paddings: Rect, insetsArgs: Rect): Pair<Rect, Rect> {
            val insets =
                Rect(
                    min(insetsArgs.left, paddings.left),
                    min(insetsArgs.top, paddings.top),
                    min(insetsArgs.right, paddings.right),
                    min(insetsArgs.bottom, paddings.bottom),
                )
            return Pair(
                Rect(
                    paddings.left - insets.left,
                    paddings.top - insets.top,
                    paddings.right - insets.right,
                    paddings.bottom - insets.bottom,
                ),
                insets,
            )
        }

        fun calculateHotseatBarSizePx(
            iconSizePx: Int,
            isVerticalLayout: Boolean,
            hotseatProfile: HotseatProfile,
            hotseatBarBottomSpacePx: Int,
            hotseatQsbSpace: Int,
            isQsbInline: Boolean,
        ): Int {
            return when {
                isVerticalLayout -> {
                    (iconSizePx +
                        hotseatProfile.barEdgePaddingPx +
                        hotseatProfile.barWorkspaceSpacePx)
                }
                isQsbInline -> {
                    (max(iconSizePx, hotseatProfile.qsbVisualHeight) + hotseatBarBottomSpacePx)
                }
                else -> {
                    (iconSizePx +
                        hotseatQsbSpace +
                        hotseatProfile.qsbVisualHeight +
                        hotseatBarBottomSpacePx)
                }
            }
        }

        private fun createWorkspacePadding(
            isVerticalLayout: Boolean,
            isSeascape: Boolean,
            isFixedLandscape: Boolean,
            isQsbInline: Boolean,
            isScalableGrid: Boolean,
            responsiveWorkspaceWidthSpec: CalculatedResponsiveSpec,
            responsiveWorkspaceHeightSpec: CalculatedResponsiveSpec,
            desiredWorkspaceHorizontalMarginPx: Int,
            workspaceTopPadding: Int,
            workspaceBottomPadding: Int,
            insets: Rect,
            edgeMarginPx: Int,
            hotseatProfile: HotseatProfile,
            hotseatBarBottomSpacePx: Int,
            hotseatQsbSpace: Int,
            iconSize: Int,
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
                    isQsbInline = isQsbInline,
                )

            when {
                isVerticalLayout -> {
                    val endMargin = hotseatBarSizePx + responsiveWorkspaceWidthSpec.endPaddingPx
                    return Rect(
                        /* left */
                        if (isSeascape) endMargin else responsiveWorkspaceWidthSpec.startPaddingPx,
                        /* top */
                        responsiveWorkspaceHeightSpec.startPaddingPx,
                        /* right */
                        if (isSeascape) responsiveWorkspaceWidthSpec.startPaddingPx else endMargin,
                        /* bottom */
                        max(0, (responsiveWorkspaceHeightSpec.endPaddingPx - insets.bottom)),
                    )
                }

                else -> {
                    // Pad the bottom of the workspace with hotseat bar
                    // and leave a bit of space in case a widget go all the way down
                    val padding =
                        Rect(
                            /* left */ desiredWorkspaceHorizontalMarginPx,
                            /* top */ (workspaceTopPadding +
                                (if (isScalableGrid) 0 else edgeMarginPx)),
                            /* right */ desiredWorkspaceHorizontalMarginPx,
                            /* bottom */ (hotseatBarSizePx + workspaceBottomPadding - insets.bottom),
                        )

                    // In fixed Landscape we don't need padding on the side next to the cutout
                    // because
                    // the cutout is already adding padding to all of Launcher, we only need on the
                    // other
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
        }

        fun createWorkspaceProfileResponsiveGrid(
            res: Resources,
            inv: InvariantDeviceProfile,
            deviceProperties: DeviceProperties,
            iconSizeSteps: IconSizeSteps,
            isVerticalLayout: Boolean,
            isScalableGrid: Boolean,
            isSeascape: Boolean,
            isQsbInline: Boolean,
            responsiveWorkspaceWidthSpec: CalculatedResponsiveSpec,
            responsiveWorkspaceHeightSpec: CalculatedResponsiveSpec,
            responsiveWorkspaceCellSpec: CalculatedCellSpec,
            iconScale: Float,
            cellScaleToFit: Float,
            insets: Rect,
            hotseatProfile: HotseatProfile,
            hotseatBarBottomSpacePx: Int,
            hotseatQsbSpace: Int,
            panelCount: Int,
        ): WorkspaceProfile {

            val cellLayoutBorderSpacePx =
                Point(responsiveWorkspaceWidthSpec.gutterPx, responsiveWorkspaceHeightSpec.gutterPx)
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

            val iconDrawablePaddingOriginalPx = responsiveWorkspaceCellSpec.iconDrawablePadding
            var iconTextSizePx = responsiveWorkspaceCellSpec.iconTextSize
            var iconSizePx = responsiveWorkspaceCellSpec.iconSize
            val cellWidthPx = responsiveWorkspaceWidthSpec.cellSizePx
            val cellHeightPx = responsiveWorkspaceHeightSpec.cellSizePx
            var maxIconTextLineCount = responsiveWorkspaceCellSpec.iconTextMaxLineCount

            if (cellWidthPx < iconSizePx) {
                // get a smaller icon size
                iconSizePx = iconSizeSteps.getIconSmallerThan(cellWidthPx)
            }

            var iconDrawablePaddingPx: Int
            if (isVerticalLayout) {
                iconDrawablePaddingPx = 0
                iconTextSizePx = 0
                maxIconTextLineCount = 0
            } else {
                iconDrawablePaddingPx =
                    getNormalizedIconDrawablePadding(iconSizePx, iconDrawablePaddingOriginalPx)
            }

            val cellContentDimensions =
                CellContentDimensions(
                    iconSizePx,
                    iconDrawablePaddingPx,
                    iconTextSizePx,
                    maxIconTextLineCount,
                )
            val cellContentHeight =
                cellContentDimensions.resizeToFitCellHeight(cellHeightPx, iconSizeSteps)
            iconSizePx = cellContentDimensions.iconSizePx
            iconDrawablePaddingPx = cellContentDimensions.iconDrawablePaddingPx
            iconTextSizePx = cellContentDimensions.iconTextSizePx
            maxIconTextLineCount = cellContentDimensions.maxLineCount

            val cellYPaddingPx =
                if (isVerticalLayout) {
                    max(0, cellSize.y - getIconSizeWithOverlap(iconSizePx)) / 2
                } else {
                    max(0, (cellHeightPx - cellContentHeight)) / 2
                }

            val edgeMarginPx = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin)

            val cellLayoutPadding =
                if (deviceProperties.isTwoPanels) cellLayoutBorderSpacePx.x / 2
                else res.getDimensionPixelSize(R.dimen.cell_layout_padding)

            val workspaceTopPadding = responsiveWorkspaceHeightSpec.startPaddingPx
            val workspaceBottomPadding = responsiveWorkspaceHeightSpec.endPaddingPx
            val desiredWorkspaceHorizontalMarginPx = responsiveWorkspaceWidthSpec.startPaddingPx
            val noInsetWorkspacePadding =
                createWorkspacePadding(
                    isVerticalLayout = isVerticalLayout,
                    isSeascape = isSeascape,
                    isFixedLandscape = inv.isFixedLandscape,
                    isScalableGrid = isScalableGrid,
                    responsiveWorkspaceWidthSpec = responsiveWorkspaceWidthSpec,
                    responsiveWorkspaceHeightSpec = responsiveWorkspaceHeightSpec,
                    desiredWorkspaceHorizontalMarginPx = desiredWorkspaceHorizontalMarginPx,
                    workspaceTopPadding = workspaceTopPadding,
                    workspaceBottomPadding = workspaceBottomPadding,
                    insets = insets,
                    edgeMarginPx = edgeMarginPx,
                    hotseatProfile = hotseatProfile,
                    hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                    iconSize = iconSizePx,
                    hotseatQsbSpace = hotseatQsbSpace,
                    isQsbInline = isQsbInline,
                )

            val (workspacePadding, cellLayoutPaddingPx) =
                insetPadding(
                    noInsetWorkspacePadding,
                    Rect(cellLayoutPadding, cellLayoutPadding, cellLayoutPadding, cellLayoutPadding),
                )

            val numColumns: Int = panelCount * inv.numColumns

            // TODO: this is really bad, we shouldn't be calculating this twice
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
                cellYPaddingPx = cellYPaddingPx,
                maxIconTextLineCount = maxIconTextLineCount,
                iconCenterVertically = isVerticalLayout,
                gridVisualizationPaddingX =
                    res.getDimensionPixelSize(R.dimen.grid_visualization_horizontal_cell_spacing),
                gridVisualizationPaddingY =
                    res.getDimensionPixelSize(R.dimen.grid_visualization_vertical_cell_spacing),
                workspacePageIndicatorHeight =
                    res.getDimensionPixelSize(R.dimen.workspace_page_indicator_height),
                workspacePageIndicatorOverlapWorkspace =
                    res.getDimensionPixelSize(R.dimen.workspace_page_indicator_overlap_workspace),
                iconDrawablePaddingOriginalPx = responsiveWorkspaceCellSpec.iconDrawablePadding,
                desiredWorkspaceHorizontalMarginOriginalPx =
                    responsiveWorkspaceWidthSpec.startPaddingPx,
                workspaceContentScale = res.getFloat(R.dimen.workspace_content_scale),
                workspaceSpringLoadedMinNextPageVisiblePx =
                    res.getDimensionPixelSize(
                        R.dimen.dynamic_grid_spring_loaded_min_next_space_visible
                    ),
                workspaceCellPaddingXPx =
                    res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_padding_x),
                workspaceTopPadding = workspaceTopPadding,
                workspaceBottomPadding = workspaceBottomPadding,
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
                scale = 1f,
                extraSpace = 0,
            )
        }

        fun createWorkspaceProfile(
            context: Context,
            res: Resources,
            deviceProperties: DeviceProperties,
            scale: Float,
            inv: InvariantDeviceProfile,
            iconSizeSteps: IconSizeSteps,
            isVerticalLayout: Boolean,
            isResponsiveGrid: Boolean,
            isScalableGrid: Boolean,
            isQsbInline: Boolean,
            mResponsiveWorkspaceWidthSpec: CalculatedResponsiveSpec?,
            mResponsiveWorkspaceHeightSpec: CalculatedResponsiveSpec?,
            mResponsiveWorkspaceCellSpec: CalculatedCellSpec?,
            typeIndex: Int,
            metrics: DisplayMetrics,
            panelCount: Int,
            iconSizePx: Int,
            insets: Rect,
            isFirstPass: Boolean,
            isSeascape: Boolean,
            hotseatProfile: HotseatProfile,
            hotseatBarBottomSpacePx: Int,
            hotseatQsbSpace: Int,
            hotseatBarSizePx: Int,
        ): WorkspaceProfile {
            // Icon scale should never exceed 1, otherwise pixellation may occur.
            val iconScale = min(1f, scale)
            val cellScaleToFit = scale
            // Workspace
            return when {
                (isResponsiveGrid &&
                    mResponsiveWorkspaceWidthSpec != null &&
                    mResponsiveWorkspaceHeightSpec != null &&
                    mResponsiveWorkspaceCellSpec != null) ->
                    createWorkspaceProfileResponsiveGrid(
                        res = res,
                        inv = inv,
                        iconSizeSteps = iconSizeSteps,
                        isVerticalLayout = isVerticalLayout,
                        responsiveWorkspaceWidthSpec = mResponsiveWorkspaceWidthSpec,
                        responsiveWorkspaceHeightSpec = mResponsiveWorkspaceHeightSpec,
                        responsiveWorkspaceCellSpec = mResponsiveWorkspaceCellSpec,
                        iconScale = iconScale,
                        cellScaleToFit = cellScaleToFit,
                        deviceProperties = deviceProperties,
                        isScalableGrid = isScalableGrid,
                        insets = insets,
                        isSeascape = isSeascape,
                        hotseatProfile = hotseatProfile,
                        hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                        hotseatQsbSpace = hotseatQsbSpace,
                        isQsbInline = isQsbInline,
                        panelCount = panelCount,
                    )

                else ->
                    createWorkspaceProfileNonResponsive(
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
                        context = context,
                        isFirstPass = isFirstPass,
                        insets = insets,
                        isSeascape = isSeascape,
                        hotseatProfile = hotseatProfile,
                        hotseatBarBottomSpacePx = hotseatBarBottomSpacePx,
                        hotseatQsbSpace = hotseatQsbSpace,
                        hotseatBarSizePx = hotseatBarSizePx,
                    )
            }
        }
    }
}
