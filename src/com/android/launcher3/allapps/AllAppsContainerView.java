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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.method.TextKeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BaseContainerView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeleteDropTarget;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Folder;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherTransitionable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.util.ComponentKey;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;



/**
 * A merge algorithm that merges every section indiscriminately.
 */
final class FullMergeAlgorithm implements AlphabeticalAppsList.MergeAlgorithm {

    @Override
    public boolean continueMerging(AlphabeticalAppsList.SectionInfo section,
           AlphabeticalAppsList.SectionInfo withSection,
           int sectionAppCount, int numAppsPerRow, int mergeCount) {
        // Don't merge the predicted apps
        if (section.firstAppItem.viewType != AllAppsGridAdapter.ICON_VIEW_TYPE) {
            return false;
        }
        // Otherwise, merge every other section
        return true;
    }
}

/**
 * The logic we use to merge multiple sections.  We only merge sections when their final row
 * contains less than a certain number of icons, and stop at a specified max number of merges.
 * In addition, we will try and not merge sections that identify apps from different scripts.
 */
final class SimpleSectionMergeAlgorithm implements AlphabeticalAppsList.MergeAlgorithm {

    private int mMinAppsPerRow;
    private int mMinRowsInMergedSection;
    private int mMaxAllowableMerges;
    private CharsetEncoder mAsciiEncoder;

    public SimpleSectionMergeAlgorithm(int minAppsPerRow, int minRowsInMergedSection, int maxNumMerges) {
        mMinAppsPerRow = minAppsPerRow;
        mMinRowsInMergedSection = minRowsInMergedSection;
        mMaxAllowableMerges = maxNumMerges;
        mAsciiEncoder = Charset.forName("US-ASCII").newEncoder();
    }

    @Override
    public boolean continueMerging(AlphabeticalAppsList.SectionInfo section,
           AlphabeticalAppsList.SectionInfo withSection,
           int sectionAppCount, int numAppsPerRow, int mergeCount) {
        // Don't merge the predicted apps
        if (section.firstAppItem.viewType != AllAppsGridAdapter.ICON_VIEW_TYPE) {
            return false;
        }

        // Continue merging if the number of hanging apps on the final row is less than some
        // fixed number (ragged), the merged rows has yet to exceed some minimum row count,
        // and while the number of merged sections is less than some fixed number of merges
        int rows = sectionAppCount / numAppsPerRow;
        int cols = sectionAppCount % numAppsPerRow;

        // Ensure that we do not merge across scripts, currently we only allow for english and
        // native scripts so we can test if both can just be ascii encoded
        boolean isCrossScript = false;
        if (section.firstAppItem != null && withSection.firstAppItem != null) {
            isCrossScript = mAsciiEncoder.canEncode(section.firstAppItem.sectionName) !=
                    mAsciiEncoder.canEncode(withSection.firstAppItem.sectionName);
        }
        return (0 < cols && cols < mMinAppsPerRow) &&
                rows < mMinRowsInMergedSection &&
                mergeCount < mMaxAllowableMerges &&
                !isCrossScript;
    }
}

/**
 * The all apps view container.
 */
public class AllAppsContainerView extends BaseContainerView implements DragSource,
        LauncherTransitionable, View.OnTouchListener, View.OnLongClickListener,
        AllAppsSearchBarController.Callbacks {

    private static final int MIN_ROWS_IN_MERGED_SECTION_PHONE = 3;
    private static final int MAX_NUM_MERGES_PHONE = 2;

    private final Launcher mLauncher;
    private final AlphabeticalAppsList mApps;
    private final AllAppsGridAdapter mAdapter;
    private final RecyclerView.LayoutManager mLayoutManager;
    private final RecyclerView.ItemDecoration mItemDecoration;

    // The computed bounds of the container
    private final Rect mContentBounds = new Rect();

    private AllAppsRecyclerView mAppsRecyclerView;
    private AllAppsSearchBarController mSearchBarController;

    private View mSearchContainer;
    private ExtendedEditText mSearchInput;
    private HeaderElevationController mElevationController;

    private SpannableStringBuilder mSearchQueryBuilder = null;

    private int mSectionNamesMargin;
    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;
    private int mRecyclerViewTopBottomPadding;
    // This coordinate is relative to this container view
    private final Point mBoundsCheckLastTouchDownPos = new Point(-1, -1);
    // This coordinate is relative to its parent
    private final Point mIconLastTouchPos = new Point();

    public AllAppsContainerView(Context context) {
        this(context, null);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Resources res = context.getResources();

        mLauncher = (Launcher) context;
        mSectionNamesMargin = res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin);
        mApps = new AlphabeticalAppsList(context);
        mAdapter = new AllAppsGridAdapter(mLauncher, mApps, this, mLauncher, this);
        mApps.setAdapter(mAdapter);
        mLayoutManager = mAdapter.getLayoutManager();
        mItemDecoration = mAdapter.getItemDecoration();
        mRecyclerViewTopBottomPadding =
                res.getDimensionPixelSize(R.dimen.all_apps_list_top_bottom_padding);

        mSearchQueryBuilder = new SpannableStringBuilder();
        Selection.setSelection(mSearchQueryBuilder, 0);
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
    }

    /**
     * Updates existing apps in the list
     */
    public void updateApps(List<AppInfo> apps) {
        mApps.updateApps(apps);
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        mApps.removeApps(apps);
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

        updateBackgroundAndPaddings();
    }

    /**
     * Scrolls this list view to the top.
     */
    public void scrollToTop() {
        mAppsRecyclerView.scrollToTop();
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
        mElevationController = Utilities.ATLEAST_LOLLIPOP
                ? new HeaderElevationController.ControllerVL(mSearchContainer)
                : new HeaderElevationController.ControllerV16(mSearchContainer);

        // Load the all apps recycler view
        mAppsRecyclerView = (AllAppsRecyclerView) findViewById(R.id.apps_list_view);
        mAppsRecyclerView.setApps(mApps);
        mAppsRecyclerView.setLayoutManager(mLayoutManager);
        mAppsRecyclerView.setAdapter(mAdapter);
        mAppsRecyclerView.setHasFixedSize(true);
        mAppsRecyclerView.addOnScrollListener(mElevationController);
        mAppsRecyclerView.setElevationController(mElevationController);

        if (mItemDecoration != null) {
            mAppsRecyclerView.addItemDecoration(mItemDecoration);
        }

        // Precalculate the prediction icon and normal icon sizes
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        final int widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                getResources().getDisplayMetrics().widthPixels, MeasureSpec.AT_MOST);
        final int heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                getResources().getDisplayMetrics().heightPixels, MeasureSpec.AT_MOST);

        BubbleTextView icon = (BubbleTextView) layoutInflater.inflate(
                R.layout.all_apps_icon, this, false);
        icon.applyDummyInfo();
        icon.measure(widthMeasureSpec, heightMeasureSpec);
        BubbleTextView predIcon = (BubbleTextView) layoutInflater.inflate(
                R.layout.all_apps_prediction_bar_icon, this, false);
        predIcon.applyDummyInfo();
        predIcon.measure(widthMeasureSpec, heightMeasureSpec);
        mAppsRecyclerView.setPremeasuredIconHeights(predIcon.getMeasuredHeight(),
                icon.getMeasuredHeight());

        updateBackgroundAndPaddings();
    }

    @Override
    public void onBoundsChanged(Rect newBounds) {
        mLauncher.updateOverlayBounds(newBounds);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mContentBounds.set(mContentPadding.left, mContentPadding.top,
                MeasureSpec.getSize(widthMeasureSpec) - mContentPadding.right,
                MeasureSpec.getSize(heightMeasureSpec) - mContentPadding.bottom);

        // Update the number of items in the grid before we measure the view
        // TODO: mSectionNamesMargin is currently 0, but also account for it,
        // if it's enabled in the future.
        int availableWidth = (!mContentBounds.isEmpty() ? mContentBounds.width() :
                MeasureSpec.getSize(widthMeasureSpec))
                    - 2 * mAppsRecyclerView.getMaxScrollbarWidth();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        grid.updateAppsViewNumCols(getResources(), availableWidth);
        if (mNumAppsPerRow != grid.allAppsNumCols ||
                mNumPredictedAppsPerRow != grid.allAppsNumPredictiveCols) {
            mNumAppsPerRow = grid.allAppsNumCols;
            mNumPredictedAppsPerRow = grid.allAppsNumPredictiveCols;

            // If there is a start margin to draw section names, determine how we are going to merge
            // app sections
            boolean mergeSectionsFully = mSectionNamesMargin == 0 || !grid.isPhone;
            AlphabeticalAppsList.MergeAlgorithm mergeAlgorithm = mergeSectionsFully ?
                    new FullMergeAlgorithm() :
                    new SimpleSectionMergeAlgorithm((int) Math.ceil(mNumAppsPerRow / 2f),
                            MIN_ROWS_IN_MERGED_SECTION_PHONE, MAX_NUM_MERGES_PHONE);

            mAppsRecyclerView.setNumAppsPerRow(grid, mNumAppsPerRow);
            mAdapter.setNumAppsPerRow(mNumAppsPerRow);
            mApps.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow, mergeAlgorithm);

            if (mNumAppsPerRow > 0) {
                int iconSize = availableWidth / mNumAppsPerRow;
                int iconSpacing = (iconSize - grid.allAppsIconSizePx) / 2;
                mSearchInput.setPaddingRelative(iconSpacing, 0, iconSpacing, 0);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Update the background and padding of the Apps view and children.  Instead of insetting the
     * container view, we inset the background and padding of the recycler view to allow for the
     * recycler view to handle touch events (for fast scrolling) all the way to the edge.
     */
    @Override
    protected void onUpdateBgPadding(Rect padding, Rect bgPadding) {
        mAppsRecyclerView.updateBackgroundPadding(bgPadding);
        mAdapter.updateBackgroundPadding(bgPadding);
        mElevationController.updateBackgroundPadding(bgPadding);

        // Pad the recycler view by the background padding plus the start margin (for the section
        // names)
        int maxScrollBarWidth = mAppsRecyclerView.getMaxScrollbarWidth();
        int startInset = Math.max(mSectionNamesMargin, maxScrollBarWidth);
        int topBottomPadding = mRecyclerViewTopBottomPadding;
        if (Utilities.isRtl(getResources())) {
            mAppsRecyclerView.setPadding(padding.left + maxScrollBarWidth,
                    topBottomPadding, padding.right + startInset, topBottomPadding);
        } else {
            mAppsRecyclerView.setPadding(padding.left + startInset, topBottomPadding,
                    padding.right + maxScrollBarWidth, topBottomPadding);
        }

        MarginLayoutParams lp = (MarginLayoutParams) mSearchContainer.getLayoutParams();
        lp.leftMargin = padding.left;
        lp.rightMargin = padding.right;
        mSearchContainer.setLayoutParams(lp);
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
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return handleTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return handleTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mIconLastTouchPos.set((int) ev.getX(), (int) ev.getY());
                break;
        }
        return false;
    }

    @Override
    public boolean onLongClick(View v) {
        // Return early if this is not initiated from a touch
        if (!v.isInTouchMode()) return false;
        // When we have exited all apps or are in transition, disregard long clicks
        if (!mLauncher.isAppsViewVisible() ||
                mLauncher.getWorkspace().isSwitchingState()) return false;
        // Return if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return false;

        // Start the drag
        mLauncher.getWorkspace().beginDragShared(v, mIconLastTouchPos, this, false);
        // Enter spring loaded mode
        mLauncher.enterSpringLoadedDragMode();

        return false;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return true;
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
    public void onFlingToDeleteCompleted() {
        // We just dismiss the drag when we fling, so cleanup here
        mLauncher.exitSpringLoadedDragModeDelayed(true,
                Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
        mLauncher.unlockScreenOrientation(false);
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

        // Display an error message if the drag failed due to there not being enough space on the
        // target layout we were dropping on.
        if (!success) {
            boolean showOutOfSpaceMessage = false;
            if (target instanceof Workspace) {
                int currentScreen = mLauncher.getCurrentWorkspaceScreen();
                Workspace workspace = (Workspace) target;
                CellLayout layout = (CellLayout) workspace.getChildAt(currentScreen);
                ItemInfo itemInfo = (ItemInfo) d.dragInfo;
                if (layout != null) {
                    showOutOfSpaceMessage =
                            !layout.findCellForSpan(null, itemInfo.spanX, itemInfo.spanY);
                }
            }
            if (showOutOfSpaceMessage) {
                mLauncher.showOutOfSpaceMessage(false);
            }

            d.deferDragViewCleanupPostAnimation = false;
        }
    }

    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
        if (toWorkspace) {
            reset();
        }
    }

    /**
     * Handles the touch events to dismiss all apps when clicking outside the bounds of the
     * recycler view.
     */
    private boolean handleTouchEvent(MotionEvent ev) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mContentBounds.isEmpty()) {
                    // Outset the fixed bounds and check if the touch is outside all apps
                    Rect tmpRect = new Rect(mContentBounds);
                    tmpRect.inset(-grid.allAppsIconSizePx / 2, 0);
                    if (ev.getX() < tmpRect.left || ev.getX() > tmpRect.right) {
                        mBoundsCheckLastTouchDownPos.set(x, y);
                        return true;
                    }
                } else {
                    // Check if the touch is outside all apps
                    if (ev.getX() < getPaddingLeft() ||
                            ev.getX() > (getWidth() - getPaddingRight())) {
                        mBoundsCheckLastTouchDownPos.set(x, y);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mBoundsCheckLastTouchDownPos.x > -1) {
                    ViewConfiguration viewConfig = ViewConfiguration.get(getContext());
                    float dx = ev.getX() - mBoundsCheckLastTouchDownPos.x;
                    float dy = ev.getY() - mBoundsCheckLastTouchDownPos.y;
                    float distance = (float) Math.hypot(dx, dy);
                    if (distance < viewConfig.getScaledTouchSlop()) {
                        // The background was clicked, so just go home
                        Launcher launcher = (Launcher) getContext();
                        launcher.showWorkspace(true);
                        return true;
                    }
                }
                // Fall through
            case MotionEvent.ACTION_CANCEL:
                mBoundsCheckLastTouchDownPos.set(-1, -1);
                break;
        }
        return false;
    }

    @Override
    public void onSearchResult(String query, ArrayList<ComponentKey> apps) {
        if (apps != null) {
            if (mApps.setOrderedFilter(apps)) {
                mAppsRecyclerView.onSearchResultsChanged();
            }
            mAdapter.setLastSearchQuery(query);
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
}
