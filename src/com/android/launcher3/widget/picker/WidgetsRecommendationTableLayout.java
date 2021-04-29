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

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.widget.WidgetCell;

import java.util.ArrayList;
import java.util.List;

/** A {@link TableLayout} for showing recommended widgets. */
public final class WidgetsRecommendationTableLayout extends TableLayout {
    private static final String TAG = "WidgetsRecommendationTableLayout";
    private static final float DOWN_SCALE_RATIO = 0.9f;
    private static final float MAX_DOWN_SCALE_RATIO = 0.5f;
    private final float mWidgetsRecommendationTableVerticalPadding;
    private final float mWidgetCellTextViewsHeight;
    private final float mWidgetPreviewPadding;

    private float mRecommendationTableMaxHeight = Float.MAX_VALUE;
    @Nullable private OnLongClickListener mWidgetCellOnLongClickListener;
    @Nullable private OnClickListener mWidgetCellOnClickListener;
    @Nullable private OnTouchListener mWidgetCellOnTouchListener;

    public WidgetsRecommendationTableLayout(Context context) {
        this(context, /* attrs= */ null);
    }

    public WidgetsRecommendationTableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        // There are 1 row for title, 1 row for dimension and 2 rows for description.
        mWidgetsRecommendationTableVerticalPadding = 2 * getResources()
                .getDimensionPixelSize(R.dimen.widget_cell_vertical_padding);
        mWidgetCellTextViewsHeight = 4 * getResources().getDimension(R.dimen.widget_cell_font_size);
        mWidgetPreviewPadding = 2 * getResources()
                .getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);
    }

    /** Sets a {@link android.view.View.OnLongClickListener} for all widget cells in this table. */
    public void setWidgetCellLongClickListener(OnLongClickListener onLongClickListener) {
        mWidgetCellOnLongClickListener = onLongClickListener;
    }

    /** Sets a {@link android.view.View.OnClickListener} for all widget cells in this table. */
    public void setWidgetCellOnClickListener(OnClickListener widgetCellOnClickListener) {
        mWidgetCellOnClickListener = widgetCellOnClickListener;
    }

    /** Sets a {@link android.view.View.OnTouchListener} for all widget cells in this table. */
    public void setWidgetCellOnTouchListener(OnTouchListener widgetCellOnTouchListener) {
        mWidgetCellOnTouchListener = widgetCellOnTouchListener;
    }

    /**
     * Sets a list of recommended widgets that would like to be displayed in this table within the
     * desired {@code recommendationTableMaxHeight}.
     *
     * <p>If the content can't fit {@code recommendationTableMaxHeight}, this view will remove a
     * last row from the {@code recommendedWidgets} until it fits or only one row left. If the only
     * row still doesn't fit, we scale down the preview image.
     */
    public void setRecommendedWidgets(List<ArrayList<WidgetItem>> recommendedWidgets,
            float recommendationTableMaxHeight) {
        mRecommendationTableMaxHeight = recommendationTableMaxHeight;
        RecommendationTableData data = fitRecommendedWidgetsToTableSpace(/* previewScale= */ 1f,
                recommendedWidgets);
        bindData(data);
    }

    private void bindData(RecommendationTableData data) {
        if (data.mRecommendationTable.size() == 0) {
            setVisibility(GONE);
            return;
        }

        removeAllViews();

        for (int i = 0; i < data.mRecommendationTable.size(); i++) {
            List<WidgetItem> widgetItems = data.mRecommendationTable.get(i);
            TableRow tableRow = new TableRow(getContext());
            tableRow.setGravity(Gravity.TOP);

            for (WidgetItem widgetItem : widgetItems) {
                WidgetCell widgetCell = addItemCell(tableRow);
                widgetCell.setPreviewSize(widgetItem.spanX, widgetItem.spanY, data.mPreviewScale);
                widgetCell.applyFromCellItem(widgetItem,
                        LauncherAppState.getInstance(getContext()).getWidgetCache());
                widgetCell.ensurePreview();
            }
            addView(tableRow);
        }
        setVisibility(VISIBLE);
    }

    private WidgetCell addItemCell(ViewGroup parent) {
        WidgetCell widget = (WidgetCell) LayoutInflater.from(
                getContext()).inflate(R.layout.widget_cell, parent, false);

        widget.setOnTouchListener(mWidgetCellOnTouchListener);
        View previewContainer = widget.findViewById(R.id.widget_preview_container);
        previewContainer.setOnClickListener(mWidgetCellOnClickListener);
        previewContainer.setOnLongClickListener(mWidgetCellOnLongClickListener);
        widget.setAnimatePreview(false);

        parent.addView(widget);
        return widget;
    }

    private RecommendationTableData fitRecommendedWidgetsToTableSpace(
            float previewScale,
            List<ArrayList<WidgetItem>> recommendedWidgetsInTable) {
        if (previewScale < MAX_DOWN_SCALE_RATIO) {
            Log.w(TAG, "Hide recommended widgets. Can't down scale previews to " + previewScale);
            return new RecommendationTableData(List.of(), previewScale);
        }
        // A naive estimation of the widgets recommendation table height without inflation.
        float totalHeight = mWidgetsRecommendationTableVerticalPadding;
        DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
        for (int i = 0; i < recommendedWidgetsInTable.size(); i++) {
            List<WidgetItem> widgetItems = recommendedWidgetsInTable.get(i);
            float rowHeight = 0;
            for (int j = 0; j < widgetItems.size(); j++) {
                float previewHeight = widgetItems.get(j).spanY * deviceProfile.cellHeightPx
                        * previewScale + mWidgetPreviewPadding;
                rowHeight = Math.max(rowHeight, previewHeight + mWidgetCellTextViewsHeight);
            }
            totalHeight += rowHeight;
        }

        if (totalHeight < mRecommendationTableMaxHeight) {
            return new RecommendationTableData(recommendedWidgetsInTable, previewScale);
        }

        if (recommendedWidgetsInTable.size() > 1) {
            // We don't want to scale down widgets preview unless we really need to. Reduce the
            // num of row by 1 to see if it fits.
            return fitRecommendedWidgetsToTableSpace(
                    previewScale,
                    recommendedWidgetsInTable.subList(/* fromIndex= */0,
                            /* toIndex= */recommendedWidgetsInTable.size() - 1));
        }

        float nextPreviewScale = previewScale * DOWN_SCALE_RATIO;
        return fitRecommendedWidgetsToTableSpace(nextPreviewScale, recommendedWidgetsInTable);
    }

    /** Data class for the widgets recommendation table and widgets preview scaling. */
    private class RecommendationTableData {
        private final List<ArrayList<WidgetItem>> mRecommendationTable;
        private final float mPreviewScale;

        RecommendationTableData(List<ArrayList<WidgetItem>> recommendationTable,
                float previewScale) {
            mRecommendationTable = recommendationTable;
            mPreviewScale = previewScale;
        }
    }
}
