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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Property;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

import java.util.List;

/**
 * A RecyclerView with custom fast scroll support for the all apps view.
 */
public class AllAppsRecyclerView extends BaseRecyclerView implements LogContainerProvider {

    private AlphabeticalAppsList mApps;
    private AllAppsFastScrollHelper mFastScrollHelper;
    private int mNumAppsPerRow;

    // The specific view heights that we use to calculate scroll
    private SparseIntArray mViewHeights = new SparseIntArray();
    private SparseIntArray mCachedScrollPositions = new SparseIntArray();

    // The empty-search result background
    private AllAppsBackgroundDrawable mEmptySearchBackground;
    private int mEmptySearchBackgroundTopOffset;

    private SpringAnimationHandler mSpringAnimationHandler;
    private OverScrollHelper mOverScrollHelper;
    private SwipeDetector mPullDetector;

    private float mContentTranslationY = 0;
    public static final Property<AllAppsRecyclerView, Float> CONTENT_TRANS_Y =
            new Property<AllAppsRecyclerView, Float>(Float.class, "appsRecyclerViewContentTransY") {
                @Override
                public Float get(AllAppsRecyclerView allAppsRecyclerView) {
                    return allAppsRecyclerView.getContentTranslationY();
                }

                @Override
                public void set(AllAppsRecyclerView allAppsRecyclerView, Float y) {
                    allAppsRecyclerView.setContentTranslationY(y);
                }
            };

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
        Resources res = getResources();
        addOnItemTouchListener(this);
        mEmptySearchBackgroundTopOffset = res.getDimensionPixelSize(
                R.dimen.all_apps_empty_search_bg_top_offset);

        mOverScrollHelper = new OverScrollHelper();
        mPullDetector = new SwipeDetector(getContext(), mOverScrollHelper, SwipeDetector.VERTICAL);
        mPullDetector.setDetectableScrollConditions(SwipeDetector.DIRECTION_BOTH, true);
    }

    public void setSpringAnimationHandler(SpringAnimationHandler springAnimationHandler) {
        if (FeatureFlags.LAUNCHER3_PHYSICS) {
            mSpringAnimationHandler = springAnimationHandler;
            addOnScrollListener(new SpringMotionOnScrollListener());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        mPullDetector.onTouchEvent(e);
        if (FeatureFlags.LAUNCHER3_PHYSICS && mSpringAnimationHandler != null) {
            mSpringAnimationHandler.addMovement(e);
        }
        return super.onTouchEvent(e);
    }

    /**
     * Sets the list of apps in this view, used to determine the fastscroll position.
     */
    public void setApps(AlphabeticalAppsList apps) {
        mApps = apps;
        mFastScrollHelper = new AllAppsFastScrollHelper(this, apps);
    }

    public AlphabeticalAppsList getApps() {
        return mApps;
    }

    /**
     * Sets the number of apps per row in this recycler view.
     */
    public void setNumAppsPerRow(DeviceProfile grid, int numAppsPerRow) {
        mNumAppsPerRow = numAppsPerRow;

        RecyclerView.RecycledViewPool pool = getRecycledViewPool();
        int approxRows = (int) Math.ceil(grid.availableHeightPx / grid.allAppsIconSizePx);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET_DIVIDER, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_ICON, approxRows * mNumAppsPerRow);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_PREDICTION_ICON, mNumAppsPerRow);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_PREDICTION_DIVIDER, 1);
    }

    /**
     * Ensures that we can present a stable scrollbar for views of varying types by pre-measuring
     * all the different view types.
     */
    public void preMeasureViews(AllAppsGridAdapter adapter) {
        View icon = adapter.onCreateViewHolder(this, AllAppsGridAdapter.VIEW_TYPE_ICON).itemView;
        final int iconHeight = icon.getLayoutParams().height;
        mViewHeights.put(AllAppsGridAdapter.VIEW_TYPE_ICON, iconHeight);
        mViewHeights.put(AllAppsGridAdapter.VIEW_TYPE_PREDICTION_ICON, iconHeight);

        final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                getResources().getDisplayMetrics().widthPixels, View.MeasureSpec.AT_MOST);
        final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                getResources().getDisplayMetrics().heightPixels, View.MeasureSpec.AT_MOST);

        putSameHeightFor(adapter, widthMeasureSpec, heightMeasureSpec,
                AllAppsGridAdapter.VIEW_TYPE_PREDICTION_DIVIDER,
                AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET_DIVIDER);
        putSameHeightFor(adapter, widthMeasureSpec, heightMeasureSpec,
                AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET);
        putSameHeightFor(adapter, widthMeasureSpec, heightMeasureSpec,
                AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH);

        if (FeatureFlags.DISCOVERY_ENABLED) {
            putSameHeightFor(adapter, widthMeasureSpec, heightMeasureSpec,
                    AllAppsGridAdapter.VIEW_TYPE_APPS_LOADING_DIVIDER);
            putSameHeightFor(adapter, widthMeasureSpec, heightMeasureSpec,
                    AllAppsGridAdapter.VIEW_TYPE_DISCOVERY_ITEM);
        }
    }

    private void putSameHeightFor(AllAppsGridAdapter adapter, int w, int h, int... viewTypes) {
        View view = adapter.onCreateViewHolder(this, viewTypes[0]).itemView;
        view.measure(w, h);
        for (int viewType : viewTypes) {
            mViewHeights.put(viewType, view.getMeasuredHeight());
        }
    }

    /**
     * Scrolls this recycler view to the top.
     */
    public void scrollToTop() {
        // Ensure we reattach the scrollbar if it was previously detached while fast-scrolling
        if (mScrollbar != null) {
            mScrollbar.reattachThumbToScroll();
        }
        scrollToPosition(0);
    }

    @Override
    public void onDraw(Canvas c) {
        // Draw the background
        if (mEmptySearchBackground != null && mEmptySearchBackground.getAlpha() > 0) {
            mEmptySearchBackground.draw(c);
        }

        super.onDraw(c);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.translate(0, mContentTranslationY);
        super.dispatchDraw(canvas);
        canvas.translate(0, -mContentTranslationY);
    }

    public float getContentTranslationY() {
        return mContentTranslationY;
    }

    /**
     * Use this method instead of calling {@link #setTranslationY(float)}} directly to avoid drawing
     * on top of other Views.
     */
    public void setContentTranslationY(float y) {
        mContentTranslationY = y;
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mEmptySearchBackground || super.verifyDrawable(who);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateEmptySearchBackgroundBounds();
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        if (mApps.hasFilter()) {
            targetParent.containerType = ContainerType.SEARCHRESULT;
        } else {
            if (v instanceof BubbleTextView) {
                BubbleTextView icon = (BubbleTextView) v;
                int position = getChildPosition(icon);
                if (position != NO_POSITION) {
                    List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
                    AlphabeticalAppsList.AdapterItem item = items.get(position);
                    if (item.viewType == AllAppsGridAdapter.VIEW_TYPE_PREDICTION_ICON) {
                        targetParent.containerType = ContainerType.PREDICTION;
                        target.predictedRank = item.rowAppIndex;
                        return;
                    }
                }
            }
            targetParent.containerType = ContainerType.ALLAPPS;
        }
    }

    public void onSearchResultsChanged() {
        // Always scroll the view to the top so the user can see the changed results
        scrollToTop();

        if (mApps.shouldShowEmptySearch()) {
            if (mEmptySearchBackground == null) {
                mEmptySearchBackground = DrawableFactory.get(getContext())
                        .getAllAppsBackground(getContext());
                mEmptySearchBackground.setAlpha(0);
                mEmptySearchBackground.setCallback(this);
                updateEmptySearchBackgroundBounds();
            }
            mEmptySearchBackground.animateBgAlpha(1f, 150);
        } else if (mEmptySearchBackground != null) {
            // For the time being, we just immediately hide the background to ensure that it does
            // not overlap with the results
            mEmptySearchBackground.setBgAlpha(0f);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        mPullDetector.onTouchEvent(e);
        boolean result = super.onInterceptTouchEvent(e) || mOverScrollHelper.isInOverScroll();
        if (!result && e.getAction() == MotionEvent.ACTION_DOWN
                && mEmptySearchBackground != null && mEmptySearchBackground.getAlpha() > 0) {
            mEmptySearchBackground.setHotspot(e.getX(), e.getY());
        }
        return result;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public String scrollToPositionAtProgress(float touchFraction) {
        int rowCount = mApps.getNumAppRows();
        if (rowCount == 0) {
            return "";
        }

        // Stop the scroller if it is scrolling
        stopScroll();

        // Find the fastscroll section that maps to this touch fraction
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollSections =
                mApps.getFastScrollerSections();
        AlphabeticalAppsList.FastScrollSectionInfo lastInfo = fastScrollSections.get(0);
        for (int i = 1; i < fastScrollSections.size(); i++) {
            AlphabeticalAppsList.FastScrollSectionInfo info = fastScrollSections.get(i);
            if (info.touchFraction > touchFraction) {
                break;
            }
            lastInfo = info;
        }

        // Update the fast scroll
        int scrollY = getCurrentScrollY();
        int availableScrollHeight = getAvailableScrollHeight();
        mFastScrollHelper.smoothScrollToSection(scrollY, availableScrollHeight, lastInfo);
        return lastInfo.sectionName;
    }

    @Override
    public void onFastScrollCompleted() {
        super.onFastScrollCompleted();
        mFastScrollHelper.onFastScrollCompleted();
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            public void onChanged() {
                mCachedScrollPositions.clear();
            }
        });
        mFastScrollHelper.onSetAdapter((AllAppsGridAdapter) adapter);
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        // No bottom fading edge.
        return 0;
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
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();

        // Skip early if there are no items or we haven't been measured
        if (items.isEmpty() || mNumAppsPerRow == 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        // Skip early if, there no child laid out in the container.
        int scrollY = getCurrentScrollY();
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

    @Override
    public boolean supportsFastScrolling() {
        // Only allow fast scrolling when the user is not searching, since the results are not
        // grouped in a meaningful order
        return !mApps.hasFilter();
    }

    @Override
    public int getCurrentScrollY() {
        // Return early if there are no items or we haven't been measured
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
        if (items.isEmpty() || mNumAppsPerRow == 0 || getChildCount() == 0) {
            return -1;
        }

        // Calculate the y and offset for the item
        View child = getChildAt(0);
        int position = getChildPosition(child);
        if (position == NO_POSITION) {
            return -1;
        }
        return getPaddingTop() +
                getCurrentScrollY(position, getLayoutManager().getDecoratedTop(child));
    }

    public int getCurrentScrollY(int position, int offset) {
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
        AlphabeticalAppsList.AdapterItem posItem = position < items.size() ?
                items.get(position) : null;
        int y = mCachedScrollPositions.get(position, -1);
        if (y < 0) {
            y = 0;
            for (int i = 0; i < position; i++) {
                AlphabeticalAppsList.AdapterItem item = items.get(i);
                if (AllAppsGridAdapter.isIconViewType(item.viewType)) {
                    // Break once we reach the desired row
                    if (posItem != null && posItem.viewType == item.viewType &&
                            posItem.rowIndex == item.rowIndex) {
                        break;
                    }
                    // Otherwise, only account for the first icon in the row since they are the same
                    // size within a row
                    if (item.rowAppIndex == 0) {
                        y += mViewHeights.get(item.viewType, 0);
                    }
                } else {
                    // Rest of the views span the full width
                    y += mViewHeights.get(item.viewType, 0);
                }
            }
            mCachedScrollPositions.put(position, y);
        }
        return y - offset;
    }

    /**
     * Returns the available scroll height:
     *   AvailableScrollHeight = Total height of the all items - last page height
     */
    @Override
    protected int getAvailableScrollHeight() {
        return getPaddingTop() + getCurrentScrollY(mApps.getAdapterItems().size(), 0)
                - getHeight() + getPaddingBottom();
    }

    /**
     * Updates the bounds of the empty search background.
     */
    private void updateEmptySearchBackgroundBounds() {
        if (mEmptySearchBackground == null) {
            return;
        }

        // Center the empty search background on this new view bounds
        int x = (getMeasuredWidth() - mEmptySearchBackground.getIntrinsicWidth()) / 2;
        int y = mEmptySearchBackgroundTopOffset;
        mEmptySearchBackground.setBounds(x, y,
                x + mEmptySearchBackground.getIntrinsicWidth(),
                y + mEmptySearchBackground.getIntrinsicHeight());
    }

    private class SpringMotionOnScrollListener extends RecyclerView.OnScrollListener {

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (mOverScrollHelper.isInOverScroll()) {
                // OverScroll will handle animating the springs.
                return;
            }

            // We only start the spring animation when we hit the top/bottom, to ensure
            // that all of the animations start at the same time.
            if (dy < 0 && !canScrollVertically(-1)) {
                mSpringAnimationHandler.animateToFinalPosition(0, 1);
            } else if (dy > 0 && !canScrollVertically(1)) {
                mSpringAnimationHandler.animateToFinalPosition(0, -1);
            }
        }
    }

    private class OverScrollHelper implements SwipeDetector.Listener {

        private static final float MAX_RELEASE_VELOCITY = 5000; // px / s
        private static final float MAX_OVERSCROLL_PERCENTAGE = 0.07f;

        private boolean mIsInOverScroll;

        // We use this value to calculate the actual amount the user has overscrolled.
        private float mFirstDisplacement = 0;

        private boolean mAlreadyScrollingUp;
        private int mFirstScrollYOnScrollUp;

        @Override
        public void onDragStart(boolean start) {
        }

        @Override
        public boolean onDrag(float displacement, float velocity) {
            boolean isScrollingUp = displacement > 0;
            if (isScrollingUp) {
                if (!mAlreadyScrollingUp) {
                    mFirstScrollYOnScrollUp = getCurrentScrollY();
                    mAlreadyScrollingUp = true;
                }
            } else {
                mAlreadyScrollingUp = false;
            }

            // Only enter overscroll if the user is interacting with the RecyclerView directly
            // and if one of the following criteria are met:
            // - User scrolls down when they're already at the bottom.
            // - User starts scrolling up, hits the top, and continues scrolling up.
            boolean wasInOverScroll = mIsInOverScroll;
            mIsInOverScroll = !mScrollbar.isDraggingThumb() &&
                    ((!canScrollVertically(1) && displacement < 0) ||
                    (!canScrollVertically(-1) && isScrollingUp && mFirstScrollYOnScrollUp != 0));

            if (wasInOverScroll && !mIsInOverScroll) {
                // Exit overscroll. This can happen when the user is in overscroll and then
                // scrolls the opposite way.
                reset(false /* shouldSpring */);
            } else if (mIsInOverScroll) {
                if (Float.compare(mFirstDisplacement, 0) == 0) {
                    // Because users can scroll before entering overscroll, we need to
                    // subtract the amount where the user was not in overscroll.
                    mFirstDisplacement = displacement;
                }
                float overscrollY = displacement - mFirstDisplacement;
                setContentTranslationY(getDampedOverScroll(overscrollY));
            }

            return mIsInOverScroll;
        }

        @Override
        public void onDragEnd(float velocity, boolean fling) {
           reset(mIsInOverScroll  /* shouldSpring */);
        }

        private void reset(boolean shouldSpring) {
            float y = getContentTranslationY();
            if (Float.compare(y, 0) != 0) {
                if (FeatureFlags.LAUNCHER3_PHYSICS && shouldSpring) {
                    // We calculate our own velocity to give the springs the desired effect.
                    float velocity = y / getDampedOverScroll(getHeight()) * MAX_RELEASE_VELOCITY;
                    // We want to negate the velocity because we are moving to 0 from -1 due to the
                    // downward motion. (y-axis -1 is above 0).
                    mSpringAnimationHandler.animateToPositionWithVelocity(0, -1, -velocity);
                }

                ObjectAnimator.ofFloat(AllAppsRecyclerView.this,
                        AllAppsRecyclerView.CONTENT_TRANS_Y, 0)
                        .setDuration(100)
                        .start();
            }
            mIsInOverScroll = false;
            mFirstDisplacement = 0;
            mFirstScrollYOnScrollUp = 0;
            mAlreadyScrollingUp = false;
        }

        public boolean isInOverScroll() {
            return mIsInOverScroll;
        }

        private float getDampedOverScroll(float y) {
            return OverScroll.dampedScroll(y, getHeight());
        }
    }
}
