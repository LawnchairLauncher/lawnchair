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
package com.android.launcher3.search;

import static com.android.launcher3.LauncherState.ALL_APPS;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.SearchAlgorithm;

import java.util.ArrayList;

/**
 * A search view shown in all apps for on device search
 */
public class FallbackSearchInputView extends ExtendedEditText
        implements AllAppsSearchBarController.Callbacks, AllAppsStore.OnUpdateListener {

    private final AllAppsSearchBarController mSearchBarController;

    private AlphabeticalAppsList mApps;
    private Runnable mOnResultsChanged;
    private AllAppsContainerView mAppsView;

    public FallbackSearchInputView(Context context) {
        this(context, null);
    }

    public FallbackSearchInputView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FallbackSearchInputView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSearchBarController = new AllAppsSearchBarController();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Launcher.getLauncher(getContext()).getAppsView().getAppsStore().addUpdateListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Launcher.getLauncher(getContext()).getAppsView().getAppsStore().removeUpdateListener(this);
    }

    /**
     * Initializes SearchInput
     */
    public void initialize(AllAppsContainerView appsView, SearchAlgorithm algo, Runnable changed) {
        mOnResultsChanged = changed;
        mApps = appsView.getApps();
        mAppsView = appsView;
        mSearchBarController.initialize(algo, this, Launcher.getLauncher(getContext()), this);
    }

    @Override
    public void onSearchResult(String query, ArrayList<AdapterItem> items) {
        if (mApps != null && getParent() != null) {
            mApps.setSearchResults(items);
            notifyResultChanged();
            collapseAppsViewHeader(true);
            mAppsView.setLastSearchQuery(query);
        }
    }

    @Override
    public void onAppendSearchResult(String query, ArrayList<AdapterItem> items) {
        if (mApps != null && getParent() != null) {
            mApps.appendSearchResults(items);
            notifyResultChanged();
        }
    }

    @Override
    public void clearSearchResult() {
        if (getParent() != null && mApps != null) {
            mApps.setSearchResults(null);
            notifyResultChanged();
            collapseAppsViewHeader(false);
            mAppsView.onClearSearchResult();
        }
    }

    @Override
    public void onAppsUpdated() {
        mSearchBarController.refreshSearchResult();
    }

    private void collapseAppsViewHeader(boolean collapse) {
        FloatingHeaderView header = mAppsView.getFloatingHeaderView();
        if (header != null) {
            header.setCollapsed(collapse);
        }
    }

    private void notifyResultChanged() {
        if (mOnResultsChanged != null) {
            mOnResultsChanged.run();
        }
        mAppsView.onSearchResultsChanged();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        // TODO: Consider animating the state transition here
        if (focused) {
            // Getting focus will open the keyboard. Go to the all-apps state, so that the input
            // box is at the top and there is enough space below to show search results.
            Launcher.getLauncher(getContext()).getStateManager().goToState(ALL_APPS, false);
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }
}
