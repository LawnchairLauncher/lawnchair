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
package ch.deletescape.lawnchair.allapps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
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
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ch.deletescape.lawnchair.AppInfo;
import ch.deletescape.lawnchair.BaseContainerView;
import ch.deletescape.lawnchair.BubbleTextView;
import ch.deletescape.lawnchair.CellLayout;
import ch.deletescape.lawnchair.DeleteDropTarget;
import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.DragSource;
import ch.deletescape.lawnchair.DropTarget;
import ch.deletescape.lawnchair.ExtendedEditText;
import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherTransitionable;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.Workspace;
import ch.deletescape.lawnchair.allapps.theme.IAllAppsThemer;
import ch.deletescape.lawnchair.anim.SpringAnimationHandler;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.dragndrop.DragOptions;
import ch.deletescape.lawnchair.folder.Folder;
import ch.deletescape.lawnchair.graphics.TintedDrawableSpan;
import ch.deletescape.lawnchair.keyboard.FocusedItemDecorator;
import ch.deletescape.lawnchair.util.ComponentKey;
import ch.deletescape.lawnchair.util.PackageUserKey;


/**
 * A merge algorithm that merges every section indiscriminately.
 */
final class FullMergeAlgorithm implements AlphabeticalAppsList.MergeAlgorithm {

    @Override
    public boolean continueMerging(AlphabeticalAppsList.SectionInfo section) {
        // Only merge apps
        return section.firstAppItem.viewType == AllAppsGridAdapter.VIEW_TYPE_ICON;
    }
}

/**
 * The all apps view container.
 */
public class AllAppsContainerView extends BaseContainerView implements DragSource,
        LauncherTransitionable, View.OnLongClickListener, AllAppsSearchBarController.Callbacks {

    private final Launcher mLauncher;
    private final AlphabeticalAppsList mApps;
    private final AllAppsGridAdapter mAdapter;
    private final SpringAnimationHandler<AllAppsGridAdapter.ViewHolder> mSpringAnimationHandler;
    private final RecyclerView.LayoutManager mLayoutManager;

    // The computed bounds of the container
    private final Rect mContentBounds = new Rect();
    private final boolean mUseRoundSearchBar;

    private AllAppsRecyclerView mAppsRecyclerView;
    private AllAppsSearchBarController mSearchBarController;

    private View mSearchContainer;
    private ExtendedEditText mSearchInput;
    private HeaderElevationController mElevationController;
    private int mSearchContainerOffsetTop;

    private SpannableStringBuilder mSearchQueryBuilder = null;

    private int mSectionNamesMargin;
    private int mNumAppsPerRow;
    private int mRecyclerViewBottomPadding;
    // This coordinate is relative to this container view
    private final Point mBoundsCheckLastTouchDownPos = new Point(-1, -1);

    private AllAppsBackground mAllAppsBackground;
    private IAllAppsThemer mTheme;

    public AllAppsContainerView(Context context) {
        this(context, null);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(FeatureFlags.INSTANCE.applyDarkTheme(context, FeatureFlags.DARK_ALLAPPS), attrs, defStyleAttr);
        Resources res = context.getResources();

        mLauncher = Launcher.getLauncher(context);
        mSectionNamesMargin = res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin);
        mApps = new AlphabeticalAppsList(context);
        mAdapter = new AllAppsGridAdapter(mLauncher, mApps, mLauncher, this);
        mSpringAnimationHandler = mAdapter.getSpringAnimationHandler();
        mApps.setAdapter(mAdapter);
        mLayoutManager = mAdapter.getLayoutManager();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (!grid.isVerticalBarLayout()) {
            mRecyclerViewBottomPadding = 0;
            setPadding(0, 0, 0, 0);
        } else {
            mRecyclerViewBottomPadding =
                    res.getDimensionPixelSize(R.dimen.all_apps_list_bottom_padding);
        }
        mSearchQueryBuilder = new SpannableStringBuilder();
        Selection.setSelection(mSearchQueryBuilder, 0);
        mUseRoundSearchBar = Utilities.getPrefs(context).getUseRoundSearchBar();
        mTheme = Utilities.getThemer().allAppsTheme(context);
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

//    public void setSearchBarVisible(boolean visible) {
//        if (visible) {
//            mSearchBarController.setVisibility(View.VISIBLE);
//        } else {
//            mSearchBarController.setVisibility(View.INVISIBLE);
//        }
//    }

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
        Utilities.mapCoordInSelfToDescendent(mAppsRecyclerView, this, point);

        // IF the MotionEvent is inside the search box, and the container keeps on receiving
        // touch input, container should move down.
        if (mLauncher.getDragLayer().isEventOverView(mSearchContainer, ev)) {
            return true;
        }

        // IF the MotionEvent is inside the thumb, container should not be pulled down.
        if (mAppsRecyclerView.getScrollBar().isNearThumb(point[0], point[1])) {
            return false;
        }

        // IF a shortcuts container is open, container should not be pulled down.
        if (mLauncher.getOpenShortcutsContainer() != null) {
            return false;
        }

        // IF scroller is at the very top OR there is no scroll bar because there is probably not
        // enough items to scroll, THEN it's okay for the container to be pulled down.
        return mAppsRecyclerView.getScrollBar().getThumbOffset().y <= 0;
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
        if (!Utilities.getPrefs(getContext()).getKeepScrollState()) {
            scrollToTop();
        }
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
        mSearchInput = findViewById(R.id.search_box_input);
        int hintAndCursorColor = mTheme.getSearchBarHintTextColor();
        if (!mUseRoundSearchBar)
            mSearchInput.setHintTextColor(hintAndCursorColor);
        mSearchInput.setCursorColor(hintAndCursorColor);

        if (mTheme.getSearchTextColor() != 0) {
            mSearchInput.setTextColor(mTheme.getSearchTextColor());
        }

        // Update the hint to contain the icon.
        // Prefix the original hint with two spaces. The first space gets replaced by the icon
        // using span. The second space is used for a singe space character between the hint
        // and the icon.
        SpannableString spanned = new SpannableString("  " + mSearchInput.getHint());
        spanned.setSpan(new TintedDrawableSpan(getContext(), R.drawable.ic_allapps_search),
                0, 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        mSearchInput.setHint(spanned);

        mSearchContainerOffsetTop = getResources().getDimensionPixelSize(
                R.dimen.all_apps_search_bar_margin_top);


        // Load the all apps recycler view
        mAppsRecyclerView = findViewById(R.id.apps_list_view);
        mAppsRecyclerView.setApps(mApps);
        mAppsRecyclerView.setLayoutManager(mLayoutManager);
        mAppsRecyclerView.setAdapter(mAdapter);
        mAppsRecyclerView.setHasFixedSize(true);
        mAppsRecyclerView.setItemAnimator(null);
        mAppsRecyclerView.setSpringAnimationHandler(mSpringAnimationHandler);
        if (!mUseRoundSearchBar) {
            mElevationController = new HeaderElevationController.ControllerVL(mSearchContainer);
            mAppsRecyclerView.addOnScrollListener(mElevationController);
            mAppsRecyclerView.setElevationController(mElevationController);
        }

        FocusedItemDecorator focusedItemDecorator = new FocusedItemDecorator(mAppsRecyclerView);
        mAppsRecyclerView.addItemDecoration(focusedItemDecorator);
        mAppsRecyclerView.preMeasureViews(mAdapter);
        mAdapter.setIconFocusListener(focusedItemDecorator.getFocusListener());

        getRevealView().setVisibility(View.VISIBLE);
        getContentView().setVisibility(View.VISIBLE);
        getContentView().setBackground(null);

        mAllAppsBackground = (AllAppsBackground) getRevealView();
        mBaseDrawable = mAllAppsBackground.getBaseDrawable();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthPx = MeasureSpec.getSize(widthMeasureSpec);
        int heightPx = MeasureSpec.getSize(heightMeasureSpec);
        updatePaddingsAndMargins(widthPx, heightPx);
        mContentBounds.set(mContainerPaddingLeft, 0, widthPx - mContainerPaddingRight, heightPx);

        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (mNumAppsPerRow != grid.inv.numColumnsDrawer) {
            mNumAppsPerRow = grid.inv.numColumnsDrawer;

            int numAppsPerRow = mTheme.numIconPerRow(mNumAppsPerRow);

            mAppsRecyclerView.setNumAppsPerRow(grid, numAppsPerRow);
            mAdapter.setNumAppsPerRow(numAppsPerRow);
            mApps.setNumAppsPerRow(numAppsPerRow, new FullMergeAlgorithm());
            if (numAppsPerRow > 0) {
                int rvPadding = mAppsRecyclerView.getPaddingStart(); // Assumes symmetry
                final int thumbMaxWidth =
                        getResources().getDimensionPixelSize(
                                R.dimen.container_fastscroll_thumb_max_width);
                mSearchContainer.setPadding(
                        rvPadding - mContainerPaddingLeft + thumbMaxWidth,
                        mSearchContainer.getPaddingTop(),
                        rvPadding - mContainerPaddingRight + thumbMaxWidth,
                        mSearchContainer.getPaddingBottom());
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Update the background and padding of the Apps view and children.  Instead of insetting the
     * container view, we inset the background and padding of the recycler view to allow for the
     * recycler view to handle touch events (for fast scrolling) all the way to the edge.
     */
    private void updatePaddingsAndMargins(int widthPx, int heightPx) {
        Rect bgPadding = new Rect();
        getRevealView().getBackground().getPadding(bgPadding);

        mAppsRecyclerView.updateBackgroundPadding(bgPadding);
        mAdapter.updateBackgroundPadding(bgPadding);

        // Pad the recycler view by the background padding plus the start margin (for the section
        // names)
        int maxScrollBarWidth = mAppsRecyclerView.getMaxScrollbarWidth();
        int startInset = Math.max(mSectionNamesMargin, maxScrollBarWidth);
        if (Utilities.isRtl(getResources())) {
            mAppsRecyclerView.setPadding(bgPadding.left + maxScrollBarWidth, 0, bgPadding.right
                    + startInset, mRecyclerViewBottomPadding);
        } else {
            mAppsRecyclerView.setPadding(bgPadding.left + startInset, 0, bgPadding.right +
                    maxScrollBarWidth, mRecyclerViewBottomPadding);
        }

        MarginLayoutParams lp = (MarginLayoutParams) mSearchContainer.getLayoutParams();
        lp.leftMargin = bgPadding.left;
        lp.rightMargin = bgPadding.right;

        // Clip the view to the left and right edge of the background to
        // to prevent shadows from rendering beyond the edges
        final Rect newClipBounds = new Rect(
                bgPadding.left, 0, widthPx - bgPadding.right, heightPx);
        setClipBounds(newClipBounds);

        // Allow the overscroll effect to reach the edges of the view
        mAppsRecyclerView.setClipToPadding(false);

        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (!grid.isVerticalBarLayout()) {
            MarginLayoutParams mlp = (MarginLayoutParams) mAppsRecyclerView.getLayoutParams();

            Rect insets = mLauncher.getDragLayer().getInsets();
            getContentView().setPadding(0, 0, 0, 0);
            int height = insets.top;
            if (mUseRoundSearchBar) {
                height += getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_round_height);
                height += getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_round_margin_bottom);
            } else if (!grid.isVerticalBarLayout()) {
                height += grid.inv.searchHeightAddition;
            }

            mlp.topMargin = height;
            mAppsRecyclerView.setLayoutParams(mlp);

            mSearchContainer.setPadding(
                    mSearchContainer.getPaddingLeft(),
                    insets.top + mSearchContainerOffsetTop,
                    mSearchContainer.getPaddingRight(),
                    mSearchContainer.getPaddingBottom());
            lp.height = height;

            View navBarBg = findViewById(R.id.nav_bar_bg);
            ViewGroup.LayoutParams params = navBarBg.getLayoutParams();
            params.height = insets.bottom;
            navBarBg.setLayoutParams(params);
            navBarBg.setVisibility(View.VISIBLE);
        }
        if (mUseRoundSearchBar) {
            View divider = findViewById(R.id.search_bar_divider);
            MarginLayoutParams dividerParams = (MarginLayoutParams) divider.getLayoutParams();
            dividerParams.topMargin = lp.height - dividerParams.height;
            divider.setLayoutParams(dividerParams);
        }
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

    @Override
    public boolean onLongClick(View v) {
        // Return early if this is not initiated from a touch
        if (!v.isInTouchMode()) return false;
        // When we have exited all apps or are in transition, disregard long clicks

        if (!mLauncher.isAppsViewVisible() ||
                mLauncher.getWorkspace().isSwitchingState()) return false;
        // Return if global dragging is not enabled or we are already dragging
        if (!mLauncher.isDraggingEnabled()) return false;
        if (mLauncher.getDragController().isDragging()) return false;

        if (v instanceof AllAppsIconRowView) {
            ((AllAppsIconRowView) v).beginDrag(this);
            return false;
        }

        // Start the drag
        DragOptions dragOptions = new DragOptions();
        mLauncher.getWorkspace().beginDragShared(v, this, dragOptions);
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

        // Display an error message if the drag failed due to there not being enough space on the
        // target layout we were dropping on.
        if (!success) {
            boolean showOutOfSpaceMessage = false;
            if (target instanceof Workspace && !mLauncher.getDragController().isDeferringDrag()) {
                int currentScreen = mLauncher.getCurrentWorkspaceScreen();
                Workspace workspace = (Workspace) target;
                CellLayout layout = (CellLayout) workspace.getChildAt(currentScreen);
                ItemInfo itemInfo = d.dragInfo;
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
    public void onLauncherTransitionPrepare(boolean multiplePagesVisible) {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionStart() {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionStep(float t) {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionEnd(boolean toWorkspace) {
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
                    }
                } else {
                    // Check if the touch is outside all apps
                    if (ev.getX() < getPaddingLeft() ||
                            ev.getX() > (getWidth() - getPaddingRight())) {
                        mBoundsCheckLastTouchDownPos.set(x, y);
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
                        Launcher launcher = Launcher.getLauncher(getContext());
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

    public boolean shouldRestoreImeState() {
        return !TextUtils.isEmpty(mSearchInput.getText());
    }


    public void updateIconBadges(Set set) {
        PackageUserKey packageUserKey = new PackageUserKey(null, null);
        int childCount = this.mAppsRecyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childAt = this.mAppsRecyclerView.getChildAt(i);
            if ((childAt instanceof BubbleTextView) && childAt.getTag() instanceof ItemInfo) {
                ItemInfo itemInfo = (ItemInfo) childAt.getTag();
                if (packageUserKey.updateFromItemInfo(itemInfo) && set.contains(packageUserKey)) {
                    ((BubbleTextView) childAt).applyBadgeState(itemInfo, true);
                }
            }
        }
    }

    public void setStatusBarHeight(float height) {
        mAllAppsBackground.setStatusBarHeight(height);
    }

    @Override
    public void setRevealDrawableColor(int color) {
        mAllAppsBackground.setBackgroundColor(color);
    }

    public void setWallpaperTranslation(float translation) {
        mAllAppsBackground.setWallpaperTranslation(translation + getTop());
    }

    public void setBlurOpacity(int opacity) {
        mAllAppsBackground.setBlurOpacity(opacity);
    }

    public void setAppIconTextStyle(int color, int maxLines) {
        mAdapter.setAppIconTextStyle(color, maxLines);
        mAdapter.notifyDataSetChanged();
    }

    public SpringAnimationHandler<AllAppsGridAdapter.ViewHolder> getSpringAnimationHandler() {
        return mSpringAnimationHandler;
    }

}
