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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.List;

/**
 * A RecyclerView with custom fastscroll support.  This is the main container for the all apps
 * icons.
 */
public class AppsContainerRecyclerView extends BaseContainerRecyclerView {

    /**
     * The current scroll state of the recycler view.  We use this in updateVerticalScrollbarBounds()
     * and scrollToPositionAtProgress() to determine the scroll position of the recycler view so
     * that we can calculate what the scroll bar looks like, and where to jump to from the fast
     * scroller.
     */
    private static class ScrollPositionState {
        // The index of the first visible row
        int rowIndex;
        // The offset of the first visible row
        int rowTopOffset;
        // The height of a given row (they are currently all the same height)
        int rowHeight;
    }

    private static final float FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR = 1.5f;

    private AlphabeticalAppsList mApps;
    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;

    private Drawable mScrollbar;
    private Drawable mFastScrollerBg;
    private Rect mTmpFastScrollerInvalidateRect = new Rect();
    private Rect mFastScrollerBounds = new Rect();
    private Rect mVerticalScrollbarBounds = new Rect();
    private boolean mDraggingFastScroller;
    private String mFastScrollSectionName;
    private Paint mFastScrollTextPaint;
    private Rect mFastScrollTextBounds = new Rect();
    private float mFastScrollAlpha;
    private int mPredictionBarHeight;
    private int mDownX;
    private int mDownY;
    private int mLastX;
    private int mLastY;
    private int mScrollbarWidth;
    private int mScrollbarMinHeight;
    private int mScrollbarInset;
    private Rect mBackgroundPadding = new Rect();
    private ScrollPositionState mScrollPosState = new ScrollPositionState();

    public AppsContainerRecyclerView(Context context) {
        this(context, null);
    }

    public AppsContainerRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsContainerRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AppsContainerRecyclerView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr);

        Resources res = context.getResources();
        int fastScrollerSize = res.getDimensionPixelSize(R.dimen.apps_view_fast_scroll_popup_size);
        mScrollbar = res.getDrawable(R.drawable.apps_list_scrollbar_thumb);
        mFastScrollerBg = res.getDrawable(R.drawable.apps_list_fastscroll_bg);
        mFastScrollerBg.setBounds(0, 0, fastScrollerSize, fastScrollerSize);
        mFastScrollTextPaint = new Paint();
        mFastScrollTextPaint.setColor(Color.WHITE);
        mFastScrollTextPaint.setAntiAlias(true);
        mFastScrollTextPaint.setTextSize(res.getDimensionPixelSize(
                R.dimen.apps_view_fast_scroll_text_size));
        mScrollbarWidth = res.getDimensionPixelSize(R.dimen.apps_view_fast_scroll_bar_width);
        mScrollbarMinHeight =
                res.getDimensionPixelSize(R.dimen.apps_view_fast_scroll_bar_min_height);
        mScrollbarInset =
                res.getDimensionPixelSize(R.dimen.apps_view_fast_scroll_scrubber_touch_inset);
        setFastScrollerAlpha(getFastScrollerAlpha());
        setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    /**
     * Sets the list of apps in this view, used to determine the fastscroll position.
     */
    public void setApps(AlphabeticalAppsList apps) {
        mApps = apps;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        // Register a change listener to update the scroll position state whenever the data set
        // changes.
        adapter.registerAdapterDataObserver(new AdapterDataObserver() {
            @Override
            public void onChanged() {
                post(new Runnable() {
                    @Override
                    public void run() {
                        refreshCurScrollPosition();
                    }
                });
            }
        });
        super.setAdapter(adapter);
    }

    /**
     * Sets the number of apps per row in this recycler view.
     */
    public void setNumAppsPerRow(int numAppsPerRow, int numPredictedAppsPerRow) {
        mNumAppsPerRow = numAppsPerRow;
        mNumPredictedAppsPerRow = numPredictedAppsPerRow;
    }

    public void updateBackgroundPadding(Drawable background) {
        background.getPadding(mBackgroundPadding);
    }

    /**
     * Sets the prediction bar height.
     */
    public void setPredictionBarHeight(int height) {
        mPredictionBarHeight = height;
    }

    /**
     * Sets the fast scroller alpha.
     */
    public void setFastScrollerAlpha(float alpha) {
        mFastScrollAlpha = alpha;
        invalidateFastScroller(mFastScrollerBounds);
    }

    /**
     * Gets the fast scroller alpha.
     */
    public float getFastScrollerAlpha() {
        return mFastScrollAlpha;
    }

    /**
     * Returns the scroll bar width.
     */
    public int getScrollbarWidth() {
        return mScrollbarWidth;
    }

    /**
     * Scrolls this recycler view to the top.
     */
    public void scrollToTop() {
        scrollToPosition(0);
        updateScrollY(0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addOnItemTouchListener(this);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawVerticalScrubber(canvas);
        drawFastScrollerPopup(canvas);
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
                mDownX = mLastX = x;
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
                    mLastX = x;
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

    /**
     * Animates the visibility of the fast scroller popup.
     */
    private void animateFastScrollerVisibility(boolean visible) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(this, "fastScrollerAlpha", visible ? 1f : 0f);
        anim.setDuration(visible ? 200 : 150);
        anim.start();
    }

    /**
     * Returns whether a given point is near the scrollbar.
     */
    private boolean isPointNearScrollbar(int x, int y) {
        // Check if we are scrolling
        updateVerticalScrollbarBounds();
        mVerticalScrollbarBounds.inset(mScrollbarInset, mScrollbarInset);
        return mVerticalScrollbarBounds.contains(x, y);
    }

    /**
     * Draws the fast scroller popup.
     */
    private void drawFastScrollerPopup(Canvas canvas) {
        if (mFastScrollAlpha > 0f && !mFastScrollSectionName.isEmpty()) {
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
     * Draws the vertical scrollbar.
     */
    private void drawVerticalScrubber(Canvas canvas) {
        updateVerticalScrollbarBounds();

        // Draw the scroll bar
        int restoreCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.translate(mVerticalScrollbarBounds.left, mVerticalScrollbarBounds.top);
        mScrollbar.setBounds(0, 0, mScrollbarWidth, mVerticalScrollbarBounds.height());
        mScrollbar.draw(canvas);
        canvas.restoreToCount(restoreCount);
    }

    /**
     * Invalidates the fast scroller popup.
     */
    private void invalidateFastScroller(Rect bounds) {
        invalidate(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    private String scrollToPositionAtProgress(float touchFraction) {
        // Ensure that we have any sections
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollSections =
                mApps.getFastScrollerSections();
        if (fastScrollSections.isEmpty()) {
            return "";
        }

        // Stop the scroller if it is scrolling
        LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
        stopScroll();

        // If there is a prediction bar, then capture the appropriate area for the prediction bar
        float predictionBarFraction = 0f;
        if (!mApps.getPredictedApps().isEmpty()) {
            predictionBarFraction = (float) mNumPredictedAppsPerRow / mApps.getSize();
            if (touchFraction <= predictionBarFraction) {
                // Scroll to the top of the view, where the prediction bar is
                layoutManager.scrollToPositionWithOffset(0, 0);
                updateScrollY(0);
                return "";
            }
        }

        // Since the app ranges are from 0..1, we need to map the touch fraction back to 0..1 from
        // predictionBarFraction..1
        touchFraction = (touchFraction - predictionBarFraction) *
                (1f / (1f - predictionBarFraction));
        AlphabeticalAppsList.FastScrollSectionInfo lastScrollSection = fastScrollSections.get(0);
        for (int i = 1; i < fastScrollSections.size(); i++) {
            AlphabeticalAppsList.FastScrollSectionInfo scrollSection = fastScrollSections.get(i);
            if (lastScrollSection.appRangeFraction <= touchFraction &&
                    touchFraction < scrollSection.appRangeFraction) {
                break;
            }
            lastScrollSection = scrollSection;
        }

        // We need to workaround the RecyclerView to get the right scroll position
        refreshCurScrollPosition();

        // Scroll to the view at the position, anchored at the top of the screen. We call the scroll
        // method on the LayoutManager directly since it is not exposed by RecyclerView.
        layoutManager.scrollToPositionWithOffset(lastScrollSection.appItem.position, 0);

        return lastScrollSection.sectionName;
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    private void updateVerticalScrollbarBounds() {
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();

        // Skip early if there are no items
        if (items.isEmpty()) {
            mVerticalScrollbarBounds.setEmpty();
            return;
        }

        // Find the index and height of the first visible row (all rows have the same height)
        int x;
        int y;
        int predictionBarHeight = mApps.getPredictedApps().isEmpty() ? 0 : mPredictionBarHeight;
        int rowCount = getNumRows();
        getCurScrollState(mScrollPosState, items);
        if (mScrollPosState.rowIndex != -1) {
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            int totalScrollHeight = rowCount * mScrollPosState.rowHeight + predictionBarHeight;
            if (totalScrollHeight > height) {
                int scrollbarHeight = Math.max(mScrollbarMinHeight,
                        (int) (height / ((float) totalScrollHeight / height)));

                // Calculate the position and size of the scroll bar
                if (Utilities.isRtl(getResources())) {
                    x = mBackgroundPadding.left;
                } else {
                    x = getWidth() - mBackgroundPadding.right - mScrollbarWidth;
                }

                // To calculate the offset, we compute the percentage of the total scrollable height
                // that the user has already scrolled and then map that to the scroll bar bounds
                int availableY = totalScrollHeight - height;
                int availableScrollY = height - scrollbarHeight;
                y = (mScrollPosState.rowIndex * mScrollPosState.rowHeight) + predictionBarHeight
                        - mScrollPosState.rowTopOffset;
                y = getPaddingTop() +
                        (int) (((float) (getPaddingTop() + y) / availableY) * availableScrollY);

                mVerticalScrollbarBounds.set(x, y, x + mScrollbarWidth, y + scrollbarHeight);
                return;
            }
        }
        mVerticalScrollbarBounds.setEmpty();
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
                x = mBackgroundPadding.left + getScrollBarSize();
            } else {
                x = getWidth() - getPaddingRight() - getScrollBarSize() - bgBounds.width();
            }
            y = mLastY - (int) (FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * bgBounds.height());
            y = Math.max(getPaddingTop(), Math.min(y, getHeight() - getPaddingBottom() -
                    bgBounds.height()));
            mFastScrollerBounds.set(x, y, x + bgBounds.width(), y + bgBounds.height());
        } else {
            mFastScrollerBounds.setEmpty();
        }
    }

    /**
     * Returns the row index for a app index in the list.
     */
    private int findRowForAppIndex(int index) {
        List<AlphabeticalAppsList.SectionInfo> sections = mApps.getSections();
        int appIndex = 0;
        int rowCount = 0;
        for (AlphabeticalAppsList.SectionInfo info : sections) {
            int numRowsInSection = (int) Math.ceil((float) info.numApps / mNumAppsPerRow);
            if (appIndex + info.numApps > index) {
                return rowCount + ((index - appIndex) / mNumAppsPerRow);
            }
            appIndex += info.numApps;
            rowCount += numRowsInSection;
        }
        return appIndex;
    }

    /**
     * Returns the total number of rows in the list.
     */
    private int getNumRows() {
        List<AlphabeticalAppsList.SectionInfo> sections = mApps.getSections();
        int rowCount = 0;
        for (AlphabeticalAppsList.SectionInfo info : sections) {
            int numRowsInSection = (int) Math.ceil((float) info.numApps / mNumAppsPerRow);
            rowCount += numRowsInSection;
        }
        return rowCount;
    }

    /**
     * Forces a refresh of the scroll position to any scroll listener.
     */
    private void refreshCurScrollPosition() {
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
        getCurScrollState(mScrollPosState, items);
        if (mScrollPosState.rowIndex != -1) {
            int predictionBarHeight = mApps.getPredictedApps().isEmpty() ? 0 : mPredictionBarHeight;
            int scrollY = getPaddingTop() + (mScrollPosState.rowIndex * mScrollPosState.rowHeight) +
                    predictionBarHeight - mScrollPosState.rowTopOffset;
            updateScrollY(scrollY);
        }
    }

    /**
     * Returns the current scroll state.
     */
    private void getCurScrollState(ScrollPositionState stateOut,
            List<AlphabeticalAppsList.AdapterItem> items) {
        stateOut.rowIndex = -1;
        stateOut.rowTopOffset = -1;
        stateOut.rowHeight = -1;

        // Return early if there are no items
        if (items.isEmpty()) {
            return;
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            int position = getChildPosition(child);
            if (position != NO_POSITION) {
                AlphabeticalAppsList.AdapterItem item = items.get(position);
                if (item.viewType == AppsGridAdapter.ICON_VIEW_TYPE) {
                    stateOut.rowIndex = findRowForAppIndex(item.appIndex);
                    stateOut.rowTopOffset = getLayoutManager().getDecoratedTop(child);
                    stateOut.rowHeight = child.getHeight();
                    break;
                }
            }
        }
    }
}
