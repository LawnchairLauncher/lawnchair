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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.android.launcher3.util.Thunk;


/**
 * A base {@link RecyclerView}, which does the following:
 * <ul>
 *   <li> NOT intercept a touch unless the scrolling velocity is below a predefined threshold.
 *   <li> Enable fast scroller.
 * </ul>
 */
public abstract class BaseRecyclerView extends RecyclerView
        implements RecyclerView.OnItemTouchListener {

    private static final int SCROLL_DELTA_THRESHOLD_DP = 4;

    /** Keeps the last known scrolling delta/velocity along y-axis. */
    @Thunk int mDy = 0;
    private float mDeltaThreshold;

    /**
     * The current scroll state of the recycler view.  We use this in onUpdateScrollbar()
     * and scrollToPositionAtProgress() to determine the scroll position of the recycler view so
     * that we can calculate what the scroll bar looks like, and where to jump to from the fast
     * scroller.
     */
    public static class ScrollPositionState {
        // The index of the first visible row
        public int rowIndex;
        // The offset of the first visible row
        public int rowTopOffset;
        // The adapter position of the first visible item
        public int itemPos;
    }

    protected BaseRecyclerViewFastScrollBar mScrollbar;

    private int mDownX;
    private int mDownY;
    private int mLastY;
    protected Rect mBackgroundPadding = new Rect();

    public BaseRecyclerView(Context context) {
        this(context, null);
    }

    public BaseRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDeltaThreshold = getResources().getDisplayMetrics().density * SCROLL_DELTA_THRESHOLD_DP;
        mScrollbar = new BaseRecyclerViewFastScrollBar(this, getResources());

        ScrollListener listener = new ScrollListener();
        setOnScrollListener(listener);
    }

    private class ScrollListener extends OnScrollListener {
        public ScrollListener() {
            // Do nothing
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            mDy = dy;

            // TODO(winsonc): If we want to animate the section heads while scrolling, we can
            //                initiate that here if the recycler view scroll state is not
            //                RecyclerView.SCROLL_STATE_IDLE.

            onUpdateScrollbar(dy);
        }
    }

    public void reset() {
        mScrollbar.reattachThumbToScroll();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addOnItemTouchListener(this);
    }

    /**
     * We intercept the touch handling only to support fast scrolling when initiated from the
     * scroll bar.  Otherwise, we fall back to the default RecyclerView touch handling.
     */
    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent ev) {
        return handleTouchEvent(ev);
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent ev) {
        handleTouchEvent(ev);
    }

    /**
     * Handles the touch event and determines whether to show the fast scroller (or updates it if
     * it is already showing).
     */
    private boolean handleTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Keep track of the down positions
                mDownX = x;
                mDownY = mLastY = y;
                if (shouldStopScroll(ev)) {
                    stopScroll();
                }
                mScrollbar.handleTouchEvent(ev, mDownX, mDownY, mLastY);
                break;
            case MotionEvent.ACTION_MOVE:
                mLastY = y;
                mScrollbar.handleTouchEvent(ev, mDownX, mDownY, mLastY);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onFastScrollCompleted();
                mScrollbar.handleTouchEvent(ev, mDownX, mDownY, mLastY);
                break;
        }
        return mScrollbar.isDraggingThumb();
    }

    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // DO NOT REMOVE, NEEDED IMPLEMENTATION FOR M BUILDS
    }

    /**
     * Returns whether this {@link MotionEvent} should trigger the scroll to be stopped.
     */
    protected boolean shouldStopScroll(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if ((Math.abs(mDy) < mDeltaThreshold &&
                    getScrollState() != RecyclerView.SCROLL_STATE_IDLE)) {
                // now the touch events are being passed to the {@link WidgetCell} until the
                // touch sequence goes over the touch slop.
                return true;
            }
        }
        return false;
    }

    public void updateBackgroundPadding(Rect padding) {
        mBackgroundPadding.set(padding);
    }

    public Rect getBackgroundPadding() {
        return mBackgroundPadding;
    }

    /**
     * Returns the scroll bar width when the user is scrolling.
     */
    public int getMaxScrollbarWidth() {
        return mScrollbar.getThumbMaxWidth();
    }

    /**
     * Returns the visible height of the recycler view:
     *   VisibleHeight = View height - top padding - bottom padding
     */
    protected int getVisibleHeight() {
        int visibleHeight = getHeight() - mBackgroundPadding.top - mBackgroundPadding.bottom;
        return visibleHeight;
    }

    /**
     * Returns the available scroll height:
     *   AvailableScrollHeight = Total height of the all items - last page height
     */
    protected int getAvailableScrollHeight(int rowCount) {
        int totalHeight = getPaddingTop() + getTop(rowCount) + getPaddingBottom();
        int availableScrollHeight = totalHeight - getVisibleHeight();
        return availableScrollHeight;
    }

    /**
     * Returns the available scroll bar height:
     *   AvailableScrollBarHeight = Total height of the visible view - thumb height
     */
    protected int getAvailableScrollBarHeight() {
        int availableScrollBarHeight = getVisibleHeight() - mScrollbar.getThumbHeight();
        return availableScrollBarHeight;
    }

    /**
     * Returns the track color (ignoring alpha), can be overridden by each subclass.
     */
    public int getFastScrollerTrackColor(int defaultTrackColor) {
        return defaultTrackColor;
    }

    /**
     * Returns the inactive thumb color, can be overridden by each subclass.
     */
    public int getFastScrollerThumbInactiveColor(int defaultInactiveThumbColor) {
        return defaultInactiveThumbColor;
    }

    /**
     * Returns the scrollbar for this recycler view.
     */
    public BaseRecyclerViewFastScrollBar getScrollBar() {
        return mScrollbar;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        onUpdateScrollbar(0);
        mScrollbar.draw(canvas);
    }

    /**
     * Updates the scrollbar thumb offset to match the visible scroll of the recycler view.  It does
     * this by mapping the available scroll area of the recycler view to the available space for the
     * scroll bar.
     *
     * @param scrollPosState the current scroll position
     * @param rowCount the number of rows, used to calculate the total scroll height (assumes that
     *                 all rows are the same height)
     */
    protected void synchronizeScrollBarThumbOffsetToViewScroll(ScrollPositionState scrollPosState,
            int rowCount) {
        // Only show the scrollbar if there is height to be scrolled
        int availableScrollBarHeight = getAvailableScrollBarHeight();
        int availableScrollHeight = getAvailableScrollHeight(rowCount);
        if (availableScrollHeight <= 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        // Calculate the current scroll position, the scrollY of the recycler view accounts for the
        // view padding, while the scrollBarY is drawn right up to the background padding (ignoring
        // padding)
        int scrollY = getScrollTop(scrollPosState);
        int scrollBarY = mBackgroundPadding.top +
                (int) (((float) scrollY / availableScrollHeight) * availableScrollBarHeight);

        // Calculate the position and size of the scroll bar
        int scrollBarX;
        if (Utilities.isRtl(getResources())) {
            scrollBarX = mBackgroundPadding.left;
        } else {
            scrollBarX = getWidth() - mBackgroundPadding.right - mScrollbar.getThumbWidth();
        }
        mScrollbar.setThumbOffset(scrollBarX, scrollBarY);
    }

    /**
     * @return whether fast scrolling is supported in the current state.
     */
    protected boolean supportsFastScrolling() {
        return true;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     * <p>Override in each subclass of this base class.
     *
     * @return the scroll top of this recycler view.
     */
    protected int getScrollTop(ScrollPositionState scrollPosState) {
        return getPaddingTop() + getTop(scrollPosState.rowIndex) -
                scrollPosState.rowTopOffset;
    }

    /**
     * Returns information about the item that the recycler view is currently scrolled to.
     */
    protected abstract void getCurScrollState(ScrollPositionState stateOut, int viewTypeMask);

    /**
     * Returns the top (or y position) of the row at the specified index.
     */
    protected abstract int getTop(int rowIndex);

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     * <p>Override in each subclass of this base class.
     */
    protected abstract String scrollToPositionAtProgress(float touchFraction);

    /**
     * Updates the bounds for the scrollbar.
     * <p>Override in each subclass of this base class.
     */
    protected abstract void onUpdateScrollbar(int dy);

    /**
     * <p>Override in each subclass of this base class.
     */
    protected void onFastScrollCompleted() {}
}