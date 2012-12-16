/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.cyanogenmod.trebuchet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.Scroller;

import com.cyanogenmod.trebuchet.R;

import java.util.ArrayList;

/**
 * An abstraction of the original Workspace which supports browsing through a
 * sequential list of "pages"
 */
public abstract class PagedView extends ViewGroup implements ViewGroup.OnHierarchyChangeListener {
    private static final String TAG = "PagedView";
    private static final boolean DEBUG = false;
    protected static final int INVALID_PAGE = -1;

    // the min drag distance for a fling to register, to prevent random page shifts
    private static final int MIN_LENGTH_FOR_FLING = 25;

    protected static final int PAGE_SNAP_ANIMATION_DURATION = 400;
    protected static final int MAX_PAGE_SNAP_DURATION = 550;
    protected static final int SLOW_PAGE_SNAP_ANIMATION_DURATION = 750;
    protected static final float NANOTIME_DIV = 1000000000.0f;

    private static final float OVERSCROLL_ACCELERATE_FACTOR = 2;
    private static final float OVERSCROLL_DAMP_FACTOR = 0.14f;

    private static final float RETURN_TO_ORIGINAL_PAGE_THRESHOLD = 0.33f;
    // The page is moved more than halfway, automatically move to the next page on touch up.
    private static final float SIGNIFICANT_MOVE_THRESHOLD = 0.4f;

    // The following constants need to be scaled based on density. The scaled versions will be
    // assigned to the corresponding member variables below.
    private static final int FLING_THRESHOLD_VELOCITY = 500;
    private static final int MIN_SNAP_VELOCITY = 1500;
    private static final int MIN_FLING_VELOCITY = 250;

    static final int AUTOMATIC_PAGE_SPACING = -1;

    protected int mFlingThresholdVelocity;
    protected int mMinFlingVelocity;
    protected int mMinSnapVelocity;

    // Vertical paged view
    protected boolean mVertical;

    protected float mDensity;
    protected float mSmoothingTime;
    protected float mTouchX;
    protected float mTouchY;

    protected boolean mFirstLayout = true;

    protected int mCurrentPage;
    protected int mNextPage = INVALID_PAGE;
    protected int mMaxScrollX;
    protected int mMaxScrollY;
    protected Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    private float mDownMotionX;
    private float mDownMotionY;
    protected float mLastMotionX;
    protected float mLastMotionXRemainder;
    protected float mLastMotionY;
    protected float mLastMotionYRemainder;
    protected float mTotalMotionX;
    protected float mTotalMotionY;
    private int mLastScreenScroll = -1;
    private int[] mChildOffsets;
    private int[] mChildRelativeOffsets;
    private int[] mChildOffsetsWithLayoutScale;

    protected final static int TOUCH_STATE_REST = 0;
    protected final static int TOUCH_STATE_SCROLLING = 1;
    protected final static int TOUCH_STATE_PREV_PAGE = 2;
    protected final static int TOUCH_STATE_NEXT_PAGE = 3;
    protected final static float ALPHA_QUANTIZE_LEVEL = 0.0001f;

    protected int mTouchState = TOUCH_STATE_REST;
    protected boolean mForceScreenScrolled = false;

    protected OnLongClickListener mLongClickListener;

    protected boolean mAllowLongPress = true;

    protected int mTouchSlop;
    private int mPagingTouchSlop;
    private int mMaximumVelocity;
    private int mMinimumWidth;
    private int mMinimumHeight;
    protected int mPageSpacing;
    protected int mPageLayoutPaddingTop;
    protected int mPageLayoutPaddingBottom;
    protected int mPageLayoutPaddingLeft;
    protected int mPageLayoutPaddingRight;
    protected int mPageLayoutWidthGap;
    protected int mPageLayoutHeightGap;
    protected int mCellCountX = 0;
    protected int mCellCountY = 0;
    protected boolean mCenterPages;
    protected boolean mAllowOverScroll = true;
    protected int mUnboundedScrollX;
    protected int mUnboundedScrollY;
    protected int[] mTempVisiblePagesRange = new int[2];
    protected boolean mForceDrawAllChildrenNextFrame;

    // mOverScrollX is equal to getScrollX() when we're within the normal scroll range. Otherwise
    // it is equal to the scaled overscroll position. We use a separate value so as to prevent
    // the screens from continuing to translate beyond the normal bounds.
    protected int mOverScrollX;
    protected int mOverScrollY;

    // parameter that adjusts the layout to be optimized for pages with that scale factor
    protected float mLayoutScale = 1.0f;

    protected static final int INVALID_POINTER = -1;

    protected int mActivePointerId = INVALID_POINTER;

    private PageSwitchListener mPageSwitchListener;

    protected ArrayList<Boolean> mDirtyPageContent;

    // If true, syncPages and syncPageItems will be called to refresh pages
    protected boolean mContentIsRefreshable = true;

    // If true, modify alpha of neighboring pages as user scrolls left/right
    protected boolean mFadeInAdjacentScreens = true;

    // If true, mFadeInAdjacentScreens will be handled manually
    protected boolean mHandleFadeInAdjacentScreens = false;

    // It true, use a different slop parameter (pagingTouchSlop = 2 * touchSlop) for deciding
    // to switch to a new page
    protected boolean mUsePagingTouchSlop = true;

    // If true, the subclass should directly update scrollX itself in its computeScroll method
    // (SmoothPagedView does this)
    protected boolean mDeferScrollUpdate = false;

    protected boolean mIsPageMoving = false;

    // All syncs and layout passes are deferred until data is ready.
    protected boolean mIsDataReady = false;

    // Scrolling indicator
    private ValueAnimator mScrollIndicatorAnimator;
    private View mScrollIndicator;
    private int mScrollIndicatorPaddingLeft;
    private int mScrollIndicatorPaddingTop;
    private int mScrollIndicatorPaddingRight;
    private int mScrollIndicatorPaddingBottom;
    private boolean mHasScrollIndicator = true;
    private boolean mShouldShowScrollIndicator = false;
    private boolean mShouldShowScrollIndicatorImmediately = false;
    protected static final int sScrollIndicatorFadeInDuration = 150;
    protected static final int sScrollIndicatorFadeOutDuration = 650;
    protected static final int sScrollIndicatorFadeOutShortDuration = 150;
    protected static final int sScrollIndicatorFlashDuration = 650;
    private boolean mScrollingPaused = false;

    // If set, will defer loading associated pages until the scrolling settles
    private boolean mDeferLoadAssociatedPagesUntilScrollCompletes;

    public interface PageSwitchListener {
        void onPageSwitch(View newPage, int newPageIndex);
    }

    public PagedView(Context context) {
        this(context, null);
    }

    public PagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.PagedView, defStyle, 0);
        setPageSpacing(a.getDimensionPixelSize(R.styleable.PagedView_pageSpacing, 0));
        mPageLayoutPaddingTop = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingTop, 0);
        mPageLayoutPaddingBottom = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingBottom, 0);
        mPageLayoutPaddingLeft = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingLeft, 0);
        mPageLayoutPaddingRight = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingRight, 0);
        mPageLayoutWidthGap = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutWidthGap, 0);
        mPageLayoutHeightGap = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutHeightGap, 0);
        mScrollIndicatorPaddingLeft =
            a.getDimensionPixelSize(R.styleable.PagedView_scrollIndicatorPaddingLeft, 0);
        mScrollIndicatorPaddingTop =
            a.getDimensionPixelSize(R.styleable.PagedView_scrollIndicatorPaddingTop, 0);
        mScrollIndicatorPaddingRight =
            a.getDimensionPixelSize(R.styleable.PagedView_scrollIndicatorPaddingRight, 0);
        mScrollIndicatorPaddingBottom =
            a.getDimensionPixelSize(R.styleable.PagedView_scrollIndicatorPaddingBottom, 0);
        a.recycle();

        setHapticFeedbackEnabled(false);
        init();
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void init() {
        mDirtyPageContent = new ArrayList<Boolean>();
        mDirtyPageContent.ensureCapacity(32);
        mScroller = new Scroller(getContext(), getScrollInterpolator());
        mCurrentPage = 0;
        mVertical = false;
        mCenterPages = true;

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mPagingTouchSlop = configuration.getScaledPagingTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mDensity = getResources().getDisplayMetrics().density;

        mFlingThresholdVelocity = (int) (FLING_THRESHOLD_VELOCITY * mDensity);
        mMinFlingVelocity = (int) (MIN_FLING_VELOCITY * mDensity);
        mMinSnapVelocity = (int) (MIN_SNAP_VELOCITY * mDensity);
        setOnHierarchyChangeListener(this);
    }

    public void setPageSwitchListener(PageSwitchListener pageSwitchListener) {
        mPageSwitchListener = pageSwitchListener;
        if (mPageSwitchListener != null) {
            mPageSwitchListener.onPageSwitch(getPageAt(mCurrentPage), mCurrentPage);
        }
    }

    /**
     * Called by subclasses to mark that data is ready, and that we can begin loading and laying
     * out pages.
     */
    protected void setDataIsReady() {
        mIsDataReady = true;
    }
    protected boolean isDataReady() {
        return mIsDataReady;
    }

    /**
     * Returns the index of the currently displayed page.
     *
     * @return The index of the currently displayed page.
     */
    int getCurrentPage() {
        return mCurrentPage;
    }
    int getNextPage() {
        return (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
    }

    int getPageCount() {
        return getChildCount();
    }

    View getPageAt(int index) {
        return getChildAt(index);
    }

    protected int indexToPage(int index) {
        return index;
    }

    /**
     * Updates the scroll of the current page immediately to its final scroll position.  We use this
     * in CustomizePagedView to allow tabs to share the same PagedView while resetting the scroll of
     * the previous tab page.
     */
    protected void updateCurrentPageScroll() {
        int newXY = getChildOffset(mCurrentPage) - getRelativeChildOffset(mCurrentPage);
        scrollTo(!mVertical ? newXY : 0, mVertical ? newXY : 0);
        if (!mVertical) {
            mScroller.setFinalX(newXY);
        } else {
            mScroller.setFinalY(newXY);
        }
        mScroller.forceFinished(true);
    }

    /**
     * Called during AllApps/Home transitions to avoid unnecessary work. When that other animation
     * ends, {@link #resumeScrolling()} should be called, along with
     * {@link #updateCurrentPageScroll()} to correctly set the final state and re-enable scrolling.
     */
    void pauseScrolling() {
        mScroller.forceFinished(true);
        cancelScrollingIndicatorAnimations();
        mScrollingPaused = true;
    }

    /**
     * Enables scrolling again.
     * @see #pauseScrolling()
     */
    void resumeScrolling() {
        mScrollingPaused = false;
    }
    /**
     * Sets the current page.
     */
    void setCurrentPage(int currentPage) {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
        // don't introduce any checks like mCurrentPage == currentPage here-- if we change the
        // the default
        if (getChildCount() == 0) {
            return;
        }


        mCurrentPage = Math.max(0, Math.min(currentPage, getPageCount() - 1));
        updateCurrentPageScroll();
        updateScrollingIndicator();
        notifyPageSwitchListener();
        invalidate();
    }

    protected void notifyPageSwitchListener() {
        if (mPageSwitchListener != null) {
            mPageSwitchListener.onPageSwitch(getPageAt(mCurrentPage), mCurrentPage);
        }
    }

    protected void pageBeginMoving() {
        if (!mIsPageMoving) {
            mIsPageMoving = true;
            onPageBeginMoving();
        }
    }

    protected void pageEndMoving() {
        if (mIsPageMoving) {
            mIsPageMoving = false;
            onPageEndMoving();
        }
    }

    protected boolean isPageMoving() {
        return mIsPageMoving;
    }

    // a method that subclasses can override to add behavior
    protected void onPageBeginMoving() {
    }

    // a method that subclasses can override to add behavior
    protected void onPageEndMoving() {
    }

    /**
     * Registers the specified listener on each page contained in this workspace.
     *
     * @param l The listener used to respond to long clicks.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mLongClickListener = l;
        final int count = getPageCount();
        for (int i = 0; i < count; i++) {
            getPageAt(i).setOnLongClickListener(l);
        }
    }

    @Override
    public void scrollBy(int x, int y) {
        scrollTo((!mVertical ? mUnboundedScrollX : mScrollX) + x, (mVertical ? mUnboundedScrollY : mScrollY) + y);
    }

    @Override
    public void scrollTo(int x, int y) {
        mUnboundedScrollX = x;
        mUnboundedScrollY = y;

        if (!mVertical) {
            if (x < 0) {
                super.scrollTo(0, y);
                if (mAllowOverScroll) {
                    overScroll(x);
                }
            } else if (x > mMaxScrollX) {
                super.scrollTo(mMaxScrollX, y);
                if (mAllowOverScroll) {
                    overScroll(x - mMaxScrollX);
                }
            } else {
                mOverScrollX = x;
                super.scrollTo(x, y);
            }
        } else {
            if (y < 0) {
                super.scrollTo(x, 0);
                if (mAllowOverScroll) {
                    overScroll(y);
                }
            } else if (y > mMaxScrollY) {
                super.scrollTo(x, mMaxScrollY);
                if (mAllowOverScroll) {
                    overScroll(y - mMaxScrollY);
                }
            } else {
                mOverScrollY = y;
                super.scrollTo(x, y);
            }
        }
        mTouchX = x;
        mTouchY = y;
        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
    }

    // we moved this functionality to a helper function so SmoothPagedView can reuse it
    protected boolean computeScrollHelper() {
        if (mScroller.computeScrollOffset()) {
            // Don't bother scrolling if the page does not need to be moved
            if (getScrollX() != mScroller.getCurrX()
                || getScrollY() != mScroller.getCurrY()
                || mOverScrollX != mScroller.getCurrX()) {
                scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            }
            invalidate();
            return true;
        } else if (mNextPage != INVALID_PAGE) {
            mCurrentPage = Math.max(0, Math.min(mNextPage, getPageCount() - 1));
            mNextPage = INVALID_PAGE;
            notifyPageSwitchListener();

            // Load the associated pages if necessary
            if (mDeferLoadAssociatedPagesUntilScrollCompletes) {
                loadAssociatedPages(mCurrentPage);
                mDeferLoadAssociatedPagesUntilScrollCompletes = false;
            }

            // We don't want to trigger a page end moving unless the page has settled
            // and the user has stopped scrolling
            if (mTouchState == TOUCH_STATE_REST) {
                pageEndMoving();
            }

            // Notify the user when the page changes
            AccessibilityManager accessibilityManager = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager.isEnabled()) {
                AccessibilityEvent ev =
                    AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED);
                ev.getText().add(getCurrentPageDescription());
                sendAccessibilityEventUnchecked(ev);
            }
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        computeScrollHelper();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!mIsDataReady) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        // Return early if we aren't given a proper dimension
        if (widthSize <= 0 || heightSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        if (!mVertical && widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Workspace can only be used in EXACTLY mode.");
        }
        if (mVertical && heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Workspace can only be used in EXACTLY mode.");
        }

        int maxChildHeight = 0;
        int maxChildWidth = 0;

        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        final int horizontalPadding = getPaddingLeft() + getPaddingRight();


        // The children are given the same width and height as the workspace
        // unless they were set to WRAP_CONTENT
        if (DEBUG) Log.d(TAG, "PagedView.onMeasure(): " + widthSize + ", " + heightSize);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            // disallowing padding in paged view (just pass 0)
            final View child = getPageAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            int childWidthMode;
            if (lp.width == LayoutParams.WRAP_CONTENT) {
                childWidthMode = MeasureSpec.AT_MOST;
            } else {
                childWidthMode = MeasureSpec.EXACTLY;
            }

            int childHeightMode;
            if (lp.height == LayoutParams.WRAP_CONTENT) {
                childHeightMode = MeasureSpec.AT_MOST;
            } else {
                childHeightMode = MeasureSpec.EXACTLY;
            }

            final int childWidthMeasureSpec =
                MeasureSpec.makeMeasureSpec(widthSize - horizontalPadding, childWidthMode);
            final int childHeightMeasureSpec =
                MeasureSpec.makeMeasureSpec(heightSize - verticalPadding, childHeightMode);

            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            maxChildHeight = Math.max(maxChildHeight, child.getMeasuredHeight());
            maxChildWidth = Math.max(maxChildWidth, child.getMeasuredWidth());
            if (DEBUG) Log.d(TAG, "\tmeasure-child" + i + ": " + child.getMeasuredWidth() + ", "
                    + child.getMeasuredHeight());
        }

        if (!mVertical && heightMode == MeasureSpec.AT_MOST) {
            heightSize = maxChildHeight + verticalPadding;
        } else if (mVertical && widthMode == MeasureSpec.AT_MOST) {
            widthSize = maxChildWidth + horizontalPadding;
        }

        setMeasuredDimension(widthSize, heightSize);

        // We can't call getChildOffset/getRelativeChildOffset until we set the measured dimensions.
        // We also wait until we set the measured dimensions before flushing the cache as well, to
        // ensure that the cache is filled with good values.
        invalidateCachedOffsets();

        if (childCount > 0) {
            if (DEBUG) Log.d(TAG, "getRelativeChildOffset(): " + getMeasuredWidth() + ", "
                    + getChildWidth(0));

            // Calculate the variable page spacing if necessary
            if (mPageSpacing == AUTOMATIC_PAGE_SPACING) {
                // The gap between pages in the PagedView should be equal to the gap from the page
                // to the edge of the screen (so it is not visible in the current screen).  To
                // account for unequal padding on each side of the paged view, we take the maximum
                // of the left/right gap and use that as the gap between each page.
                int offset = getRelativeChildOffset(0);
                int spacing = Math.max(offset, widthSize - offset -
                        getChildAt(0).getMeasuredWidth());
                setPageSpacing(spacing);
            }
        }

        updateScrollingIndicatorPosition();

        if (childCount > 0) {
            mMaxScrollX = mMaxScrollY = getChildOffset(childCount - 1) - getRelativeChildOffset(childCount - 1);
        } else {
            mMaxScrollX = mMaxScrollY = 0;
        }
    }

    protected void scrollToNewPageWithoutMovingPages(int newCurrentPage) {
        int newXY = getChildOffset(newCurrentPage) - getRelativeChildOffset(newCurrentPage);
        int delta = newXY - (!mVertical ? mScrollX : mScrollY);

        final int pageCount = getChildCount();
        for (int i = 0; i < pageCount; i++) {
            View page = getPageAt(i);
            if (!mVertical) {
                page.setX(page.getX() + delta);
            } else {
                page.setY(page.getY() + delta);
            }
        }
        setCurrentPage(newCurrentPage);
    }

    // A layout scale of 1.0f assumes that the pages, in their unshrunken state, have a
    // scale of 1.0f. A layout scale of 0.8f assumes the pages have a scale of 0.8f, and
    // tightens the layout accordingly
    public void setLayoutScale(float childrenScale) {
        mLayoutScale = childrenScale;
        invalidateCachedOffsets();

        // Now we need to do a re-layout, but preserving absolute X and Y coordinates
        int childCount = getChildCount();
        float childrenX[] = new float[childCount];
        float childrenY[] = new float[childCount];
        for (int i = 0; i < childCount; i++) {
            final View child = getPageAt(i);
            childrenX[i] = child.getX();
            childrenY[i] = child.getY();
        }
        // Trigger a full re-layout (never just call onLayout directly!)
        int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
        requestLayout();
        measure(widthSpec, heightSpec);
        layout(getLeft(), getTop(), getRight(), getBottom());
        for (int i = 0; i < childCount; i++) {
            final View child = getPageAt(i);
            child.setX(childrenX[i]);
            child.setY(childrenY[i]);
        }

        // Also, the page offset has changed  (since the pages are now smaller);
        // update the page offset, but again preserving absolute X and Y coordinates
        scrollToNewPageWithoutMovingPages(mCurrentPage);
    }

    public void setPageSpacing(int pageSpacing) {
        mPageSpacing = pageSpacing;
        invalidateCachedOffsets();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!mIsDataReady) {
            return;
        }

        if (DEBUG) Log.d(TAG, "PagedView.onLayout()");
        final int verticalPadding = mPaddingTop + mPaddingBottom;
        final int horizontalPadding = mPaddingLeft + mPaddingRight;
        final int childCount = getChildCount();
        int childLeft = 0;
        int childTop = 0;
        if (childCount > 0) {
            if (DEBUG) Log.d(TAG, "getRelativeChildOffset(): " + getMeasuredWidth() + ", "
                    + getChildWidth(0));

            if (!mVertical) {
                childLeft = getRelativeChildOffset(0);
            } else {
                childTop = getRelativeChildOffset(0);
            }

            // Calculate the variable page spacing if necessary
            if (mPageSpacing < 0) {
                if (!mVertical) {
                    setPageSpacing(((right - left) - getChildAt(0).getMeasuredWidth()) / 2);
                } else {
                    setPageSpacing(((top - bottom) - getChildAt(0).getMeasuredHeight()) / 2);
                }
            }
        }

        for (int i = 0; i < childCount; i++) {
            final View child = getPageAt(i);
            if (child.getVisibility() != View.GONE) {
                final int childWidth = !mVertical ? getScaledMeasuredWidth(child) : child.getMeasuredWidth();
                final int childHeight = mVertical ? getScaledMeasuredHeight(child) : child.getMeasuredHeight();
                if (!mVertical) {
                    childTop = mPaddingTop;
                } else {
                    childLeft = mPaddingLeft;
                }

                if (mCenterPages) {
                    if (!mVertical) {
                        childTop += ((getMeasuredHeight() - verticalPadding) - childHeight) / 2;
                    } else {
                        childLeft += ((getMeasuredWidth() - horizontalPadding) - childWidth) / 2;
                    }
                }

                if (DEBUG) Log.d(TAG, "\tlayout-child" + i + ": " + childLeft + ", " + childTop);
                child.layout(childLeft, childTop,
                        childLeft + (!mVertical ? child.getMeasuredWidth() : childWidth),
                        childTop + (mVertical ? child.getMeasuredHeight() : childHeight));
                if (!mVertical) {
                    childLeft += childWidth + mPageSpacing;
                } else {
                    childTop += childHeight + mPageSpacing;
                }
            }
        }

        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getChildCount()) {
            int newXY = getChildOffset(mCurrentPage) - getRelativeChildOffset(mCurrentPage);
            if (!mVertical) {
                setHorizontalScrollBarEnabled(false);
                scrollTo(newXY, 0);
                mScroller.setFinalX(newXY);
                setHorizontalScrollBarEnabled(true);
            } else {
                setVerticalScrollBarEnabled(false);
                scrollTo(0, newXY);
                mScroller.setFinalY(newXY);
                setVerticalScrollBarEnabled(true);
            }
            mFirstLayout = false;
        }
    }

    protected void screenScrolled(int screenScroll) {
        if (isScrollingIndicatorEnabled()) {
            updateScrollingIndicator();
        }
        boolean isInOverscroll = mOverScrollX < 0 || mOverScrollX > mMaxScrollX;

        if (mFadeInAdjacentScreens && !isInOverscroll && !mHandleFadeInAdjacentScreens) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != null) {
                    float scrollProgress = getScrollProgress(screenScroll, child, i);
                    float alpha = 1 - Math.abs(scrollProgress);
                    child.setAlpha(alpha);
                }
            }
            invalidate();
        }
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        // This ensures that when children are added, they get the correct transforms / alphas
        // in accordance with any scroll effects.
        mForceScreenScrolled = true;
        invalidate();
        invalidateCachedOffsets();
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
    }

    protected void invalidateCachedOffsets() {
        int count = getChildCount();
        if (count == 0) {
            mChildOffsets = null;
            mChildRelativeOffsets = null;
            mChildOffsetsWithLayoutScale = null;
            return;
        }

        mChildOffsets = new int[count];
        mChildRelativeOffsets = new int[count];
        mChildOffsetsWithLayoutScale = new int[count];
        for (int i = 0; i < count; i++) {
            mChildOffsets[i] = -1;
            mChildRelativeOffsets[i] = -1;
            mChildOffsetsWithLayoutScale[i] = -1;
        }
    }

    protected int getChildOffset(int index) {
        int[] childOffsets = Float.compare(mLayoutScale, 1f) == 0 ?
                mChildOffsets : mChildOffsetsWithLayoutScale;

        if (childOffsets != null && childOffsets[index] != -1) {
            return childOffsets[index];
        } else {
            if (getChildCount() == 0)
                return 0;

            int offset = getRelativeChildOffset(0);
            for (int i = 0; i < index; ++i) {
                offset += (!mVertical ? getScaledMeasuredWidth(getPageAt(i)) :
                        getScaledMeasuredHeight(getPageAt(i))) + mPageSpacing;
            }
            if (childOffsets != null) {
                childOffsets[index] = offset;
            }
            return offset;
        }
    }

    protected int getRelativeChildOffset(int index) {
        if (mChildRelativeOffsets != null && mChildRelativeOffsets[index] != -1) {
            return mChildRelativeOffsets[index];
        } else {
            int offset;
            if (!mVertical) {
                final int padding = mPaddingLeft + mPaddingRight;
                offset = mPaddingLeft +
                        (getMeasuredWidth() - padding - getChildWidth(index)) / 2;
            } else {
                final int padding = mPaddingTop + mPaddingBottom;
                offset = mPaddingTop +
                        (getMeasuredHeight() - padding - getChildHeight(index)) / 2;
            }
            if (mChildRelativeOffsets != null) {
                mChildRelativeOffsets[index] = offset;
            }
            return offset;
        }
    }

    protected int getScaledRelativeChildOffset(int index) {
        int offset;
        if (!mVertical) {
            final int padding = mPaddingLeft + mPaddingRight;
            offset = mPaddingLeft + (getMeasuredWidth() - padding -
                    getScaledMeasuredWidth(getPageAt(index))) / 2;
        } else {
            final int padding = mPaddingTop + mPaddingBottom;
            offset = mPaddingTop + (getMeasuredHeight() - padding -
                    getScaledMeasuredHeight(getPageAt(index))) / 2;
        }
        return offset;
    }

    protected int getScaledMeasuredWidth(View child) {
        // This functions are called enough times that it actually makes a difference in the
        // profiler -- so just inline the max() here
        final int measuredWidth = child.getMeasuredWidth();
        final int minWidth = mMinimumWidth;
        final int maxWidth = (minWidth > measuredWidth) ? minWidth : measuredWidth;
        return (int) (maxWidth * mLayoutScale + 0.5f);
    }

    protected int getScaledMeasuredHeight(View child) {
        // This functions are called enough times that it actually makes a difference in the
        // profiler -- so just inline the max() here
        final int measuredHeight = child.getMeasuredHeight();
        final int minHeight = mMinimumHeight;
        final int maxHeight = (minHeight > measuredHeight) ? minHeight : measuredHeight;
        return (int) (maxHeight * mLayoutScale + 0.5f);
    }

    protected void getVisiblePages(int[] range) {
        final int pageCount = getChildCount();

        if (pageCount > 0) {
            if (!mVertical) {
                final int pageWidth = getScaledMeasuredWidth(getPageAt(0));
                final int screenWidth = getMeasuredWidth();
                int x = getScaledRelativeChildOffset(0) + pageWidth;
                int leftScreen = 0;
                int rightScreen = 0;
                while (x <= mScrollX && leftScreen < pageCount - 1) {
                    leftScreen++;
                    x += getScaledMeasuredWidth(getPageAt(leftScreen)) + mPageSpacing;
                }
                rightScreen = leftScreen;


                while (x < mScrollX + screenWidth && rightScreen < pageCount - 1) {
                    rightScreen++;
                    x += getScaledMeasuredWidth(getPageAt(rightScreen)) + mPageSpacing;
                }
                range[0] = leftScreen;
                range[1] = rightScreen;
            } else {
                final int pageHeight = getScaledMeasuredHeight(getPageAt(0));
                final int screenHeight = getMeasuredHeight();
                int y = getScaledRelativeChildOffset(0) + pageHeight;
                int topScreen = 0;
                int bottomScreen = 0;
                while (y <= mScrollY && topScreen < pageCount - 1) {
                    topScreen++;
                    y += getScaledMeasuredHeight(getPageAt(topScreen)) + mPageSpacing;
                }
                bottomScreen = topScreen;
                while (y < mScrollY + screenHeight && bottomScreen < pageCount - 1) {
                    bottomScreen++;
                    y += getScaledMeasuredHeight(getPageAt(bottomScreen)) + mPageSpacing;
                }
                range[0] = topScreen;
                range[1] = bottomScreen;
            }
        } else {
            range[0] = -1;
            range[1] = -1;
        }
    }

    protected boolean shouldDrawChild(View child) {
        return child.getAlpha() > 0 && child.getVisibility() == VISIBLE;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!mVertical) {
            if (mOverScrollX != mLastScreenScroll || mForceScreenScrolled) {
                screenScrolled(mOverScrollX);
                mLastScreenScroll = mOverScrollX;
                mForceScreenScrolled = false;
            }
        } else {
            if (mOverScrollY != mLastScreenScroll || mForceScreenScrolled) {
                screenScrolled(mOverScrollY);
                mLastScreenScroll = mOverScrollY;
                mForceScreenScrolled = false;
            }
        }

        // Find out which screens are visible; as an optimization we only call draw on them
        final int pageCount = getChildCount();
        if (pageCount > 0) {
            getVisiblePages(mTempVisiblePagesRange);
            final int leftTopScreen = mTempVisiblePagesRange[0];
            final int rightBottomScreen = mTempVisiblePagesRange[1];
            if (leftTopScreen != -1 && rightBottomScreen != -1) {
                final long drawingTime = getDrawingTime();
                // Clip to the bounds
                canvas.save();
                canvas.clipRect(getScrollX(), getScrollY(), getScrollX() + getRight() - getLeft(),
                        getScrollY() + getBottom() - getTop());

                for (int i = getChildCount() - 1; i >= 0; i--) {
                    final View v = getPageAt(i);
                    if ((mForceDrawAllChildrenNextFrame || (leftTopScreen <= i && i <= rightBottomScreen))
                            && shouldDrawChild(v)) {
                        drawChild(canvas, v, drawingTime);
                    }
                }
                mForceDrawAllChildrenNextFrame = false;
                canvas.restore();
            }
        }
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int page = indexToPage(indexOfChild(child));
        if (page != mCurrentPage || !mScroller.isFinished()) {
            snapToPage(page);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int focusablePage;
        if (mNextPage != INVALID_PAGE) {
            focusablePage = mNextPage;
        } else {
            focusablePage = mCurrentPage;
        }
        View v = getPageAt(focusablePage);
        if (v != null) {
            return v.requestFocus(direction, previouslyFocusedRect);
        }
        return false;
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (direction == View.FOCUS_LEFT) {
            if (getCurrentPage() > 0) {
                snapToPage(getCurrentPage() - 1);
                return true;
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (getCurrentPage() < getPageCount() - 1) {
                snapToPage(getCurrentPage() + 1);
                return true;
            }
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (mCurrentPage >= 0 && mCurrentPage < getPageCount()) {
            getPageAt(mCurrentPage).addFocusables(views, direction, focusableMode);
        }
        if (direction == View.FOCUS_LEFT) {
            if (mCurrentPage > 0) {
                getPageAt(mCurrentPage - 1).addFocusables(views, direction, focusableMode);
            }
        } else if (direction == View.FOCUS_RIGHT){
            if (mCurrentPage < getPageCount() - 1) {
                getPageAt(mCurrentPage + 1).addFocusables(views, direction, focusableMode);
            }
        }
    }

    /**
     * If one of our descendant views decides that it could be focused now, only
     * pass that along if it's on the current page.
     *
     * This happens when live folders requery, and if they're off page, they
     * end up calling requestFocus, which pulls it on page.
     */
    @Override
    public void focusableViewAvailable(View focused) {
        View current = getPageAt(mCurrentPage);
        View v = focused;
        while (true) {
            if (v == current) {
                super.focusableViewAvailable(focused);
                return;
            }
            if (v == this) {
                return;
            }
            ViewParent parent = v.getParent();
            if (parent instanceof View) {
                v = (View)v.getParent();
            } else {
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            // We need to make sure to cancel our long press if
            // a scrollable widget takes over touch events
            final View currentPage = getPageAt(mCurrentPage);
            currentPage.cancelLongPress();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    /**
     * Return true if a tap at (x, y) should trigger a flip to the previous page.
     */
    protected boolean hitsPreviousPage(float x, float y) {
        return ((!mVertical ? x : y) < getRelativeChildOffset(mCurrentPage) - mPageSpacing);
    }

    /**
     * Return true if a tap at (x, y) should trigger a flip to the next page.
     */
    protected boolean hitsNextPage(float x, float y) {
        return  ((!mVertical ? x : y) > ((!mVertical ? getMeasuredWidth() : getMeasuredHeight()) -
                getRelativeChildOffset(mCurrentPage) + mPageSpacing));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */
        acquireVelocityTrackerAndAddMovement(ev);

        // Skip touch handling if there are no pages to swipe
        if (getChildCount() <= 0) return super.onInterceptTouchEvent(ev);

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) &&
                (mTouchState == TOUCH_STATE_SCROLLING)) {
            return true;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */
                if (mActivePointerId != INVALID_POINTER) {
                    determineScrollingStart(ev);
                    break;
                }
                // if mActivePointerId is INVALID_POINTER, then we must have missed an ACTION_DOWN
                // event. in that case, treat the first occurence of a move event as a ACTION_DOWN
                // i.e. fall through to the next case (don't break)
                // (We sometimes miss ACTION_DOWN events in Workspace because it ignores all events
                // while it's small- this was causing a crash before we checked for INVALID_POINTER)
            }

            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                // Remember location of down touch
                mDownMotionX = x;
                mDownMotionY = y;
                mLastMotionX = x;
                mLastMotionY = y;
                mLastMotionXRemainder = 0;
                mLastMotionYRemainder = 0;
                mTotalMotionX = 0;
                mTotalMotionY = 0;
                mActivePointerId = ev.getPointerId(0);
                mAllowLongPress = true;

                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                final int xyDist = Math.abs(!mVertical ? (mScroller.getFinalX() - mScroller.getCurrX()) :
                        (mScroller.getFinalY() - mScroller.getCurrY()));
                final boolean finishedScrolling = (mScroller.isFinished() || xyDist < mTouchSlop);
                if (finishedScrolling) {
                    mTouchState = TOUCH_STATE_REST;
                    mScroller.abortAnimation();
                } else {
                    mTouchState = TOUCH_STATE_SCROLLING;
                }

                // check if this can be the beginning of a tap on the side of the pages
                // to scroll the current page
                if (mTouchState != TOUCH_STATE_PREV_PAGE && mTouchState != TOUCH_STATE_NEXT_PAGE) {
                    if (getChildCount() > 0) {
                        if (hitsPreviousPage(x, y)) {
                            mTouchState = TOUCH_STATE_PREV_PAGE;
                        } else if (hitsNextPage(x, y)) {
                            mTouchState = TOUCH_STATE_NEXT_PAGE;
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchState = TOUCH_STATE_REST;
                mAllowLongPress = false;
                mActivePointerId = INVALID_POINTER;
                releaseVelocityTracker();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                releaseVelocityTracker();
                break;
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mTouchState != TOUCH_STATE_REST;
    }

    protected void determineScrollingStart(MotionEvent ev) {
        determineScrollingStart(ev, 1.0f);
    }

    /*
     * Determines if we should change the touch state to start scrolling after the
     * user moves their touch point too far.
     */
    protected void determineScrollingStart(MotionEvent ev, float touchSlopScale) {
        /*
         * Locally do absolute value. mLastMotionX is set to the y value
         * of the down event.
         */
        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex == -1) {
            return;
        }
        final float x = ev.getX(pointerIndex);
        final float y = ev.getY(pointerIndex);
        final int xDiff = (int) Math.abs(x - mLastMotionX);
        final int yDiff = (int) Math.abs(y - mLastMotionY);

        final int touchSlop = Math.round(touchSlopScale * mTouchSlop);
        boolean xPaged = xDiff > mPagingTouchSlop;
        boolean yPaged = yDiff > mPagingTouchSlop;
        boolean xMoved = xDiff > touchSlop;
        boolean yMoved = yDiff > touchSlop;

        if (xMoved || (!mVertical ? xPaged : yPaged) || yMoved) {
            if (!mVertical) {
                if (mUsePagingTouchSlop ? xPaged : xMoved) {
                    // Scroll if the user moved far enough along the X axis
                    mTouchState = TOUCH_STATE_SCROLLING;
                    mTotalMotionX += Math.abs(mLastMotionX - x);
                    mLastMotionX = x;
                    mLastMotionXRemainder = 0;
                    mTouchX = mScrollX;
                    pageBeginMoving();
                }
            } else {
                if (mUsePagingTouchSlop ? yPaged : yMoved) {
                    // Scroll if the user moved far enough along the X axis
                    mTouchState = TOUCH_STATE_SCROLLING;
                    mTotalMotionY += Math.abs(mLastMotionY - y);
                    mLastMotionY = y;
                    mLastMotionYRemainder = 0;
                    mTouchY = mScrollY;
                    pageBeginMoving();
                }
            }
            // Either way, cancel any pending longpress
            cancelCurrentPageLongPress();
        }
    }

    protected void cancelCurrentPageLongPress() {
        if (mAllowLongPress) {
            mAllowLongPress = false;
            // Try canceling the long press. It could also have been scheduled
            // by a distant descendant, so use the mAllowLongPress flag to block
            // everything
            final View currentPage = getPageAt(mCurrentPage);
            if (currentPage != null) {
                currentPage.cancelLongPress();
            }
        }
    }

    protected float getScrollProgress(int screenScroll, View v, int page) {
        final int halfScreenSize = (!mVertical ? getMeasuredWidth() : getMeasuredHeight()) / 2;
        int screenCenter = screenScroll + (!mVertical ? getMeasuredWidth() : getMeasuredHeight()) / 2;

        int totalDistance = (!mVertical ? getScaledMeasuredWidth(v) :
                getScaledMeasuredHeight(v)) + mPageSpacing;
        int delta = screenCenter - (getChildOffset(page) -
                getRelativeChildOffset(page) + halfScreenSize);

        float scrollProgress = delta / (totalDistance * 1.0f);
        scrollProgress = Math.min(scrollProgress, 1.0f);
        scrollProgress = Math.max(scrollProgress, -1.0f);
        return scrollProgress;
    }

    // This curve determines how the effect of scrolling over the limits of the page dimishes
    // as the user pulls further and further from the bounds
    private float overScrollInfluenceCurve(float f) {
        f -= 1.0f;
        return f * f * f + 1.0f;
    }

    protected void acceleratedOverScroll(float amount) {
        int screenSize = !mVertical ? getMeasuredWidth() : getMeasuredHeight();

        // We want to reach the max over scroll effect when the user has
        // over scrolled half the size of the screen
        float f = OVERSCROLL_ACCELERATE_FACTOR * (amount / screenSize);

        if (f == 0) return;

        // Clamp this factor, f, to -1 < f < 1
        if (Math.abs(f) >= 1) {
            f /= Math.abs(f);
        }

        int overScrollAmount = (int) Math.round(f * screenSize);
        if (amount < 0) {
            if (!mVertical) {
                mOverScrollX = overScrollAmount;
                mScrollX = 0;
            } else {
                mOverScrollY = overScrollAmount;
                mScrollY = 0;
            }
        } else {
            if (!mVertical) {
                mOverScrollX = mMaxScrollX + overScrollAmount;
                mScrollX = mMaxScrollX;
            } else {
                mOverScrollY = mMaxScrollY + overScrollAmount;
                mScrollY = mMaxScrollY;
            }
        }
        invalidate();
    }

    protected void dampedOverScroll(float amount) {
        int screenSize = !mVertical ? getMeasuredWidth() : getMeasuredHeight();

        float f = (amount / screenSize);

        if (f == 0) return;
        f = f / (Math.abs(f)) * (overScrollInfluenceCurve(Math.abs(f)));

        // Clamp this factor, f, to -1 < f < 1
        if (Math.abs(f) >= 1) {
            f /= Math.abs(f);
        }

        int overScrollAmount = (int) Math.round(OVERSCROLL_DAMP_FACTOR * f * screenSize);
        if (amount < 0) {
            if (!mVertical) {
                mOverScrollX = overScrollAmount;
                mScrollX = 0;
            } else {
                mOverScrollY = overScrollAmount;
                mScrollY = 0;
            }
        } else {
            if (!mVertical) {
                mOverScrollX = mMaxScrollX + overScrollAmount;
                mScrollX = mMaxScrollX;
            } else {
                mOverScrollY = mMaxScrollY + overScrollAmount;
                mScrollY = mMaxScrollY;
            }
        }
        invalidate();
    }

    protected void overScroll(float amount) {
        dampedOverScroll(amount);
    }

    protected float maxOverScroll() {
        // Using the formula in overScroll, assuming that f = 1.0 (which it should generally not
        // exceed). Used to find out how much extra wallpaper we need for the over scroll effect
        float f = 1.0f;
        f = f / (Math.abs(f)) * (overScrollInfluenceCurve(Math.abs(f)));
        return OVERSCROLL_DAMP_FACTOR * f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Skip touch handling if there are no pages to swipe
        if (getChildCount() <= 0) return super.onTouchEvent(ev);

        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            /*
             * If being flinged and user touches, stop the fling. isFinished
             * will be false if being flinged.
             */
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }

            // Remember where the motion event started
            mDownMotionX = mLastMotionX = ev.getX();
            mDownMotionY = mLastMotionY = ev.getY();
            mLastMotionXRemainder = 0;
            mLastMotionYRemainder = 0;
            mTotalMotionX = 0;
            mTotalMotionY = 0;
            mActivePointerId = ev.getPointerId(0);
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                pageBeginMoving();
            }
            break;

        case MotionEvent.ACTION_MOVE:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                // Scroll to follow the motion event
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = ev.getX(pointerIndex);
                final float deltaX = mLastMotionX + mLastMotionXRemainder - x;
                final float y = ev.getY(pointerIndex);
                final float deltaY = mLastMotionY + mLastMotionYRemainder - y;

                mTotalMotionX += Math.abs(deltaX);
                mTotalMotionY += Math.abs(deltaY);

                // Only scroll and update mLastMotionX if we have moved some discrete amount.  We
                // keep the remainder because we are actually testing if we've moved from the last
                // scrolled position (which is discrete).
                if (Math.abs(!mVertical ? deltaX : deltaY) >= 1.0f) {
                    if (!mVertical) {
                        mTouchX += deltaX;
                        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                        if (!mDeferScrollUpdate) {
                            scrollBy((int) deltaX, 0);
                            if (DEBUG) Log.d(TAG, "onTouchEvent().Scrolling: " + deltaX);
                        } else {
                            invalidate();
                        }
                        mLastMotionX = x;
                        mLastMotionXRemainder = deltaX - (int) deltaX;
                    } else {
                        mTouchY += deltaY;
                        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                        if (!mDeferScrollUpdate) {
                            scrollBy(0, (int) deltaY);
                            if (DEBUG) Log.d(TAG, "onTouchEvent().Scrolling: " + deltaY);
                        } else {
                            invalidate();
                        }
                        mLastMotionY = y;
                        mLastMotionYRemainder = deltaY - (int) deltaY;
                    }
                } else {
                    awakenScrollBars();
                }
            } else {
                determineScrollingStart(ev);
            }
            break;

        case MotionEvent.ACTION_UP:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                final int activePointerId = mActivePointerId;
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityX = (int) velocityTracker.getXVelocity(activePointerId);
                int velocityY = (int) velocityTracker.getYVelocity(activePointerId);
                final int deltaX = (int) (x - mDownMotionX);
                final int deltaY = (int) (y - mDownMotionY);
                final int pageWidth = getScaledMeasuredWidth(getPageAt(mCurrentPage));
                boolean isSignificantMove = Math.abs(!mVertical ? deltaX : deltaY) > pageWidth *
                        SIGNIFICANT_MOVE_THRESHOLD;

                mTotalMotionX += Math.abs(mLastMotionX + mLastMotionXRemainder - x);
                mTotalMotionY += Math.abs(mLastMotionY + mLastMotionYRemainder - y);

                boolean isFling = mTotalMotionX > MIN_LENGTH_FOR_FLING &&
                        Math.abs(!mVertical ? velocityX : velocityY) > mFlingThresholdVelocity;

                // In the case that the page is moved far to one direction and then is flung
                // in the opposite direction, we use a threshold to determine whether we should
                // just return to the starting page, or if we should skip one further.
                boolean returnToOriginalPage = false;
                if (Math.abs(!mVertical ? deltaX : deltaY) > pageWidth * RETURN_TO_ORIGINAL_PAGE_THRESHOLD &&
                        Math.signum(!mVertical ? velocityX : velocityY) !=
                        Math.signum(!mVertical ? deltaX : deltaY) && isFling) {
                    returnToOriginalPage = true;
                }

                int finalPage;
                // We give flings precedence over large moves, which is why we short-circuit our
                // test for a large move if a fling has been registered. That is, a large
                // move to the left and fling to the right will register as a fling to the right.
                if (!mVertical) {
                    if (((isSignificantMove && deltaX > 0 && !isFling) ||
                            (isFling && velocityX > 0)) && mCurrentPage > 0) {
                        finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage - 1;
                        snapToPageWithVelocity(finalPage, velocityX);
                    } else if (((isSignificantMove && deltaX < 0 && !isFling) ||
                            (isFling && velocityX < 0)) &&
                            mCurrentPage < getChildCount() - 1) {
                        finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage + 1;
                        snapToPageWithVelocity(finalPage, velocityX);
                    } else {
                        snapToDestination();
                    }
                } else {
                    if (((isSignificantMove && deltaY > 0 && !isFling) ||
                            (isFling && velocityY > 0)) && mCurrentPage > 0) {
                        finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage - 1;
                        snapToPageWithVelocity(finalPage, velocityY);
                    } else if (((isSignificantMove && deltaY < 0 && !isFling) ||
                            (isFling && velocityY < 0)) &&
                            mCurrentPage < getChildCount() - 1) {
                        finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage + 1;
                        snapToPageWithVelocity(finalPage, velocityY);
                    } else {
                        snapToDestination();
                    }
                }
            } else if (mTouchState == TOUCH_STATE_PREV_PAGE) {
                // at this point we have not moved beyond the touch slop
                // (otherwise mTouchState would be TOUCH_STATE_SCROLLING), so
                // we can just page
                int nextPage = Math.max(0, mCurrentPage - 1);
                if (nextPage != mCurrentPage) {
                    snapToPage(nextPage);
                } else {
                    snapToDestination();
                }
            } else if (mTouchState == TOUCH_STATE_NEXT_PAGE) {
                // at this point we have not moved beyond the touch slop
                // (otherwise mTouchState would be TOUCH_STATE_SCROLLING), so
                // we can just page
                int nextPage = Math.min(getChildCount() - 1, mCurrentPage + 1);
                if (nextPage != mCurrentPage) {
                    snapToPage(nextPage);
                } else {
                    snapToDestination();
                }
            } else {
                onUnhandledTap(ev);
            }
            mTouchState = TOUCH_STATE_REST;
            mActivePointerId = INVALID_POINTER;
            releaseVelocityTracker();
            break;

        case MotionEvent.ACTION_CANCEL:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                snapToDestination();
            }
            mTouchState = TOUCH_STATE_REST;
            mActivePointerId = INVALID_POINTER;
            releaseVelocityTracker();
            break;

        case MotionEvent.ACTION_POINTER_UP:
            onSecondaryPointerUp(ev);
            break;
        }

        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    // Handle mouse (or ext. device) by shifting the page depending on the scroll
                    final float vscroll;
                    final float hscroll;
                    if ((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
                        vscroll = 0;
                        hscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    } else {
                        vscroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                        hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                    }
                    if (hscroll != 0 || vscroll != 0) {
                        if (hscroll > 0 || vscroll > 0) {
                            scrollRight();
                        } else {
                            scrollLeft();
                        }
                        return true;
                    }
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    private void acquireVelocityTrackerAndAddMovement(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = mDownMotionX = ev.getX(newPointerIndex);
            mLastMotionY = mDownMotionY = ev.getY(newPointerIndex);
            mLastMotionXRemainder = 0;
            mLastMotionYRemainder = 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    protected void onUnhandledTap(MotionEvent ev) {}

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int page = indexToPage(indexOfChild(child));
        if (page >= 0 && page != getCurrentPage() && !isInTouchMode()) {
            snapToPage(page);
        }
    }

    protected int getChildIndexForRelativeOffset(int relativeOffset) {
        final int childCount = getChildCount();
        int leftTop;
        int rightBottom;
        for (int i = 0; i < childCount; ++i) {
            leftTop = getRelativeChildOffset(i);
            rightBottom = (leftTop + (!mVertical ? getScaledMeasuredWidth(getPageAt(i)) :
                    getScaledMeasuredHeight(getPageAt(i))));
            if (leftTop <= relativeOffset && relativeOffset <= rightBottom) {
                return i;
            }
        }
        return -1;
    }

    protected int getChildWidth(int index) {
        // This functions are called enough times that it actually makes a difference in the
        // profiler -- so just inline the max() here
        final int measuredWidth = getPageAt(index).getMeasuredWidth();
        final int minWidth = mMinimumWidth;
        return (minWidth > measuredWidth) ? minWidth : measuredWidth;
    }

    protected int getChildHeight(int index) {
        // This functions are called enough times that it actually makes a difference in the
        // profiler -- so just inline the max() here
        final int measuredHeight = getPageAt(index).getMeasuredHeight();
        final int minHeight = mMinimumHeight;
        return (minHeight > measuredHeight) ? minHeight : measuredHeight;
    }

    int getPageNearestToCenterOfScreen() {
        int minDistanceFromScreenCenter = Integer.MAX_VALUE;
        int minDistanceFromScreenCenterIndex = -1;
        int screenCenter = !mVertical ? mScrollX + (getMeasuredWidth() / 2) :
                mScrollY + (getMeasuredHeight() / 2);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            View layout = getPageAt(i);
            int halfChild;
            if (!mVertical) {
                int childWidth = getScaledMeasuredWidth(layout);
                halfChild = (childWidth / 2);
            } else {
                int childHeight = getScaledMeasuredHeight(layout);
                halfChild = (childHeight / 2);
            }
            int childCenter = getChildOffset(i) + halfChild;
            int distanceFromScreenCenter = Math.abs(childCenter - screenCenter);
            if (distanceFromScreenCenter < minDistanceFromScreenCenter) {
                minDistanceFromScreenCenter = distanceFromScreenCenter;
                minDistanceFromScreenCenterIndex = i;
            }
        }
        return minDistanceFromScreenCenterIndex;
    }

    protected void snapToDestination() {
        snapToPage(getPageNearestToCenterOfScreen(), PAGE_SNAP_ANIMATION_DURATION);
    }

    public static class QuintInterpolator implements Interpolator {
        public QuintInterpolator() {
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            return t*t*t*t*t + 1;
        }
    }

    public static class QuadInterpolator implements Interpolator {
        public QuadInterpolator() {
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            return -(t*t*t*t - 1);
        }
    }

    protected Interpolator getScrollInterpolator() {
        return new QuintInterpolator();
    }

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    protected void snapToPageWithVelocity(int whichPage, int velocity) {
        whichPage = Math.max(0, Math.min(whichPage, getChildCount() - 1));
        int halfScreenSize = (!mVertical ? getMeasuredWidth() : getMeasuredHeight()) / 2;

        if (DEBUG) Log.d(TAG, "snapToPage.getChildOffset(): " + getChildOffset(whichPage));
        if (DEBUG) Log.d(TAG, "snapToPageWithVelocity.getRelativeChildOffset(): "
                + getMeasuredWidth() + ", " + getChildWidth(whichPage));
        final int newXY = getChildOffset(whichPage) - getRelativeChildOffset(whichPage);
        int delta = newXY - (!mVertical ? mUnboundedScrollX : mUnboundedScrollY);
        int duration = 0;

        if (Math.abs(velocity) < mMinFlingVelocity) {
            // If the velocity is low enough, then treat this more as an automatic page advance
            // as opposed to an apparent physical response to flinging
            snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
            return;
        }

        // Here we compute a "distance" that will be used in the computation of the overall
        // snap duration. This is a function of the actual distance that needs to be traveled;
        // we keep this value close to half screen size in order to reduce the variance in snap
        // duration as a function of the distance the page needs to travel.
        float distanceRatio = Math.min(1f, 1.0f * Math.abs(delta) / (2 * halfScreenSize));
        float distance = halfScreenSize + halfScreenSize *
                distanceInfluenceForSnapDuration(distanceRatio);

        velocity = Math.abs(velocity);
        velocity = Math.max(mMinSnapVelocity, velocity);

        // we want the page's snap velocity to approximately match the velocity at which the
        // user flings, so we scale the duration by a value near to the derivative of the scroll
        // interpolator at zero, ie. 5. We use 4 to make it a little slower.
        duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        duration = Math.min(duration, MAX_PAGE_SNAP_DURATION);

        snapToPage(whichPage, delta, duration);
    }

    protected void snapToPage(int whichPage) {
        snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
    }

    protected void snapToPage(int whichPage, int duration) {
        whichPage = Math.max(0, Math.min(whichPage, getPageCount() - 1));

        if (DEBUG) Log.d(TAG, "snapToPage.getChildOffset(): " + getChildOffset(whichPage));
        if (DEBUG) Log.d(TAG, "snapToPage.getRelativeChildOffset(): " + getMeasuredWidth() + ", "
                + getChildWidth(whichPage));
        int newXY = getChildOffset(whichPage) - getRelativeChildOffset(whichPage);
        final int sXY = !mVertical ? mUnboundedScrollX : mUnboundedScrollY;
        final int delta = newXY - sXY;
        snapToPage(whichPage, delta, duration);
    }

    protected void snapToPage(int whichPage, int delta, int duration) {
        mNextPage = whichPage;

        View focusedChild = getFocusedChild();
        if (focusedChild != null && whichPage != mCurrentPage &&
                focusedChild == getPageAt(mCurrentPage)) {
            focusedChild.clearFocus();
        }

        pageBeginMoving();
        awakenScrollBars(duration);
        if (duration == 0) {
            duration = Math.abs(delta);
        }

        if (!mScroller.isFinished()) mScroller.abortAnimation();
        if (!mVertical) {
            mScroller.startScroll(mUnboundedScrollX, 0, delta, 0, duration);
        } else {
            mScroller.startScroll(0, mUnboundedScrollY, 0, delta, duration);
        }

        // Load associated pages immediately if someone else is handling the scroll, otherwise defer
        // loading associated pages until the scroll settles
        if (mDeferScrollUpdate) {
            loadAssociatedPages(mNextPage);
        } else {
            mDeferLoadAssociatedPagesUntilScrollCompletes = true;
        }
        notifyPageSwitchListener();
        invalidate();
    }

    public void scrollLeft() {
        if (mScroller.isFinished()) {
            if (mCurrentPage > 0) snapToPage(mCurrentPage - 1);
        } else {
            if (mNextPage > 0) snapToPage(mNextPage - 1);
        }
    }

    public void scrollRight() {
        if (mScroller.isFinished()) {
            if (mCurrentPage < getChildCount() -1) snapToPage(mCurrentPage + 1);
        } else {
            if (mNextPage < getChildCount() -1) snapToPage(mNextPage + 1);
        }
    }

    public int getPageForView(View v) {
        int result = -1;
        if (v != null) {
            ViewParent vp = v.getParent();
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                if (vp == getPageAt(i)) {
                    return i;
                }
            }
        }
        return result;
    }

    /**
     * @return True is long presses are still allowed for the current touch
     */
    public boolean allowLongPress() {
        return mAllowLongPress;
    }

    /**
     * Set true to allow long-press events to be triggered, usually checked by
     * {@link Launcher} to accept or block dpad-initiated long-presses.
     */
    public void setAllowLongPress(boolean allowLongPress) {
        mAllowLongPress = allowLongPress;
    }

    public static class SavedState extends BaseSavedState {
        int currentPage = -1;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            currentPage = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(currentPage);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    protected void loadAssociatedPages(int page) {
        loadAssociatedPages(page, false);
    }
    protected void loadAssociatedPages(int page, boolean immediateAndOnly) {
        if (mContentIsRefreshable) {
            final int count = getChildCount();
            if (page < count) {
                int lowerPageBound = getAssociatedLowerPageBound(page);
                int upperPageBound = getAssociatedUpperPageBound(page);
                if (DEBUG) Log.d(TAG, "loadAssociatedPages: " + lowerPageBound + "/"
                        + upperPageBound);
                // First, clear any pages that should no longer be loaded
                for (int i = 0; i < count; ++i) {
                    Page layout = (Page) getPageAt(i);
                    if ((i < lowerPageBound) || (i > upperPageBound)) {
                        if (layout.getPageChildCount() > 0) {
                            layout.removeAllViewsOnPage();
                        }
                        mDirtyPageContent.set(i, true);
                    }
                }
                // Next, load any new pages
                for (int i = 0; i < count; ++i) {
                    if ((i != page) && immediateAndOnly) {
                        continue;
                    }
                    if (lowerPageBound <= i && i <= upperPageBound) {
                        if (mDirtyPageContent.get(i)) {
                            syncPageItems(i, (i == page) && immediateAndOnly);
                            mDirtyPageContent.set(i, false);
                        }
                    }
                }
            }
        }
    }

    protected int getAssociatedLowerPageBound(int page) {
        return Math.max(0, page - 1);
    }
    protected int getAssociatedUpperPageBound(int page) {
        final int count = getChildCount();
        return Math.min(page + 1, count - 1);
    }

    /**
     * This method is called ONLY to synchronize the number of pages that the paged view has.
     * To actually fill the pages with information, implement syncPageItems() below.  It is
     * guaranteed that syncPageItems() will be called for a particular page before it is shown,
     * and therefore, individual page items do not need to be updated in this method.
     */
    public abstract void syncPages();

    /**
     * This method is called to synchronize the items that are on a particular page.  If views on
     * the page can be reused, then they should be updated within this method.
     */
    public abstract void syncPageItems(int page, boolean immediate);

    protected void invalidatePageData() {
        invalidatePageData(-1, false);
    }
    protected void invalidatePageData(int currentPage) {
        invalidatePageData(currentPage, false);
    }
    protected void invalidatePageData(int currentPage, boolean immediateAndOnly) {
        if (!mIsDataReady) {
            return;
        }

        if (mContentIsRefreshable) {
            // Force all scrolling-related behavior to end
            mScroller.forceFinished(true);
            mNextPage = INVALID_PAGE;

            // Update all the pages
            syncPages();

            // We must force a measure after we've loaded the pages to update the content width and
            // to determine the full scroll width
            measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));

            // Set a new page as the current page if necessary
            if (currentPage > -1) {
                setCurrentPage(Math.min(getPageCount() - 1, currentPage));
            }

            // Mark each of the pages as dirty
            final int count = getChildCount();
            mDirtyPageContent.clear();
            for (int i = 0; i < count; ++i) {
                mDirtyPageContent.add(true);
            }

            // Load any pages that are necessary for the current window of views
            loadAssociatedPages(mCurrentPage, immediateAndOnly);
            requestLayout();
        }
    }

    protected View getScrollingIndicator() {
        // We use mHasScrollIndicator to prevent future lookups if there is no sibling indicator
        // found
        if (mHasScrollIndicator && mScrollIndicator == null) {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                if (!mVertical) {
                    mScrollIndicator = (View) parent.findViewById(R.id.paged_view_indicator_horizontal);
                } else {
                    mScrollIndicator = (View) parent.findViewById(R.id.paged_view_indicator_vertical);
                }
                mHasScrollIndicator = mScrollIndicator != null;
                if (mHasScrollIndicator) {
                    mScrollIndicator.setVisibility(View.VISIBLE);
                }
            }
        }
        return mScrollIndicator;
    }

    protected boolean isScrollingIndicatorEnabled() {
        return true;
    }

    Runnable hideScrollingIndicatorRunnable = new Runnable() {
        @Override
        public void run() {
            hideScrollingIndicator(false);
        }
    };
    protected void flashScrollingIndicator(boolean animated) {
        removeCallbacks(hideScrollingIndicatorRunnable);
        showScrollingIndicator(!animated);
        postDelayed(hideScrollingIndicatorRunnable, sScrollIndicatorFlashDuration);
    }

    protected void showScrollingIndicator(boolean immediately) {
        showScrollingIndicator(immediately, sScrollIndicatorFadeInDuration);
    }

    protected void showScrollingIndicator(boolean immediately, int duration) {
        mShouldShowScrollIndicator = true;
        mShouldShowScrollIndicatorImmediately = true;
        if (getChildCount() <= 1) return;
        if (!isScrollingIndicatorEnabled()) return;

        mShouldShowScrollIndicator = false;
        getScrollingIndicator();
        if (mScrollIndicator != null) {
            // Fade the indicator in
            updateScrollingIndicatorPosition();
            mScrollIndicator.setVisibility(View.VISIBLE);
            cancelScrollingIndicatorAnimations();
            if (immediately || mScrollingPaused) {
                mScrollIndicator.setAlpha(1f);
            } else {
                mScrollIndicatorAnimator = LauncherAnimUtils.ofFloat(mScrollIndicator, "alpha", 1f);
                mScrollIndicatorAnimator.setDuration(duration);
                mScrollIndicatorAnimator.start();
            }
        }
    }

    protected void cancelScrollingIndicatorAnimations() {
        if (mScrollIndicatorAnimator != null) {
            mScrollIndicatorAnimator.cancel();
        }
    }

    protected void hideScrollingIndicator(boolean immediately) {
        hideScrollingIndicator(immediately, sScrollIndicatorFadeOutDuration);
    }

    protected void hideScrollingIndicator(boolean immediately, int duration) {
        if (getChildCount() <= 1) return;
        if (!isScrollingIndicatorEnabled()) return;

        getScrollingIndicator();
        if (mScrollIndicator != null) {
            // Fade the indicator out
            updateScrollingIndicatorPosition();
            cancelScrollingIndicatorAnimations();
            if (immediately || mScrollingPaused) {
                mScrollIndicator.setVisibility(View.INVISIBLE);
                mScrollIndicator.setAlpha(0f);
            } else {
                mScrollIndicatorAnimator = LauncherAnimUtils.ofFloat(mScrollIndicator, "alpha", 0f);
                mScrollIndicatorAnimator.setDuration(duration);
                mScrollIndicatorAnimator.addListener(new AnimatorListenerAdapter() {
                    private boolean cancelled = false;
                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        cancelled = true;
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!cancelled) {
                            mScrollIndicator.setVisibility(View.INVISIBLE);
                        }
                    }
                });
                mScrollIndicatorAnimator.start();
            }
        }
    }

    protected void enableScrollingIndicator() {
        mHasScrollIndicator = true;
        getScrollingIndicator();
        if (mScrollIndicator != null) {
            mScrollIndicator.setVisibility(View.VISIBLE);
        }
    }

    protected void disableScrollingIndicator() {
        if (mScrollIndicator != null) {
            mScrollIndicator.setVisibility(View.GONE);
        }
        mHasScrollIndicator = false;
        mScrollIndicator = null;
    }

    /**
     * To be overridden by subclasses to determine whether the scroll indicator should stretch to
     * fill its space on the track or not.
     */
    protected boolean hasElasticScrollIndicator() {
        return true;
    }

    private void updateScrollingIndicator() {
        if (getChildCount() <= 1) return;
        if (!isScrollingIndicatorEnabled()) return;

        getScrollingIndicator();
        if (mScrollIndicator != null) {
            updateScrollingIndicatorPosition();
        }
        if (mShouldShowScrollIndicator) {
            showScrollingIndicator(mShouldShowScrollIndicatorImmediately);
        }
    }

    private void updateScrollingIndicatorPosition() {
        if (!isScrollingIndicatorEnabled()) return;
        if (mScrollIndicator == null) return;
        int numPages = getChildCount();
        int pageSize = !mVertical ? getMeasuredWidth() : getMeasuredHeight();
        int lastChildIndex = Math.max(0, getChildCount() - 1);
        int maxScroll = getChildOffset(lastChildIndex) - getRelativeChildOffset(lastChildIndex);
        if (!mVertical) {
            int trackWidth = pageSize - mScrollIndicatorPaddingLeft - mScrollIndicatorPaddingRight;
            int indicatorWidth = mScrollIndicator.getMeasuredWidth() -
                    mScrollIndicator.getPaddingLeft() - mScrollIndicator.getPaddingRight();

            float offset = Math.max(0f, Math.min(1f, (float) getScrollX() / maxScroll));
            int indicatorSpace = trackWidth / numPages;
            int indicatorPos = (int) (offset * (trackWidth - indicatorSpace)) + mScrollIndicatorPaddingLeft;
            if (hasElasticScrollIndicator()) {
                if (mScrollIndicator.getMeasuredWidth() != indicatorSpace) {
                    mScrollIndicator.getLayoutParams().width = indicatorSpace;
                    mScrollIndicator.requestLayout();
                }
            } else {
                int indicatorCenterOffset = indicatorSpace / 2 - indicatorWidth / 2;
                indicatorPos += indicatorCenterOffset;
            }
            mScrollIndicator.setTranslationX(indicatorPos);
        } else {
            int trackHeight = pageSize - mScrollIndicatorPaddingTop - mScrollIndicatorPaddingBottom;
            int indicatorHeight = mScrollIndicator.getMeasuredHeight() -
                    mScrollIndicator.getPaddingTop() - mScrollIndicator.getPaddingBottom();

            float offset = Math.max(0f, Math.min(1f, (float) getScrollY() / maxScroll));
            int indicatorSpace = trackHeight / numPages;
            int indicatorPos = (int) (offset * (trackHeight - indicatorSpace)) + mScrollIndicatorPaddingTop;
            if (hasElasticScrollIndicator()) {
                if (mScrollIndicator.getMeasuredHeight() != indicatorSpace) {
                    mScrollIndicator.getLayoutParams().height = indicatorSpace;
                    mScrollIndicator.requestLayout();
                }
            } else {
                int indicatorCenterOffset = indicatorSpace / 2 - indicatorHeight / 2;
                indicatorPos += indicatorCenterOffset;
            }
            mScrollIndicator.setTranslationY(indicatorPos);
        }
    }

    public void showScrollIndicatorTrack() {
    }

    public void hideScrollIndicatorTrack() {
    }

    /* Accessibility */
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(getPageCount() > 1);
        if (getCurrentPage() < getPageCount() - 1) {
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }
        if (getCurrentPage() > 0) {
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setScrollable(true);
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            event.setFromIndex(mCurrentPage);
            event.setToIndex(mCurrentPage);
            event.setItemCount(getChildCount());
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
                if (getCurrentPage() < getPageCount() - 1) {
                    scrollRight();
                    return true;
                }
            } break;
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                if (getCurrentPage() > 0) {
                    scrollLeft();
                    return true;
                }
            } break;
        }
        return false;
    }

    protected String getCurrentPageDescription() {
        return String.format(getContext().getString(R.string.default_scroll_format),
                 getNextPage() + 1, getChildCount());
    }

    @Override
    public boolean onHoverEvent(android.view.MotionEvent event) {
        return true;
    }
}
