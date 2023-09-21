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

import java.util.List;

/**
 * Binds data from {@link WidgetsListHeaderEntry} to UI elements in {@link WidgetsListHeaderHolder}.
 */
public final class WidgetsListHeaderViewHolderBinder implements
        ViewHolderBinder<WidgetsListHeaderEntry, WidgetsListHeaderHolder> {
    private final LayoutInflater mLayoutInflater;
    private final OnHeaderClickListener mOnHeaderClickListener;
    private final boolean mIsTwoPane;

    public WidgetsListHeaderViewHolderBinder(LayoutInflater layoutInflater,
            OnHeaderClickListener onHeaderClickListener, boolean isTwoPane) {
        mLayoutInflater = layoutInflater;
        mOnHeaderClickListener = onHeaderClickListener;
        mIsTwoPane = isTwoPane;
    }

    @Override
    public WidgetsListHeaderHolder newViewHolder(ViewGroup parent) {
        return new WidgetsListHeaderHolder((WidgetsListHeader) mLayoutInflater.inflate(
                mIsTwoPane
                        ? R.layout.widgets_list_row_header_two_pane
                        : R.layout.widgets_list_row_header,
                parent,
                false));
    }

    @Override
    public void bindViewHolder(WidgetsListHeaderHolder viewHolder, WidgetsListHeaderEntry data,
            @ListPosition int position, List<Object> payloads) {
        WidgetsListHeader widgetsListHeader = viewHolder.mWidgetsListHeader;
        widgetsListHeader.applyFromItemInfoWithIcon(data);
        widgetsListHeader.setExpanded(data.isWidgetListShown());
        widgetsListHeader.setListDrawableState(
                WidgetsListDrawableState.obtain(
                        (position & POSITION_FIRST) != 0,
                        (position & POSITION_LAST) != 0));
        widgetsListHeader.setOnClickListener(view -> {
            widgetsListHeader.setExpanded(mIsTwoPane || !widgetsListHeader.isExpanded());
            mOnHeaderClickListener.onHeaderClicked(widgetsListHeader.isExpanded(),
                    PackageUserKey.fromPackageItemInfo(data.mPkgItem));
        });
    }
}
