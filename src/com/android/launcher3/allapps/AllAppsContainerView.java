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
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Bundle;
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
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BaseContainerView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeleteDropTarget;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Folder;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherTransitionable;
import com.android.launcher3.R;
import com.android.launcher3.Stats;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Thunk;

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
        // Merge EVERYTHING
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
        LauncherTransitionable, AlphabeticalAppsList.AdapterChangedCallback,
        AllAppsGridAdapter.PredictionBarSpacerCallbacks, View.OnTouchListener,
        View.OnLongClickListener, ViewTreeObserver.OnPreDrawListener,
        AllAppsSearchBarController.Callbacks, Stats.LaunchSourceProvider {

    private static final int MIN_ROWS_IN_MERGED_SECTION_PHONE = 3;
    private static final int MAX_NUM_MERGES_PHONE = 2;

    @Thunk Launcher mLauncher;
    @Thunk AlphabeticalAppsList mApps;
    private LayoutInflater mLayoutInflater;
    private AllAppsGridAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private RecyclerView.ItemDecoration mItemDecoration;

    @Thunk View mContent;
    @Thunk View mContainerView;
    @Thunk View mRevealView;
    @Thunk AllAppsRecyclerView mAppsRecyclerView;
    @Thunk ViewGroup mPredictionBarView;
    @Thunk AllAppsSearchBarController mSearchBarController;
    private ViewGroup mSearchBarContainerView;
    private View mSearchBarView;

    private int mSectionNamesMargin;
    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;
    private int mRecyclerViewTopBottomPadding;
    // This coordinate is relative to this container view
    private final Point mBoundsCheckLastTouchDownPos = new Point(-1, -1);
    // This coordinate is relative to its parent
    private final Point mIconLastTouchPos = new Point();
    // This coordinate is used to proxy click and long-click events to the prediction bar icons
    private final Point mPredictionIconTouchDownPos = new Point();
    // Normal container insets
    private int mPredictionBarHeight;
    private int mLastRecyclerViewScrollPos = -1;
    @Thunk boolean mFocusPredictionBarOnFirstBind;

    private SpannableStringBuilder mSearchQueryBuilder = null;

    private CheckLongPressHelper mPredictionIconCheckForLongPress;
    private View mPredictionIconUnderTouch;

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
        mLayoutInflater = LayoutInflater.from(context);
        DeviceProfile grid = mLauncher.getDeviceProfile();
        mPredictionBarHeight = (int) (grid.allAppsIconSizePx + grid.iconDrawablePaddingOriginalPx +
                Utilities.calculateTextHeight(grid.allAppsIconTextSizePx) +
                2 * res.getDimensionPixelSize(R.dimen.all_apps_icon_top_bottom_padding) +
                res.getDimensionPixelSize(R.dimen.all_apps_prediction_bar_top_padding) +
                res.getDimensionPixelSize(R.dimen.all_apps_prediction_bar_bottom_padding));
        mSectionNamesMargin = res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin);
        mApps = new AlphabeticalAppsList(context);
        mApps.setAdapterChangedCallback(this);
        mAdapter = new AllAppsGridAdapter(context, mApps, this, this, mLauncher, this);
        mAdapter.setEmptySearchText(res.getString(R.string.all_apps_loading_message));
        mAdapter.setPredictionRowHeight(mPredictionBarHeight);
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
        mSearchBarController.initialize(mApps, this);

        // Add the new search view to the layout
        View searchBarView = searchController.getView(mSearchBarContainerView);
        mSearchBarContainerView.addView(searchBarView);
        mSearchBarContainerView.setVisibility(View.VISIBLE);
        mSearchBarView = searchBarView;
        setHasSearchBar();

        updateBackgroundAndPaddings();
    }

    /**
     * Scrolls this list view to the top.
     */
    public void scrollToTop() {
        mAppsRecyclerView.scrollToTop();
    }

    /**
     * Returns the content view used for the launcher transitions.
     */
    public View getContentView() {
        return mContainerView;
    }

    /**
     * Returns the all apps search view.
     */
    public View getSearchBarView() {
        return mSearchBarView;
    }

    /**
     * Returns the reveal view used for the launcher transitions.
     */
    public View getRevealView() {
        return mRevealView;
    }

    /**
     * Returns an new instance of the default app search controller.
     */
    public AllAppsSearchBarController newDefaultAppSearchController() {
        return new DefaultAppSearchController(getContext(), this, mAppsRecyclerView);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        boolean isRtl = Utilities.isRtl(getResources());
        mAdapter.setRtl(isRtl);
        mContent = findViewById(R.id.content);

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        View.OnFocusChangeListener focusProxyListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (!mApps.getPredictedApps().isEmpty()) {
                        // If the prediction bar is going to be bound, then defer focusing until
                        // it is first bound
                        if (mPredictionBarView.getChildCount() == 0) {
                            mFocusPredictionBarOnFirstBind = true;
                        } else {
                            mPredictionBarView.requestFocus();
                        }
                    } else {
                        mAppsRecyclerView.requestFocus();
                    }
                }
            }
        };
        mSearchBarContainerView = (ViewGroup) findViewById(R.id.search_box_container);
        mSearchBarContainerView.setOnFocusChangeListener(focusProxyListener);
        mContainerView = findViewById(R.id.all_apps_container);
        mContainerView.setOnFocusChangeListener(focusProxyListener);
        mRevealView = findViewById(R.id.all_apps_reveal);

        // Load the all apps recycler view
        mAppsRecyclerView = (AllAppsRecyclerView) findViewById(R.id.apps_list_view);
        mAppsRecyclerView.setApps(mApps);
        mAppsRecyclerView.setPredictionBarHeight(mPredictionBarHeight);
        mAppsRecyclerView.setLayoutManager(mLayoutManager);
        mAppsRecyclerView.setAdapter(mAdapter);
        mAppsRecyclerView.setHasFixedSize(true);
        if (mItemDecoration != null) {
            mAppsRecyclerView.addItemDecoration(mItemDecoration);
        }

        // Fix the prediction bar height
        mPredictionBarView = (ViewGroup) findViewById(R.id.prediction_bar);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPredictionBarView.getLayoutParams();
        lp.height = mPredictionBarHeight;

        updateBackgroundAndPaddings();
    }

    @Override
    public void onBoundsChanged(Rect newBounds) {
        mLauncher.updateOverlayBounds(newBounds);
    }

    @Override
    public void onBindPredictionBar() {
        updatePredictionBarVisibility();

        List<AppInfo> predictedApps = mApps.getPredictedApps();

        // Remove extra prediction icons
        while (mPredictionBarView.getChildCount() > mNumPredictedAppsPerRow) {
            mPredictionBarView.removeViewAt(mPredictionBarView.getChildCount() - 1);
        }

        int childCount = mPredictionBarView.getChildCount();
        for (int i = 0; i < mNumPredictedAppsPerRow; i++) {
            BubbleTextView icon;
            if (i < childCount) {
                // If a child at that index exists, then get that child
                icon = (BubbleTextView) mPredictionBarView.getChildAt(i);
            } else {
                // Otherwise, inflate a new icon
                icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.all_apps_prediction_bar_icon, mPredictionBarView, false);
                icon.setFocusable(true);
                icon.setLongPressTimeout(ViewConfiguration.get(getContext()).getLongPressTimeout());
                mPredictionBarView.addView(icon);
            }

            // Either apply the app info to the child, or hide the view
            if (i < predictedApps.size()) {
                if (icon.getVisibility() != View.VISIBLE) {
                    icon.setVisibility(View.VISIBLE);
                }
                icon.applyFromApplicationInfo(predictedApps.get(i));
            } else {
                icon.setVisibility(View.INVISIBLE);
            }
        }

        if (mFocusPredictionBarOnFirstBind) {
            mFocusPredictionBarOnFirstBind = false;
            mPredictionBarView.requestFocus();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Update the number of items in the grid before we measure the view
        int availableWidth = !mContentBounds.isEmpty() ? mContentBounds.width() :
                MeasureSpec.getSize(widthMeasureSpec);
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

            mAppsRecyclerView.setNumAppsPerRow(mNumAppsPerRow);
            mAdapter.setNumAppsPerRow(mNumAppsPerRow);
            mApps.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow, mergeAlgorithm);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Update the background and padding of the Apps view and children.  Instead of insetting the
     * container view, we inset the background and padding of the recycler view to allow for the
     * recycler view to handle touch events (for fast scrolling) all the way to the edge.
     */
    @Override
    protected void onUpdateBackgroundAndPaddings(Rect searchBarBounds, Rect padding) {
        boolean isRtl = Utilities.isRtl(getResources());

        // TODO: Use quantum_panel instead of quantum_panel_shape
        InsetDrawable background = new InsetDrawable(
                getResources().getDrawable(R.drawable.quantum_panel_shape), padding.left, 0,
                padding.right, 0);
        Rect bgPadding = new Rect();
        background.getPadding(bgPadding);
        mContainerView.setBackground(background);
        mRevealView.setBackground(background.getConstantState().newDrawable());
        mAppsRecyclerView.updateBackgroundPadding(bgPadding);
        mAdapter.updateBackgroundPadding(bgPadding);

        // Hack: We are going to let the recycler view take the full width, so reset the padding on
        // the container to zero after setting the background and apply the top-bottom padding to
        // the content view instead so that the launcher transition clips correctly.
        mContent.setPadding(0, padding.top, 0, padding.bottom);
        mContainerView.setPadding(0, 0, 0, 0);

        // Pad the recycler view by the background padding plus the start margin (for the section
        // names)
        int startInset = Math.max(mSectionNamesMargin, mAppsRecyclerView.getMaxScrollbarWidth());
        int topBottomPadding = mRecyclerViewTopBottomPadding;
        if (isRtl) {
            mAppsRecyclerView.setPadding(padding.left + mAppsRecyclerView.getMaxScrollbarWidth(),
                    topBottomPadding, padding.right + startInset, topBottomPadding);
        } else {
            mAppsRecyclerView.setPadding(padding.left + startInset, topBottomPadding,
                    padding.right + mAppsRecyclerView.getMaxScrollbarWidth(), topBottomPadding);
        }

        // Inset the search bar to fit its bounds above the container
        if (mSearchBarView != null) {
            Rect backgroundPadding = new Rect();
            if (mSearchBarView.getBackground() != null) {
                mSearchBarView.getBackground().getPadding(backgroundPadding);
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                    mSearchBarContainerView.getLayoutParams();
            lp.leftMargin = searchBarBounds.left - backgroundPadding.left;
            lp.topMargin = searchBarBounds.top - backgroundPadding.top;
            lp.rightMargin = (getMeasuredWidth() - searchBarBounds.right) - backgroundPadding.right;
            mSearchBarContainerView.requestLayout();
        }

        // Update the prediction bar insets as well
        mPredictionBarView = (ViewGroup) findViewById(R.id.prediction_bar);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPredictionBarView.getLayoutParams();
        lp.leftMargin = padding.left + mAppsRecyclerView.getMaxScrollbarWidth();
        lp.rightMargin = padding.right + mAppsRecyclerView.getMaxScrollbarWidth();
        mPredictionBarView.requestLayout();
    }

    @Override
    public boolean onPreDraw() {
        if (mNumAppsPerRow > 0) {
            // Update the position of the prediction bar to match the scroll of the all apps list
            synchronizeToRecyclerViewScrollPosition(mAppsRecyclerView.getScrollPosition());
        }
        return true;
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
                    layout.calculateSpans(itemInfo);
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
    public void onAdapterItemsChanged() {
        updatePredictionBarVisibility();
    }

    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        // Register for a pre-draw listener to synchronize the recycler view scroll to other views
        // in this container
        if (!toWorkspace) {
            getViewTreeObserver().addOnPreDrawListener(this);
        }
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
            getViewTreeObserver().removeOnPreDrawListener(this);
            mLastRecyclerViewScrollPos = -1;

            // Reset the search bar after transitioning home
            mSearchBarController.reset();
        }
    }

    /**
     * Updates the container when the recycler view is scrolled.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void synchronizeToRecyclerViewScrollPosition(int scrollY) {
        if (mLastRecyclerViewScrollPos != scrollY) {
            mLastRecyclerViewScrollPos = scrollY;

            // Scroll the prediction bar with the contents of the recycler view
            mPredictionBarView.setTranslationY(-scrollY + mAppsRecyclerView.getPaddingTop());
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // If we were waiting for long-click, cancel the request once a child has started handling
        // the scrolling
        if (mPredictionIconCheckForLongPress != null) {
            mPredictionIconCheckForLongPress.cancelLongPress();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
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
                // We workaround the fact that the recycler view needs the touches for the scroll
                // and we want to intercept it for clicks in the prediction bar by handling clicks
                // and long clicks in the prediction bar ourselves.
                if (mPredictionBarView != null && mPredictionBarView.getVisibility() == View.VISIBLE) {
                    mPredictionIconTouchDownPos.set(x, y);
                    mPredictionIconUnderTouch = findPredictedAppAtCoordinate(x, y);
                    if (mPredictionIconUnderTouch != null) {
                        mPredictionIconCheckForLongPress =
                                new CheckLongPressHelper(mPredictionIconUnderTouch, this);
                        mPredictionIconCheckForLongPress.postCheckForLongPress();
                    }
                }

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
            case MotionEvent.ACTION_MOVE:
                if (mPredictionIconUnderTouch != null) {
                    float dist = (float) Math.hypot(x - mPredictionIconTouchDownPos.x,
                            y - mPredictionIconTouchDownPos.y);
                    if (dist > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                        if (mPredictionIconCheckForLongPress != null) {
                            mPredictionIconCheckForLongPress.cancelLongPress();
                        }
                        mPredictionIconCheckForLongPress = null;
                        mPredictionIconUnderTouch = null;
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

                // Trigger the click on the prediction bar icon if that's where we touched
                if (mPredictionIconUnderTouch != null &&
                        !mPredictionIconCheckForLongPress.hasPerformedLongPress()) {
                    mLauncher.onClick(mPredictionIconUnderTouch);
                }

                // Fall through
            case MotionEvent.ACTION_CANCEL:
                mBoundsCheckLastTouchDownPos.set(-1, -1);
                mPredictionIconTouchDownPos.set(-1, -1);

                // On touch up/cancel, cancel the long press on the prediction bar icon if it has
                // not yet been performed
                if (mPredictionIconCheckForLongPress != null) {
                    mPredictionIconCheckForLongPress.cancelLongPress();
                    mPredictionIconCheckForLongPress = null;
                }
                mPredictionIconUnderTouch = null;

                break;
        }
        return false;
    }

    @Override
    public void onSearchResult(String query, ArrayList<ComponentKey> apps) {
        if (apps != null) {
            if (apps.isEmpty()) {
                String formatStr = getResources().getString(R.string.all_apps_no_search_results);
                mAdapter.setEmptySearchText(String.format(formatStr, query));
            } else {
                mAppsRecyclerView.scrollToTop();
            }
            mApps.setOrderedFilter(apps);
        }
    }

    @Override
    public void clearSearchResult() {
        mApps.setOrderedFilter(null);

        // Clear the search query
        mSearchQueryBuilder.clear();
        mSearchQueryBuilder.clearSpans();
        Selection.setSelection(mSearchQueryBuilder, 0);
    }

    @Override
    public void fillInLaunchSourceData(Bundle sourceData) {
        // Since the other cases are caught by the AllAppsRecyclerView LaunchSourceProvider, we just
        // handle the prediction bar icons here
        sourceData.putString(Stats.SOURCE_EXTRA_CONTAINER, Stats.CONTAINER_ALL_APPS);
        sourceData.putString(Stats.SOURCE_EXTRA_SUB_CONTAINER,
                Stats.SUB_CONTAINER_ALL_APPS_PREDICTION);
    }

    /**
     * Returns the predicted app in the prediction bar given a set of local coordinates.
     */
    private View findPredictedAppAtCoordinate(int x, int y) {
        Rect hitRect = new Rect();

        // Ensure that are touching in the recycler view
        int[] coord = {x, y};
        Utilities.mapCoordInSelfToDescendent(mAppsRecyclerView, this, coord);
        mAppsRecyclerView.getHitRect(hitRect);
        if (!hitRect.contains(coord[0], coord[1])) {
            return null;
        }

        // Check against the children of the prediction bar
        coord[0] = x;
        coord[1] = y;
        Utilities.mapCoordInSelfToDescendent(mPredictionBarView, this, coord);
        for (int i = 0; i < mPredictionBarView.getChildCount(); i++) {
            View child = mPredictionBarView.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.getHitRect(hitRect);
            if (hitRect.contains(coord[0], coord[1])) {
                return child;
            }
        }
        return null;
    }

    /**
     * Updates the visibility of the prediction bar.
     * @return whether the prediction bar is visible
     */
    private boolean updatePredictionBarVisibility() {
        boolean showPredictionBar = !mApps.getPredictedApps().isEmpty() &&
                (!mApps.hasFilter() || mSearchBarController.shouldShowPredictionBar());
        if (showPredictionBar) {
            mPredictionBarView.setVisibility(View.VISIBLE);
        } else if (!showPredictionBar) {
            mPredictionBarView.setVisibility(View.INVISIBLE);
        }
        return showPredictionBar;
    }
}
