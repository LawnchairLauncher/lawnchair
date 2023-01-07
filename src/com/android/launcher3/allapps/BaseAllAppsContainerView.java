/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_COUNT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Insettable;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.views.SpringRelativeLayout;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip.OnActivePageChangedListener;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Base all apps view container.
 *
 * @param <T> Type of context inflating all apps.
 */
public abstract class BaseAllAppsContainerView<T extends Context & ActivityContext>
        extends SpringRelativeLayout implements DragSource, Insettable,
        OnDeviceProfileChangeListener, OnActivePageChangedListener,
        ScrimView.ScrimDrawingController {

    protected static final String BUNDLE_KEY_CURRENT_PAGE = "launcher.allapps.current_page";

    public static final float PULL_MULTIPLIER = .02f;
    public static final float FLING_VELOCITY_MULTIPLIER = 1200f;

    // Render the header protection at all times to debug clipping issues.
    private static final boolean DEBUG_HEADER_PROTECTION = false;

    private final Paint mHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mInsets = new Rect();

    /** Context of an activity or window that is inflating this container. */
    protected final T mActivityContext;
    protected final List<AdapterHolder> mAH;
    protected final Predicate<ItemInfo> mPersonalMatcher = ItemInfoMatcher.ofUser(
            Process.myUserHandle());
    private final AllAppsStore mAllAppsStore = new AllAppsStore();

    private final RecyclerView.OnScrollListener mScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateHeaderScroll(recyclerView.computeVerticalScrollOffset());
                }
            };

    protected final WorkProfileManager mWorkManager;

    private final Paint mNavBarScrimPaint;
    private int mNavBarScrimHeight = 0;

    protected AllAppsPagedView mViewPager;
    private SearchRecyclerView mSearchRecyclerView;
    private SearchAdapterProvider<?> mMainAdapterProvider;

    protected FloatingHeaderView mHeader;
    protected View mBottomSheetBackground;
    private View mBottomSheetHandleArea;

    /**
     * View that defines the search box. Result is rendered inside {@link #mSearchRecyclerView}.
     */
    protected View mSearchContainer;
    protected SearchUiManager mSearchUiManager;

    protected boolean mUsingTabs;
    private boolean mHasWorkApps;

    protected RecyclerViewFastScroller mTouchHandler;
    protected final Point mFastScrollerOffset = new Point();

    protected final int mScrimColor;
    private final int mHeaderProtectionColor;
    protected final float mHeaderThreshold;
    private final Path mTmpPath = new Path();
    private final RectF mTmpRectF = new RectF();
    private float[] mBottomSheetCornerRadii;
    private ScrimView mScrimView;
    private int mHeaderColor;
    private int mBottomSheetBackgroundColor;
    private int mTabsProtectionAlpha;

    protected BaseAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);

        mScrimColor = Themes.getAttrColor(context, R.attr.allAppsScrimColor);
        mHeaderThreshold = getResources().getDimensionPixelSize(
                R.dimen.dynamic_grid_cell_border_spacing);
        mHeaderProtectionColor = Themes.getAttrColor(context, R.attr.allappsHeaderProtectionColor);

        mWorkManager = new WorkProfileManager(
                mActivityContext.getSystemService(UserManager.class),
                this, LauncherPrefs.getPrefs(mActivityContext),
                mActivityContext.getStatsLogManager());
        mAH = Arrays.asList(null, null, null);
        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(Themes.getAttrColor(context, R.attr.allAppsNavBarScrimColor));

        mAllAppsStore.addUpdateListener(this::onAppsUpdated);
        mActivityContext.addOnDeviceProfileChangeListener(this);

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && getActiveRecyclerView() != null) {
                getActiveRecyclerView().requestFocus();
            }
        });
        initContent();
    }

    /**
     * Initializes the view hierarchy and internal variables. Any initialization which actually uses
     * these members should be done in {@link #onFinishInflate()}.
     * In terms of subclass initialization, the following would be parallel order for activity:
     *   initContent -> onPreCreate
     *   constructor/init -> onCreate
     *   onFinishInflate -> onPostCreate
     */
    protected void initContent() {
        mMainAdapterProvider = createMainAdapterProvider();

        mAH.set(AdapterHolder.MAIN, new AdapterHolder(AdapterHolder.MAIN));
        mAH.set(AdapterHolder.WORK, new AdapterHolder(AdapterHolder.WORK));
        mAH.set(AdapterHolder.SEARCH, new AdapterHolder(AdapterHolder.SEARCH));

        getLayoutInflater().inflate(R.layout.all_apps_content, this);
        mHeader = findViewById(R.id.all_apps_header);
        mBottomSheetBackground = findViewById(R.id.bottom_sheet_background);
        mBottomSheetHandleArea = findViewById(R.id.bottom_sheet_handle_area);
        mSearchRecyclerView = findViewById(R.id.search_results_list_view);

        // Add the search box next to the header
        mSearchContainer = inflateSearchBox();
        addView(mSearchContainer, indexOfChild(mHeader) + 1);
        mSearchUiManager = (SearchUiManager) mSearchContainer;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAH.get(AdapterHolder.SEARCH).setup(mSearchRecyclerView,
                /* Filter out A-Z apps */ itemInfo -> false);
        rebindAdapters(true /* force */);
        float cornerRadius = Themes.getDialogCornerRadius(getContext());
        mBottomSheetCornerRadii = new float[]{
                cornerRadius,
                cornerRadius, // Top left radius in px
                cornerRadius,
                cornerRadius, // Top right radius in px
                0,
                0, // Bottom right
                0,
                0 // Bottom left
        };
        final TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.colorBackground, value, true);
        mBottomSheetBackgroundColor = value.data;
        updateBackground(mActivityContext.getDeviceProfile());
    }

    /**
     * Inflates the search box
     */
    protected View inflateSearchBox() {
        return getLayoutInflater().inflate(R.layout.search_container_all_apps, this, false);
    }

    /** Creates the adapter provider for the main section. */
    protected SearchAdapterProvider<?> createMainAdapterProvider() {
        return new DefaultSearchAdapterProvider(mActivityContext);
    }

    /** The adapter provider for the main section. */
    public final SearchAdapterProvider<?> getMainAdapterProvider() {
        return mMainAdapterProvider;
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> sparseArray) {
        try {
            // Many slice view id is not properly assigned, and hence throws null
            // pointer exception in the underneath method. Catching the exception
            // simply doesn't restore these slice views. This doesn't have any
            // user visible effect because because we query them again.
            super.dispatchRestoreInstanceState(sparseArray);
        } catch (Exception e) {
            Log.e("AllAppsContainerView", "restoreInstanceState viewId = 0", e);
        }

        Bundle state = (Bundle) sparseArray.get(R.id.work_tab_state_id, null);
        if (state != null) {
            int currentPage = state.getInt(BUNDLE_KEY_CURRENT_PAGE, 0);
            if (currentPage == AdapterHolder.WORK && mViewPager != null) {
                mViewPager.setCurrentPage(currentPage);
                rebindAdapters();
            } else {
                reset(true);
            }
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        super.dispatchSaveInstanceState(container);
        Bundle state = new Bundle();
        state.putInt(BUNDLE_KEY_CURRENT_PAGE, getCurrentPage());
        container.put(R.id.work_tab_state_id, state);
    }

    /**
     * Sets the long click listener for icons
     */
    public void setOnIconLongClickListener(OnLongClickListener listener) {
        for (AdapterHolder holder : mAH) {
            holder.mAdapter.setOnIconLongClickListener(listener);
        }
    }

    public AllAppsStore getAppsStore() {
        return mAllAppsStore;
    }

    public WorkProfileManager getWorkManager() {
        return mWorkManager;
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        for (AdapterHolder holder : mAH) {
            holder.mAdapter.setAppsPerRow(dp.numShownAllAppsColumns);
            if (holder.mRecyclerView != null) {
                // Remove all views and clear the pool, while keeping the data same. After this
                // call, all the viewHolders will be recreated.
                holder.mRecyclerView.swapAdapter(holder.mRecyclerView.getAdapter(), true);
                holder.mRecyclerView.getRecycledViewPool().clear();
            }
        }
        updateBackground(dp);
    }

    protected void updateBackground(DeviceProfile deviceProfile) {
        mBottomSheetBackground.setVisibility(deviceProfile.isTablet ? View.VISIBLE : View.GONE);
        // Note: For tablets, the opaque background and header protection are added in drawOnScrim.
        // For the taskbar entrypoint, the scrim is drawn differently, so a static background is
        // added in TaskbarAllAppsContainerView and header protection is not yet supported.
    }

    private void onAppsUpdated() {
        mHasWorkApps = Stream.of(mAllAppsStore.getApps()).anyMatch(mWorkManager.getMatcher());
        if (!isSearching()) {
            rebindAdapters();
            if (mHasWorkApps) {
                mWorkManager.reset();
            }
        }

        mActivityContext.getStatsLogManager().logger()
                .withCardinality(mAllAppsStore.getApps().length)
                .log(LAUNCHER_ALLAPPS_COUNT);
    }

    /**
     * Returns whether the view itself will handle the touch event or not.
     */
    public boolean shouldContainerScroll(MotionEvent ev) {
        BaseDragLayer dragLayer = mActivityContext.getDragLayer();
        // Scroll if not within the container view (e.g. over large-screen scrim).
        if (!dragLayer.isEventOverView(getVisibleContainerView(), ev)) {
            return true;
        }
        if (dragLayer.isEventOverView(mBottomSheetHandleArea, ev)) {
            return true;
        }
        AllAppsRecyclerView rv = getActiveRecyclerView();
        if (rv == null) {
            return true;
        }
        if (rv.getScrollbar() != null
                && rv.getScrollbar().getThumbOffsetY() >= 0
                && dragLayer.isEventOverView(rv.getScrollbar(), ev)) {
            return false;
        }
        return rv.shouldContainerScroll(ev, dragLayer);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // The AllAppsContainerView houses the QSB and is hence visible from the Workspace
        // Overview states. We shouldn't intercept for the scrubber in these cases.
        if (!isInAllApps()) {
            mTouchHandler = null;
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;
            }
        }
        if (mTouchHandler != null) {
            return mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isInAllApps()) {
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar() != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;

            }
        }
        if (mTouchHandler != null) {
            mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
            return true;
        }
        if (isSearching()
                && mActivityContext.getDragLayer().isEventOverView(getVisibleContainerView(), ev)) {
            // if in search state, consume touch event.
            return true;
        }
        return false;
    }

    /** Description of the container view based on its current state. */
    public String getDescription() {
        StringCache cache = mActivityContext.getStringCache();
        if (mUsingTabs) {
            if (cache != null) {
                return isPersonalTab()
                        ? cache.allAppsPersonalTabAccessibility
                        : cache.allAppsWorkTabAccessibility;
            } else {
                return isPersonalTab()
                        ? getContext().getString(R.string.all_apps_button_personal_label)
                        : getContext().getString(R.string.all_apps_button_work_label);
            }
        }
        return getContext().getString(R.string.all_apps_button_label);
    }

    /** The current active recycler view (A-Z list from one of the profiles, or search results). */
    public AllAppsRecyclerView getActiveRecyclerView() {
        if (isSearching()) {
            return getSearchRecyclerView();
        }
        return getActiveAppsRecyclerView();
    }

    /** The current apps recycler view in the container. */
    private AllAppsRecyclerView getActiveAppsRecyclerView() {
        if (!mUsingTabs || isPersonalTab()) {
            return mAH.get(AdapterHolder.MAIN).mRecyclerView;
        } else {
            return mAH.get(AdapterHolder.WORK).mRecyclerView;
        }
    }

    /**
     * The container for A-Z apps (the ViewPager for main+work tabs, or main RV). This is currently
     * hidden while searching.
     **/
    protected View getAppsRecyclerViewContainer() {
        return mViewPager != null ? mViewPager : findViewById(R.id.apps_list_view);
    }

    /** The RV for search results, which is hidden while A-Z apps are visible. */
    public SearchRecyclerView getSearchRecyclerView() {
        return mSearchRecyclerView;
    }

    protected boolean isPersonalTab() {
        return mViewPager == null || mViewPager.getNextPage() == 0;
    }

    /**
     * Switches the current page to the provided {@code tab} if tabs are supported, otherwise does
     * nothing.
     */
    public void switchToTab(int tab) {
        if (mUsingTabs) {
            mViewPager.setCurrentPage(tab);
        }
    }

    public LayoutInflater getLayoutInflater() {
        return LayoutInflater.from(getContext());
    }

    /**
     * Resets the state of AllApps.
     */
    public void reset(boolean animate) {
        for (int i = 0; i < mAH.size(); i++) {
            if (mAH.get(i).mRecyclerView != null) {
                mAH.get(i).mRecyclerView.scrollToTop();
            }
        }
        if (isHeaderVisible()) {
            mHeader.reset(animate);
        }
        // Reset the base recycler view after transitioning home.
        updateHeaderScroll(0);
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {}

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile grid = mActivityContext.getDeviceProfile();

        applyAdapterSideAndBottomPaddings(grid);

        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        mlp.leftMargin = insets.left;
        mlp.rightMargin = insets.right;
        setLayoutParams(mlp);

        if (grid.isVerticalBarLayout()) {
            setPadding(grid.workspacePadding.left, 0, grid.workspacePadding.right, 0);
        } else {
            int topPadding = grid.allAppsTopPadding;
            if (FeatureFlags.ENABLE_FLOATING_SEARCH_BAR.get() && !grid.isTablet) {
                topPadding += getResources().getDimensionPixelSize(
                        R.dimen.all_apps_additional_top_padding_floating_search);
            }
            setPadding(grid.allAppsLeftRightMargin, topPadding, grid.allAppsLeftRightMargin, 0);
        }

        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    /**
     * Returns a padding in case a scrim is shown on the bottom of the view and a padding is needed.
     */
    protected int getNavBarScrimHeight(WindowInsets insets) {
        return 0;
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        mNavBarScrimHeight = getNavBarScrimHeight(insets);
        applyAdapterSideAndBottomPaddings(mActivityContext.getDeviceProfile());
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mNavBarScrimHeight > 0) {
            canvas.drawRect(0, getHeight() - mNavBarScrimHeight, getWidth(), getHeight(),
                    mNavBarScrimPaint);
        }
    }

    protected void rebindAdapters() {
        rebindAdapters(false /* force */);
    }

    protected void rebindAdapters(boolean force) {
        updateSearchResultsVisibility();

        boolean showTabs = shouldShowTabs();
        if (showTabs == mUsingTabs && !force) {
            return;
        }

        if (isSearching()) {
            mUsingTabs = showTabs;
            mWorkManager.detachWorkModeSwitch();
            return;
        }

        // replaceAppsRVcontainer() needs to use both mUsingTabs value to remove the old view AND
        // showTabs value to create new view. Hence the mUsingTabs new value assignment MUST happen
        // after this call.
        replaceAppsRVContainer(showTabs);
        mUsingTabs = showTabs;

        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);

        if (mUsingTabs) {
            mAH.get(AdapterHolder.MAIN).setup(mViewPager.getChildAt(0), mPersonalMatcher);
            mAH.get(AdapterHolder.WORK).setup(mViewPager.getChildAt(1), mWorkManager.getMatcher());
            mAH.get(AdapterHolder.WORK).mRecyclerView.setId(R.id.apps_list_view_work);
            if (FeatureFlags.ENABLE_EXPANDING_PAUSE_WORK_BUTTON.get()) {
                mAH.get(AdapterHolder.WORK).mRecyclerView.addOnScrollListener(
                        mWorkManager.newScrollListener());
            }
            mViewPager.getPageIndicator().setActiveMarker(AdapterHolder.MAIN);
            findViewById(R.id.tab_personal)
                    .setOnClickListener((View view) -> {
                        if (mViewPager.snapToPage(AdapterHolder.MAIN)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB);
                        }
                        mActivityContext.hideKeyboard();
                    });
            findViewById(R.id.tab_work)
                    .setOnClickListener((View view) -> {
                        if (mViewPager.snapToPage(AdapterHolder.WORK)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB);
                        }
                        mActivityContext.hideKeyboard();
                    });
            setDeviceManagementResources();
            onActivePageChanged(mViewPager.getNextPage());
        } else {
            mAH.get(AdapterHolder.MAIN).setup(findViewById(R.id.apps_list_view), null);
            mAH.get(AdapterHolder.WORK).mRecyclerView = null;
        }
        setupHeader();

        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);
        mAllAppsStore.registerIconContainer(mAH.get(AdapterHolder.SEARCH).mRecyclerView);
    }

    protected void updateSearchResultsVisibility() {
        if (isSearching()) {
            getSearchRecyclerView().setVisibility(VISIBLE);
            getAppsRecyclerViewContainer().setVisibility(GONE);
            mHeader.setVisibility(GONE);
        } else {
            getSearchRecyclerView().setVisibility(GONE);
            getAppsRecyclerViewContainer().setVisibility(VISIBLE);
            mHeader.setVisibility(VISIBLE);
        }
        if (mHeader.isSetUp()) {
            mHeader.setActiveRV(getCurrentPage());
        }
    }

    private void applyAdapterSideAndBottomPaddings(DeviceProfile grid) {
        int bottomPadding = Math.max(mInsets.bottom, mNavBarScrimHeight);
        mAH.forEach(adapterHolder -> {
            adapterHolder.mPadding.bottom = bottomPadding;
            adapterHolder.mPadding.left =
                    adapterHolder.mPadding.right = grid.allAppsLeftRightPadding;
            adapterHolder.applyPadding();
        });
    }

    private void setDeviceManagementResources() {
        if (mActivityContext.getStringCache() != null) {
            Button personalTab = findViewById(R.id.tab_personal);
            personalTab.setText(mActivityContext.getStringCache().allAppsPersonalTab);

            Button workTab = findViewById(R.id.tab_work);
            workTab.setText(mActivityContext.getStringCache().allAppsWorkTab);
        }
    }

    protected boolean shouldShowTabs() {
        return mHasWorkApps;
    }

    protected boolean isSearching() {
        return false;
    }

    protected View replaceAppsRVContainer(boolean showTabs) {
        for (int i = AdapterHolder.MAIN; i <= AdapterHolder.WORK; i++) {
            AdapterHolder adapterHolder = mAH.get(i);
            if (adapterHolder.mRecyclerView != null) {
                adapterHolder.mRecyclerView.setLayoutManager(null);
                adapterHolder.mRecyclerView.setAdapter(null);
            }
        }
        View oldView = getAppsRecyclerViewContainer();
        int index = indexOfChild(oldView);
        removeView(oldView);
        int layout = showTabs ? R.layout.all_apps_tabs : R.layout.all_apps_rv_layout;
        View newView = getLayoutInflater().inflate(layout, this, false);
        addView(newView, index);
        if (showTabs) {
            mViewPager = (AllAppsPagedView) newView;
            mViewPager.initParentViews(this);
            mViewPager.getPageIndicator().setOnActivePageChangedListener(this);

            mWorkManager.reset();
            post(() -> mAH.get(AdapterHolder.WORK).applyPadding());

        } else {
            mWorkManager.detachWorkModeSwitch();
            mViewPager = null;
        }
        return newView;
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        if (mAH.get(currentActivePage).mRecyclerView != null) {
            mAH.get(currentActivePage).mRecyclerView.bindFastScrollbar();
        }
        // Header keeps track of active recycler view to properly render header protection.
        mHeader.setActiveRV(currentActivePage);
        reset(true /* animate */);

        mWorkManager.onActivePageChanged(currentActivePage);
    }

    // Used by tests only
    private boolean isDescendantViewVisible(int viewId) {
        final View view = findViewById(viewId);
        if (view == null) return false;

        if (!view.isShown()) return false;

        return view.getGlobalVisibleRect(new Rect());
    }

    @VisibleForTesting
    public boolean isPersonalTabVisible() {
        return isDescendantViewVisible(R.id.tab_personal);
    }

    @VisibleForTesting
    public boolean isWorkTabVisible() {
        return isDescendantViewVisible(R.id.tab_work);
    }

    public AlphabeticalAppsList<T> getSearchResultList() {
        return mAH.get(AdapterHolder.SEARCH).mAppsList;
    }

    public FloatingHeaderView getFloatingHeaderView() {
        return mHeader;
    }

    @VisibleForTesting
    public View getContentView() {
        return isSearching() ? getSearchRecyclerView() : getAppsRecyclerViewContainer();
    }

    /** The current page visible in all apps. */
    public int getCurrentPage() {
        return isSearching()
                ? AdapterHolder.SEARCH
                : mViewPager == null ? AdapterHolder.MAIN : mViewPager.getNextPage();
    }

    /** The scroll bar for the active apps recycler view. */
    public RecyclerViewFastScroller getScrollBar() {
        AllAppsRecyclerView rv = getActiveAppsRecyclerView();
        return rv == null ? null : rv.getScrollbar();
    }

    void setupHeader() {
        mHeader.setVisibility(View.VISIBLE);
        boolean tabsHidden = !mUsingTabs;
        mHeader.setup(
                mAH.get(AdapterHolder.MAIN).mRecyclerView,
                mAH.get(AdapterHolder.WORK).mRecyclerView,
                (SearchRecyclerView) mAH.get(AdapterHolder.SEARCH).mRecyclerView,
                getCurrentPage(),
                tabsHidden);

        int padding = mHeader.getMaxTranslation();
        mAH.forEach(adapterHolder -> {
            adapterHolder.mPadding.top = padding;
            adapterHolder.applyPadding();
            if (adapterHolder.mRecyclerView != null) {
                adapterHolder.mRecyclerView.scrollToTop();
            }
        });
    }

    public boolean isHeaderVisible() {
        return mHeader != null && mHeader.getVisibility() == View.VISIBLE;
    }

    /**
     * Adds an update listener to animator that adds springs to the animation.
     */
    public void addSpringFromFlingUpdateListener(ValueAnimator animator,
            float velocity /* release velocity */,
            float progress /* portion of the distance to travel*/) {
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                float distance = (1 - progress) * getHeight(); // px
                float settleVelocity = Math.min(0, distance
                        / (AllAppsTransitionController.INTERP_COEFF * animator.getDuration())
                        + velocity);
                absorbSwipeUpVelocity(Math.max(1000, Math.abs(
                        Math.round(settleVelocity * FLING_VELOCITY_MULTIPLIER))));
            }
        });
    }

    /** Invoked when the container is pulled. */
    public void onPull(float deltaDistance, float displacement) {
        absorbPullDeltaDistance(PULL_MULTIPLIER * deltaDistance, PULL_MULTIPLIER * displacement);
        // Current motion spec is to actually push and not pull
        // on this surface. However, until EdgeEffect.onPush (b/190612804) is
        // implemented at view level, we will simply pull
    }

    @Override
    public void getDrawingRect(Rect outRect) {
        super.getDrawingRect(outRect);
        outRect.offset(0, (int) getTranslationY());
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        invalidateHeader();
    }

    public void setScrimView(ScrimView scrimView) {
        mScrimView = scrimView;
    }

    @Override
    public void drawOnScrim(Canvas canvas) {
        boolean isTablet = mActivityContext.getDeviceProfile().isTablet;

        // Draw full background panel for tablets.
        if (isTablet) {
            mHeaderPaint.setColor(mBottomSheetBackgroundColor);
            View panel = (View) mBottomSheetBackground;
            float translationY = ((View) panel.getParent()).getTranslationY();
            mTmpRectF.set(panel.getLeft(), panel.getTop() + translationY,
                    panel.getRight(), panel.getBottom());
            mTmpPath.reset();
            mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
            canvas.drawPath(mTmpPath, mHeaderPaint);
        }

        if (DEBUG_HEADER_PROTECTION) {
            mHeaderPaint.setColor(Color.MAGENTA);
            mHeaderPaint.setAlpha(255);
        } else {
            mHeaderPaint.setColor(mHeaderColor);
            mHeaderPaint.setAlpha((int) (getAlpha() * Color.alpha(mHeaderColor)));
        }
        if (mHeaderPaint.getColor() == mScrimColor || mHeaderPaint.getColor() == 0) {
            return;
        }
        int bottom = getHeaderBottom() + getVisibleContainerView().getPaddingTop();
        FloatingHeaderView headerView = getFloatingHeaderView();
        if (isTablet) {
            // Start adding header protection if search bar or tabs will attach to the top.
            if (!FeatureFlags.ENABLE_FLOATING_SEARCH_BAR.get() || mUsingTabs) {
                View panel = (View) mBottomSheetBackground;
                float translationY = ((View) panel.getParent()).getTranslationY();
                mTmpRectF.set(panel.getLeft(), panel.getTop() + translationY, panel.getRight(),
                        bottom);
                mTmpPath.reset();
                mTmpPath.addRoundRect(mTmpRectF, mBottomSheetCornerRadii, Direction.CW);
                canvas.drawPath(mTmpPath, mHeaderPaint);
            }
        } else {
            canvas.drawRect(0, 0, canvas.getWidth(), bottom, mHeaderPaint);
        }
        int tabsHeight = headerView.getPeripheralProtectionHeight();
        if (mTabsProtectionAlpha > 0 && tabsHeight != 0) {
            if (DEBUG_HEADER_PROTECTION) {
                mHeaderPaint.setColor(Color.BLUE);
                mHeaderPaint.setAlpha(255);
            } else {
                mHeaderPaint.setAlpha((int) (getAlpha() * mTabsProtectionAlpha));
            }
            int left = 0;
            int right = canvas.getWidth();
            if (isTablet) {
                left = mBottomSheetBackground.getLeft();
                right = mBottomSheetBackground.getRight();
            }
            canvas.drawRect(left, bottom, right, bottom + tabsHeight, mHeaderPaint);
        }
    }

    /**
     * redraws header protection
     */
    public void invalidateHeader() {
        if (mScrimView != null) {
            mScrimView.invalidate();
        }
    }

    protected void updateHeaderScroll(int scrolledOffset) {
        float prog = Utilities.boundToRange((float) scrolledOffset / mHeaderThreshold, 0f, 1f);
        int headerColor = getHeaderColor(prog);
        int tabsAlpha = mHeader.getPeripheralProtectionHeight() == 0 ? 0
                : (int) (Utilities.boundToRange(
                        (scrolledOffset + mHeader.mSnappedScrolledY) / mHeaderThreshold, 0f, 1f)
                        * 255);
        if (headerColor != mHeaderColor || mTabsProtectionAlpha != tabsAlpha) {
            mHeaderColor = headerColor;
            mTabsProtectionAlpha = tabsAlpha;
            invalidateHeader();
        }
    }

    protected int getHeaderColor(float blendRatio) {
        return ColorUtils.blendARGB(mScrimColor, mHeaderProtectionColor, blendRatio);
    }

    protected abstract BaseAllAppsAdapter<T> createAdapter(AlphabeticalAppsList<T> mAppsList,
            BaseAdapterProvider[] adapterProviders);

    public int getHeaderBottom() {
        int bottom = (int) getTranslationY() + mHeader.getClipTop();
        if (FeatureFlags.ENABLE_FLOATING_SEARCH_BAR.get()) {
            if (mActivityContext.getDeviceProfile().isTablet) {
                return bottom + mBottomSheetBackground.getTop();
            }
            return bottom;
        }
        return bottom + mHeader.getTop();
    }

    /**
     * Returns a view that denotes the visible part of all apps container view.
     */
    public View getVisibleContainerView() {
        return mActivityContext.getDeviceProfile().isTablet ? mBottomSheetBackground : this;
    }

    protected void onInitializeRecyclerView(RecyclerView rv) {
        rv.addOnScrollListener(mScrollListener);
    }

    /**
     * Returns {@code true} the All Apps UI is currently being displayed on the target surface and
     * is interactive.
     */
    public abstract boolean isInAllApps();

    /** Holds a {@link BaseAllAppsAdapter} and related fields. */
    public class AdapterHolder {
        public static final int MAIN = 0;
        public static final int WORK = 1;
        public static final int SEARCH = 2;

        private final int mType;
        public final BaseAllAppsAdapter<T> mAdapter;
        final RecyclerView.LayoutManager mLayoutManager;
        final AlphabeticalAppsList<T> mAppsList;
        final Rect mPadding = new Rect();
        AllAppsRecyclerView mRecyclerView;

        AdapterHolder(int type) {
            mType = type;
            mAppsList = new AlphabeticalAppsList<>(mActivityContext,
                    isSearch() ? null : mAllAppsStore,
                    isWork() ? mWorkManager : null);
            BaseAdapterProvider[] adapterProviders =
                    new BaseAdapterProvider[]{mMainAdapterProvider};

            mAdapter = createAdapter(mAppsList, adapterProviders);
            mAppsList.setAdapter(mAdapter);
            mLayoutManager = mAdapter.getLayoutManager();
        }

        void setup(@NonNull View rv, @Nullable Predicate<ItemInfo> matcher) {
            mAppsList.updateItemFilter(matcher);
            mRecyclerView = (AllAppsRecyclerView) rv;
            mRecyclerView.setEdgeEffectFactory(createEdgeEffectFactory());
            mRecyclerView.setApps(mAppsList);
            mRecyclerView.setLayoutManager(mLayoutManager);
            mRecyclerView.setAdapter(mAdapter);
            mRecyclerView.setHasFixedSize(true);
            // No animations will occur when changes occur to the items in this RecyclerView.
            mRecyclerView.setItemAnimator(null);
            onInitializeRecyclerView(mRecyclerView);
            FocusedItemDecorator focusedItemDecorator = new FocusedItemDecorator(mRecyclerView);
            mRecyclerView.addItemDecoration(focusedItemDecorator);
            mAdapter.setIconFocusListener(focusedItemDecorator.getFocusListener());
            applyPadding();
        }

        void applyPadding() {
            if (mRecyclerView != null) {
                int bottomOffset = 0;
                if (isWork() && mWorkManager.getWorkModeSwitch() != null) {
                    bottomOffset = mInsets.bottom + mWorkManager.getWorkModeSwitch().getHeight();
                }
                mRecyclerView.setPadding(mPadding.left, mPadding.top, mPadding.right,
                        mPadding.bottom + bottomOffset);
            }
        }

        private boolean isWork() {
            return mType == WORK;
        }

        private boolean isSearch() {
            return mType == SEARCH;
        }
    }
}
