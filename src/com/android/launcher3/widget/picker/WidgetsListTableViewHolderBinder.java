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
package com.android.launcher3.widget.picker;

import static com.android.launcher3.widget.picker.WidgetsListDrawableState.LAST;
import static com.android.launcher3.widget.picker.WidgetsListDrawableState.MIDDLE;

import android.graphics.Bitmap;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;

import com.android.launcher3.R;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.util.WidgetsTableUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds data from {@link WidgetsListContentEntry} to UI elements in {@link WidgetsRowViewHolder}.
 */
public final class WidgetsListTableViewHolderBinder
        implements ViewHolderBinder<WidgetsListContentEntry, WidgetsRowViewHolder> {
    private static final boolean DEBUG = false;
    private static final String TAG = "WidgetsListRowViewHolderBinder";

    private final LayoutInflater mLayoutInflater;
    private final OnClickListener mIconClickListener;
    private final OnLongClickListener mIconLongClickListener;
    private final WidgetsListDrawableFactory mListDrawableFactory;

    public WidgetsListTableViewHolderBinder(
            LayoutInflater layoutInflater,
            OnClickListener iconClickListener,
            OnLongClickListener iconLongClickListener,
            WidgetsListDrawableFactory listDrawableFactory) {
        mLayoutInflater = layoutInflater;
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
        mListDrawableFactory = listDrawableFactory;
    }

    @Override
    public WidgetsRowViewHolder newViewHolder(ViewGroup parent) {
        if (DEBUG) {
            Log.v(TAG, "\nonCreateViewHolder");
        }

        WidgetsRowViewHolder viewHolder =
                new WidgetsRowViewHolder(mLayoutInflater.inflate(
                        R.layout.widgets_table_container, parent, false));
        viewHolder.tableContainer.setBackgroundDrawable(
                mListDrawableFactory.createContentBackgroundDrawable());
        return viewHolder;
    }

    @Override
    public void bindViewHolder(WidgetsRowViewHolder holder, WidgetsListContentEntry entry,
            @ListPosition int position, List<Object> payloads) {
        for (Object payload : payloads) {
            Pair<WidgetItem, Bitmap> pair = (Pair) payload;
            holder.previewCache.put(pair.first, pair.second);
        }

        WidgetsListTableView table = holder.tableContainer;
        if (DEBUG) {
            Log.d(TAG, String.format("onBindViewHolder [widget#=%d, table.getChildCount=%d]",
                    entry.mWidgets.size(), table.getChildCount()));
        }
        table.setListDrawableState(((position & POSITION_LAST) != 0) ? LAST : MIDDLE);
        List<ArrayList<WidgetItem>> widgetItemsTable =
                WidgetsTableUtils.groupWidgetItemsIntoTableWithReordering(
                        entry.mWidgets, entry.getMaxSpanSizeInCells());
        recycleTableBeforeBinding(table, widgetItemsTable);

        // Bind the widget items.
        for (int i = 0; i < widgetItemsTable.size(); i++) {
            List<WidgetItem> widgetItemsPerRow = widgetItemsTable.get(i);
            for (int j = 0; j < widgetItemsPerRow.size(); j++) {
                TableRow row = (TableRow) table.getChildAt(i);
                row.setVisibility(View.VISIBLE);
                WidgetCell widget = (WidgetCell) row.getChildAt(j);
                widget.clear();
                WidgetItem widgetItem = widgetItemsPerRow.get(j);
                widget.setVisibility(View.VISIBLE);

                // When preview loads, notify adapter to rebind the item and possibly animate
                widget.applyFromCellItem(widgetItem, 1f,
                        bitmap -> {
                        if (holder.getBindingAdapter() != null) {
                            holder.getBindingAdapter().notifyItemChanged(
                                    holder.getBindingAdapterPosition(),
                                    Pair.create(widgetItem, bitmap));
                            }
                        }, holder.previewCache.get(widgetItem));
            }
        }
    }

    /**
     * Adds and hides table rows and columns from {@code table} to ensure there is sufficient room
     * to display {@code widgetItemsTable}.
     *
     * <p>Instead of recreating all UI elements in {@code table}, this function recycles all
     * existing UI elements. Instead of deleting excessive elements, it hides them.
     */
    private void recycleTableBeforeBinding(TableLayout table,
            List<ArrayList<WidgetItem>> widgetItemsTable) {
        // Hide extra table rows.
        for (int i = widgetItemsTable.size(); i < table.getChildCount(); i++) {
            table.getChildAt(i).setVisibility(View.GONE);
        }

        for (int i = 0; i < widgetItemsTable.size(); i++) {
            List<WidgetItem> widgetItems = widgetItemsTable.get(i);
            TableRow tableRow;
            if (i < table.getChildCount()) {
                tableRow = (TableRow) table.getChildAt(i);
            } else {
                tableRow = new TableRow(table.getContext());
                tableRow.setGravity(Gravity.TOP);
                table.addView(tableRow);
            }
            if (tableRow.getChildCount() > widgetItems.size()) {
                for (int j = widgetItems.size(); j < tableRow.getChildCount(); j++) {
                    tableRow.getChildAt(j).setVisibility(View.GONE);
                }
            } else {
                for (int j = tableRow.getChildCount(); j < widgetItems.size(); j++) {
                    WidgetCell widget = (WidgetCell) mLayoutInflater.inflate(
                            R.layout.widget_cell, tableRow, false);
                    // set up touch.
                    View preview = widget.findViewById(R.id.widget_preview_container);
                    preview.setOnClickListener(mIconClickListener);
                    preview.setOnLongClickListener(mIconLongClickListener);
                    widget.setAnimatePreview(false);
                    tableRow.addView(widget);
                }
            }
        }
    }

    @Override
    public void unbindViewHolder(WidgetsRowViewHolder holder) {
        int numOfRows = holder.tableContainer.getChildCount();
        holder.previewCache.clear();
        for (int i = 0; i < numOfRows; i++) {
            TableRow tableRow = (TableRow) holder.tableContainer.getChildAt(i);
            int numOfCols = tableRow.getChildCount();
            for (int j = 0; j < numOfCols; j++) {
                WidgetCell widget = (WidgetCell) tableRow.getChildAt(j);
                widget.clear();
            }
        }
    }
}
