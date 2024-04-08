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

import static com.android.launcher3.Flags.enableCategorizedWidgetSuggestions;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION;
import static com.android.launcher3.widget.util.WidgetSizes.getWidgetSizePx;
import static com.android.launcher3.widget.util.WidgetsTableUtils.WIDGETS_TABLE_ROW_SIZE_COMPARATOR;

import static java.lang.Math.max;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.Nullable;
import androidx.annotation.Px;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.picker.util.WidgetPreviewContainerSize;

import java.util.ArrayList;
import java.util.List;

/** A {@link TableLayout} for showing recommended widgets. */
public final class WidgetsRecommendationTableLayout extends TableLayout {
    private final float mWidgetsRecommendationTableVerticalPadding;
    private final float mWidgetCellVerticalPadding;
    private final float mWidgetCellTextViewsHeight;

    @Nullable private OnLongClickListener mWidgetCellOnLongClickListener;
    @Nullable private OnClickListener mWidgetCellOnClickListener;

    public WidgetsRecommendationTableLayout(Context context) {
        this(context, /* attrs= */ null);
    }

    public WidgetsRecommendationTableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        // There are 1 row for title, 1 row for dimension and 2 rows for description.
        mWidgetsRecommendationTableVerticalPadding = 2 * getResources()
                .getDimensionPixelSize(R.dimen.widget_recommendations_table_vertical_padding);
        mWidgetCellVerticalPadding = 2 * getResources()
                .getDimensionPixelSize(R.dimen.widget_cell_vertical_padding);
        mWidgetCellTextViewsHeight = 4 * getResources().getDimension(R.dimen.widget_cell_font_size);
    }

    /** Sets a {@link android.view.View.OnLongClickListener} for all widget cells in this table. */
    public void setWidgetCellLongClickListener(OnLongClickListener onLongClickListener) {
        mWidgetCellOnLongClickListener = onLongClickListener;
    }

    /** Sets a {@link android.view.View.OnClickListener} for all widget cells in this table. */
    public void setWidgetCellOnClickListener(OnClickListener widgetCellOnClickListener) {
        mWidgetCellOnClickListener = widgetCellOnClickListener;
    }

    /**
     * Sets a list of recommended widgets that would like to be displayed in this table within the
     * desired {@code recommendationTableMaxHeight}.
     *
     * <p>If the content can't fit {@code recommendationTableMaxHeight}, this view will remove a
     * last row from the {@code recommendedWidgets} until it fits or only one row left.
     *
     * <p>Returns the list of widgets that could fit</p>
     */
    public List<ArrayList<WidgetItem>> setRecommendedWidgets(
            List<ArrayList<WidgetItem>> recommendedWidgets,
            DeviceProfile deviceProfile, float recommendationTableMaxHeight) {
        List<ArrayList<WidgetItem>> rows = selectRowsThatFitInAvailableHeight(recommendedWidgets,
                recommendationTableMaxHeight, deviceProfile);
        bindData(rows);
        return rows;
    }

    private void bindData(List<ArrayList<WidgetItem>> recommendationTable) {
        if (recommendationTable.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        removeAllViews();

        for (int i = 0; i < recommendationTable.size(); i++) {
            List<WidgetItem> widgetItems = recommendationTable.get(i);
            TableRow tableRow = new TableRow(getContext());
            tableRow.setGravity(Gravity.TOP);
            for (WidgetItem widgetItem : widgetItems) {
                WidgetCell widgetCell = addItemCell(tableRow);
                widgetCell.applyFromCellItem(widgetItem);
                widgetCell.showAppIconInWidgetTitle(true);
                if (enableCategorizedWidgetSuggestions()) {
                    widgetCell.showDescription(false);
                    widgetCell.showDimensions(false);
                }
            }
            addView(tableRow);
        }
        setVisibility(VISIBLE);
    }

    private WidgetCell addItemCell(ViewGroup parent) {
        WidgetCell widget = (WidgetCell) LayoutInflater.from(
                getContext()).inflate(R.layout.widget_cell, parent, false);

        View previewContainer = widget.findViewById(R.id.widget_preview_container);
        previewContainer.setOnClickListener(mWidgetCellOnClickListener);
        previewContainer.setOnLongClickListener(mWidgetCellOnLongClickListener);
        widget.setAnimatePreview(false);
        widget.setSourceContainer(CONTAINER_WIDGETS_PREDICTION);

        parent.addView(widget);
        return widget;
    }

    private List<ArrayList<WidgetItem>> selectRowsThatFitInAvailableHeight(
            List<ArrayList<WidgetItem>> recommendedWidgets, @Px float recommendationTableMaxHeight,
            DeviceProfile deviceProfile) {
        List<ArrayList<WidgetItem>> filteredRows = new ArrayList<>();
        // A naive estimation of the widgets recommendation table height without inflation.
        float totalHeight = mWidgetsRecommendationTableVerticalPadding;

        for (int i = 0; i < recommendedWidgets.size(); i++) {
            List<WidgetItem> widgetItems = recommendedWidgets.get(i);
            float rowHeight = 0;
            for (int j = 0; j < widgetItems.size(); j++) {
                WidgetItem widgetItem = widgetItems.get(j);
                WidgetPreviewContainerSize previewContainerSize =
                        WidgetPreviewContainerSize.Companion.forItem(widgetItem, deviceProfile);
                float widgetItemHeight = getWidgetSizePx(deviceProfile, previewContainerSize.spanX,
                        previewContainerSize.spanY).getHeight();
                rowHeight = max(rowHeight,
                        widgetItemHeight + mWidgetCellTextViewsHeight + mWidgetCellVerticalPadding);
            }
            if (totalHeight + rowHeight <= recommendationTableMaxHeight) {
                totalHeight += rowHeight;
                filteredRows.add(new ArrayList<>(widgetItems));
            }
        }

        // Perform re-ordering once we have filtered out recommendations that fit.
        return filteredRows.stream().sorted(WIDGETS_TABLE_ROW_SIZE_COMPARATOR).toList();
    }
}
