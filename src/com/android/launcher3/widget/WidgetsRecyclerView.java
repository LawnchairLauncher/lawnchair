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
import android.graphics.Rect;
import android.support.v7.widget.LinearLayoutManager;
import android.util.AttributeSet;
import android.view.View;
import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.PackageItemInfo;

/**
 * The widgets recycler view.
 */
public class WidgetsRecyclerView extends BaseRecyclerView {

    private static final String TAG = "WidgetsRecyclerView";
    private WidgetsModel mWidgets;

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
    }

    /**
     * Sets the widget model in this view, used to determine the fast scroll position.
     */
    public void setWidgets(WidgetsModel widgets) {
        mWidgets = widgets;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public String scrollToPositionAtProgress(float touchFraction) {
        float pos = mWidgets.getPackageSize() * touchFraction;

        int posInt = (int) pos;
        LinearLayoutManager layoutManager = ((LinearLayoutManager) getLayoutManager());
        getCurScrollState(scrollPosState);
        layoutManager.scrollToPositionWithOffset((int) pos,
                (int) (scrollPosState.rowHeight * ((float) posInt - pos)));

        posInt = (int) ((touchFraction == 1)? pos -1 : pos);
        PackageItemInfo p = mWidgets.getPackageItemInfo(posInt);
        return p.titleSectionName;
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void updateVerticalScrollbarBounds() {
        int rowCount = mWidgets.getPackageSize();
        verticalScrollbarBounds.setEmpty();

        // Skip early if, there are no items.
        if (rowCount == 0) {
            return;
        }

        // Skip early if, there no child laid out in the container.
        getCurScrollState(scrollPosState);
        if (scrollPosState.rowIndex < 0) {
            return;
        }

        int actualHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        int totalScrollHeight = rowCount * scrollPosState.rowHeight;
        // Skip early if the height of all the rows are actually less than the container height.
        if (totalScrollHeight < actualHeight) {
            verticalScrollbarBounds.setEmpty();
            return;
        }

        int scrollbarHeight = (int) (actualHeight / ((float) totalScrollHeight / actualHeight));
        int availableY = totalScrollHeight - actualHeight;
        int availableScrollY = actualHeight - scrollbarHeight;
        int y = (scrollPosState.rowIndex * scrollPosState.rowHeight)
                - scrollPosState.rowTopOffset;
        y = getPaddingTop() +
                (int) (((float) (getPaddingTop() + y) / availableY) * availableScrollY);

        // Calculate the position and size of the scroll bar.
        int x = getWidth() - getScrollbarWidth() - mBackgroundPadding.right;
        if (Utilities.isRtl(getResources())) {
            x = mBackgroundPadding.left;
        }
        verticalScrollbarBounds.set(x, y, x + getScrollbarWidth(), y + scrollbarHeight);
    }

    /**
     * Returns the current scroll state.
     */
    private void getCurScrollState(ScrollPositionState stateOut) {
        stateOut.rowIndex = -1;
        stateOut.rowTopOffset = -1;
        stateOut.rowHeight = -1;

        int rowCount = mWidgets.getPackageSize();

        // Return early if there are no items
        if (rowCount == 0) {
            return;
        }
        View child = getChildAt(0);
        int position = getChildPosition(child);

        stateOut.rowIndex = position;
        stateOut.rowTopOffset = getLayoutManager().getDecoratedTop(child);
        stateOut.rowHeight = child.getHeight();
    }
}