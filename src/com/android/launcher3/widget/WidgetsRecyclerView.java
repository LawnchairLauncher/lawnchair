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

package com.android.launcher3.widget;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.LinearLayoutManager;
import android.util.AttributeSet;
import android.view.View;
import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.model.WidgetsModel;

/**
 * The widgets recycler view.
 */
public class WidgetsRecyclerView extends BaseRecyclerView {

    private static final String TAG = "WidgetsRecyclerView";
    private WidgetsListAdapter mAdapter;

    public WidgetsRecyclerView(Context context) {
        this(context, null);
    }

    public WidgetsRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        // API 21 and below only support 3 parameter ctor.
        super(context, attrs, defStyleAttr);
    }

    public WidgetsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addOnItemTouchListener(this);
        // create a layout manager with Launcher's context so that scroll position
        // can be preserved during screen rotation.
        setLayoutManager(new LinearLayoutManager(getContext()));
    }

    public int getFastScrollerTrackColor(int defaultTrackColor) {
        return Color.WHITE;
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

        int posInt = (int) ((touchFraction == 1)? pos -1 : pos);
        return mAdapter.getSectionName(posInt);
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void onUpdateScrollbar(int dy) {
        // Skip early if widgets are not bound.
        if (isModelNotReady()) {
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

        View child = getChildAt(0);
        int rowIndex = getChildPosition(child);
        int y = (child.getMeasuredHeight() * rowIndex);
        int offset = getLayoutManager().getDecoratedTop(child);

        return getPaddingTop() + y - offset;
    }

    /**
     * Returns the available scroll height:
     *   AvailableScrollHeight = Total height of the all items - last page height
     */
    @Override
    protected int getAvailableScrollHeight() {
        View child = getChildAt(0);
        int height = child.getMeasuredHeight() * mAdapter.getItemCount();
        int totalHeight = getPaddingTop() + height + getPaddingBottom();
        int availableScrollHeight = totalHeight - getScrollbarTrackHeight();
        return availableScrollHeight;
    }

    private boolean isModelNotReady() {
        return mAdapter.getItemCount() == 0;
    }
}