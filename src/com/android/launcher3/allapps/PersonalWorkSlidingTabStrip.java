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

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.util.Themes;

/**
 * Supports two indicator colors, dedicated for personal and work tabs.
 */
public class PersonalWorkSlidingTabStrip extends LinearLayout {
    private static final int POSITION_PERSONAL = 0;
    private static final int POSITION_WORK = 1;
    private static final int PEEK_DURATION = 1000;
    private static final float PEAK_OFFSET = 0.4f;

    private static final String KEY_SHOWED_PEEK_WORK_TAB = "showed_peek_work_tab";

    private final Paint mSelectedIndicatorPaint;
    private final Paint mDividerPaint;
    private final SharedPreferences mSharedPreferences;

    private int mSelectedIndicatorHeight;
    private int mIndicatorLeft = -1;
    private int mIndicatorRight = -1;
    private int mIndicatorPosition = 0;
    private float mIndicatorOffset;
    private int mSelectedPosition = 0;

    public PersonalWorkSlidingTabStrip(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);

        mSelectedIndicatorHeight =
                getResources().getDimensionPixelSize(R.dimen.all_apps_tabs_indicator_height);

        mSelectedIndicatorPaint = new Paint();
        mSelectedIndicatorPaint.setColor(
                Themes.getAttrColor(context, android.R.attr.colorAccent));

        mDividerPaint = new Paint();
        mDividerPaint.setColor(Themes.getAttrColor(context, android.R.attr.colorControlHighlight));
        mDividerPaint.setStrokeWidth(
                getResources().getDimensionPixelSize(R.dimen.all_apps_divider_height));

        mSharedPreferences = Launcher.getLauncher(getContext()).getSharedPrefs();
    }

    public void updateIndicatorPosition(int position, float positionOffset) {
        mIndicatorPosition = position;
        mIndicatorOffset = positionOffset;
        updateIndicatorPosition();
    }

    public void updateTabTextColor(int pos) {
        mSelectedPosition = pos;
        for (int i = 0; i < getChildCount(); i++) {
            Button tab = (Button) getChildAt(i);
            tab.setSelected(i == pos);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateTabTextColor(mSelectedPosition);
        updateIndicatorPosition(mIndicatorPosition, mIndicatorOffset);
    }

    private void updateIndicatorPosition() {
        final View tab = getChildAt(mIndicatorPosition);
        int left, right;

        if (tab != null && tab.getWidth() > 0) {
            left = tab.getLeft();
            right = tab.getRight();

            if (mIndicatorOffset > 0f && mIndicatorPosition < getChildCount() - 1) {
                // Draw the selection partway between the tabs
                View nextTitle = getChildAt(mIndicatorPosition + 1);
                left = (int) (mIndicatorOffset * nextTitle.getLeft() +
                        (1.0f - mIndicatorOffset) * left);
                right = (int) (mIndicatorOffset * nextTitle.getRight() +
                        (1.0f - mIndicatorOffset) * right);
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
        canvas.drawRect(mIndicatorLeft, getHeight() - mSelectedIndicatorHeight,
            mIndicatorRight, getHeight(), mSelectedIndicatorPaint);
    }

    public void peekWorkTabIfNecessary() {
        if (mSharedPreferences.getBoolean(KEY_SHOWED_PEEK_WORK_TAB, false)) {
            return;
        }
        if (mIndicatorPosition != POSITION_PERSONAL) {
            return;
        }
        peekWorkTab();
        mSharedPreferences.edit().putBoolean(KEY_SHOWED_PEEK_WORK_TAB, true).apply();
    }

    private void peekWorkTab() {
        final boolean isRtl = Utilities.isRtl(getResources());
        ValueAnimator animator = ValueAnimator.ofFloat(0, isRtl ? 1 - PEAK_OFFSET : PEAK_OFFSET, 0);
        animator.setDuration(PEEK_DURATION);
        animator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        animator.addUpdateListener(
                animation -> updateIndicatorPosition(mIndicatorPosition,
                        (float) animation.getAnimatedValue()));
        animator.start();
    }
}
