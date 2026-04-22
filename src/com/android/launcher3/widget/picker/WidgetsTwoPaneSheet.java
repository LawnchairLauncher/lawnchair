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

import static com.android.launcher3.Flags.enableTieredWidgetsByDefaultInPicker;
import static com.android.launcher3.UtilitiesKt.CLIP_CHILDREN_FALSE_MODIFIER;
import static com.android.launcher3.UtilitiesKt.CLIP_TO_PADDING_FALSE_MODIFIER;
import static com.android.launcher3.UtilitiesKt.modifyAttributesOnViewTree;
import static com.android.launcher3.UtilitiesKt.restoreAttributesOnViewTree;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;
import static com.android.launcher3.widget.picker.WidgetsListItemAnimator.WIDGET_LIST_ITEM_APPEARANCE_DELAY;
import static com.android.launcher3.widget.picker.model.data.WidgetPickerDataUtils.findContentEntryForPackageUser;

import android.content.Context;
import android.graphics.Rect;
import android.os.Process;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.cache.CacheLookupFlag;
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
    private TextView mHeaderDescription;

    /**
     * A menu displayed for options (e.g. "show all widgets" filter) around widget lists in the
     * picker.
     */
    protected View mWidgetOptionsMenu;
    /**
     * State of the options in the menu (if displayed to the user).
     */
    @Nullable
    protected WidgetOptionsMenuState mWidgetOptionsMenuState = null;

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
        if (!mDeviceProfile.getDeviceProperties().isTwoPanels()) {
            mWidgetRecommendationsView.enableFullPageViewIfLowDensity();
        }
        // To save the currently displayed page, so that, it can be requested when rebinding
        // recommendations with different size constraints.
        mWidgetRecommendationsView.addPageSwitchListener(
                newPage -> mRecommendationsCurrentPage = newPage);

        mHeaderTitle = mContent.findViewById(R.id.title);
        mHeaderDescription = mContent.findViewById(R.id.widget_picker_description);

        mWidgetOptionsMenu = mContent.findViewById(R.id.widget_picker_widget_options_menu);
        if (!enableTieredWidgetsByDefaultInPicker()) {
            setupWidgetOptionsMenu();
        }

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
    public void mayUpdateTitleAndDescription(@Nullable String title, @Nullable String description) {
        if (title != null) {
            mHeaderTitle.setText(title);
        }
        if (description != null) {
            mHeaderDescription.setText(description);
            mHeaderDescription.setVisibility(VISIBLE);
        }
    }

    protected void setupWidgetOptionsMenu() {
        mWidgetOptionsMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mWidgetOptionsMenuState != null) {
                    PopupMenu popupMenu = new PopupMenu(mActivityContext, /*anchor=*/ v,
                            Gravity.END);
                    MenuItem menuItem = popupMenu.getMenu().add(
                            R.string.widget_picker_show_all_widgets_menu_item_title);
                    menuItem.setCheckable(true);
                    menuItem.setChecked(mWidgetOptionsMenuState.showAllWidgets);
                    menuItem.setOnMenuItemClickListener(
                            item -> onShowAllWidgetsMenuItemClick(item));
                    popupMenu.show();
                }
            }
        });
    }

    private boolean onShowAllWidgetsMenuItemClick(MenuItem menuItem) {
        mWidgetOptionsMenuState.showAllWidgets = !mWidgetOptionsMenuState.showAllWidgets;
        menuItem.setChecked(mWidgetOptionsMenuState.showAllWidgets);

        // Refresh widgets
        onWidgetsBound();
        if (mIsInSearchMode) {
            mSearchBar.reset();
        } else if (!mSuggestedWidgetsPackageUserKey.equals(mSelectedHeader)) {
            mAdapters.get(mActivePage).mWidgetsListAdapter.selectFirstHeaderEntry();
            mAdapters.get(mActivePage).mWidgetsRecyclerView.scrollToTop();
        }
        return true;
    }

    @Override
    protected int getTabletHorizontalMargin(DeviceProfile deviceProfile) {
        // two pane picker is full width for fold as well as tablet.
        return getResources().getDimensionPixelSize(
                R.dimen.widget_picker_two_panels_left_right_margin);
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
        if (changed && mDeviceProfile.getDeviceProperties().isTwoPanels()) {
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

    // Used by the two pane sheet to show 3-dot menu to toggle between default lists and all lists
    // when enableTieredWidgetsByDefaultInPicker is OFF. This code path and the 3-dot menu can be
    // safely deleted when it's alternative "enableTieredWidgetsByDefaultInPicker" flag is inlined.
    @Override
    protected List<WidgetsListBaseEntry> getWidgetsToDisplay() {
        List<WidgetsListBaseEntry> allWidgets =
                mActivityContext.getWidgetPickerDataProvider().get().getAllWidgets();
        List<WidgetsListBaseEntry> defaultWidgets =
                mActivityContext.getWidgetPickerDataProvider().get().getDefaultWidgets();

        if (allWidgets.isEmpty() || defaultWidgets.isEmpty()) {
            // no menu if there are no default widgets to show
            mWidgetOptionsMenuState = null;
            mWidgetOptionsMenu.setVisibility(GONE);
        } else {
            if (mWidgetOptionsMenuState == null) {
                mWidgetOptionsMenuState = new WidgetOptionsMenuState();
            }

            mWidgetOptionsMenu.setVisibility(VISIBLE);
            return mWidgetOptionsMenuState.showAllWidgets ? allWidgets : defaultWidgets;
        }

        return allWidgets;
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
    public void onWidgetsListExpandButtonClick(View v) {
        super.onWidgetsListExpandButtonClick(v);
        // Refresh right pane with updated data for the selected header.
        if (mSelectedHeader != null && mSelectedHeader != mSuggestedWidgetsPackageUserKey) {
            getHeaderChangeListener().onHeaderChanged(mSelectedHeader);
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

        PackageItemInfo packageItemInfo = new HighresPackageItemInfo(
                /* packageName= */ SUGGESTIONS_PACKAGE_NAME,
                Process.myUserHandle());
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
                        /*items=*/ List.of(), // not necessary
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
            mSuggestedWidgetsPackageUserKey = PackageUserKey.fromPackageItemInfo(packageItemInfo);
            final boolean isChangingHeaders = mSelectedHeader == null
                    || !mSelectedHeader.equals(mSuggestedWidgetsPackageUserKey);
            if (isChangingHeaders)  {
                // If the initial focus view is still focused or widget picker is still opening, it
                // is likely a programmatic header click.
                if (mSelectedHeader != null && !mOpenCloseAnimation.getAnimationPlayer().isRunning()
                        && !getAccessibilityInitialFocusView().isAccessibilityFocused()) {
                    if (Utilities.ATLEAST_P) {
                        mRightPaneScrollView.setAccessibilityPaneTitle(suggestionsRightPaneTitle);
                    }
                    focusOnFirstWidgetCell(mWidgetRecommendationsView);
                }
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
    }

    @Override
    @Px
    protected float getMaxAvailableHeightForRecommendations() {
        if (mRecommendedWidgetsCount > 0) {
            // If widgets were already selected for display, we show them all on orientation change
            // in a two pane picker
            return Float.MAX_VALUE;
        }

        return (mDeviceProfile.getDeviceProperties().getHeightPx()
                - mDeviceProfile.getBottomSheetProfile().getBottomSheetTopPadding())
                * RECOMMENDATION_SECTION_HEIGHT_RATIO_TWO_PANE;
    }

    @Override
    @Px
    protected int getAvailableWidthForSuggestions(int pickerAvailableWidth) {
        int rightPaneWidth = (int) Math.ceil(0.67 * pickerAvailableWidth);

        if (mDeviceProfile.getDeviceProperties().isTwoPanels()) {
            // See onLayout
            int leftPaneWidth = (int) (0.33 * pickerAvailableWidth);
            @Px int minLeftPaneWidthPx = Utilities.dpToPx(MINIMUM_WIDTH_LEFT_PANE_FOLDABLE_DP);
            @Px int maxLeftPaneWidthPx = Utilities.dpToPx(MAXIMUM_WIDTH_LEFT_PANE_FOLDABLE_DP);
            if (leftPaneWidth < minLeftPaneWidthPx) {
                leftPaneWidth = minLeftPaneWidthPx;
            } else if (leftPaneWidth > maxLeftPaneWidthPx) {
                leftPaneWidth = maxLeftPaneWidthPx;
            }
            rightPaneWidth = pickerAvailableWidth - leftPaneWidth;
        }

        // Since suggestions are shown in right pane, the available width is 2/3 of total width of
        // bottom sheet.
        return rightPaneWidth - getResources().getDimensionPixelSize(
                R.dimen.widget_list_horizontal_margin_two_pane); // right pane end margin.
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        super.onActivePageChanged(currentActivePage);

        // If active page didn't change then we don't want to update the header.
        if (mActivePage == currentActivePage) {
            return;
        }

        mActivePage = currentActivePage;

        // When using talkback, swiping left while on right pane, should navigate to the widgets
        // list on left.
        mAdapters.get(mActivePage).mWidgetsRecyclerView.setAccessibilityTraversalBefore(
                mRightPaneScrollView.getId());

        // On page change, select the first item in the list to show in the right pane.
        mAdapters.get(currentActivePage).mWidgetsListAdapter.selectFirstHeaderEntry();
        mAdapters.get(currentActivePage).mWidgetsRecyclerView.scrollToTop();
    }

    @Override
    protected void updateRecyclerViewVisibility(AdapterHolder adapterHolder) {
        // The first item is always an empty space entry. Look for any more items.
        boolean isWidgetAvailable = adapterHolder.mWidgetsListAdapter.hasVisibleEntries();
        if (!isWidgetAvailable) {
            mRightPane.removeAllViews();
            mRightPane.addView(mNoWidgetsView);
            // with no widgets message, no header is selected on left
            if (mSuggestedWidgetsPackageUserKey != null
                    && mSuggestedWidgetsPackageUserKey.equals(mSelectedHeader)
                    && mSuggestedWidgetsHeader != null) {
                mSuggestedWidgetsHeader.setExpanded(false);
            }
            mSelectedHeader = null;
        }
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

    private HeaderChangeListener getHeaderChangeListener() {
        return new HeaderChangeListener() {
            @Override
            public void onHeaderChanged(@NonNull PackageUserKey selectedHeader) {
                final boolean isSameHeader = mSelectedHeader != null
                        && mSelectedHeader.equals(selectedHeader);
                // If the initial focus view is still focused or widget picker is still opening, it
                // is likely a programmatic header click.
                final boolean isUserClick = mSelectedHeader != null
                        && !mOpenCloseAnimation.getAnimationPlayer().isRunning()
                        && !getAccessibilityInitialFocusView().isAccessibilityFocused();
                mSelectedHeader = selectedHeader;

                WidgetsListContentEntry contentEntry;
                if (enableTieredWidgetsByDefaultInPicker()) {
                    contentEntry = mAdapters.get(
                            getCurrentAdapterHolderType()).mWidgetsListAdapter.getContentEntry(
                            selectedHeader);
                } else { // Can be deleted when inlining the "enableTieredWidgetsByDefaultInPicker"
                    // flag
                    final boolean showDefaultWidgets = mWidgetOptionsMenuState != null
                            && !mWidgetOptionsMenuState.showAllWidgets;
                    contentEntry = findContentEntryForPackageUser(
                            mActivityContext.getWidgetPickerDataProvider().get(),
                            selectedHeader, showDefaultWidgets);
                }

                if (contentEntry == null || mRightPane == null) {
                    return;
                }

                if (mSuggestedWidgetsHeader != null) {
                    mSuggestedWidgetsHeader.setExpanded(false);
                }

                WidgetsListContentEntry contentEntryToBind;
                // Setting max span size enables row to understand how to fit more than one item
                // in a row.
                contentEntryToBind = contentEntry.withMaxSpanSize(mMaxSpanPerRow);

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
                if (isUserClick) {
                    if (Utilities.ATLEAST_P) {
                        mRightPaneScrollView.setAccessibilityPaneTitle(getContext().getString(
                                R.string.widget_picker_right_pane_accessibility_title,
                                contentEntry.mPkgItem.title));
                    }
                    postDelayed(() -> focusOnFirstWidgetCell(widgetsRowViewHolder.tableContainer),
                            WIDGET_LIST_ITEM_APPEARANCE_DELAY);
                }
                mRightPaneScrollView.setScrollY(0);
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

    /**
     * Requests focus on the first widget cell in the given widget section.
     */
    private static void focusOnFirstWidgetCell(ViewGroup parent) {
        if (parent == null) return;
        WidgetCell cell = Utilities.findViewByPredicate(parent, v -> v instanceof WidgetCell);
        if (cell != null) {
            cell.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
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

    /**
     * Holds the selection state of the options menu (if presented to the user).
     */
    protected static class WidgetOptionsMenuState {
        /**
         * UI state indicating whether to show default or all widgets.
         * <p>If true, shows all widgets; else shows the default widgets.</p>
         */
        public boolean showAllWidgets = false;
    }

    private static class HighresPackageItemInfo extends PackageItemInfo {
        HighresPackageItemInfo(String packageName, UserHandle user) {
            super(packageName, user);
        }

        @Override
        public CacheLookupFlag getMatchingLookupFlag() {
            return DEFAULT_LOOKUP_FLAG;
        }
    }
}
