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

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import ch.deletescape.lawnchair.allapps.AllAppsTabs;
import ch.deletescape.lawnchair.allapps.AllAppsTabs.Tab;
import ch.deletescape.lawnchair.colors.ColorEngine;
import ch.deletescape.lawnchair.colors.ColorEngine.OnColorChangeListener;
import ch.deletescape.lawnchair.preferences.DrawerTabEditBottomSheet;
import ch.deletescape.lawnchair.settings.DrawerTabs;
import ch.deletescape.lawnchair.settings.DrawerTabs.CustomTab;
import ch.deletescape.lawnchair.views.ColoredButton;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.util.Themes;

/**
 * Supports two indicator colors, dedicated for personal and work tabs.
 */
public class PersonalWorkSlidingTabStrip extends LinearLayout implements PageIndicator,
        OnColorChangeListener {

    private static final int POSITION_PERSONAL = 0;
    private static final int POSITION_WORK = 1;

    private static final String KEY_SHOWED_PEEK_WORK_TAB = "showed_peek_work_tab";

    private final Paint mSelectedIndicatorPaint;
    private final Paint mDividerPaint;
    private final SharedPreferences mSharedPreferences;

    private int mSelectedIndicatorHeight;
    private int mIndicatorLeft = -1;
    private int mIndicatorRight = -1;
    private float mScrollOffset;
    private int mSelectedPosition = 0;

    private AllAppsContainerView mContainerView;
    private int mLastActivePage = 0;
    private boolean mIsRtl;

    private int mTextColorTertiary;

    private ArgbEvaluator mArgbEvaluator = new ArgbEvaluator();

    public PersonalWorkSlidingTabStrip(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);

        mSelectedIndicatorHeight =
                getResources().getDimensionPixelSize(R.dimen.all_apps_tabs_indicator_height);

        mSelectedIndicatorPaint = new Paint();

        mDividerPaint = new Paint();
        mDividerPaint.setColor(Themes.getAttrColor(context, android.R.attr.colorControlHighlight));
        mDividerPaint.setStrokeWidth(
                getResources().getDimensionPixelSize(R.dimen.all_apps_divider_height));

        mSharedPreferences = Launcher.getLauncher(getContext()).getSharedPrefs();
        mIsRtl = Utilities.isRtl(getResources());

        mTextColorTertiary = Themes.getAttrColor(getContext(), android.R.attr.textColorTertiary);
        ColorEngine.getInstance(context)
                .addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT);
    }

    private void updateIndicatorPosition(float scrollOffset) {
        mScrollOffset = scrollOffset;
        updateIndicatorPosition();
    }

    private void updateTabTextColor(int pos) {
        mSelectedPosition = pos;
        for (int i = 0; i < getChildCount(); i++) {
            ColoredButton tab = (ColoredButton) getChildAt(i);
            tab.setTextColor(pos == i ? tab.getColor() : mTextColorTertiary);
        }
    }

    private void resetTabTextColor() {
        for (int i = 0; i < getChildCount(); i++) {
            ColoredButton tab = (ColoredButton) getChildAt(i);
            tab.reset();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateTabTextColor(mSelectedPosition);
        updateIndicatorPosition(mScrollOffset);
    }

    private void updateIndicatorPosition() {
        float scaled = mScrollOffset * (getChildCount() - 1);
        int left = -1, right = -1;
        int leftIndex = (int) Math.floor(scaled);
        float leftFraction = scaled - leftIndex;
        float rightFraction = 1 - leftFraction;
        ColoredButton leftTab = (ColoredButton) getChildAt(leftIndex);
        ColoredButton rightTab = (ColoredButton) getChildAt(leftIndex + 1);
        if (leftTab != null && rightTab != null) {
            left = (int) (leftTab.getLeft() + leftTab.getWidth() * leftFraction);
            right = (int) (rightTab.getRight() - (rightTab.getWidth() * rightFraction));
            int leftColor = leftTab.getColor();
            int rightColor = rightTab.getColor();
            if (leftColor == rightColor) {
                mSelectedIndicatorPaint.setColor(leftColor);
            } else {
                mSelectedIndicatorPaint.setColor(
                        (Integer) mArgbEvaluator.evaluate(leftFraction, leftColor, rightColor));
            }
        } else if (leftTab != null) {
            left = (int) (leftTab.getLeft() + leftTab.getWidth() * leftFraction);
            right = left + leftTab.getWidth();
            mSelectedIndicatorPaint.setColor(leftTab.getColor());
        } else if (rightTab != null) {
            right = (int) (rightTab.getRight() - (rightTab.getWidth() * rightFraction));
            left = right - rightTab.getWidth();
            mSelectedIndicatorPaint.setColor(rightTab.getColor());
        }
        setIndicatorPosition(left, right);
    }

    private View getLeftTab() {
        return mIsRtl ? getChildAt(getChildCount() - 1) : getChildAt(0);
    }

    private View getRightTab() {
        return mIsRtl ? getChildAt(0) : getChildAt(getChildCount() - 1);
    }

    private void setIndicatorPosition(int left, int right) {
        if (left != mIndicatorLeft || right != mIndicatorRight) {
            mIndicatorLeft = left;
            mIndicatorRight = right;
            invalidate();
            centerInScrollView();
        }
    }

    private void centerInScrollView() {
        HorizontalScrollView scrollView = (HorizontalScrollView) getParent();
        int padding = getLeft();
        int center = (mIndicatorLeft + mIndicatorRight) / 2 + padding;
        int scroll = center - (scrollView.getWidth() / 2);
        int maxAmount = getWidth() - scrollView.getWidth() + padding + padding;
        int boundedScroll = Utilities.boundToRange(scroll, 0, maxAmount);
        scrollView.scrollTo(boundedScroll, 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float y = getHeight() - mDividerPaint.getStrokeWidth();
        canvas.drawLine(getPaddingLeft(), y, getWidth() - getPaddingRight(), y, mDividerPaint);
        canvas.drawRect(mIndicatorLeft, getHeight() - mSelectedIndicatorHeight,
                mIndicatorRight, getHeight(), mSelectedIndicatorPaint);
    }

    public void highlightWorkTabIfNecessary() {
        if (mSharedPreferences.getBoolean(KEY_SHOWED_PEEK_WORK_TAB, false)) {
            return;
        }
        if (mLastActivePage != POSITION_PERSONAL) {
            return;
        }
        highlightWorkTab();
        mSharedPreferences.edit().putBoolean(KEY_SHOWED_PEEK_WORK_TAB, true).apply();
    }

    private void highlightWorkTab() {
        View v = getChildAt(POSITION_WORK);
        v.post(() -> {
            v.setPressed(true);
            v.setPressed(false);
        });
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        float scrollOffset = ((float) currentScroll) / totalScroll;
        updateIndicatorPosition(scrollOffset);
    }

    @Override
    public void setActiveMarker(int activePage) {
        updateTabTextColor(activePage);
        if (mContainerView != null && mLastActivePage != activePage) {
            mContainerView.onTabChanged(activePage);
        }
        mLastActivePage = activePage;
    }

    public void setContainerView(AllAppsContainerView containerView) {
        mContainerView = containerView;
    }

    @Override
    public void setMarkersCount(int numMarkers) {
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onColorChange(String resolver, int color, int foregroundColor) {
        resetTabTextColor();
        updateTabTextColor(mSelectedPosition);
        updateIndicatorPosition();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.width = LayoutParams.WRAP_CONTENT;
            lp.weight = 0;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int used = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            used += child.getMeasuredWidth();
        }
        if (used < getMeasuredWidth()) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                lp.width = 0;
                lp.weight = 1;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    void inflateButtons(AllAppsTabs tabs) {
        int childCount = getChildCount();
        int count = tabs.getCount();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = childCount; i < count; i++) {
            inflater.inflate(R.layout.all_apps_tab, this);
        }
        while (getChildCount() > count) {
            removeViewAt(0);
        }
        for (int i = 0; i < tabs.getCount(); i++) {
            Tab tab = tabs.get(i);
            ColoredButton button = (ColoredButton) getChildAt(i);
            button.setColorResolver(tab.getDrawerTab().getColorResolver());
            button.reset();
            button.setText(tab.getName());
            button.setOnLongClickListener(v -> {
                DrawerTabs.Tab drawerTab = tab.getDrawerTab();
                if (drawerTab instanceof CustomTab) {
                    DrawerTabEditBottomSheet.Companion
                            .edit(Launcher.getLauncher(getContext()), (CustomTab) drawerTab);
                    return true;
                }
                return false;
            });
        }
        updateIndicatorPosition();
        invalidate();
    }
}
