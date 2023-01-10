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
package com.android.launcher3.util;

import android.content.Context;
import android.util.SparseIntArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.State;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

/**
 * Extension of {@link GridLayoutManager} with support for smooth scrolling
 */
public class ScrollableLayoutManager extends GridLayoutManager {

    // keyed on item type
    protected final SparseIntArray mCachedSizes = new SparseIntArray();

    private RecyclerView mRv;

    /**
     * Precalculated total height keyed on the item position. This is always incremental.
     * Subclass can override {@link #incrementTotalHeight} to incorporate the layout logic.
     * For example all-apps should have same values for items in same row,
     *     sample values: 0, 10, 10, 10, 10, 20, 20, 20, 20
     * whereas widgets will have strictly increasing values
     *     sample values: 0, 10, 50, 60, 110
     */
    private int[] mTotalHeightCache = new int[1];
    private int mLastValidHeightIndex = 0;

    public ScrollableLayoutManager(Context context) {
        super(context, 1, GridLayoutManager.VERTICAL, false);
    }

    @Override
    public void onAttachedToWindow(RecyclerView view) {
        super.onAttachedToWindow(view);
        mRv = view;
    }

    @Override
    public void layoutDecorated(@NonNull View child, int left, int top, int right, int bottom) {
        super.layoutDecorated(child, left, top, right, bottom);
        updateCachedSize(child);
    }

    @Override
    public void layoutDecoratedWithMargins(@NonNull View child, int left, int top, int right,
            int bottom) {
        super.layoutDecoratedWithMargins(child, left, top, right, bottom);
        updateCachedSize(child);
    }

    private void updateCachedSize(@NonNull View child) {
        int viewType = mRv.getChildViewHolder(child).getItemViewType();
        int size = child.getMeasuredHeight();
        if (mCachedSizes.get(viewType, -1) != size) {
            invalidateScrollCache();
        }
        mCachedSizes.put(viewType, size);
    }

    @Override
    public int computeVerticalScrollExtent(State state) {
        return mRv == null ? 0 : mRv.getHeight();
    }

    @Override
    public int computeVerticalScrollOffset(State state) {
        Adapter adapter = mRv == null ? null : mRv.getAdapter();
        if (adapter == null) {
            return 0;
        }
        if (adapter.getItemCount() == 0 || getChildCount() == 0) {
            return 0;
        }
        View child = getChildAt(0);
        ViewHolder holder = mRv.findContainingViewHolder(child);
        if (holder == null) {
            return 0;
        }
        int itemPosition = holder.getLayoutPosition();
        if (itemPosition < 0) {
            return 0;
        }
        return getPaddingTop() + getItemsHeight(adapter, itemPosition) - getDecoratedTop(child);
    }

    @Override
    public int computeVerticalScrollRange(State state) {
        Adapter adapter = mRv == null ? null : mRv.getAdapter();
        return adapter == null ? 0 : getItemsHeight(adapter, adapter.getItemCount());
    }

    /**
     * Returns the sum of the height, in pixels, of this list adapter's items from index
     * 0 (inclusive) until {@code untilIndex} (exclusive). If untilIndex is same as the itemCount,
     * it returns the full height of all the items.
     *
     * <p>If the untilIndex is larger than the total number of items in this adapter, returns the
     * sum of all items' height.
     */
    private int getItemsHeight(Adapter adapter, int untilIndex) {
        final int totalItems = adapter.getItemCount();
        if (mTotalHeightCache.length < (totalItems + 1)) {
            mTotalHeightCache = new int[totalItems + 1];
            mLastValidHeightIndex = 0;
        }
        if (untilIndex > totalItems) {
            untilIndex = totalItems;
        } else if (untilIndex < 0) {
            untilIndex = 0;
        }
        if (untilIndex <= mLastValidHeightIndex) {
            return mTotalHeightCache[untilIndex];
        }

        int totalItemsHeight = mTotalHeightCache[mLastValidHeightIndex];
        for (int i = mLastValidHeightIndex; i < untilIndex; i++) {
            totalItemsHeight = incrementTotalHeight(adapter, i, totalItemsHeight);
            mTotalHeightCache[i + 1] = totalItemsHeight;
        }
        mLastValidHeightIndex = untilIndex;
        return totalItemsHeight;
    }

    /**
     * The current implementation assumes a linear list with every item taking up the whole row.
     * Subclasses should override this method to account for any spanning logic
     */
    protected int incrementTotalHeight(Adapter adapter, int position, int heightUntilLastPos) {
        return heightUntilLastPos + mCachedSizes.get(adapter.getItemViewType(position));
    }

    private void invalidateScrollCache() {
        mLastValidHeightIndex = 0;
    }

    @Override
    public void onItemsAdded(RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsAdded(recyclerView, positionStart, itemCount);
        invalidateScrollCache();
    }

    @Override
    public void onItemsChanged(RecyclerView recyclerView) {
        super.onItemsChanged(recyclerView);
        invalidateScrollCache();
    }

    @Override
    public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount);
        invalidateScrollCache();
    }

    @Override
    public void onItemsMoved(RecyclerView recyclerView, int from, int to, int itemCount) {
        super.onItemsMoved(recyclerView, from, to, itemCount);
        invalidateScrollCache();
    }

    @Override
    public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount,
            Object payload) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount, payload);
        invalidateScrollCache();
    }
}
