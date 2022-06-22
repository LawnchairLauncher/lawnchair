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

import static com.android.launcher3.widget.picker.WidgetsListAdapter.VIEW_TYPE_WIDGETS_HEADER;
import static com.android.launcher3.widget.picker.WidgetsListAdapter.VIEW_TYPE_WIDGETS_SEARCH_HEADER;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener;

import com.android.launcher3.FastScrollRecyclerView;
import com.android.launcher3.R;

/**
 * The widgets recycler view.
 */
public class WidgetsRecyclerView extends FastScrollRecyclerView implements OnItemTouchListener {

    private WidgetsListAdapter mAdapter;

    private final int mScrollbarTop;

    private final Point mFastScrollerOffset = new Point();
    private boolean mTouchDownOnScroller;
    private HeaderViewDimensionsProvider mHeaderViewDimensionsProvider;

    /**
     * There is always 1 or 0 item of VIEW_TYPE_WIDGETS_LIST. Other types have fixes sizes, so the
     * the size can be used for all other items of same type. Caching the last know size for
     * VIEW_TYPE_WIDGETS_LIST allows us to use it to estimate full size even when
     * VIEW_TYPE_WIDGETS_LIST is not visible on the screen.
     */
    private final SparseIntArray mCachedSizes = new SparseIntArray();
    private final SpacingDecoration mSpacingDecoration;

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

        mSpacingDecoration = new SpacingDecoration(context);
        addItemDecoration(mSpacingDecoration);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // create a layout manager with Launcher's context so that scroll position
        // can be preserved during screen rotation.
        setLayoutManager(new LinearLayoutManager(getContext()));
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

    /**
     * Returns the sum of the height, in pixels, of this list adapter's items from index 0 until
     * {@code untilIndex}.
     *
     * <p>If the untilIndex is larger than the total number of items in this adapter, returns the
     * sum of all items' height.
     */
    @Override
    protected int getItemsHeight(int untilIndex) {
        // Initialize cache
        int childCount = getChildCount();
        int startPosition;
        if (childCount > 0
                && ((startPosition = getChildAdapterPosition(getChildAt(0))) != NO_POSITION)) {
            for (int i = 0; i < childCount; i++) {
                mCachedSizes.put(
                        mAdapter.getItemViewType(startPosition + i),
                        getChildAt(i).getMeasuredHeight());
            }
        }

        if (untilIndex > mAdapter.getItems().size()) {
            untilIndex = mAdapter.getItems().size();
        }
        int totalItemsHeight = 0;
        for (int i = 0; i < untilIndex; i++) {
            int type = mAdapter.getItemViewType(i);
            totalItemsHeight += mCachedSizes.get(type) + mSpacingDecoration.getSpacing(i, type);
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

    private static class SpacingDecoration extends RecyclerView.ItemDecoration {

        private final int mSpacingBetweenEntries;

        SpacingDecoration(@NonNull Context context) {
            mSpacingBetweenEntries =
                    context.getResources().getDimensionPixelSize(R.dimen.widget_list_entry_spacing);
        }

        @Override
        public void getItemOffsets(
                @NonNull Rect outRect,
                @NonNull View view,
                @NonNull RecyclerView parent,
                @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);
            outRect.top += getSpacing(position, parent.getAdapter().getItemViewType(position));
        }

        public int getSpacing(int position, int type) {
            boolean isHeader = type == VIEW_TYPE_WIDGETS_SEARCH_HEADER
                    || type == VIEW_TYPE_WIDGETS_HEADER;
            return position > 0 && isHeader ? mSpacingBetweenEntries : 0;
        }
    }
}
