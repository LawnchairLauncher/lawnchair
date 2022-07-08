/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.appprediction;

import static com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.android.launcher3.R;
import com.android.launcher3.allapps.FloatingHeaderRow;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

/**
 * A view which shows a horizontal divider
 */
@TargetApi(Build.VERSION_CODES.O)
public class AppsDividerView extends View implements FloatingHeaderRow {

    public enum DividerType {
        NONE,
        LINE,
        ALL_APPS_LABEL
    }

    private final TextPaint mPaint = new TextPaint();
    private DividerType mDividerType = DividerType.NONE;

    private final @ColorInt int mStrokeColor;
    private final @ColorInt int mAllAppsLabelTextColor;

    private Layout mAllAppsLabelLayout;
    private boolean mShowAllAppsLabel;

    private FloatingHeaderView mParent;
    private boolean mTabsHidden;
    private FloatingHeaderRow[] mRows = FloatingHeaderRow.NO_ROWS;

    private boolean mIsScrolledOut = false;

    private final int[] mDividerSize;

    public AppsDividerView(Context context) {
        this(context, null);
    }

    public AppsDividerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsDividerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        boolean isMainColorDark = Themes.getAttrBoolean(context, R.attr.isMainColorDark);
        mDividerSize = new int[]{
                getResources().getDimensionPixelSize(R.dimen.all_apps_divider_width),
                getResources().getDimensionPixelSize(R.dimen.all_apps_divider_height)
        };

        mStrokeColor = ContextCompat.getColor(context, isMainColorDark
                ? R.color.all_apps_prediction_row_separator_dark
                : R.color.all_apps_prediction_row_separator);

        mAllAppsLabelTextColor = ContextCompat.getColor(context, isMainColorDark
                ? R.color.all_apps_label_text_dark
                : R.color.all_apps_label_text);

        mShowAllAppsLabel = !ActivityContext.lookupContext(
                getContext()).getOnboardingPrefs().hasReachedMaxCount(ALL_APPS_VISITED_COUNT);
    }

    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] rows, boolean tabsHidden) {
        mParent = parent;
        mTabsHidden = tabsHidden;
        mRows = rows;
        updateDividerType();
    }

    /** {@code true} if all apps label should be shown in place of divider. */
    public void setShowAllAppsLabel(boolean showAllAppsLabel) {
        if (showAllAppsLabel != mShowAllAppsLabel) {
            mShowAllAppsLabel = showAllAppsLabel;
            updateDividerType();
        }
    }

    @Override
    public int getExpectedHeight() {
        return getPaddingTop() + getPaddingBottom();
    }

    @Override
    public boolean shouldDraw() {
        return mDividerType != DividerType.NONE;
    }

    @Override
    public boolean hasVisibleContent() {
        return false;
    }

    private void updateDividerType() {
        final DividerType dividerType;
        if (!mTabsHidden) {
            dividerType = DividerType.NONE;
        } else {
            // Check how many sections above me.
            int sectionCount = 0;
            for (FloatingHeaderRow row : mRows) {
                if (row == this) {
                    break;
                } else if (row.shouldDraw()) {
                    sectionCount++;
                }
            }

            if (mShowAllAppsLabel && sectionCount > 0) {
                dividerType = DividerType.ALL_APPS_LABEL;
            } else if (sectionCount == 1) {
                dividerType = DividerType.LINE;
            } else {
                dividerType = DividerType.NONE;
            }
        }

        if (mDividerType != dividerType) {
            mDividerType = dividerType;
            int topPadding;
            int bottomPadding;
            switch (dividerType) {
                case LINE:
                    topPadding = 0;
                    bottomPadding = getResources()
                            .getDimensionPixelSize(R.dimen.all_apps_prediction_row_divider_height);
                    mPaint.setColor(mStrokeColor);
                    break;
                case ALL_APPS_LABEL:
                    topPadding = getAllAppsLabelLayout().getHeight() + getResources()
                            .getDimensionPixelSize(R.dimen.all_apps_label_top_padding);
                    bottomPadding = getResources()
                            .getDimensionPixelSize(R.dimen.all_apps_label_bottom_padding);
                    mPaint.setColor(mAllAppsLabelTextColor);
                    break;
                case NONE:
                default:
                    topPadding = bottomPadding = 0;
                    break;
            }
            setPadding(getPaddingLeft(), topPadding, getPaddingRight(), bottomPadding);
            updateViewVisibility();
            invalidate();
            requestLayout();
            if (mParent != null) {
                mParent.onHeightUpdated();
            }
        }
    }

    private void updateViewVisibility() {
        setVisibility(mDividerType == DividerType.NONE
                ? GONE
                : (mIsScrolledOut ? INVISIBLE : VISIBLE));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDividerType == DividerType.LINE) {
            int l = (getWidth() - mDividerSize[0]) / 2;
            int t = getHeight() - (getPaddingBottom() / 2);
            int radius = mDividerSize[1];
            canvas.drawRoundRect(l, t, l + mDividerSize[0], t + mDividerSize[1], radius, radius,
                    mPaint);
        } else if (mDividerType == DividerType.ALL_APPS_LABEL) {
            Layout textLayout = getAllAppsLabelLayout();
            int x = getWidth() / 2 - textLayout.getWidth() / 2;
            int y = getHeight() - getPaddingBottom() - textLayout.getHeight();
            canvas.translate(x, y);
            textLayout.draw(canvas);
            canvas.translate(-x, -y);
        }
    }

    private Layout getAllAppsLabelLayout() {
        if (mAllAppsLabelLayout == null) {
            mPaint.setAntiAlias(true);
            mPaint.setTypeface(Typeface.create("google-sans", Typeface.NORMAL));
            mPaint.setTextSize(
                    getResources().getDimensionPixelSize(R.dimen.all_apps_label_text_size));

            CharSequence allAppsLabelText = getResources().getText(R.string.all_apps_label);
            mAllAppsLabelLayout = StaticLayout.Builder.obtain(
                    allAppsLabelText, 0, allAppsLabelText.length(), mPaint,
                    Math.round(mPaint.measureText(allAppsLabelText.toString())))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(1)
                    .setIncludePad(true)
                    .build();
        }
        return mAllAppsLabelLayout;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getPaddingBottom() + getPaddingTop());
    }

    @Override
    public void setVerticalScroll(int scroll, boolean isScrolledOut) {
        setTranslationY(scroll);
        mIsScrolledOut = isScrolledOut;
        updateViewVisibility();
    }

    @Override
    public Class<AppsDividerView> getTypeClass() {
        return AppsDividerView.class;
    }

    @Override
    public View getFocusedChild() {
        return null;
    }
}
