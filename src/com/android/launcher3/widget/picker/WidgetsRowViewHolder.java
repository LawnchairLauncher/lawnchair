/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.view.View;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.launcher3.R;

/** A {@link ViewHolder} for showing widgets of an app in the full widget picker. */
public final class WidgetsRowViewHolder extends ViewHolder {

    public final WidgetsListTableView mTableContainer;

    public WidgetsRowViewHolder(View v) {
        super(v);

        mTableContainer = v.findViewById(R.id.widgets_table);
    }
}
