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

package com.android.launcher3;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.views.RecyclerViewFastScroller;

import androidx.recyclerview.widget.RecyclerView;


/**
 * A base {@link RecyclerView}, which does the following:
 * <ul>
 *   <li> NOT intercept a touch unless the scrolling velocity is below a predefined threshold.
 *   <li> Enable fast scroller.
 * </ul>
 */
public abstract class BaseRecyclerView extends RecyclerView  {

    protected RecyclerViewFastScroller mScrollbar;

    public BaseRecyclerView(Context context) {
        this(context, null);
    }

    public BaseRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        bindFastScrollbar();
    }

    public void bindFastScrollbar() {
        ViewGroup parent = (ViewGroup) getParent().getParent();
        mScrollbar = parent.findViewById(R.id.fast_scroller);
        mScrollbar.setRecyclerView(this, parent.findViewById(R.id.fast_scroller_popup));
        onUpdateScrollbar(0);
    }

    public RecyclerViewFastScroller getScrollbar() {
        return mScrollbar;
    }

    public int getScrollBarTop() {
        return getPaddingTop();
    }

    /**
     * Returns the height of the fast scroll bar
     */
    public int getScrollbarTrackHeight() {
        return mScrollbar.getHeight() - getScrollBarTop() - getPaddingBottom();
    }

    /**
     * Returns the available scroll height:
     *   AvailableScrollHeight = Total height of the all items - last page height
     */
    protected abstract int getAvailableScrollHeight();

    /**
     * Returns the available scroll bar height:
     *   AvailableScrollBarHeight = Total height of the visible view - thumb height
     */
    protected int getAvailableScrollBarHeight() {
        int availableScrollBarHeight = getScrollbarTrackHeight() - mScrollbar.getThumbHeight();
        return availableScrollBarHeight;
    }

    /**
     * Updates the scrollbar thumb offset to match the visible scroll of the recycler view.  It does
     * this by mapping the available scroll area of the recycler view to the available space for the
     * scroll bar.
     *
     * @param scrollY the current scroll y
     */
    protected void synchronizeScrollBarThumbOffsetToViewScroll(int scrollY,
            int availableScrollHeight) {
        // Only show the scrollbar if there is height to be scrolled
        if (availableScrollHeight <= 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        // Calculate the current scroll position, the scrollY of the recycler view accounts for the
        // view padding, while the scrollBarY is drawn right up to the background padding (ignoring
        // padding)
        int scrollBarY =
                (int) (((float) scrollY / availableScrollHeight) * getAvailableScrollBarHeight());

        // Calculate the position and size of the scroll bar
        mScrollbar.setThumbOffsetY(scrollBarY);
    }

    /**
     * Returns whether the view itself will handle the touch event or not.
     * @param ev MotionEvent in {@param eventSource}
     */
    public boolean shouldContainerScroll(MotionEvent ev, View eventSource) {
        float[] point = new float[2];
        point[0] = ev.getX();
        point[1] = ev.getY();
        Utilities.mapCoordInSelfToDescendant(mScrollbar, eventSource, point);
        // IF the MotionEvent is inside the thumb, container should not be pulled down.
        if (mScrollbar.shouldBlockIntercept((int) point[0], (int) point[1])) {
            return false;
        }

        // IF scroller is at the very top OR there is no scroll bar because there is probably not
        // enough items to scroll, THEN it's okay for the container to be pulled down.
        if (getCurrentScrollY() == 0) {
            return true;
        }
        return false;
    }

    /**
     * @return whether fast scrolling is supported in the current state.
     */
    public boolean supportsFastScrolling() {
        return true;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     * <p>Override in each subclass of this base class.
     *
     * @return the scroll top of this recycler view.
     */
    public abstract int getCurrentScrollY();

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     * <p>Override in each subclass of this base class.
     */
    public abstract String scrollToPositionAtProgress(float touchFraction);

    /**
     * Updates the bounds for the scrollbar.
     * <p>Override in each subclass of this base class.
     */
    public abstract void onUpdateScrollbar(int dy);

    /**
     * <p>Override in each subclass of this base class.
     */
    public void onFastScrollCompleted() {}

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);

        if (state == SCROLL_STATE_IDLE) {
            AccessibilityManagerCompat.sendScrollFinishedEventToTest(getContext());
        }
    }
}