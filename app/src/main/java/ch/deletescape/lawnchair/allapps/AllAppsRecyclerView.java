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

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Property;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

import ch.deletescape.lawnchair.BaseRecyclerView;
import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.anim.SpringAnimationHandler;

/**
 * A RecyclerView with custom fast scroll support for the all apps view.
 */
public class AllAppsRecyclerView extends BaseRecyclerView {
    public static final Property<AllAppsRecyclerView, Float> CONTENT_TRANS_Y =
            new Property<AllAppsRecyclerView, Float>(Float.class, "appsRecyclerViewContentTransY") {

                @Override
                public Float get(AllAppsRecyclerView object) {
                    return object.getContentTranslationY();
                }

                @Override
                public void set(AllAppsRecyclerView object, Float value) {
                    object.setContentTranslationY(value);
                }
            };

    private AlphabeticalAppsList mApps;
    private AllAppsFastScrollHelper mFastScrollHelper;
    private int mNumAppsPerRow;

    // The specific view heights that we use to calculate scroll
    private SparseIntArray mViewHeights = new SparseIntArray();
    private SparseIntArray mCachedScrollPositions = new SparseIntArray();

    private HeaderElevationController mElevationController;
    private SpringAnimationHandler<AllAppsGridAdapter.ViewHolder> mSpringAnimationHandler;

    private OverScrollHelper mOverScrollHelper;
    private VerticalPullDetector mPullDetector;

    public AllAppsRecyclerView(Context context) {
        this(context, null);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        addOnItemTouchListener(this);
        mScrollbar.setDetachThumbOnFastScroll();
        addOnItemTouchListener(this);
        mOverScrollHelper = new OverScrollHelper();
        mPullDetector = new VerticalPullDetector(getContext());
        mPullDetector.setListener(mOverScrollHelper);
        mPullDetector.setDetectableScrollConditions(3, true);
    }

    public void setSpringAnimationHandler(SpringAnimationHandler<AllAppsGridAdapter.ViewHolder> springAnimationHandler) {
        if (springAnimationHandler == null) return;
        setOverScrollMode(OVER_SCROLL_NEVER);
        mSpringAnimationHandler = springAnimationHandler;
        addOnScrollListener(new SpringMotionOnScrollListener());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mSpringAnimationHandler != null) {
            mPullDetector.onTouchEvent(ev);
            mSpringAnimationHandler.addMovement(ev);
        }
        return super.onTouchEvent(ev);
    }

    /**
     * Sets the list of apps in this view, used to determine the fastscroll position.
     */
    public void setApps(AlphabeticalAppsList apps) {
        mApps = apps;
        mFastScrollHelper = new AllAppsFastScrollHelper(this, apps);
    }

    public void setElevationController(HeaderElevationController elevationController) {
        mElevationController = elevationController;
    }

    /**
     * Sets the number of apps per row in this recycler view.
     */
    public void setNumAppsPerRow(DeviceProfile grid, int numAppsPerRow) {
        mNumAppsPerRow = numAppsPerRow;

        RecyclerView.RecycledViewPool pool = getRecycledViewPool();
        int approxRows = (int) Math.ceil(grid.availableHeightPx / grid.allAppsIconSizePx);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_SEARCH_DIVIDER, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET_DIVIDER, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_ICON, approxRows * mNumAppsPerRow);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_SECTION_BREAK, approxRows);
    }

    /**
     * Ensures that we can present a stable scrollbar for views of varying types by pre-measuring
     * all the different view types.
     */
    public void preMeasureViews(AllAppsGridAdapter adapter) {
        final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                getResources().getDisplayMetrics().widthPixels, View.MeasureSpec.AT_MOST);
        final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                getResources().getDisplayMetrics().heightPixels, View.MeasureSpec.AT_MOST);

        // Icons
        View icon = adapter.onCreateViewHolder(this,
                AllAppsGridAdapter.VIEW_TYPE_ICON).mContent;
        int iconHeight = icon.getLayoutParams().height;
        mViewHeights.put(AllAppsGridAdapter.VIEW_TYPE_ICON, iconHeight);

        // Search divider
        View searchDivider = adapter.onCreateViewHolder(this,
                AllAppsGridAdapter.VIEW_TYPE_SEARCH_DIVIDER).mContent;
        searchDivider.measure(widthMeasureSpec, heightMeasureSpec);
        int searchDividerHeight = searchDivider.getMeasuredHeight();
        mViewHeights.put(AllAppsGridAdapter.VIEW_TYPE_SEARCH_DIVIDER, searchDividerHeight);

        // Generic dividers
        View divider = adapter.onCreateViewHolder(this,
                AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET_DIVIDER).mContent;
        divider.measure(widthMeasureSpec, heightMeasureSpec);
        int dividerHeight = divider.getMeasuredHeight();
        mViewHeights.put(AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET_DIVIDER, dividerHeight);

        // Search views
        View emptySearch = adapter.onCreateViewHolder(this,
                AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH).mContent;
        emptySearch.measure(widthMeasureSpec, heightMeasureSpec);
        mViewHeights.put(AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH,
                emptySearch.getMeasuredHeight());
        View searchMarket = adapter.onCreateViewHolder(this,
                AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET).mContent;
        searchMarket.measure(widthMeasureSpec, heightMeasureSpec);
        mViewHeights.put(AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET,
                searchMarket.getMeasuredHeight());

        // Section breaks
        mViewHeights.put(AllAppsGridAdapter.VIEW_TYPE_SECTION_BREAK, 0);
    }

    /**
     * Scrolls this recycler view to the top.
     */
    public void scrollToTop() {
        // Ensure we reattach the scrollbar if it was previously detached while fast-scrolling
        if (mScrollbar.isThumbDetached()) {
            mScrollbar.reattachThumbToScroll();
        }
        scrollToPosition(0);
        if (mElevationController != null) {
            mElevationController.reset();
        }
    }

    /**
     * We need to override the draw to ensure that we don't draw the overscroll effect beyond the
     * background bounds.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Clip to ensure that we don't draw the overscroll effect beyond the background bounds
        canvas.clipRect(mBackgroundPadding.left, mBackgroundPadding.top,
                getWidth() - mBackgroundPadding.right,
                getHeight() - mBackgroundPadding.bottom);
        super.dispatchDraw(canvas);
    }

    public void onSearchResultsChanged() {
        // Always scroll the view to the top so the user can see the changed results
        scrollToTop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mSpringAnimationHandler == null) return super.onInterceptTouchEvent(ev);
        mPullDetector.onTouchEvent(ev);
        return super.onInterceptTouchEvent(ev) || mOverScrollHelper.isInOverScroll();
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
            @Override
            public void onChanged() {
                mCachedScrollPositions.clear();
            }
        });
        mFastScrollHelper.onSetAdapter((AllAppsGridAdapter) adapter);
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void onUpdateScrollbar(int dy) {
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();

        // Skip early if there are no items or we haven't been measured
        if (items.isEmpty() || mNumAppsPerRow == 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        // Skip early if, there no child laid out in the container.
        int scrollY = getCurrentScrollY();
        if (scrollY < 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        // Only show the scrollbar if there is height to be scrolled
        int availableScrollHeight = getAvailableScrollHeight();
        if (availableScrollHeight <= 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        if (mScrollbar.isThumbDetached()) {
            if (!mScrollbar.isDraggingThumb()) {
                // Calculate the current scroll position, the scrollY of the recycler view accounts
                // for the view padding, while the scrollBarY is drawn right up to the background
                // padding (ignoring padding)
                int availableScrollBarHeight = getAvailableScrollBarHeight();
                int scrollBarX = getScrollBarX();
                int scrollBarY = mBackgroundPadding.top +
                        (int) (((float) scrollY / availableScrollHeight) * availableScrollBarHeight);

                int thumbScrollY = mScrollbar.getThumbOffset().y;
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
                    mScrollbar.setThumbOffset(scrollBarX, thumbScrollY);
                    if (scrollBarY == thumbScrollY) {
                        mScrollbar.reattachThumbToScroll();
                    }
                } else {
                    // User is scrolling in an opposite direction to the direction that the thumb
                    // needs to catch up to the scroll position.  Do nothing except for updating
                    // the scroll bar x to match the thumb width.
                    mScrollbar.setThumbOffset(scrollBarX, thumbScrollY);
                }
            }
        } else {
            synchronizeScrollBarThumbOffsetToViewScroll(scrollY, availableScrollHeight);
        }
    }

    @Override
    protected boolean supportsFastScrolling() {
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
        int position = getChildAdapterPosition(child);
        if (position == NO_POSITION) {
            return -1;
        }
        return getCurrentScrollY(position, getLayoutManager().getDecoratedTop(child));
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

        return getPaddingTop() + y - offset;
    }

    @Override
    protected int getVisibleHeight() {
        return super.getVisibleHeight()
                - Launcher.getLauncher(getContext()).getDragLayer().getInsets().bottom;
    }

    /**
     * Returns the available scroll height:
     * AvailableScrollHeight = Total height of the all items - last page height
     */
    @Override
    protected int getAvailableScrollHeight() {
        int paddedHeight = getCurrentScrollY(mApps.getAdapterItems().size(), 0);
        int totalHeight = paddedHeight + getPaddingBottom();
        return totalHeight - getVisibleHeight();
    }

    class OverScrollHelper implements VerticalPullDetector.Listener {
        private boolean mAlreadyScrollingUp;
        private float mFirstDisplacement;
        private int mFirstScrollYOnScrollUp;
        private boolean mIsInOverScroll;

        private OverScrollHelper() {
            this.mFirstDisplacement = 0.0f;
        }

        public void onDragStart(boolean z) {

        }

        public boolean onDrag(float f, float f2) {
            boolean z = true;
            boolean z2 = f > 0.0f;
            if (!z2) {
                this.mAlreadyScrollingUp = false;
            } else if (!this.mAlreadyScrollingUp) {
                this.mFirstScrollYOnScrollUp = AllAppsRecyclerView.this.getCurrentScrollY();
                this.mAlreadyScrollingUp = true;
            }
            boolean z3 = this.mIsInOverScroll;
            if (AllAppsRecyclerView.this.mScrollbar.isDraggingThumb()) {
                z = false;
            } else if ((AllAppsRecyclerView.this.canScrollVertically(1) || f >= 0.0f) && (AllAppsRecyclerView.this.canScrollVertically(-1) || !z2 || this.mFirstScrollYOnScrollUp == 0)) {
                z = false;
            }
            this.mIsInOverScroll = z;
            if (z3 && !this.mIsInOverScroll) {
                reset(false);
            } else if (this.mIsInOverScroll) {
                if (Float.compare(this.mFirstDisplacement, 0.0f) == 0) {
                    this.mFirstDisplacement = f;
                }
                AllAppsRecyclerView.this.setContentTranslationY(getDampedOverScroll(f - this.mFirstDisplacement));
            }
            return this.mIsInOverScroll;
        }

        public void onDragEnd(float f, boolean z) {
            reset(this.mIsInOverScroll);
        }

        private void reset(boolean z) {
            float contentTranslationY = AllAppsRecyclerView.this.getContentTranslationY();
            if (Float.compare(contentTranslationY, 0.0f) != 0) {
                if (mSpringAnimationHandler != null)
                    mSpringAnimationHandler.animateToPositionWithVelocity(0.0f, -1, -((contentTranslationY / getDampedOverScroll((float) AllAppsRecyclerView.this.getHeight())) * 5000.0f));
                ObjectAnimator.ofFloat(AllAppsRecyclerView.this, AllAppsRecyclerView.CONTENT_TRANS_Y, new float[]{0.0f}).setDuration(100).start();
            }
            this.mIsInOverScroll = false;
            this.mFirstDisplacement = 0.0f;
            this.mFirstScrollYOnScrollUp = 0;
            this.mAlreadyScrollingUp = false;
        }

        public boolean isInOverScroll() {
            return this.mIsInOverScroll;
        }

        private float getDampedOverScroll(float f) {
            return dampedOverScroll(f, (float) AllAppsRecyclerView.this.getHeight()) * 0.07f;
        }

        private float overScrollInfluenceCurve(float f) {
            float f2 = f - 1.0f;
            return (f2 * (f2 * f2)) + 1.0f;
        }

        private float dampedOverScroll(float f, float f2) {
            float f3 = f / f2;
            if (Float.compare(f3, 0.0f) == 0) {
                return 0.0f;
            }
            f3 = overScrollInfluenceCurve(Math.abs(f3)) * (f3 / Math.abs(f3));
            if (Math.abs(f3) >= 1.0f) {
                f3 /= Math.abs(f3);
            }
            return (float) Math.round(f3 * f2);
        }
    }

    class SpringMotionOnScrollListener extends OnScrollListener {
        private SpringMotionOnScrollListener() {
        }

        public void onScrolled(RecyclerView recyclerView, int i, int i2) {
            if (!mOverScrollHelper.isInOverScroll()) {
                if (i2 < 0 && !canScrollVertically(-1)) {
                    mSpringAnimationHandler.animateToFinalPosition(0.0f, 1);
                } else if (i2 > 0 && !canScrollVertically(1)) {
                    mSpringAnimationHandler.animateToFinalPosition(0.0f, -1);
                }
            }
        }
    }

}
