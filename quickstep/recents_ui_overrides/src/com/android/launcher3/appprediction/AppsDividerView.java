/**
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

import static com.android.launcher3.LauncherState.ALL_APPS;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.R;
import com.android.launcher3.allapps.FloatingHeaderRow;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.util.Themes;

/**
 * A view which shows a horizontal divider
 */
@TargetApi(Build.VERSION_CODES.O)
public class AppsDividerView extends View implements LauncherStateManager.StateListener,
        FloatingHeaderRow {

    private static final String ALL_APPS_VISITED_COUNT = "launcher.all_apps_visited_count";
    private static final int SHOW_ALL_APPS_LABEL_ON_ALL_APPS_VISITED_COUNT = 20;

    public enum DividerType {
        NONE,
        LINE,
        ALL_APPS_LABEL
    }

    private final Launcher mLauncher;
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

    public AppsDividerView(Context context) {
        this(context, null);
    }

    public AppsDividerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsDividerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);

        boolean isMainColorDark = Themes.getAttrBoolean(context, R.attr.isMainColorDark);
        mPaint.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.all_apps_divider_height));

        mStrokeColor = ContextCompat.getColor(context, isMainColorDark
                ? R.color.all_apps_prediction_row_separator_dark
                : R.color.all_apps_prediction_row_separator);

        mAllAppsLabelTextColor = ContextCompat.getColor(context, isMainColorDark
                ? R.color.all_apps_label_text_dark
                : R.color.all_apps_label_text);
    }

    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] rows, boolean tabsHidden) {
        mParent = parent;
        mTabsHidden = tabsHidden;
        mRows = rows;
        updateDividerType();
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
                    sectionCount ++;
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
            int side = getResources().getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
            int y = getHeight() - (getPaddingBottom() / 2);
            int x1 = getPaddingLeft() + side;
            int x2 = getWidth() - getPaddingRight() - side;
            canvas.drawLine(x1, y, x2, y, mPaint);
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
            mPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (shouldShowAllAppsLabel()) {
            mShowAllAppsLabel = true;
            mLauncher.getStateManager().addStateListener(this);
            updateDividerType();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mLauncher.getStateManager().removeStateListener(this);
    }

    @Override
    public void onStateTransitionStart(LauncherState toState) { }

    @Override
    public void onStateTransitionComplete(LauncherState finalState) {
        if (finalState == ALL_APPS) {
            setAllAppsVisitedCount(getAllAppsVisitedCount() + 1);
        } else {
            if (mShowAllAppsLabel != shouldShowAllAppsLabel()) {
                mShowAllAppsLabel = !mShowAllAppsLabel;
                updateDividerType();
            }

            if (!mShowAllAppsLabel) {
                mLauncher.getStateManager().removeStateListener(this);
            }
        }
    }

    private void setAllAppsVisitedCount(int count) {
        mLauncher.getSharedPrefs().edit().putInt(ALL_APPS_VISITED_COUNT, count).apply();
    }

    private int getAllAppsVisitedCount() {
        return mLauncher.getSharedPrefs().getInt(ALL_APPS_VISITED_COUNT, 0);
    }

    private boolean shouldShowAllAppsLabel() {
        return getAllAppsVisitedCount() < SHOW_ALL_APPS_LABEL_ON_ALL_APPS_VISITED_COUNT;
    }

    @Override
    public void setInsets(Rect insets, DeviceProfile grid) {
        int leftRightPadding = grid.desiredWorkspaceLeftRightMarginPx
                + grid.cellLayoutPaddingLeftRightPx;
        setPadding(leftRightPadding, getPaddingTop(), leftRightPadding, getPaddingBottom());
    }

    @Override
    public void setContentVisibility(boolean hasHeaderExtra, boolean hasAllAppsContent,
            PropertySetter setter, Interpolator headerFade, Interpolator allAppsFade) {
        // Don't use setViewAlpha as we want to control the visibility ourselves.
        setter.setFloat(this, ALPHA, hasAllAppsContent ? 1 : 0, allAppsFade);
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
}
