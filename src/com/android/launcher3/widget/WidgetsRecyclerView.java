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
import android.graphics.Canvas;
import android.graphics.Color;
import android.support.v7.widget.LinearLayoutManager;
import android.util.AttributeSet;
import android.view.View;
import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.R;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.model.WidgetsModel;

/**
 * The widgets recycler view.
 */
public class WidgetsRecyclerView extends BaseRecyclerView {

    private static final String TAG = "WidgetsRecyclerView";
    private WidgetsModel mWidgets;
    private ScrollPositionState mScrollPosState = new ScrollPositionState();

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

    public int getFastScrollerTrackColor(int defaultTrackColor) {
        return Color.WHITE;
    }

    /**
     * Sets the widget model in this view, used to determine the fast scroll position.
     */
    public void setWidgets(WidgetsModel widgets) {
        mWidgets = widgets;
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

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public String scrollToPositionAtProgress(float touchFraction) {
        // Skip early if widgets are not bound.
        if (mWidgets == null) {
            return "";
        }

        // Skip early if there are no widgets.
        int rowCount = mWidgets.getPackageSize();
        if (rowCount == 0) {
            return "";
        }

        // Stop the scroller if it is scrolling
        stopScroll();

        getCurScrollState(mScrollPosState);
        float pos = rowCount * touchFraction;
        int availableScrollHeight = getAvailableScrollHeight(rowCount, mScrollPosState.rowHeight);
        LinearLayoutManager layoutManager = ((LinearLayoutManager) getLayoutManager());
        layoutManager.scrollToPositionWithOffset(0, (int) -(availableScrollHeight * touchFraction));

        int posInt = (int) ((touchFraction == 1)? pos -1 : pos);
        PackageItemInfo p = mWidgets.getPackageItemInfo(posInt);
        return p.titleSectionName;
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void onUpdateScrollbar(int dy) {
        // Skip early if widgets are not bound.
        if (mWidgets == null) {
            return;
        }

        // Skip early if there are no widgets.
        int rowCount = mWidgets.getPackageSize();
        if (rowCount == 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        // Skip early if, there no child laid out in the container.
        getCurScrollState(mScrollPosState);
        if (mScrollPosState.rowIndex < 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        synchronizeScrollBarThumbOffsetToViewScroll(mScrollPosState, rowCount);
    }

    /**
     * Returns the current scroll state.
     */
    protected void getCurScrollState(ScrollPositionState stateOut) {
        stateOut.rowIndex = -1;
        stateOut.rowTopOffset = -1;
        stateOut.rowHeight = -1;

        // Skip early if widgets are not bound.
        if (mWidgets == null) {
            return;
        }

        // Return early if there are no items
        int rowCount = mWidgets.getPackageSize();
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