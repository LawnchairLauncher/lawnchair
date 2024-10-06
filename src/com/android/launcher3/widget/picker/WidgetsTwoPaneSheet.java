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

import static com.android.launcher3.Flags.enableCategorizedWidgetSuggestions;
import static com.android.launcher3.Flags.enableUnfoldedTwoPanePicker;
import static com.android.launcher3.UtilitiesKt.CLIP_CHILDREN_FALSE_MODIFIER;
import static com.android.launcher3.UtilitiesKt.CLIP_TO_PADDING_FALSE_MODIFIER;
import static com.android.launcher3.UtilitiesKt.modifyAttributesOnViewTree;
import static com.android.launcher3.UtilitiesKt.restoreAttributesOnViewTree;

import android.content.Context;
import android.graphics.Rect;
import android.os.Process;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.WidgetCell;
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

    // This ratio defines the max percentage of content area that the recommendations can display
    // with respect to the bottom sheet's height.
    private static final float RECOMMENDATION_SECTION_HEIGHT_RATIO_TWO_PANE = 0.70f;
    private FrameLayout mSuggestedWidgetsContainer;
    private WidgetsListHeader mSuggestedWidgetsHeader;
    private PackageUserKey mSuggestedWidgetsPackageUserKey;
    private View mPrimaryWidgetListView;
    private LinearLayout mRightPane;

    private ScrollView mRightPaneScrollView;
    private WidgetsListTableViewHolderBinder mWidgetsListTableViewHolderBinder;

    private boolean mOldIsSwipeToDismissInProgress;
    private int mActivePage = -1;
    @Nullable
    private PackageUserKey mSelectedHeader;

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

        mWidgetRecommendationsContainer = mContent.findViewById(
                R.id.widget_recommendations_container);
        mWidgetRecommendationsView = mContent.findViewById(
                R.id.widget_recommendations_view);
        mWidgetRecommendationsView.initParentViews(mWidgetRecommendationsContainer);
        mWidgetRecommendationsView.setWidgetCellLongClickListener(this);
        mWidgetRecommendationsView.setWidgetCellOnClickListener(this);
        // To save the currently displayed page, so that, it can be requested when rebinding
        // recommendations with different size constraints.
        mWidgetRecommendationsView.addPageSwitchListener(
                newPage -> mRecommendationsCurrentPage = newPage);

        mHeaderTitle = mContent.findViewById(R.id.title);
        mRightPane = mContent.findViewById(R.id.right_pane);
        mRightPaneScrollView = mContent.findViewById(R.id.right_pane_scroll_view);
        mRightPaneScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        mPrimaryWidgetListView = findViewById(R.id.primary_widgets_list_view);
        mPrimaryWidgetListView.setOutlineProvider(mViewOutlineProvider);
        mPrimaryWidgetListView.setClipToOutline(true);

        onWidgetsBound();

        // Set the fast scroller as not visible for two pane layout.
        mFastScroller.setVisibility(GONE);
    }

    @Override
    protected int getTabletHorizontalMargin(DeviceProfile deviceProfile) {
        if (enableCategorizedWidgetSuggestions()) {
            // two pane picker is full width for fold as well as tablet.
            return getResources().getDimensionPixelSize(
                    R.dimen.widget_picker_two_panels_left_right_margin);
        }
        if (deviceProfile.isTwoPanels && enableUnfoldedTwoPanePicker()) {
            // enableUnfoldedTwoPanePicker made two pane picker full-width for fold only.
            return getResources().getDimensionPixelSize(
                    R.dimen.widget_picker_two_panels_left_right_margin);
        }
        if (deviceProfile.isLandscape && !deviceProfile.isTwoPanels) {
            // non-fold tablet landscape margins (ag/22163531)
            return getResources().getDimensionPixelSize(
                    R.dimen.widget_picker_landscape_tablet_left_right_margin);
        }
        return deviceProfile.allAppsLeftRightMargin;
    }

    @Override
    protected void onUserSwipeToDismissProgressChanged() {
        super.onUserSwipeToDismissProgressChanged();
        boolean isSwipeToDismissInProgress = mSwipeToDismissProgress.value > 0;
        if (isSwipeToDismissInProgress == mOldIsSwipeToDismissInProgress) {
            return;
        }
        mOldIsSwipeToDismissInProgress = isSwipeToDismissInProgress;
        if (isSwipeToDismissInProgress) {
            modifyAttributesOnViewTree(mPrimaryWidgetListView, (ViewParent) mContent,
                    CLIP_CHILDREN_FALSE_MODIFIER);
            modifyAttributesOnViewTree(mRightPaneScrollView,  (ViewParent) mContent,
                    CLIP_CHILDREN_FALSE_MODIFIER, CLIP_TO_PADDING_FALSE_MODIFIER);
        } else {
            restoreAttributesOnViewTree(mPrimaryWidgetListView, mContent,
                    CLIP_CHILDREN_FALSE_MODIFIER);
            restoreAttributesOnViewTree(mRightPaneScrollView, mContent,
                    CLIP_CHILDREN_FALSE_MODIFIER, CLIP_TO_PADDING_FALSE_MODIFIER);
        }
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

            post(() -> {
                // The following calls all trigger requestLayout, so we post them to avoid
                // calling requestLayout during a layout pass. This also fixes the related warnings
                // in logcat.
                leftPane.setLayoutParams(layoutParams);
                requestApplyInsets();
                if (mSelectedHeader != null) {
                    if (mSelectedHeader.equals(mSuggestedWidgetsPackageUserKey)) {
                        mSuggestedWidgetsHeader.callOnClick();
                    } else {
                        getHeaderChangeListener().onHeaderChanged(mSelectedHeader);
                    }
                }
            });
        }
    }

    @Override
    public void onWidgetsBound() {
        super.onWidgetsBound();
        if (mRecommendedWidgetsCount == 0 && mSelectedHeader == null) {
            mAdapters.get(mActivePage).mWidgetsListAdapter.selectFirstHeaderEntry();
            mAdapters.get(mActivePage).mWidgetsRecyclerView.scrollToTop();
        }
    }

    @Override
    public void onRecommendedWidgetsBound() {
        super.onRecommendedWidgetsBound();

        if (mSuggestedWidgetsContainer == null && mRecommendedWidgetsCount > 0) {
            setupSuggestedWidgets(LayoutInflater.from(getContext()));
            mSuggestedWidgetsHeader.callOnClick();
        } else if (mSelectedHeader != null
                && mSelectedHeader.equals(mSuggestedWidgetsPackageUserKey)) {
            // Reselect widget if we are reloading recommendations while it is currently showing.
            selectWidgetCell(mWidgetRecommendationsContainer, getLastSelectedWidgetItem());
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
        String suggestionsHeaderTitle = getContext().getString(
                R.string.suggested_widgets_header_title);
        String suggestionsRightPaneTitle = getContext().getString(
                R.string.widget_picker_right_pane_accessibility_title, suggestionsHeaderTitle);
        packageItemInfo.title = suggestionsHeaderTitle;
        // Suggestions may update at run time. The widgets count on suggestions doesn't add any
        // value, so, we don't show the count.
        WidgetsListHeaderEntry widgetsListHeaderEntry = WidgetsListHeaderEntry.create(
                        packageItemInfo,
                        /*titleSectionName=*/ suggestionsHeaderTitle,
                        /*items=*/ mActivityContext.getPopupDataProvider().getRecommendedWidgets(),
                        /*visibleWidgetsCount=*/ 0)
                .withWidgetListShown();

        mSuggestedWidgetsHeader.applyFromItemInfoWithIcon(widgetsListHeaderEntry);
        mSuggestedWidgetsHeader.setIcon(
                getContext().getDrawable(R.drawable.widget_suggestions_icon));
        mSuggestedWidgetsHeader.setOnClickListener(view -> {
            mSuggestedWidgetsHeader.setExpanded(true);
            resetExpandedHeaders();
            mRightPane.removeAllViews();
            mRightPane.addView(mWidgetRecommendationsContainer);
            mRightPaneScrollView.setScrollY(0);
            mRightPane.setAccessibilityPaneTitle(suggestionsRightPaneTitle);
            mSuggestedWidgetsPackageUserKey = PackageUserKey.fromPackageItemInfo(packageItemInfo);
            final boolean isChangingHeaders = mSelectedHeader == null
                    || !mSelectedHeader.equals(mSuggestedWidgetsPackageUserKey);
            if (isChangingHeaders)  {
                // If switching from another header, unselect any WidgetCells. This is necessary
                // because we do not clear/recycle the WidgetCells in the recommendations container
                // when the header is clicked, only when onRecommendationsBound is called. That
                // means a WidgetCell in the recommendations container may still be selected from
                // the last time the recommendations were shown.
                unselectWidgetCell(mWidgetRecommendationsContainer, getLastSelectedWidgetItem());
            }
            mSelectedHeader = mSuggestedWidgetsPackageUserKey;
        });
        mSuggestedWidgetsContainer.addView(mSuggestedWidgetsHeader);
        mRightPane.setAccessibilityPaneTitle(suggestionsRightPaneTitle);
    }

    @Override
    @Px
    protected float getMaxAvailableHeightForRecommendations() {
        if (mRecommendedWidgetsCount > 0) {
            // If widgets were already selected for display, we show them all on orientation change
            // in a two pane picker
            return Float.MAX_VALUE;
        }

        return (mDeviceProfile.heightPx - mDeviceProfile.bottomSheetTopPadding)
                * RECOMMENDATION_SECTION_HEIGHT_RATIO_TWO_PANE;
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
                final boolean isSameHeader = mSelectedHeader != null
                        && mSelectedHeader.equals(selectedHeader);
                mSelectedHeader = selectedHeader;
                WidgetsListContentEntry contentEntry = mActivityContext.getPopupDataProvider()
                        .getSelectedAppWidgets(selectedHeader);

                if (contentEntry == null || mRightPane == null) {
                    return;
                }

                if (mSuggestedWidgetsHeader != null) {
                    mSuggestedWidgetsHeader.setExpanded(false);
                }

                WidgetsListContentEntry contentEntryToBind;
                if (enableCategorizedWidgetSuggestions()) {
                    // Setting max span size enables row to understand how to fit more than one item
                    // in a row.
                    contentEntryToBind = contentEntry.withMaxSpanSize(mMaxSpanPerRow);
                } else {
                    contentEntryToBind = contentEntry;
                }

                WidgetsRowViewHolder widgetsRowViewHolder =
                        mWidgetsListTableViewHolderBinder.newViewHolder(mRightPane);
                mWidgetsListTableViewHolderBinder.bindViewHolder(widgetsRowViewHolder,
                        contentEntryToBind,
                        ViewHolderBinder.POSITION_FIRST | ViewHolderBinder.POSITION_LAST,
                        Collections.EMPTY_LIST);
                if (isSameHeader) {
                    // Reselect the last selected widget if we are reloading the same header.
                    selectWidgetCell(widgetsRowViewHolder.tableContainer,
                            getLastSelectedWidgetItem());
                }
                widgetsRowViewHolder.mDataCallback = data -> {
                    mWidgetsListTableViewHolderBinder.bindViewHolder(widgetsRowViewHolder,
                            contentEntryToBind,
                            ViewHolderBinder.POSITION_FIRST | ViewHolderBinder.POSITION_LAST,
                            Collections.singletonList(data));
                    if (isSameHeader) {
                        selectWidgetCell(widgetsRowViewHolder.tableContainer,
                                getLastSelectedWidgetItem());
                    }
                };
                mRightPane.removeAllViews();
                mRightPane.addView(widgetsRowViewHolder.itemView);
                mRightPaneScrollView.setScrollY(0);
                mRightPane.setAccessibilityPaneTitle(
                        getContext().getString(
                                R.string.widget_picker_right_pane_accessibility_title,
                                contentEntry.mPkgItem.title));
            }
        };
    }

    private static void selectWidgetCell(ViewGroup parent, WidgetItem item) {
        if (parent == null || item == null) return;
        WidgetCell cell = Utilities.findViewByPredicate(parent, v -> v instanceof WidgetCell wc
                && wc.matchesItem(item));
        if (cell != null && !cell.isShowingAddButton()) {
            cell.callOnClick();
        }
    }

    private static void unselectWidgetCell(ViewGroup parent, WidgetItem item) {
        if (parent == null || item == null) return;
        WidgetCell cell = Utilities.findViewByPredicate(parent, v -> v instanceof WidgetCell wc
                && wc.matchesItem(item));
        if (cell != null && cell.isShowingAddButton()) {
            cell.hideAddButton(/* animate= */ false);
        }
    }

    @Override
    public void setInsets(Rect insets) {
        super.setInsets(insets);
        FrameLayout rightPaneContainer = mContent.findViewById(R.id.right_pane_container);
        rightPaneContainer.setPadding(
                rightPaneContainer.getPaddingLeft(),
                rightPaneContainer.getPaddingTop(),
                rightPaneContainer.getPaddingRight(),
                mBottomPadding);
        requestLayout();
    }

    @Override
    protected int getWidgetListHorizontalMargin() {
        return getResources().getDimensionPixelSize(
                R.dimen.widget_list_left_pane_horizontal_margin);
    }

    @Override
    protected boolean isTwoPane() {
        return true;
    }

    @Override
    protected int getHeaderTopClip(@NonNull WidgetCell cell) {
        return 0;
    }

    @Override
    protected void scrollCellContainerByY(WidgetCell wc, int scrollByY) {
        for (ViewParent parent = wc.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollView scrollView) {
                scrollView.smoothScrollBy(0, scrollByY);
                return;
            } else if (parent == this) {
                return;
            }
        }
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
