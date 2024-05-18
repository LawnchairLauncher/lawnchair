/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

import static com.android.launcher3.Flags.enableUnfoldedTwoPanePicker;

import android.content.Context;
import android.graphics.Outline;
import android.os.Process;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Px;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;

import java.util.Collections;
import java.util.List;

/**
 * Popup for showing the full list of available widgets with a two-pane layout.
 */
public class WidgetsTwoPaneSheet extends WidgetsFullSheet {

    private static final int PERSONAL_TAB = 0;
    private static final int WORK_TAB = 1;
    private static final int MINIMUM_WIDTH_LEFT_PANE_FOLDABLE_DP = 268;
    private static final int MAXIMUM_WIDTH_LEFT_PANE_FOLDABLE_DP = 395;
    private static final String SUGGESTIONS_PACKAGE_NAME = "widgets_list_suggestions_entry";

    private LinearLayout mSuggestedWidgetsContainer;
    private WidgetsListHeader mSuggestedWidgetsHeader;
    private LinearLayout mRightPane;

    private ScrollView mRightPaneScrollView;
    private WidgetsListTableViewHolderBinder mWidgetsListTableViewHolderBinder;
    private int mActivePage = -1;

    private final ViewOutlineProvider mViewOutlineProviderRightPane = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(
                    0,
                    0,
                    view.getMeasuredWidth(),
                    view.getMeasuredHeight() - getResources().getDimensionPixelSize(
                            R.dimen.widget_list_horizontal_margin_two_pane),
                    view.getResources().getDimensionPixelSize(
                            R.dimen.widget_list_top_bottom_corner_radius)
            );
        }
    };

    public WidgetsTwoPaneSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public WidgetsTwoPaneSheet(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void setupSheet() {
        // Set the header change listener in the adapter
        mAdapters.get(AdapterHolder.PRIMARY)
                .mWidgetsListAdapter.setHeaderChangeListener(getHeaderChangeListener());
        mAdapters.get(AdapterHolder.WORK)
                .mWidgetsListAdapter.setHeaderChangeListener(getHeaderChangeListener());
        mAdapters.get(AdapterHolder.SEARCH)
                .mWidgetsListAdapter.setHeaderChangeListener(getHeaderChangeListener());

        LayoutInflater layoutInflater = LayoutInflater.from(getContext());

        int contentLayoutRes = mHasWorkProfile ? R.layout.widgets_two_pane_sheet_paged_view
                : R.layout.widgets_two_pane_sheet_recyclerview;
        layoutInflater.inflate(contentLayoutRes, findViewById(R.id.recycler_view_container), true);

        setupViews();

        mWidgetsListTableViewHolderBinder =
                new WidgetsListTableViewHolderBinder(mActivityContext, layoutInflater, this, this);
        mRecommendedWidgetsTable = mContent.findViewById(R.id.recommended_widget_table);
        mRecommendedWidgetsTable.setWidgetCellLongClickListener(this);
        mRecommendedWidgetsTable.setWidgetCellOnClickListener(this);
        mHeaderTitle = mContent.findViewById(R.id.title);
        mRightPane = mContent.findViewById(R.id.right_pane);
        mRightPane.setOutlineProvider(mViewOutlineProviderRightPane);
        mRightPaneScrollView = mContent.findViewById(R.id.right_pane_scroll_view);
        mRightPaneScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        onRecommendedWidgetsBound();
        onWidgetsBound();
        setUpEducationViewsIfNeeded();

        // Set the fast scroller as not visible for two pane layout.
        mFastScroller.setVisibility(GONE);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && mDeviceProfile.isTwoPanels && enableUnfoldedTwoPanePicker()) {
            LinearLayout layout = mContent.findViewById(R.id.linear_layout_container);
            FrameLayout leftPane = layout.findViewById(R.id.recycler_view_container);
            LinearLayout.LayoutParams layoutParams = (LayoutParams) leftPane.getLayoutParams();
            // Width is 1/3 of the sheet unless it's less than min width or max width
            int leftPaneWidth = layout.getMeasuredWidth() / 3;
            @Px int minLeftPaneWidthPx = Utilities.dpToPx(MINIMUM_WIDTH_LEFT_PANE_FOLDABLE_DP);
            @Px int maxLeftPaneWidthPx = Utilities.dpToPx(MAXIMUM_WIDTH_LEFT_PANE_FOLDABLE_DP);
            if (leftPaneWidth < minLeftPaneWidthPx) {
                layoutParams.width = minLeftPaneWidthPx;
            } else if (leftPaneWidth > maxLeftPaneWidthPx) {
                layoutParams.width = maxLeftPaneWidthPx;
            } else {
                layoutParams.width = 0;
            }
            layoutParams.weight = layoutParams.width == 0 ? 0.33F : 0;
            leftPane.setLayoutParams(layoutParams);
        }
    }

    @Override
    public void onRecommendedWidgetsBound() {
        super.onRecommendedWidgetsBound();

        if (mSuggestedWidgetsContainer == null && mHasRecommendedWidgets) {
            setupSuggestedWidgets(LayoutInflater.from(getContext()));
            mSuggestedWidgetsHeader.callOnClick();
        }
    }

    private void setupSuggestedWidgets(LayoutInflater layoutInflater) {
        // Add suggested widgets.
        mSuggestedWidgetsContainer = mSearchScrollView.findViewById(R.id.suggestions_header);

        // Inflate the suggestions header.
        mSuggestedWidgetsHeader = (WidgetsListHeader) layoutInflater.inflate(
                R.layout.widgets_list_row_header_two_pane,
                mSuggestedWidgetsContainer,
                false);
        mSuggestedWidgetsHeader.setExpanded(true);

        PackageItemInfo packageItemInfo = new PackageItemInfo(
                /* packageName= */ SUGGESTIONS_PACKAGE_NAME,
                Process.myUserHandle()) {
            @Override
            public boolean usingLowResIcon() {
                return false;
            }
        };
        packageItemInfo.title = getContext().getString(R.string.suggested_widgets_header_title);
        WidgetsListHeaderEntry widgetsListHeaderEntry = WidgetsListHeaderEntry.create(
                        packageItemInfo,
                        getContext().getString(R.string.suggested_widgets_header_title),
                        mActivityContext.getPopupDataProvider().getRecommendedWidgets())
                .withWidgetListShown();

        mSuggestedWidgetsHeader.applyFromItemInfoWithIcon(widgetsListHeaderEntry);
        mSuggestedWidgetsHeader.setIcon(
                getContext().getDrawable(R.drawable.widget_suggestions_icon));
        mSuggestedWidgetsHeader.setOnClickListener(view -> {
            mSuggestedWidgetsHeader.setExpanded(true);
            resetExpandedHeaders();
            mRightPane.removeAllViews();
            mRightPane.addView(mRecommendedWidgetsTable);
            mRightPaneScrollView.setScrollY(0);
        });
        mSuggestedWidgetsContainer.addView(mSuggestedWidgetsHeader);
    }

    @Override
    protected float getMaxTableHeight(float noWidgetsViewHeight) {
        return Float.MAX_VALUE;
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        super.onActivePageChanged(currentActivePage);

        // If active page didn't change then we don't want to update the header.
        if (mActivePage == currentActivePage) {
            return;
        }

        mActivePage = currentActivePage;

        if (mSuggestedWidgetsHeader == null) {
            mAdapters.get(currentActivePage).mWidgetsListAdapter.selectFirstHeaderEntry();
            mAdapters.get(currentActivePage).mWidgetsRecyclerView.scrollToTop();
        } else if (currentActivePage == PERSONAL_TAB || currentActivePage == WORK_TAB) {
            mSuggestedWidgetsHeader.callOnClick();
        }
    }

    @Override
    protected void updateRecyclerViewVisibility(AdapterHolder adapterHolder) {
        // The first item is always an empty space entry. Look for any more items.
        boolean isWidgetAvailable = adapterHolder.mWidgetsListAdapter.hasVisibleEntries();

        mRightPane.setVisibility(isWidgetAvailable ? VISIBLE : GONE);

        super.updateRecyclerViewVisibility(adapterHolder);
    }

    @Override
    public void onSearchResults(List<WidgetsListBaseEntry> entries) {
        super.onSearchResults(entries);
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsListAdapter.selectFirstHeaderEntry();
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView.scrollToTop();
    }

    @Override
    protected boolean shouldScroll(MotionEvent ev) {
        return getPopupContainer().isEventOverView(mRightPaneScrollView, ev)
                ? mRightPaneScrollView.canScrollVertically(-1)
                : super.shouldScroll(ev);
    }

    @Override
    protected void setViewVisibilityBasedOnSearch(boolean isInSearchMode) {
        super.setViewVisibilityBasedOnSearch(isInSearchMode);

        if (mSuggestedWidgetsHeader != null && mSuggestedWidgetsContainer != null) {
            if (!isInSearchMode) {
                mSuggestedWidgetsContainer.setVisibility(VISIBLE);
                mSuggestedWidgetsHeader.callOnClick();
            } else {
                mSuggestedWidgetsContainer.setVisibility(GONE);
            }
        } else if (!isInSearchMode) {
            mAdapters.get(mActivePage).mWidgetsListAdapter.selectFirstHeaderEntry();
        }

    }

    @Override
    protected View getContentView() {
        return mRightPane;
    }

    private HeaderChangeListener getHeaderChangeListener() {
        return new HeaderChangeListener() {
            @Override
            public void onHeaderChanged(@NonNull PackageUserKey selectedHeader) {
                WidgetsListContentEntry contentEntry = mActivityContext.getPopupDataProvider()
                        .getSelectedAppWidgets(selectedHeader);

                if (contentEntry == null || mRightPane == null) {
                    return;
                }

                if (mSuggestedWidgetsHeader != null) {
                    mSuggestedWidgetsHeader.setExpanded(false);
                }
                WidgetsRowViewHolder widgetsRowViewHolder =
                        mWidgetsListTableViewHolderBinder.newViewHolder(mRightPane);
                mWidgetsListTableViewHolderBinder.bindViewHolder(widgetsRowViewHolder,
                        contentEntry,
                        ViewHolderBinder.POSITION_FIRST | ViewHolderBinder.POSITION_LAST,
                        Collections.EMPTY_LIST);
                widgetsRowViewHolder.mDataCallback = data -> {
                    mWidgetsListTableViewHolderBinder.bindViewHolder(widgetsRowViewHolder,
                            contentEntry,
                            ViewHolderBinder.POSITION_FIRST | ViewHolderBinder.POSITION_LAST,
                            Collections.singletonList(data));
                };
                mRightPane.removeAllViews();
                mRightPane.addView(widgetsRowViewHolder.itemView);
                mRightPaneScrollView.setScrollY(0);
            }
        };
    }

    @Override
    protected boolean isTwoPane() {
        return true;
    }

    /**
     * This is a listener for when the selected header gets changed in the left pane.
     */
    public interface HeaderChangeListener {
        /**
         * Sets the right pane to have the widgets for the currently selected header from
         * the left pane.
         */
        void onHeaderChanged(@NonNull PackageUserKey selectedHeader);
    }
}
