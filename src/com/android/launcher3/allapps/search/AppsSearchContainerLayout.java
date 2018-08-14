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
package com.android.launcher3.allapps.search;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.getSize;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.graphics.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.content.Context;
import android.graphics.Rect;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.method.TextKeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.allapps.SearchUiManager;
import com.android.launcher3.graphics.TintedDrawableSpan;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;

/**
 * Layout to contain the All-apps search UI.
 */
public class AppsSearchContainerLayout extends ExtendedEditText
        implements SearchUiManager, AllAppsSearchBarController.Callbacks,
        AllAppsStore.OnUpdateListener, Insettable {


    private final Launcher mLauncher;
    private final AllAppsSearchBarController mSearchBarController;
    private final SpannableStringBuilder mSearchQueryBuilder;

    private AlphabeticalAppsList mApps;
    private AllAppsContainerView mAppsView;

    // This value was used to position the QSB. We store it here for translationY animations.
    private final float mFixedTranslationY;
    private final float mMarginTopAdjusting;

    public AppsSearchContainerLayout(Context context) {
        this(context, null);
    }

    public AppsSearchContainerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsSearchContainerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);
        mSearchBarController = new AllAppsSearchBarController();

        mSearchQueryBuilder = new SpannableStringBuilder();
        Selection.setSelection(mSearchQueryBuilder, 0);

        mFixedTranslationY = getTranslationY();
        mMarginTopAdjusting = mFixedTranslationY - getPaddingTop();

        // Update the hint to contain the icon.
        // Prefix the original hint with two spaces. The first space gets replaced by the icon
        // using span. The second space is used for a singe space character between the hint
        // and the icon.
        SpannableString spanned = new SpannableString("  " + getHint());
        spanned.setSpan(new TintedDrawableSpan(getContext(), R.drawable.ic_allapps_search),
                0, 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        setHint(spanned);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mLauncher.getAppsView().getAppsStore().addUpdateListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mLauncher.getAppsView().getAppsStore().removeUpdateListener(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Update the width to match the grid padding
        DeviceProfile dp = mLauncher.getDeviceProfile();
        int myRequestedWidth = getSize(widthMeasureSpec);
        int rowWidth = myRequestedWidth - mAppsView.getActiveRecyclerView().getPaddingLeft()
                - mAppsView.getActiveRecyclerView().getPaddingRight();

        int cellWidth = DeviceProfile.calculateCellWidth(rowWidth, dp.inv.numHotseatIcons);
        int iconVisibleSize = Math.round(ICON_VISIBLE_AREA_FACTOR * dp.iconSizePx);
        int iconPadding = cellWidth - iconVisibleSize;

        int myWidth = rowWidth - iconPadding + getPaddingLeft() + getPaddingRight();
        super.onMeasure(makeMeasureSpec(myWidth, EXACTLY), heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // Shift the widget horizontally so that its centered in the parent (b/63428078)
        View parent = (View) getParent();
        int availableWidth = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        int myWidth = right - left;
        int expectedLeft = parent.getPaddingLeft() + (availableWidth - myWidth) / 2;
        int shift = expectedLeft - left;
        setTranslationX(shift);
    }

    @Override
    public void initialize(AllAppsContainerView appsView) {
        mApps = appsView.getApps();
        mAppsView = appsView;
        mSearchBarController.initialize(
                new DefaultAppSearchAlgorithm(mApps.getApps()), this, mLauncher, this);
    }

    @Override
    public void onAppsUpdated() {
        mSearchBarController.refreshSearchResult();
    }

    @Override
    public void resetSearch() {
        mSearchBarController.reset();
    }

    @Override
    public void preDispatchKeyEvent(KeyEvent event) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (!mSearchBarController.isSearchFieldFocused() &&
                event.getAction() == KeyEvent.ACTION_DOWN) {
            final int unicodeChar = event.getUnicodeChar();
            final boolean isKeyNotWhitespace = unicodeChar > 0 &&
                    !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar);
            if (isKeyNotWhitespace) {
                boolean gotKey = TextKeyListener.getInstance().onKeyDown(this, mSearchQueryBuilder,
                        event.getKeyCode(), event);
                if (gotKey && mSearchQueryBuilder.length() > 0) {
                    mSearchBarController.focusSearchField();
                }
            }
        }
    }

    @Override
    public void onSearchResult(String query, ArrayList<ComponentKey> apps) {
        if (apps != null) {
            mApps.setOrderedFilter(apps);
            notifyResultChanged();
            mAppsView.setLastSearchQuery(query);
        }
    }

    @Override
    public void clearSearchResult() {
        if (mApps.setOrderedFilter(null)) {
            notifyResultChanged();
        }

        // Clear the search query
        mSearchQueryBuilder.clear();
        mSearchQueryBuilder.clearSpans();
        Selection.setSelection(mSearchQueryBuilder, 0);
        mAppsView.onClearSearchResult();
    }

    private void notifyResultChanged() {
        mAppsView.onSearchResultsChanged();
    }

    @Override
    public void setInsets(Rect insets) {
        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        mlp.topMargin = Math.round(Math.max(-mFixedTranslationY, insets.top - mMarginTopAdjusting));
        requestLayout();

        DeviceProfile dp = mLauncher.getDeviceProfile();
        if (dp.isVerticalBarLayout()) {
            mLauncher.getAllAppsController().setScrollRangeDelta(0);
        } else {
            mLauncher.getAllAppsController().setScrollRangeDelta(
                    insets.bottom + mlp.topMargin + mFixedTranslationY);
        }
    }
}
