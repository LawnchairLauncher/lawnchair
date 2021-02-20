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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;

import com.android.launcher3.R;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.dragndrop.LivePreviewWidgetCell;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.model.WidgetsListContentEntry;

import java.util.List;

/**
 * Binds data from {@link WidgetsListContentEntry} to UI elements in {@link WidgetsRowViewHolder}.
 */
public class WidgetsListRowViewHolderBinder
        implements ViewHolderBinder<WidgetsListContentEntry, WidgetsRowViewHolder> {
    private static final boolean DEBUG = false;
    private static final String TAG = "WidgetsListRowViewHolderBinder";

    private final LayoutInflater mLayoutInflater;
    private final int mIndent;
    private final OnClickListener mIconClickListener;
    private final OnLongClickListener mIconLongClickListener;
    private final WidgetPreviewLoader mWidgetPreviewLoader;
    private boolean mApplyBitmapDeferred = false;

    public WidgetsListRowViewHolderBinder(
            Context context,
            LayoutInflater layoutInflater,
            OnClickListener iconClickListener,
            OnLongClickListener iconLongClickListener,
            WidgetPreviewLoader widgetPreviewLoader) {
        mLayoutInflater = layoutInflater;
        mIndent = context.getResources().getDimensionPixelSize(R.dimen.widget_section_indent);
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
        mWidgetPreviewLoader = widgetPreviewLoader;
    }

    /**
     * Defers applying bitmap on all the {@link WidgetCell} at
     * {@link #bindViewHolder(WidgetsRowViewHolder, WidgetsListContentEntry)} if
     * {@code applyBitmapDeferred} is {@code true}.
     */
    public void setApplyBitmapDeferred(boolean applyBitmapDeferred) {
        mApplyBitmapDeferred = applyBitmapDeferred;
    }

    @Override
    public WidgetsRowViewHolder newViewHolder(ViewGroup parent) {
        if (DEBUG) {
            Log.v(TAG, "\nonCreateViewHolder");
        }

        ViewGroup container = (ViewGroup) mLayoutInflater.inflate(
                R.layout.widgets_scroll_container, parent, false);

        // if the end padding is 0, then container view (horizontal scroll view) doesn't respect
        // the end of the linear layout width + the start padding and doesn't allow scrolling.
        container.findViewById(R.id.widgets_cell_list).setPaddingRelative(mIndent, 0, 1, 0);

        return new WidgetsRowViewHolder(container);
    }

    @Override
    public void bindViewHolder(WidgetsRowViewHolder holder, WidgetsListContentEntry entry) {
        List<WidgetItem> infoList = entry.mWidgets;

        ViewGroup row = holder.cellContainer;
        if (DEBUG) {
            Log.d(TAG, String.format("onBindViewHolder [widget#=%d, row.getChildCount=%d]",
                    infoList.size(), row.getChildCount()));
        }

        // Add more views.
        // if there are too many, hide them.
        int expectedChildCount = infoList.size() + Math.max(0, infoList.size() - 1);
        int childCount = row.getChildCount();

        if (expectedChildCount > childCount) {
            for (int i = childCount; i < expectedChildCount; i++) {
                if ((i & 1) == 1) {
                    // Add a divider for odd index
                    mLayoutInflater.inflate(R.layout.widget_list_divider, row);
                } else {
                    // Add cell for even index
                    LivePreviewWidgetCell widget = (LivePreviewWidgetCell) mLayoutInflater.inflate(
                            R.layout.live_preview_widget_cell, row, false);

                    // set up touch.
                    widget.setOnClickListener(mIconClickListener);
                    widget.setOnLongClickListener(mIconLongClickListener);
                    row.addView(widget);
                }
            }
        } else if (expectedChildCount < childCount) {
            for (int i = expectedChildCount; i < childCount; i++) {
                row.getChildAt(i).setVisibility(View.GONE);
            }
        }

        // Bind the view in the widget horizontal tray region.
        for (int i = 0; i < infoList.size(); i++) {
            LivePreviewWidgetCell widget = (LivePreviewWidgetCell) row.getChildAt(2 * i);
            widget.reset();
            widget.applyFromCellItem(infoList.get(i), mWidgetPreviewLoader);
            widget.setApplyBitmapDeferred(mApplyBitmapDeferred);
            widget.ensurePreview();
            widget.setVisibility(View.VISIBLE);

            if (i > 0) {
                row.getChildAt(2 * i - 1).setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void unbindViewHolder(WidgetsRowViewHolder holder) {
        int total = holder.cellContainer.getChildCount();
        for (int i = 0; i < total; i += 2) {
            WidgetCell widget = (WidgetCell) holder.cellContainer.getChildAt(i);
            widget.clear();
        }
    }
}
