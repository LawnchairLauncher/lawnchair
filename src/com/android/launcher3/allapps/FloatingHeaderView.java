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
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.systemui.plugins.AllAppsRow;
import com.android.systemui.plugins.AllAppsRow.OnHeightUpdatedListener;
import com.android.systemui.plugins.PluginListener;

import java.util.ArrayList;
import java.util.Map;

public class FloatingHeaderView extends LinearLayout implements
        ValueAnimator.AnimatorUpdateListener, PluginListener<AllAppsRow>, Insettable,
        OnHeightUpdatedListener {

    private final Rect mClip = new Rect(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    private final ValueAnimator mAnimator = ValueAnimator.ofInt(0, 0);
    private final Point mTempOffset = new Point();
    private final RecyclerView.OnScrollListener mOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        }

        @Override
        public void onScrolled(RecyclerView rv, int dx, int dy) {
            if (rv != mCurrentRV) {
                return;
            }

            if (mAnimator.isStarted()) {
                mAnimator.cancel();
            }

            int current = -mCurrentRV.getCurrentScrollY();
            moved(current);
            applyVerticalMove();
        }
    };

    private final int mHeaderTopPadding;

    protected final Map<AllAppsRow, PluginHeaderRow> mPluginRows = new ArrayMap<>();

    protected ViewGroup mTabLayout;
    private AllAppsRecyclerView mMainRV;
    private AllAppsRecyclerView mWorkRV;
    private AllAppsRecyclerView mCurrentRV;
    private ViewGroup mParent;
    private boolean mHeaderCollapsed;
    private int mSnappedScrolledY;
    private int mTranslationY;

    private boolean mAllowTouchForwarding;
    private boolean mForwardToRecyclerView;

    protected boolean mTabsHidden;
    protected int mMaxTranslation;
    private boolean mMainRVActive = true;

    private boolean mCollapsed = false;

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
        mHeaderTopPadding = context.getResources()
                .getDimensionPixelSize(R.dimen.all_apps_header_top_padding);
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

        if (mMaxTranslation != oldMaxHeight) {
            AllAppsContainerView parent = (AllAppsContainerView) getParent();
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

    public void setup(AllAppsContainerView.AdapterHolder[] mAH, boolean tabsHidden) {
        for (FloatingHeaderRow row : mAllRows) {
            row.setup(this, mAllRows, tabsHidden);
        }
        updateExpectedHeight();

        mTabsHidden = tabsHidden;
        mTabLayout.setVisibility(tabsHidden ? View.GONE : View.VISIBLE);
        mMainRV = setupRV(mMainRV, mAH[AllAppsContainerView.AdapterHolder.MAIN].recyclerView);
        mWorkRV = setupRV(mWorkRV, mAH[AllAppsContainerView.AdapterHolder.WORK].recyclerView);
        mParent = (ViewGroup) mMainRV.getParent();
        setMainActive(mMainRVActive || mWorkRV == null);
        reset(false);
    }

    private AllAppsRecyclerView setupRV(AllAppsRecyclerView old, AllAppsRecyclerView updated) {
        if (old != updated && updated != null ) {
            updated.addOnScrollListener(mOnScrollListener);
        }
        return updated;
    }

    private void updateExpectedHeight() {
        mMaxTranslation = 0;
        if (mCollapsed) {
            return;
        }
        for (FloatingHeaderRow row : mAllRows) {
            mMaxTranslation += row.getExpectedHeight();
        }
    }

    public void setMainActive(boolean active) {
        mCurrentRV = active ? mMainRV : mWorkRV;
        mMainRVActive = active;
    }

    public int getMaxTranslation() {
        if (mMaxTranslation == 0 && mTabsHidden) {
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
        } else if (!mHeaderCollapsed) {
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

        if (mCollapsed || uncappedTranslationY < mTranslationY - mHeaderTopPadding) {
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
        mClip.top = mMaxTranslation + mTranslationY;
        // clipping on a draw might cause additional redraw
        mMainRV.setClipBounds(mClip);
        if (mWorkRV != null) {
            mWorkRV.setClipBounds(mClip);
        }
    }

    /**
     * Hides all the floating rows
     */
    public void setCollapsed(boolean collapse) {
        if (mCollapsed == collapse) return;

        mCollapsed = collapse;
        onHeightUpdated();
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

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mTranslationY = (Integer) animation.getAnimatedValue();
        applyVerticalMove();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mAllowTouchForwarding) {
            mForwardToRecyclerView = false;
            return super.onInterceptTouchEvent(ev);
        }
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
        p.x = getLeft() - mCurrentRV.getLeft() - mParent.getLeft();
        p.y = getTop() - mCurrentRV.getTop() - mParent.getTop();
    }

    public void setContentVisibility(boolean hasHeader, boolean hasAllAppsContent,
            PropertySetter setter, Interpolator headerFade, Interpolator allAppsFade) {
        for (FloatingHeaderRow row : mAllRows) {
            row.setContentVisibility(hasHeader, hasAllAppsContent, setter, headerFade, allAppsFade);
        }

        allowTouchForwarding(hasAllAppsContent);
        setter.setFloat(mTabLayout, ALPHA, hasAllAppsContent ? 1 : 0, headerFade);
    }

    protected void allowTouchForwarding(boolean allow) {
        mAllowTouchForwarding = allow;
    }

    public boolean hasVisibleContent() {
        for (FloatingHeaderRow row : mAllRows) {
            if (row.hasVisibleContent()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = Launcher.getLauncher(getContext()).getDeviceProfile();
        for (FloatingHeaderRow row : mAllRows) {
            row.setInsets(insets, grid);
        }
    }

    public <T extends FloatingHeaderRow> T findFixedRowByType(Class<T> type) {
        for (FloatingHeaderRow row : mAllRows) {
            if (row.getTypeClass() == type) {
                return (T) row;
            }
        }
        return null;
    }
}


