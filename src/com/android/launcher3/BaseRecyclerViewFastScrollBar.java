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

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.util.Thunk;

/**
 * The track and scrollbar that shows when you scroll the list.
 */
public class BaseRecyclerViewFastScrollBar {

    public interface FastScrollFocusableView {
        void setFastScrollFocused(boolean focused, boolean animated);
    }

    private final static int MAX_TRACK_ALPHA = 30;
    private final static int SCROLL_BAR_VIS_DURATION = 150;

    @Thunk BaseRecyclerView mRv;
    private BaseRecyclerViewFastScrollPopup mPopup;

    private AnimatorSet mScrollbarAnimator;

    private int mThumbInactiveColor;
    private int mThumbActiveColor;
    @Thunk Point mThumbOffset = new Point(-1, -1);
    @Thunk Paint mThumbPaint;
    private Paint mTrackPaint;
    private int mThumbMinWidth;
    private int mThumbMaxWidth;
    @Thunk int mThumbWidth;
    @Thunk int mThumbHeight;
    // The inset is the buffer around which a point will still register as a click on the scrollbar
    private int mTouchInset;
    private boolean mIsDragging;

    // This is the offset from the top of the scrollbar when the user first starts touching.  To
    // prevent jumping, this offset is applied as the user scrolls.
    private int mTouchOffset;

    private Rect mInvalidateRect = new Rect();
    private Rect mTmpRect = new Rect();

    public BaseRecyclerViewFastScrollBar(BaseRecyclerView rv, Resources res) {
        mRv = rv;
        mPopup = new BaseRecyclerViewFastScrollPopup(rv, res);
        mTrackPaint = new Paint();
        mTrackPaint.setColor(rv.getFastScrollerTrackColor(Color.BLACK));
        mTrackPaint.setAlpha(0);
        mThumbInactiveColor = rv.getFastScrollerThumbInactiveColor(
                res.getColor(R.color.container_fastscroll_thumb_inactive_color));
        mThumbActiveColor = res.getColor(R.color.container_fastscroll_thumb_active_color);
        mThumbPaint = new Paint();
        mThumbPaint.setColor(mThumbInactiveColor);
        mThumbWidth = mThumbMinWidth = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_min_width);
        mThumbMaxWidth = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_max_width);
        mThumbHeight = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_height);
        mTouchInset = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_touch_inset);
    }

    public void setScrollbarThumbOffset(int x, int y) {
        if (mThumbOffset.x == x && mThumbOffset.y == y) {
            return;
        }
        mInvalidateRect.set(mThumbOffset.x, 0, mThumbOffset.x + mThumbWidth, mRv.getHeight());
        mThumbOffset.set(x, y);
        mInvalidateRect.union(new Rect(mThumbOffset.x, 0, mThumbOffset.x + mThumbWidth,
                mRv.getHeight()));
        mRv.invalidate(mInvalidateRect);
    }

    // Setter/getter for the search bar width for animations
    public void setWidth(int width) {
        mInvalidateRect.set(mThumbOffset.x, 0, mThumbOffset.x + mThumbWidth, mRv.getHeight());
        mThumbWidth = width;
        mInvalidateRect.union(new Rect(mThumbOffset.x, 0, mThumbOffset.x + mThumbWidth,
                mRv.getHeight()));
        mRv.invalidate(mInvalidateRect);
    }

    public int getWidth() {
        return mThumbWidth;
    }

    // Setter/getter for the track background alpha for animations
    public void setTrackAlpha(int alpha) {
        mTrackPaint.setAlpha(alpha);
        mInvalidateRect.set(mThumbOffset.x, 0, mThumbOffset.x + mThumbWidth, mRv.getHeight());
        mRv.invalidate(mInvalidateRect);
    }

    public int getTrackAlpha() {
        return mTrackPaint.getAlpha();
    }

    public int getThumbHeight() {
        return mThumbHeight;
    }

    public int getThumbMaxWidth() {
        return mThumbMaxWidth;
    }

    public boolean isDragging() {
        return mIsDragging;
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
                if (isNearPoint(downX, downY)) {
                    mTouchOffset = downY - mThumbOffset.y;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                // Check if we should start scrolling
                if (!mIsDragging && isNearPoint(downX, downY) &&
                        Math.abs(y - downY) > config.getScaledTouchSlop()) {
                    mRv.getParent().requestDisallowInterceptTouchEvent(true);
                    mIsDragging = true;
                    mTouchOffset += (lastY - downY);
                    mPopup.animateVisibility(true);
                    animateScrollbar(true);
                }
                if (mIsDragging) {
                    // Update the fastscroller section name at this touch position
                    int top = mRv.getBackgroundPadding().top;
                    int bottom = mRv.getHeight() - mRv.getBackgroundPadding().bottom - mThumbHeight;
                    float boundedY = (float) Math.max(top, Math.min(bottom, y - mTouchOffset));
                    String sectionName = mRv.scrollToPositionAtProgress((boundedY - top) /
                            (bottom - top));
                    mPopup.setSectionName(sectionName);
                    mPopup.animateVisibility(!sectionName.isEmpty());
                    mRv.invalidate(mPopup.updateFastScrollerBounds(mRv, lastY));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchOffset = 0;
                if (mIsDragging) {
                    mIsDragging = false;
                    mPopup.animateVisibility(false);
                    animateScrollbar(false);
                }
                break;
        }
    }

    public void draw(Canvas canvas) {
        if (mThumbOffset.x < 0 || mThumbOffset.y < 0) {
            return;
        }

        // Draw the scroll bar track and thumb
        if (mTrackPaint.getAlpha() > 0) {
            canvas.drawRect(mThumbOffset.x, 0, mThumbOffset.x + mThumbWidth, mRv.getHeight(), mTrackPaint);
        }
        canvas.drawRect(mThumbOffset.x, mThumbOffset.y, mThumbOffset.x + mThumbWidth,
                mThumbOffset.y + mThumbHeight, mThumbPaint);

        // Draw the popup
        mPopup.draw(canvas);
    }

    /**
     * Animates the width and color of the scrollbar.
     */
    private void animateScrollbar(boolean isScrolling) {
        if (mScrollbarAnimator != null) {
            mScrollbarAnimator.cancel();
        }
        ObjectAnimator trackAlphaAnim = ObjectAnimator.ofInt(this, "trackAlpha",
                isScrolling ? MAX_TRACK_ALPHA : 0);
        ObjectAnimator thumbWidthAnim = ObjectAnimator.ofInt(this, "width",
                isScrolling ? mThumbMaxWidth : mThumbMinWidth);
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(),
                mThumbPaint.getColor(), isScrolling ? mThumbActiveColor : mThumbInactiveColor);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                mThumbPaint.setColor((Integer) animator.getAnimatedValue());
                mRv.invalidate(mThumbOffset.x, mThumbOffset.y, mThumbOffset.x + mThumbWidth,
                        mThumbOffset.y + mThumbHeight);
            }
        });
        mScrollbarAnimator = new AnimatorSet();
        mScrollbarAnimator.playTogether(trackAlphaAnim, thumbWidthAnim, colorAnimation);
        mScrollbarAnimator.setDuration(SCROLL_BAR_VIS_DURATION);
        mScrollbarAnimator.start();
    }

    /**
     * Returns whether the specified points are near the scroll bar bounds.
     */
    private boolean isNearPoint(int x, int y) {
        mTmpRect.set(mThumbOffset.x, mThumbOffset.y, mThumbOffset.x + mThumbWidth,
                mThumbOffset.y + mThumbHeight);
        mTmpRect.inset(mTouchInset, mTouchInset);
        return mTmpRect.contains(x, y);
    }
}
