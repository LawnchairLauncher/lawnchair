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

import android.content.Context;
import android.graphics.Rect;
import android.support.animation.FloatValueHolder;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.method.TextKeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.allapps.SearchUiManager;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.discovery.AppDiscoveryItem;
import com.android.launcher3.discovery.AppDiscoveryUpdateState;
import com.android.launcher3.graphics.TintedDrawableSpan;
import com.android.launcher3.util.ComponentKey;
import java.util.ArrayList;

/**
 * Layout to contain the All-apps search UI.
 */
public class AppsSearchContainerLayout extends FrameLayout
        implements SearchUiManager, AllAppsSearchBarController.Callbacks {

    private final Launcher mLauncher;
    private final int mMinHeight;
    private final int mSearchBoxHeight;
    private final AllAppsSearchBarController mSearchBarController;
    private final SpannableStringBuilder mSearchQueryBuilder;

    private ExtendedEditText mSearchInput;
    private AlphabeticalAppsList mApps;
    private AllAppsRecyclerView mAppsRecyclerView;
    private AllAppsGridAdapter mAdapter;
    private View mDivider;
    private HeaderElevationController mElevationController;

    private SpringAnimation mSpring;

    public AppsSearchContainerLayout(Context context) {
        this(context, null);
    }

    public AppsSearchContainerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsSearchContainerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);
        mMinHeight = getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_height);
        mSearchBoxHeight = getResources()
                .getDimensionPixelSize(R.dimen.all_apps_search_bar_field_height);
        mSearchBarController = new AllAppsSearchBarController();

        mSearchQueryBuilder = new SpannableStringBuilder();
        Selection.setSelection(mSearchQueryBuilder, 0);

        // Note: This spring does nothing.
        mSpring = new SpringAnimation(new FloatValueHolder()).setSpring(new SpringForce(0));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSearchInput = findViewById(R.id.search_box_input);
        mDivider = findViewById(R.id.search_divider);
        mElevationController = new HeaderElevationController(mDivider);

        // Update the hint to contain the icon.
        // Prefix the original hint with two spaces. The first space gets replaced by the icon
        // using span. The second space is used for a singe space character between the hint
        // and the icon.
        SpannableString spanned = new SpannableString("  " + mSearchInput.getHint());
        spanned.setSpan(new TintedDrawableSpan(getContext(), R.drawable.ic_allapps_search),
                0, 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        mSearchInput.setHint(spanned);

        DeviceProfile dp = mLauncher.getDeviceProfile();
        if (!dp.isVerticalBarLayout()) {
            LayoutParams lp = (LayoutParams) mDivider.getLayoutParams();
            lp.leftMargin = lp.rightMargin = dp.edgeMarginPx;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            getLayoutParams().height = mLauncher.getDragLayer().getInsets().top + mMinHeight;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }


    @Override
    public void initialize(
            AlphabeticalAppsList appsList, AllAppsRecyclerView recyclerView) {
        mApps = appsList;
        mAppsRecyclerView = recyclerView;
        mAppsRecyclerView.addOnScrollListener(mElevationController);
        mAdapter = (AllAppsGridAdapter) mAppsRecyclerView.getAdapter();
        mSearchBarController.initialize(
                new DefaultAppSearchAlgorithm(getContext(), appsList.getApps()), mSearchInput, mLauncher, this);
    }

    @Override
    public @NonNull SpringAnimation getSpringForFling() {
        return mSpring;
    }

    @Override
    public void refreshSearchResult() {
        mSearchBarController.refreshSearchResult();
    }

    @Override
    public void reset() {
        mElevationController.reset();
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
            mAdapter.setLastSearchQuery(query);
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
    }

    @Override
    public void onAppDiscoverySearchUpdate(
            @Nullable AppDiscoveryItem app, @NonNull AppDiscoveryUpdateState state) {
        if (!mLauncher.isDestroyed()) {
            mApps.onAppDiscoverySearchUpdate(app, state);
            notifyResultChanged();
        }
    }

    private void notifyResultChanged() {
        mElevationController.reset();
        mAppsRecyclerView.onSearchResultsChanged();
    }

    @Override
    public void addOnScrollRangeChangeListener(final OnScrollRangeChangeListener listener) {
        mLauncher.getHotseat().addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                DeviceProfile dp = mLauncher.getDeviceProfile();
                if (!dp.isVerticalBarLayout()) {
                    Rect insets = mLauncher.getDragLayer().getInsets();
                    int hotseatBottom = bottom - dp.hotseatBarBottomPaddingPx - insets.bottom;
                    int searchTopMargin = insets.top + (mMinHeight - mSearchBoxHeight)
                            + ((MarginLayoutParams) getLayoutParams()).bottomMargin;
                    listener.onScrollRangeChanged(hotseatBottom - searchTopMargin);
                } else {
                    listener.onScrollRangeChanged(bottom);
                }
            }
        });
    }
}
