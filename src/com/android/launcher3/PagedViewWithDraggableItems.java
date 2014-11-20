/*
 * Copyright (C) 2010 The Android Open Source Project
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


/* Class that does most of the work of enabling dragging items out of a PagedView by performing a
 * vertical drag. Used by both CustomizePagedView and AllAppsPagedView.
 * Subclasses must do the following:
 *   * call setDragSlopeThreshold after making an instance of the PagedViewWithDraggableItems
 *   * call child.setOnLongClickListener(this) and child.setOnTouchListener(this) on all children
 *       (good place to do it is in syncPageItems)
 *   * override beginDragging(View) (but be careful to call super.beginDragging(View)
 *
 */
public abstract class PagedViewWithDraggableItems extends PagedView
    implements View.OnLongClickListener, View.OnTouchListener {
    private View mLastTouchedItem;
    private boolean mIsDragging;
    private boolean mIsDragEnabled;
    private float mDragSlopeThreshold;
    private Launcher mLauncher;

    public PagedViewWithDraggableItems(Context context) {
        this(context, null);
    }

    public PagedViewWithDraggableItems(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewWithDraggableItems(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = (Launcher) context;
    }

    protected boolean beginDragging(View v) {
        boolean wasDragging = mIsDragging;
        mIsDragging = true;
        return !wasDragging;
    }

    protected void cancelDragging() {
        mIsDragging = false;
        mLastTouchedItem = null;
        mIsDragEnabled = false;
    }

    private void handleTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                cancelDragging();
                mIsDragEnabled = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTouchState != TOUCH_STATE_SCROLLING && !mIsDragging && mIsDragEnabled) {
                    determineDraggingStart(ev);
                }
                break;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        handleTouchEvent(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        handleTouchEvent(ev);
        return super.onTouchEvent(ev);
    }

    public void trimMemory() {
        mLastTouchedItem = null;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mLastTouchedItem = v;
        mIsDragEnabled = true;
        return false;
    }

    @Override
    public boolean onLongClick(View v) {
        // Return early if this is not initiated from a touch
        if (!v.isInTouchMode()) return false;
        // Return early if we are still animating the pages
        if (mNextPage != INVALID_PAGE) return false;
        // When we have exited all apps or are in transition, disregard long clicks
        if (!mLauncher.isAllAppsVisible() ||
                mLauncher.getWorkspace().isSwitchingState()) return false;
        // Return if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return false;

        return beginDragging(v);
    }

    /*
     * Determines if we should change the touch state to start scrolling after the
     * user moves their touch point too far.
     */
    protected void determineScrollingStart(MotionEvent ev) {
        if (!mIsDragging) super.determineScrollingStart(ev);
    }

    /*
     * Determines if we should change the touch state to start dragging after the
     * user moves their touch point far enough.
     */
    protected void determineDraggingStart(MotionEvent ev) {
        /*
         * Locally do absolute value. mLastMotionX is set to the y value
         * of the down event.
         */
        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
        final float x = ev.getX(pointerIndex);
        final float y = ev.getY(pointerIndex);
        final int xDiff = (int) Math.abs(x - mLastMotionX);
        final int yDiff = (int) Math.abs(y - mLastMotionY);

        final int touchSlop = mTouchSlop;
        boolean yMoved = yDiff > touchSlop;
        boolean isUpwardMotion = (yDiff / (float) xDiff) > mDragSlopeThreshold;

        if (isUpwardMotion && yMoved && mLastTouchedItem != null) {
            // Drag if the user moved far enough along the Y axis
            beginDragging(mLastTouchedItem);

            // Cancel any pending long press
            if (mAllowLongPress) {
                mAllowLongPress = false;
                // Try canceling the long press. It could also have been scheduled
                // by a distant descendant, so use the mAllowLongPress flag to block
                // everything
                final View currentPage = getPageAt(mCurrentPage);
                if (currentPage != null) {
                    currentPage.cancelLongPress();
                }
            }
        }
    }

    public void setDragSlopeThreshold(float dragSlopeThreshold) {
        mDragSlopeThreshold = dragSlopeThreshold;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelDragging();
        super.onDetachedFromWindow();
    }
}
