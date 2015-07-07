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
import android.graphics.Canvas;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.BaseRecyclerViewFastScrollBar;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Stats;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Thunk;

import java.util.List;

/**
 * A RecyclerView with custom fast scroll support for the all apps view.
 */
public class AllAppsRecyclerView extends BaseRecyclerView
        implements Stats.LaunchSourceProvider {

    private static final int FAST_SCROLL_MODE_JUMP_TO_FIRST_ICON = 0;
    private static final int FAST_SCROLL_MODE_FREE_SCROLL = 1;

    private static final int FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_ROW = 0;
    private static final int FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_SECTIONS = 1;

    private AlphabeticalAppsList mApps;
    private int mNumAppsPerRow;

    @Thunk BaseRecyclerViewFastScrollBar.FastScrollFocusableView mLastFastScrollFocusedView;
    @Thunk int mPrevFastScrollFocusedPosition;
    @Thunk int mFastScrollFrameIndex;
    @Thunk final int[] mFastScrollFrames = new int[10];

    private final int mFastScrollMode = FAST_SCROLL_MODE_JUMP_TO_FIRST_ICON;
    private final int mScrollBarMode = FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_ROW;

    private ScrollPositionState mScrollPosState = new ScrollPositionState();

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
    public void setNumAppsPerRow(DeviceProfile grid, int numAppsPerRow) {
        mNumAppsPerRow = numAppsPerRow;

        RecyclerView.RecycledViewPool pool = getRecycledViewPool();
        int approxRows = (int) Math.ceil(grid.availableHeightPx / grid.allAppsIconSizePx);
        pool.setMaxRecycledViews(AllAppsGridAdapter.EMPTY_SEARCH_VIEW_TYPE, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.ICON_VIEW_TYPE, approxRows * mNumAppsPerRow);
        pool.setMaxRecycledViews(AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE, mNumAppsPerRow);
        pool.setMaxRecycledViews(AllAppsGridAdapter.SECTION_BREAK_VIEW_TYPE, approxRows);
    }

    /**
     * Scrolls this recycler view to the top.
     */
    public void scrollToTop() {
        scrollToPosition(0);
    }

    /**
     * We need to override the draw to ensure that we don't draw the overscroll effect beyond the
     * background bounds.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.clipRect(mBackgroundPadding.left, mBackgroundPadding.top,
                getWidth() - mBackgroundPadding.right,
                getHeight() - mBackgroundPadding.bottom);
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Bind event handlers
        addOnItemTouchListener(this);
    }

    @Override
    public void fillInLaunchSourceData(Bundle sourceData) {
        sourceData.putString(Stats.SOURCE_EXTRA_CONTAINER, Stats.CONTAINER_ALL_APPS);
        if (mApps.hasFilter()) {
            sourceData.putString(Stats.SOURCE_EXTRA_SUB_CONTAINER,
                    Stats.SUB_CONTAINER_ALL_APPS_SEARCH);
        } else {
            sourceData.putString(Stats.SOURCE_EXTRA_SUB_CONTAINER,
                    Stats.SUB_CONTAINER_ALL_APPS_A_Z);
        }
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public String scrollToPositionAtProgress(float touchFraction) {
        int rowCount = mApps.getNumAppRows();
        if (rowCount == 0) {
            return "";
        }

        // Stop the scroller if it is scrolling
        stopScroll();

        // Find the fastscroll section that maps to this touch fraction
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollSections =
                mApps.getFastScrollerSections();
        AlphabeticalAppsList.FastScrollSectionInfo lastInfo = fastScrollSections.get(0);
        if (mScrollBarMode == FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_ROW) {
            for (int i = 1; i < fastScrollSections.size(); i++) {
                AlphabeticalAppsList.FastScrollSectionInfo info = fastScrollSections.get(i);
                if (info.touchFraction > touchFraction) {
                    break;
                }
                lastInfo = info;
            }
        } else if (mScrollBarMode == FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_SECTIONS){
            lastInfo = fastScrollSections.get((int) (touchFraction * (fastScrollSections.size() - 1)));
        } else {
            throw new RuntimeException("Unexpected scroll bar mode");
        }

        // Map the touch position back to the scroll of the recycler view
        getCurScrollState(mScrollPosState, mApps.getAdapterItems());
        int availableScrollHeight = getAvailableScrollHeight(rowCount, mScrollPosState.rowHeight, 0);
        LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
        if (mFastScrollMode == FAST_SCROLL_MODE_FREE_SCROLL) {
            layoutManager.scrollToPositionWithOffset(0, (int) -(availableScrollHeight * touchFraction));
        }

        if (mPrevFastScrollFocusedPosition != lastInfo.fastScrollToItem.position) {
            mPrevFastScrollFocusedPosition = lastInfo.fastScrollToItem.position;

            // Reset the last focused view
            if (mLastFastScrollFocusedView != null) {
                mLastFastScrollFocusedView.setFastScrollFocused(false, true);
                mLastFastScrollFocusedView = null;
            }

            if (mFastScrollMode == FAST_SCROLL_MODE_JUMP_TO_FIRST_ICON) {
                smoothSnapToPosition(mPrevFastScrollFocusedPosition, mScrollPosState);
            } else if (mFastScrollMode == FAST_SCROLL_MODE_FREE_SCROLL) {
                final ViewHolder vh = findViewHolderForPosition(mPrevFastScrollFocusedPosition);
                if (vh != null &&
                        vh.itemView instanceof BaseRecyclerViewFastScrollBar.FastScrollFocusableView) {
                    mLastFastScrollFocusedView =
                            (BaseRecyclerViewFastScrollBar.FastScrollFocusableView) vh.itemView;
                    mLastFastScrollFocusedView.setFastScrollFocused(true, true);
                }
            } else {
                throw new RuntimeException("Unexpected fast scroll mode");
            }
        }
        return lastInfo.sectionName;
    }

    @Override
    public void onFastScrollCompleted() {
        super.onFastScrollCompleted();
        // Reset and clean up the last focused view
        if (mLastFastScrollFocusedView != null) {
            mLastFastScrollFocusedView.setFastScrollFocused(false, true);
            mLastFastScrollFocusedView = null;
        }
        mPrevFastScrollFocusedPosition = -1;
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void onUpdateScrollbar() {
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();

        // Skip early if there are no items or we haven't been measured
        if (items.isEmpty() || mNumAppsPerRow == 0) {
            mScrollbar.setScrollbarThumbOffset(-1, -1);
            return;
        }

        // Find the index and height of the first visible row (all rows have the same height)
        int rowCount = mApps.getNumAppRows();
        getCurScrollState(mScrollPosState, items);
        if (mScrollPosState.rowIndex < 0) {
            mScrollbar.setScrollbarThumbOffset(-1, -1);
            return;
        }

        synchronizeScrollBarThumbOffsetToViewScroll(mScrollPosState, rowCount, 0);
    }

    /**
     * This runnable runs a single frame of the smooth scroll animation and posts the next frame
     * if necessary.
     */
    @Thunk Runnable mSmoothSnapNextFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (mFastScrollFrameIndex < mFastScrollFrames.length) {
                scrollBy(0, mFastScrollFrames[mFastScrollFrameIndex]);
                mFastScrollFrameIndex++;
                postOnAnimation(mSmoothSnapNextFrameRunnable);
            } else {
                // Animation completed, set the fast scroll state on the target view
                final ViewHolder vh = findViewHolderForPosition(mPrevFastScrollFocusedPosition);
                if (vh != null &&
                        vh.itemView instanceof BaseRecyclerViewFastScrollBar.FastScrollFocusableView &&
                        mLastFastScrollFocusedView != vh.itemView) {
                    mLastFastScrollFocusedView =
                            (BaseRecyclerViewFastScrollBar.FastScrollFocusableView) vh.itemView;
                    mLastFastScrollFocusedView.setFastScrollFocused(true, true);
                }
            }
        }
    };

    /**
     * Smoothly snaps to a given position.  We do this manually by calculating the keyframes
     * ourselves and animating the scroll on the recycler view.
     */
    private void smoothSnapToPosition(final int position, ScrollPositionState scrollPosState) {
        removeCallbacks(mSmoothSnapNextFrameRunnable);

        // Calculate the full animation from the current scroll position to the final scroll
        // position, and then run the animation for the duration.
        int curScrollY = getPaddingTop() +
                (scrollPosState.rowIndex * scrollPosState.rowHeight) - scrollPosState.rowTopOffset;
        int newScrollY = getScrollAtPosition(position, scrollPosState.rowHeight);
        int numFrames = mFastScrollFrames.length;
        for (int i = 0; i < numFrames; i++) {
            // TODO(winsonc): We can interpolate this as well.
            mFastScrollFrames[i] = (newScrollY - curScrollY) / numFrames;
        }
        mFastScrollFrameIndex = 0;
        postOnAnimation(mSmoothSnapNextFrameRunnable);
    }

    /**
     * Returns the current scroll state of the apps rows.
     */
    private void getCurScrollState(ScrollPositionState stateOut,
            List<AlphabeticalAppsList.AdapterItem> items) {
        stateOut.rowIndex = -1;
        stateOut.rowTopOffset = -1;
        stateOut.rowHeight = -1;

        // Return early if there are no items or we haven't been measured
        if (items.isEmpty() || mNumAppsPerRow == 0) {
            return;
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            int position = getChildPosition(child);
            if (position != NO_POSITION) {
                AlphabeticalAppsList.AdapterItem item = items.get(position);
                if (item.viewType == AllAppsGridAdapter.ICON_VIEW_TYPE ||
                        item.viewType == AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE) {
                    stateOut.rowIndex = item.rowIndex;
                    stateOut.rowTopOffset = getLayoutManager().getDecoratedTop(child);
                    stateOut.rowHeight = child.getHeight();
                    break;
                }
            }
        }
    }

    /**
     * Returns the scrollY for the given position in the adapter.
     */
    private int getScrollAtPosition(int position, int rowHeight) {
        AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(position);
        if (item.viewType == AllAppsGridAdapter.ICON_VIEW_TYPE ||
                item.viewType == AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE) {
            int offset = item.rowIndex > 0 ? getPaddingTop() : 0;
            return offset + item.rowIndex * rowHeight;
        } else {
            return 0;
        }
    }
}
