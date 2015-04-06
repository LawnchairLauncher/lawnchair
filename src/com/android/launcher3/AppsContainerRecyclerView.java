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
public class AppsContainerRecyclerView extends RecyclerView
        implements RecyclerView.OnItemTouchListener {

    private static final float FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR = 1.5f;

    private AlphabeticalAppsList mApps;
    private int mNumAppsPerRow;

    private Drawable mScrollbar;
    private Drawable mFastScrollerBg;
    private Rect mVerticalScrollbarBounds = new Rect();
    private boolean mDraggingFastScroller;
    private String mFastScrollSectionName;
    private Paint mFastScrollTextPaint;
    private Rect mFastScrollTextBounds = new Rect();
    private float mFastScrollAlpha;
    private int mDownX;
    private int mDownY;
    private int mLastX;
    private int mLastY;
    private int mScrollbarWidth;
    private int mScrollbarMinHeight;
    private int mScrollbarInset;

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
    }

    /**
     * Sets the list of apps in this view, used to determine the fastscroll position.
     */
    public void setApps(AlphabeticalAppsList apps) {
        mApps = apps;
    }

    /**
     * Sets the number of apps per row in this recycler view.
     */
    public void setNumAppsPerRow(int rowSize) {
        mNumAppsPerRow = rowSize;
    }

    /**
     * Sets the fast scroller alpha.
     */
    public void setFastScrollerAlpha(float alpha) {
        mFastScrollAlpha = alpha;
        invalidateFastScroller();
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

    @Override
    protected void onFinishInflate() {
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
                stopScroll();
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
                    invalidateFastScroller();
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
        if (mFastScrollAlpha > 0f) {
            int x;
            int y;
            boolean isRtl = (getResources().getConfiguration().getLayoutDirection() ==
                    LAYOUT_DIRECTION_RTL);

            // Calculate the position for the fast scroller popup
            Rect bgBounds = mFastScrollerBg.getBounds();
            if (isRtl) {
                x = getPaddingLeft() + getScrollBarSize();
            } else {
                x = getWidth() - getPaddingRight() - getScrollBarSize() - bgBounds.width();
            }
            y = mLastY - (int) (FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * bgBounds.height());
            y = Math.max(getPaddingTop(), Math.min(y, getHeight() - getPaddingBottom() -
                    bgBounds.height()));

            // Draw the fast scroller popup
            int restoreCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.translate(x, y);
            mFastScrollerBg.setAlpha((int) (mFastScrollAlpha * 255));
            mFastScrollerBg.draw(canvas);
            mFastScrollTextPaint.setAlpha((int) (mFastScrollAlpha * 255));
            mFastScrollTextPaint.getTextBounds(mFastScrollSectionName, 0,
                    mFastScrollSectionName.length(), mFastScrollTextBounds);
            canvas.drawText(mFastScrollSectionName,
                    (bgBounds.width() - mFastScrollTextBounds.width()) / 2,
                    bgBounds.height() - (bgBounds.height() - mFastScrollTextBounds.height()) / 2,
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
    private void invalidateFastScroller() {
        invalidate(getWidth() - getPaddingRight() - getScrollBarSize() -
                mFastScrollerBg.getIntrinsicWidth(), 0, getWidth(), getHeight());
    }

    /**
     * Maps the progress (from 0..1) to the position that should be visible
     */
    private String scrollToPositionAtProgress(float progress) {
        List<AlphabeticalAppsList.SectionInfo> sections = mApps.getSections();
        if (sections.isEmpty()) {
            return "";
        }

        // Find the position of the first application in the section that contains the row at the
        // current progress
        int rowAtProgress = (int) (progress * getNumRows());
        int rowCount = 0;
        AlphabeticalAppsList.SectionInfo lastSectionInfo = null;
        for (AlphabeticalAppsList.SectionInfo section : sections) {
            int numRowsInSection = (int) Math.ceil((float) section.numAppsInSection / mNumAppsPerRow);
            if (rowCount + numRowsInSection >= rowAtProgress) {
                lastSectionInfo = section;
                break;
            }
            rowCount += numRowsInSection;
        }
        int position = mApps.getAdapterItems().indexOf(lastSectionInfo.firstAppItem);

        // Scroll the position into view, anchored at the top of the screen if possible. We call the
        // scroll method on the LayoutManager directly since it is not exposed by RecyclerView.
        LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
        stopScroll();
        layoutManager.scrollToPositionWithOffset(position, 0);

        // Return the section name of the row
        return mApps.getAdapterItems().get(position).sectionName;
    }

    /**
     * Returns the bounds for the scrollbar.
     */
    private void updateVerticalScrollbarBounds() {
        // Skip early if there are no items
        if (mApps.getAdapterItems().isEmpty()) {
            mVerticalScrollbarBounds.setEmpty();
            return;
        }

        // Find the index and height of the first visible row (all rows have the same height)
        int x;
        int y;
        boolean isRtl = (getResources().getConfiguration().getLayoutDirection() ==
                LAYOUT_DIRECTION_RTL);
        int rowIndex = -1;
        int rowTopOffset = -1;
        int rowHeight = -1;
        int rowCount = getNumRows();
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            int position = getChildAdapterPosition(child);
            if (position != NO_POSITION) {
                AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(position);
                if (!item.isSectionHeader) {
                    rowIndex = findRowForAppIndex(item.appIndex);
                    rowTopOffset = getLayoutManager().getDecoratedTop(child);
                    rowHeight = child.getHeight();
                    break;
                }
            }
        }

        if (rowIndex != -1) {
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            int totalScrollHeight = rowCount * rowHeight;
            if (totalScrollHeight > height) {
                int scrollbarHeight = Math.max(mScrollbarMinHeight,
                        (int) (height / ((float) totalScrollHeight / height)));

                // Calculate the position and size of the scroll bar
                if (isRtl) {
                    x = getPaddingLeft();
                } else {
                    x = getWidth() - getPaddingRight() - mScrollbarWidth;
                }

                // To calculate the offset, we compute the percentage of the total scrollable height
                // that the user has already scrolled and then map that to the scroll bar bounds
                int availableY = totalScrollHeight - height;
                int availableScrollY = height - scrollbarHeight;
                y = (rowIndex * rowHeight) - rowTopOffset;
                y = getPaddingTop() +
                        (int) (((float) (getPaddingTop() + y) / availableY) * availableScrollY);

                mVerticalScrollbarBounds.set(x, y, x + mScrollbarWidth, y + scrollbarHeight);
                return;
            }
        }
        mVerticalScrollbarBounds.setEmpty();
    }

    /**
     * Returns the row index for a given position in the list.
     */
    private int findRowForAppIndex(int position) {
        List<AlphabeticalAppsList.SectionInfo> sections = mApps.getSections();
        int appIndex = 0;
        int rowCount = 0;
        for (AlphabeticalAppsList.SectionInfo info : sections) {
            int numRowsInSection = (int) Math.ceil((float) info.numAppsInSection / mNumAppsPerRow);
            if (appIndex + info.numAppsInSection > position) {
                return rowCount + ((position - appIndex) / mNumAppsPerRow);
            }
            appIndex += info.numAppsInSection;
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
            int numRowsInSection = (int) Math.ceil((float) info.numAppsInSection / mNumAppsPerRow);
            rowCount += numRowsInSection;
        }
        return rowCount;
    }
}
