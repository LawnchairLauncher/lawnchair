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
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.model.WidgetsListSearchHeaderEntry;

/**
 * Binds data from {@link WidgetsListHeaderEntry} to UI elements in {@link WidgetsListHeaderHolder}.
 */
public final class WidgetsListSearchHeaderViewHolderBinder implements
        ViewHolderBinder<WidgetsListSearchHeaderEntry, WidgetsListSearchHeaderHolder> {
    private final LayoutInflater mLayoutInflater;
    private final OnHeaderClickListener mOnHeaderClickListener;
    private final WidgetsListDrawableFactory mListDrawableFactory;
    private final WidgetsListAdapter mWidgetsListAdapter;

    public WidgetsListSearchHeaderViewHolderBinder(LayoutInflater layoutInflater,
            OnHeaderClickListener onHeaderClickListener,
            WidgetsListDrawableFactory listDrawableFactory,
            WidgetsListAdapter listAdapter) {
        mLayoutInflater = layoutInflater;
        mOnHeaderClickListener = onHeaderClickListener;
        mListDrawableFactory = listDrawableFactory;
        mWidgetsListAdapter = listAdapter;
    }

    @Override
    public WidgetsListSearchHeaderHolder newViewHolder(ViewGroup parent) {
        WidgetsListHeader header = (WidgetsListHeader) mLayoutInflater.inflate(
                R.layout.widgets_list_row_header, parent, false);
        header.setBackground(mListDrawableFactory.createHeaderBackgroundDrawable());
        return new WidgetsListSearchHeaderHolder(header);
    }

    @Override
    public void bindViewHolder(WidgetsListSearchHeaderHolder viewHolder,
            WidgetsListSearchHeaderEntry data, int position) {
        WidgetsListHeader widgetsListHeader = viewHolder.mWidgetsListHeader;
        widgetsListHeader.applyFromItemInfoWithIcon(data);
        widgetsListHeader.setExpanded(data.isWidgetListShown());
        widgetsListHeader.setListDrawableState(
                WidgetsListDrawableState.obtain(
                        /* isFirst= */ position == 0,
                        /* isLast= */ position == mWidgetsListAdapter.getItemCount() - 1,
                        /* isExpanded= */ data.isWidgetListShown()));
        widgetsListHeader.setOnExpandChangeListener(isExpanded ->
                mOnHeaderClickListener.onHeaderClicked(isExpanded,
                        new PackageUserKey(data.mPkgItem.packageName, data.mPkgItem.user)));
    }
}
