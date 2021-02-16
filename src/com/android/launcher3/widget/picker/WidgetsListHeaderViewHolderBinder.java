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

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.launcher3.R;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;

/**
 * Binds data from {@link WidgetsListHeaderEntry} to UI elements in {@link WidgetsListHeaderHolder}.
 */
public final class WidgetsListHeaderViewHolderBinder implements
        ViewHolderBinder<WidgetsListHeaderEntry, WidgetsListHeaderHolder> {
    private final LayoutInflater mLayoutInflater;
    private final OnHeaderClickListener mOnHeaderClickListener;

    public WidgetsListHeaderViewHolderBinder(LayoutInflater layoutInflater,
            OnHeaderClickListener onHeaderClickListener) {
        mLayoutInflater = layoutInflater;
        mOnHeaderClickListener = onHeaderClickListener;
    }

    @Override
    public WidgetsListHeaderHolder newViewHolder(ViewGroup parent) {
        WidgetsListHeader header = (WidgetsListHeader) mLayoutInflater.inflate(
                R.layout.widgets_list_row_header, parent, false);

        return new WidgetsListHeaderHolder(header);
    }

    @Override
    public void bindViewHolder(WidgetsListHeaderHolder viewHolder, WidgetsListHeaderEntry data) {
        WidgetsListHeader widgetsListHeader = viewHolder.mWidgetsListHeader;
        widgetsListHeader.applyFromItemInfoWithIcon(data);
        widgetsListHeader.setExpanded(data.isWidgetListShown());
        widgetsListHeader.setOnExpandChangeListener(isExpanded ->
                mOnHeaderClickListener.onHeaderClicked(isExpanded, data.mPkgItem.packageName));
    }

    /** A listener to be invoked when {@link WidgetsListHeader} is clicked. */
    public interface OnHeaderClickListener {
        /** Calls when {@link WidgetsListHeader} is clicked to show / hide widgets for a package. */
        void onHeaderClicked(boolean showWidgets, String packageName);
    }
}
