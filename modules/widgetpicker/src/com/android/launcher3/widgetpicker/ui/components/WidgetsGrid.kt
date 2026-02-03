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

package com.android.launcher3.widgetpicker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastMaxOfOrDefault
import androidx.compose.ui.util.fastSumBy
import com.android.launcher3.widgetpicker.shared.model.PickableWidget
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetId
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.components.WidgetGridDimensions.MAX_ITEMS_PER_ROW
import com.android.launcher3.widgetpicker.ui.model.WidgetSizeGroup
import kotlin.math.max

/**
 * Displays widgets with their previews and details organized as a grid.
 *
 * @param widgetSizeGroups group of widgets that use same preview size bucket (container) and hence
 *   can be displayed side by side in a row for optimal previewing.
 * @param showAllWidgetDetails whether to show all details of each widget in the grid OR just show a
 *   label.
 * @param appIcons optional map containing app icons to show in the widget details besides the label
 *   (when showing the widgets outside of app context e.g. recommendations)
 * @param showDragShadow indicates if in a drag and drop session, widget picker should show drag
 *   shadow containing the preview; if not set, a transparent shadow is rendered and host should
 *   manage providing a shadow on its own.
 * @param onWidgetInteraction callback invoked when a widget is being dragged and picker has started
 *   global drag and drop session.
 * @param modifier modifier with parent constraints and additional modifications
 */
@Composable
fun WidgetsGrid(
    widgetSizeGroups: List<WidgetSizeGroup>,
    showAllWidgetDetails: Boolean,
    previews: Map<WidgetId, WidgetPreview>,
    modifier: Modifier,
    appIcons: Map<WidgetAppId, WidgetAppIcon> = emptyMap(),
    showDragShadow: Boolean,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
) {
    var addButtonWidgetId by remember { mutableStateOf<WidgetId?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = WidgetGridDimensions.gridVerticalPadding),
    ) {
        widgetSizeGroups.forEach { group ->
            WidgetsFlowRow(
                widgetSizeGroup = group,
                showAllWidgetDetails = showAllWidgetDetails,
                appIcons = appIcons,
                previews = previews,
                showDragShadow = showDragShadow,
                addButtonWidgetId = addButtonWidgetId,
                onWidgetInteraction = onWidgetInteraction,
                onAddButtonToggle = { id ->
                    addButtonWidgetId =
                        if (id != addButtonWidgetId) {
                            id
                        } else {
                            null
                        }
                },
            )
        }
    }
}

/**
 * Custom layout displaying similarly sized widgets in multiple rows.
 * - A key feature is the alignment of the top baseline of each widget's details section within a
 *   row, regardless of preview size.
 * - Supports a maximum of [MAX_ITEMS_PER_ROW] per row.
 * - Row height adapts to the tallest element within it.
 *
 * Example visualization:
 * ```
 * xxxxxx                        xxxxxx
 * xxxxxx        xxxxxxxx        xxxxxx
 * xxxxxx        xxxxxxxx        xxxxxx   <- Different preview heights
 * <title>       <title>         <title>  <- Top baseline aligned
 * <span>        <span>          <span>
 * <description> <description>
 * <continued..>
 * ```
 */
@Composable
private fun WidgetsFlowRow(
    widgetSizeGroup: WidgetSizeGroup,
    showAllWidgetDetails: Boolean,
    addButtonWidgetId: WidgetId?,
    appIcons: Map<WidgetAppId, WidgetAppIcon>,
    previews: Map<WidgetId, WidgetPreview>,
    showDragShadow: Boolean,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    onAddButtonToggle: (WidgetId) -> Unit,
    cellHorizontalPadding: Dp = WidgetGridDimensions.cellHorizontalPadding,
    rowVerticalSpacing: Dp = WidgetGridDimensions.rowVerticalSpacing,
    minItemWidth: Dp = WidgetGridDimensions.minItemWidth,
) {
    val items = widgetSizeGroup.widgets

    WidgetsFlowRowLayout(
        widgetPreviews = {
            Previews(
                widgets = items,
                previews = previews,
                showDragShadow = showDragShadow,
                onWidgetInteraction = onWidgetInteraction,
                onAddButtonToggle = onAddButtonToggle,
            )
        },
        widgetDetails = {
            Details(
                showAllWidgetDetails = showAllWidgetDetails,
                widgets = items,
                appIcons = appIcons,
                addButtonWidgetId = addButtonWidgetId,
                onWidgetInteraction = onWidgetInteraction,
                onAddButtonToggle = onAddButtonToggle,
            )
        },
        previewContainerWidthPx = widgetSizeGroup.previewContainerWidthPx,
        cellHorizontalPadding = cellHorizontalPadding,
        rowVerticalSpacing = rowVerticalSpacing,
        minItemWidth = minItemWidth,
    )
}

@Composable
private fun Previews(
    widgets: List<PickableWidget>,
    previews: Map<WidgetId, WidgetPreview>,
    showDragShadow: Boolean,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    onAddButtonToggle: (WidgetId) -> Unit,
) {
    widgets.forEachIndexed { index, widgetItem ->
        val id = widgetItem.id

        val widgetPreview: WidgetPreview =
            remember(id, previews) {
                previews.getOrDefault(id, WidgetPreview.PlaceholderWidgetPreview)
            }

        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier =
                Modifier.fillMaxSize().clearAndSetSemantics {
                    traversalIndex = index.toFloat()
                    testTag = buildWidgetPickerTestTag(WIDGET_PREVIEW_TEST_TAG)
                },
        ) {
            WidgetPreview(
                id = widgetItem.id,
                sizeInfo = widgetItem.sizeInfo,
                preview = widgetPreview,
                widgetInfo = widgetItem.widgetInfo,
                showDragShadow = showDragShadow,
                onWidgetInteraction = onWidgetInteraction,
                onAddButtonToggle = onAddButtonToggle,
            )
        }
    }
}

@Composable
private fun Details(
    showAllWidgetDetails: Boolean,
    widgets: List<PickableWidget>,
    addButtonWidgetId: WidgetId?,
    appIcons: Map<WidgetAppId, WidgetAppIcon>,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    onAddButtonToggle: (WidgetId) -> Unit,
) {
    widgets.forEachIndexed { index, widgetItem ->
        val appId = widgetItem.appId
        val icon = remember(appId, appIcons) { appIcons.getOrDefault(appId, null) }
        val appIcon: (@Composable () -> Unit)? =
            icon?.let { { WidgetAppIcon(widgetAppIcon = it, size = AppIconSize.SMALL) } }

        WidgetDetails(
            widget = widgetItem,
            showAllDetails = showAllWidgetDetails,
            showAddButton = addButtonWidgetId == widgetItem.id,
            appIcon = appIcon,
            onWidgetAddClick = onWidgetInteraction,
            onAddButtonToggle = onAddButtonToggle,
            modifier =
                Modifier.semantics(mergeDescendants = true) { traversalIndex = index.toFloat() },
        )
    }
}

@Composable
private fun WidgetsFlowRowLayout(
    widgetPreviews: @Composable () -> Unit,
    widgetDetails: @Composable () -> Unit,
    previewContainerWidthPx: Int,
    cellHorizontalPadding: Dp,
    rowVerticalSpacing: Dp,
    minItemWidth: Dp,
) {
    Layout(
        modifier = Modifier.semantics { isTraversalGroup = true },
        contents = listOf(widgetPreviews, widgetDetails),
    ) { (widgetPreviewMeasurables, widgetDetailsMeasurables), constraints ->
        check(widgetPreviewMeasurables.size == widgetDetailsMeasurables.size)
        val parentWidth = constraints.maxWidth
        val rowVerticalSpacingPx = rowVerticalSpacing.roundToPx()
        val cellHorizontalPaddingPx = cellHorizontalPadding.roundToPx()
        val minItemWidthPx = minItemWidth.roundToPx()

        val (possibleItemsPerRow, availableWidthPerItem) =
            calculateItemsPerRowAndMaxWidthPerItem(
                cellHorizontalPaddingPx = cellHorizontalPaddingPx,
                previewContainerWidthPx = previewContainerWidthPx,
                minItemWidthPx = minItemWidthPx,
                parentWidth = parentWidth,
            )

        // Measure and group into rows
        val previewPlaceablesRows =
            widgetPreviewMeasurables.measureAndSplitIntoRows(
                itemsPerRow = possibleItemsPerRow,
                constraints =
                    constraints.copy(
                        maxWidth = availableWidthPerItem,
                        maxHeight = Constraints.Infinity,
                    ),
            )
        val detailsPlaceableRows =
            widgetDetailsMeasurables.measureAndSplitIntoRows(
                itemsPerRow = possibleItemsPerRow,
                constraints =
                    constraints.copy(
                        maxWidth = availableWidthPerItem,
                        maxHeight = Constraints.Infinity,
                    ),
            )
        check(previewPlaceablesRows.size == detailsPlaceableRows.size)

        // Now we need:
        // 1) totalGridHeight to pass to layout constraints and
        // 2) height of the tallest preview in each row, and
        // 3) height of tallest details section in a row.
        val (totalGridHeight, measuredRowDimensions) =
            collectMeasuredDimensions(
                numberOfRows = previewPlaceablesRows.size,
                previewPlaceableRows = previewPlaceablesRows,
                detailsPlaceableRows = detailsPlaceableRows,
                rowVerticalSpacingPx = rowVerticalSpacingPx,
            )

        // Place
        layout(constraints.maxWidth, totalGridHeight) {
            placeRows(
                previewPlaceableRows = previewPlaceablesRows,
                detailsPlaceableRows = detailsPlaceableRows,
                measuredRowDimensions = measuredRowDimensions,
                parentWidth = parentWidth,
                rowVerticalSpacingPx = rowVerticalSpacingPx,
            )
        }
    }
}

private fun collectMeasuredDimensions(
    numberOfRows: Int,
    previewPlaceableRows: List<List<Placeable>>,
    detailsPlaceableRows: List<List<Placeable>>,
    rowVerticalSpacingPx: Int,
): Pair<Int, MutableList<MeasuredRowDimensions>> {
    var totalGridHeight = 0
    val measuredRowDimensions = mutableListOf<MeasuredRowDimensions>()
    repeat(numberOfRows) { index ->
        val previewsRow = previewPlaceableRows[index]
        val detailsRow = detailsPlaceableRows[index]

        val maxPreviewHeight = previewsRow.fastMaxOfOrDefault(0) { it.height }
        val maxDetailsHeight = detailsRow.fastMaxOfOrDefault(0) { it.height }
        val totalRowWidth = detailsRow.fastSumBy { it.width }

        measuredRowDimensions.add(
            MeasuredRowDimensions(
                tallestPreviewHeight = maxPreviewHeight,
                tallestDetailsHeight = maxDetailsHeight,
                totalWidth = totalRowWidth,
            )
        )

        totalGridHeight += maxPreviewHeight + maxDetailsHeight + rowVerticalSpacingPx
    }
    return Pair(totalGridHeight, measuredRowDimensions)
}

private fun Placeable.PlacementScope.placeRows(
    previewPlaceableRows: List<List<Placeable>>,
    detailsPlaceableRows: List<List<Placeable>>,
    measuredRowDimensions: List<MeasuredRowDimensions>,
    parentWidth: Int,
    rowVerticalSpacingPx: Int,
) {
    check(previewPlaceableRows.size == detailsPlaceableRows.size)
    val rowSize = previewPlaceableRows.size

    var yPosition = 0
    repeat(rowSize) { index ->
        val previewsRow = previewPlaceableRows[index]
        val detailsRow = detailsPlaceableRows[index]
        val measuredRow = measuredRowDimensions[index]
        // Divide padding between items to center everything.
        val padding = ((parentWidth - measuredRow.totalWidth) / previewsRow.size) / 2

        var xPosition = 0
        repeat(previewsRow.size) { rowItemIndex ->
            val detailItem = detailsRow[rowItemIndex]
            val previewItem = previewsRow[rowItemIndex]
            val itemWidth = max(detailItem.width, previewItem.width)

            xPosition += padding

            // Offset the preview by the difference in its height when compared to its
            // tallest sibling, so that it will appear bottom aligned.
            val previewTopOffset =
                measuredRow.tallestPreviewHeight - previewsRow[rowItemIndex].height
            previewsRow[rowItemIndex].placeRelative(xPosition, yPosition + previewTopOffset)
            // place details after size of the tallest preview
            detailsRow[rowItemIndex].placeRelative(
                xPosition,
                (yPosition + (measuredRow.tallestPreviewHeight)),
            )
            xPosition += itemWidth + padding // right padding
        }

        // Move to next row
        yPosition +=
            measuredRow.tallestPreviewHeight +
                measuredRow.tallestDetailsHeight +
                rowVerticalSpacingPx
    }
}

private fun calculateItemsPerRowAndMaxWidthPerItem(
    cellHorizontalPaddingPx: Int,
    previewContainerWidthPx: Int,
    minItemWidthPx: Int,
    parentWidth: Int,
): Pair<Int, Int> {
    val totalItemHorizontalPadding = 2 * cellHorizontalPaddingPx

    // Let's assume at minimum an item takes up preview container width
    val minWidthItemMightNeed = max(previewContainerWidthPx, minItemWidthPx)

    // And with its horizontal padding added, we can then calculate how many items fit in a row
    // and then cap it to a maximum limit.
    val possibleItemsPerRow =
        (parentWidth / (minWidthItemMightNeed + totalItemHorizontalPadding)).coerceIn(
            minimumValue = 1,
            maximumValue = MAX_ITEMS_PER_ROW,
        )

    // Using the capped number, we find out how much space will then be available for an item.
    val availableWidthPerItem =
        (parentWidth - (totalItemHorizontalPadding * possibleItemsPerRow)) / possibleItemsPerRow

    return Pair(possibleItemsPerRow, availableWidthPerItem)
}

private data class MeasuredRowDimensions(
    val tallestPreviewHeight: Int,
    val tallestDetailsHeight: Int,
    val totalWidth: Int,
)

/**
 * Measures the items and in same pass attempts to group the placeables into multiple rows based on
 * the available width.
 */
private fun List<Measurable>.measureAndSplitIntoRows(
    constraints: Constraints,
    itemsPerRow: Int,
): List<List<Placeable>> {
    return fastFold(mutableListOf<MutableList<Placeable>>()) { rows, measurable ->
        val placeable = measurable.measure(constraints)

        if (rows.isEmpty() || rows.last().size == itemsPerRow) {
            rows.add(mutableListOf(placeable))
        } else {
            rows.last().add(placeable)
        }

        rows
    }
}

private object WidgetGridDimensions {
    val cellHorizontalPadding: Dp = 4.dp
    val rowVerticalSpacing: Dp = 12.dp
    val gridVerticalPadding: Dp = 16.dp

    // We display at max 3 items side by side - which usually is case in case of shortcuts.
    const val MAX_ITEMS_PER_ROW = 3
    val minItemWidth = 100.dp
}

private const val WIDGET_PREVIEW_TEST_TAG = "widget_preview"
