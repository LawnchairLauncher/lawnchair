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
package com.android.launcher3.workprofile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.util.Themes;

/**
 * Supports two indicator colors, dedicated for personal and work tabs.
 */
public class PersonalWorkSlidingTabStrip extends LinearLayout implements PageIndicator {
    private final Paint mSelectedIndicatorPaint;
    private final Paint mDividerPaint;

    private int mSelectedIndicatorHeight;
    private final int mSelectedIndicatorRadius;

    private int mIndicatorLeft = -1;
    private int mIndicatorRight = -1;
    private float mScrollOffset;
    private int mSelectedPosition = 0;

    private OnActivePageChangedListener mOnActivePageChangedListener;
    private int mLastActivePage = 0;
    private boolean mIsRtl;

    public PersonalWorkSlidingTabStrip(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);

        mSelectedIndicatorHeight =
                getResources().getDimensionPixelSize(R.dimen.all_apps_header_pill_height);

        mSelectedIndicatorRadius = getResources().getDimensionPixelSize(
                R.dimen.all_apps_header_pill_corner_radius);

        mSelectedIndicatorPaint = new Paint();
        mSelectedIndicatorPaint.setColor(
                Themes.getAttrColor(context, android.R.attr.colorAccent));

        mDividerPaint = new Paint();
        mDividerPaint.setColor(Themes.getAttrColor(context, android.R.attr.colorControlHighlight));
        mDividerPaint.setStrokeWidth(
                getResources().getDimensionPixelSize(R.dimen.all_apps_divider_height));

        mIsRtl = Utilities.isRtl(getResources());
    }

    /**
     * Highlights tab with index pos
     */
    public void updateTabTextColor(int pos) {
        mSelectedPosition = pos;
        for (int i = 0; i < getChildCount(); i++) {
            Button tab = (Button) getChildAt(i);
            tab.setSelected(i == pos);
        }
    }

    private void updateIndicatorPosition(float scrollOffset) {
        mScrollOffset = scrollOffset;
        updateIndicatorPosition();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateTabTextColor(mSelectedPosition);
        updateIndicatorPosition(mScrollOffset);
    }

    private void updateIndicatorPosition() {
        int left = -1, right = -1;
        final View leftTab = getLeftTab();
        if (leftTab != null) {
            left = (int) (leftTab.getLeft() + leftTab.getWidth() * mScrollOffset);
            right = left + leftTab.getWidth();
        }
        setIndicatorPosition(left, right);
    }

    private View getLeftTab() {
        return mIsRtl ? getChildAt(1) : getChildAt(0);
    }

    private void setIndicatorPosition(int left, int right) {
        if (left != mIndicatorLeft || right != mIndicatorRight) {
            mIndicatorLeft = left;
            mIndicatorRight = right;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float y = getHeight() - mDividerPaint.getStrokeWidth();
        canvas.drawRoundRect(mIndicatorLeft, getHeight() - mSelectedIndicatorHeight,
                mIndicatorRight, getHeight(), mSelectedIndicatorRadius, mSelectedIndicatorRadius,
                mSelectedIndicatorPaint);
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        float scrollOffset = ((float) currentScroll) / totalScroll;
        updateIndicatorPosition(scrollOffset);
    }

    @Override
    public void setActiveMarker(int activePage) {
        updateTabTextColor(activePage);
        if (mOnActivePageChangedListener != null && mLastActivePage != activePage) {
            updateIndicatorPosition(activePage);
            mOnActivePageChangedListener.onActivePageChanged(activePage);
        }
        mLastActivePage = activePage;
    }

    public void setOnActivePageChangedListener(OnActivePageChangedListener listener) {
        mOnActivePageChangedListener = listener;
    }

    @Override
    public void setMarkersCount(int numMarkers) {
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * Interface definition for a callback to be invoked when an active page has been changed.
     */
    public interface OnActivePageChangedListener {
        /** Called when the active page has been changed. */
        void onActivePageChanged(int currentActivePage);
    }
}
