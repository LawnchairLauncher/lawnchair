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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
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
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Insettable;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.discovery.AppDiscoveryItem;
import com.android.launcher3.discovery.AppDiscoveryUpdateState;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.graphics.TintedDrawableSpan;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The all apps view container.
 */
public class AllAppsContainerView extends BaseContainerView implements DragSource,
        View.OnLongClickListener, AllAppsSearchBarController.Callbacks, Insettable {

    private final Launcher mLauncher;
    private final AlphabeticalAppsList mApps;
    private final AllAppsGridAdapter mAdapter;
    private final RecyclerView.LayoutManager mLayoutManager;

    private AllAppsRecyclerView mAppsRecyclerView;
    private AllAppsSearchBarController mSearchBarController;

    private View mSearchContainer;
    private int mSearchContainerMinHeight;
    private ExtendedEditText mSearchInput;
    private HeaderElevationController mElevationController;

    private SpannableStringBuilder mSearchQueryBuilder = null;

    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;

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
        mApps.setAdapter(mAdapter);
        mLayoutManager = mAdapter.getLayoutManager();
        mSearchQueryBuilder = new SpannableStringBuilder();
        mSearchContainerMinHeight
                = getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_height);

        Selection.setSelection(mSearchQueryBuilder, 0);
    }

    @Override
    protected void updateBackground(
            int paddingLeft, int paddingTop, int paddingRight, int paddingBottom) {
        if (FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP) {
            if (mLauncher.getDeviceProfile().isVerticalBarLayout()) {
                getRevealView().setBackground(new InsetDrawable(mBaseDrawable,
                        paddingLeft, paddingTop, paddingRight, paddingBottom));
                getContentView().setBackground(
                        new InsetDrawable(new ColorDrawable(Color.TRANSPARENT),
                                paddingLeft, paddingTop, paddingRight, paddingBottom));
            } else {
                getRevealView().setBackground(mBaseDrawable);
            }
        } else {
            super.updateBackground(paddingLeft, paddingTop, paddingRight, paddingBottom);
        }
    }

    /**
     * Sets the current set of predicted apps.
     */
    public void setPredictedApps(List<ComponentKey> apps) {
        mApps.setPredictedApps(apps);
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        mApps.setApps(apps);
    }

    /**
     * Adds new apps to the list.
     */
    public void addApps(List<AppInfo> apps) {
        mApps.addApps(apps);
        mSearchBarController.refreshSearchResult();
    }

    /**
     * Updates existing apps in the list
     */
    public void updateApps(List<AppInfo> apps) {
        mApps.updateApps(apps);
        mSearchBarController.refreshSearchResult();
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        mApps.removeApps(apps);
        mSearchBarController.refreshSearchResult();
    }

    public void setSearchBarVisible(boolean visible) {
        if (visible) {
            mSearchBarController.setVisibility(View.VISIBLE);
        } else {
            mSearchBarController.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Sets the search bar that shows above the a-z list.
     */
    public void setSearchBarController(AllAppsSearchBarController searchController) {
        if (mSearchBarController != null) {
            throw new RuntimeException("Expected search bar controller to only be set once");
        }
        mSearchBarController = searchController;
        mSearchBarController.initialize(mApps, mSearchInput, mLauncher, this);
        mAdapter.setSearchController(mSearchBarController);
    }

    /**
     * Scrolls this list view to the top.
     */
    public void scrollToTop() {
        mAppsRecyclerView.scrollToTop();
    }

    /**
     * Returns whether the view itself will handle the touch event or not.
     */
    public boolean shouldContainerScroll(MotionEvent ev) {
        int[] point = new int[2];
        point[0] = (int) ev.getX();
        point[1] = (int) ev.getY();
        Utilities.mapCoordInSelfToDescendant(mAppsRecyclerView, this, point);

        // IF the MotionEvent is inside the search box, and the container keeps on receiving
        // touch input, container should move down.
        if (mLauncher.getDragLayer().isEventOverView(mSearchContainer, ev)) {
            return true;
        }

        // IF the MotionEvent is inside the thumb, container should not be pulled down.
        if (mAppsRecyclerView.getScrollBar().isNearThumb(point[0], point[1])) {
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
        if (mSearchBarController != null) {
            mSearchBarController.focusSearchField();
        }
    }

    /**
     * Resets the state of AllApps.
     */
    public void reset() {
        // Reset the search bar and base recycler view after transitioning home
        scrollToTop();
        mSearchBarController.reset();
        mAppsRecyclerView.reset();
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

        mSearchContainer = findViewById(R.id.search_container);
        mSearchInput = (ExtendedEditText) findViewById(R.id.search_box_input);

        // Update the hint to contain the icon.
        // Prefix the original hint with two spaces. The first space gets replaced by the icon
        // using span. The second space is used for a singe space character between the hint
        // and the icon.
        SpannableString spanned = new SpannableString("  " + mSearchInput.getHint());
        spanned.setSpan(new TintedDrawableSpan(getContext(), R.drawable.ic_allapps_search),
                0, 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        mSearchInput.setHint(spanned);

        mElevationController = new HeaderElevationController(mSearchContainer);

        // Load the all apps recycler view
        mAppsRecyclerView = (AllAppsRecyclerView) findViewById(R.id.apps_list_view);
        mAppsRecyclerView.setApps(mApps);
        mAppsRecyclerView.setLayoutManager(mLayoutManager);
        mAppsRecyclerView.setAdapter(mAdapter);
        mAppsRecyclerView.setHasFixedSize(true);
        mAppsRecyclerView.addOnScrollListener(mElevationController);
        mAppsRecyclerView.setElevationController(mElevationController);

        FocusedItemDecorator focusedItemDecorator = new FocusedItemDecorator(mAppsRecyclerView);
        mAppsRecyclerView.addItemDecoration(focusedItemDecorator);
        mAppsRecyclerView.preMeasureViews(mAdapter);
        mAdapter.setIconFocusListener(focusedItemDecorator.getFocusListener());

        if (FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP) {
            getRevealView().setVisibility(View.VISIBLE);
            getContentView().setVisibility(View.VISIBLE);
            getContentView().setBackground(null);
        }
    }

    @Override
    public View getTouchDelegateTargetView() {
        return mAppsRecyclerView;
    }

    @Override
    public void onBoundsChanged(Rect newBounds) { }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        grid.updateAppsViewNumCols();
        if (FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP) {
            if (mNumAppsPerRow != grid.inv.numColumns ||
                    mNumPredictedAppsPerRow != grid.inv.numColumns) {
                mNumAppsPerRow = grid.inv.numColumns;
                mNumPredictedAppsPerRow = grid.inv.numColumns;

                mAppsRecyclerView.setNumAppsPerRow(grid, mNumAppsPerRow);
                mAdapter.setNumAppsPerRow(mNumAppsPerRow);
                mApps.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow);
            }
            if (!grid.isVerticalBarLayout()) {
                MarginLayoutParams searchContainerLp =
                        (MarginLayoutParams) mSearchContainer.getLayoutParams();

                searchContainerLp.height = mLauncher.getDragLayer().getInsets().top
                        + mSearchContainerMinHeight;
                mSearchContainer.setLayoutParams(searchContainerLp);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // --- remove START when {@code FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP} is enabled. ---

        // Update the number of items in the grid before we measure the view
        grid.updateAppsViewNumCols();
        if (mNumAppsPerRow != grid.allAppsNumCols ||
                mNumPredictedAppsPerRow != grid.allAppsNumPredictiveCols) {
            mNumAppsPerRow = grid.allAppsNumCols;
            mNumPredictedAppsPerRow = grid.allAppsNumPredictiveCols;

            mAppsRecyclerView.setNumAppsPerRow(grid, mNumAppsPerRow);
            mAdapter.setNumAppsPerRow(mNumAppsPerRow);
            mApps.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow);
        }

        // --- remove END when {@code FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP} is enabled. ---
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (!mSearchBarController.isSearchFieldFocused() &&
                event.getAction() == KeyEvent.ACTION_DOWN) {
            final int unicodeChar = event.getUnicodeChar();
            final boolean isKeyNotWhitespace = unicodeChar > 0 &&
                    !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar);
            if (isKeyNotWhitespace) {
                boolean gotKey = TextKeyListener.getInstance().onKeyDown(this, mSearchQueryBuilder,
                        event.getKeyCode(), event);
                if (gotKey && mSearchQueryBuilder.length() > 0) {
                    mSearchBarController.focusSearchField();
                }
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onLongClick(final View v) {
        // Return early if this is not initiated from a touch
        if (!v.isInTouchMode()) return false;
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
    public void onSearchResult(String query, ArrayList<ComponentKey> apps) {
        if (apps != null) {
            mApps.setOrderedFilter(apps);
            mAppsRecyclerView.onSearchResultsChanged();
            mAdapter.setLastSearchQuery(query);
        }
    }

    @Override
    public void onAppDiscoverySearchUpdate(@Nullable AppDiscoveryItem app,
            @NonNull AppDiscoveryUpdateState state) {
        if (!mLauncher.isDestroyed()) {
            mApps.onAppDiscoverySearchUpdate(app, state);
            mAppsRecyclerView.onSearchResultsChanged();
        }
    }

    @Override
    public void clearSearchResult() {
        if (mApps.setOrderedFilter(null)) {
            mAppsRecyclerView.onSearchResultsChanged();
        }

        // Clear the search query
        mSearchQueryBuilder.clear();
        mSearchQueryBuilder.clearSpans();
        Selection.setSelection(mSearchQueryBuilder, 0);
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        targetParent.containerType = mAppsRecyclerView.getContainerType(v);
    }

    public boolean shouldRestoreImeState() {
        return !TextUtils.isEmpty(mSearchInput.getText());
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
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
            navBarBg.setVisibility(View.VISIBLE);
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
}
