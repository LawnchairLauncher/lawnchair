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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ClickShadowView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Insettable;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.PromiseAppInfo;
import com.android.launcher3.R;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.TransformingTouchDelegate;
import com.android.launcher3.views.BottomUserEducationView;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * The all apps view container.
 */
public class AllAppsContainerView extends RelativeLayout implements DragSource,
        View.OnLongClickListener, Insettable, DeviceProfile.LauncherLayoutChangeListener,
        BubbleTextView.BubbleTextShadowHandler {

    protected final Rect mBasePadding = new Rect();

    private final Launcher mLauncher;
    private final AdapterHolder[] mAH;
    private final ClickShadowView mTouchFeedbackView;
    private final ItemInfoMatcher mPersonalMatcher = ItemInfoMatcher.ofUser(Process.myUserHandle());
    private final ItemInfoMatcher mWorkMatcher = ItemInfoMatcher.not(mPersonalMatcher);

    private SearchUiManager mSearchUiManager;
    private View mSearchContainer;
    private InterceptingViewPager mViewPager;
    private FloatingHeaderView mHeader;
    private TabsPagerAdapter mTabsPagerAdapter;

    private SpannableStringBuilder mSearchQueryBuilder = null;

    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;

    private TransformingTouchDelegate mTouchDelegate;
    private boolean mUsingTabs;
    private boolean mHasPredictions = false;
    private boolean mSearchModeWhileUsingTabs = false;

    private final HashMap<ComponentKey, AppInfo> mComponentToAppMap = new HashMap<>();

    public AllAppsContainerView(Context context) {
        this(context, null);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);

        mSearchQueryBuilder = new SpannableStringBuilder();

        Selection.setSelection(mSearchQueryBuilder, 0);

        mTouchFeedbackView = new ClickShadowView(context);
        // Make the feedback view large enough to hold the blur bitmap.
        int size = mLauncher.getDeviceProfile().allAppsIconSizePx
                + mTouchFeedbackView.getExtraSize();
        addView(mTouchFeedbackView, size, size);

        mAH = new AdapterHolder[2];
        mAH[AdapterHolder.MAIN] = new AdapterHolder(false /* isWork */);
        mAH[AdapterHolder.WORK] = new AdapterHolder(true /* isWork */);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        DeviceProfile grid = Launcher.getLauncher(getContext()).getDeviceProfile();
        grid.addLauncherLayoutChangedListener(this);

        applyTouchDelegate();
    }

    private void applyTouchDelegate() {
        RecyclerView rv = getActiveRecyclerView();
        mTouchDelegate = new TransformingTouchDelegate(rv);
        mTouchDelegate.setBounds(
                rv.getLeft() - mBasePadding.left,
                rv.getTop() - mBasePadding.top,
                rv.getRight() + mBasePadding.right,
                rv.getBottom() + mBasePadding.bottom);
        setTouchDelegate(mTouchDelegate);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        DeviceProfile grid = Launcher.getLauncher(getContext()).getDeviceProfile();
        grid.removeLauncherLayoutChangedListener(this);
    }

    /**
     * Calculate the background padding as it can change due to insets/content padding change.
     */
    @Override
    public void onLauncherLayoutChanged() {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (!grid.isVerticalBarLayout()) {
            return;
        }

        int[] padding = grid.getContainerPadding();
        int paddingLeft = padding[0];
        int paddingRight = padding[1];
        mBasePadding.set(paddingLeft, 0, paddingRight, 0);
        setPadding(paddingLeft, 0, paddingRight, 0);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        applyTouchDelegate();
    }

    @Override
    public void setPressedIcon(BubbleTextView icon, Bitmap background) {
        mTouchFeedbackView.setPressedIcon(icon, background);
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        boolean hasWorkProfileApp = hasWorkProfileApp(apps);
        if (mUsingTabs != hasWorkProfileApp) {
            rebindAdapters(hasWorkProfileApp);
        }
        mComponentToAppMap.clear();
        addOrUpdateApps(apps);
    }

    /**
     * Adds or updates existing apps in the list
     */
    public void addOrUpdateApps(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            mComponentToAppMap.put(app.toComponentKey(), app);
        }
        onAppsUpdated();
        mSearchUiManager.refreshSearchResult();
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            mComponentToAppMap.remove(app.toComponentKey());
        }
        onAppsUpdated();
        mSearchUiManager.refreshSearchResult();
    }

    private void onAppsUpdated() {
        for (int i = 0; i < getNumOfAdapters(); i++) {
            mAH[i].appsList.onAppsUpdated();
        }
    }

    private int getNumOfAdapters() {
        return mUsingTabs ? mAH.length : 1;
    }

    public void updatePromiseAppProgress(PromiseAppInfo app) {
        for (int i = 0; i < mAH.length; i++) {
            updatePromiseAppProgress(app, mAH[i].recyclerView);
        }
        if (isHeaderVisible()) {
            updatePromiseAppProgress(app, mHeader.getPredictionRow());
        }
    }

    private void updatePromiseAppProgress(PromiseAppInfo app, ViewGroup parent) {
        if (parent == null) {
            return;
        }
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            if (child instanceof BubbleTextView && child.getTag() == app) {
                BubbleTextView bubbleTextView = (BubbleTextView) child;
                bubbleTextView.applyProgressLevel(app.level);
            }
        }
    }

    /**
     * Returns whether the view itself will handle the touch event or not.
     */
    public boolean shouldContainerScroll(MotionEvent ev) {
        // IF the MotionEvent is inside the search box, and the container keeps on receiving
        // touch input, container should move down.
        if (mLauncher.getDragLayer().isEventOverView(mSearchContainer, ev)) {
            return true;
        }
        AllAppsRecyclerView rv = getActiveRecyclerView();
        return rv == null || rv.shouldContainerScroll(ev, mLauncher.getDragLayer());
    }

    public AllAppsRecyclerView getActiveRecyclerView() {
        if (!mUsingTabs || mViewPager.getCurrentItem() == 0) {
            return mAH[AdapterHolder.MAIN].recyclerView;
        } else {
            return mAH[AdapterHolder.WORK].recyclerView;
        }
    }

    /**
     * Resets the state of AllApps.
     */
    public void reset() {
        for (int i = 0; i < mAH.length; i++) {
            if (mAH[i].recyclerView != null) {
                mAH[i].recyclerView.scrollToTop();
            }
        }
        if (isHeaderVisible()) {
            mHeader.reset();
        }
        // Reset the search bar and base recycler view after transitioning home
        mSearchUiManager.reset();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && getActiveRecyclerView() != null) {
                    getActiveRecyclerView().requestFocus();
                }
            }
        });

        mHeader = findViewById(R.id.all_apps_header);
        rebindAdapters(mUsingTabs);

        mSearchContainer = findViewById(R.id.search_container_all_apps);
        mSearchUiManager = (SearchUiManager) mSearchContainer;
        mSearchUiManager.initialize(this);

        onLauncherLayoutChanged();
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        // Update the number of items in the grid before we measure the view
        grid.updateAppsViewNumCols();

        if (mNumAppsPerRow != grid.inv.numColumns ||
                mNumPredictedAppsPerRow != grid.inv.numColumns) {
            mNumAppsPerRow = grid.inv.numColumns;
            mNumPredictedAppsPerRow = grid.inv.numColumns;
            for (int i = 0; i < mAH.length; i++) {
                mAH[i].applyNumsPerRow();
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        mSearchUiManager.preDispatchKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onLongClick(final View v) {
        // When we have exited all apps or are in transition, disregard long clicks
        if (!mLauncher.isInState(LauncherState.ALL_APPS) ||
                mLauncher.getWorkspace().isSwitchingState()) return false;
        // Return if global dragging is not enabled or we are already dragging
        if (!mLauncher.isDraggingEnabled()) return false;
        if (mLauncher.getDragController().isDragging()) return false;

        // Start the drag
        final DragController dragController = mLauncher.getDragController();
        dragController.addDragListener(new DragController.DragListener() {
            @Override
            public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
                v.setVisibility(INVISIBLE);
            }

            @Override
            public void onDragEnd() {
                v.setVisibility(VISIBLE);
                dragController.removeDragListener(this);
            }
        });

        DeviceProfile grid = mLauncher.getDeviceProfile();
        DragOptions options = new DragOptions();
        options.intrinsicIconScaleFactor = (float) grid.allAppsIconSizePx / grid.iconSizePx;
        mLauncher.getWorkspace().beginDragShared(v, this, options);
        return false;
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) { }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        // This is filled in {@link AllAppsRecyclerView}
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].padding.bottom = insets.bottom;
            mAH[i].applyPadding();
        }
        if (grid.isVerticalBarLayout()) {
            ViewGroup.MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
            mlp.leftMargin = insets.left;
            mlp.topMargin = insets.top;
            mlp.rightMargin = insets.right;
            setLayoutParams(mlp);
        } else {
            View navBarBg = findViewById(R.id.nav_bar_bg);
            ViewGroup.LayoutParams navBarBgLp = navBarBg.getLayoutParams();
            navBarBgLp.height = insets.bottom;
            navBarBg.setLayoutParams(navBarBgLp);
        }
    }

    public void updateIconBadges(Set<PackageUserKey> updatedBadges) {
        final PackageUserKey packageUserKey = new PackageUserKey(null, null);
        for (int j = 0; j < mAH.length; j++) {
            if (mAH[j].recyclerView != null) {
                final int n = mAH[j].recyclerView.getChildCount();
                for (int i = 0; i < n; i++) {
                    View child = mAH[j].recyclerView.getChildAt(i);
                    if (!(child instanceof BubbleTextView) || !(child.getTag() instanceof ItemInfo)) {
                        continue;
                    }
                    ItemInfo info = (ItemInfo) child.getTag();
                    if (packageUserKey.updateFromItemInfo(info) && updatedBadges.contains(packageUserKey)) {
                        ((BubbleTextView) child).applyBadgeState(info, true /* animate */);
                    }
                }
            }
        }
    }

    public SpringAnimationHandler getSpringAnimationHandler() {
        return mUsingTabs ? null : mAH[AdapterHolder.MAIN].animationHandler;
    }

    private void rebindAdapters(boolean showTabs) {
        if (showTabs != mUsingTabs) {
            replaceRVContainer(showTabs);
        }
        mUsingTabs = showTabs;

        if (mUsingTabs) {
            mAH[AdapterHolder.MAIN].setup(mViewPager.getChildAt(0), mPersonalMatcher);
            mAH[AdapterHolder.WORK].setup(mViewPager.getChildAt(1), mWorkMatcher);
            setupWorkProfileTabs();
            setupHeader();
        } else {
            mTabsPagerAdapter = null;
            mAH[AdapterHolder.MAIN].setup(findViewById(R.id.apps_list_view), null);
            mAH[AdapterHolder.WORK].recyclerView = null;
            if (FeatureFlags.ALL_APPS_PREDICTION_ROW_VIEW) {
                setupHeader();
            } else {
                mHeader.setVisibility(View.GONE);
            }
        }

        applyTouchDelegate();
    }

    private boolean hasWorkProfileApp(List<AppInfo> apps) {
        if (FeatureFlags.ALL_APPS_TABS_ENABLED) {
            for (AppInfo app : apps) {
                if (mWorkMatcher.matches(app, null)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void replaceRVContainer(boolean showTabs) {
        for (int i = 0; i < mAH.length; i++) {
            if (mAH[i].recyclerView != null) {
                mAH[i].recyclerView.setLayoutManager(null);
            }
        }
        View oldView = getRecyclerViewContainer();
        int index = indexOfChild(oldView);
        removeView(oldView);
        int layout = showTabs ? R.layout.all_apps_tabs : R.layout.all_apps_rv_layout;
        View newView = LayoutInflater.from(getContext()).inflate(layout, this, false);
        addView(newView, index);
        mViewPager = showTabs ? (InterceptingViewPager) newView : null;
    }

    public View getRecyclerViewContainer() {
        return mViewPager != null ? mViewPager : findViewById(R.id.apps_list_view);
    }

    private void setupWorkProfileTabs() {
        if (mTabsPagerAdapter != null) {
            return;
        }
        final PersonalWorkSlidingTabStrip tabs = findViewById(R.id.tabs);
        mViewPager.setAdapter(mTabsPagerAdapter = new TabsPagerAdapter());
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                tabs.updateIndicatorPosition(position, positionOffset);
            }

            @Override
            public void onPageSelected(int pos) {
                tabs.updateTabTextColor(pos);
                mHeader.setMainActive(pos == 0);
                reset();
                applyTouchDelegate();
                if (mAH[pos].recyclerView != null) {
                    mAH[pos].recyclerView.bindFastScrollbar();
                }
                if (pos == AdapterHolder.WORK) {
                    BottomUserEducationView.showIfNeeded(mLauncher);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
        mAH[AdapterHolder.MAIN].recyclerView.bindFastScrollbar();

        findViewById(R.id.tab_personal)
                .setOnClickListener((View view) -> mViewPager.setCurrentItem(0));
        findViewById(R.id.tab_work)
                .setOnClickListener((View view) -> mViewPager.setCurrentItem(1));
    }

    public void setPredictedApps(List<ComponentKeyMapper<AppInfo>> apps) {
        if (isHeaderVisible()) {
            mHeader.getPredictionRow().setPredictedApps(apps);
        }
        mAH[AdapterHolder.MAIN].appsList.setPredictedApps(apps);
        boolean hasPredictions = !apps.isEmpty();
        if (mHasPredictions != hasPredictions) {
            mHasPredictions = hasPredictions;
            if (FeatureFlags.ALL_APPS_PREDICTION_ROW_VIEW) {
                setupHeader();
            }
        }
    }

    public AppInfo findApp(ComponentKeyMapper<AppInfo> mapper) {
        return mapper.getItem(mComponentToAppMap);
    }

    public AlphabeticalAppsList getApps() {
        return mAH[AdapterHolder.MAIN].appsList;
    }

    public boolean isUsingTabs() {
        return mUsingTabs;
    }

    public FloatingHeaderView getFloatingHeaderView() {
        return mHeader;
    }

    private void setupHeader() {
        if (mHeader == null) {
            return;
        }
        mHeader.setVisibility(View.VISIBLE);
        mHeader.setup(mAH, mComponentToAppMap, mNumPredictedAppsPerRow);

        int padding = mHeader.getPredictionRow().getExpectedHeight();
        if (mHasPredictions && !mUsingTabs) {
            padding += mHeader.getPaddingTop() + mHeader.getPaddingBottom();
        }
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].paddingTopForTabs = padding;
            mAH[i].applyPadding();
        }
    }

    public void setLastSearchQuery(String query) {
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].adapter.setLastSearchQuery(query);
        }
        boolean hasQuery = !TextUtils.isEmpty(query);
        if (mUsingTabs && hasQuery) {
            mSearchModeWhileUsingTabs = true;
            rebindAdapters(false); // hide tabs
        } else if (mSearchModeWhileUsingTabs && !hasQuery) {
            mSearchModeWhileUsingTabs = false;
            rebindAdapters(true); // show tabs
        }
    }

    public void onSearchResultsChanged() {
        for (int i = 0; i < mAH.length; i++) {
            if (mAH[i].recyclerView != null) {
                mAH[i].recyclerView.onSearchResultsChanged();
            }
        }
    }

    public void setRecyclerViewPaddingTop(int top) {
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].padding.top = top;
            mAH[i].applyPadding();
        }
    }

    public void setRecyclerViewSidePadding(int left, int right) {
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].padding.left = left;
            mAH[i].padding.right = right;
            mAH[i].applyPadding();
        }
    }

    public void setRecyclerViewVerticalFadingEdgeEnabled(boolean enabled) {
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].applyVerticalFadingEdgeEnabled(enabled);
        }
    }

    public void addElevationController(RecyclerView.OnScrollListener scrollListener) {
        if (!mUsingTabs) {
            mAH[AdapterHolder.MAIN].recyclerView.addOnScrollListener(scrollListener);
        }
    }

    public List<AppInfo> getPredictedApps() {
        if (mUsingTabs) {
            return mHeader.getPredictionRow().getPredictedApps();
        } else {
            return mAH[AdapterHolder.MAIN].appsList.getPredictedApps();
        }
    }

    private boolean isHeaderVisible() {
        return mHeader != null && mHeader.getVisibility() == View.VISIBLE;
    }

    public class AdapterHolder {
        public static final int MAIN = 0;
        public static final int WORK = 1;

        final AllAppsGridAdapter adapter;
        final LinearLayoutManager layoutManager;
        final SpringAnimationHandler animationHandler;
        final AlphabeticalAppsList appsList;
        final Rect padding = new Rect();
        int paddingTopForTabs;
        AllAppsRecyclerView recyclerView;
        boolean verticalFadingEdge;

        AdapterHolder(boolean isWork) {
            appsList = new AlphabeticalAppsList(mLauncher, mComponentToAppMap, isWork);
            adapter = new AllAppsGridAdapter(mLauncher, appsList, mLauncher,
                    AllAppsContainerView.this, true);
            appsList.setAdapter(adapter);
            animationHandler = adapter.getSpringAnimationHandler();
            layoutManager = adapter.getLayoutManager();
        }

        void setup(@NonNull View rv, @Nullable ItemInfoMatcher matcher) {
            appsList.updateItemFilter(matcher);
            recyclerView = (AllAppsRecyclerView) rv;
            recyclerView.setApps(appsList, mUsingTabs);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setAdapter(adapter);
            recyclerView.setHasFixedSize(true);
            // No animations will occur when changes occur to the items in this RecyclerView.
            recyclerView.setItemAnimator(null);
            if (FeatureFlags.LAUNCHER3_PHYSICS && animationHandler != null) {
                recyclerView.setSpringAnimationHandler(animationHandler);
            }
            FocusedItemDecorator focusedItemDecorator = new FocusedItemDecorator(recyclerView);
            recyclerView.addItemDecoration(focusedItemDecorator);
            recyclerView.preMeasureViews(adapter);
            adapter.setIconFocusListener(focusedItemDecorator.getFocusListener());
            applyVerticalFadingEdgeEnabled(verticalFadingEdge);
            applyPadding();
            applyNumsPerRow();
        }

        void applyPadding() {
            if (recyclerView != null) {
                int paddingTop = mUsingTabs || FeatureFlags.ALL_APPS_PREDICTION_ROW_VIEW
                        ? paddingTopForTabs : padding.top;
                recyclerView.setPadding(padding.left, paddingTop, padding.right, padding.bottom);
            }
            if (isHeaderVisible()) {
                mHeader.getPredictionRow()
                        .setPadding(padding.left, 0 , padding.right, 0);
            }
        }

        void applyNumsPerRow() {
            if (mNumAppsPerRow > 0) {
                if (recyclerView != null) {
                    recyclerView.setNumAppsPerRow(mLauncher.getDeviceProfile(), mNumAppsPerRow);
                }
                adapter.setNumAppsPerRow(mNumAppsPerRow);
                appsList.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow);
                if (isHeaderVisible()) {
                    mHeader.getPredictionRow()
                            .setNumAppsPerRow(mNumPredictedAppsPerRow);
                }
            }
        }

        public void applyVerticalFadingEdgeEnabled(boolean enabled) {
            verticalFadingEdge = enabled;
            mAH[AdapterHolder.MAIN].recyclerView.setVerticalFadingEdgeEnabled(!mUsingTabs
                    && verticalFadingEdge);
        }
    }

    private class TabsPagerAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            if (position == 0) {
                return mAH[AdapterHolder.MAIN].recyclerView;
            } else {
                return mAH[AdapterHolder.WORK].recyclerView;
            }
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                return getResources().getString(R.string.all_apps_personal_tab);
            } else {
                return getResources().getString(R.string.all_apps_work_tab);
            }
        }
    }

}
