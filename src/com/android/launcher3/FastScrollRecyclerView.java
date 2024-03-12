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

import static com.android.launcher3.testing.shared.TestProtocol.SCROLL_FINISHED_MESSAGE;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.app.animation.Interpolators;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.views.RecyclerViewFastScroller;


/**
 * A base {@link RecyclerView}, which does the following:
 * <ul>
 *   <li> NOT intercept a touch unless the scrolling velocity is below a predefined threshold.
 *   <li> Enable fast scroller.
 * </ul>
 */
public abstract class FastScrollRecyclerView extends RecyclerView  {

    protected RecyclerViewFastScroller mScrollbar;

    public FastScrollRecyclerView(Context context) {
        this(context, null);
    }

    public FastScrollRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FastScrollRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void bindFastScrollbar(RecyclerViewFastScroller scrollbar) {
        mScrollbar = scrollbar;
        mScrollbar.setRecyclerView(this);
        onUpdateScrollbar(0);
    }

    @Nullable
    public RecyclerViewFastScroller getScrollbar() {
        return mScrollbar;
    }

    public int getScrollBarTop() {
        return getPaddingTop();
    }

    public int getScrollBarMarginBottom() {
        return getPaddingBottom();
    }

    /**
     * Returns the height of the fast scroll bar
     */
    public int getScrollbarTrackHeight() {
        return mScrollbar.getHeight() - getScrollBarTop() - getScrollBarMarginBottom();
    }

    /**
     * Returns the available scroll height:
     *   AvailableScrollHeight = Total height of the all items - last page height
     */
    protected int getAvailableScrollHeight() {
        // AvailableScrollHeight = Total height of the all items - first page height
        int firstPageHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        int availableScrollHeight = computeVerticalScrollRange() - firstPageHeight;
        return Math.max(0, availableScrollHeight);
    }

    /**
     * Returns the available scroll bar height:
     *   AvailableScrollBarHeight = Total height of the visible view - thumb height
     */
    protected int getAvailableScrollBarHeight() {
        return getScrollbarTrackHeight() - mScrollbar.getThumbHeight();
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
        return computeVerticalScrollOffset() == 0;
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
     */
    public abstract CharSequence scrollToPositionAtProgress(float touchFraction);

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
            AccessibilityManagerCompat.sendTestProtocolEventToTest(getContext(),
                    SCROLL_FINISHED_MESSAGE);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (isLayoutSuppressed()) info.setScrollable(false);
    }

    /**
     * Scrolls this recycler view to the top.
     */
    public void scrollToTop() {
        if (mScrollbar != null) {
            mScrollbar.reattachThumbToScroll();
        }
        scrollToPosition(0);
    }
}
