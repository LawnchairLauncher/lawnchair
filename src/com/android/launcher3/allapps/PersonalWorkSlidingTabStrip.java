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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;

/**
 * Supports two indicator colors, dedicated for personal and work tabs.
 */
public class PersonalWorkSlidingTabStrip extends LinearLayout {
    private final Paint mPersonalTabIndicatorPaint;
    private final Paint mWorkTabIndicatorPaint;
    private final Paint mDividerPaint;

    private int mSelectedIndicatorHeight;
    private int mIndicatorLeft = -1;
    private int mIndicatorRight = -1;
    private int mSelectedPosition = 0;
    private float mSelectionOffset;
    private boolean mIsRtl;

    public PersonalWorkSlidingTabStrip(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);

        mSelectedIndicatorHeight =
                getResources().getDimensionPixelSize(R.dimen.all_apps_tabs_indicator_height);

        mPersonalTabIndicatorPaint = new Paint();
        mPersonalTabIndicatorPaint.setColor(
                Themes.getAttrColor(context, android.R.attr.colorAccent));

        mWorkTabIndicatorPaint = new Paint();
        mWorkTabIndicatorPaint.setColor(getResources().getColor(R.color.work_profile_color));

        mIsRtl = Utilities.isRtl(getResources());

        mDividerPaint = new Paint();
        mDividerPaint.setColor(Themes.getAttrColor(context, android.R.attr.colorControlHighlight));
        mDividerPaint.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.all_apps_divider_height));
    }

    public void updateIndicatorPosition(int position, float positionOffset) {
        mSelectedPosition = position;
        mSelectionOffset = positionOffset;
        updateIndicatorPosition();
    }

    public void updateTabTextColor(int pos) {
        for (int i = 0; i < getChildCount(); i++) {
            Button tab = (Button) getChildAt(i);
            tab.setSelected(i == pos);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateTabTextColor(mSelectedPosition);
        updateIndicatorPosition(mSelectedPosition, 0);
    }

    private void updateIndicatorPosition() {
        final View tab = getChildAt(mSelectedPosition);
        int left, right;

        if (tab != null && tab.getWidth() > 0) {
            left = tab.getLeft();
            right = tab.getRight();

            if (mSelectionOffset > 0f && mSelectedPosition < getChildCount() - 1) {
                // Draw the selection partway between the tabs
                View nextTitle = getChildAt(mSelectedPosition + 1);
                left = (int) (mSelectionOffset * nextTitle.getLeft() +
                        (1.0f - mSelectionOffset) * left);
                right = (int) (mSelectionOffset * nextTitle.getRight() +
                        (1.0f - mSelectionOffset) * right);
            }
        } else {
            left = right = -1;
        }

        setIndicatorPosition(left, right);
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
        canvas.drawLine(getPaddingLeft(), y, getWidth() - getPaddingRight(), y, mDividerPaint);

        final float middleX = getWidth() / 2.0f;
        if (mIndicatorLeft <= middleX) {
            canvas.drawRect(mIndicatorLeft, getHeight() - mSelectedIndicatorHeight,
                    middleX, getHeight(), getPaint(true /* firstHalf */));
        }
        if (mIndicatorRight > middleX) {
            canvas.drawRect(middleX, getHeight() - mSelectedIndicatorHeight,
                    mIndicatorRight, getHeight(), getPaint(false /* firstHalf */));
        }
    }

    private Paint getPaint(boolean firstHalf) {
        boolean isPersonal = mIsRtl ^ firstHalf;
        return isPersonal ? mPersonalTabIndicatorPaint : mWorkTabIndicatorPaint;
    }
}
