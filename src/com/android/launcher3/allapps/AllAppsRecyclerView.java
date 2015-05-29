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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;

import java.util.List;

/**
 * A RecyclerView with custom fast scroll support for the all apps view.
 */
public class AllAppsRecyclerView extends BaseRecyclerView {

    private AlphabeticalAppsList mApps;
    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;

    private int mPredictionBarHeight;
    private int mScrollbarMinHeight;

    private Rect mBackgroundPadding = new Rect();

    private Launcher mLauncher;

    public AllAppsRecyclerView(Context context) {
        this(context, null);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr);
        mLauncher = (Launcher) context;
    }

    /**
     * Sets the list of apps in this view, used to determine the fastscroll position.
     */
    public void setApps(AlphabeticalAppsList apps) {
        mApps = apps;
    }

    /**
     * Sets the number of apps per row in this recycler view.
     */
    public void setNumAppsPerRow(int numAppsPerRow, int numPredictedAppsPerRow) {
        mNumAppsPerRow = numAppsPerRow;
        mNumPredictedAppsPerRow = numPredictedAppsPerRow;

        DeviceProfile grid = mLauncher.getDeviceProfile();
        RecyclerView.RecycledViewPool pool = getRecycledViewPool();
        int approxRows = (int) Math.ceil(grid.availableHeightPx / grid.allAppsIconSizePx);
        pool.setMaxRecycledViews(AllAppsGridAdapter.PREDICTION_BAR_SPACER_TYPE, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.EMPTY_SEARCH_VIEW_TYPE, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.ICON_VIEW_TYPE, approxRows * mNumAppsPerRow);
        pool.setMaxRecycledViews(AllAppsGridAdapter.SECTION_BREAK_VIEW_TYPE, approxRows);
    }

    public void updateBackgroundPadding(Drawable background) {
        background.getPadding(mBackgroundPadding);
    }

    /**
     * Sets the prediction bar height.
     */
    public void setPredictionBarHeight(int height) {
        mPredictionBarHeight = height;
    }

    /**
     * Scrolls this recycler view to the top.
     */
    public void scrollToTop() {
        scrollToPosition(0);
    }

    /**
     * Returns the current scroll position.
     */
    public int getScrollPosition() {
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
        getCurScrollState(scrollPosState, items);
        if (scrollPosState.rowIndex != -1) {
            int predictionBarHeight = mApps.getPredictedApps().isEmpty() ? 0 : mPredictionBarHeight;
            return getPaddingTop() + (scrollPosState.rowIndex * scrollPosState.rowHeight) +
                    predictionBarHeight - scrollPosState.rowTopOffset;
        }
        return 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addOnItemTouchListener(this);
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public String scrollToPositionAtProgress(float touchFraction) {
        // Ensure that we have any sections
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollSections =
                mApps.getFastScrollerSections();
        if (fastScrollSections.isEmpty()) {
            return "";
        }

        // Stop the scroller if it is scrolling
        LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
        stopScroll();

        // If there is a prediction bar, then capture the appropriate area for the prediction bar
        float predictionBarFraction = 0f;
        if (!mApps.getPredictedApps().isEmpty()) {
            predictionBarFraction = (float) mNumPredictedAppsPerRow / mApps.getSize();
            if (touchFraction <= predictionBarFraction) {
                // Scroll to the top of the view, where the prediction bar is
                layoutManager.scrollToPositionWithOffset(0, 0);
                return "";
            }
        }

        // Since the app ranges are from 0..1, we need to map the touch fraction back to 0..1 from
        // predictionBarFraction..1
        touchFraction = (touchFraction - predictionBarFraction) *
                (1f / (1f - predictionBarFraction));
        AlphabeticalAppsList.FastScrollSectionInfo lastScrollSection = fastScrollSections.get(0);
        for (int i = 1; i < fastScrollSections.size(); i++) {
            AlphabeticalAppsList.FastScrollSectionInfo scrollSection = fastScrollSections.get(i);
            if (lastScrollSection.appRangeFraction <= touchFraction &&
                    touchFraction < scrollSection.appRangeFraction) {
                break;
            }
            lastScrollSection = scrollSection;
        }

        // Scroll to the view at the position, anchored at the top of the screen. We call the scroll
        // method on the LayoutManager directly since it is not exposed by RecyclerView.
        layoutManager.scrollToPositionWithOffset(lastScrollSection.appItem.position, 0);

        return lastScrollSection.sectionName;
    }


    /**
     * Returns the row index for a app index in the list.
     */
    private int findRowForAppIndex(int index) {
        List<AlphabeticalAppsList.SectionInfo> sections = mApps.getSections();
        int appIndex = 0;
        int rowCount = 0;
        for (AlphabeticalAppsList.SectionInfo info : sections) {
            int numRowsInSection = (int) Math.ceil((float) info.numApps / mNumAppsPerRow);
            if (appIndex + info.numApps > index) {
                return rowCount + ((index - appIndex) / mNumAppsPerRow);
            }
            appIndex += info.numApps;
            rowCount += numRowsInSection;
        }
        return appIndex;
    }

    /**
     * Returns the total number of rows in the list.
     */
    private int getNumRows() {
        List<AlphabeticalAppsList.SectionInfo> sections = mApps.getSections();
        int rowCount = 0;
        for (AlphabeticalAppsList.SectionInfo info : sections) {
            int numRowsInSection = (int) Math.ceil((float) info.numApps / mNumAppsPerRow);
            rowCount += numRowsInSection;
        }
        return rowCount;
    }


    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void updateVerticalScrollbarBounds() {
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();

        // Skip early if there are no items.
        if (items.isEmpty()) {
            verticalScrollbarBounds.setEmpty();
            return;
        }

        // Find the index and height of the first visible row (all rows have the same height)
        int x, y;
        int predictionBarHeight = mApps.getPredictedApps().isEmpty() ? 0 : mPredictionBarHeight;
        int rowCount = getNumRows();
        getCurScrollState(scrollPosState, items);
        if (scrollPosState.rowIndex != -1) {
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            int totalScrollHeight = rowCount * scrollPosState.rowHeight + predictionBarHeight;
            if (totalScrollHeight > height) {
                int scrollbarHeight = Math.max(mScrollbarMinHeight,
                        (int) (height / ((float) totalScrollHeight / height)));

                // Calculate the position and size of the scroll bar
                if (Utilities.isRtl(getResources())) {
                    x = mBackgroundPadding.left;
                } else {
                    x = getWidth() - mBackgroundPadding.right - getScrollbarWidth();
                }

                // To calculate the offset, we compute the percentage of the total scrollable height
                // that the user has already scrolled and then map that to the scroll bar bounds
                int availableY = totalScrollHeight - height;
                int availableScrollY = height - scrollbarHeight;
                y = (scrollPosState.rowIndex * scrollPosState.rowHeight) + predictionBarHeight
                        - scrollPosState.rowTopOffset;
                y = getPaddingTop() +
                        (int) (((float) (getPaddingTop() + y) / availableY) * availableScrollY);

                verticalScrollbarBounds.set(x, y, x + getScrollbarWidth(), y + scrollbarHeight);
                return;
            }
        }
        verticalScrollbarBounds.setEmpty();
    }

    /**
     * Returns the current scroll state.
     */
    private void getCurScrollState(ScrollPositionState stateOut,
            List<AlphabeticalAppsList.AdapterItem> items) {
        stateOut.rowIndex = -1;
        stateOut.rowTopOffset = -1;
        stateOut.rowHeight = -1;

        // Return early if there are no items
        if (items.isEmpty()) {
            return;
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            int position = getChildPosition(child);
            if (position != NO_POSITION) {
                AlphabeticalAppsList.AdapterItem item = items.get(position);
                if (item.viewType == AllAppsGridAdapter.ICON_VIEW_TYPE) {
                    stateOut.rowIndex = findRowForAppIndex(item.appIndex);
                    stateOut.rowTopOffset = getLayoutManager().getDecoratedTop(child);
                    stateOut.rowHeight = child.getHeight();
                    break;
                }
            }
        }
    }
}
