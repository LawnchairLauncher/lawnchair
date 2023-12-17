/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

/** Initializes the search box and its interactions with All Apps. */
public class AllAppsSearchUiDelegate {

    protected final ActivityAllAppsContainerView<?> mAppsView;
    protected final ActivityContext mActivityContext;

    public AllAppsSearchUiDelegate(ActivityAllAppsContainerView<?> appsView) {
        mAppsView = appsView;
        mActivityContext = ActivityContext.lookupContext(mAppsView.getContext());
    }

    /** Invoked when an All Apps {@link RecyclerView} is initialized. */
    public void onInitializeRecyclerView(RecyclerView rv) {
        // Do nothing.
    }

    /** Invoked when search results are updated in All Apps. */
    public void onSearchResultsChanged(List<AdapterItem> results, int searchResultCode) {
        // Do nothing.
    }

    /** Invoked when transition animations to go to search is completed . */
    public void onAnimateToSearchStateCompleted() {
        // Do nothing
    }

    /** Invoked when the search bar has been added to All Apps. */
    public void onInitializeSearchBar() {
        // Do nothing.
    }

    /** Invoked when the search bar has been removed from All Apps. */
    public void onDestroySearchBar() {
        // Do nothing.
    }

    /** The layout inflater for All Apps and search UI. */
    public LayoutInflater getLayoutInflater() {
        return LayoutInflater.from(mAppsView.getContext());
    }

    /** Inflate the search bar for All Apps. */
    public View inflateSearchBar() {
        return getLayoutInflater().inflate(R.layout.search_container_all_apps, mAppsView, false);
    }

    /** Whether the search box is floating above the apps surface (inset by the IME). */
    public boolean isSearchBarFloating() {
        return false;
    }

    /** Creates the adapter provider for the main section. */
    public SearchAdapterProvider<?> createMainAdapterProvider() {
        return new DefaultSearchAdapterProvider(mActivityContext);
    }
}
