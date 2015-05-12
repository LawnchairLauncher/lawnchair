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
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.android.launcher3.util.Thunk;

/**
 * A base {@link RecyclerView}, which will NOT intercept a touch sequence unless the scrolling
 * velocity is below a predefined threshold.
 */
public class BaseContainerRecyclerView extends RecyclerView
        implements RecyclerView.OnItemTouchListener {

    /**
     * Listener to get notified when the absolute scroll changes.
     */
    public interface OnScrollToListener {
        void onScrolledTo(int x, int y);
    }

    private static final int SCROLL_DELTA_THRESHOLD_DP = 4;

    /** Keeps the last known scrolling delta/velocity along y-axis. */
    @Thunk int mDy = 0;
    @Thunk int mScrollY;
    private float mDeltaThreshold;
    private OnScrollToListener mScrollToListener;

    public BaseContainerRecyclerView(Context context) {
        this(context, null);
    }

    public BaseContainerRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseContainerRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDeltaThreshold = getResources().getDisplayMetrics().density * SCROLL_DELTA_THRESHOLD_DP;

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
            mScrollY += dy;
            if (mScrollToListener != null) {
                mScrollToListener.onScrolledTo(0, mScrollY);
            }
        }
    }

    /**
     * Sets an additional scroll listener, only needed for LMR1 version of the support lib.
     */
    public void setOnScrollListenerProxy(OnScrollToListener listener) {
        mScrollToListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addOnItemTouchListener(this);
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent ev) {
        if (shouldStopScroll(ev)) {
            stopScroll();
        }
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent ev) {
        // Do nothing.
    }

    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // DO NOT REMOVE, NEEDED IMPLEMENTATION FOR M BUILDS
    }

    /**
     * Updates the scroll position, used to workaround a RecyclerView issue with scrolling to
     * position.
     */
    protected void updateScrollY(int scrollY) {
        mScrollY = scrollY;
        if (mScrollToListener != null) {
            mScrollToListener.onScrolledTo(0, mScrollY);
        }
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
}