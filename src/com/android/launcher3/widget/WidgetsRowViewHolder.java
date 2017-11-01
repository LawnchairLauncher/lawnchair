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
package com.android.launcher3.widget;

import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;

public class WidgetsRowViewHolder extends ViewHolder {

    public final ViewGroup cellContainer;
    public final BubbleTextView title;

    public WidgetsRowViewHolder(ViewGroup v) {
        super(v);

        cellContainer = (ViewGroup) v.findViewById(R.id.widgets_cell_list);
        title = (BubbleTextView) v.findViewById(R.id.section);
    }
}
