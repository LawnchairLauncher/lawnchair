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
     * Groups widgets items into a 2D array which matches their appearance in a UI table.
     *
     * <p>Grouping:
     * 1. Widgets and shortcuts never group together in the same row.
     * 2. The ordered widgets are grouped together in the same row until their total horizontal
     *    spans exceed the {@code maxSpansPerRow} - 1.
     * 3. The order shortcuts are grouped together in the same row until their total horizontal
     *    spans exceed the {@code maxSpansPerRow} - 1.
     * 4. If there is only one widget in a row, its width may exceed the {@code maxSpansPerRow}.
     *
     * <p>Let's say the {@code maxSpansPerRow} is set to 6. Widgets can be grouped in the same row
     * if their total horizontal spans added don't exceed 5.
     * Example 1: Row 1: 2x2, 2x3, 1x1. Total horizontal spans is 5. This is okay.
     * Example 2: Row 1: 2x2, 4x3, 1x1. the total horizontal spans is 7. This is wrong. 4x3 and 1x1
     * should be moved to a new row.
     * Example 3: Row 1: 6x4. This is okay because this is the only item in the row.
     */
    public static List<ArrayList<WidgetItem>> groupWidgetItemsIntoTable(
            List<WidgetItem> widgetItems, final int maxSpansPerRow) {
        List<WidgetItem> sortedWidgetItems = widgetItems.stream().sorted(WIDGET_SHORTCUT_COMPARATOR)
                .collect(Collectors.toList());
        List<ArrayList<WidgetItem>> widgetItemsTable = new ArrayList<>();
        ArrayList<WidgetItem> widgetItemsAtRow = null;
        for (WidgetItem widgetItem : sortedWidgetItems) {
            if (widgetItemsAtRow == null) {
                widgetItemsAtRow = new ArrayList<>();
                widgetItemsTable.add(widgetItemsAtRow);
            }
            int numOfWidgetItems = widgetItemsAtRow.size();
            int totalHorizontalSpan = widgetItemsAtRow.stream().map(item -> item.spanX)
                    .reduce(/* default= */ 0, Integer::sum);
            int totalHorizontalSpanAfterAddingWidget = widgetItem.spanX + totalHorizontalSpan;
            if (numOfWidgetItems == 0) {
                widgetItemsAtRow.add(widgetItem);
            } else if (
                    // The max spans per row is reduced by 1 to ensure we don't pack too many
                    // 1xn widgets on the same row, which may reduce the space for rendering a
                    // widget's description.
                    totalHorizontalSpanAfterAddingWidget <= maxSpansPerRow - 1
                            && widgetItem.hasSameType(widgetItemsAtRow.get(numOfWidgetItems - 1))) {
                // Group items in the same row if
                // 1. they are with the same type, i.e. a row can only have widgets or shortcuts but
                //    never a mix of both.
                // 2. the total number of horizontal spans are smaller than or equal to
                //    MAX_SPAN_PER_ROW. If an item has a horizontal span > MAX_SPAN_PER_ROW, we just
                //    place it in its own row regardless of the horizontal span limit.
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
