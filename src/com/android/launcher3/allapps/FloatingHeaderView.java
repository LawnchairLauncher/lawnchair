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
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.android.launcher3.AppInfo;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;

import java.util.HashMap;

public class FloatingHeaderView extends RelativeLayout implements
        ValueAnimator.AnimatorUpdateListener {


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
            apply();
        }
    };

    private PredictionRowView mPredictionRow;
    private ViewGroup mTabLayout;
    private View mDivider;
    private AllAppsRecyclerView mMainRV;
    private AllAppsRecyclerView mWorkRV;
    private AllAppsRecyclerView mCurrentRV;
    private ViewGroup mParent;
    private boolean mTabsHidden;
    private boolean mHeaderCollapsed;
    private int mMaxTranslation;
    private int mSnappedScrolledY;
    private int mTranslationY;
    private boolean mForwardToRecyclerView;

    public FloatingHeaderView(@NonNull Context context) {
        this(context, null);
    }

    public FloatingHeaderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTabLayout = findViewById(R.id.tabs);
        mDivider = findViewById(R.id.divider);
        mPredictionRow = findViewById(R.id.header_content);
    }

    public void setup(AllAppsContainerView.AdapterHolder[] mAH,
            HashMap<ComponentKey, AppInfo> componentToAppMap, int numPredictedAppsPerRow) {
        mTabsHidden = mAH[AllAppsContainerView.AdapterHolder.WORK].recyclerView == null;
        mTabLayout.setVisibility(mTabsHidden ? View.GONE : View.VISIBLE);
        mPredictionRow.setPadding(0, 0, 0, mTabsHidden ? getResources()
                .getDimensionPixelSize(R.dimen.all_apps_prediction_row_divider_height) : 0);
        mPredictionRow.setup(mAH[AllAppsContainerView.AdapterHolder.MAIN].adapter,
                componentToAppMap, numPredictedAppsPerRow);
        mMaxTranslation = mPredictionRow.getExpectedHeight();
        mMainRV = setupRV(mMainRV, mAH[AllAppsContainerView.AdapterHolder.MAIN].recyclerView);
        mWorkRV = setupRV(mWorkRV, mAH[AllAppsContainerView.AdapterHolder.WORK].recyclerView);
        mParent = (ViewGroup) mMainRV.getParent();
        setMainActive(true);
        setupDivider();
    }

    private AllAppsRecyclerView setupRV(AllAppsRecyclerView old, AllAppsRecyclerView updated) {
        if (old != updated && updated != null ) {
            updated.addOnScrollListener(mOnScrollListener);
        }
        return updated;
    }

    private void setupDivider() {
        Resources res = getResources();
        int verticalGap = res.getDimensionPixelSize(R.dimen.all_apps_divider_margin_vertical);
        int sideGap = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        mDivider.setPadding(sideGap, verticalGap,sideGap, mTabsHidden ? verticalGap : 0);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mDivider.getLayoutParams();
        lp.removeRule(RelativeLayout.ALIGN_BOTTOM);
        lp.addRule(RelativeLayout.ALIGN_BOTTOM, mTabsHidden ? R.id.header_content : R.id.tabs);
        mDivider.setLayoutParams(lp);
    }

    public void setMainActive(boolean active) {
        mCurrentRV = active ? mMainRV : mWorkRV;
        mSnappedScrolledY = mCurrentRV.getCurrentScrollY() - mMaxTranslation;
        setExpanded(true);
    }

    public PredictionRowView getPredictionRow() {
        return mPredictionRow;
    }

    public View getDivider() {
        return mDivider;
    }

    public void reset() {
        setExpanded(true);
    }

    private boolean canSnapAt(int currentScrollY) {
        return Math.abs(currentScrollY) <= mPredictionRow.getHeight();
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
                mSnappedScrolledY = currentScrollY;
            }
        }
    }

    private void apply() {
        int uncappedTranslationY = mTranslationY;
        mTranslationY = Math.max(mTranslationY, -mMaxTranslation);
        if (mTranslationY != uncappedTranslationY) {
            // we hide it completely if already capped (for opening search anim)
            mPredictionRow.setVisibility(View.INVISIBLE);
        } else {
            mPredictionRow.setVisibility(View.VISIBLE);
            mPredictionRow.setTranslationY(uncappedTranslationY);
        }
        mTabLayout.setTranslationY(mTranslationY);
        mDivider.setTranslationY(mTabsHidden ? uncappedTranslationY : mTranslationY);
        mClip.top = mMaxTranslation + mTranslationY;
        // clipping on a draw might cause additional redraw
        mMainRV.setClipBounds(mClip);
        if (mWorkRV != null) {
            mWorkRV.setClipBounds(mClip);
        }
    }

    private void setExpanded(boolean expand) {
        int translateTo = expand ? 0 : -mMaxTranslation;
        mAnimator.setIntValues(mTranslationY, translateTo);
        mAnimator.addUpdateListener(this);
        mAnimator.setDuration(150);
        mAnimator.start();
        mHeaderCollapsed = !expand;
        mSnappedScrolledY = expand
                ? mCurrentRV.getCurrentScrollY() - mMaxTranslation
                : mCurrentRV.getCurrentScrollY();
    }

    public boolean isExpanded() {
        return !mHeaderCollapsed;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mTranslationY = (Integer) animation.getAnimatedValue();
        apply();
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
        p.x = getLeft() - mCurrentRV.getLeft() - mParent.getLeft();
        p.y = getTop() - mCurrentRV.getTop() - mParent.getTop();
    }

}


