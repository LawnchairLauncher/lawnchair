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

import static com.android.launcher3.config.FeatureFlags.ALL_APPS_GONE_VISIBILITY;
import static com.android.launcher3.config.FeatureFlags.ENABLE_ALL_APPS_RV_PREINFLATION;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import static com.android.launcher3.logger.LauncherAtom.SearchResultContainer;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_PERSONAL_SCROLLED_DOWN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_PERSONAL_SCROLLED_UP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_SCROLLED_UNKNOWN_DIRECTION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_SEARCH_SCROLLED_DOWN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_SEARCH_SCROLLED_UP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_VERTICAL_SWIPE_BEGIN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_VERTICAL_SWIPE_END;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WORK_FAB_BUTTON_COLLAPSE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WORK_FAB_BUTTON_EXTEND;
import static com.android.launcher3.recyclerview.AllAppsRecyclerViewPoolKt.EXTRA_ICONS_COUNT;
import static com.android.launcher3.recyclerview.AllAppsRecyclerViewPoolKt.PREINFLATE_ICONS_ROW_COUNT;
import static com.android.launcher3.util.LogConfig.SEARCH_LOGGING;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.FastScrollRecyclerView;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

/**
 * A RecyclerView with custom fast scroll support for the all apps view.
 */
public class AllAppsRecyclerView extends FastScrollRecyclerView {
    protected static final String TAG = "AllAppsRecyclerView";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_LATENCY = Utilities.isPropertyEnabled(SEARCH_LOGGING);
    private Consumer<View> mChildAttachedConsumer;

    protected final int mNumAppsPerRow;
    private final AllAppsFastScrollHelper mFastScrollHelper;
    private int mCumulativeVerticalScroll;

    protected AlphabeticalAppsList<?> mApps;

    public AllAppsRecyclerView(Context context) {
        this(context, null);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr);
        mNumAppsPerRow = LauncherAppState.getIDP(context).numColumns;
        mFastScrollHelper = new AllAppsFastScrollHelper(this);
    }

    /**
     * Sets the list of apps in this view, used to determine the fastscroll position.
     */
    public void setApps(AlphabeticalAppsList<?> apps) {
        mApps = apps;
    }

    public AlphabeticalAppsList<?> getApps() {
        return mApps;
    }

    protected void updatePoolSize() {
        updatePoolSize(false);
    }

    void updatePoolSize(boolean hasWorkProfile) {
        DeviceProfile grid = ActivityContext.lookupContext(getContext()).getDeviceProfile();
        RecyclerView.RecycledViewPool pool = getRecycledViewPool();
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_ALL_APPS_DIVIDER, 1);

        // By default the max num of pool size for app icons is num of app icons in one page of
        // all apps.
        int maxPoolSizeForAppIcons = grid.getMaxAllAppsRowCount()
                * grid.numShownAllAppsColumns;
        if (ALL_APPS_GONE_VISIBILITY.get() && ENABLE_ALL_APPS_RV_PREINFLATION.get()) {
            // If we set all apps' hidden visibility to GONE and enable pre-inflation, we want to
            // preinflate one page of all apps icons plus [PREINFLATE_ICONS_ROW_COUNT] rows +
            // [EXTRA_ICONS_COUNT]. Thus we need to bump the max pool size of app icons accordingly.
            maxPoolSizeForAppIcons +=
                    PREINFLATE_ICONS_ROW_COUNT * grid.numShownAllAppsColumns + EXTRA_ICONS_COUNT;
        }
        if (hasWorkProfile) {
            maxPoolSizeForAppIcons *= 2;
        }
        pool.setMaxRecycledViews(
                AllAppsGridAdapter.VIEW_TYPE_ICON, maxPoolSizeForAppIcons);
    }

    @Override
    public void onDraw(Canvas c) {
        if (DEBUG) {
            Log.d(TAG, "onDraw at = " + System.currentTimeMillis());
        }
        if (DEBUG_LATENCY) {
            Log.d(SEARCH_LOGGING,  getClass().getSimpleName() + " onDraw; time stamp = "
                    + System.currentTimeMillis());
        }
        super.onDraw(c);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updatePoolSize();
    }

    public void onSearchResultsChanged() {
        // Always scroll the view to the top so the user can see the changed results
        scrollToTop();
    }

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);

        StatsLogManager mgr = ActivityContext.lookupContext(getContext()).getStatsLogManager();
        switch (state) {
            case SCROLL_STATE_DRAGGING:
                mCumulativeVerticalScroll = 0;
                requestFocus();
                mgr.logger().sendToInteractionJankMonitor(
                        LAUNCHER_ALLAPPS_VERTICAL_SWIPE_BEGIN, this);
                ActivityContext.lookupContext(getContext()).hideKeyboard();
                break;
            case SCROLL_STATE_IDLE:
                mgr.logger().sendToInteractionJankMonitor(
                        LAUNCHER_ALLAPPS_VERTICAL_SWIPE_END, this);
                logCumulativeVerticalScroll();
                break;
        }
    }

    @Override
    public void onScrolled(int dx, int dy) {
        super.onScrolled(dx, dy);
        mCumulativeVerticalScroll += dy;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public CharSequence scrollToPositionAtProgress(float touchFraction) {
        int rowCount = mApps.getNumAppRows();
        if (rowCount == 0) {
            return "";
        }

        // Find the fastscroll section that maps to this touch fraction
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollSections =
                mApps.getFastScrollerSections();
        int count = fastScrollSections.size();
        if (count == 0) {
            return "";
        }
        int index = Utilities.boundToRange((int) (touchFraction * count), 0, count - 1);
        AlphabeticalAppsList.FastScrollSectionInfo section = fastScrollSections.get(index);
        mFastScrollHelper.smoothScrollToSection(section);
        return section.sectionName;
    }

    @Override
    public void onFastScrollCompleted() {
        super.onFastScrollCompleted();
        mFastScrollHelper.onFastScrollCompleted();
    }

    @Override
    protected boolean isPaddingOffsetRequired() {
        return true;
    }

    @Override
    protected int getTopPaddingOffset() {
        return -getPaddingTop();
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void onUpdateScrollbar(int dy) {
        if (mApps == null) {
            return;
        }
        List<AllAppsGridAdapter.AdapterItem> items = mApps.getAdapterItems();

        // Skip early if there are no items or we haven't been measured
        if (items.isEmpty() || mNumAppsPerRow == 0 || getChildCount() == 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        // Skip early if, there no child laid out in the container.
        int scrollY = computeVerticalScrollOffset();
        if (scrollY < 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        // Only show the scrollbar if there is height to be scrolled
        int availableScrollBarHeight = getAvailableScrollBarHeight();
        int availableScrollHeight = getAvailableScrollHeight();
        if (availableScrollHeight <= 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        if (mScrollbar.isThumbDetached()) {
            if (!mScrollbar.isDraggingThumb()) {
                // Calculate the current scroll position, the scrollY of the recycler view accounts
                // for the view padding, while the scrollBarY is drawn right up to the background
                // padding (ignoring padding)
                int scrollBarY = (int)
                        (((float) scrollY / availableScrollHeight) * availableScrollBarHeight);

                int thumbScrollY = mScrollbar.getThumbOffsetY();
                int diffScrollY = scrollBarY - thumbScrollY;
                if (diffScrollY * dy > 0f) {
                    // User is scrolling in the same direction the thumb needs to catch up to the
                    // current scroll position.  We do this by mapping the difference in movement
                    // from the original scroll bar position to the difference in movement necessary
                    // in the detached thumb position to ensure that both speed towards the same
                    // position at either end of the list.
                    if (dy < 0) {
                        int offset = (int) ((dy * thumbScrollY) / (float) scrollBarY);
                        thumbScrollY += Math.max(offset, diffScrollY);
                    } else {
                        int offset = (int) ((dy * (availableScrollBarHeight - thumbScrollY)) /
                                (float) (availableScrollBarHeight - scrollBarY));
                        thumbScrollY += Math.min(offset, diffScrollY);
                    }
                    thumbScrollY = Math.max(0, Math.min(availableScrollBarHeight, thumbScrollY));
                    mScrollbar.setThumbOffsetY(thumbScrollY);
                    if (scrollBarY == thumbScrollY) {
                        mScrollbar.reattachThumbToScroll();
                    }
                } else {
                    // User is scrolling in an opposite direction to the direction that the thumb
                    // needs to catch up to the scroll position.  Do nothing except for updating
                    // the scroll bar x to match the thumb width.
                    mScrollbar.setThumbOffsetY(thumbScrollY);
                }
            }
        } else {
            synchronizeScrollBarThumbOffsetToViewScroll(scrollY, availableScrollHeight);
        }
    }

    /**
     * This will be called just before a new child is attached to the window. Passing in null will
     * remove the consumer.
     */
    protected void setChildAttachedConsumer(@Nullable Consumer<View> childAttachedConsumer) {
        mChildAttachedConsumer = childAttachedConsumer;
    }

    @Override
    public void onChildAttachedToWindow(@NonNull View child) {
        if (mChildAttachedConsumer != null) {
            mChildAttachedConsumer.accept(child);
        }
        super.onChildAttachedToWindow(child);
    }

    @Override
    public int getScrollBarTop() {
        return getResources().getDimensionPixelOffset(R.dimen.all_apps_header_top_padding);
    }

    @Override
    public int getScrollBarMarginBottom() {
        return getRootWindowInsets() == null ? 0
                : getRootWindowInsets().getSystemWindowInsetBottom();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void logCumulativeVerticalScroll() {
        ActivityContext context = ActivityContext.lookupContext(getContext());
        StatsLogManager mgr = context.getStatsLogManager();
        ActivityAllAppsContainerView<?> appsView = context.getAppsView();
        ExtendedEditText editText = appsView.getSearchUiManager().getEditText();
        ContainerInfo containerInfo = ContainerInfo.newBuilder().setSearchResultContainer(
                SearchResultContainer
                        .newBuilder()
                        .setQueryLength((editText == null) ? -1 : editText.length())).build();
        if (mCumulativeVerticalScroll == 0) {
            // mCumulativeVerticalScroll == 0 when user comes back to original position, we
            // don't know the direction of scrolling.
            mgr.logger().withContainerInfo(containerInfo).log(
                    LAUNCHER_ALLAPPS_SCROLLED_UNKNOWN_DIRECTION);
            return;
        } else if (appsView.isSearching()) {
            // In search results page
            mgr.logger().withContainerInfo(containerInfo).log((mCumulativeVerticalScroll > 0)
                    ? LAUNCHER_ALLAPPS_SEARCH_SCROLLED_DOWN
                    : LAUNCHER_ALLAPPS_SEARCH_SCROLLED_UP);
            return;
        } else if (appsView.mViewPager != null) {
            int currentPage = appsView.mViewPager.getCurrentPage();
            if (currentPage == ActivityAllAppsContainerView.AdapterHolder.WORK) {
                // In work A-Z list
                mgr.logger().withContainerInfo(containerInfo).log((mCumulativeVerticalScroll > 0)
                        ? LAUNCHER_WORK_FAB_BUTTON_COLLAPSE
                        : LAUNCHER_WORK_FAB_BUTTON_EXTEND);
                return;
            }
        }
        // In personal A-Z list
        mgr.logger().withContainerInfo(containerInfo).log((mCumulativeVerticalScroll > 0)
                ? LAUNCHER_ALLAPPS_PERSONAL_SCROLLED_DOWN
                : LAUNCHER_ALLAPPS_PERSONAL_SCROLLED_UP);
    }
}
