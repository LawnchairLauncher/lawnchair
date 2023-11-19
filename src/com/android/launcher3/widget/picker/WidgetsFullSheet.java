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

import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.config.FeatureFlags.LARGE_SCREEN_WIDGET_PICKER;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGETSTRAY_SEARCHED;
import static com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
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
import android.view.Window;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.model.UserManagerState;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.recyclerview.ViewHolderBinder;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.views.ArrowTipView;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.SpringRelativeLayout;
import com.android.launcher3.views.StickyHeaderLayout;
import com.android.launcher3.views.WidgetsEduView;
import com.android.launcher3.widget.BaseWidgetSheet;
import com.android.launcher3.widget.LauncherWidgetHolder.ProviderChangedListener;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.picker.search.SearchModeListener;
import com.android.launcher3.widget.picker.search.WidgetsSearchBar;
import com.android.launcher3.widget.util.WidgetsTableUtils;
import com.android.launcher3.workprofile.PersonalWorkPagedView;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip.OnActivePageChangedListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import app.lawnchair.theme.color.ColorTokens;
import app.lawnchair.theme.drawable.DrawableTokens;

/**
 * Popup for showing the full list of available widgets
 */
public class WidgetsFullSheet extends BaseWidgetSheet
        implements ProviderChangedListener, OnActivePageChangedListener,
        WidgetsRecyclerView.HeaderViewDimensionsProvider, SearchModeListener {

    private static final long FADE_IN_DURATION = 150;
    private static final long EDUCATION_TIP_DELAY_MS = 200;
    private static final long EDUCATION_DIALOG_DELAY_MS = 500;
    private static final float VERTICAL_START_POSITION = 0.3f;
    private static final int PERSONAL_TAB = 0;
    private static final int WORK_TAB = 1;
    private static final String SUGGESTIONS_PACKAGE_NAME = "widgets_list_suggestions_entry";
    // The widget recommendation table can easily take over the entire screen on
    // devices with small
    // resolution or landscape on phone. This ratio defines the max percentage of
    // content area that
    // the table can display.
    private static final float RECOMMENDATION_TABLE_HEIGHT_RATIO = 0.75f;

    private static final String KEY_WIDGETS_EDUCATION_DIALOG_SEEN = "launcher.widgets_education_dialog_seen";

    private final UserManagerState mUserManagerState = new UserManagerState();

    private final boolean mHasWorkProfile;
    private final SparseArray<AdapterHolder> mAdapters = new SparseArray();
    private final UserHandle mCurrentUser = Process.myUserHandle();
    private final Predicate<WidgetsListBaseEntry> mPrimaryWidgetsFilter = entry -> mCurrentUser
            .equals(entry.mPkgItem.user);
    private final Predicate<WidgetsListBaseEntry> mWorkWidgetsFilter = entry -> !mCurrentUser
            .equals(entry.mPkgItem.user)
            && !mUserManagerState.isUserQuiet(entry.mPkgItem.user);
    @Nullable
    private ArrowTipView mLatestEducationalTip;
    private final OnLayoutChangeListener mLayoutChangeListenerToShowTips = new OnLayoutChangeListener() {
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

    private final OnAttachStateChangeListener mBindScrollbarInSearchMode = new OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View view) {
            WidgetsRecyclerView searchRecyclerView = mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView;
            if (mIsInSearchMode && searchRecyclerView != null) {
                searchRecyclerView.bindFastScrollbar(mFastScroller);
            }
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
        }
    };

    private final ViewOutlineProvider mViewOutlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRect(
                    0,
                    0,
                    view.getMeasuredWidth(),
                    view.getMeasuredHeight() + getBottomOffsetPx());
        }
    };

    @Px
    private final int mTabsHeight;

    @Nullable
    private WidgetsRecyclerView mCurrentWidgetsRecyclerView;
    @Nullable
    private PersonalWorkPagedView mViewPager;
    private boolean mIsInSearchMode;
    private boolean mIsNoWidgetsViewNeeded;
    @Px
    private int mMaxSpanPerRow;
    private TextView mNoWidgetsView;

    private StickyHeaderLayout mSearchScrollView;
    private WidgetsRecommendationTableLayout mRecommendedWidgetsTable;
    private LinearLayout mSuggestedWidgetsContainer;
    private WidgetsListHeader mSuggestedWidgetsHeader;
    private View mTabBar;
    private View mSearchBarContainer;
    private WidgetsSearchBar mSearchBar;
    private TextView mHeaderTitle;
    private FrameLayout mRightPane;
    private WidgetsListTableViewHolderBinder mWidgetsListTableViewHolderBinder;
    private DeviceProfile mDeviceProfile;
    private final boolean mIsTwoPane;

    private int mOrientation;
    private @Nullable WidgetsRecyclerView mCurrentTouchEventRecyclerView;

    private RecyclerViewFastScroller mFastScroller;

    public WidgetsFullSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDeviceProfile = Launcher.getLauncher(context).getDeviceProfile();
        mIsTwoPane = mDeviceProfile.isTablet
                && mDeviceProfile.isLandscape
                && LARGE_SCREEN_WIDGET_PICKER.get();
        mHasWorkProfile = context.getSystemService(LauncherApps.class).getProfiles().size() > 1;
        mAdapters.put(AdapterHolder.PRIMARY, new AdapterHolder(AdapterHolder.PRIMARY));
        mAdapters.put(AdapterHolder.WORK, new AdapterHolder(AdapterHolder.WORK));
        mAdapters.put(AdapterHolder.SEARCH, new AdapterHolder(AdapterHolder.SEARCH));

        Resources resources = getResources();
        mTabsHeight = mHasWorkProfile
                ? resources.getDimensionPixelSize(R.dimen.all_apps_header_pill_height)
                : 0;

        mUserManagerState.init(UserCache.INSTANCE.get(context),
                context.getSystemService(UserManager.class));
        setContentBackground(getContext().getDrawable(R.drawable.bg_widgets_full_sheet));
    }

    public WidgetsFullSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.container);
        mContent.setBackground(DrawableTokens.BgWidgetsFullSheet.resolve(getContext()));

        View collapseHandle = findViewById(R.id.collapse_handle);
        collapseHandle.setBackgroundColor(ColorTokens.TextColorSecondary.resolveColor(getContext()));

        mContent.setOutlineProvider(mViewOutlineProvider);
        mContent.setClipToOutline(true);

        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        int contentLayoutRes = mHasWorkProfile ? R.layout.widgets_full_sheet_paged_view
                : R.layout.widgets_full_sheet_recyclerview;
        if (mIsTwoPane) {
            contentLayoutRes = mHasWorkProfile ? R.layout.widgets_full_sheet_paged_view_large_screen
                    : R.layout.widgets_full_sheet_recyclerview_large_screen;
        }
        layoutInflater.inflate(contentLayoutRes, mContent, true);

        View searchBarContainer = findViewById(R.id.search_bar_container);
        searchBarContainer.setBackgroundColor(ColorTokens.ColorBackground.resolveColor(getContext()));
        mFastScroller = findViewById(R.id.fast_scroller);
        if (mIsTwoPane) {
            mFastScroller.setVisibility(GONE);
        }
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

        mNoWidgetsView = findViewById(R.id.no_widgets_text);

        mSearchScrollView = findViewById(R.id.search_and_recommendations_container);
        mSearchScrollView.setCurrentRecyclerView(findViewById(R.id.primary_widgets_list_view));

        mRecommendedWidgetsTable = mIsTwoPane
                ? mContent.findViewById(R.id.recommended_widget_table)
                : mSearchScrollView.findViewById(R.id.recommended_widget_table);

        mRecommendedWidgetsTable.setWidgetCellLongClickListener(this);
        mRecommendedWidgetsTable.setWidgetCellOnClickListener(this);

        // Add suggested widgets.
        if (mIsTwoPane) {
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
            });
            mSuggestedWidgetsContainer.addView(mSuggestedWidgetsHeader);
        }

        mTabBar = mSearchScrollView.findViewById(R.id.tabs);
        mSearchBarContainer = mSearchScrollView.findViewById(R.id.search_bar_container);
        mSearchBar = mSearchScrollView.findViewById(R.id.widgets_search_bar);
        mHeaderTitle = mIsTwoPane
                ? mContent.findViewById(R.id.title)
                : mSearchScrollView.findViewById(R.id.title);
        mRightPane = mIsTwoPane ? mContent.findViewById(R.id.right_pane) : null;
        mWidgetsListTableViewHolderBinder = new WidgetsListTableViewHolderBinder(mActivityContext, layoutInflater, this,
                this);
        onRecommendedWidgetsBound();
        onWidgetsBound();

        mSearchBar.initialize(
                mActivityContext.getPopupDataProvider(), /* searchModeListener= */ this);

        setUpEducationViewsIfNeeded();
    }

    private void setDeviceManagementResources() {
        Button personalTab = findViewById(R.id.tab_personal);
        personalTab.setText(R.string.all_apps_personal_tab);

        Button workTab = findViewById(R.id.tab_work);
        workTab.setText(R.string.all_apps_work_tab);
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {

        // if the current active page changes to personal or work we set suggestions
        // to be the selected widget
        if (mIsTwoPane && (currentActivePage == PERSONAL_TAB || currentActivePage == WORK_TAB)) {
            mSuggestedWidgetsHeader.callOnClick();
        }

        AdapterHolder currentAdapterHolder = mAdapters.get(currentActivePage);
        WidgetsRecyclerView currentRecyclerView = mAdapters.get(currentActivePage).mWidgetsRecyclerView;

        updateRecyclerViewVisibility(currentAdapterHolder);
        attachScrollbarToRecyclerView(currentRecyclerView);
    }

    @Override
    public void onBackProgressed(@FloatRange(from = 0.0, to = 1.0) float progress) {
        super.onBackProgressed(progress);
        mFastScroller.setVisibility(progress > 0 ? View.INVISIBLE : View.VISIBLE);
    }

    private void attachScrollbarToRecyclerView(WidgetsRecyclerView recyclerView) {
        recyclerView.bindFastScrollbar(mFastScroller);
        if (mCurrentWidgetsRecyclerView != recyclerView) {
            // Only reset the scroll position & expanded apps if the currently shown
            // recycler view
            // has been updated.
            reset();
            resetExpandedHeaders();
            mCurrentWidgetsRecyclerView = recyclerView;
            mSearchScrollView.setCurrentRecyclerView(recyclerView);
        }
    }

    private void updateRecyclerViewVisibility(AdapterHolder adapterHolder) {
        // The first item is always an empty space entry. Look for any more items.
        boolean isWidgetAvailable = adapterHolder.mWidgetsListAdapter.hasVisibleEntries();

        if (mIsTwoPane) {
            mRightPane.setVisibility(isWidgetAvailable ? VISIBLE : GONE);
        }

        adapterHolder.mWidgetsRecyclerView.setVisibility(isWidgetAvailable ? VISIBLE : GONE);

        if (adapterHolder.mAdapterType == AdapterHolder.SEARCH) {
            mNoWidgetsView.setText(R.string.no_search_results);
        } else if (adapterHolder.mAdapterType == AdapterHolder.WORK
                && mUserManagerState.isAnyProfileQuietModeEnabled()
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
        mActivityContext.getAppWidgetHolder().addProviderChangeListener(this);
        notifyWidgetProvidersChanged();
        onRecommendedWidgetsBound();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.getAppWidgetHolder().removeProviderChangeListener(this);
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
        int bottomPadding = Math.max(insets.bottom, mNavBarScrimHeight);
        setBottomPadding(mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView, bottomPadding);
        setBottomPadding(mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView, bottomPadding);
        if (mHasWorkProfile) {
            setBottomPadding(mAdapters.get(AdapterHolder.WORK).mWidgetsRecyclerView, bottomPadding);
        }
        ((MarginLayoutParams) mNoWidgetsView.getLayoutParams()).bottomMargin = bottomPadding;

        if (bottomPadding > 0) {
            setupNavBarColor();
        } else {
            clearNavBarColor();
        }

        requestLayout();
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
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
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
        if (getMeasuredWidth() == 0)
            return false;

        View content = mHasWorkProfile
                ? mViewPager
                : mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView;
        if (mIsTwoPane && mRightPane != null) {
            content = mRightPane;
        }

        @Px
        int maxHorizontalSpan = content.getMeasuredWidth() - (2 * mContentHorizontalMargin);
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
    public void notifyWidgetProvidersChanged() {
        mActivityContext.refreshAndBindWidgetsForPackageUser(null);
    }

    @Override
    public void onWidgetsBound() {
        if (mIsInSearchMode) {
            return;
        }
        List<WidgetsListBaseEntry> allWidgets = mActivityContext.getPopupDataProvider().getAllWidgets();

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
        // Update recommended widgets section so that it occupies appropriate space on
        // screen to
        // leave enough space for presence/absence of mNoWidgetsView.
        boolean isNoWidgetsViewNeeded = !mAdapters.get(AdapterHolder.PRIMARY).mWidgetsListAdapter.hasVisibleEntries()
                || (mHasWorkProfile && mAdapters.get(AdapterHolder.WORK).mWidgetsListAdapter.hasVisibleEntries());
        if (mIsNoWidgetsViewNeeded != isNoWidgetsViewNeeded) {
            mIsNoWidgetsViewNeeded = isNoWidgetsViewNeeded;
            onRecommendedWidgetsBound();
        }
    }

    @Override
    public void enterSearchMode() {
        if (mIsInSearchMode)
            return;
        setViewVisibilityBasedOnSearch(/* isInSearchMode= */ true);
        attachScrollbarToRecyclerView(mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView);
        mActivityContext.getStatsLogManager().logger().log(LAUNCHER_WIDGETSTRAY_SEARCHED);
    }

    @Override
    public void exitSearchMode() {
        if (!mIsInSearchMode)
            return;
        onSearchResults(new ArrayList<>());
        setViewVisibilityBasedOnSearch(/* isInSearchMode= */ false);
        if (mHasWorkProfile) {
            mViewPager.snapToPage(AdapterHolder.PRIMARY);
        }
        attachScrollbarToRecyclerView(mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView);
    }

    @Override
    public void onSearchResults(List<WidgetsListBaseEntry> entries) {
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsListAdapter.setWidgetsOnSearch(entries);
        updateRecyclerViewVisibility(mAdapters.get(AdapterHolder.SEARCH));
        if (mIsTwoPane) {
            mAdapters.get(AdapterHolder.SEARCH).mWidgetsListAdapter.selectFirstHeaderEntry();
        }
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView.scrollToTop();
    }

    private void setViewVisibilityBasedOnSearch(boolean isInSearchMode) {
        mIsInSearchMode = isInSearchMode;
        if (isInSearchMode) {
            mRecommendedWidgetsTable.setVisibility(GONE);
            if (mIsTwoPane) {
                mSuggestedWidgetsContainer.setVisibility(GONE);
            }
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
            if (mIsTwoPane) {
                mSuggestedWidgetsContainer.setVisibility(VISIBLE);
                mSuggestedWidgetsHeader.callOnClick();
            }
            // Visibility of recommended widgets, recycler views and headers are handled in
            // methods
            // below.
            onRecommendedWidgetsBound();
            onWidgetsBound();
        }
    }

    private void resetExpandedHeaders() {
        mAdapters.get(AdapterHolder.PRIMARY).mWidgetsListAdapter.resetExpandedHeader();
        mAdapters.get(AdapterHolder.WORK).mWidgetsListAdapter.resetExpandedHeader();
    }

    @Override
    public void onRecommendedWidgetsBound() {
        if (mIsInSearchMode) {
            return;
        }
        List<WidgetItem> recommendedWidgets = mActivityContext.getPopupDataProvider().getRecommendedWidgets();
        if (recommendedWidgets.size() > 0) {
            float noWidgetsViewHeight = 0;
            if (mIsNoWidgetsViewNeeded) {
                // Make sure recommended section leaves enough space for noWidgetsView.
                Rect noWidgetsViewTextBounds = new Rect();
                mNoWidgetsView.getPaint()
                        .getTextBounds(mNoWidgetsView.getText().toString(), /* start= */ 0,
                                mNoWidgetsView.getText().length(), noWidgetsViewTextBounds);
                noWidgetsViewHeight = noWidgetsViewTextBounds.height();
            }
            doMeasure(
                    makeMeasureSpec(mActivityContext.getDeviceProfile().availableWidthPx,
                            MeasureSpec.EXACTLY),
                    makeMeasureSpec(mActivityContext.getDeviceProfile().availableHeightPx,
                            MeasureSpec.EXACTLY));
            float maxTableHeight = mIsTwoPane ? Float.MAX_VALUE
                    : (mContent.getMeasuredHeight()
                            - mTabsHeight - getHeaderViewHeight()
                            - noWidgetsViewHeight) * RECOMMENDATION_TABLE_HEIGHT_RATIO;

            List<ArrayList<WidgetItem>> recommendedWidgetsInTable = WidgetsTableUtils
                    .groupWidgetItemsUsingRowPxWithoutReordering(
                            recommendedWidgets,
                            mActivityContext,
                            mActivityContext.getDeviceProfile(),
                            mMaxSpanPerRow,
                            mWidgetCellHorizontalPadding);
            mRecommendedWidgetsTable.setRecommendedWidgets(
                    recommendedWidgetsInTable, maxTableHeight);
        } else {
            mRecommendedWidgetsTable.setVisibility(GONE);
        }
    }

    private void open(boolean animate) {
        if (animate) {
            if (getPopupContainer().getInsets().bottom > 0) {
                mContent.setAlpha(0);
                setTranslationShift(VERTICAL_START_POSITION);
            }
            mOpenCloseAnimator.setValues(
                    PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
            mOpenCloseAnimator
                    .setDuration(mActivityContext.getDeviceProfile().bottomSheetOpenDuration)
                    .setInterpolator(AnimationUtils.loadInterpolator(
                            getContext(), android.R.interpolator.linear_out_slow_in));
            mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mOpenCloseAnimator.removeListener(this);
                }
            });
            post(() -> {
                mOpenCloseAnimator.start();
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
        // Disable swipe down when recycler view is scrolling or scroll view is
        // scrolling
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = false;
            WidgetsRecyclerView recyclerView = getRecyclerView();
            RecyclerViewFastScroller scroller = recyclerView.getScrollbar();
            if (scroller.getThumbOffsetY() >= 0
                    && getPopupContainer().isEventOverView(scroller, ev)) {
                mNoIntercept = true;
            } else if (getPopupContainer().isEventOverView(recyclerView, ev)) {
                mNoIntercept = !recyclerView.shouldContainerScroll(ev, getPopupContainer());
            } else if (mIsTwoPane && getPopupContainer().isEventOverView(mRightPane, ev)) {
                mNoIntercept = mRightPane.getScrollY() > 0;
            }

            if (mSearchBar.isSearchBarFocused()
                    && !getPopupContainer().isEventOverView(mSearchBarContainer, ev)) {
                mSearchBar.clearSearchBarFocus();
            }
        }
        return super.onControllerInterceptTouchEvent(ev);
    }

    /** Shows the {@link WidgetsFullSheet} on the launcher. */
    public static WidgetsFullSheet show(Launcher launcher, boolean animate) {
        WidgetsFullSheet sheet = (WidgetsFullSheet) launcher.getLayoutInflater()
                .inflate(LARGE_SCREEN_WIDGET_PICKER.get()
                        && launcher.getDeviceProfile().isTablet
                        && launcher.getDeviceProfile().isLandscape
                                ? R.layout.widgets_full_sheet_large_screen
                                : R.layout.widgets_full_sheet,
                        launcher.getDragLayer(), false);
        sheet.attachToContainer();
        sheet.mIsOpen = true;
        sheet.open(animate);
        return sheet;
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

    /**
     * Gets the {@link WidgetsRecyclerView} which shows all widgets in
     * {@link WidgetsFullSheet}.
     */
    @VisibleForTesting
    public static WidgetsRecyclerView getWidgetsView(Launcher launcher) {
        return launcher.findViewById(R.id.primary_widgets_list_view);
    }

    @Override
    public void addHintCloseAnim(
            float distanceToMove, Interpolator interpolator, PendingAnimation target) {
        target.setFloat(getRecyclerView(), VIEW_TRANSLATE_Y, -distanceToMove, interpolator);
        target.setViewAlpha(getRecyclerView(), 0.5f, interpolator);
    }

    @Override
    protected void onCloseComplete() {
        super.onCloseComplete();
        removeCallbacks(mShowEducationTipTask);
        if (mLatestEducationalTip != null) {
            mLatestEducationalTip.close(false);
        }
        AccessibilityManagerCompat.sendStateEventToTest(getContext(), NORMAL_STATE_ORDINAL);
    }

    @Override
    public int getHeaderViewHeight() {
        return measureHeightWithVerticalMargins(mHeaderTitle)
                + measureHeightWithVerticalMargins(mSearchBarContainer);
    }

    /** private the height, in pixel, + the vertical margins of a given view. */
    private static int measureHeightWithVerticalMargins(View view) {
        if (view.getVisibility() != VISIBLE) {
            return 0;
        }
        MarginLayoutParams marginLayoutParams = (MarginLayoutParams) view.getLayoutParams();
        return view.getMeasuredHeight() + marginLayoutParams.bottomMargin
                + marginLayoutParams.topMargin;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mIsInSearchMode) {
            mSearchBar.reset();
        }

        // Checks the orientation of the screen
        if (LARGE_SCREEN_WIDGET_PICKER.get()
                && mOrientation != newConfig.orientation
                && mDeviceProfile.isTablet) {
            mOrientation = newConfig.orientation;
            handleClose(false);
            show(Launcher.getLauncher(getContext()), false);
        }
    }

    @Override
    public void onBackInvoked() {
        if (mIsInSearchMode) {
            mSearchBar.reset();
            animateSlideInViewToNoScale();
        } else {
            super.onBackInvoked();
        }
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        super.onDragStart(start, startDisplacement);
        if (Utilities.ATLEAST_R) {
            getWindowInsetsController().hide(WindowInsets.Type.ime());
        } else {
            Window window = mActivityContext.getWindow();
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, this);
            controller.hide(WindowInsetsCompat.Type.ime());
        }
    }

    @Nullable
    private View getViewToShowEducationTip() {
        if (mRecommendedWidgetsTable.getVisibility() == VISIBLE
                && mRecommendedWidgetsTable.getChildCount() > 0) {
            return ((ViewGroup) mRecommendedWidgetsTable.getChildAt(0)).getChildAt(0);
        }

        AdapterHolder adapterHolder = mAdapters.get(mIsInSearchMode
                ? AdapterHolder.SEARCH
                : mViewPager == null
                        ? AdapterHolder.PRIMARY
                        : mViewPager.getCurrentPage());
        WidgetsRowViewHolder viewHolderForTip = (WidgetsRowViewHolder) IntStream.range(
                0, adapterHolder.mWidgetsListAdapter.getItemCount())
                .mapToObj(adapterHolder.mWidgetsRecyclerView::findViewHolderForAdapterPosition)
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
        mActivityContext.getSharedPrefs().edit()
                .putBoolean(KEY_WIDGETS_EDUCATION_DIALOG_SEEN, true).apply();
        return WidgetsEduView.showEducationDialog(mActivityContext);
    }

    /** Returns {@code true} if education dialog has previously been shown. */
    protected boolean hasSeenEducationDialog() {
        return mActivityContext.getSharedPrefs()
                .getBoolean(KEY_WIDGETS_EDUCATION_DIALOG_SEEN, false)
                || Utilities.isRunningInTestHarness();
    }

    private void setUpEducationViewsIfNeeded() {
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

    /** A holder class for holding adapters & their corresponding recycler view. */
    private final class AdapterHolder {
        static final int PRIMARY = 0;
        static final int WORK = 1;
        static final int SEARCH = 2;

        private final int mAdapterType;
        private final WidgetsListAdapter mWidgetsListAdapter;
        private final DefaultItemAnimator mWidgetsListItemAnimator;

        private WidgetsRecyclerView mWidgetsRecyclerView;

        AdapterHolder(int adapterType) {
            mAdapterType = adapterType;
            Context context = getContext();
            HeaderChangeListener headerChangeListener = new HeaderChangeListener() {
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
                    WidgetsRowViewHolder widgetsRowViewHolder = mWidgetsListTableViewHolderBinder
                            .newViewHolder(mRightPane);
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
                }
            };
            mWidgetsListAdapter = new WidgetsListAdapter(
                    context,
                    LayoutInflater.from(context),
                    this::getEmptySpaceHeight,
                    /* iconClickListener= */ WidgetsFullSheet.this,
                    /* iconLongClickListener= */ WidgetsFullSheet.this,
                    mIsTwoPane ? headerChangeListener : null);
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
            // Disable change animations because it disrupts the item focus upon adapter
            // item
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
            if (!mIsTwoPane) {
                mWidgetsRecyclerView.setEdgeEffectFactory(
                        ((SpringRelativeLayout) mContent).createEdgeEffectFactory());
            }
            // Recycler view binds to fast scroller when it is attached to screen. Make sure
            // search recycler view is bound to fast scroller if user is in search mode at
            // the time
            // of attachment.
            if (mAdapterType == PRIMARY || mAdapterType == WORK) {
                mWidgetsRecyclerView.addOnAttachStateChangeListener(mBindScrollbarInSearchMode);
            }
            mWidgetsListAdapter.setMaxHorizontalSpansPxPerRow(mMaxSpanPerRow);
        }
    }

    /**
     * This is a listener for when the selected header gets changed in the left
     * pane.
     */
    public interface HeaderChangeListener {
        /**
         * Sets the right pane to have the widgets for the currently selected header
         * from
         * the left pane.
         */
        void onHeaderChanged(@NonNull PackageUserKey selectedHeader);
    }
}
