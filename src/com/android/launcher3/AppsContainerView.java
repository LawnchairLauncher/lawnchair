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
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
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
    @Thunk AppsContainerRecyclerView mAppsListView;
    private EditText mSearchBar;
    private int mNumAppsPerRow;
    private Point mLastTouchDownPos = new Point();
    private Rect mPadding = new Rect();
    private int mContentMarginStart;

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
     * Scrolls this list view to the top.
     */
    public void scrollToTop() {
        mAppsListView.scrollToPosition(0);
    }

    /**
     * Returns the content view used for the launcher transitions.
     */
    public View getContentView() {
        return findViewById(R.id.apps_list);
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
        mSearchBar = (EditText) findViewById(R.id.app_search_box);
        mSearchBar.addTextChangedListener(this);
        mSearchBar.setOnEditorActionListener(this);
        mAppsListView = (AppsContainerRecyclerView) findViewById(R.id.apps_list_view);
        mAppsListView.setApps(mApps);
        mAppsListView.setNumAppsPerRow(mNumAppsPerRow);
        mAppsListView.setLayoutManager(mLayoutManager);
        mAppsListView.setAdapter(mAdapter);
        mAppsListView.setHasFixedSize(true);
        if (isRtl) {
            mAppsListView.setPadding(
                    mAppsListView.getPaddingLeft(),
                    mAppsListView.getPaddingTop(),
                    mAppsListView.getPaddingRight() + mContentMarginStart,
                    mAppsListView.getPaddingBottom());
        } else {
            mAppsListView.setPadding(
                    mAppsListView.getPaddingLeft() + mContentMarginStart,
                    mAppsListView.getPaddingTop(),
                    mAppsListView.getPaddingRight(),
                    mAppsListView.getPaddingBottom());
        }
        if (mItemDecoration != null) {
            mAppsListView.addItemDecoration(mItemDecoration);
        }
        mPadding.set(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
                getPaddingBottom());
    }

    @Override
    public void setInsets(Rect insets) {
        setPadding(mPadding.left + insets.left, mPadding.top + insets.top,
                mPadding.right + insets.right, mPadding.bottom + insets.bottom);
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN ||
                ev.getAction() == MotionEvent.ACTION_MOVE) {
            mLastTouchDownPos.set((int) ev.getX(), (int) ev.getY());
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
        mLauncher.getWorkspace().beginDragShared(v, mLastTouchDownPos, this, false);

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
        return true;
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
                public boolean retainApp(AppInfo info) {
                    String title = info.title.toString();
                    String sectionName = mApps.getSectionNameForApp(info);
                    return sectionName.toLowerCase().contains(filterText) ||
                            title.toLowerCase().replaceAll("\\s+", "").contains(filterText);
                }
            });
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (ALLOW_SINGLE_APP_LAUNCH && actionId == EditorInfo.IME_ACTION_DONE) {
            List<AppInfo> appsWithoutSections = mApps.getAppsWithoutSectionBreaks();
            List<AppInfo> apps = mApps.getApps();
            if (appsWithoutSections.size() == 1) {
                mAppsListView.getChildAt(apps.indexOf(appsWithoutSections.get(0))).performClick();
                InputMethodManager imm = (InputMethodManager)
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
            }
            return true;
        }
        return false;
    }

    @Override
    public View getContent() {
        return null;
    }

    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        if (!toWorkspace) {
            // Disable the focus so that the search bar doesn't get focus
            mSearchBar.setFocusableInTouchMode(false);
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
            // Clear the search bar
            mSearchBar.setText("");
        } else {
            mSearchBar.setFocusableInTouchMode(true);
        }
    }
}
