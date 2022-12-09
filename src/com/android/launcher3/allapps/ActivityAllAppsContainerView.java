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

import static com.android.launcher3.allapps.BaseAllAppsContainerView.AdapterHolder.SEARCH;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile.DeviceProfileListenable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.views.AppLauncher;

import java.util.ArrayList;

/**
 * All apps container view with search support for use in a dragging activity.
 *
 * @param <T> Type of context inflating all apps.
 */
public class ActivityAllAppsContainerView<T extends Context & AppLauncher
        & DeviceProfileListenable> extends BaseAllAppsContainerView<T> {

    private static final long DEFAULT_SEARCH_TRANSITION_DURATION_MS = 300;

    // Used to animate Search results out and A-Z apps in, or vice-versa.
    private final SearchTransitionController mSearchTransitionController;

    protected SearchUiManager mSearchUiManager;
    /**
     * View that defines the search box. Result is rendered inside the recycler view defined in the
     * base class.
     */
    private View mSearchContainer;
    /** {@code true} when rendered view is in search state instead of the scroll state. */
    private boolean mIsSearching;
    private boolean mRebindAdaptersAfterSearchAnimation;

    public ActivityAllAppsContainerView(Context context) {
        this(context, null);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActivityAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mSearchTransitionController = new SearchTransitionController(this);
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    public View getSearchView() {
        return mSearchContainer;
    }

    /** Invoke when the current search session is finished. */
    public void onClearSearchResult() {
        getMainAdapterProvider().clearHighlightedItem();
        animateToSearchState(false);
        rebindAdapters();
    }

    /**
     * Sets results list for search
     */
    public void setSearchResults(ArrayList<AdapterItem> results) {
        getMainAdapterProvider().clearHighlightedItem();
        if (getSearchResultList().setSearchResults(results)) {
            getSearchRecyclerView().onSearchResultsChanged();
        }
        if (results != null) {
            animateToSearchState(true);
        }
    }

    private void animateToSearchState(boolean goingToSearch) {
        animateToSearchState(goingToSearch, DEFAULT_SEARCH_TRANSITION_DURATION_MS);
    }

    private void animateToSearchState(boolean goingToSearch, long durationMs) {
        if (!mSearchTransitionController.isRunning() && goingToSearch == isSearching()) {
            return;
        }
        if (goingToSearch) {
            // Fade out the button to pause work apps.
            mWorkManager.onActivePageChanged(SEARCH);
        }
        mSearchTransitionController.animateToSearchState(goingToSearch, durationMs,
                /* onEndRunnable = */ () -> {
                    mIsSearching = goingToSearch;
                    updateSearchResultsVisibility();
                    int previousPage = getCurrentPage();
                    if (mRebindAdaptersAfterSearchAnimation) {
                        rebindAdapters(false);
                        mRebindAdaptersAfterSearchAnimation = false;
                    }
                    if (!goingToSearch) {
                        setSearchResults(null);
                        if (mViewPager != null) {
                            mViewPager.setCurrentPage(previousPage);
                        }
                        onActivePageChanged(previousPage);
                    }
                });
    }

    @Override
    protected final SearchAdapterProvider<?> createMainAdapterProvider() {
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
        // Animate to A-Z with 0 time to reset the animation with proper state management.
        animateToSearchState(false, 0);
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
        if (!mUsingTabs && isSearching()) {
            return getContext().getString(R.string.all_apps_search_results);
        } else {
            return super.getDescription();
        }
    }

    @Override
    public boolean isSearching() {
        return mIsSearching;
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        if (mSearchTransitionController.isRunning()) {
            // Will be called at the end of the animation.
            return;
        }
        super.onActivePageChanged(currentActivePage);
    }

    @Override
    protected void rebindAdapters(boolean force) {
        if (mSearchTransitionController.isRunning()) {
            mRebindAdaptersAfterSearchAnimation = true;
            return;
        }
        super.rebindAdapters(force);
        if (!FeatureFlags.ENABLE_DEVICE_SEARCH.get()
                || getMainAdapterProvider().getDecorator() == null
                || getSearchRecyclerView() == null) {
            return;
        }

        RecyclerView.ItemDecoration decoration = getMainAdapterProvider().getDecorator();
        getSearchRecyclerView().removeItemDecoration(decoration); // In case it is already added.
        getSearchRecyclerView().addItemDecoration(decoration);
    }

    @Override
    protected View replaceAppsRVContainer(boolean showTabs) {
        View rvContainer = super.replaceAppsRVContainer(showTabs);

        removeCustomRules(rvContainer);
        removeCustomRules(getSearchRecyclerView());
        if (FeatureFlags.ENABLE_FLOATING_SEARCH_BAR.get()) {
            alignParentTop(rvContainer, showTabs);
            alignParentTop(getSearchRecyclerView(), /* tabs= */ false);
            layoutAboveSearchContainer(rvContainer);
            layoutAboveSearchContainer(getSearchRecyclerView());
        } else {
            layoutBelowSearchContainer(rvContainer, showTabs);
            layoutBelowSearchContainer(getSearchRecyclerView(), /* tabs= */ false);
        }

        return rvContainer;
    }

    @Override
    void setupHeader() {
        super.setupHeader();

        removeCustomRules(mHeader);
        if (FeatureFlags.ENABLE_FLOATING_SEARCH_BAR.get()) {
            alignParentTop(mHeader, false /* includeTabsMargin */);
        } else {
            layoutBelowSearchContainer(mHeader, false /* includeTabsMargin */);
        }
    }

    @Override
    protected void updateHeaderScroll(int scrolledOffset) {
        super.updateHeaderScroll(scrolledOffset);
        if (mSearchUiManager.getEditText() == null) {
            return;
        }

        float prog = Utilities.boundToRange((float) scrolledOffset / mHeaderThreshold, 0f, 1f);
        boolean bgVisible = mSearchUiManager.getBackgroundVisibility();
        if (scrolledOffset == 0 && !isSearching()) {
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
    public int getHeaderBottom() {
        if (FeatureFlags.ENABLE_FLOATING_SEARCH_BAR.get()) {
            return super.getHeaderBottom();
        }
        return super.getHeaderBottom() + mSearchContainer.getBottom();
    }

    private void layoutBelowSearchContainer(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_TOP, R.id.search_container_all_apps);

        int topMargin = getContext().getResources().getDimensionPixelSize(
                R.dimen.all_apps_header_top_margin);
        if (includeTabsMargin) {
            topMargin += getContext().getResources().getDimensionPixelSize(
                    R.dimen.all_apps_header_pill_height);
        }
        layoutParams.topMargin = topMargin;
    }

    private void layoutAboveSearchContainer(View v) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ABOVE, R.id.search_container_all_apps);
    }

    private void alignParentTop(View v, boolean includeTabsMargin) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layoutParams.topMargin =
                includeTabsMargin
                        ? getContext().getResources().getDimensionPixelSize(
                                R.dimen.all_apps_header_pill_height)
                        : 0;
    }

    private void removeCustomRules(View v) {
        if (!(v.getLayoutParams() instanceof RelativeLayout.LayoutParams)) {
            return;
        }

        RelativeLayout.LayoutParams layoutParams = (LayoutParams) v.getLayoutParams();
        layoutParams.removeRule(RelativeLayout.ABOVE);
        layoutParams.removeRule(RelativeLayout.ALIGN_TOP);
        layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
    }

    @Override
    protected BaseAllAppsAdapter<T> createAdapter(AlphabeticalAppsList<T> appsList,
            BaseAdapterProvider[] adapterProviders) {
        return new AllAppsGridAdapter<>(mActivityContext, getLayoutInflater(), appsList,
                adapterProviders);
    }
}
