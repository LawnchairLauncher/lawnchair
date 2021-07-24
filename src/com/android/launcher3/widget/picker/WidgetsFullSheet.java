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
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGETSTRAY_SEARCHED;
import static com.android.launcher3.testing.TestProtocol.NORMAL_STATE_ORDINAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Process;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.views.ArrowTipView;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.TopRoundedCornerView;
import com.android.launcher3.views.WidgetsEduView;
import com.android.launcher3.widget.BaseWidgetSheet;
import com.android.launcher3.widget.LauncherAppWidgetHost.ProviderChangedListener;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.picker.search.SearchModeListener;
import com.android.launcher3.widget.picker.search.WidgetsSearchBar;
import com.android.launcher3.widget.util.WidgetsTableUtils;
import com.android.launcher3.workprofile.PersonalWorkPagedView;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip.OnActivePageChangedListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Popup for showing the full list of available widgets
 */
public class WidgetsFullSheet extends BaseWidgetSheet
        implements ProviderChangedListener, OnActivePageChangedListener,
        WidgetsRecyclerView.HeaderViewDimensionsProvider, SearchModeListener {
    private static final String TAG = WidgetsFullSheet.class.getSimpleName();

    private static final long DEFAULT_OPEN_DURATION = 267;
    private static final long FADE_IN_DURATION = 150;
    private static final long EDUCATION_TIP_DELAY_MS = 200;
    private static final long EDUCATION_DIALOG_DELAY_MS = 500;
    private static final float VERTICAL_START_POSITION = 0.3f;
    // The widget recommendation table can easily take over the entire screen on devices with small
    // resolution or landscape on phone. This ratio defines the max percentage of content area that
    // the table can display.
    private static final float RECOMMENDATION_TABLE_HEIGHT_RATIO = 0.75f;

    private static final String KEY_WIDGETS_EDUCATION_DIALOG_SEEN =
            "launcher.widgets_education_dialog_seen";

    private final Rect mInsets = new Rect();
    private final boolean mHasWorkProfile;
    private final SparseArray<AdapterHolder> mAdapters = new SparseArray();
    private final UserHandle mCurrentUser = Process.myUserHandle();
    private final Predicate<WidgetsListBaseEntry> mPrimaryWidgetsFilter =
            entry -> mCurrentUser.equals(entry.mPkgItem.user);
    private final Predicate<WidgetsListBaseEntry> mWorkWidgetsFilter =
            mPrimaryWidgetsFilter.negate();
    @Nullable private ArrowTipView mLatestEducationalTip;
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
                        searchRecyclerView.bindFastScrollbar();
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                }
            };

    private final int mTabsHeight;
    private final int mViewPagerTopPadding;
    private final int mSearchAndRecommendationContainerBottomMargin;
    private final int mWidgetCellHorizontalPadding;

    @Nullable private WidgetsRecyclerView mCurrentWidgetsRecyclerView;
    @Nullable private PersonalWorkPagedView mViewPager;
    private boolean mIsInSearchMode;
    private boolean mIsNoWidgetsViewNeeded;
    private int mMaxSpansPerRow = 4;
    private View mTabsView;
    private TextView mNoWidgetsView;
    private SearchAndRecommendationViewHolder mSearchAndRecommendationViewHolder;
    private SearchAndRecommendationsScrollController mSearchAndRecommendationsScrollController;

    public WidgetsFullSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHasWorkProfile = context.getSystemService(LauncherApps.class).getProfiles().size() > 1;
        mAdapters.put(AdapterHolder.PRIMARY, new AdapterHolder(AdapterHolder.PRIMARY));
        mAdapters.put(AdapterHolder.WORK, new AdapterHolder(AdapterHolder.WORK));
        mAdapters.put(AdapterHolder.SEARCH, new AdapterHolder(AdapterHolder.SEARCH));
        mTabsHeight = mHasWorkProfile
                ? getContext().getResources()
                        .getDimensionPixelSize(R.dimen.all_apps_header_pill_height)
                : 0;
        mViewPagerTopPadding = mHasWorkProfile
                ? getContext().getResources()
                    .getDimensionPixelSize(R.dimen.widget_picker_view_pager_top_padding)
                : 0;
        mSearchAndRecommendationContainerBottomMargin = getContext().getResources()
                .getDimensionPixelSize(mHasWorkProfile
                        ? R.dimen.search_and_recommended_widgets_container_small_bottom_margin
                        : R.dimen.search_and_recommended_widgets_container_bottom_margin);
        mWidgetCellHorizontalPadding = 2 * getResources().getDimensionPixelOffset(
                R.dimen.widget_cell_horizontal_padding);
    }

    public WidgetsFullSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.container);
        TopRoundedCornerView springLayout = (TopRoundedCornerView) mContent;

        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        int contentLayoutRes = mHasWorkProfile ? R.layout.widgets_full_sheet_paged_view
                : R.layout.widgets_full_sheet_recyclerview;
        layoutInflater.inflate(contentLayoutRes, springLayout, true);

        RecyclerViewFastScroller fastScroller = findViewById(R.id.fast_scroller);
        mAdapters.get(AdapterHolder.PRIMARY).setup(findViewById(R.id.primary_widgets_list_view));
        mAdapters.get(AdapterHolder.SEARCH).setup(findViewById(R.id.search_widgets_list_view));
        if (mHasWorkProfile) {
            mViewPager = findViewById(R.id.widgets_view_pager);
            mViewPager.initParentViews(this);
            mViewPager.getPageIndicator().setOnActivePageChangedListener(this);
            mViewPager.getPageIndicator().setActiveMarker(AdapterHolder.PRIMARY);
            mTabsView = findViewById(R.id.tabs);
            findViewById(R.id.tab_personal)
                    .setOnClickListener((View view) -> mViewPager.snapToPage(0));
            findViewById(R.id.tab_work)
                    .setOnClickListener((View view) -> mViewPager.snapToPage(1));
            fastScroller.setIsRecyclerViewFirstChildInParent(false);
            mAdapters.get(AdapterHolder.WORK).setup(findViewById(R.id.work_widgets_list_view));
        } else {
            mViewPager = null;
        }

        layoutInflater.inflate(R.layout.widgets_full_sheet_search_and_recommendations, springLayout,
                true);
        mNoWidgetsView = findViewById(R.id.no_widgets_text);
        mSearchAndRecommendationViewHolder = new SearchAndRecommendationViewHolder(
                findViewById(R.id.search_and_recommendations_container));
        TopRoundedCornerView.LayoutParams layoutParams =
                (TopRoundedCornerView.LayoutParams)
                        mSearchAndRecommendationViewHolder.mContainer.getLayoutParams();
        layoutParams.bottomMargin = mSearchAndRecommendationContainerBottomMargin;
        mSearchAndRecommendationViewHolder.mContainer.setLayoutParams(layoutParams);
        mSearchAndRecommendationsScrollController = new SearchAndRecommendationsScrollController(
                mHasWorkProfile,
                mTabsHeight,
                mSearchAndRecommendationViewHolder,
                findViewById(R.id.primary_widgets_list_view),
                mHasWorkProfile ? findViewById(R.id.work_widgets_list_view) : null,
                findViewById(R.id.search_widgets_list_view),
                mTabsView,
                mViewPager,
                mNoWidgetsView);
        fastScroller.setOnFastScrollChangeListener(mSearchAndRecommendationsScrollController);


        onRecommendedWidgetsBound();
        onWidgetsBound();

        mSearchAndRecommendationViewHolder.mSearchBar.initialize(
                mActivityContext.getPopupDataProvider(), /* searchModeListener= */ this);

        setUpEducationViewsIfNeeded();
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
        recyclerView.bindFastScrollbar();
        if (mCurrentWidgetsRecyclerView != recyclerView) {
            // Only reset the scroll position & expanded apps if the currently shown recycler view
            // has been updated.
            reset();
            resetExpandedHeaders();
            mCurrentWidgetsRecyclerView = recyclerView;
            mSearchAndRecommendationsScrollController.setCurrentRecyclerView(recyclerView);
        }
    }

    private void updateRecyclerViewVisibility(AdapterHolder adapterHolder) {
        boolean isWidgetAvailable = adapterHolder.mWidgetsListAdapter.getItemCount() > 0;
        adapterHolder.mWidgetsRecyclerView.setVisibility(isWidgetAvailable ? VISIBLE : GONE);

        mNoWidgetsView.setText(
                adapterHolder.mAdapterType == AdapterHolder.SEARCH
                        ? R.string.no_search_results
                        : R.string.no_widgets_available);
        mNoWidgetsView.setVisibility(isWidgetAvailable ? GONE : VISIBLE);
    }

    private void reset() {
        mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView.scrollToTop();
        if (mHasWorkProfile) {
            mAdapters.get(AdapterHolder.WORK).mWidgetsRecyclerView.scrollToTop();
        }
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView.scrollToTop();
        mSearchAndRecommendationsScrollController.reset(/* animate= */ true);
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
        mActivityContext.getAppWidgetHost().addProviderChangeListener(this);
        notifyWidgetProvidersChanged();
        onRecommendedWidgetsBound();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivityContext.getAppWidgetHost().removeProviderChangeListener(this);
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

        setBottomPadding(mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView, insets.bottom);
        setBottomPadding(mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView, insets.bottom);
        if (mHasWorkProfile) {
            setBottomPadding(mAdapters.get(AdapterHolder.WORK).mWidgetsRecyclerView, insets.bottom);
        }
        mSearchAndRecommendationsScrollController.updateBottomInset(insets.bottom);
        if (insets.bottom > 0) {
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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        doMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mSearchAndRecommendationsScrollController.updateMarginAndPadding()) {
            doMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        if (updateMaxSpansPerRow()) {
            doMeasure(widthMeasureSpec, heightMeasureSpec);

            if (mSearchAndRecommendationsScrollController.updateMarginAndPadding()) {
                doMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    /** Returns {@code true} if the max spans have been updated. */
    private boolean updateMaxSpansPerRow() {
        if (getMeasuredWidth() == 0) return false;

        int previousMaxSpansPerRow = mMaxSpansPerRow;
        mMaxSpansPerRow = getMeasuredWidth()
                / (mActivityContext.getDeviceProfile().cellWidthPx + mWidgetCellHorizontalPadding);

        if (previousMaxSpansPerRow != mMaxSpansPerRow) {
            mAdapters.get(AdapterHolder.PRIMARY).mWidgetsListAdapter.setMaxHorizontalSpansPerRow(
                    mMaxSpansPerRow);
            mAdapters.get(AdapterHolder.SEARCH).mWidgetsListAdapter.setMaxHorizontalSpansPerRow(
                    mMaxSpansPerRow);
            if (mHasWorkProfile) {
                mAdapters.get(AdapterHolder.WORK).mWidgetsListAdapter.setMaxHorizontalSpansPerRow(
                        mMaxSpansPerRow);
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
        List<WidgetsListBaseEntry> allWidgets =
                mActivityContext.getPopupDataProvider().getAllWidgets();

        AdapterHolder primaryUserAdapterHolder = mAdapters.get(AdapterHolder.PRIMARY);
        primaryUserAdapterHolder.mWidgetsListAdapter.setWidgets(allWidgets);

        if (mHasWorkProfile) {
            mViewPager.setVisibility(VISIBLE);
            mTabsView.setVisibility(VISIBLE);
            AdapterHolder workUserAdapterHolder = mAdapters.get(AdapterHolder.WORK);
            workUserAdapterHolder.mWidgetsListAdapter.setWidgets(allWidgets);
            onActivePageChanged(mViewPager.getCurrentPage());
        } else {
            updateRecyclerViewVisibility(primaryUserAdapterHolder);
        }
        // Update recommended widgets section so that it occupies appropriate space on screen to
        // leave enough space for presence/absence of mNoWidgetsView.
        boolean isNoWidgetsViewNeeded =
                mAdapters.get(AdapterHolder.PRIMARY).mWidgetsListAdapter.getItemCount() == 0
                        || (mHasWorkProfile && mAdapters.get(AdapterHolder.WORK)
                                .mWidgetsListAdapter.getItemCount() == 0);
        if (mIsNoWidgetsViewNeeded != isNoWidgetsViewNeeded) {
            mIsNoWidgetsViewNeeded = isNoWidgetsViewNeeded;
            onRecommendedWidgetsBound();
        }
    }

    @Override
    public void enterSearchMode() {
        if (mIsInSearchMode) return;
        setViewVisibilityBasedOnSearch(/*isInSearchMode= */ true);
        attachScrollbarToRecyclerView(mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView);
        mActivityContext.getStatsLogManager().logger().log(LAUNCHER_WIDGETSTRAY_SEARCHED);
    }

    @Override
    public void exitSearchMode() {
        if (!mIsInSearchMode) return;
        onSearchResults(new ArrayList<>());
        setViewVisibilityBasedOnSearch(/*isInSearchMode=*/ false);
        if (mHasWorkProfile) {
            mViewPager.snapToPage(AdapterHolder.PRIMARY);
        }
        attachScrollbarToRecyclerView(mAdapters.get(AdapterHolder.PRIMARY).mWidgetsRecyclerView);

        mSearchAndRecommendationsScrollController.updateMarginAndPadding();
    }

    @Override
    public void onSearchResults(List<WidgetsListBaseEntry> entries) {
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsListAdapter.setWidgetsOnSearch(entries);
        updateRecyclerViewVisibility(mAdapters.get(AdapterHolder.SEARCH));
        mAdapters.get(AdapterHolder.SEARCH).mWidgetsRecyclerView.scrollToTop();
    }

    private void setViewVisibilityBasedOnSearch(boolean isInSearchMode) {
        mIsInSearchMode = isInSearchMode;
        if (isInSearchMode) {
            mSearchAndRecommendationViewHolder.mRecommendedWidgetsTable.setVisibility(GONE);
            if (mHasWorkProfile) {
                mViewPager.setVisibility(GONE);
                mTabsView.setVisibility(GONE);
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

    private void resetExpandedHeaders() {
        mAdapters.get(AdapterHolder.PRIMARY).mWidgetsListAdapter.resetExpandedHeader();
        mAdapters.get(AdapterHolder.WORK).mWidgetsListAdapter.resetExpandedHeader();
    }

    @Override
    public void onRecommendedWidgetsBound() {
        if (mIsInSearchMode) {
            return;
        }
        List<WidgetItem> recommendedWidgets =
                mActivityContext.getPopupDataProvider().getRecommendedWidgets();
        WidgetsRecommendationTableLayout table =
                mSearchAndRecommendationViewHolder.mRecommendedWidgetsTable;
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
            float maxTableHeight = (mContent.getMeasuredHeight()
                    - mTabsHeight - mViewPagerTopPadding - getHeaderViewHeight()
                    - noWidgetsViewHeight) * RECOMMENDATION_TABLE_HEIGHT_RATIO;

            List<ArrayList<WidgetItem>> recommendedWidgetsInTable =
                    WidgetsTableUtils.groupWidgetItemsIntoTable(recommendedWidgets,
                            mMaxSpansPerRow);
            table.setRecommendedWidgets(recommendedWidgetsInTable, maxTableHeight);
        } else {
            table.setVisibility(GONE);
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
                    .setDuration(DEFAULT_OPEN_DURATION)
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
        handleClose(animate, DEFAULT_OPEN_DURATION);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_WIDGETS_FULL_SHEET) != 0;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        // Disable swipe down when recycler view is scrolling
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = false;
            RecyclerViewFastScroller scroller = getRecyclerView().getScrollbar();
            if (scroller.getThumbOffsetY() >= 0
                    && getPopupContainer().isEventOverView(scroller, ev)) {
                mNoIntercept = true;
            } else if (getPopupContainer().isEventOverView(mContent, ev)) {
                mNoIntercept = !getRecyclerView().shouldContainerScroll(ev, getPopupContainer());
            }

            if (mSearchAndRecommendationViewHolder.mSearchBar.isSearchBarFocused()
                    && !getPopupContainer().isEventOverView(
                            mSearchAndRecommendationViewHolder.mSearchBarContainer, ev)) {
                mSearchAndRecommendationViewHolder.mSearchBar.clearSearchBarFocus();
            }
        }
        return super.onControllerInterceptTouchEvent(ev);
    }

    /** Shows the {@link WidgetsFullSheet} on the launcher. */
    public static WidgetsFullSheet show(Launcher launcher, boolean animate) {
        WidgetsFullSheet sheet = (WidgetsFullSheet) launcher.getLayoutInflater()
                .inflate(R.layout.widgets_full_sheet, launcher.getDragLayer(), false);
        sheet.attachToContainer();
        sheet.mIsOpen = true;
        sheet.open(animate);
        return sheet;
    }

    /** Gets the {@link WidgetsRecyclerView} which shows all widgets in {@link WidgetsFullSheet}. */
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
        return measureHeightWithVerticalMargins(mSearchAndRecommendationViewHolder.mCollapseHandle)
                + measureHeightWithVerticalMargins(mSearchAndRecommendationViewHolder.mHeaderTitle)
                + measureHeightWithVerticalMargins(
                        (View) mSearchAndRecommendationViewHolder.mSearchBarContainer);
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
            mSearchAndRecommendationViewHolder.mSearchBar.reset();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (mIsInSearchMode) {
            mSearchAndRecommendationViewHolder.mSearchBar.reset();
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        super.onDragStart(start, startDisplacement);
        getWindowInsetsController().hide(WindowInsets.Type.ime());
    }

    @Nullable private View getViewToShowEducationTip() {
        if (mSearchAndRecommendationViewHolder.mRecommendedWidgetsTable.getVisibility() == VISIBLE
                && mSearchAndRecommendationViewHolder.mRecommendedWidgetsTable.getChildCount() > 0
        ) {
            return ((ViewGroup) mSearchAndRecommendationViewHolder.mRecommendedWidgetsTable
                    .getChildAt(0)).getChildAt(0);
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
            return ((ViewGroup) viewHolderForTip.mTableContainer.getChildAt(0)).getChildAt(0);
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
                || Utilities.IS_RUNNING_IN_TEST_HARNESS;
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
            LauncherAppState apps = LauncherAppState.getInstance(context);
            mWidgetsListAdapter = new WidgetsListAdapter(
                    context,
                    LayoutInflater.from(context),
                    apps.getWidgetCache(),
                    apps.getIconCache(),
                    /* iconClickListener= */ WidgetsFullSheet.this,
                    /* iconLongClickListener= */ WidgetsFullSheet.this);
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

        void setup(WidgetsRecyclerView recyclerView) {
            mWidgetsRecyclerView = recyclerView;
            mWidgetsRecyclerView.setAdapter(mWidgetsListAdapter);
            mWidgetsRecyclerView.setItemAnimator(mWidgetsListItemAnimator);
            mWidgetsRecyclerView.setHeaderViewDimensionsProvider(WidgetsFullSheet.this);
            mWidgetsRecyclerView.setEdgeEffectFactory(
                    ((TopRoundedCornerView) mContent).createEdgeEffectFactory());
            // Recycler view binds to fast scroller when it is attached to screen. Make sure
            // search recycler view is bound to fast scroller if user is in search mode at the time
            // of attachment.
            if (mAdapterType == PRIMARY || mAdapterType == WORK) {
                mWidgetsRecyclerView.addOnAttachStateChangeListener(mBindScrollbarInSearchMode);
            }
            mWidgetsListAdapter.setApplyBitmapDeferred(false, mWidgetsRecyclerView);
            mWidgetsListAdapter.setMaxHorizontalSpansPerRow(mMaxSpansPerRow);
        }
    }

    final class SearchAndRecommendationViewHolder {
        final SearchAndRecommendationsView mContainer;
        final View mCollapseHandle;
        final View mSearchBarContainer;
        final WidgetsSearchBar mSearchBar;
        final TextView mHeaderTitle;
        final WidgetsRecommendationTableLayout mRecommendedWidgetsTable;

        SearchAndRecommendationViewHolder(
                SearchAndRecommendationsView searchAndRecommendationContainer) {
            mContainer = searchAndRecommendationContainer;
            mCollapseHandle = mContainer.findViewById(R.id.collapse_handle);
            mSearchBarContainer = mContainer.findViewById(R.id.search_bar_container);
            mSearchBar = mContainer.findViewById(R.id.widgets_search_bar);
            mHeaderTitle = mContainer.findViewById(R.id.title);
            mRecommendedWidgetsTable = mContainer.findViewById(R.id.recommended_widget_table);
            mRecommendedWidgetsTable.setWidgetCellOnTouchListener((view, event) -> {
                getRecyclerView().onTouchEvent(event);
                return false;
            });
            mRecommendedWidgetsTable.setWidgetCellLongClickListener(WidgetsFullSheet.this);
            mRecommendedWidgetsTable.setWidgetCellOnClickListener(WidgetsFullSheet.this);
        }
    }
}
