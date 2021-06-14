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

import android.net.Uri;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.BaseAdapterProvider;

/**
 * A UI expansion wrapper providing for search results
 */
public abstract class SearchAdapterProvider extends BaseAdapterProvider {

    protected final BaseDraggingActivity mLauncher;

    public SearchAdapterProvider(BaseDraggingActivity launcher, AllAppsContainerView appsView) {
        mLauncher = launcher;
    }

    /**
     * Called from LiveSearchManager to notify slice status updates.
     */
    public void onSliceStatusUpdate(Uri sliceUri) {
    }

    /**
     * Handles selection event on search adapter item. Returns false if provider can not handle
     * event
     */
    public abstract boolean launchHighlightedItem();

    /**
     * Returns the current highlighted view
     */
    public abstract View getHighlightedItem();

    /**
     * Returns the item decorator.
     */
    public abstract RecyclerView.ItemDecoration getDecorator();
}
