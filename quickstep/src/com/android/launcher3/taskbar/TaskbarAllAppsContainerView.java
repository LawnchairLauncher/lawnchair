/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.BaseAllAppsContainerView;
import com.android.launcher3.allapps.search.SearchAdapterProvider;

/** All apps container accessible from taskbar. */
public class TaskbarAllAppsContainerView extends BaseAllAppsContainerView<TaskbarActivityContext> {
    public TaskbarAllAppsContainerView(Context context) {
        this(context, null);
    }

    public TaskbarAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected SearchAdapterProvider<?> createMainAdapterProvider() {
        // Task bar all apps does not yet support search, so this implementation is minimal.
        return new SearchAdapterProvider<TaskbarActivityContext>(mActivityContext) {
            @Override
            public boolean launchHighlightedItem() {
                return false;
            }

            @Override
            public View getHighlightedItem() {
                return null;
            }

            @Override
            public RecyclerView.ItemDecoration getDecorator() {
                return null;
            }

            @Override
            public boolean isViewSupported(int viewType) {
                return false;
            }

            @Override
            public void onBindView(AllAppsGridAdapter.ViewHolder holder, int position) { }

            @Override
            public AllAppsGridAdapter.ViewHolder onCreateViewHolder(LayoutInflater layoutInflater,
                    ViewGroup parent, int viewType) {
                return null;
            }
        };
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // TODO(b/204696617): Switch to status bar insets once they work.
        setInsets(insets.getInsets(WindowInsets.Type.tappableElement()).toRect());
        return super.onApplyWindowInsets(insets);
    }
}
