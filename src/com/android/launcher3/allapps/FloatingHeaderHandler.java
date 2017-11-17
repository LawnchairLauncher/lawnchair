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
import android.content.res.Resources;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.android.launcher3.R;

public class FloatingHeaderHandler extends RecyclerView.OnScrollListener
        implements ValueAnimator.AnimatorUpdateListener {

    private final View mHeaderView;
    private final PredictionRowView mPredictionRow;
    private final ViewGroup mTabLayout;
    private final View mDivider;
    private final Rect mClip = new Rect(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    private final ValueAnimator mAnimator = ValueAnimator.ofInt(0, 0);

    private RecyclerView mMainRV;
    private RecyclerView mWorkRV;
    private boolean mTopOnlyMode;
    private boolean mHeaderHidden;
    private int mMaxTranslation;
    private int mSnappedScrolledY;
    private int mTranslationY;
    private int mMainScrolledY;
    private int mWorkScrolledY;
    private boolean mMainRVActive;

    public FloatingHeaderHandler(@NonNull ViewGroup header) {
        mHeaderView = header;
        mTabLayout = header.findViewById(R.id.tabs);
        mDivider = header.findViewById(R.id.divider);
        mPredictionRow = header.findViewById(R.id.header_content);
    }

    public void setup(@NonNull RecyclerView personalRV, @Nullable RecyclerView workRV,
        int predictionRowHeight) {
        mTopOnlyMode = workRV == null;
        mTabLayout.setVisibility(mTopOnlyMode ? View.GONE : View.VISIBLE);
        mPredictionRow.getLayoutParams().height = predictionRowHeight;
        mMaxTranslation = predictionRowHeight;
        mMainRV = personalRV;
        mMainRV.addOnScrollListener(this);
        mWorkRV = workRV;
        if (workRV != null) {
            workRV.addOnScrollListener(this);
        }
        setMainActive(true);
        setupDivider();
    }

    private void setupDivider() {
        Resources res = mHeaderView.getResources();
        int verticalGap = res.getDimensionPixelSize(R.dimen.all_apps_divider_margin_vertical);
        int sideGap = res.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        mDivider.setPadding(sideGap, verticalGap,sideGap, mTopOnlyMode ? verticalGap : 0);
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mDivider.getLayoutParams();
        lp.removeRule(RelativeLayout.ALIGN_BOTTOM);
        lp.addRule(RelativeLayout.ALIGN_BOTTOM, mTopOnlyMode ? R.id.header_content : R.id.tabs);
        mDivider.setLayoutParams(lp);
    }

    public void setMainActive(boolean active) {
        mMainRVActive = active;
        mSnappedScrolledY = getCurrentScroll() - mMaxTranslation;
        setExpanded(true);
    }

    public View getHeaderView() {
        return mHeaderView;
    }

    public PredictionRowView getContentView() {
        return mPredictionRow;
    }

    public ViewGroup getTabLayout() {
        return mTabLayout;
    }

    public View getDivider() {
        return mDivider;
    }

    @Override
    public void onScrolled(RecyclerView rv, int dx, int dy) {
        boolean isMainRV = rv == mMainRV;
        if (isMainRV != mMainRVActive) {
            return;
        }

        if (mAnimator.isStarted()) {
            mAnimator.cancel();
        }

        int current = isMainRV
                ? (mMainScrolledY -= dy)
                : (mWorkScrolledY -= dy);

        moved(current);
        apply();
    }

    public void reset() {
        mMainScrolledY = 0;
        mWorkScrolledY = 0;
        setExpanded(true);
    }

    private boolean canSnapAt(int currentScrollY) {
        return !mTopOnlyMode || Math.abs(currentScrollY) <= mPredictionRow.getHeight();
    }

    private void moved(final int currentScrollY) {
        if (mHeaderHidden) {
            if (currentScrollY <= mSnappedScrolledY) {
                if (canSnapAt(currentScrollY)) {
                    mSnappedScrolledY = currentScrollY;
                }
            } else {
                mHeaderHidden = false;
            }
            mTranslationY = currentScrollY;
        } else if (!mHeaderHidden) {
            mTranslationY = currentScrollY - mSnappedScrolledY - mMaxTranslation;

            // update state vars
            if (mTranslationY >= 0) { // expanded: must not move down further
                mTranslationY = 0;
                mSnappedScrolledY = currentScrollY - mMaxTranslation;
            } else if (mTranslationY <= -mMaxTranslation) { // hide or stay hidden
                mHeaderHidden = true;
                mSnappedScrolledY = currentScrollY;
            }
        }
    }

    private void apply() {
        int uncappedTranslationY = mTranslationY;
        mTranslationY = Math.max(mTranslationY, -mMaxTranslation);
        mPredictionRow.setTranslationY(uncappedTranslationY);
        mTabLayout.setTranslationY(mTranslationY);
        mDivider.setTranslationY(mTopOnlyMode ? uncappedTranslationY : mTranslationY);
        mClip.top = mMaxTranslation + mTranslationY;
        // clipping on a draw might cause additional redraw
        mMainRV.setClipBounds(mClip);
        if (mWorkRV != null) {
            mWorkRV.setClipBounds(mClip);
        }
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        if (!mTopOnlyMode && newState == RecyclerView.SCROLL_STATE_IDLE
                && mTranslationY != -mMaxTranslation && mTranslationY != 0) {
            float scroll = Math.abs(getCurrentScroll());
            boolean expand =  scroll > mMaxTranslation
                    ? Math.abs(mTranslationY) < mMaxTranslation / 2 : true;
            setExpanded(expand);
        }
    }

    private void setExpanded(boolean expand) {
        int translateTo = expand ? 0 : -mMaxTranslation;
        mAnimator.setIntValues(mTranslationY, translateTo);
        mAnimator.addUpdateListener(this);
        mAnimator.setDuration(150);
        mAnimator.start();
        mHeaderHidden = !expand;
        mSnappedScrolledY = expand ? getCurrentScroll() - mMaxTranslation : getCurrentScroll();
    }

    public boolean isExpanded() {
        return !mHeaderHidden;
    }

    private int getCurrentScroll() {
        return mMainRVActive ? mMainScrolledY : mWorkScrolledY;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mTranslationY = (Integer) animation.getAnimatedValue();
        apply();
    }

}
