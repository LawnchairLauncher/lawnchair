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
import android.util.Size;

import androidx.annotation.Px;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.widget.picker.util.WidgetPreviewContainerSize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** An utility class which groups {@link WidgetItem}s into a table. */
public final class WidgetsTableUtils {
    private static final int MAX_ITEMS_IN_ROW = 3;

    /**
     * Groups widgets in the following order:
     * 1. Widgets always go before shortcuts.
     * 2. Widgets with smaller vertical spans will be shown first.
     * 3. If widgets have the same vertical spans, then widgets with a smaller horizontal spans will
     *    go first.
     * 4. If both widgets have the same horizontal and vertical spans, they will use the same order
     *    from the given {@code widgetItems}.
     */
    private static final Comparator<WidgetItem> WIDGET_SHORTCUT_COMPARATOR = (item, otherItem) -> {
        if (item.widgetInfo != null && otherItem.widgetInfo == null) return -1;

        if (item.widgetInfo == null && otherItem.widgetInfo != null) return 1;
        if (item.spanY == otherItem.spanY) {
            if (item.spanX == otherItem.spanX) return 0;
            return item.spanX > otherItem.spanX ? 1 : -1;
        }
        return item.spanY > otherItem.spanY ? 1 : -1;
    };

    /**
     * Comparator that enables displaying rows in increasing order of their size (totalW * H);
     * except for shortcuts which always show at the bottom.
     */
    public static final Comparator<ArrayList<WidgetItem>> WIDGETS_TABLE_ROW_SIZE_COMPARATOR =
            Comparator.comparingInt(row -> {
                if (row.stream().anyMatch(WidgetItem::isShortcut)) {
                    return Integer.MAX_VALUE;
                } else {
                    int rowWidth = row.stream().mapToInt(w -> w.spanX).sum();
                    int rowHeight = row.get(0).spanY;
                    return (rowWidth * rowHeight);
                }
            });

    /**
     * Groups {@code widgetItems} items into a 2D array which matches their appearance in a UI
     * table. This takes liberty to rearrange widgets to make the table visually appealing.
     */
    public static List<ArrayList<WidgetItem>> groupWidgetItemsUsingRowPxWithReordering(
            List<WidgetItem> widgetItems, Context context, final DeviceProfile dp,
            final @Px int rowPx, final @Px int cellPadding) {
        List<WidgetItem> sortedWidgetItems = widgetItems.stream().sorted(WIDGET_SHORTCUT_COMPARATOR)
                .collect(Collectors.toList());
        List<ArrayList<WidgetItem>> rows = groupWidgetItemsUsingRowPxWithoutReordering(
                sortedWidgetItems, context, dp, rowPx,
                cellPadding);
        return rows.stream().sorted(WIDGETS_TABLE_ROW_SIZE_COMPARATOR).toList();
    }

    /**
     * Groups {@code widgetItems} into a 2D array which matches their appearance in a UI table while
     * maintaining their order. This function is a variant of
     * {@code groupWidgetItemsIntoTableWithoutReordering} in that this uses widget container's
     * pixels for calculation.
     *
     * <p>Grouping:
     * 1. Widgets and shortcuts never group together in the same row.
     * 2. Widgets are grouped together only if they have same preview container size.
     * 3. Widgets are grouped together in the same row until the total of individual container sizes
     *    exceed the total allowed pixels for the row.
     * 3. The ordered shortcuts are grouped together in the same row until their individual
     *    occupying pixels exceed the total allowed pixels for the cell.
     * 4. If there is only one widget in a row, its width may exceed the {@code rowPx}.
     *
     * <p>See WidgetTableUtilsTest
     */
    public static List<ArrayList<WidgetItem>> groupWidgetItemsUsingRowPxWithoutReordering(
            List<WidgetItem> widgetItems, Context context, final DeviceProfile dp,
            final @Px int rowPx, final @Px int cellPadding) {
        List<ArrayList<WidgetItem>> widgetItemsTable = new ArrayList<>();
        ArrayList<WidgetItem> widgetItemsAtRow = null;
        // A row displays only items of same container size.
        WidgetPreviewContainerSize containerSizeForRow = null;
        @Px int currentRowWidth = 0;

        for (WidgetItem widgetItem : widgetItems) {
            if (widgetItemsAtRow == null) {
                widgetItemsAtRow = new ArrayList<>();
                widgetItemsTable.add(widgetItemsAtRow);
            }
            int numOfWidgetItems = widgetItemsAtRow.size();

            WidgetPreviewContainerSize containerSize =
                    WidgetPreviewContainerSize.Companion.forItem(widgetItem, dp);
            Size containerSizePx = WidgetSizes.getWidgetSizePx(dp, containerSize.spanX,
                    containerSize.spanY);
            @Px int containerWidth = containerSizePx.getWidth() + (2 * cellPadding);

            if (numOfWidgetItems == 0) {
                widgetItemsAtRow.add(widgetItem);
                containerSizeForRow = containerSize;
                currentRowWidth = containerWidth;
            } else if (widgetItemsAtRow.size() < MAX_ITEMS_IN_ROW
                    && (currentRowWidth + containerWidth) <= rowPx
                    && widgetItem.hasSameType(widgetItemsAtRow.get(numOfWidgetItems - 1))
                    && containerSize.equals(containerSizeForRow)) {
                // Group items in the same row if
                // 1. they are with the same type, i.e. a row can only have widgets or shortcuts but
                //    never a mix of both.
                // 2. Each widget in the given row has same preview container size.
                widgetItemsAtRow.add(widgetItem);
                currentRowWidth += containerWidth;
            } else {
                widgetItemsAtRow = new ArrayList<>();
                widgetItemsTable.add(widgetItemsAtRow);
                widgetItemsAtRow.add(widgetItem);
                containerSizeForRow = containerSize;
                currentRowWidth = containerWidth;
            }
        }
        return widgetItemsTable;
    }
}
