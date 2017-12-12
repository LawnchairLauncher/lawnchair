/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.views;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.FastScrollThumbDrawable;
import com.android.launcher3.util.Themes;

/**
 * The track and scrollbar that shows when you scroll the list.
 */
public class RecyclerViewFastScroller extends View {

    private static final int SCROLL_DELTA_THRESHOLD_DP = 4;

    private static final Property<RecyclerViewFastScroller, Integer> TRACK_WIDTH =
            new Property<RecyclerViewFastScroller, Integer>(Integer.class, "width") {

                @Override
                public Integer get(RecyclerViewFastScroller scrollBar) {
                    return scrollBar.mWidth;
                }

                @Override
                public void set(RecyclerViewFastScroller scrollBar, Integer value) {
                    scrollBar.setTrackWidth(value);
                }
            };

    private final static int MAX_TRACK_ALPHA = 30;
    private final static int SCROLL_BAR_VIS_DURATION = 150;
    private static final float FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR = 0.75f;

    private final int mMinWidth;
    private final int mMaxWidth;
    private final int mThumbPadding;

    /** Keeps the last known scrolling delta/velocity along y-axis. */
    private int mDy = 0;
    private final float mDeltaThreshold;

    private final ViewConfiguration mConfig;

    // Current width of the track
    private int mWidth;
    private ObjectAnimator mWidthAnimator;

    private final Paint mThumbPaint;
    protected final int mThumbHeight;

    private final Paint mTrackPaint;

    private float mLastTouchY;
    private boolean mIsDragging;
    private boolean mIsThumbDetached;
    private final boolean mCanThumbDetach;
    private boolean mIgnoreDragGesture;

    // This is the offset from the top of the scrollbar when the user first starts touching.  To
    // prevent jumping, this offset is applied as the user scrolls.
    protected int mTouchOffsetY;
    protected int mThumbOffsetY;

    // Fast scroller popup
    private TextView mPopupView;
    private boolean mPopupVisible;
    private String mPopupSectionName;

    protected BaseRecyclerView mRv;

    private int mDownX;
    private int mDownY;
    private int mLastY;

    public RecyclerViewFastScroller(Context context) {
        this(context, null);
    }

    public RecyclerViewFastScroller(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecyclerViewFastScroller(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mTrackPaint = new Paint();
        mTrackPaint.setColor(Themes.getAttrColor(context, android.R.attr.textColorPrimary));
        mTrackPaint.setAlpha(MAX_TRACK_ALPHA);

        mThumbPaint = new Paint();
        mThumbPaint.setAntiAlias(true);
        mThumbPaint.setColor(Themes.getColorAccent(context));
        mThumbPaint.setStyle(Paint.Style.FILL);

        Resources res = getResources();
        mWidth = mMinWidth = res.getDimensionPixelSize(R.dimen.fastscroll_track_min_width);
        mMaxWidth = res.getDimensionPixelSize(R.dimen.fastscroll_track_max_width);

        mThumbPadding = res.getDimensionPixelSize(R.dimen.fastscroll_thumb_padding);
        mThumbHeight = res.getDimensionPixelSize(R.dimen.fastscroll_thumb_height);

        mConfig = ViewConfiguration.get(context);
        mDeltaThreshold = res.getDisplayMetrics().density * SCROLL_DELTA_THRESHOLD_DP;

        TypedArray ta =
                context.obtainStyledAttributes(attrs, R.styleable.RecyclerViewFastScroller, defStyleAttr, 0);
        mCanThumbDetach = ta.getBoolean(R.styleable.RecyclerViewFastScroller_canThumbDetach, false);
        ta.recycle();
    }

    public void setRecyclerView(BaseRecyclerView rv, TextView popupView) {
        mRv = rv;
        mRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                mDy = dy;

                // TODO(winsonc): If we want to animate the section heads while scrolling, we can
                //                initiate that here if the recycler view scroll state is not
                //                RecyclerView.SCROLL_STATE_IDLE.

                mRv.onUpdateScrollbar(dy);
            }
        });

        mPopupView = popupView;
        mPopupView.setBackground(
                new FastScrollThumbDrawable(mThumbPaint, Utilities.isRtl(getResources())));
    }

    public void reattachThumbToScroll() {
        mIsThumbDetached = false;
    }

    public void setThumbOffsetY(int y) {
        if (mThumbOffsetY == y) {
            return;
        }
        mThumbOffsetY = y;
        invalidate();
    }

    public int getThumbOffsetY() {
        return mThumbOffsetY;
    }

    private void setTrackWidth(int width) {
        if (mWidth == width) {
            return;
        }
        mWidth = width;
        invalidate();
    }

    public int getThumbHeight() {
        return mThumbHeight;
    }

    public boolean isDraggingThumb() {
        return mIsDragging;
    }

    public boolean isThumbDetached() {
        return mIsThumbDetached;
    }

    /**
     * Handles the touch event and determines whether to show the fast scroller (or updates it if
     * it is already showing).
     */
    public boolean handleTouchEvent(MotionEvent ev) {
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Keep track of the down positions
                mDownX = x;
                mDownY = mLastY = y;

                if ((Math.abs(mDy) < mDeltaThreshold &&
                        mRv.getScrollState() != RecyclerView.SCROLL_STATE_IDLE)) {
                    // now the touch events are being passed to the {@link WidgetCell} until the
                    // touch sequence goes over the touch slop.
                    mRv.stopScroll();
                }
                if (isNearThumb(x, y)) {
                    mTouchOffsetY = mDownY - mThumbOffsetY;
                } else if (FeatureFlags.LAUNCHER3_DIRECT_SCROLL
                        && mRv.supportsFastScrolling()
                        && isNearScrollBar(mDownX)) {
                    calcTouchOffsetAndPrepToFastScroll(mDownY, mLastY);
                    updateFastScrollSectionNameAndThumbOffset(mLastY, y);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                mLastY = y;

                // Check if we should start scrolling, but ignore this fastscroll gesture if we have
                // exceeded some fixed movement
                mIgnoreDragGesture |= Math.abs(y - mDownY) > mConfig.getScaledPagingTouchSlop();
                if (!mIsDragging && !mIgnoreDragGesture && mRv.supportsFastScrolling() &&
                        isNearThumb(mDownX, mLastY) &&
                        Math.abs(y - mDownY) > mConfig.getScaledTouchSlop()) {
                    calcTouchOffsetAndPrepToFastScroll(mDownY, mLastY);
                }
                if (mIsDragging) {
                    updateFastScrollSectionNameAndThumbOffset(mLastY, y);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mRv.onFastScrollCompleted();
                mTouchOffsetY = 0;
                mLastTouchY = 0;
                mIgnoreDragGesture = false;
                if (mIsDragging) {
                    mIsDragging = false;
                    animatePopupVisibility(false);
                    showActiveScrollbar(false);
                }
                break;
        }
        return mIsDragging;
    }

    private void calcTouchOffsetAndPrepToFastScroll(int downY, int lastY) {
        mRv.getParent().requestDisallowInterceptTouchEvent(true);
        mIsDragging = true;
        if (mCanThumbDetach) {
            mIsThumbDetached = true;
        }
        mTouchOffsetY += (lastY - downY);
        animatePopupVisibility(true);
        showActiveScrollbar(true);
    }

    private void updateFastScrollSectionNameAndThumbOffset(int lastY, int y) {
        // Update the fastscroller section name at this touch position
        int bottom = mRv.getScrollbarTrackHeight() - mThumbHeight;
        float boundedY = (float) Math.max(0, Math.min(bottom, y - mTouchOffsetY));
        String sectionName = mRv.scrollToPositionAtProgress(boundedY / bottom);
        if (!sectionName.equals(mPopupSectionName)) {
            mPopupSectionName = sectionName;
            mPopupView.setText(sectionName);
        }
        animatePopupVisibility(!sectionName.isEmpty());
        updatePopupY(lastY);
        mLastTouchY = boundedY;
        setThumbOffsetY((int) mLastTouchY);
    }

    public void onDraw(Canvas canvas) {
        if (mThumbOffsetY < 0) {
            return;
        }
        int saveCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.translate(getWidth() / 2, mRv.getPaddingTop());
        // Draw the track
        float halfW = mWidth / 2;
        canvas.drawRoundRect(-halfW, 0, halfW, mRv.getScrollbarTrackHeight(),
                mWidth, mWidth, mTrackPaint);

        canvas.translate(0, mThumbOffsetY);
        halfW += mThumbPadding;
        float r = mWidth + mThumbPadding + mThumbPadding;
        canvas.drawRoundRect(-halfW, 0, halfW, mThumbHeight, r, r, mThumbPaint);
        canvas.restoreToCount(saveCount);
    }


    /**
     * Animates the width of the scrollbar.
     */
    private void showActiveScrollbar(boolean isScrolling) {
        if (mWidthAnimator != null) {
            mWidthAnimator.cancel();
        }

        mWidthAnimator = ObjectAnimator.ofInt(this, TRACK_WIDTH,
                isScrolling ? mMaxWidth : mMinWidth);
        mWidthAnimator.setDuration(SCROLL_BAR_VIS_DURATION);
        mWidthAnimator.start();
    }

    /**
     * Returns whether the specified point is inside the thumb bounds.
     */
    private boolean isNearThumb(int x, int y) {
        int offset = y - mRv.getPaddingTop() - mThumbOffsetY;

        return x >= 0 && x < getWidth() && offset >= 0 && offset <= mThumbHeight;
    }

    /**
     * Returns true if AllAppsTransitionController can handle vertical motion
     * beginning at this point.
     */
    public boolean shouldBlockIntercept(int x, int y) {
        return isNearThumb(x, y);
    }

    /**
     * Returns whether the specified x position is near the scroll bar.
     */
    public boolean isNearScrollBar(int x) {
        return x >= (getWidth() - mMaxWidth) / 2 && x <= (getWidth() + mMaxWidth) / 2;
    }

    private void animatePopupVisibility(boolean visible) {
        if (mPopupVisible != visible) {
            mPopupVisible = visible;
            mPopupView.animate().cancel();
            mPopupView.animate().alpha(visible ? 1f : 0f).setDuration(visible ? 200 : 150).start();
        }
    }

    private void updatePopupY(int lastTouchY) {
        int height = mPopupView.getHeight();
        float top = lastTouchY - (FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * height)
                + mRv.getPaddingTop();
        top = Utilities.boundToRange(top,
                mMaxWidth, mRv.getScrollbarTrackHeight() - mMaxWidth - height);
        mPopupView.setTranslationY(top);
    }
}
