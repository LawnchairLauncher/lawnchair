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
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.model.WidgetsModel;

/**
 * The widgets recycler view.
 */
public class WidgetsRecyclerView extends BaseRecyclerView {

    private WidgetsModel mWidgets;
    private Rect mBackgroundPadding = new Rect();

    public WidgetsRecyclerView(Context context) {
        this(context, null);
    }

    public WidgetsRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addOnItemTouchListener(this);
    }

    public void updateBackgroundPadding(Drawable background) {
        background.getPadding(mBackgroundPadding);
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
        // Ensure that we have any sections
        return "";
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void updateVerticalScrollbarBounds() {
        int rowCount = mWidgets.getPackageSize();

        // Skip early if there are no items.
        if (rowCount == 0) {
            verticalScrollbarBounds.setEmpty();
            return;
        }

        int x, y;
        getCurScrollState(scrollPosState);
        if (scrollPosState.rowIndex < 0) {
            verticalScrollbarBounds.setEmpty();
        }
        // TODO
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
        // TODO
    }
}