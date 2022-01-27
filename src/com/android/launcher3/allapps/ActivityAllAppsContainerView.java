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
package com.android.launcher3.allapps;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.PackageManagerHelper;

import java.util.Objects;

/**
 * All apps container view with search support for use in a dragging activity.
 *
 * @param <T> Type of context inflating all apps.
 */
public class ActivityAllAppsContainerView<T extends BaseDraggingActivity> extends
        BaseAllAppsContainerView<T> {

    protected SearchUiManager mSearchUiManager;
    /**
     * View that defines the search box. Result is rendered inside the recycler view defined in the
     * base class.
     */
    private View mSearchContainer;
    /** {@code true} when rendered view is in search state instead of the scroll state. */
    private boolean mIsSearching;

    public ActivityAllAppsContainerView(Context context) {
        this(context, null);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext.addOnDeviceProfileChangeListener(this);
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    public View getSearchView() {
        return mSearchContainer;
    }

    /** Updates all apps container with the latest search query. */
    public void setLastSearchQuery(String query) {
        Intent marketSearchIntent = PackageManagerHelper.getMarketSearchIntent(
                mActivityContext, query);
        OnClickListener marketSearchClickListener = (v) -> mActivityContext.startActivitySafely(v,
                marketSearchIntent, null);
        for (int i = 0; i < mAH.size(); i++) {
            mAH.get(i).adapter.setLastSearchQuery(query, marketSearchClickListener);
        }
        mIsSearching = true;
        rebindAdapters();
        mHeader.setCollapsed(true);
    }

    /** Invoke when the current search session is finished. */
    public void onClearSearchResult() {
        mIsSearching = false;
        mHeader.setCollapsed(false);
        rebindAdapters();
        mHeader.reset(false);
    }

    /** Invoke when the search results change. */
    public void onSearchResultsChanged() {
        for (int i = 0; i < mAH.size(); i++) {
            if (mAH.get(i).mRecyclerView != null) {
                mAH.get(i).mRecyclerView.onSearchResultsChanged();
            }
        }
    }

    /** Handles selection on focused view and returns {@code true} on success. */
    public boolean launchHighlightedItem() {
        return getMainAdapterProvider().launchHighlightedItem();
    }

    @Override
    protected SearchAdapterProvider<?> createMainAdapterProvider() {
        return mActivityContext.createSearchAdapterProvider(this);
    }

    @Override
    public boolean shouldContainerScroll(MotionEvent ev) {
        // IF the MotionEvent is inside the search box, and the container keeps on receiving
        // touch input, container should move down.
        if (mActivityContext.getDragLayer().isEventOverView(mSearchContainer, ev)) {
            return true;
        }
        return super.shouldContainerScroll(ev);
    }

    @Override
    public void reset(boolean animate) {
        super.reset(animate);
        // Reset the search bar after transitioning home.
        mSearchUiManager.resetSearch();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSearchContainer = findViewById(R.id.search_container_all_apps);
        mSearchUiManager = (SearchUiManager) mSearchContainer;
        mSearchUiManager.initializeSearch(this);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        mSearchUiManager.preDispatchKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public String getDescription() {
        if (!mUsingTabs && mIsSearching) {
            return getContext().getString(R.string.all_apps_search_results);
        } else {
            return super.getDescription();
        }
    }

    @Override
    protected boolean showTabs() {
        return super.showTabs() && !mIsSearching;
    }

    @Override
    protected void rebindAdapters(boolean force) {
        super.rebindAdapters(force);
        if (!FeatureFlags.ENABLE_DEVICE_SEARCH.get()
                || getMainAdapterProvider().getDecorator() == null) {
            return;
        }

        RecyclerView.ItemDecoration decoration = getMainAdapterProvider().getDecorator();
        mAH.stream()
                .map(adapterHolder -> adapterHolder.mRecyclerView)
                .filter(Objects::nonNull)
                .forEach(v -> {
                    v.removeItemDecoration(decoration); // Remove in case it is already added.
                    v.addItemDecoration(decoration);
                });
    }

    @Override
    protected void updateHeaderScroll(int scrolledOffset) {
        super.updateHeaderScroll(scrolledOffset);
        if (mSearchUiManager.getEditText() == null) {
            return;
        }

        float prog = Utilities.boundToRange((float) scrolledOffset / mHeaderThreshold, 0f, 1f);
        boolean bgVisible = mSearchUiManager.getBackgroundVisibility();
        if (scrolledOffset == 0 && !mIsSearching) {
            bgVisible = true;
        } else if (scrolledOffset > mHeaderThreshold) {
            bgVisible = false;
        }
        mSearchUiManager.setBackgroundVisibility(bgVisible, 1 - prog);
    }

    @Override
    protected int getHeaderColor(float blendRatio) {
        return ColorUtils.setAlphaComponent(
                super.getHeaderColor(blendRatio),
                (int) (mSearchContainer.getAlpha() * 255));
    }

    @Override
    protected int getHeaderBottom() {
        return super.getHeaderBottom() + mSearchContainer.getBottom();
    }
}
