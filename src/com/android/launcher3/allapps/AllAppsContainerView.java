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
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.support.v7.widget.LinearLayoutManager;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BaseContainerView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeleteDropTarget;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Insettable;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.PromiseAppInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.PackageUserKey;

import java.util.List;
import java.util.Set;

/**
 * The all apps view container.
 */
public class AllAppsContainerView extends BaseContainerView implements DragSource,
        View.OnLongClickListener, Insettable {

    private final Launcher mLauncher;
    private final AlphabeticalAppsList mApps;
    private final AllAppsGridAdapter mAdapter;
    private final LinearLayoutManager mLayoutManager;

    private AllAppsRecyclerView mAppsRecyclerView;
    private SearchUiManager mSearchUiManager;
    private View mSearchContainer;

    private SpannableStringBuilder mSearchQueryBuilder = null;

    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;

    private SpringAnimationHandler mSpringAnimationHandler;

    public AllAppsContainerView(Context context) {
        this(context, null);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);
        mApps = new AlphabeticalAppsList(context);
        mAdapter = new AllAppsGridAdapter(mLauncher, mApps, mLauncher, this);
        mSpringAnimationHandler = mAdapter.getSpringAnimationHandler();
        mApps.setAdapter(mAdapter);
        mLayoutManager = mAdapter.getLayoutManager();
        mSearchQueryBuilder = new SpannableStringBuilder();

        Selection.setSelection(mSearchQueryBuilder, 0);
    }

    @Override
    protected void updateBackground(
            int paddingLeft, int paddingTop, int paddingRight, int paddingBottom) {
        if (mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            getRevealView().setBackground(new InsetDrawable(mBaseDrawable,
                    paddingLeft, paddingTop, paddingRight, paddingBottom));
            getContentView().setBackground(
                    new InsetDrawable(new ColorDrawable(Color.TRANSPARENT),
                            paddingLeft, paddingTop, paddingRight, paddingBottom));
        } else {
            getRevealView().setBackground(mBaseDrawable);
        }
    }

    /**
     * Sets the current set of predicted apps.
     */
    public void setPredictedApps(List<ComponentKeyMapper<AppInfo>> apps) {
        mApps.setPredictedApps(apps);
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        mApps.setApps(apps);
    }

    /**
     * Adds or updates existing apps in the list
     */
    public void addOrUpdateApps(List<AppInfo> apps) {
        mApps.addOrUpdateApps(apps);
        mSearchUiManager.refreshSearchResult();
    }

    public void updatePromiseAppProgress(PromiseAppInfo app) {
        int childCount = mAppsRecyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mAppsRecyclerView.getChildAt(i);
            if (child instanceof BubbleTextView && child.getTag() == app) {
                BubbleTextView bubbleTextView = (BubbleTextView) child;
                bubbleTextView.applyProgressLevel(app.level);
            }
        }
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        mApps.removeApps(apps);
        mSearchUiManager.refreshSearchResult();
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

        int[] point = new int[2];
        point[0] = (int) ev.getX();
        point[1] = (int) ev.getY();
        Utilities.mapCoordInSelfToDescendant(
                mAppsRecyclerView.getScrollBar(), mLauncher.getDragLayer(), point);
        // IF the MotionEvent is inside the thumb, container should not be pulled down.
        if (mAppsRecyclerView.getScrollBar().shouldBlockIntercept(point[0], point[1])) {
            return false;
        }

        // IF scroller is at the very top OR there is no scroll bar because there is probably not
        // enough items to scroll, THEN it's okay for the container to be pulled down.
        if (mAppsRecyclerView.getCurrentScrollY() == 0) {
            return true;
        }
        return false;
    }

    /**
     * Focuses the search field and begins an app search.
     */
    public void startAppsSearch() {
        mSearchUiManager.startAppsSearch();
    }

    /**
     * Resets the state of AllApps.
     */
    public void reset() {
        // Reset the search bar and base recycler view after transitioning home
        mAppsRecyclerView.scrollToTop();
        mSearchUiManager.reset();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        getContentView().setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mAppsRecyclerView.requestFocus();
                }
            }
        });

        // Load the all apps recycler view
        mAppsRecyclerView = findViewById(R.id.apps_list_view);
        mAppsRecyclerView.setApps(mApps);
        mAppsRecyclerView.setLayoutManager(mLayoutManager);
        mAppsRecyclerView.setAdapter(mAdapter);
        mAppsRecyclerView.setHasFixedSize(true);
        // No animations will occur when changes occur to the items in this RecyclerView.
        mAppsRecyclerView.setItemAnimator(null);
        if (FeatureFlags.LAUNCHER3_PHYSICS) {
            mAppsRecyclerView.setSpringAnimationHandler(mSpringAnimationHandler);
        }

        mSearchContainer = findViewById(R.id.search_container_all_apps);
        mSearchUiManager = (SearchUiManager) mSearchContainer;
        mSearchUiManager.initialize(mApps, mAppsRecyclerView);


        FocusedItemDecorator focusedItemDecorator = new FocusedItemDecorator(mAppsRecyclerView);
        mAppsRecyclerView.addItemDecoration(focusedItemDecorator);
        mAppsRecyclerView.preMeasureViews(mAdapter);
        mAdapter.setIconFocusListener(focusedItemDecorator.getFocusListener());

        getRevealView().setVisibility(View.VISIBLE);
        getContentView().setVisibility(View.VISIBLE);
        getContentView().setBackground(null);
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    @Override
    public View getTouchDelegateTargetView() {
        return mAppsRecyclerView;
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

            mAppsRecyclerView.setNumAppsPerRow(grid, mNumAppsPerRow);
            mAdapter.setNumAppsPerRow(mNumAppsPerRow);
            mApps.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow);
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
        if (!mLauncher.isAppsViewVisible() ||
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
        mLauncher.getWorkspace().beginDragShared(v, this, new DragOptions());
        return false;
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        return true;
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return false;
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        return (float) grid.allAppsIconSizePx / grid.iconSizePx;
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean isFlingToDelete,
            boolean success) {
        if (isFlingToDelete || !success || (target != mLauncher.getWorkspace() &&
                !(target instanceof DeleteDropTarget) && !(target instanceof Folder))) {
            // Exit spring loaded mode if we have not successfully dropped or have not handled the
            // drop in Workspace
            mLauncher.exitSpringLoadedDragModeDelayed(true,
                    Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
        }
        mLauncher.unlockScreenOrientation(false);

        if (!success) {
            d.deferDragViewCleanupPostAnimation = false;
        }
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        // This is filled in {@link AllAppsRecyclerView}
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        mAppsRecyclerView.setPadding(
                mAppsRecyclerView.getPaddingLeft(), mAppsRecyclerView.getPaddingTop(),
                mAppsRecyclerView.getPaddingRight(), insets.bottom);

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
        final int n = mAppsRecyclerView.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = mAppsRecyclerView.getChildAt(i);
            if (!(child instanceof BubbleTextView) || !(child.getTag() instanceof ItemInfo)) {
                continue;
            }
            ItemInfo info = (ItemInfo) child.getTag();
            if (packageUserKey.updateFromItemInfo(info) && updatedBadges.contains(packageUserKey)) {
                ((BubbleTextView) child).applyBadgeState(info, true /* animate */);
            }
        }
    }

    public SpringAnimationHandler getSpringAnimationHandler() {
        return mSpringAnimationHandler;
    }
}
