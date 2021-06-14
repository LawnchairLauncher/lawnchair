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

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.widget.picker.SearchAndRecommendationsScrollController.OnContentChangeListener;

/**
 * A layout manager for the {@link WidgetsRecyclerView}.
 *
 * {@link #setOnContentChangeListener(OnContentChangeListener)} can be used to register a callback
 * for when the content of the layout manager has changed, following measurement and animation.
 */
public final class WidgetsListLayoutManager extends LinearLayoutManager {
    @Nullable
    private OnContentChangeListener mOnContentChangeListener;

    public WidgetsListLayoutManager(Context context) {
        super(context);
    }

    @Override
    public void onLayoutCompleted(RecyclerView.State state) {
        super.onLayoutCompleted(state);
        if (mOnContentChangeListener != null) {
            mOnContentChangeListener.onContentChanged();
        }
    }

    public void setOnContentChangeListener(@Nullable OnContentChangeListener listener) {
        mOnContentChangeListener = listener;
    }
}
