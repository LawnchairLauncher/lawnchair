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

import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.views.StickyHeaderLayout.EmptySpaceView;
import com.android.launcher3.widget.model.WidgetListSpaceEntry;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * {@link ViewHolderBinder} for binding the top empty space
 */
public class WidgetsSpaceViewHolderBinder
        implements ViewHolderBinder<WidgetListSpaceEntry, ViewHolder> {

    private final IntSupplier mEmptySpaceHeightProvider;

    public WidgetsSpaceViewHolderBinder(IntSupplier emptySpaceHeightProvider) {
        mEmptySpaceHeightProvider = emptySpaceHeightProvider;
    }

    @Override
    public ViewHolder newViewHolder(ViewGroup parent) {
        return new ViewHolder(new EmptySpaceView(parent.getContext())) { };
    }

    @Override
    public void bindViewHolder(ViewHolder holder, WidgetListSpaceEntry data,
            @ListPosition int position, List<Object> payloads) {
        ((EmptySpaceView) holder.itemView).setFixedHeight(mEmptySpaceHeightProvider.getAsInt());
    }
}
