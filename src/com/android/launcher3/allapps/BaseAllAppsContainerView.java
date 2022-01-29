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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Insettable;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.views.SpringRelativeLayout;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip.OnActivePageChangedListener;

import java.util.Arrays;
import java.util.List;

/**
 * Base all apps view container.
 *
 * @param <T> Type of context inflating all apps.
 */
public abstract class BaseAllAppsContainerView<T extends Context & ActivityContext> extends
        SpringRelativeLayout implements DragSource, Insettable, OnDeviceProfileChangeListener,
        OnActivePageChangedListener, ScrimView.ScrimDrawingController {

    private static final String BUNDLE_KEY_CURRENT_PAGE = "launcher.allapps.current_page";

    public static final float PULL_MULTIPLIER = .02f;
    public static final float FLING_VELOCITY_MULTIPLIER = 1200f;

    private final Paint mHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mInsets = new Rect();

    /** Context of an activity or window that is inflating this container. */
    protected final T mActivityContext;
    protected final List<AdapterHolder> mAH;
    protected final ItemInfoMatcher mPersonalMatcher = ItemInfoMatcher.ofUser(
            Process.myUserHandle());
    private final SearchAdapterProvider<?> mMainAdapterProvider;
    private final AllAppsStore mAllAppsStore = new AllAppsStore();

    private final RecyclerView.OnScrollListener mScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateHeaderScroll(((AllAppsRecyclerView) recyclerView).getCurrentScrollY());
                }
            };
    private final WorkProfileManager mWorkManager;

    private final Paint mNavBarScrimPaint;
    private int mNavBarScrimHeight = 0;

    private AllAppsPagedView mViewPager;

    protected FloatingHeaderView mHeader;

    protected boolean mUsingTabs;
    private boolean mHasWorkApps;

    protected RecyclerViewFastScroller mTouchHandler;
    protected final Point mFastScrollerOffset = new Point();

    private final int mScrimColor;
    private final int mHeaderProtectionColor;
    protected final float mHeaderThreshold;
    private ScrimView mScrimView;
    private int mHeaderColor;
    private int mTabsProtectionAlpha;

    protected BaseAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);
        mMainAdapterProvider = createMainAdapterProvider();

        mScrimColor = Themes.getAttrColor(context, R.attr.allAppsScrimColor);
        mHeaderThreshold = getResources().getDimensionPixelSize(
                R.dimen.dynamic_grid_cell_border_spacing);
        mHeaderProtectionColor = Themes.getAttrColor(context, R.attr.allappsHeaderProtectionColor);

        mWorkManager = new WorkProfileManager(
                mActivityContext.getSystemService(UserManager.class),
                this,
                Utilities.getPrefs(mActivityContext));
        mAH = Arrays.asList(null, null);
        mAH.set(AdapterHolder.MAIN, new AdapterHolder(false /* isWork */));
        mAH.set(AdapterHolder.WORK, new AdapterHolder(true /* isWork */));

        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(Themes.getAttrColor(context, R.attr.allAppsNavBarScrimColor));

        mAllAppsStore.addUpdateListener(this::onAppsUpdated);

        updateBackground(mActivityContext.getDeviceProfile());
    }

    /** Creates the adapter provider for the main section. */
    protected abstract SearchAdapterProvider<?> createMainAdapterProvider();

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
            if (currentPage != 0 && mViewPager != null) {
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
            holder.adapter.setOnIconLongClickListener(listener);
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
            holder.adapter.setAppsPerRow(dp.numShownAllAppsColumns);
            if (holder.mRecyclerView != null) {
                // Remove all views and clear the pool, while keeping the data same. After this
                // call, all the viewHolders will be recreated.
                holder.mRecyclerView.swapAdapter(holder.mRecyclerView.getAdapter(), true);
                holder.mRecyclerView.getRecycledViewPool().clear();
            }
        }
        updateBackground(dp);
    }

    private void updateBackground(DeviceProfile deviceProfile) {
        setBackground(deviceProfile.isTablet
                ? getContext().getDrawable(R.drawable.bg_all_apps_bottom_sheet)
                : null);
    }

    private void onAppsUpdated() {
        boolean hasWorkApps = false;
        for (AppInfo app : mAllAppsStore.getApps()) {
            if (mWorkManager.getMatcher().matches(app, null)) {
                hasWorkApps = true;
                break;
            }
        }
        mHasWorkApps = hasWorkApps;
        if (!mAH.get(AdapterHolder.MAIN).mAppsList.hasFilter()) {
            rebindAdapters();
            if (hasWorkApps) {
                mWorkManager.reset();
            }
        }
    }

    /**
     * Returns whether the view itself will handle the touch event or not.
     */
    public boolean shouldContainerScroll(MotionEvent ev) {
        // Scroll if not within the container view (e.g. over large-screen scrim).
        if (!mActivityContext.getDragLayer().isEventOverView(this, ev)) {
            return true;
        }
        // TODO(b/216203409) Support dragging down from bottom sheet divider, if present.
        AllAppsRecyclerView rv = getActiveRecyclerView();
        if (rv == null) {
            return true;
        }
        if (rv.getScrollbar().getThumbOffsetY() >= 0
                && mActivityContext.getDragLayer().isEventOverView(rv.getScrollbar(), ev)) {
            return false;
        }
        return rv.shouldContainerScroll(ev, mActivityContext.getDragLayer());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(),
                    mFastScrollerOffset)) {
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
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(),
                    mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            } else {
                mTouchHandler = null;

            }
        }
        if (mTouchHandler != null) {
            mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
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

    /** The current recycler view visible in the container. */
    public AllAppsRecyclerView getActiveRecyclerView() {
        if (!mUsingTabs || isPersonalTab()) {
            return mAH.get(AdapterHolder.MAIN).mRecyclerView;
        } else {
            return mAH.get(AdapterHolder.WORK).mRecyclerView;
        }
    }

    protected boolean isPersonalTab() {
        return mViewPager.getNextPage() == 0;
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
    protected void onFinishInflate() {
        super.onFinishInflate();

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && getActiveRecyclerView() != null) {
                getActiveRecyclerView().requestFocus();
            }
        });

        mHeader = findViewById(R.id.all_apps_header);
        rebindAdapters(true /* force */);
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile grid = mActivityContext.getDeviceProfile();

        for (int i = 0; i < mAH.size(); i++) {
            mAH.get(i).mPadding.bottom = insets.bottom;
            mAH.get(i).mPadding.left = mAH.get(i).mPadding.right = grid.allAppsLeftRightPadding;
            mAH.get(i).applyPadding();
        }

        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        mlp.topMargin = grid.isTablet ? insets.top : 0;
        int leftRightMargin = grid.allAppsLeftRightMargin;
        mlp.leftMargin = insets.left + leftRightMargin;
        mlp.rightMargin = insets.right + leftRightMargin;
        setLayoutParams(mlp);

        if (grid.isVerticalBarLayout()) {
            setPadding(grid.workspacePadding.left, 0, grid.workspacePadding.right, 0);
        } else {
            setPadding(0, 0, 0, 0);
        }

        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        if (Utilities.ATLEAST_Q) {
            mNavBarScrimHeight = insets.getTappableElementInsets().bottom;
        } else {
            mNavBarScrimHeight = insets.getStableInsetBottom();
        }
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
        boolean showTabs = showTabs();
        if (showTabs == mUsingTabs && !force) {
            return;
        }
        replaceRVContainer(showTabs);
        mUsingTabs = showTabs;

        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.MAIN).mRecyclerView);
        mAllAppsStore.unregisterIconContainer(mAH.get(AdapterHolder.WORK).mRecyclerView);

        if (mUsingTabs) {
            mAH.get(AdapterHolder.MAIN).setup(mViewPager.getChildAt(0), mPersonalMatcher);
            mAH.get(AdapterHolder.WORK).setup(mViewPager.getChildAt(1), mWorkManager.getMatcher());
            mAH.get(AdapterHolder.WORK).mRecyclerView.setId(R.id.apps_list_view_work);
            mViewPager.getPageIndicator().setActiveMarker(AdapterHolder.MAIN);
            findViewById(R.id.tab_personal)
                    .setOnClickListener((View view) -> {
                        if (mViewPager.snapToPage(AdapterHolder.MAIN)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_PERSONAL_TAB);
                        }
                    });
            findViewById(R.id.tab_work)
                    .setOnClickListener((View view) -> {
                        if (mViewPager.snapToPage(AdapterHolder.WORK)) {
                            mActivityContext.getStatsLogManager().logger()
                                    .log(LAUNCHER_ALLAPPS_TAP_ON_WORK_TAB);
                        }
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
    }

    private void setDeviceManagementResources() {
        if (mActivityContext.getStringCache() != null) {
            Button personalTab = findViewById(R.id.tab_personal);
            personalTab.setText(mActivityContext.getStringCache().allAppsPersonalTab);

            Button workTab = findViewById(R.id.tab_work);
            workTab.setText(mActivityContext.getStringCache().allAppsWorkTab);
        }
    }

    protected boolean showTabs() {
        return mHasWorkApps;
    }

    private void replaceRVContainer(boolean showTabs) {
        for (AdapterHolder adapterHolder : mAH) {
            if (adapterHolder.mRecyclerView != null) {
                adapterHolder.mRecyclerView.setLayoutManager(null);
                adapterHolder.mRecyclerView.setAdapter(null);
            }
        }
        View oldView = getRecyclerViewContainer();
        int index = indexOfChild(oldView);
        removeView(oldView);
        int layout = showTabs ? R.layout.all_apps_tabs : R.layout.all_apps_rv_layout;
        View newView = getLayoutInflater().inflate(layout, this, false);
        addView(newView, index);
        if (showTabs) {
            mViewPager = (AllAppsPagedView) newView;
            mViewPager.initParentViews(this);
            mViewPager.getPageIndicator().setOnActivePageChangedListener(this);
            if (mWorkManager.attachWorkModeSwitch()) {
                mWorkManager.getWorkModeSwitch().post(
                        () -> mAH.get(AdapterHolder.WORK).applyPadding());
            }
        } else {
            mWorkManager.detachWorkModeSwitch();
            mViewPager = null;
        }
    }

    public View getRecyclerViewContainer() {
        return mViewPager != null ? mViewPager : findViewById(R.id.apps_list_view);
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        mHeader.setMainActive(currentActivePage == AdapterHolder.MAIN);
        if (mAH.get(currentActivePage).mRecyclerView != null) {
            mAH.get(currentActivePage).mRecyclerView.bindFastScrollbar();
        }
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

    public AlphabeticalAppsList<T> getApps() {
        return mAH.get(AdapterHolder.MAIN).mAppsList;
    }

    public FloatingHeaderView getFloatingHeaderView() {
        return mHeader;
    }

    @VisibleForTesting
    public View getContentView() {
        return mViewPager == null ? getActiveRecyclerView() : mViewPager;
    }

    /** The current page visible in all apps. */
    public int getCurrentPage() {
        return mViewPager != null ? mViewPager.getCurrentPage() : AdapterHolder.MAIN;
    }

    /** The scroll bar for the active recycler view. */
    public RecyclerViewFastScroller getScrollBar() {
        AllAppsRecyclerView rv = getActiveRecyclerView();
        return rv == null ? null : rv.getScrollbar();
    }

    void setupHeader() {
        mHeader.setVisibility(View.VISIBLE);
        mHeader.setup(mAH, mAH.get(AdapterHolder.WORK).mRecyclerView == null);

        int padding = mHeader.getMaxTranslation();
        for (int i = 0; i < mAH.size(); i++) {
            mAH.get(i).mPadding.top = padding;
            mAH.get(i).applyPadding();
            if (mAH.get(i).mRecyclerView != null) {
                mAH.get(i).mRecyclerView.scrollToTop();
            }
        }
    }

    /** @see View#setVerticalFadingEdgeEnabled(boolean). */
    public void setRecyclerViewVerticalFadingEdgeEnabled(boolean enabled) {
        for (int i = 0; i < mAH.size(); i++) {
            mAH.get(i).applyVerticalFadingEdgeEnabled(enabled);
        }
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
                float distance = (float) ((1 - progress) * getHeight()); // px
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
        if (!mHeader.isHeaderProtectionSupported()) return;
        mHeaderPaint.setColor(mHeaderColor);
        mHeaderPaint.setAlpha((int) (getAlpha() * Color.alpha(mHeaderColor)));
        if (mHeaderPaint.getColor() != mScrimColor && mHeaderPaint.getColor() != 0) {
            int bottom = getHeaderBottom();
            canvas.drawRect(0, 0, canvas.getWidth(), bottom, mHeaderPaint);
            int tabsHeight = getFloatingHeaderView().getPeripheralProtectionHeight();
            if (mTabsProtectionAlpha > 0 && tabsHeight != 0) {
                mHeaderPaint.setAlpha((int) (getAlpha() * mTabsProtectionAlpha));
                canvas.drawRect(0, bottom, canvas.getWidth(), bottom + tabsHeight, mHeaderPaint);
            }
        }
    }

    /**
     * redraws header protection
     */
    public void invalidateHeader() {
        if (mScrimView != null && mHeader.isHeaderProtectionSupported()) {
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

    protected int getHeaderBottom() {
        return (int) getTranslationY();
    }

    /** Holds a {@link AllAppsGridAdapter} and related fields. */
    public class AdapterHolder {
        public static final int MAIN = 0;
        public static final int WORK = 1;

        private final boolean mIsWork;
        public final AllAppsGridAdapter<T> adapter;
        final LinearLayoutManager mLayoutManager;
        final AlphabeticalAppsList<T> mAppsList;
        final Rect mPadding = new Rect();
        AllAppsRecyclerView mRecyclerView;
        boolean mVerticalFadingEdge;

        AdapterHolder(boolean isWork) {
            mIsWork = isWork;
            mAppsList = new AlphabeticalAppsList<>(mActivityContext, mAllAppsStore,
                    isWork ? mWorkManager.getAdapterProvider() : null);

            BaseAdapterProvider[] adapterProviders =
                    isWork ? new BaseAdapterProvider[]{mMainAdapterProvider,
                            mWorkManager.getAdapterProvider()}
                            : new BaseAdapterProvider[]{mMainAdapterProvider};

            adapter = new AllAppsGridAdapter<>(mActivityContext, getLayoutInflater(), mAppsList,
                    adapterProviders);
            mAppsList.setAdapter(adapter);
            mLayoutManager = adapter.getLayoutManager();
        }

        void setup(@NonNull View rv, @Nullable ItemInfoMatcher matcher) {
            mAppsList.updateItemFilter(matcher);
            mRecyclerView = (AllAppsRecyclerView) rv;
            mRecyclerView.setEdgeEffectFactory(createEdgeEffectFactory());
            mRecyclerView.setApps(mAppsList);
            mRecyclerView.setLayoutManager(mLayoutManager);
            mRecyclerView.setAdapter(adapter);
            mRecyclerView.setHasFixedSize(true);
            // No animations will occur when changes occur to the items in this RecyclerView.
            mRecyclerView.setItemAnimator(null);
            mRecyclerView.addOnScrollListener(mScrollListener);
            FocusedItemDecorator focusedItemDecorator = new FocusedItemDecorator(mRecyclerView);
            mRecyclerView.addItemDecoration(focusedItemDecorator);
            adapter.setIconFocusListener(focusedItemDecorator.getFocusListener());
            applyVerticalFadingEdgeEnabled(mVerticalFadingEdge);
            applyPadding();
        }

        void applyPadding() {
            if (mRecyclerView != null) {
                int bottomOffset = 0;
                if (mIsWork && mWorkManager.getWorkModeSwitch() != null) {
                    bottomOffset = mInsets.bottom + mWorkManager.getWorkModeSwitch().getHeight();
                }
                mRecyclerView.setPadding(mPadding.left, mPadding.top, mPadding.right,
                        mPadding.bottom + bottomOffset);
            }
        }

        private void applyVerticalFadingEdgeEnabled(boolean enabled) {
            mVerticalFadingEdge = enabled;
            mAH.get(AdapterHolder.MAIN).mRecyclerView.setVerticalFadingEdgeEnabled(!mUsingTabs
                    && mVerticalFadingEdge);
        }
    }
}
