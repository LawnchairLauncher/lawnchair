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
package com.android.launcher3.allapps.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.allapps.AllAppsGridAdapter;

/**
 * Provides views for local search results
 */
public class DefaultSearchAdapterProvider extends SearchAdapterProvider {

    public DefaultSearchAdapterProvider(BaseDraggingActivity launcher) {
        super(launcher);
    }

    @Override
    public void onBindView(AllAppsGridAdapter.ViewHolder holder, int position) {

    }

    @Override
    public boolean isSearchView(int viewType) {
        return false;
    }

    @Override
    public AllAppsGridAdapter.ViewHolder onCreateViewHolder(LayoutInflater layoutInflater,
            ViewGroup parent, int viewType) {
        return null;
    }

    @Override
    public boolean onAdapterItemSelected(AllAppsGridAdapter.AdapterItem focusedItem) {
        return false;
    }
}
