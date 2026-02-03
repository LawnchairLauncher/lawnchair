/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.widget.model.WidgetsListExpandActionEntry;

import java.util.List;

/**
 * Creates and populates views for the {@link WidgetsListExpandActionEntry}.
 */
public class WidgetsListExpandActionViewHolderBinder implements
        ViewHolderBinder<WidgetsListExpandActionEntry, RecyclerView.ViewHolder> {
    @NonNull
    View.OnClickListener mExpandListClickListener;
    private final LayoutInflater mLayoutInflater;

    public WidgetsListExpandActionViewHolderBinder(
            @NonNull LayoutInflater layoutInflater,
            @NonNull View.OnClickListener expandListClickListener) {
        mLayoutInflater = layoutInflater;
        mExpandListClickListener = expandListClickListener;
    }

    @Override
    public RecyclerView.ViewHolder newViewHolder(ViewGroup parent) {
        return new RecyclerView.ViewHolder(mLayoutInflater.inflate(
                R.layout.widgets_list_expand_button, parent, false)) {
        };
    }

    @Override
    public void bindViewHolder(RecyclerView.ViewHolder viewHolder,
            WidgetsListExpandActionEntry data, int position, List<Object> payloads) {
        viewHolder.itemView.setOnClickListener(mExpandListClickListener);
    }
}
