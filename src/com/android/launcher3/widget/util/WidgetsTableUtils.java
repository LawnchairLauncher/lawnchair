/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget.util;

import android.content.Context;

import androidx.annotation.Px;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.model.WidgetItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** An utility class which groups {@link WidgetItem}s into a table. */
public final class WidgetsTableUtils {

    /**
     * Groups widgets in the following order:
     * 1. Widgets always go before shortcuts.
     * 2. Widgets with smaller horizontal spans will be shown first.
     * 3. If widgets have the same horizontal spans, then widgets with a smaller vertical spans will
     *    go first.
     * 4. If both widgets have the same horizontal and vertical spans, they will use the same order
     *    from the given {@code widgetItems}.
     */
    private static final Comparator<WidgetItem> WIDGET_SHORTCUT_COMPARATOR = (item, otherItem) -> {
        if (item.widgetInfo != null && otherItem.widgetInfo == null) return -1;

        if (item.widgetInfo == null && otherItem.widgetInfo != null) return 1;
        if (item.spanX == otherItem.spanX) {
            if (item.spanY == otherItem.spanY) return 0;
            return item.spanY > otherItem.spanY ? 1 : -1;
        }
        return item.spanX > otherItem.spanX ? 1 : -1;
    };

    /**
     * Groups {@code widgetItems} items into a 2D array which matches their appearance in a UI
     * table. This takes liberty to rearrange widgets to make the table visually appealing.
     */
    public static List<ArrayList<WidgetItem>> groupWidgetItemsUsingRowPxWithReordering(
            List<WidgetItem> widgetItems, Context context, final DeviceProfile dp,
            final @Px int rowPx, final @Px int cellPadding) {
        List<WidgetItem> sortedWidgetItems = widgetItems.stream().sorted(WIDGET_SHORTCUT_COMPARATOR)
                .collect(Collectors.toList());
        return groupWidgetItemsUsingRowPxWithoutReordering(sortedWidgetItems, context, dp, rowPx,
                cellPadding);
    }

    /**
     * Groups {@code widgetItems} into a 2D array which matches their appearance in a UI table while
     * maintaining their order. This function is a variant of
     * {@code groupWidgetItemsIntoTableWithoutReordering} in that this uses widget pixels for
     * calculation.
     *
     * <p>Grouping:
     * 1. Widgets and shortcuts never group together in the same row.
     * 2. The ordered widgets are grouped together in the same row until their individual occupying
     *    pixels exceed the total allowed pixels for the cell.
     * 3. The ordered shortcuts are grouped together in the same row until their individual
     *    occupying pixels exceed the total allowed pixels for the cell.
     * 4. If there is only one widget in a row, its width may exceed the {@code rowPx}.
     *
     * <p>Let's say the {@code rowPx} is set to 600 and we have 5 widgets. Widgets can be grouped
     * in the same row if each of their individual occupying pixels does not exceed
     * {@code rowPx} / 5 - 2 * {@code cellPadding}.
     * Example 1: Row 1: 200x200, 200x300, 100x100. Average horizontal pixels is 200 and no widgets
     * exceed that width. This is okay.
     * Example 2: Row 1: 200x200, 400x300, 100x100. Average horizontal pixels is 200 and one widget
     * exceed that width. This is not allowed.
     * Example 3: Row 1: 700x400. This is okay because this is the only item in the row.
     */
    public static List<ArrayList<WidgetItem>> groupWidgetItemsUsingRowPxWithoutReordering(
            List<WidgetItem> widgetItems, Context context, final DeviceProfile dp,
            final @Px int rowPx, final @Px int cellPadding) {

        List<ArrayList<WidgetItem>> widgetItemsTable = new ArrayList<>();
        ArrayList<WidgetItem> widgetItemsAtRow = null;
        for (WidgetItem widgetItem : widgetItems) {
            if (widgetItemsAtRow == null) {
                widgetItemsAtRow = new ArrayList<>();
                widgetItemsTable.add(widgetItemsAtRow);
            }
            int numOfWidgetItems = widgetItemsAtRow.size();
            @Px int individualSpan = (rowPx / (numOfWidgetItems + 1)) - (2 * cellPadding);
            if (numOfWidgetItems == 0) {
                widgetItemsAtRow.add(widgetItem);
            } else if (
                    // Since the size of the widget cell is determined by dividing the maximum span
                    // pixels evenly, making sure that each widget would have enough span pixels to
                    // show their contents.
                    widgetItem.hasSameType(widgetItemsAtRow.get(numOfWidgetItems - 1))
                    && widgetItemsAtRow.stream().allMatch(
                            item -> WidgetSizes.getWidgetItemSizePx(context, dp, item)
                                    .getWidth() <= individualSpan)
                    && WidgetSizes.getWidgetItemSizePx(context, dp, widgetItem)
                            .getWidth() <= individualSpan) {
                // Group items in the same row if
                // 1. they are with the same type, i.e. a row can only have widgets or shortcuts but
                //    never a mix of both.
                // 2. Each widget will have horizontal cell span pixels that is at least as large as
                //    it is required to fit in the horizontal content, unless the widget horizontal
                //    span pixels is larger than the maximum allowed.
                //    If an item has horizontal span pixels larger than the maximum allowed pixels
                //    per row, we just place it in its own row regardless of the horizontal span
                //    limit.
                widgetItemsAtRow.add(widgetItem);
            } else {
                widgetItemsAtRow = new ArrayList<>();
                widgetItemsTable.add(widgetItemsAtRow);
                widgetItemsAtRow.add(widgetItem);
            }
        }
        return widgetItemsTable;
    }
}
