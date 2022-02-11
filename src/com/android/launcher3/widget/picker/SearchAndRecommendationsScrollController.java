/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.widget.picker.WidgetsSpaceViewHolderBinder.EmptySpaceView;
import com.android.launcher3.widget.picker.search.WidgetsSearchBar;

/**
 * A controller which measures & updates {@link WidgetsFullSheet}'s views padding, margin and
 * vertical displacement upon scrolling.
 */
final class SearchAndRecommendationsScrollController implements
        RecyclerView.OnChildAttachStateChangeListener {

    private static final FloatProperty<SearchAndRecommendationsScrollController> SCROLL_OFFSET =
            new FloatProperty<SearchAndRecommendationsScrollController>("scrollAnimOffset") {
        @Override
        public void setValue(SearchAndRecommendationsScrollController controller, float offset) {
            controller.mScrollOffset = offset;
            controller.updateHeaderScroll();
        }

        @Override
        public Float get(SearchAndRecommendationsScrollController controller) {
            return controller.mScrollOffset;
        }
    };

    private static final MotionEventProxyMethod INTERCEPT_PROXY = ViewGroup::onInterceptTouchEvent;
    private static final MotionEventProxyMethod TOUCH_PROXY = ViewGroup::onTouchEvent;

    final SearchAndRecommendationsView mContainer;
    final View mSearchBarContainer;
    final WidgetsSearchBar mSearchBar;
    final TextView mHeaderTitle;
    final WidgetsRecommendationTableLayout mRecommendedWidgetsTable;
    @Nullable final View mTabBar;

    private WidgetsRecyclerView mCurrentRecyclerView;
    private EmptySpaceView mCurrentEmptySpaceView;

    private float mLastScroll = 0;
    private float mScrollOffset = 0;
    private Animator mOffsetAnimator;

    private boolean mShouldForwardToRecyclerView = false;

    private int mHeaderHeight;

    SearchAndRecommendationsScrollController(
            SearchAndRecommendationsView searchAndRecommendationContainer) {
        mContainer = searchAndRecommendationContainer;
        mSearchBarContainer = mContainer.findViewById(R.id.search_bar_container);
        mSearchBar = mContainer.findViewById(R.id.widgets_search_bar);
        mHeaderTitle = mContainer.findViewById(R.id.title);
        mRecommendedWidgetsTable = mContainer.findViewById(R.id.recommended_widget_table);
        mTabBar = mContainer.findViewById(R.id.tabs);

        mContainer.setSearchAndRecommendationScrollController(this);
    }

    public void setCurrentRecyclerView(WidgetsRecyclerView currentRecyclerView) {
        boolean animateReset = mCurrentRecyclerView != null;
        if (mCurrentRecyclerView != null) {
            mCurrentRecyclerView.removeOnChildAttachStateChangeListener(this);
        }
        mCurrentRecyclerView = currentRecyclerView;
        mCurrentRecyclerView.addOnChildAttachStateChangeListener(this);
        findCurrentEmptyView();
        reset(animateReset);
    }

    public int getHeaderHeight() {
        return mHeaderHeight;
    }

    private void updateHeaderScroll() {
        mLastScroll = getCurrentScroll();
        mHeaderTitle.setTranslationY(mLastScroll);
        mRecommendedWidgetsTable.setTranslationY(mLastScroll);

        float searchYDisplacement = Math.max(mLastScroll, -mSearchBarContainer.getTop());
        mSearchBarContainer.setTranslationY(searchYDisplacement);

        if (mTabBar != null) {
            float tabsDisplacement = Math.max(mLastScroll, -mTabBar.getTop()
                    + mSearchBarContainer.getHeight());
            mTabBar.setTranslationY(tabsDisplacement);
        }
    }

    private float getCurrentScroll() {
        return mScrollOffset + (mCurrentEmptySpaceView == null ? 0 : mCurrentEmptySpaceView.getY());
    }

    /**
     * Updates the scrollable header height
     *
     * @return {@code true} if the header height or dependent property changed.
     */
    public boolean updateHeaderHeight() {
        boolean hasSizeUpdated = false;

        int headerHeight = mContainer.getMeasuredHeight();
        if (headerHeight != mHeaderHeight) {
            mHeaderHeight = headerHeight;
            hasSizeUpdated = true;
        }

        if (mCurrentEmptySpaceView != null
                && mCurrentEmptySpaceView.setFixedHeight(mHeaderHeight)) {
            hasSizeUpdated = true;
        }
        return hasSizeUpdated;
    }

    /** Resets any previous view translation. */
    public void reset(boolean animate) {
        if (mOffsetAnimator != null) {
            mOffsetAnimator.cancel();
            mOffsetAnimator = null;
        }

        mScrollOffset = 0;
        if (!animate) {
            updateHeaderScroll();
        } else {
            float startValue = mLastScroll - getCurrentScroll();
            mOffsetAnimator = ObjectAnimator.ofFloat(this, SCROLL_OFFSET, startValue, 0);
            mOffsetAnimator.addListener(forEndCallback(() -> mOffsetAnimator = null));
            mOffsetAnimator.start();
        }
    }

    /**
     * Returns {@code true} if a touch event should be intercepted by this controller.
     */
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return (mShouldForwardToRecyclerView = proxyMotionEvent(event, INTERCEPT_PROXY));
    }

    /**
     * Returns {@code true} if this controller has intercepted and consumed a touch event.
     */
    public boolean onTouchEvent(MotionEvent event) {
        return mShouldForwardToRecyclerView && proxyMotionEvent(event, TOUCH_PROXY);
    }

    private boolean proxyMotionEvent(MotionEvent event, MotionEventProxyMethod method) {
        float dx = mCurrentRecyclerView.getLeft() - mContainer.getLeft();
        float dy = mCurrentRecyclerView.getTop() - mContainer.getTop();
        event.offsetLocation(dx, dy);
        try {
            return method.proxyEvent(mCurrentRecyclerView, event);
        } finally {
            event.offsetLocation(-dx, -dy);
        }
    }

    @Override
    public void onChildViewAttachedToWindow(@NonNull View view) {
        if (view instanceof EmptySpaceView) {
            findCurrentEmptyView();
        }
    }

    @Override
    public void onChildViewDetachedFromWindow(@NonNull View view) {
        if (view == mCurrentEmptySpaceView) {
            findCurrentEmptyView();
        }
    }

    private void findCurrentEmptyView() {
        if (mCurrentEmptySpaceView != null) {
            mCurrentEmptySpaceView.setOnYChangeCallback(null);
            mCurrentEmptySpaceView = null;
        }
        int childCount = mCurrentRecyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View view = mCurrentRecyclerView.getChildAt(i);
            if (view instanceof EmptySpaceView) {
                mCurrentEmptySpaceView = (EmptySpaceView) view;
                mCurrentEmptySpaceView.setFixedHeight(getHeaderHeight());
                mCurrentEmptySpaceView.setOnYChangeCallback(this::updateHeaderScroll);
                return;
            }
        }
    }

    private interface MotionEventProxyMethod {

        boolean proxyEvent(ViewGroup view, MotionEvent event);
    }
}
