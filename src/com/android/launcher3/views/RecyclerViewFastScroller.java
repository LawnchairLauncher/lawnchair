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
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.FastScrollThumbDrawable;
import com.android.launcher3.util.Themes;

import java.util.Collections;
import java.util.List;

/**
 * The track and scrollbar that shows when you scroll the list.
 */
public class RecyclerViewFastScroller extends View {

    private static final int SCROLL_DELTA_THRESHOLD_DP = 4;
    private static final Rect sTempRect = new Rect();

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

    private static final List<Rect> SYSTEM_GESTURE_EXCLUSION_RECT =
            Collections.singletonList(new Rect());

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
    private final RectF mThumbBounds = new RectF();
    private final Point mThumbDrawOffset = new Point();

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
    private RecyclerView.OnScrollListener mOnScrollListener;

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
        if (mRv != null && mOnScrollListener != null) {
            mRv.removeOnScrollListener(mOnScrollListener);
        }
        mRv = rv;

        mRv.addOnScrollListener(mOnScrollListener = new RecyclerView.OnScrollListener() {
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
        updatePopupY((int) y);
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
    public boolean handleTouchEvent(MotionEvent ev, Point offset) {
        int x = (int) ev.getX() - offset.x;
        int y = (int) ev.getY() - offset.y;
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
                } else if (mRv.supportsFastScrolling()
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
        mLastTouchY = boundedY;
        setThumbOffsetY((int) mLastTouchY);
    }

    public void onDraw(Canvas canvas) {
        if (mThumbOffsetY < 0) {
            return;
        }
        int saveCount = canvas.save();
        canvas.translate(getWidth() / 2, mRv.getScrollBarTop());
        mThumbDrawOffset.set(getWidth() / 2, mRv.getScrollBarTop());
        // Draw the track
        float halfW = mWidth / 2;
        canvas.drawRoundRect(-halfW, 0, halfW, mRv.getScrollbarTrackHeight(),
                mWidth, mWidth, mTrackPaint);

        canvas.translate(0, mThumbOffsetY);
        mThumbDrawOffset.y += mThumbOffsetY;
        halfW += mThumbPadding;
        float r = getScrollThumbRadius();
        mThumbBounds.set(-halfW, 0, halfW, mThumbHeight);
        canvas.drawRoundRect(mThumbBounds, r, r, mThumbPaint);
        if (Utilities.ATLEAST_Q) {
            mThumbBounds.roundOut(SYSTEM_GESTURE_EXCLUSION_RECT.get(0));
            SYSTEM_GESTURE_EXCLUSION_RECT.get(0).offset(mThumbDrawOffset.x, mThumbDrawOffset.y);
            setSystemGestureExclusionRects(SYSTEM_GESTURE_EXCLUSION_RECT);
        }
        canvas.restoreToCount(saveCount);
    }

    private float getScrollThumbRadius() {
        return mWidth + mThumbPadding + mThumbPadding;
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
        int offset = y - mThumbOffsetY;

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
        if (!mPopupVisible) {
            return;
        }
        int height = mPopupView.getHeight();
        // Aligns the rounded corner of the pop up with the top of the thumb.
        float top = mRv.getScrollBarTop() + lastTouchY + (getScrollThumbRadius() / 2f)
                - (height / 2f);
        top = Utilities.boundToRange(top, 0,
                getTop() + mRv.getScrollBarTop() + mRv.getScrollbarTrackHeight() - height);
        mPopupView.setTranslationY(top);
    }

    public boolean isHitInParent(float x, float y, Point outOffset) {
        if (mThumbOffsetY < 0) {
            return false;
        }
        getHitRect(sTempRect);
        sTempRect.top += mRv.getScrollBarTop();
        if (outOffset != null) {
            outOffset.set(sTempRect.left, sTempRect.top);
        }
        return sTempRect.contains((int) x, (int) y);
    }

    @Override
    public boolean hasOverlappingRendering() {
        // There is actually some overlap between the track and the thumb. But since the track
        // alpha is so low, it does not matter.
        return false;
    }
}
