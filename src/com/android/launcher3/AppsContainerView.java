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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.util.Thunk;

import java.util.List;


/**
 * The all apps list view container.
 */
public class AppsContainerView extends FrameLayout implements DragSource, Insettable, TextWatcher,
        TextView.OnEditorActionListener, LauncherTransitionable, View.OnTouchListener,
        View.OnLongClickListener {

    private static final boolean ALLOW_SINGLE_APP_LAUNCH = true;

    private static final int GRID_LAYOUT = 0;
    private static final int LIST_LAYOUT = 1;
    private static final int USE_LAYOUT = GRID_LAYOUT;

    @Thunk Launcher mLauncher;
    @Thunk AlphabeticalAppsList mApps;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private RecyclerView.ItemDecoration mItemDecoration;

    private LinearLayout mContentView;
    @Thunk AppsContainerRecyclerView mAppsRecyclerView;
    private EditText mSearchBarView;
    
    private int mNumAppsPerRow;
    private Point mLastTouchDownPos = new Point(-1, -1);
    private Point mLastTouchPos = new Point();
    private Rect mInsets = new Rect();
    private Rect mFixedBounds = new Rect();
    private int mContentMarginStart;
    // Normal container insets
    private int mContainerInset;
    // Fixed bounds container insets
    private int mFixedBoundsContainerInset;

    public AppsContainerView(Context context) {
        this(context, null);
    }

    public AppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        Resources res = context.getResources();

        mContainerInset = context.getResources().getDimensionPixelSize(
                R.dimen.apps_container_inset);
        mFixedBoundsContainerInset = context.getResources().getDimensionPixelSize(
                R.dimen.apps_container_fixed_bounds_inset);
        mLauncher = (Launcher) context;
        mApps = new AlphabeticalAppsList(context);
        if (USE_LAYOUT == GRID_LAYOUT) {
            mNumAppsPerRow = grid.appsViewNumCols;
            AppsGridAdapter adapter = new AppsGridAdapter(context, mApps, mNumAppsPerRow, this,
                    mLauncher, this);
            adapter.setEmptySearchText(res.getString(R.string.loading_apps_message));
            mLayoutManager = adapter.getLayoutManager(context);
            mItemDecoration = adapter.getItemDecoration();
            mAdapter = adapter;
            mContentMarginStart = adapter.getContentMarginStart();
        } else if (USE_LAYOUT == LIST_LAYOUT) {
            mNumAppsPerRow = 1;
            AppsListAdapter adapter = new AppsListAdapter(context, mApps, this, mLauncher, this);
            adapter.setEmptySearchText(res.getString(R.string.loading_apps_message));
            mLayoutManager = adapter.getLayoutManager(context);
            mAdapter = adapter;
        }
        mApps.setAdapter(mAdapter);
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
     * Hides the search bar
     */
    public void hideSearchBar() {
        mSearchBarView.setVisibility(View.GONE);
        updateBackgrounds();
        updatePaddings();
    }

    /**
     * Scrolls this list view to the top.
     */
    public void scrollToTop() {
        mAppsRecyclerView.scrollToPosition(0);
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
        boolean isRtl = (getResources().getConfiguration().getLayoutDirection() ==
                LAYOUT_DIRECTION_RTL);
        if (USE_LAYOUT == GRID_LAYOUT) {
            ((AppsGridAdapter) mAdapter).setRtl(isRtl);
        }

        // Work around the search box getting first focus and showing the cursor by
        // proxying the focus from the content view to the recycler view directly
        mContentView = (LinearLayout) findViewById(R.id.apps_list);
        mContentView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (v == mContentView && hasFocus) {
                    mAppsRecyclerView.requestFocus();
                }
            }
        });
        mSearchBarView = (EditText) findViewById(R.id.app_search_box);
        if (mSearchBarView != null) {
            mSearchBarView.addTextChangedListener(this);
            mSearchBarView.setOnEditorActionListener(this);
        }
        mAppsRecyclerView = (AppsContainerRecyclerView) findViewById(R.id.apps_list_view);
        mAppsRecyclerView.setApps(mApps);
        mAppsRecyclerView.setNumAppsPerRow(mNumAppsPerRow);
        mAppsRecyclerView.setLayoutManager(mLayoutManager);
        mAppsRecyclerView.setAdapter(mAdapter);
        mAppsRecyclerView.setHasFixedSize(true);
        if (mItemDecoration != null) {
            mAppsRecyclerView.addItemDecoration(mItemDecoration);
        }
        updateBackgrounds();
        updatePaddings();
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        updatePaddings();
    }

    /**
     * Sets the fixed bounds for this Apps view.
     */
    public void setFixedBounds(Context context, Rect fixedBounds) {
        if (!fixedBounds.isEmpty() && !fixedBounds.equals(mFixedBounds)) {
            // Update the number of items in the grid
            LauncherAppState app = LauncherAppState.getInstance();
            DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
            if (grid.updateAppsViewNumCols(context.getResources(), fixedBounds.width())) {
                mNumAppsPerRow = grid.appsViewNumCols;
                mAppsRecyclerView.setNumAppsPerRow(mNumAppsPerRow);
                if (USE_LAYOUT == GRID_LAYOUT) {
                    ((AppsGridAdapter) mAdapter).setNumAppsPerRow(mNumAppsPerRow);
                }
            }

            mFixedBounds.set(fixedBounds);
        }
        updateBackgrounds();
        updatePaddings();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return handleTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return handleTouchEvent(ev);
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mLastTouchPos.set((int) ev.getX(), (int) ev.getY());
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
        mLauncher.getWorkspace().beginDragShared(v, mLastTouchPos, this, false);

        // We delay entering spring-loaded mode slightly to make sure the UI
        // thready is free of any work.
        postDelayed(new Runnable() {
            @Override
            public void run() {
                // We don't enter spring-loaded mode if the drag has been cancelled
                if (mLauncher.getDragController().isDragging()) {
                    // Go into spring loaded mode (must happen before we startDrag())
                    mLauncher.enterSpringLoadedDragMode();
                }
            }
        }, 150);

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
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
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
        if (s.toString().isEmpty()) {
            mApps.setFilter(null);
        } else {
            String formatStr = getResources().getString(R.string.apps_view_no_search_results);
            if (USE_LAYOUT == GRID_LAYOUT) {
                ((AppsGridAdapter) mAdapter).setEmptySearchText(String.format(formatStr,
                        s.toString()));
            } else {
                ((AppsListAdapter) mAdapter).setEmptySearchText(String.format(formatStr,
                        s.toString()));
            }

            final String filterText = s.toString().toLowerCase().replaceAll("\\s+", "");
            mApps.setFilter(new AlphabeticalAppsList.Filter() {
                @Override
                public boolean retainApp(AppInfo info, String sectionName) {
                    String title = info.title.toString();
                    return sectionName.toLowerCase().contains(filterText) ||
                            title.toLowerCase().replaceAll("\\s+", "").contains(filterText);
                }
            });
        }
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
                if (!item.isSectionHeader) {
                    mAppsRecyclerView.getChildAt(i).performClick();
                    InputMethodManager imm = (InputMethodManager)
                            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public View getContent() {
        return null;
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
        if (mSearchBarView != null) {
            if (toWorkspace) {
                // Clear the search bar
                mSearchBarView.setText("");
            }
        }
    }

    /**
     * Handles the touch events to dismiss all apps when clicking outside the bounds of the
     * recycler view.
     */
    private boolean handleTouchEvent(MotionEvent ev) {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mFixedBounds.isEmpty()) {
                    // Outset the fixed bounds and check if the touch is outside all apps
                    Rect tmpRect = new Rect(mFixedBounds);
                    tmpRect.inset(-grid.allAppsIconSizePx / 2, 0);
                    if (ev.getX() < tmpRect.left || ev.getX() > tmpRect.right) {
                        mLastTouchDownPos.set((int) ev.getX(), (int) ev.getY());
                        return true;
                    }
                } else {
                    // Check if the touch is outside all apps
                    if (ev.getX() < getPaddingLeft() ||
                            ev.getX() > (getWidth() - getPaddingRight())) {
                        mLastTouchDownPos.set((int) ev.getX(), (int) ev.getY());
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mLastTouchDownPos.x > -1) {
                    ViewConfiguration viewConfig = ViewConfiguration.get(getContext());
                    float dx = ev.getX() - mLastTouchDownPos.x;
                    float dy = ev.getY() - mLastTouchDownPos.y;
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
                mLastTouchDownPos.set(-1, -1);
                break;
        }
        return false;
    }

    /**
     * Update the padding of the Apps view and children.  To ensure that the RecyclerView has the
     * full width to handle touches right to the edge of the screen, we only apply the top and
     * bottom padding to the AppsContainerView and then the left/right padding on the RecyclerView
     * itself.  In particular, the left/right padding is applied to the background of the view,
     * and then additionally inset by the start margin.
     */
    private void updatePaddings() {
        boolean isRtl = (getResources().getConfiguration().getLayoutDirection() ==
                LAYOUT_DIRECTION_RTL);
        boolean hasSearchBar = (mSearchBarView != null) &&
                (mSearchBarView.getVisibility() == View.VISIBLE);

        if (mFixedBounds.isEmpty()) {
            // If there are no fixed bounds, then use the default padding and insets
            setPadding(mInsets.left, mContainerInset + mInsets.top, mInsets.right,
                    mContainerInset + mInsets.bottom);
        } else {
            // If there are fixed bounds, then we update the padding to reflect the fixed bounds.
            setPadding(mFixedBounds.left, mFixedBounds.top + mFixedBoundsContainerInset,
                    getMeasuredWidth() - mFixedBounds.right,
                    mInsets.bottom + mFixedBoundsContainerInset);
        }

        // Update the apps recycler view
        int inset = mFixedBounds.isEmpty() ? mContainerInset : mFixedBoundsContainerInset;
        if (isRtl) {
            mAppsRecyclerView.setPadding(inset, inset, inset + mContentMarginStart, inset);
        } else {
            mAppsRecyclerView.setPadding(inset + mContentMarginStart, inset, inset, inset);
        }

        // Update the search bar
        if (hasSearchBar) {
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) mSearchBarView.getLayoutParams();
            lp.leftMargin = lp.rightMargin = inset;
        }
    }

    /**
     * Update the background of the Apps view and children.
     */
    private void updateBackgrounds() {
        int inset = mFixedBounds.isEmpty() ? mContainerInset : mFixedBoundsContainerInset;
        boolean hasSearchBar = (mSearchBarView != null) &&
                (mSearchBarView.getVisibility() == View.VISIBLE);

        // Update the background of the reveal view and list to be inset with the fixed bound
        // insets instead of the default insets
        mAppsRecyclerView.setBackground(new InsetDrawable(
                getContext().getResources().getDrawable(
                        hasSearchBar ? R.drawable.apps_list_search_bg : R.drawable.apps_list_bg),
                inset, 0, inset, 0));
        getRevealView().setBackground(new InsetDrawable(
                getContext().getResources().getDrawable(R.drawable.apps_reveal_bg),
                inset, 0, inset, 0));
    }
}
