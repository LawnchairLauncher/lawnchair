/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.views.ActivityContext;
import com.android.systemui.plugins.AllAppsRow;
import com.android.systemui.plugins.AllAppsRow.OnHeightUpdatedListener;
import com.android.systemui.plugins.PluginListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class FloatingHeaderView extends LinearLayout implements
        ValueAnimator.AnimatorUpdateListener, PluginListener<AllAppsRow>, Insettable,
        OnHeightUpdatedListener {

    private final Rect mRVClip = new Rect(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    private final Rect mHeaderClip = new Rect(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    private final ValueAnimator mAnimator = ValueAnimator.ofInt(0, 0);
    private final Point mTempOffset = new Point();
    private final RecyclerView.OnScrollListener mOnScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {}

                @Override
                public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                    if (rv != mCurrentRV) {
                        return;
                    }

                    if (mAnimator.isStarted()) {
                        mAnimator.cancel();
                    }

                    int current = -mCurrentRV.computeVerticalScrollOffset();
                    boolean headerCollapsed = mHeaderCollapsed;
                    moved(current);
                    applyVerticalMove();
                    if (headerCollapsed != mHeaderCollapsed) {
                        ActivityAllAppsContainerView<?> parent =
                                (ActivityAllAppsContainerView<?>) getParent();
                        parent.invalidateHeader();
                    }
                }
            };

    protected final Map<AllAppsRow, PluginHeaderRow> mPluginRows = new ArrayMap<>();

    // These two values are necessary to ensure that the header protection is drawn correctly.
    private final int mTabsAdditionalPaddingTop;
    private final int mTabsAdditionalPaddingBottom;

    protected ViewGroup mTabLayout;
    private AllAppsRecyclerView mMainRV;
    private AllAppsRecyclerView mWorkRV;
    private SearchRecyclerView mSearchRV;
    private AllAppsRecyclerView mCurrentRV;
    protected int mSnappedScrolledY;
    private int mTranslationY;

    private boolean mForwardToRecyclerView;

    protected boolean mTabsHidden;
    protected int mMaxTranslation;

    // Whether the header has been scrolled off-screen.
    private boolean mHeaderCollapsed;
    // Whether floating rows like predicted apps are hidden.
    private boolean mFloatingRowsCollapsed;
    // Total height of all current floating rows. Collapsed rows == 0 height.
    private int mFloatingRowsHeight;

    // This is initialized once during inflation and stays constant after that. Fixed views
    // cannot be added or removed dynamically.
    private FloatingHeaderRow[] mFixedRows = FloatingHeaderRow.NO_ROWS;

    // Array of all fixed rows and plugin rows. This is initialized every time a plugin is
    // enabled or disabled, and represent the current set of all rows.
    private FloatingHeaderRow[] mAllRows = FloatingHeaderRow.NO_ROWS;

    public FloatingHeaderView(@NonNull Context context) {
        this(context, null);
    }

    public FloatingHeaderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTabsAdditionalPaddingTop = context.getResources()
                .getDimensionPixelSize(R.dimen.all_apps_header_top_adjustment);
        mTabsAdditionalPaddingBottom = context.getResources()
                .getDimensionPixelSize(R.dimen.all_apps_header_bottom_adjustment);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTabLayout = findViewById(R.id.tabs);

        // Find all floating header rows.
        ArrayList<FloatingHeaderRow> rows = new ArrayList<>();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child instanceof FloatingHeaderRow) {
                rows.add((FloatingHeaderRow) child);
            }
        }
        mFixedRows = rows.toArray(new FloatingHeaderRow[rows.size()]);
        mAllRows = mFixedRows;
        updateFloatingRowsHeight();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PluginManagerWrapper.INSTANCE.get(getContext()).addPluginListener(this,
                AllAppsRow.class, true /* allowMultiple */);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        PluginManagerWrapper.INSTANCE.get(getContext()).removePluginListener(this);
    }

    private void recreateAllRowsArray() {
        int pluginCount = mPluginRows.size();
        if (pluginCount == 0) {
            mAllRows = mFixedRows;
        } else {
            int count = mFixedRows.length;
            mAllRows = new FloatingHeaderRow[count + pluginCount];
            for (int i = 0; i < count; i++) {
                mAllRows[i] = mFixedRows[i];
            }

            for (PluginHeaderRow row : mPluginRows.values()) {
                mAllRows[count] = row;
                count++;
            }
        }
        updateFloatingRowsHeight();
    }

    @Override
    public void onPluginConnected(AllAppsRow allAppsRowPlugin, Context context) {
        PluginHeaderRow headerRow = new PluginHeaderRow(allAppsRowPlugin, this);
        addView(headerRow.mView, indexOfChild(mTabLayout));
        mPluginRows.put(allAppsRowPlugin, headerRow);
        recreateAllRowsArray();
        allAppsRowPlugin.setOnHeightUpdatedListener(this);
    }

    @Override
    public void onHeightUpdated() {
        int oldMaxHeight = mMaxTranslation;
        updateExpectedHeight();

        if (mMaxTranslation != oldMaxHeight || mFloatingRowsCollapsed) {
            ActivityAllAppsContainerView parent = (ActivityAllAppsContainerView) getParent();
            if (parent != null) {
                parent.setupHeader();
            }
        }
    }

    @Override
    public void onPluginDisconnected(AllAppsRow plugin) {
        PluginHeaderRow row = mPluginRows.get(plugin);
        removeView(row.mView);
        mPluginRows.remove(plugin);
        recreateAllRowsArray();
        onHeightUpdated();
    }

    @Override
    public View getFocusedChild() {
        if (FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
            for (FloatingHeaderRow row : mAllRows) {
                if (row.hasVisibleContent() && row.isVisible()) {
                    return row.getFocusedChild();
                }
            }
            return null;
        }
        return super.getFocusedChild();
    }

    void setup(AllAppsRecyclerView mainRV, AllAppsRecyclerView workRV, SearchRecyclerView searchRV,
            int activeRV, boolean tabsHidden) {
        for (FloatingHeaderRow row : mAllRows) {
            row.setup(this, mAllRows, tabsHidden);
        }
        updateExpectedHeight();

        mTabsHidden = tabsHidden;
        maybeSetTabVisibility(VISIBLE);
        mMainRV = mainRV;
        mWorkRV = workRV;
        mSearchRV = searchRV;
        setActiveRV(activeRV);
        reset(false);
    }

    /** Whether this header has been set up previously. */
    boolean isSetUp() {
        return mMainRV != null;
    }

    /** Set the active AllApps RV which will adjust the alpha of the header when scrolled. */
    void setActiveRV(int rvType) {
        if (mCurrentRV != null) {
            mCurrentRV.removeOnScrollListener(mOnScrollListener);
        }
        mCurrentRV =
                rvType == AdapterHolder.MAIN ? mMainRV
                : rvType == AdapterHolder.WORK ? mWorkRV : mSearchRV;
        mCurrentRV.addOnScrollListener(mOnScrollListener);
        maybeSetTabVisibility(rvType == AdapterHolder.SEARCH ? GONE : VISIBLE);
    }

    /** Update tab visibility to the given state, only if tabs are active (work profile exists). */
    void maybeSetTabVisibility(int visibility) {
        mTabLayout.setVisibility(mTabsHidden ? GONE : visibility);
    }

    private void updateExpectedHeight() {
        updateFloatingRowsHeight();
        mMaxTranslation = 0;
        if (mFloatingRowsCollapsed) {
            return;
        }
        mMaxTranslation += mFloatingRowsHeight;
        if (!mTabsHidden) {
            mMaxTranslation += mTabsAdditionalPaddingBottom
                    + getResources().getDimensionPixelSize(R.dimen.all_apps_tabs_margin_top);
        }
    }

    int getMaxTranslation() {
        if (mMaxTranslation == 0 && (mTabsHidden || mFloatingRowsCollapsed)) {
            return getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_bottom_padding);
        } else if (mMaxTranslation > 0 && mTabsHidden) {
            return mMaxTranslation + getPaddingTop();
        } else {
            return mMaxTranslation;
        }
    }

    private boolean canSnapAt(int currentScrollY) {
        return Math.abs(currentScrollY) <= mMaxTranslation;
    }

    private void moved(final int currentScrollY) {
        if (mHeaderCollapsed) {
            if (currentScrollY <= mSnappedScrolledY) {
                if (canSnapAt(currentScrollY)) {
                    mSnappedScrolledY = currentScrollY;
                }
            } else {
                mHeaderCollapsed = false;
            }
            mTranslationY = currentScrollY;
        } else {
            mTranslationY = currentScrollY - mSnappedScrolledY - mMaxTranslation;

            // update state vars
            if (mTranslationY >= 0) { // expanded: must not move down further
                mTranslationY = 0;
                mSnappedScrolledY = currentScrollY - mMaxTranslation;
            } else if (mTranslationY <= -mMaxTranslation) { // hide or stay hidden
                mHeaderCollapsed = true;
                mSnappedScrolledY = -mMaxTranslation;
            }
        }
    }

    protected void applyVerticalMove() {
        int uncappedTranslationY = mTranslationY;
        mTranslationY = Math.max(mTranslationY, -mMaxTranslation);

        if (mFloatingRowsCollapsed || uncappedTranslationY < mTranslationY - getPaddingTop()) {
            // we hide it completely if already capped (for opening search anim)
            for (FloatingHeaderRow row : mAllRows) {
                row.setVerticalScroll(0, true /* isScrolledOut */);
            }
        } else {
            for (FloatingHeaderRow row : mAllRows) {
                row.setVerticalScroll(uncappedTranslationY, false /* isScrolledOut */);
            }
        }

        mTabLayout.setTranslationY(mTranslationY);

        int clipTop = getPaddingTop() - mTabsAdditionalPaddingTop;
        if (mTabsHidden) {
            // Add back spacing that is otherwise covered by the tabs.
            clipTop += mTabsAdditionalPaddingTop;
        }
        mRVClip.top = mTabsHidden || mFloatingRowsCollapsed ? clipTop : 0;
        mHeaderClip.top = clipTop;
        // clipping on a draw might cause additional redraw
        setClipBounds(mHeaderClip);
        if (mMainRV != null) {
            mMainRV.setClipBounds(mRVClip);
        }
        if (mWorkRV != null) {
            mWorkRV.setClipBounds(mRVClip);
        }
        if (mSearchRV != null) {
            mSearchRV.setClipBounds(mRVClip);
        }
    }

    /**
     * Hides all the floating rows
     */
    public void setFloatingRowsCollapsed(boolean collapsed) {
        if (mFloatingRowsCollapsed == collapsed) {
            return;
        }

        mFloatingRowsCollapsed = collapsed;
        onHeightUpdated();
    }

    public int getClipTop() {
        return mHeaderClip.top;
    }

    public void reset(boolean animate) {
        if (mAnimator.isStarted()) {
            mAnimator.cancel();
        }
        if (animate) {
            mAnimator.setIntValues(mTranslationY, 0);
            mAnimator.addUpdateListener(this);
            mAnimator.setDuration(150);
            mAnimator.start();
        } else {
            mTranslationY = 0;
            applyVerticalMove();
        }
        mHeaderCollapsed = false;
        mSnappedScrolledY = -mMaxTranslation;
        mCurrentRV.scrollToTop();
    }

    public boolean isExpanded() {
        return !mHeaderCollapsed;
    }

    /** Returns true if personal/work tabs are currently in use. */
    public boolean usingTabs() {
        return !mTabsHidden;
    }

    ViewGroup getTabLayout() {
        return mTabLayout;
    }

    /** Calculates the combined height of any floating rows (e.g. predicted apps, app divider). */
    private void updateFloatingRowsHeight() {
        mFloatingRowsHeight =
                Arrays.stream(mAllRows).mapToInt(FloatingHeaderRow::getExpectedHeight).sum();
    }

    /** Gets the combined height of any floating rows (e.g. predicted apps, app divider). */
    int getFloatingRowsHeight() {
        return mFloatingRowsHeight;
    }

    int getTabsAdditionalPaddingBottom() {
        return mTabsAdditionalPaddingBottom;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mTranslationY = (Integer) animation.getAnimatedValue();
        applyVerticalMove();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        calcOffset(mTempOffset);
        ev.offsetLocation(mTempOffset.x, mTempOffset.y);
        mForwardToRecyclerView = mCurrentRV.onInterceptTouchEvent(ev);
        ev.offsetLocation(-mTempOffset.x, -mTempOffset.y);
        return mForwardToRecyclerView || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mForwardToRecyclerView) {
            // take this view's and parent view's (view pager) location into account
            calcOffset(mTempOffset);
            event.offsetLocation(mTempOffset.x, mTempOffset.y);
            try {
                return mCurrentRV.onTouchEvent(event);
            } finally {
                event.offsetLocation(-mTempOffset.x, -mTempOffset.y);
            }
        } else {
            return super.onTouchEvent(event);
        }
    }

    private void calcOffset(Point p) {
        p.x = getLeft() - mCurrentRV.getLeft() - ((ViewGroup) mCurrentRV.getParent()).getLeft();
        p.y = getTop() - mCurrentRV.getTop() - ((ViewGroup) mCurrentRV.getParent()).getTop();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void setInsets(Rect insets) {
        int leftRightPadding = ActivityContext.lookupContext(getContext())
                .getDeviceProfile().allAppsLeftRightPadding;
        setPadding(leftRightPadding, getPaddingTop(), leftRightPadding, getPaddingBottom());
    }

    public <T extends FloatingHeaderRow> T findFixedRowByType(Class<T> type) {
        for (FloatingHeaderRow row : mAllRows) {
            if (row.getTypeClass() == type) {
                return (T) row;
            }
        }
        return null;
    }

    /**
     * Returns visible height of FloatingHeaderView contents requiring header protection
     */
    int getPeripheralProtectionHeight() {
        // we only want to show protection when work tab is available and header is either
        // collapsed or animating to/from collapsed state
        if (mTabsHidden || mFloatingRowsCollapsed || !mHeaderCollapsed) {
            return 0;
        }
        return Math.max(0,
                getTabLayout().getBottom() - getPaddingTop() + getPaddingBottom() + mTranslationY);
    }
}
