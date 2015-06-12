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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.launcher3.util.Thunk;

/**
 * A base {@link RecyclerView}, which does the following:
 * <ul>
 *   <li> NOT intercept a touch unless the scrolling velocity is below a predefined threshold.
 *   <li> Enable fast scroller.
 * </ul>
 */
public class BaseRecyclerView extends RecyclerView
        implements RecyclerView.OnItemTouchListener {

    private static final int SCROLL_DELTA_THRESHOLD_DP = 4;

    /** Keeps the last known scrolling delta/velocity along y-axis. */
    @Thunk int mDy = 0;
    private float mDeltaThreshold;

    //
    // Keeps track of variables required for the second function of this class: fast scroller.
    //

    private static final float FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR = 1.5f;

    /**
     * The current scroll state of the recycler view.  We use this in updateVerticalScrollbarBounds()
     * and scrollToPositionAtProgress() to determine the scroll position of the recycler view so
     * that we can calculate what the scroll bar looks like, and where to jump to from the fast
     * scroller.
     */
    public static class ScrollPositionState {
        // The index of the first visible row
        public int rowIndex;
        // The offset of the first visible row
        public int rowTopOffset;
        // The height of a given row (they are currently all the same height)
        public int rowHeight;
    }
    // Should be maintained inside overriden method #updateVerticalScrollbarBounds
    public ScrollPositionState scrollPosState = new ScrollPositionState();
    public Rect verticalScrollbarBounds = new Rect();

    private boolean mDraggingFastScroller;

    private Drawable mScrollbar;
    private Drawable mFastScrollerBg;
    private Rect mTmpFastScrollerInvalidateRect = new Rect();
    private Rect mFastScrollerBounds = new Rect();

    private String mFastScrollSectionName;
    private Paint mFastScrollTextPaint;
    private Rect mFastScrollTextBounds = new Rect();
    private float mFastScrollAlpha;

    private int mDownX;
    private int mDownY;
    private int mLastY;
    private int mScrollbarWidth;
    private int mScrollbarInset;
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

        ScrollListener listener = new ScrollListener();
        setOnScrollListener(listener);

        Resources res = context.getResources();
        int fastScrollerSize = res.getDimensionPixelSize(R.dimen.all_apps_fast_scroll_popup_size);
        mScrollbar = res.getDrawable(R.drawable.all_apps_scrollbar_thumb);
        mFastScrollerBg = res.getDrawable(R.drawable.all_apps_fastscroll_bg);
        mFastScrollerBg.setBounds(0, 0, fastScrollerSize, fastScrollerSize);
        mFastScrollTextPaint = new Paint();
        mFastScrollTextPaint.setColor(Color.WHITE);
        mFastScrollTextPaint.setAntiAlias(true);
        mFastScrollTextPaint.setTextSize(res.getDimensionPixelSize(
                R.dimen.all_apps_fast_scroll_text_size));
        mScrollbarWidth = res.getDimensionPixelSize(R.dimen.all_apps_fast_scroll_bar_width);
        mScrollbarInset =
                res.getDimensionPixelSize(R.dimen.all_apps_fast_scroll_scrubber_touch_inset);
        setFastScrollerAlpha(mFastScrollAlpha);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private class ScrollListener extends OnScrollListener {
        public ScrollListener() {
            // Do nothing
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            mDy = dy;
        }
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
        ViewConfiguration config = ViewConfiguration.get(getContext());

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
                break;
            case MotionEvent.ACTION_MOVE:
                // Check if we are scrolling
                if (!mDraggingFastScroller && isPointNearScrollbar(mDownX, mDownY) &&
                        Math.abs(y - mDownY) > config.getScaledTouchSlop()) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    mDraggingFastScroller = true;
                    animateFastScrollerVisibility(true);
                }
                if (mDraggingFastScroller) {
                    mLastY = y;

                    // Scroll to the right position, and update the section name
                    int top = getPaddingTop() + (mFastScrollerBg.getBounds().height() / 2);
                    int bottom = getHeight() - getPaddingBottom() -
                            (mFastScrollerBg.getBounds().height() / 2);
                    float boundedY = (float) Math.max(top, Math.min(bottom, y));
                    mFastScrollSectionName = scrollToPositionAtProgress((boundedY - top) /
                            (bottom - top));

                    // Combine the old and new fast scroller bounds to create the full invalidate
                    // rect
                    mTmpFastScrollerInvalidateRect.set(mFastScrollerBounds);
                    updateFastScrollerBounds();
                    mTmpFastScrollerInvalidateRect.union(mFastScrollerBounds);
                    invalidateFastScroller(mTmpFastScrollerInvalidateRect);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDraggingFastScroller = false;
                animateFastScrollerVisibility(false);
                break;
        }
        return mDraggingFastScroller;
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

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawVerticalScrubber(canvas);
        drawFastScrollerPopup(canvas);
    }

    /**
     * Draws the vertical scrollbar.
     */
    private void drawVerticalScrubber(Canvas canvas) {
        updateVerticalScrollbarBounds();

        // Draw the scroll bar
        int restoreCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.translate(verticalScrollbarBounds.left, verticalScrollbarBounds.top);
        mScrollbar.setBounds(0, 0, mScrollbarWidth, verticalScrollbarBounds.height());
        mScrollbar.draw(canvas);
        canvas.restoreToCount(restoreCount);
    }

    /**
     * Draws the fast scroller popup.
     */
    private void drawFastScrollerPopup(Canvas canvas) {
        if (mFastScrollAlpha > 0f && mFastScrollSectionName != null && !mFastScrollSectionName.isEmpty()) {
            // Draw the fast scroller popup
            int restoreCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.translate(mFastScrollerBounds.left, mFastScrollerBounds.top);
            mFastScrollerBg.setAlpha((int) (mFastScrollAlpha * 255));
            mFastScrollerBg.draw(canvas);
            mFastScrollTextPaint.setAlpha((int) (mFastScrollAlpha * 255));
            mFastScrollTextPaint.getTextBounds(mFastScrollSectionName, 0,
                    mFastScrollSectionName.length(), mFastScrollTextBounds);
            float textWidth = mFastScrollTextPaint.measureText(mFastScrollSectionName);
            canvas.drawText(mFastScrollSectionName,
                    (mFastScrollerBounds.width() - textWidth) / 2,
                    mFastScrollerBounds.height() -
                            (mFastScrollerBounds.height() - mFastScrollTextBounds.height()) / 2,
                    mFastScrollTextPaint);
            canvas.restoreToCount(restoreCount);
        }
    }

    /**
     * Returns the scroll bar width.
     */
    public int getScrollbarWidth() {
        return mScrollbarWidth;
    }

    /**
     * Sets the fast scroller alpha.
     */
    public void setFastScrollerAlpha(float alpha) {
        mFastScrollAlpha = alpha;
        invalidateFastScroller(mFastScrollerBounds);
    }

    /**
     * Returns the fast scroller alpha.
     */
    public float getFastScrollerAlpha() {
        return mFastScrollAlpha;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     * <p>Override in each subclass of this base class.
     */
    public String scrollToPositionAtProgress(float touchFraction) {
        return null;
    }

    /**
     * Updates the bounds for the scrollbar.
     * <p>Override in each subclass of this base class.
     */
    public void updateVerticalScrollbarBounds() {};

    /**
     * Animates the visibility of the fast scroller popup.
     */
    private void animateFastScrollerVisibility(final boolean visible) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(this, "fastScrollerAlpha", visible ? 1f : 0f);
        anim.setDuration(visible ? 200 : 150);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (visible) {
                    onFastScrollingStart();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!visible) {
                    onFastScrollingEnd();
                }
            }
        });
        anim.start();
    }

    /**
     * To be overridden by subclasses.
     */
    protected void onFastScrollingStart() {}

    /**
     * To be overridden by subclasses.
     */
    protected void onFastScrollingEnd() {}

    /**
     * Invalidates the fast scroller popup.
     */
    protected void invalidateFastScroller(Rect bounds) {
        invalidate(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    /**
     * Returns whether a given point is near the scrollbar.
     */
    private boolean isPointNearScrollbar(int x, int y) {
        // Check if we are scrolling
        updateVerticalScrollbarBounds();
        verticalScrollbarBounds.inset(mScrollbarInset, mScrollbarInset);
        return verticalScrollbarBounds.contains(x, y);
    }

    /**
     * Updates the bounds for the fast scroller.
     */
    private void updateFastScrollerBounds() {
        if (mFastScrollAlpha > 0f && !mFastScrollSectionName.isEmpty()) {
            int x;
            int y;

            // Calculate the position for the fast scroller popup
            Rect bgBounds = mFastScrollerBg.getBounds();
            if (Utilities.isRtl(getResources())) {
                x = mBackgroundPadding.left + (2 * getScrollbarWidth());
            } else {
                x = getWidth() - mBackgroundPadding.right - (2 * getScrollbarWidth()) -
                        bgBounds.width();
            }
            y = mLastY - (int) (FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * bgBounds.height());
            y = Math.max(getPaddingTop(), Math.min(y, getHeight() - getPaddingBottom() -
                    bgBounds.height()));
            mFastScrollerBounds.set(x, y, x + bgBounds.width(), y + bgBounds.height());
        } else {
            mFastScrollerBounds.setEmpty();
        }
    }
}