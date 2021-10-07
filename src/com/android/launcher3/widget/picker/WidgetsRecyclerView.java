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

package com.android.launcher3.widget.picker;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TableLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.model.WidgetsListSearchHeaderEntry;
import com.android.launcher3.widget.picker.SearchAndRecommendationsScrollController.OnContentChangeListener;

/**
 * The widgets recycler view.
 */
public class WidgetsRecyclerView extends BaseRecyclerView implements OnItemTouchListener {

    private WidgetsListAdapter mAdapter;

    private final int mScrollbarTop;

    private final Point mFastScrollerOffset = new Point();
    private boolean mTouchDownOnScroller;
    private HeaderViewDimensionsProvider mHeaderViewDimensionsProvider;
    private int mLastVisibleWidgetContentTableHeight = 0;
    private int mWidgetHeaderHeight = 0;
    private final int mSpacingBetweenEntries;
    @Nullable private OnContentChangeListener mOnContentChangeListener;

    public WidgetsRecyclerView(Context context) {
        this(context, null);
    }

    public WidgetsRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        // API 21 and below only support 3 parameter ctor.
        super(context, attrs, defStyleAttr);
        mScrollbarTop = getResources().getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        addOnItemTouchListener(this);

        ActivityContext activity = ActivityContext.lookupContext(getContext());
        DeviceProfile grid = activity.getDeviceProfile();

        // The spacing used between entries.
        mSpacingBetweenEntries =
                getResources().getDimensionPixelSize(R.dimen.widget_list_entry_spacing);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // create a layout manager with Launcher's context so that scroll position
        // can be preserved during screen rotation.
        WidgetsListLayoutManager layoutManager = new WidgetsListLayoutManager(getContext());
        layoutManager.setOnContentChangeListener(mOnContentChangeListener);
        setLayoutManager(layoutManager);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);
        mAdapter = (WidgetsListAdapter) adapter;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public String scrollToPositionAtProgress(float touchFraction) {
        // Skip early if widgets are not bound.
        if (isModelNotReady()) {
            return "";
        }

        // Stop the scroller if it is scrolling
        stopScroll();

        int rowCount = mAdapter.getItemCount();
        float pos = rowCount * touchFraction;
        int availableScrollHeight = getAvailableScrollHeight();
        LinearLayoutManager layoutManager = ((LinearLayoutManager) getLayoutManager());
        layoutManager.scrollToPositionWithOffset(0, (int) -(availableScrollHeight * touchFraction));

        int posInt = (int) ((touchFraction == 1) ? pos - 1 : pos);
        return mAdapter.getSectionName(posInt);
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void onUpdateScrollbar(int dy) {
        // Skip early if widgets are not bound.
        if (isModelNotReady()) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        // Skip early if, there no child laid out in the container.
        int scrollY = getCurrentScrollY();
        if (scrollY < 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        synchronizeScrollBarThumbOffsetToViewScroll(scrollY, getAvailableScrollHeight());
    }

    @Override
    public int getCurrentScrollY() {
        // Skip early if widgets are not bound.
        if (isModelNotReady() || getChildCount() == 0) {
            return -1;
        }

        int rowIndex = -1;
        View child = null;

        LayoutManager layoutManager = getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            // Use the LayoutManager as the source of truth for visible positions. During
            // animations, the view group child may not correspond to the visible views that appear
            // at the top.
            rowIndex = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
            child = layoutManager.findViewByPosition(rowIndex);
        }

        if (child == null) {
            // If the layout manager returns null for any reason, which can happen before layout
            // has occurred for the position, then look at the child of this view as a ViewGroup.
            child = getChildAt(0);
            rowIndex = getChildPosition(child);
        }

        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view instanceof TableLayout) {
                // This assumes there is ever only one content shown in this recycler view.
                mLastVisibleWidgetContentTableHeight = view.getMeasuredHeight();
            } else if (view instanceof WidgetsListHeader
                    && mLastVisibleWidgetContentTableHeight == 0
                    && view.getMeasuredHeight() > 0) {
                // This assumes all header views are of the same height.
                mWidgetHeaderHeight = view.getMeasuredHeight();
            }
        }

        int scrollPosition = getItemsHeight(rowIndex);
        int offset = getLayoutManager().getDecoratedTop(child);

        return getPaddingTop() + scrollPosition - offset;
    }

    /**
     * Returns the available scroll height, in pixel.
     *
     * <p>If the recycler view can't be scrolled, returns 0.
     */
    @Override
    protected int getAvailableScrollHeight() {
        // AvailableScrollHeight = Total height of the all items - first page height
        int firstPageHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        int totalHeightOfAllItems = getItemsHeight(/* untilIndex= */ mAdapter.getItemCount());
        int availableScrollHeight = totalHeightOfAllItems - firstPageHeight;
        return Math.max(0, availableScrollHeight);
    }

    private boolean isModelNotReady() {
        return mAdapter.getItemCount() == 0;
    }

    @Override
    public int getScrollBarTop() {
        return mHeaderViewDimensionsProvider == null
                ? mScrollbarTop
                : mHeaderViewDimensionsProvider.getHeaderViewHeight() + mScrollbarTop;
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDownOnScroller =
                    mScrollbar.isHitInParent(e.getX(), e.getY(), mFastScrollerOffset);
        }
        if (mTouchDownOnScroller) {
            final boolean result = mScrollbar.handleTouchEvent(e, mFastScrollerOffset);
            return result;
        }
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        if (mTouchDownOnScroller) {
            mScrollbar.handleTouchEvent(e, mFastScrollerOffset);
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }

    public void setHeaderViewDimensionsProvider(
            HeaderViewDimensionsProvider headerViewDimensionsProvider) {
        mHeaderViewDimensionsProvider = headerViewDimensionsProvider;
    }

    @Override
    public void scrollToTop() {
        if (mScrollbar != null) {
            mScrollbar.reattachThumbToScroll();
        }

        if (getLayoutManager() instanceof LinearLayoutManager) {
            if (getCurrentScrollY() == 0) {
                // We are at the top, so don't scrollToPosition (would cause unnecessary relayout).
                return;
            }
        }
        scrollToPosition(0);
    }

    public void setOnContentChangeListener(@Nullable OnContentChangeListener listener) {
        mOnContentChangeListener = listener;
        WidgetsListLayoutManager layoutManager = (WidgetsListLayoutManager) getLayoutManager();
        if (layoutManager != null) {
            layoutManager.setOnContentChangeListener(listener);
        }
    }

    /**
     * Returns the sum of the height, in pixels, of this list adapter's items from index 0 until
     * {@code untilIndex}.
     *
     * <p>If the untilIndex is larger than the total number of items in this adapter, returns the
     * sum of all items' height.
     */
    private int getItemsHeight(int untilIndex) {
        if (untilIndex > mAdapter.getItems().size()) {
            untilIndex = mAdapter.getItems().size();
        }
        int totalItemsHeight = 0;
        for (int i = 0; i < untilIndex; i++) {
            WidgetsListBaseEntry entry = mAdapter.getItems().get(i);
            if (entry instanceof WidgetsListHeaderEntry
                    || entry instanceof WidgetsListSearchHeaderEntry) {
                totalItemsHeight += mWidgetHeaderHeight;
                if (i > 0) {
                    // Each header contains the spacing between entries as top decoration, except
                    // the first one.
                    totalItemsHeight += mSpacingBetweenEntries;
                }
            } else if (entry instanceof WidgetsListContentEntry) {
                totalItemsHeight += mLastVisibleWidgetContentTableHeight;
            } else {
                throw new UnsupportedOperationException("Can't estimate height for " + entry);
            }
        }
        return totalItemsHeight;
    }

    /**
     * Provides dimensions of the header view that is shown at the top of a
     * {@link WidgetsRecyclerView}.
     */
    public interface HeaderViewDimensionsProvider {
        /**
         * Returns the height, in pixels, of the header view that is shown at the top of a
         * {@link WidgetsRecyclerView}.
         */
        int getHeaderViewHeight();
    }
}
