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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.Themes;

/**
 * The track and scrollbar that shows when you scroll the list.
 */
public class BaseRecyclerViewFastScrollBar {

    private static final Property<BaseRecyclerViewFastScrollBar, Integer> TRACK_WIDTH =
            new Property<BaseRecyclerViewFastScrollBar, Integer>(Integer.class, "width") {

                @Override
                public Integer get(BaseRecyclerViewFastScrollBar scrollBar) {
                    return scrollBar.mWidth;
                }

                @Override
                public void set(BaseRecyclerViewFastScrollBar scrollBar, Integer value) {
                    scrollBar.setTrackWidth(value);
                }
            };

    private final static int MAX_TRACK_ALPHA = 30;
    private final static int SCROLL_BAR_VIS_DURATION = 150;
    private static final float FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR = 1.5f;

    private final Rect mTmpRect = new Rect();
    private final BaseRecyclerView mRv;

    private final boolean mIsRtl;

    // The inset is the buffer around which a point will still register as a click on the scrollbar
    private final int mTouchInset;

    private final int mMinWidth;
    private final int mMaxWidth;

    // Current width of the track
    private int mWidth;
    private ObjectAnimator mWidthAnimator;

    private final Path mThumbPath = new Path();
    private final Paint mThumbPaint;
    private final int mThumbHeight;

    private final Paint mTrackPaint;

    private float mLastTouchY;
    private boolean mIsDragging;
    private boolean mIsThumbDetached;
    private boolean mCanThumbDetach;
    private boolean mIgnoreDragGesture;

    // This is the offset from the top of the scrollbar when the user first starts touching.  To
    // prevent jumping, this offset is applied as the user scrolls.
    private int mTouchOffsetY;
    private int mThumbOffsetY;

    // Fast scroller popup
    private TextView mPopupView;
    private boolean mPopupVisible;
    private String mPopupSectionName;

    public BaseRecyclerViewFastScrollBar(BaseRecyclerView rv, Resources res) {
        mRv = rv;
        mTrackPaint = new Paint();
        mTrackPaint.setColor(rv.getFastScrollerTrackColor(Color.BLACK));
        mTrackPaint.setAlpha(MAX_TRACK_ALPHA);

        mThumbPaint = new Paint();
        mThumbPaint.setAntiAlias(true);
        mThumbPaint.setColor(Themes.getColorAccent(rv.getContext()));
        mThumbPaint.setStyle(Paint.Style.FILL);

        mWidth = mMinWidth = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_min_width);
        mMaxWidth = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_max_width);
        mThumbHeight = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_height);
        mTouchInset = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_touch_inset);
        mIsRtl = Utilities.isRtl(res);
        updateThumbPath();
    }

    public void setPopupView(View popup) {
        mPopupView = (TextView) popup;
    }

    public void setDetachThumbOnFastScroll() {
        mCanThumbDetach = true;
    }

    public void reattachThumbToScroll() {
        mIsThumbDetached = false;
    }

    private int getDrawLeft() {
        return mIsRtl ? 0 : (mRv.getWidth() - mMaxWidth);
    }

    public void setThumbOffsetY(int y) {
        if (mThumbOffsetY == y) {
            return;
        }

        // Invalidate the previous and new thumb area
        int drawLeft = getDrawLeft();
        mTmpRect.set(drawLeft, mThumbOffsetY, drawLeft + mMaxWidth, mThumbOffsetY + mThumbHeight);
        mThumbOffsetY = y;
        mTmpRect.union(drawLeft, mThumbOffsetY, drawLeft + mMaxWidth, mThumbOffsetY + mThumbHeight);
        mRv.invalidate(mTmpRect);
    }

    public int getThumbOffsetY() {
        return mThumbOffsetY;
    }

    private void setTrackWidth(int width) {
        if (mWidth == width) {
            return;
        }
        int left = getDrawLeft();
        // Invalidate the whole scroll bar area.
        mRv.invalidate(left, 0, left + mMaxWidth, mRv.getScrollbarTrackHeight());

        mWidth = width;
        updateThumbPath();
    }

    /**
     * Updates the path for the thumb drawable.
     */
    private void updateThumbPath() {
        int smallWidth = mIsRtl ? mWidth : -mWidth;
        int largeWidth = mIsRtl ? mMaxWidth : -mMaxWidth;

        mThumbPath.reset();
        mThumbPath.moveTo(0, 0);
        mThumbPath.lineTo(0, mThumbHeight);             // Left edge
        mThumbPath.lineTo(smallWidth, mThumbHeight);    // bottom edge
        mThumbPath.cubicTo(smallWidth, mThumbHeight,    // right edge
                largeWidth, mThumbHeight / 2,
                smallWidth, 0);
        mThumbPath.close();
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
    public void handleTouchEvent(MotionEvent ev, int downX, int downY, int lastY) {
        ViewConfiguration config = ViewConfiguration.get(mRv.getContext());

        int action = ev.getAction();
        int y = (int) ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (isNearThumb(downX, downY)) {
                    mTouchOffsetY = downY - mThumbOffsetY;
                } else if (FeatureFlags.LAUNCHER3_DIRECT_SCROLL
                        && mRv.supportsFastScrolling()
                        && isNearScrollBar(downX)) {
                    calcTouchOffsetAndPrepToFastScroll(downY, lastY);
                    updateFastScrollSectionNameAndThumbOffset(lastY, y);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                // Check if we should start scrolling, but ignore this fastscroll gesture if we have
                // exceeded some fixed movement
                mIgnoreDragGesture |= Math.abs(y - downY) > config.getScaledPagingTouchSlop();
                if (!mIsDragging && !mIgnoreDragGesture && mRv.supportsFastScrolling() &&
                        isNearThumb(downX, lastY) &&
                        Math.abs(y - downY) > config.getScaledTouchSlop()) {
                    calcTouchOffsetAndPrepToFastScroll(downY, lastY);
                }
                if (mIsDragging) {
                    updateFastScrollSectionNameAndThumbOffset(lastY, y);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
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

    public void draw(Canvas canvas) {
        if (mThumbOffsetY < 0) {
            return;
        }
        int saveCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
        if (!mIsRtl) {
            canvas.translate(mRv.getWidth(), 0);
        }
        // Draw the track
        int thumbWidth = mIsRtl ? mWidth : -mWidth;
        canvas.drawRect(0, 0, thumbWidth, mRv.getScrollbarTrackHeight(), mTrackPaint);

        canvas.translate(0, mThumbOffsetY);
        canvas.drawPath(mThumbPath, mThumbPaint);
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
    public boolean isNearThumb(int x, int y) {
        int left = getDrawLeft();
        mTmpRect.set(left, mThumbOffsetY, left + mMaxWidth, mThumbOffsetY + mThumbHeight);
        mTmpRect.inset(mTouchInset, mTouchInset);
        return mTmpRect.contains(x, y);
    }

    /**
     * Returns whether the specified x position is near the scroll bar.
     */
    public boolean isNearScrollBar(int x) {
        int left = getDrawLeft();
        return x >= left && x <= left + mMaxWidth;
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
        float top = lastTouchY - (FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * height);
        top = Math.max(mMaxWidth, Math.min(top, mRv.getScrollbarTrackHeight() - mMaxWidth - height));
        mPopupView.setTranslationY(top);
    }
}
