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
package com.android.launcher3.widget.picker;

import static com.android.launcher3.Flags.enableCategorizedWidgetSuggestions;
import static com.android.launcher3.Flags.enableUnfoldedTwoPanePicker;
import static com.android.launcher3.LauncherPrefs.WIDGETS_EDUCATION_DIALOG_SEEN;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.SEARCH;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGETSTRAY_SEARCHED;
import static com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL;

import android.animation.Animator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.model.UserManagerState;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.views.ArrowTipView;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.SpringRelativeLayout;
import com.android.launcher3.views.StickyHeaderLayout;
import com.android.launcher3.views.WidgetsEduView;
import com.android.launcher3.widget.BaseWidgetSheet;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.picker.search.SearchModeListener;
import com.android.launcher3.widget.picker.search.WidgetsSearchBar;
import com.android.launcher3.workprofile.PersonalWorkPagedView;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip.OnActivePageChangedListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Popup for showing the full list of available widgets
 */
public class WidgetsFullSheet extends BaseWidgetSheet
        implements OnActivePageChangedListener,
        WidgetsRecyclerView.HeaderViewDimensionsProvider, SearchModeListener {

    private static final long FADE_IN_DURATION = 150;
    private static final long EDUCATION_TIP_DELAY_MS = 200;
    private static final long EDUCATION_DIALOG_DELAY_MS = 500;

    // The widget recommendation table can easily take over the entire screen on devices with small
    // resolution or landscape on phone. This ratio defines the max percentage of content area that
    // the table can display with respect to bottom sheet's height.
    private static final float RECOMMENDATION_TABLE_HEIGHT_RATIO = 0.45f;
    private static final String RECOMMENDATIONS_SAVED_STATE_KEY =
            "widgetsFullSheet:mRecommendationsCurrentPage";
    private static final String SUPER_SAVED_STATE_KEY = "widgetsFullSheet:superHierarchyState";
    private final UserCache mUserCache;
    private final UserManagerState mUserManagerState = new UserManagerState();
    private final UserHandle mCurrentUser = Process.myUserHandle();
    private final Predicate<WidgetsListBaseEntry> mPrimaryWidgetsFilter =
            entry -> mCurrentUser.equals(entry.mPkgItem.user);
    private final Predicate<WidgetsListBaseEntry> mWorkWidgetsFilter;
    protected final boolean mHasWorkProfile;
    // Number of recommendations displayed
    protected int mRecommendedWidgetsCount;
    private List<WidgetItem> mRecommendedWidgets = new ArrayList<>();
    private Map<WidgetRecommendationCategory, List<WidgetItem>> mRecommendedWidgetsMap =
            new HashMap<>();
    protected int mRecommendationsCurrentPage = 0;
    protected final SparseArray<AdapterHolder> mAdapters = new SparseArray();
    @Nullable
    private ArrowTipView mLatestEducationalTip;
    private final OnLayoutChangeListener mLayoutChangeListenerToShowTips =
            new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (hasSeenEducationTip()) {
                        removeOnLayoutChangeListener(this);
                        return;
                    }

                    // Widgets are loaded asynchronously, We are adding a delay because we only want
                    // to show the tip when the widget preview has finished loading and rendering in
                    // this view.
                    removeCallbacks(mShowEducationTipTask);
                    postDelayed(mShowEducationTipTask, EDUCATION_TIP_DELAY_MS);
                }
            };

    private final Runnable mShowEducationTipTask = () -> {
        if (hasSeenEducationTip()) {
            removeOnLayoutChangeListener(mLayoutChangeListenerToShowTips);
            return;
        }
        mLatestEducationalTip = showEducationTipOnViewIfPossible(getViewToShowEducationTip());
        if (mLatestEducationalTip != null) {
            removeOnLayoutChangeListener(mLayoutChangeListenerToShowTips);
        }
    };

    private final OnAttachStateChangeListener mBindScrollbarInSearchMode =
            new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    WidgetsRecyclerView searchRecyclerView =
                            mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView;
                    if (mIsInSearchMode && searchRecyclerView != null) {
                        searchRecyclerView.bindFastScrollbar(mFastScroller);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                }
            };

    @Px
    private final int mTabsHeight;

    @Nullable
    private WidgetsRecyclerView mCurrentWidgetsRecyclerView;
    @Nullable
    private WidgetsRecyclerView mCurrentTouchEventRecyclerView;
    @Nullable
    PersonalWorkPagedView mViewPager;
    private boolean mIsInSearchMode;
    private boolean mIsNoWidgetsViewNeeded;
    @Px
    protected int mMaxSpanPerRow;
    protected DeviceProfile mDeviceProfile;

    protected TextView mNoWidgetsView;
    protected StickyHeaderLayout mSearchScrollView;
    protected WidgetRecommendationsView mWidgetRecommendationsView;
    protected LinearLayout mWidgetRecommendationsContainer;
    protected View mTabBar;
    protected View mSearchBarContainer;
    protected WidgetsSearchBar mSearchBar;
    protected TextView mHeaderTitle;
    protected RecyclerViewFastScroller mFastScroller;
    protected int mBottomPadding;

    public WidgetsFullSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDeviceProfile = mActivityContext.getDeviceProfile();
        mUserCache = UserCache.INSTANCE.get(context);
        mHasWorkProfile = mUserCache.getUserProfiles()
                .stream()
                .anyMatch(user -> mUserCache.getUserInfo(user).isWork());
        mWorkWidgetsFilter = entry -> mHasWorkProfile
                && mUserCache.getUserInfo(entry.mPkgItem.user).isWork()
                && !mUserManagerState.isUserQuiet(entry.mPkgItem.user);
        mAdapters.put(AdapterHolder.PRIMARY, new AdapterHolder(AdapterHolder.PRIMARY));
        mAdapters.put(AdapterHolder.WORK, new AdapterHolder(AdapterHolder.WORK));
        mAdapters.put(AdapterHolder.SEARCH, new AdapterHolder(AdapterHolder.SEARCH));

        Resources resources = getResources();
        mUserManagerState.init(UserCache.INSTANCE.get(context),
                context.getSystemService(UserManager.class));
        mTabsHeight = mHasWorkProfile
                ? resources.getDimensionPixelSize(R.dimen.all_apps_header_pill_height)
                : 0;
    }

    public WidgetsFullSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContent = findViewById(R.id.container);
        setContentBackgroundWithParent(getContext().getDrawable(R.drawable.bg_widgets_full_sheet),
                mContent);
        mContent.setOutlineProvider(mViewOutlineProvider);
        mContent.setClipToOutline(true);
        setupSheet();
    }

    protected void setupSheet() {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        int contentLayoutRes = mHasWorkProfile ? R.layout.widgets_full_sheet_paged_view
                : R.layout.widgets_full_sheet_recyclerview;
        layoutInflater.inflate(contentLayoutRes, mContent, true);

        setupViews();

        mWidgetRecommendationsContainer = mSearchScrollView.findViewById(
                R.id.widget_recommendations_container);
        mWidgetRecommendationsView = mSearchScrollView.findViewById(
                R.id.widget_recommendations_view);
        // To save the currently displayed page, so that, it can be requested when rebinding
        // recommendations with different size constraints.
        mWidgetRecommendationsView.addPageSwitchListener(
                newPage -> mRecommendationsCurrentPage = newPage);
        mWidgetRecommendationsView.initParentViews(mWidgetRecommendationsContainer);
        mWidgetRecommendationsView.setWidgetCellLongClickListener(this);
        mWidgetRecommendationsView.setWidgetCellOnClickListener(this);

        mHeaderTitle = mSearchScrollView.findViewById(R.id.title);

        onWidgetsBound();
        setUpEducationViewsIfNeeded();
    }

    protected void setupViews() {
        mSearchScrollView = findViewById(R.id.search_and_recommendations_container);
        mSearchScrollView.setCurrentRecyclerView(findViewById(R.id.primary_widgets_list_view));
        mNoWidgetsView = findViewById(R.id.no_widgets_text);
        mFastScroller = findViewById(R.id.fast_scroller);
        mFastScroller.setPopupView(findViewById(R.id.fast_scroller_popup));
        mAdapters.get(AdapterHolder.PRIMARY).setup(findViewById(R.id.primary_widgets_list_view));
        mAdapters.get(AdapterHolder.SEARCH).setup(findViewById(R.id.search_widgets_list_view));
        if (mHasWorkProfile) {
            mViewPager = findViewById(R.id.widgets_view_pager);
            mViewPager.setOutlineProvider(mViewOutlineProvider);
            mViewPager.setClipToOutline(true);
            mViewPager.setClipChildren(false);
            mViewPager.initParentViews(this);
            mViewPager.getPageIndicator().setOnActivePageChangedListener(this);
            mViewPager.getPageIndicator().setActiveMarker(AdapterHolder.PRIMARY);
            findViewById(R.id.tab_personal)
                    .setOnClickListener((View view) -> mViewPager.snapToPage(0));
            findViewById(R.id.tab_work)
                    .setOnClickListener((View view) -> mViewPager.snapToPage(1));
            mAdapters.get(AdapterHolder.WORK).setup(findViewById(R.id.work_widgets_list_view));
            setDeviceManagementResources();
        } else {
            mViewPager = null;
        }

        mTabBar = mSearchScrollView.findViewById(R.id.tabs);
        mSearchBarContainer = mSearchScrollView.findViewById(R.id.search_bar_container);
        mSearchBar = mSearchScrollView.findViewById(R.id.widgets_search_bar);

        mSearchBar.initialize(
                mActivityContext.getPopupDataProvider(), /* searchModeListener= */ this);
    }

    private void setDeviceManagementResources() {
        if (mActivityContext.getStringCache() != null) {
            Button personalTab = findViewById(R.id.tab_personal);
            personalTab.setText(mActivityContext.getStringCache().widgetsPersonalTab);

            Button workTab = findViewById(R.id.tab_work);
            workTab.setText(mActivityContext.getStringCache().widgetsWorkTab);
        }
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        AdapterHolder currentAdapterHolder = mAdapters.get(currentActivePage);
        WidgetsRecyclerView currentRecyclerView =
                mAdapters.get(currentActivePage).mWidgetsRecyclerView;

        updateRecyclerViewVisibility(currentAdapterHolder);
        attachScrollbarToRecyclerView(currentRecyclerView);
    }

    private void attachScrollbarToRecyclerView(WidgetsRecyclerView recyclerView) {
        recyclerView.bindFastScrollbar(mFastScroller);
        if (mCurrentWidgetsRecyclerView != recyclerView) {
            // Only reset the scroll position & expanded apps if the currently shown recycler view
            // has been updated.
            reset();
            resetExpandedHeaders();
            mCurrentWidgetsRecyclerView = recyclerView;
            mSearchScrollView.setCurrentRecyclerView(recyclerView);
        }
    }

    protected void updateRecyclerViewVisibility(AdapterHolder adapterHolder) {
        // The first item is always an empty space entry. Look for any more items.
        boolean isWidgetAvailable = adapterHolder.mWidgetsListAdapter.hasVisibleEntries();
        adapterHolder.mWidgetsRecyclerView.setVisibility(isWidgetAvailable ? VISIBLE : GONE);

        if (adapterHolder.mAdapterType == AdapterHolder.SEARCH) {
            mNoWidgetsView.setText(R.string.no_search_results);
        } else if (adapterHolder.mAdapterType == AdapterHolder.WORK
                && mUserCache.getUserProfiles().stream()
                .filter(userHandle -> mUserCache.getUserInfo(userHandle).isWork())
                .anyMatch(mUserManagerState::isUserQuiet)
                && mActivityContext.getStringCache() != null) {
            mNoWidgetsView.setText(mActivityContext.getStringCache().workProfilePausedTitle);
        } else {
            mNoWidgetsView.setText(R.string.no_widgets_available);
        }
        mNoWidgetsView.setVisibility(isWidgetAvailable ? GONE : VISIBLE);
    }

    private void reset() {
        mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView.scrollToTop();
        if (mHasWorkProfile) {
            mAdapters.get(AdapterHolder.WORK).mWidgetsRecyclerView.scrollToTop();
        }
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView.scrollToTop();
        mSearchScrollView.reset(/* animate= */ true);
    }

    @VisibleForTesting
    public WidgetsRecyclerView getRecyclerView() {
        if (mIsInSearchMode) {
            return mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView;
        }
        if (!mHasWorkProfile || mViewPager.getCurrentPage() == AdapterHolder.PRIMARY) {
            return mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView;
        }
        return mAdapters.get(AdapterHolder.WORK).mWidgetsRecyclerView;
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(getRecyclerView(), getContext().getString(
                mIsOpen ? R.string.widgets_list : R.string.widgets_list_closed));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        LauncherAppState.getInstance(mActivityContext).getModel()
                .refreshAndBindWidgetsAndShortcuts(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView
                .removeOnAttachStateChangeListener(mBindScrollbarInSearchMode);
        if (mHasWorkProfile) {
            mAdapters.get(AdapterHolder.WORK).mWidgetsRecyclerView
                    .removeOnAttachStateChangeListener(mBindScrollbarInSearchMode);
        }
    }

    @Override
    public void setInsets(Rect insets) {
        super.setInsets(insets);
        mBottomPadding = Math.max(insets.bottom, mNavBarScrimHeight);
        setBottomPadding(mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView, mBottomPadding);
        setBottomPadding(mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView, mBottomPadding);
        if (mHasWorkProfile) {
            setBottomPadding(mAdapters.get(AdapterHolder.WORK)
                    .mWidgetsRecyclerView, mBottomPadding);
        }
        ((MarginLayoutParams) mNoWidgetsView.getLayoutParams()).bottomMargin = mBottomPadding;

        if (mBottomPadding > 0) {
            setupNavBarColor();
        } else {
            clearNavBarColor();
        }

        requestLayout();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        WindowInsets w = super.onApplyWindowInsets(insets);
        if (mInsets.bottom != mNavBarScrimHeight) {
            setInsets(mInsets);
        }
        return w;
    }

    private void setBottomPadding(RecyclerView recyclerView, int bottomPadding) {
        recyclerView.setPadding(
                recyclerView.getPaddingLeft(),
                recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                bottomPadding);
    }

    @Override
    protected void onContentHorizontalMarginChanged(int contentHorizontalMarginInPx) {
        setContentViewChildHorizontalMargin(mSearchScrollView, contentHorizontalMarginInPx);
        if (mViewPager == null) {
            setContentViewChildHorizontalPadding(
                    mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView,
                    contentHorizontalMarginInPx);
        } else {
            setContentViewChildHorizontalPadding(
                    mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView,
                    contentHorizontalMarginInPx);
            setContentViewChildHorizontalPadding(
                    mAdapters.get(AdapterHolder.WORK).mWidgetsRecyclerView,
                    contentHorizontalMarginInPx);
        }
        setContentViewChildHorizontalPadding(
                mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView,
                contentHorizontalMarginInPx);
    }

    private static void setContentViewChildHorizontalMargin(View view, int horizontalMarginInPx) {
        ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        layoutParams.setMarginStart(horizontalMarginInPx);
        layoutParams.setMarginEnd(horizontalMarginInPx);
    }

    private static void setContentViewChildHorizontalPadding(View view, int horizontalPaddingInPx) {
        view.setPadding(horizontalPaddingInPx, view.getPaddingTop(), horizontalPaddingInPx,
                view.getPaddingBottom());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        doMeasure(widthMeasureSpec, heightMeasureSpec);

        if (updateMaxSpansPerRow()) {
            doMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /** Returns {@code true} if the max spans have been updated. */
    private boolean updateMaxSpansPerRow() {
        if (getMeasuredWidth() == 0) return false;

        @Px int maxHorizontalSpan = getContentView().getMeasuredWidth()
                - (2 * mContentHorizontalMargin);
        if (mMaxSpanPerRow != maxHorizontalSpan) {
            mMaxSpanPerRow = maxHorizontalSpan;
            mAdapters.get(AdapterHolder.PRIMARY).mWidgetsListAdapter.setMaxHorizontalSpansPxPerRow(
                    maxHorizontalSpan);
            mAdapters.get(AdapterHolder.SEARCH).mWidgetsListAdapter.setMaxHorizontalSpansPxPerRow(
                    maxHorizontalSpan);
            if (mHasWorkProfile) {
                mAdapters.get(AdapterHolder.WORK).mWidgetsListAdapter.setMaxHorizontalSpansPxPerRow(
                        maxHorizontalSpan);
            }
            onRecommendedWidgetsBound();
            return true;
        }
        return false;
    }

    protected View getContentView() {
        return mHasWorkProfile
                ? mViewPager
                : mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;

        // Content is laid out as center bottom aligned
        int contentWidth = mContent.getMeasuredWidth();
        int contentLeft = (width - contentWidth - mInsets.left - mInsets.right) / 2 + mInsets.left;
        mContent.layout(contentLeft, height - mContent.getMeasuredHeight(),
                contentLeft + contentWidth, height);

        setTranslationShift(mTranslationShift);
    }

    @Override
    public void onWidgetsBound() {
        if (mIsInSearchMode) {
            return;
        }
        List<WidgetsListBaseEntry> allWidgets =
                mActivityContext.getPopupDataProvider().getAllWidgets();

        AdapterHolder primaryUserAdapterHolder = mAdapters.get(AdapterHolder.PRIMARY);
        primaryUserAdapterHolder.mWidgetsListAdapter.setWidgets(allWidgets);

        if (mHasWorkProfile) {
            mViewPager.setVisibility(VISIBLE);
            mTabBar.setVisibility(VISIBLE);
            AdapterHolder workUserAdapterHolder = mAdapters.get(AdapterHolder.WORK);
            workUserAdapterHolder.mWidgetsListAdapter.setWidgets(allWidgets);
            onActivePageChanged(mViewPager.getCurrentPage());
        } else {
            onActivePageChanged(0);
        }
        // Update recommended widgets section so that it occupies appropriate space on screen to
        // leave enough space for presence/absence of mNoWidgetsView.
        boolean isNoWidgetsViewNeeded =
                !mAdapters.get(AdapterHolder.PRIMARY).mWidgetsListAdapter.hasVisibleEntries()
                        || (mHasWorkProfile && mAdapters.get(AdapterHolder.WORK)
                        .mWidgetsListAdapter.hasVisibleEntries());
        if (mIsNoWidgetsViewNeeded != isNoWidgetsViewNeeded) {
            mIsNoWidgetsViewNeeded = isNoWidgetsViewNeeded;
            onRecommendedWidgetsBound();
        }
    }

    @Override
    public void enterSearchMode(boolean shouldLog) {
        if (mIsInSearchMode) return;
        setViewVisibilityBasedOnSearch(/*isInSearchMode= */ true);
        attachScrollbarToRecyclerView(mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView);
        if (shouldLog) {
            mActivityContext.getStatsLogManager().logger().log(LAUNCHER_WIDGETSTRAY_SEARCHED);
        }
    }

    @Override
    public void exitSearchMode() {
        if (!mIsInSearchMode) return;
        onSearchResults(new ArrayList<>());
        WidgetsRecyclerView searchRecyclerView = mAdapters.get(
                AdapterHolder.SEARCH).mWidgetsRecyclerView;
        // Remove all views when exiting the search mode; this prevents animating from stale results
        // to new ones the next time we enter search mode. By the time recycler view is hidden,
        // layout may not have happened to clear up existing results. So, instead of waiting for it
        // to happen, we clear the views here.
        searchRecyclerView.swapAdapter(
                searchRecyclerView.getAdapter(), /*removeAndRecycleExistingViews=*/ true);
        setViewVisibilityBasedOnSearch(/*isInSearchMode=*/ false);
        if (mHasWorkProfile) {
            mViewPager.snapToPage(AdapterHolder.PRIMARY);
        }
        attachScrollbarToRecyclerView(mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView);
    }

    @Override
    public void onSearchResults(List<WidgetsListBaseEntry> entries) {
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsListAdapter.setWidgetsOnSearch(entries);
        updateRecyclerViewVisibility(mAdapters.get(AdapterHolder.SEARCH));
    }

    protected void setViewVisibilityBasedOnSearch(boolean isInSearchMode) {
        mIsInSearchMode = isInSearchMode;
        if (isInSearchMode) {
            mWidgetRecommendationsContainer.setVisibility(GONE);
            if (mHasWorkProfile) {
                mViewPager.setVisibility(GONE);
                mTabBar.setVisibility(GONE);
            } else {
                mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView.setVisibility(GONE);
            }
            updateRecyclerViewVisibility(mAdapters.get(AdapterHolder.SEARCH));
            // Hide no search results view to prevent it from flashing on enter search.
            mNoWidgetsView.setVisibility(GONE);
        } else {
            mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView.setVisibility(GONE);
            // Visibility of recommended widgets, recycler views and headers are handled in methods
            // below.
            onRecommendedWidgetsBound();
            onWidgetsBound();
        }
    }

    protected void resetExpandedHeaders() {
        mAdapters.get(AdapterHolder.PRIMARY).mWidgetsListAdapter.resetExpandedHeader();
        mAdapters.get(AdapterHolder.WORK).mWidgetsListAdapter.resetExpandedHeader();
    }

    @Override
    public void onRecommendedWidgetsBound() {
        if (mIsInSearchMode) {
            return;
        }

        if (enableCategorizedWidgetSuggestions()) {
            // We avoid applying new recommendations when some are already displayed.
            if (mRecommendedWidgetsMap.isEmpty()) {
                mRecommendedWidgetsMap =
                        mActivityContext.getPopupDataProvider().getCategorizedRecommendedWidgets();
            }
            mRecommendedWidgetsCount = mWidgetRecommendationsView.setRecommendations(
                    mRecommendedWidgetsMap,
                    mDeviceProfile,
                    /* availableHeight= */ getMaxAvailableHeightForRecommendations(),
                    /* availableWidth= */ mMaxSpanPerRow,
                    /* cellPadding= */ mWidgetCellHorizontalPadding,
                    /* requestedPage= */ mRecommendationsCurrentPage
            );
        } else {
            if (mRecommendedWidgets.isEmpty()) {
                mRecommendedWidgets =
                        mActivityContext.getPopupDataProvider().getRecommendedWidgets();
            }
            mRecommendedWidgetsCount = mWidgetRecommendationsView.setRecommendations(
                    mRecommendedWidgets,
                    mDeviceProfile,
                    /* availableHeight= */ getMaxAvailableHeightForRecommendations(),
                    /* availableWidth= */ mMaxSpanPerRow,
                    /* cellPadding= */ mWidgetCellHorizontalPadding
            );
        }
        mWidgetRecommendationsContainer.setVisibility(
                mRecommendedWidgetsCount > 0 ? VISIBLE : GONE);
    }

    @Px
    protected float getMaxAvailableHeightForRecommendations() {
        // There isn't enough space to show recommendations in landscape orientation on phones with
        // a full sheet design. Tablets use a two pane picker.
        if (mDeviceProfile.isLandscape) {
            return 0f;
        }

        return (mDeviceProfile.heightPx - mDeviceProfile.bottomSheetTopPadding)
                * RECOMMENDATION_TABLE_HEIGHT_RATIO;
    }

    /** b/209579563: "Widgets" header should be focused first. */
    @Override
    protected View getAccessibilityInitialFocusView() {
        return mHeaderTitle;
    }

    private void open(boolean animate) {
        if (animate) {
            if (getPopupContainer().getInsets().bottom > 0) {
                mContent.setAlpha(0);
            }
            setUpOpenAnimation(mActivityContext.getDeviceProfile().bottomSheetOpenDuration);
            Animator animator = mOpenCloseAnimation.getAnimationPlayer();
            animator.setInterpolator(AnimationUtils.loadInterpolator(
                    getContext(), android.R.interpolator.linear_out_slow_in));
            post(() -> {
                animator.setDuration(mActivityContext.getDeviceProfile().bottomSheetOpenDuration)
                        .start();
                mContent.animate().alpha(1).setDuration(FADE_IN_DURATION);
            });
        } else {
            setTranslationShift(TRANSLATION_SHIFT_OPENED);
            post(this::announceAccessibilityChanges);
        }
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, mActivityContext.getDeviceProfile().bottomSheetCloseDuration);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_WIDGETS_FULL_SHEET) != 0;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = shouldScroll(ev);
            if (mSearchBar.isSearchBarFocused()
                    && !getPopupContainer().isEventOverView(mSearchBarContainer, ev)) {
                mSearchBar.clearSearchBarFocus();
            }
        }

        return super.onControllerInterceptTouchEvent(ev);
    }

    protected boolean shouldScroll(MotionEvent ev) {
        boolean intercept = false;
        WidgetsRecyclerView recyclerView = getRecyclerView();
        RecyclerViewFastScroller scroller = recyclerView.getScrollbar();
        // Disable swipe down when recycler view is scrolling
        if (scroller.getThumbOffsetY() >= 0 && getPopupContainer().isEventOverView(scroller, ev)) {
            intercept = true;
        } else if (getPopupContainer().isEventOverView(recyclerView, ev)) {
            intercept = !recyclerView.shouldContainerScroll(ev, getPopupContainer());
        }
        return intercept;
    }

    /** Shows the {@link WidgetsFullSheet} on the launcher. */
    public static WidgetsFullSheet show(BaseActivity activity, boolean animate) {
        WidgetsFullSheet sheet = (WidgetsFullSheet) activity.getLayoutInflater().inflate(
                getWidgetSheetId(activity),
                activity.getDragLayer(),
                false);
        sheet.attachToContainer();
        sheet.mIsOpen = true;
        sheet.open(animate);
        return sheet;
    }

    @Override
    public void saveHierarchyState(SparseArray<Parcelable> sparseArray) {
        Bundle bundle = new Bundle();
        // With widget picker open, when we open shade to switch theme, Launcher re-creates the
        // picker and calls save/restore hierarchy state. We save the state of recommendations
        // across those updates.
        bundle.putInt(RECOMMENDATIONS_SAVED_STATE_KEY, mRecommendationsCurrentPage);
        mWidgetRecommendationsView.saveState(bundle);
        SparseArray<Parcelable> superState = new SparseArray<>();
        super.saveHierarchyState(superState);
        bundle.putSparseParcelableArray(SUPER_SAVED_STATE_KEY, superState);
        sparseArray.put(0, bundle);
    }

    @Override
    public void restoreHierarchyState(SparseArray<Parcelable> sparseArray) {
        Bundle state = (Bundle) sparseArray.get(0);
        mRecommendationsCurrentPage = state.getInt(
                RECOMMENDATIONS_SAVED_STATE_KEY, /*defaultValue=*/0);
        mWidgetRecommendationsView.restoreState(state);
        super.restoreHierarchyState(state.getSparseParcelableArray(SUPER_SAVED_STATE_KEY));
    }

    private static int getWidgetSheetId(BaseActivity activity) {
        boolean isTwoPane = (activity.getDeviceProfile().isTablet
                // Enables two pane picker for tablets in all orientations when the
                // enableCategorizedWidgetSuggestions flag is on.
                && (activity.getDeviceProfile().isLandscape || enableCategorizedWidgetSuggestions())
                && !activity.getDeviceProfile().isTwoPanels)
                // Enables two pane picker for unfolded foldables if the flag is on.
                || (activity.getDeviceProfile().isTwoPanels && enableUnfoldedTwoPanePicker());

        return isTwoPane ? R.layout.widgets_two_pane_sheet : R.layout.widgets_full_sheet;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return isTouchOnScrollbar(ev) || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return maybeHandleTouchEvent(ev) || super.onTouchEvent(ev);
    }

    private boolean maybeHandleTouchEvent(MotionEvent ev) {
        boolean isEventHandled = false;

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mCurrentTouchEventRecyclerView = isTouchOnScrollbar(ev) ? getRecyclerView() : null;
        }

        if (mCurrentTouchEventRecyclerView != null) {
            final float offsetX = mContent.getX();
            final float offsetY = mContent.getY();
            ev.offsetLocation(-offsetX, -offsetY);
            isEventHandled = mCurrentTouchEventRecyclerView.dispatchTouchEvent(ev);
            ev.offsetLocation(offsetX, offsetY);
        }

        if (ev.getAction() == MotionEvent.ACTION_UP
                || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            mCurrentTouchEventRecyclerView = null;
        }

        return isEventHandled;
    }

    private boolean isTouchOnScrollbar(MotionEvent ev) {
        final float offsetX = mContent.getX();
        final float offsetY = mContent.getY();
        WidgetsRecyclerView rv = getRecyclerView();

        ev.offsetLocation(-offsetX, -offsetY);
        boolean isOnScrollBar = rv != null && rv.getScrollbar() != null && rv.isHitOnScrollBar(ev);
        ev.offsetLocation(offsetX, offsetY);

        return isOnScrollBar;
    }

    /** Gets the {@link WidgetsRecyclerView} which shows all widgets in {@link WidgetsFullSheet}. */
    @VisibleForTesting
    public static WidgetsRecyclerView getWidgetsView(BaseActivity launcher) {
        return launcher.findViewById(R.id.primary_widgets_list_view);
    }

    @Override
    public void addHintCloseAnim(
            float distanceToMove, Interpolator interpolator, PendingAnimation target) {
        target.addAnimatedFloat(mSwipeToDismissProgress, 0f, 1f, interpolator);
    }

    @Override
    protected void onCloseComplete() {
        super.onCloseComplete();
        removeCallbacks(mShowEducationTipTask);
        if (mLatestEducationalTip != null) {
            mLatestEducationalTip.close(true);
        }
        AccessibilityManagerCompat.sendStateEventToTest(getContext(), NORMAL_STATE_ORDINAL);
    }

    @Override
    public int getHeaderViewHeight() {
        return measureHeightWithVerticalMargins(mHeaderTitle)
                + measureHeightWithVerticalMargins(mSearchBarContainer);
    }

    /** private the height, in pixel, + the vertical margins of a given view. */
    protected static int measureHeightWithVerticalMargins(View view) {
        if (view == null || view.getVisibility() != VISIBLE) {
            return 0;
        }
        MarginLayoutParams marginLayoutParams = (MarginLayoutParams) view.getLayoutParams();
        return view.getMeasuredHeight() + marginLayoutParams.bottomMargin
                + marginLayoutParams.topMargin;
    }

    private int getCurrentAdapterHolderType() {
        if (mIsInSearchMode) {
            return SEARCH;
        } else if (mViewPager != null) {
            return mViewPager.getCurrentPage();
        } else {
            return AdapterHolder.PRIMARY;
        }
    }

    private void restorePreviousAdapterHolderType(int previousAdapterHolderType) {
        if (previousAdapterHolderType == AdapterHolder.WORK && mViewPager != null) {
            mViewPager.setCurrentPage(previousAdapterHolderType);
        } else if (previousAdapterHolderType == AdapterHolder.SEARCH) {
            enterSearchMode(false);
        }
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        super.onDeviceProfileChanged(dp);

        if (shouldRecreateLayout(/*oldDp=*/ mDeviceProfile, /*newDp=*/ dp)) {
            SparseArray<Parcelable> widgetsState = new SparseArray<>();
            saveHierarchyState(widgetsState);
            handleClose(false);
            WidgetsFullSheet sheet = show(BaseActivity.fromContext(getContext()), false);
            sheet.restoreRecommendations(mRecommendedWidgets, mRecommendedWidgetsMap);
            sheet.restoreHierarchyState(widgetsState);
            sheet.restorePreviousAdapterHolderType(getCurrentAdapterHolderType());
        } else if (!isTwoPane()) {
            reset();
            resetExpandedHeaders();
        }

        mDeviceProfile = dp;
    }

    private void restoreRecommendations(List<WidgetItem> recommendedWidgets,
            Map<WidgetRecommendationCategory, List<WidgetItem>> recommendedWidgetsMap) {
        mRecommendedWidgets = recommendedWidgets;
        mRecommendedWidgetsMap = recommendedWidgetsMap;
    }

    /**
     * Indicates if layout should be re-created on device profile change - so that a different
     * layout can be displayed.
     */
    private static boolean shouldRecreateLayout(DeviceProfile oldDp, DeviceProfile newDp) {
        // When folding/unfolding the foldables, we need to switch between the regular widget picker
        // and the two pane picker, so we rebuild the picker with the correct layout.
        boolean isFoldUnFold =
                oldDp.isTwoPanels != newDp.isTwoPanels && enableUnfoldedTwoPanePicker();
        // In tablets, on orientation change we switch between single and two pane picker unless the
        // categorized suggestions flag was on. With the categorized suggestions feature, we use a
        // two pane picker across all orientations.
        boolean useDifferentLayoutOnOrientationChange =
                (!enableCategorizedWidgetSuggestions() && (newDp.isTablet && !newDp.isTwoPanels
                        && oldDp.isLandscape != newDp.isLandscape));

        return isFoldUnFold || useDifferentLayoutOnOrientationChange;
    }

    /**
     * In widget search mode, we should scale down content inside widget bottom sheet, rather
     * than the whole bottom sheet, to indicate we will navigate back within the widget
     * bottom sheet.
     */
    @Override
    public boolean shouldAnimateContentViewInBackSwipe() {
        return mIsInSearchMode;
    }

    @Override
    public void onBackInvoked() {
        if (mIsInSearchMode) {
            mSearchBar.reset();
            // Posting animation to next frame will let widget sheet finish updating UI first, and
            // make animation smoother.
            post(this::animateSwipeToDismissProgressToStart);
        } else {
            super.onBackInvoked();
        }
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        super.onDragStart(start, startDisplacement);
        WindowInsetsController insetsController = getWindowInsetsController();
        if (insetsController != null) {
            insetsController.hide(WindowInsets.Type.ime());
        }
    }

    @Nullable
    private View getViewToShowEducationTip() {
        if (mWidgetRecommendationsContainer.getVisibility() == VISIBLE) {
            return mWidgetRecommendationsView.getViewForEducationTip();
        }

        AdapterHolder adapterHolder = mAdapters.get(mIsInSearchMode
                ? AdapterHolder.SEARCH
                : mViewPager == null
                        ? AdapterHolder.PRIMARY
                        : mViewPager.getCurrentPage());
        WidgetsRowViewHolder viewHolderForTip =
                (WidgetsRowViewHolder) IntStream.range(
                                0, adapterHolder.mWidgetsListAdapter.getItemCount())
                        .mapToObj(adapterHolder.mWidgetsRecyclerView::
                                findViewHolderForAdapterPosition)
                        .filter(viewHolder -> viewHolder instanceof WidgetsRowViewHolder)
                        .findFirst()
                        .orElse(null);
        if (viewHolderForTip != null) {
            return ((ViewGroup) viewHolderForTip.tableContainer.getChildAt(0)).getChildAt(0);
        }

        return null;
    }

    /** Shows education dialog for widgets. */
    private WidgetsEduView showEducationDialog() {
        LauncherPrefs.get(getContext()).put(WIDGETS_EDUCATION_DIALOG_SEEN, true);
        return WidgetsEduView.showEducationDialog(mActivityContext);
    }

    /** Returns {@code true} if education dialog has previously been shown. */
    protected boolean hasSeenEducationDialog() {
        return LauncherPrefs.get(getContext()).get(WIDGETS_EDUCATION_DIALOG_SEEN)
                || Utilities.isRunningInTestHarness();
    }

    protected void setUpEducationViewsIfNeeded() {
        if (!hasSeenEducationDialog()) {
            postDelayed(() -> {
                WidgetsEduView eduDialog = showEducationDialog();
                eduDialog.addOnCloseListener(() -> {
                    if (!hasSeenEducationTip()) {
                        addOnLayoutChangeListener(mLayoutChangeListenerToShowTips);
                        // Call #requestLayout() to trigger layout change listener in order to show
                        // arrow tip immediately if there is a widget to show it on.
                        requestLayout();
                    }
                });
            }, EDUCATION_DIALOG_DELAY_MS);
        } else if (!hasSeenEducationTip()) {
            addOnLayoutChangeListener(mLayoutChangeListenerToShowTips);
        }
    }

    protected boolean isTwoPane() {
        return false;
    }

    /** Gets the sheet for widget picker, which is used for testing. */
    @VisibleForTesting
    public View getSheet() {
        return mContent;
    }

    /** Opens the first header in widget picker and scrolls to the top of the RecyclerView. */
    @VisibleForTesting
    public void openFirstHeader() {
        mAdapters.get(AdapterHolder.PRIMARY).mWidgetsListAdapter.selectFirstHeaderEntry();
        mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView.scrollToTop();
    }

    @Override
    protected int getHeaderTopClip(@NonNull WidgetCell cell) {
        StickyHeaderLayout header = findViewById(R.id.search_and_recommendations_container);
        if (header == null) {
            return 0;
        }
        Rect cellRect = new Rect();
        boolean cellIsPartiallyVisible = cell.getGlobalVisibleRect(cellRect);
        if (cellIsPartiallyVisible) {
            Rect occludingRect = new Rect();
            for (View headerChild : header.getStickyChildren()) {
                Rect childRect = new Rect();
                boolean childVisible = headerChild.getGlobalVisibleRect(childRect);
                if (childVisible && childRect.intersect(cellRect)) {
                    occludingRect.union(childRect);
                }
            }
            if (!occludingRect.isEmpty() && cellRect.top < occludingRect.bottom) {
                return occludingRect.bottom - cellRect.top;
            }
        }
        return 0;
    }

    @Override
    protected void scrollCellContainerByY(WidgetCell wc, int scrollByY) {
        for (ViewParent parent = wc.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof WidgetsRecyclerView recyclerView) {
                // Scrollable container for main widget list.
                recyclerView.smoothScrollBy(0, scrollByY);
                return;
            } else if (parent instanceof StickyHeaderLayout header) {
                // Scrollable container for recommendations. We still scroll on the recycler (even
                // though the recommendations are not in the recycler view) because the
                // StickyHeaderLayout scroll is connected to the currently visible recycler view.
                WidgetsRecyclerView recyclerView = findVisibleRecyclerView();
                if (recyclerView != null) {
                    recyclerView.smoothScrollBy(0, scrollByY);
                }
                return;
            } else if (parent == this) {
                return;
            }
        }
    }

    @Nullable
    private WidgetsRecyclerView findVisibleRecyclerView() {
        if (mViewPager != null) {
            return (WidgetsRecyclerView) mViewPager.getPageAt(mViewPager.getCurrentPage());
        }
        return findViewById(R.id.primary_widgets_list_view);
    }

    /** A holder class for holding adapters & their corresponding recycler view. */
    final class AdapterHolder {
        static final int PRIMARY = 0;
        static final int WORK = 1;
        static final int SEARCH = 2;

        private final int mAdapterType;
        final WidgetsListAdapter mWidgetsListAdapter;
        private final DefaultItemAnimator mWidgetsListItemAnimator;

        WidgetsRecyclerView mWidgetsRecyclerView;

        AdapterHolder(int adapterType) {
            mAdapterType = adapterType;
            Context context = getContext();

            mWidgetsListAdapter = new WidgetsListAdapter(
                    context,
                    LayoutInflater.from(context),
                    this::getEmptySpaceHeight,
                    /* iconClickListener= */ WidgetsFullSheet.this,
                    /* iconLongClickListener= */ WidgetsFullSheet.this,
                    isTwoPane());
            mWidgetsListAdapter.setHasStableIds(true);
            switch (mAdapterType) {
                case PRIMARY:
                    mWidgetsListAdapter.setFilter(mPrimaryWidgetsFilter);
                    break;
                case WORK:
                    mWidgetsListAdapter.setFilter(mWorkWidgetsFilter);
                    break;
                default:
                    break;
            }
            mWidgetsListItemAnimator = new DefaultItemAnimator();
            // Disable change animations because it disrupts the item focus upon adapter item
            // change.
            mWidgetsListItemAnimator.setSupportsChangeAnimations(false);
        }

        private int getEmptySpaceHeight() {
            return mSearchScrollView.getHeaderHeight();
        }

        void setup(WidgetsRecyclerView recyclerView) {
            mWidgetsRecyclerView = recyclerView;
            mWidgetsRecyclerView.setOutlineProvider(mViewOutlineProvider);
            mWidgetsRecyclerView.setClipToOutline(true);
            mWidgetsRecyclerView.setClipChildren(false);
            mWidgetsRecyclerView.setAdapter(mWidgetsListAdapter);
            mWidgetsRecyclerView.bindFastScrollbar(mFastScroller);
            mWidgetsRecyclerView.setItemAnimator(mWidgetsListItemAnimator);
            mWidgetsRecyclerView.setHeaderViewDimensionsProvider(WidgetsFullSheet.this);
            if (!isTwoPane()) {
                mWidgetsRecyclerView.setEdgeEffectFactory(
                        ((SpringRelativeLayout) mContent).createEdgeEffectFactory());
            }
            // Recycler view binds to fast scroller when it is attached to screen. Make sure
            // search recycler view is bound to fast scroller if user is in search mode at the time
            // of attachment.
            if (mAdapterType == PRIMARY || mAdapterType == WORK) {
                mWidgetsRecyclerView.addOnAttachStateChangeListener(mBindScrollbarInSearchMode);
            }
            mWidgetsListAdapter.setMaxHorizontalSpansPxPerRow(mMaxSpanPerRow);
        }
    }
}
