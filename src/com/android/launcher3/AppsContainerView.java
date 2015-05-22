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
package com.android.launcher3;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.util.Thunk;

import java.util.List;
import java.util.regex.Pattern;


/**
 * Interface for controlling the header elevation in response to RecyclerView scroll.
 */
interface HeaderElevationController {
    void onScroll(int scrollY);
    void disable();
}

/**
 * Implementation of the header elevation mechanism for pre-L devices.  It simulates elevation
 * by drawing a gradient under the header bar.
 */
final class HeaderElevationControllerV16 implements HeaderElevationController {

    private final View mShadow;

    private final float mScrollToElevation;

    public HeaderElevationControllerV16(View header) {
        Resources res = header.getContext().getResources();
        mScrollToElevation = res.getDimension(R.dimen.all_apps_header_scroll_to_elevation);

        mShadow = new View(header.getContext());
        mShadow.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x44000000, 0x00000000}));
        mShadow.setAlpha(0);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                res.getDimensionPixelSize(R.dimen.all_apps_header_shadow_height));
        lp.topMargin = ((FrameLayout.LayoutParams) header.getLayoutParams()).height;

        ((ViewGroup) header.getParent()).addView(mShadow, lp);
    }

    @Override
    public void onScroll(int scrollY) {
        float elevationPct = (float) Math.min(scrollY, mScrollToElevation) /
                mScrollToElevation;
        mShadow.setAlpha(elevationPct);
    }

    @Override
    public void disable() {
        ViewGroup parent = (ViewGroup) mShadow.getParent();
        if (parent != null) {
            parent.removeView(mShadow);
        }
    }
}

/**
 * Implementation of the header elevation mechanism for L+ devices, which makes use of the native
 * view elevation.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
final class HeaderElevationControllerVL implements HeaderElevationController {

    private final View mHeader;
    private final float mMaxElevation;
    private final float mScrollToElevation;

    public HeaderElevationControllerVL(View header) {
        mHeader = header;

        Resources res = header.getContext().getResources();
        mMaxElevation = res.getDimension(R.dimen.all_apps_header_max_elevation);
        mScrollToElevation = res.getDimension(R.dimen.all_apps_header_scroll_to_elevation);
    }

    @Override
    public void onScroll(int scrollY) {
        float elevationPct = (float) Math.min(scrollY, mScrollToElevation) /
                mScrollToElevation;
        float newElevation = mMaxElevation * elevationPct;
        if (Float.compare(mHeader.getElevation(), newElevation) != 0) {
            mHeader.setElevation(newElevation);
        }
    }

    @Override
    public void disable() { }
}

/**
 * The all apps view container.
 */
public class AppsContainerView extends BaseContainerView implements DragSource, Insettable,
        TextWatcher, TextView.OnEditorActionListener, LauncherTransitionable,
        AlphabeticalAppsList.AdapterChangedCallback, AppsGridAdapter.PredictionBarSpacerCallbacks,
        View.OnTouchListener, View.OnClickListener, View.OnLongClickListener,
        ViewTreeObserver.OnPreDrawListener {

    public static final boolean GRID_MERGE_SECTIONS = true;

    private static final boolean ALLOW_SINGLE_APP_LAUNCH = true;
    private static final boolean DYNAMIC_HEADER_ELEVATION = true;
    private static final boolean DISMISS_SEARCH_ON_BACK = true;

    private static final int FADE_IN_DURATION = 175;
    private static final int FADE_OUT_DURATION = 100;
    private static final int SEARCH_TRANSLATION_X_DP = 18;

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s|\\p{javaSpaceChar}]+");

    @Thunk Launcher mLauncher;
    @Thunk AlphabeticalAppsList mApps;
    private LayoutInflater mLayoutInflater;
    private AppsGridAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private RecyclerView.ItemDecoration mItemDecoration;

    private FrameLayout mContentView;
    @Thunk AppsContainerRecyclerView mAppsRecyclerView;
    private ViewGroup mPredictionBarView;
    private View mHeaderView;
    private View mSearchBarContainerView;
    private View mSearchButtonView;
    private View mDismissSearchButtonView;
    private AppsContainerSearchEditTextView mSearchBarEditView;

    private HeaderElevationController mElevationController;

    private int mNumAppsPerRow;
    private int mNumPredictedAppsPerRow;
    // This coordinate is relative to this container view
    private final Point mBoundsCheckLastTouchDownPos = new Point(-1, -1);
    // This coordinate is relative to its parent
    private final Point mIconLastTouchPos = new Point();
    // This coordinate is used to proxy click and long-click events to the prediction bar icons
    private final Point mPredictionIconTouchDownPos = new Point();
    private int mContentMarginStart;
    // Normal container insets
    private int mContainerInset;
    private int mPredictionBarHeight;
    private int mLastRecyclerViewScrollPos = -1;
    private boolean mFocusPredictionBarOnFirstBind;

    private CheckLongPressHelper mPredictionIconCheckForLongPress;
    private View mPredictionIconUnderTouch;

    public AppsContainerView(Context context) {
        this(context, null);
    }

    public AppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LauncherAppState app = LauncherAppState.getInstance();
        Resources res = context.getResources();

        mLauncher = (Launcher) context;
        DeviceProfile grid = mLauncher.getDeviceProfile();

        mContainerInset = context.getResources().getDimensionPixelSize(
                R.dimen.apps_container_inset);
        mPredictionBarHeight = grid.allAppsCellHeightPx +
                2 * res.getDimensionPixelSize(R.dimen.apps_prediction_icon_top_bottom_padding);

        mLayoutInflater = LayoutInflater.from(context);

        mNumAppsPerRow = grid.appsViewNumCols;
        mNumPredictedAppsPerRow = grid.appsViewNumPredictiveCols;
        mApps = new AlphabeticalAppsList(context, mNumAppsPerRow, mNumPredictedAppsPerRow);
        mApps.setAdapterChangedCallback(this);
        mAdapter = new AppsGridAdapter(context, mApps, mNumAppsPerRow, this, this, mLauncher, this);
        mAdapter.setEmptySearchText(res.getString(R.string.loading_apps_message));
        mAdapter.setNumAppsPerRow(mNumAppsPerRow);
        mAdapter.setPredictionRowHeight(mPredictionBarHeight);
        mLayoutManager = mAdapter.getLayoutManager();
        mItemDecoration = mAdapter.getItemDecoration();
        mContentMarginStart = mAdapter.getContentMarginStart();

        mApps.setAdapter(mAdapter);
    }

    /**
     * Sets the current set of predicted apps.
     */
    public void setPredictedApps(List<ComponentName> apps) {
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
     * Hides the header bar
     */
    public void hideHeaderBar() {
        mHeaderView.setVisibility(View.GONE);
        mElevationController.disable();
        onUpdateBackgrounds();
        onUpdatePaddings();
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
        return mContentView;
    }

    /**
     * Returns the reveal view used for the launcher transitions.
     */
    public View getRevealView() {
        return findViewById(R.id.apps_view_transition_overlay);
    }

    @Override
    protected void onFinishInflate() {
        boolean isRtl = Utilities.isRtl(getResources());
        mAdapter.setRtl(isRtl);

        // Work around the search box getting first focus and showing the cursor by
        // proxying the focus from the content view to the recycler view directly
        mContentView = (FrameLayout) findViewById(R.id.apps_list);
        mContentView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (v == mContentView && hasFocus) {
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
        });

        // Fix the header view elevation if not dynamically calculating it
        mHeaderView = findViewById(R.id.header);
        mHeaderView.setOnClickListener(this);

        mElevationController = Utilities.isLmpOrAbove() ?
                new HeaderElevationControllerVL(mHeaderView) :
                    new HeaderElevationControllerV16(mHeaderView);
        if (!DYNAMIC_HEADER_ELEVATION) {
            mElevationController.onScroll(getResources()
                    .getDimensionPixelSize(R.dimen.all_apps_header_scroll_to_elevation));
        }

        // Fix the prediction bar size
        mPredictionBarView = (ViewGroup) findViewById(R.id.prediction_bar);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPredictionBarView.getLayoutParams();
        lp.height = mPredictionBarHeight;

        mSearchButtonView = mHeaderView.findViewById(R.id.search_button);
        mSearchBarContainerView = findViewById(R.id.app_search_container);
        mDismissSearchButtonView = mSearchBarContainerView.findViewById(R.id.dismiss_search_button);
        mDismissSearchButtonView.setOnClickListener(this);
        mSearchBarEditView = (AppsContainerSearchEditTextView) findViewById(R.id.app_search_box);
        if (mSearchBarEditView != null) {
            mSearchBarEditView.addTextChangedListener(this);
            mSearchBarEditView.setOnEditorActionListener(this);
            if (DISMISS_SEARCH_ON_BACK) {
                mSearchBarEditView.setOnBackKeyListener(
                        new AppsContainerSearchEditTextView.OnBackKeyListener() {
                            @Override
                            public void onBackKey() {
                                // Only hide the search field if there is no query, or if there
                                // are no filtered results
                                String query = Utilities.trim(
                                        mSearchBarEditView.getEditableText().toString());
                                if (query.isEmpty() || mApps.hasNoFilteredResults()) {
                                    hideSearchField(true, true);
                                }
                            }
                        });
            }
        }
        mAppsRecyclerView = (AppsContainerRecyclerView) findViewById(R.id.apps_list_view);
        mAppsRecyclerView.setApps(mApps);
        mAppsRecyclerView.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow);
        mAppsRecyclerView.setPredictionBarHeight(mPredictionBarHeight);
        mAppsRecyclerView.setLayoutManager(mLayoutManager);
        mAppsRecyclerView.setAdapter(mAdapter);
        mAppsRecyclerView.setHasFixedSize(true);
        if (mItemDecoration != null) {
            mAppsRecyclerView.addItemDecoration(mItemDecoration);
        }
        onUpdateBackgrounds();
        onUpdatePaddings();
    }

    @Override
    public void onBindPredictionBar() {
        updatePredictionBarVisibility();

        List<AppInfo> predictedApps = mApps.getPredictedApps();
        int childCount = mPredictionBarView.getChildCount();
        for (int i = 0; i < mNumPredictedAppsPerRow; i++) {
            BubbleTextView icon;
            if (i < childCount) {
                // If a child at that index exists, then get that child
                icon = (BubbleTextView) mPredictionBarView.getChildAt(i);
            } else {
                // Otherwise, inflate a new icon
                icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.apps_prediction_bar_icon_view, mPredictionBarView, false);
                icon.setFocusable(true);
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
    protected void onFixedBoundsUpdated() {
        // Update the number of items in the grid
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (grid.updateAppsViewNumCols(getContext().getResources(), mFixedBounds.width())) {
            mNumAppsPerRow = grid.appsViewNumCols;
            mNumPredictedAppsPerRow = grid.appsViewNumPredictiveCols;
            mAppsRecyclerView.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow);
            mAdapter.setNumAppsPerRow(mNumAppsPerRow);
            mApps.setNumAppsPerRow(mNumAppsPerRow, mNumPredictedAppsPerRow);
        }
    }

    /**
     * Update the padding of the Apps view and children.  To ensure that the RecyclerView has the
     * full width to handle touches right to the edge of the screen, we only apply the top and
     * bottom padding to the AppsContainerView and then the left/right padding on the RecyclerView
     * itself.  In particular, the left/right padding is applied to the background of the view,
     * and then additionally inset by the start margin.
     */
    @Override
    protected void onUpdatePaddings() {
        boolean isRtl = Utilities.isRtl(getResources());
        boolean hasSearchBar = (mSearchBarEditView != null) &&
                (mSearchBarEditView.getVisibility() == View.VISIBLE);

        // Set the background on the container, but let the recyclerView extend the full screen,
        // so that the fast-scroller works on the edge as well.
        mContentView.setPadding(0, 0, 0, 0);

        if (mFixedBounds.isEmpty()) {
            // If there are no fixed bounds, then use the default padding and insets
            setPadding(mInsets.left, mContainerInset + mInsets.top, mInsets.right,
                    mContainerInset + mInsets.bottom);
        } else {
            // If there are fixed bounds, then we update the padding to reflect the fixed bounds.
            setPadding(mFixedBounds.left, mFixedBounds.top, getMeasuredWidth() - mFixedBounds.right,
                    mFixedBounds.bottom);
        }

        // Update the apps recycler view, inset it by the container inset as well
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int startMargin = grid.isPhone ? mContentMarginStart : 0;
        int inset = mFixedBounds.isEmpty() ? mContainerInset : mFixedBoundsContainerInset;
        if (isRtl) {
            mAppsRecyclerView.setPadding(inset + mAppsRecyclerView.getScrollbarWidth(), inset,
                    inset + startMargin, inset);
        } else {
            mAppsRecyclerView.setPadding(inset + startMargin, inset,
                    inset + mAppsRecyclerView.getScrollbarWidth(), inset);
        }

        // Update the header bar
        if (hasSearchBar) {
            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) mHeaderView.getLayoutParams();
            lp.leftMargin = lp.rightMargin = inset;
            mHeaderView.requestLayout();
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPredictionBarView.getLayoutParams();
        lp.leftMargin = inset + mAppsRecyclerView.getScrollbarWidth();
        lp.rightMargin = inset + mAppsRecyclerView.getScrollbarWidth();
        mPredictionBarView.requestLayout();
    }

    /**
     * Update the background of the Apps view and children.
     */
    @Override
    protected void onUpdateBackgrounds() {
        int inset = mFixedBounds.isEmpty() ? mContainerInset : mFixedBoundsContainerInset;

        // Update the background of the reveal view and list to be inset with the fixed bound
        // insets instead of the default insets
        // TODO: Use quantum_panel instead of quantum_panel_shape.
        InsetDrawable background = new InsetDrawable(
                getContext().getResources().getDrawable(R.drawable.quantum_panel_shape),
                inset, 0, inset, 0);
        mContentView.setBackground(background);
        mAppsRecyclerView.updateBackgroundPadding(background);
        mAdapter.updateBackgroundPadding(background);
        getRevealView().setBackground(background.getConstantState().newDrawable());
    }

    @Override
    public boolean onPreDraw() {
        synchronizeToRecyclerViewScrollPosition(mAppsRecyclerView.getScrollPosition());
        return true;
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
    public void onClick(View v) {
        if (v == mHeaderView) {
            showSearchField();
        } else if (v == mDismissSearchButtonView) {
            hideSearchField(true, true);
        }
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
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Do nothing
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Do nothing
    }

    @Override
    public void afterTextChanged(final Editable s) {
        String queryText = s.toString();
        if (queryText.isEmpty()) {
            mApps.setFilter(null);
        } else {
            String formatStr = getResources().getString(R.string.apps_view_no_search_results);
            mAdapter.setEmptySearchText(String.format(formatStr, queryText));

            // Do an intersection of the words in the query and each title, and filter out all the
            // apps that don't match all of the words in the query.
            final String queryTextLower = queryText.toLowerCase();
            final String[] queryWords = SPLIT_PATTERN.split(queryTextLower);
            mApps.setFilter(new AlphabeticalAppsList.Filter() {
                @Override
                public boolean retainApp(AppInfo info, String sectionName) {
                    if (sectionName.toLowerCase().contains(queryTextLower)) {
                        return true;
                    }
                    String title = info.title.toString();
                    String[] words = SPLIT_PATTERN.split(title.toLowerCase());
                    for (int qi = 0; qi < queryWords.length; qi++) {
                        boolean foundMatch = false;
                        for (int i = 0; i < words.length; i++) {
                            if (words[i].startsWith(queryWords[qi])) {
                                foundMatch = true;
                                break;
                            }
                        }
                        if (!foundMatch) {
                            // If there is a word in the query that does not match any words in this
                            // title, so skip it.
                            return false;
                        }
                    }
                    return true;
                }
            });
        }
        scrollToTop();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (ALLOW_SINGLE_APP_LAUNCH && actionId == EditorInfo.IME_ACTION_DONE) {
            // Skip the quick-launch if there isn't exactly one item
            if (mApps.getSize() != 1) {
                return false;
            }

            List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
            for (int i = 0; i < items.size(); i++) {
                AlphabeticalAppsList.AdapterItem item = items.get(i);
                if (item.viewType == AppsGridAdapter.ICON_VIEW_TYPE) {
                    mAppsRecyclerView.getChildAt(i).performClick();
                    getInputMethodManager().hideSoftInputFromWindow(getWindowToken(), 0);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onAdapterItemsChanged() {
        updatePredictionBarVisibility();
    }

    @Override
    public View getContent() {
        return null;
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
        if (mSearchBarEditView != null) {
            if (toWorkspace) {
                hideSearchField(false, false);
            }
        }
        if (toWorkspace) {
            getViewTreeObserver().removeOnPreDrawListener(this);
            mLastRecyclerViewScrollPos = -1;
        }
    }

    /**
     * Updates the container when the recycler view is scrolled.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void synchronizeToRecyclerViewScrollPosition(int scrollY) {
        if (mLastRecyclerViewScrollPos != scrollY) {
            mLastRecyclerViewScrollPos = scrollY;
            if (DYNAMIC_HEADER_ELEVATION) {
                mElevationController.onScroll(scrollY);
            }

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
        LauncherAppState app = LauncherAppState.getInstance();
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

                if (!mFixedBounds.isEmpty()) {
                    // Outset the fixed bounds and check if the touch is outside all apps
                    Rect tmpRect = new Rect(mFixedBounds);
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

    /**
     * Returns the predicted app in the prediction bar given a set of local coordinates.
     */
    private View findPredictedAppAtCoordinate(int x, int y) {
        Rect hitRect = new Rect();

        // Ensure we aren't hitting the search bar
        int[] coord = {x, y};
        Utilities.mapCoordInSelfToDescendent(mHeaderView, this, coord);
        mHeaderView.getHitRect(hitRect);
        if (hitRect.contains(coord[0], coord[1])) {
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
     * Shows the search field.
     */
    private void showSearchField() {
        // Show the search bar and focus the search
        final int translationX = Utilities.pxFromDp(SEARCH_TRANSLATION_X_DP,
                getContext().getResources().getDisplayMetrics());
        mSearchBarContainerView.setVisibility(View.VISIBLE);
        mSearchBarContainerView.setAlpha(0f);
        mSearchBarContainerView.setTranslationX(translationX);
        mSearchBarContainerView.animate()
                .alpha(1f)
                .translationX(0)
                .setDuration(FADE_IN_DURATION)
                .withLayer()
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mSearchBarEditView.requestFocus();
                        getInputMethodManager().showSoftInput(mSearchBarEditView,
                                InputMethodManager.SHOW_IMPLICIT);
                    }
                });
        mSearchButtonView.animate()
                .alpha(0f)
                .translationX(-translationX)
                .setDuration(FADE_OUT_DURATION)
                .withLayer();
    }

    /**
     * Hides the search field.
     */
    private void hideSearchField(boolean animated, final boolean returnFocusToRecyclerView) {
        final boolean resetTextField = mSearchBarEditView.getText().toString().length() > 0;
        final int translationX = Utilities.pxFromDp(SEARCH_TRANSLATION_X_DP,
                getContext().getResources().getDisplayMetrics());
        if (animated) {
            // Hide the search bar and focus the recycler view
            mSearchBarContainerView.animate()
                    .alpha(0f)
                    .translationX(0)
                    .setDuration(FADE_IN_DURATION)
                    .withLayer()
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            mSearchBarContainerView.setVisibility(View.INVISIBLE);
                            if (resetTextField) {
                                mSearchBarEditView.setText("");
                            }
                            mApps.setFilter(null);
                            if (returnFocusToRecyclerView) {
                                mAppsRecyclerView.requestFocus();
                            }
                        }
                    });
            mSearchButtonView.setTranslationX(-translationX);
            mSearchButtonView.animate()
                    .alpha(1f)
                    .translationX(0)
                    .setDuration(FADE_OUT_DURATION)
                    .withLayer();
        } else {
            mSearchBarContainerView.setVisibility(View.INVISIBLE);
            if (resetTextField) {
                mSearchBarEditView.setText("");
            }
            mApps.setFilter(null);
            mSearchButtonView.setAlpha(1f);
            mSearchButtonView.setTranslationX(0f);
            if (returnFocusToRecyclerView) {
                mAppsRecyclerView.requestFocus();
            }
        }
        getInputMethodManager().hideSoftInputFromWindow(getWindowToken(), 0);
    }

    /**
     * Updates the visibility of the prediction bar.
     * @return whether the prediction bar is visible
     */
    private boolean updatePredictionBarVisibility() {
        boolean showPredictionBar = !mApps.getPredictedApps().isEmpty() && (!mApps.hasFilter() ||
                mSearchBarEditView.getEditableText().toString().isEmpty());
        if (showPredictionBar) {
            mPredictionBarView.setVisibility(View.VISIBLE);
        } else if (!showPredictionBar) {
            mPredictionBarView.setVisibility(View.INVISIBLE);
        }
        return showPredictionBar;
    }

    /**
     * Returns an input method manager.
     */
    private InputMethodManager getInputMethodManager() {
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }
}
