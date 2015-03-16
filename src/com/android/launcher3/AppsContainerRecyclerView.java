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
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
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

    private Drawable mFastScrollerBg;
    private boolean mDraggingFastScroller;
    private String mFastScrollSectionName;
    private Paint mFastScrollTextPaint;
    private Rect mFastScrollTextBounds = new Rect();
    private float mFastScrollAlpha;
    private int mDownX;
    private int mDownY;
    private int mLastX;
    private int mLastY;
    private int mGutterSize;

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
        mFastScrollerBg = res.getDrawable(R.drawable.apps_list_fastscroll_bg);
        mFastScrollerBg.setBounds(0, 0, fastScrollerSize, fastScrollerSize);
        mFastScrollTextPaint = new Paint();
        mFastScrollTextPaint.setColor(Color.WHITE);
        mFastScrollTextPaint.setAntiAlias(true);
        mFastScrollTextPaint.setTextSize(res.getDimensionPixelSize(
                R.dimen.apps_view_fast_scroll_text_size));
        mGutterSize = res.getDimensionPixelSize(R.dimen.apps_view_fast_scroll_gutter_size);
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

    @Override
    protected void onFinishInflate() {
        addOnItemTouchListener(this);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mFastScrollAlpha > 0f) {
            boolean isRtl = (getResources().getConfiguration().getLayoutDirection() ==
                    LAYOUT_DIRECTION_RTL);
            Rect bgBounds = mFastScrollerBg.getBounds();
            int restoreCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
            int x;
            if (isRtl) {
                x = getPaddingLeft() + getScrollBarSize();
            } else {
                x = getWidth() - getPaddingRight() - getScrollBarSize() - bgBounds.width();
            }
            int y = mLastY - (int) (FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * bgBounds.height());
            y = Math.max(getPaddingTop(), Math.min(y, getHeight() - getPaddingBottom() -
                    bgBounds.height()));
            canvas.translate(x, y);
            mFastScrollerBg.setAlpha((int) (mFastScrollAlpha * 255));
            mFastScrollerBg.draw(canvas);
            mFastScrollTextPaint.setAlpha((int) (mFastScrollAlpha * 255));
            mFastScrollTextPaint.getTextBounds(mFastScrollSectionName, 0,
                    mFastScrollSectionName.length(), mFastScrollTextBounds);
            canvas.drawText(mFastScrollSectionName,
                    (bgBounds.width() - mFastScrollTextBounds.width()) / 2,
                    bgBounds.height() -  (bgBounds.height() - mFastScrollTextBounds.height()) / 2,
                    mFastScrollTextPaint);
            canvas.restoreToCount(restoreCount);
        }
    }

    /**
     * We intercept the touch handling only to support fast scrolling when initiated from the
     * gutter.  Otherwise, we fall back to the default RecyclerView touch handling.
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
                boolean isRtl = (getResources().getConfiguration().getLayoutDirection() ==
                        LAYOUT_DIRECTION_RTL);
                boolean isInGutter;
                if (isRtl) {
                    isInGutter = mDownX < mGutterSize;
                } else {
                    isInGutter = mDownX >= (getWidth() - mGutterSize);
                }
                if (!mDraggingFastScroller && isInGutter &&
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
        // Get the total number of rows
        int rowCount = 0;
        for (AlphabeticalAppsList.SectionInfo info : sections) {
            int numRowsInSection = (int) Math.ceil((float) info.numAppsInSection / mNumAppsPerRow);
            rowCount += numRowsInSection;
        }

        // Find the index of the first app in that row and scroll to that position
        int rowAtProgress = (int) (progress * rowCount);
        int appIndex = 0;
        rowCount = 0;
        for (AlphabeticalAppsList.SectionInfo info : sections) {
            int numRowsInSection = (int) Math.ceil((float) info.numAppsInSection / mNumAppsPerRow);
            if (rowCount + numRowsInSection > rowAtProgress) {
                appIndex += (rowAtProgress - rowCount) * mNumAppsPerRow;
                break;
            }
            rowCount += numRowsInSection;
            appIndex += info.numAppsInSection;
        }
        appIndex = Math.max(0, Math.min(mApps.getAppsWithoutSectionBreaks().size() - 1, appIndex));
        AppInfo appInfo = mApps.getAppsWithoutSectionBreaks().get(appIndex);
        int sectionedAppIndex = mApps.getApps().indexOf(appInfo);
        scrollToPosition(sectionedAppIndex);

        // Returns the section name of the row
        return mApps.getSectionNameForApp(appInfo);
    }
}
