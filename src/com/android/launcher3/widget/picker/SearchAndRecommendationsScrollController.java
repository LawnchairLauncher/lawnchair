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

import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.widget.picker.WidgetsFullSheet.SearchAndRecommendationViewHolder;
import com.android.launcher3.workprofile.PersonalWorkPagedView;

/**
 * A controller which measures & updates {@link WidgetsFullSheet}'s views padding, margin and
 * vertical displacement upon scrolling.
 */
final class SearchAndRecommendationsScrollController implements
        RecyclerViewFastScroller.OnFastScrollChangeListener {
    private final boolean mHasWorkProfile;
    private final SearchAndRecommendationViewHolder mViewHolder;
    private final WidgetsRecyclerView mPrimaryRecyclerView;

    // The following are only non null if mHasWorkProfile is true.
    @Nullable private final WidgetsRecyclerView mWorkRecyclerView;
    @Nullable private final View mPrimaryWorkTabsView;
    @Nullable private final PersonalWorkPagedView mPrimaryWorkViewPager;

    private WidgetsRecyclerView mCurrentRecyclerView;
    private int mMaxCollapsibleHeight = 0;

    SearchAndRecommendationsScrollController(
            boolean hasWorkProfile,
            SearchAndRecommendationViewHolder viewHolder,
            WidgetsRecyclerView primaryRecyclerView,
            @Nullable WidgetsRecyclerView workRecyclerView,
            @Nullable View personalWorkTabsView,
            @Nullable PersonalWorkPagedView primaryWorkViewPager) {
        mHasWorkProfile = hasWorkProfile;
        mViewHolder = viewHolder;
        mPrimaryRecyclerView = primaryRecyclerView;
        mWorkRecyclerView = workRecyclerView;
        mPrimaryWorkTabsView = personalWorkTabsView;
        mPrimaryWorkViewPager = primaryWorkViewPager;
        mCurrentRecyclerView = mPrimaryRecyclerView;
    }

    /** Sets the current active {@link WidgetsRecyclerView}. */
    public void setCurrentRecyclerView(WidgetsRecyclerView currentRecyclerView) {
        mCurrentRecyclerView = currentRecyclerView;
    }

    /**
     * Updates the margin and padding of {@link WidgetsFullSheet} to accumulate collapsible views.
     */
    public void updateMarginAndPadding() {
        // The maximum vertical distance, in pixels, until the last collapsible element is not
        // visible from the screen when the user scrolls down the recycler view.
        mMaxCollapsibleHeight = mViewHolder.mContainer.getPaddingTop()
                + measureHeightWithVerticalMargins(mViewHolder.mCollapseHandle)
                + measureHeightWithVerticalMargins(mViewHolder.mHeaderTitle);

        int topContainerHeight = measureHeightWithVerticalMargins(mViewHolder.mContainer);
        if (mHasWorkProfile) {
            // In a work profile setup, the full widget sheet contains the following views:
            //           -------               -|
            //           Widgets               -|---> LinearLayout for search & recommendations
            //          Search bar             -|
            //      Personal | Work
            //           View Pager
            //
            // Views after the search & recommendations are not bound by RelativelyLayout param.
            // To position them on the expected location, padding & margin are added to these views

            // Tabs should have a padding of the height of the search & recommendations container.
            mPrimaryWorkTabsView.setPadding(
                    mPrimaryWorkTabsView.getPaddingLeft(),
                    topContainerHeight,
                    mPrimaryWorkTabsView.getPaddingRight(),
                    mPrimaryWorkTabsView.getPaddingBottom());

            // Instead of setting the top offset directly, we split the top offset into two values:
            // 1. topOffsetAfterAllViewsCollapsed: this is the top offset after all collapsible
            //    views are no longer visible on the screen.
            //    This value is set as the margin for the view pager.
            // 2. mMaxCollapsibleDistance
            //    This value is set as the padding for the recycler views in order to work with
            //    clipToPadding="false", which is an attribute for not showing top / bottom padding
            //    when a recycler view has not reached the top or bottom of the list.
            //    e.g. a list of 10 entries, only 3 entries are visible at a time.
            //         case 1: recycler view is scrolled to the top. Top padding is visible/
            //         (top padding)
            //         item 1
            //         item 2
            //         item 3
            //
            //         case 2: recycler view is scrolled to the middle. No padding is visible.
            //         item 4
            //         item 5
            //         item 6
            //
            //         case 3: recycler view is scrolled to the end. bottom padding is visible.
            //         item 8
            //         item 9
            //         item 10
            //         (bottom padding): not set in this case.
            //
            // When the views are first inflated, the sum of topOffsetAfterAllViewsCollapsed and
            // mMaxCollapsibleDistance should equal to the top container height.
            int tabsViewActualHeight = measureHeightWithVerticalMargins(mPrimaryWorkTabsView)
                    - mPrimaryWorkTabsView.getPaddingTop();
            int topOffsetAfterAllViewsCollapsed =
                    topContainerHeight + tabsViewActualHeight - mMaxCollapsibleHeight;

            RelativeLayout.LayoutParams layoutParams =
                    (RelativeLayout.LayoutParams) mPrimaryWorkViewPager.getLayoutParams();
            layoutParams.setMargins(0, topOffsetAfterAllViewsCollapsed, 0, 0);
            mPrimaryWorkViewPager.setLayoutParams(layoutParams);
            mPrimaryWorkViewPager.requestLayout();

            mPrimaryRecyclerView.setPadding(
                    mPrimaryRecyclerView.getPaddingLeft(),
                    mMaxCollapsibleHeight,
                    mPrimaryRecyclerView.getPaddingRight(),
                    mPrimaryRecyclerView.getPaddingBottom());
            mWorkRecyclerView.setPadding(
                    mWorkRecyclerView.getPaddingLeft(),
                    mMaxCollapsibleHeight,
                    mWorkRecyclerView.getPaddingRight(),
                    mWorkRecyclerView.getPaddingBottom());
        } else {
            mPrimaryRecyclerView.setPadding(
                    mPrimaryRecyclerView.getPaddingLeft(),
                    topContainerHeight,
                    mPrimaryRecyclerView.getPaddingRight(),
                    mPrimaryRecyclerView.getPaddingBottom());
        }
    }

    /**
     * Changes the displacement of collapsible views (e.g. title & widget recommendations) and fixed
     * views (e.g. recycler views, tabs) upon scrolling.
     */
    @Override
    public void onThumbOffsetYChanged(int unused) {
        // Always use the recycler view offset because fast scroller offset has a different scale.
        int recyclerViewYOffset = mCurrentRecyclerView.getCurrentScrollY();
        if (recyclerViewYOffset < 0) return;
        if (mMaxCollapsibleHeight > 0) {
            int yDisplacement = Math.max(-recyclerViewYOffset, -mMaxCollapsibleHeight);
            mViewHolder.mHeaderTitle.setTranslationY(yDisplacement);
            mViewHolder.mSearchBar.setTranslationY(yDisplacement);
            if (mHasWorkProfile) {
                mPrimaryWorkTabsView.setTranslationY(yDisplacement);
            }
        }
    }

    /** Resets any previous view translation. */
    public void reset() {
        mViewHolder.mHeaderTitle.setTranslationY(0);
        mViewHolder.mSearchBar.setTranslationY(0);
        if (mHasWorkProfile) {
            mPrimaryWorkTabsView.setTranslationY(0);
        }
    }

    /** private the height, in pixel, + the vertical margins of a given view. */
    private static int measureHeightWithVerticalMargins(View view) {
        MarginLayoutParams marginLayoutParams = (MarginLayoutParams) view.getLayoutParams();
        return view.getMeasuredHeight() + marginLayoutParams.bottomMargin
                + marginLayoutParams.topMargin;
    }
}
