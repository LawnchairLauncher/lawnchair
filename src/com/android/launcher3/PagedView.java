/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;

import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;

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

    public static final int PAGE_SNAP_ANIMATION_DURATION = 750;
    protected static final int SLOW_PAGE_SNAP_ANIMATION_DURATION = 950;

    // OverScroll constants
    private final static int OVERSCROLL_PAGE_SNAP_ANIMATION_DURATION = 270;

    private static final float RETURN_TO_ORIGINAL_PAGE_THRESHOLD = 0.33f;
    // The page is moved more than halfway, automatically move to the next page on touch up.
    private static final float SIGNIFICANT_MOVE_THRESHOLD = 0.4f;

    private static final float MAX_SCROLL_PROGRESS = 1.0f;

    // The following constants need to be scaled based on density. The scaled versions will be
    // assigned to the corresponding member variables below.
    private static final int FLING_THRESHOLD_VELOCITY = 500;
    private static final int MIN_SNAP_VELOCITY = 1500;
    private static final int MIN_FLING_VELOCITY = 250;

    public static final int INVALID_RESTORE_PAGE = -1001;

    private boolean mFreeScroll = false;
    private int mFreeScrollMinScrollX = -1;
    private int mFreeScrollMaxScrollX = -1;

    protected int mFlingThresholdVelocity;
    protected int mMinFlingVelocity;
    protected int mMinSnapVelocity;

    protected boolean mFirstLayout = true;
    private int mNormalChildHeight;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mCurrentPage;
    private int mChildCountOnLastLayout;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mNextPage = INVALID_PAGE;
    protected int mMaxScrollX;
    protected LauncherScroller mScroller;
    private Interpolator mDefaultInterpolator;
    private VelocityTracker mVelocityTracker;
    @Thunk int mPageSpacing = 0;

    private float mParentDownMotionX;
    private float mParentDownMotionY;
    private float mDownMotionX;
    private float mDownMotionY;
    private float mDownScrollX;
    private float mDragViewBaselineLeft;
    private float mLastMotionX;
    private float mLastMotionXRemainder;
    private float mLastMotionY;
    private float mTotalMotionX;

    private boolean mCancelTap;

    private int[] mPageScrolls;

    protected final static int TOUCH_STATE_REST = 0;
    protected final static int TOUCH_STATE_SCROLLING = 1;
    protected final static int TOUCH_STATE_PREV_PAGE = 2;
    protected final static int TOUCH_STATE_NEXT_PAGE = 3;
    protected final static int TOUCH_STATE_REORDERING = 4;

    protected int mTouchState = TOUCH_STATE_REST;

    protected OnLongClickListener mLongClickListener;

    protected int mTouchSlop;
    private int mMaximumVelocity;
    protected boolean mAllowOverScroll = true;
    protected int[] mTempVisiblePagesRange = new int[2];

    protected static final int INVALID_POINTER = -1;

    protected int mActivePointerId = INVALID_POINTER;

    protected boolean mIsPageInTransition = false;

    protected boolean mWasInOverscroll = false;

    // mOverScrollX is equal to getScrollX() when we're within the normal scroll range. Otherwise
    // it is equal to the scaled overscroll position. We use a separate value so as to prevent
    // the screens from continuing to translate beyond the normal bounds.
    protected int mOverScrollX;

    protected int mUnboundedScrollX;

    // Page Indicator
    @Thunk int mPageIndicatorViewId;
    protected PageIndicator mPageIndicator;
    // The viewport whether the pages are to be contained (the actual view may be larger than the
    // viewport)
    @ViewDebug.ExportedProperty(category = "launcher")
    private Rect mViewport = new Rect();

    // Reordering
    // We use the min scale to determine how much to expand the actually PagedView measured
    // dimensions such that when we are zoomed out, the view is not clipped
    private static int REORDERING_DROP_REPOSITION_DURATION = 200;
    @Thunk static int REORDERING_REORDER_REPOSITION_DURATION = 300;
    private static int REORDERING_SIDE_PAGE_HOVER_TIMEOUT = 80;

    private float mMinScale = 1f;
    private boolean mUseMinScale = false;
    @Thunk View mDragView;
    private Runnable mSidePageHoverRunnable;
    @Thunk int mSidePageHoverIndex = -1;
    // This variable's scope is only for the duration of startReordering() and endReordering()
    private boolean mReorderingStarted = false;
    // This variable's scope is for the duration of startReordering() and after the zoomIn()
    // animation after endReordering()
    private boolean mIsReordering;
    // The runnable that settles the page after snapToPage and animateDragViewToOriginalPosition
    private static final int NUM_ANIMATIONS_RUNNING_BEFORE_ZOOM_OUT = 2;
    private int mPostReorderingPreZoomInRemainingAnimationCount;
    private Runnable mPostReorderingPreZoomInRunnable;

    // Convenience/caching
    private static final Matrix sTmpInvMatrix = new Matrix();
    private static final float[] sTmpPoint = new float[2];
    private static final Rect sTmpRect = new Rect();

    protected final Rect mInsets = new Rect();
    protected final boolean mIsRtl;

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
        mPageIndicatorViewId = a.getResourceId(R.styleable.PagedView_pageIndicator, -1);
        a.recycle();

        setHapticFeedbackEnabled(false);
        mIsRtl = Utilities.isRtl(getResources());
        init();
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void init() {
        mScroller = new LauncherScroller(getContext());
        setDefaultInterpolator(new ScrollInterpolator());
        mCurrentPage = 0;

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledPagingTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        float density = getResources().getDisplayMetrics().density;
        mFlingThresholdVelocity = (int) (FLING_THRESHOLD_VELOCITY * density);
        mMinFlingVelocity = (int) (MIN_FLING_VELOCITY * density);
        mMinSnapVelocity = (int) (MIN_SNAP_VELOCITY * density);
        setOnHierarchyChangeListener(this);
        setWillNotDraw(false);
    }

    protected void setDefaultInterpolator(Interpolator interpolator) {
        mDefaultInterpolator = interpolator;
        mScroller.setInterpolator(mDefaultInterpolator);
    }

    public void initParentViews(View parent) {
        if (mPageIndicatorViewId > -1) {
            mPageIndicator = (PageIndicator) parent.findViewById(mPageIndicatorViewId);
            mPageIndicator.setMarkersCount(getChildCount());
            mPageIndicator.setContentDescription(getPageIndicatorDescription());
        }
    }

    // Convenience methods to map points from self to parent and vice versa
    private float[] mapPointFromViewToParent(View v, float x, float y) {
        sTmpPoint[0] = x;
        sTmpPoint[1] = y;
        v.getMatrix().mapPoints(sTmpPoint);
        sTmpPoint[0] += v.getLeft();
        sTmpPoint[1] += v.getTop();
        return sTmpPoint;
    }
    private float[] mapPointFromParentToView(View v, float x, float y) {
        sTmpPoint[0] = x - v.getLeft();
        sTmpPoint[1] = y - v.getTop();
        v.getMatrix().invert(sTmpInvMatrix);
        sTmpInvMatrix.mapPoints(sTmpPoint);
        return sTmpPoint;
    }

    private void updateDragViewTranslationDuringDrag() {
        if (mDragView != null) {
            float x = (mLastMotionX - mDownMotionX) + (getScrollX() - mDownScrollX) +
                    (mDragViewBaselineLeft - mDragView.getLeft());
            float y = mLastMotionY - mDownMotionY;
            mDragView.setTranslationX(x);
            mDragView.setTranslationY(y);

            if (DEBUG) Log.d(TAG, "PagedView.updateDragViewTranslationDuringDrag(): "
                    + x + ", " + y);
        }
    }

    public void setMinScale(float f) {
        mMinScale = f;
        mUseMinScale = true;
        requestLayout();
    }

    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        if (isReordering(true)) {
            float[] p = mapPointFromParentToView(this, mParentDownMotionX, mParentDownMotionY);
            mLastMotionX = p[0];
            mLastMotionY = p[1];
            updateDragViewTranslationDuringDrag();
        }
    }

    // Convenience methods to get the actual width/height of the PagedView (since it is measured
    // to be larger to account for the minimum possible scale)
    int getViewportWidth() {
        return mViewport.width();
    }
    public int getViewportHeight() {
        return mViewport.height();
    }

    // Convenience methods to get the offset ASSUMING that we are centering the pages in the
    // PagedView both horizontally and vertically
    int getViewportOffsetX() {
        return (getMeasuredWidth() - getViewportWidth()) / 2;
    }

    int getViewportOffsetY() {
        return (getMeasuredHeight() - getViewportHeight()) / 2;
    }

    public PageIndicator getPageIndicator() {
        return mPageIndicator;
    }

    /**
     * Returns the index of the currently displayed page. When in free scroll mode, this is the page
     * that the user was on before entering free scroll mode (e.g. the home screen page they
     * long-pressed on to enter the overview). Try using {@link #getPageNearestToCenterOfScreen()}
     * to get the page the user is currently scrolling over.
     */
    public int getCurrentPage() {
        return mCurrentPage;
    }

    /**
     * Returns the index of page to be shown immediately afterwards.
     */
    public int getNextPage() {
        return (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
    }

    public int getPageCount() {
        return getChildCount();
    }

    public View getPageAt(int index) {
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
        // If the current page is invalid, just reset the scroll position to zero
        int newX = 0;
        if (0 <= mCurrentPage && mCurrentPage < getPageCount()) {
            newX = getScrollForPage(mCurrentPage);
        }
        scrollTo(newX, 0);
        mScroller.setFinalX(newX);
        forceFinishScroller(true);
    }

    private void abortScrollerAnimation(boolean resetNextPage) {
        mScroller.abortAnimation();
        // We need to clean up the next page here to avoid computeScrollHelper from
        // updating current page on the pass.
        if (resetNextPage) {
            mNextPage = INVALID_PAGE;
        }
    }

    private void forceFinishScroller(boolean resetNextPage) {
        mScroller.forceFinished(true);
        // We need to clean up the next page here to avoid computeScrollHelper from
        // updating current page on the pass.
        if (resetNextPage) {
            mNextPage = INVALID_PAGE;
        }
    }

    private int validateNewPage(int newPage) {
        int validatedPage = newPage;
        // When in free scroll mode, we need to clamp to the free scroll page range.
        if (mFreeScroll) {
            getFreeScrollPageRange(mTempVisiblePagesRange);
            validatedPage = Math.max(mTempVisiblePagesRange[0],
                    Math.min(newPage, mTempVisiblePagesRange[1]));
        }
        // Ensure that it is clamped by the actual set of children in all cases
        validatedPage = Utilities.boundToRange(validatedPage, 0, getPageCount() - 1);
        return validatedPage;
    }

    /**
     * Sets the current page.
     */
    public void setCurrentPage(int currentPage) {
        if (!mScroller.isFinished()) {
            abortScrollerAnimation(true);
        }
        // don't introduce any checks like mCurrentPage == currentPage here-- if we change the
        // the default
        if (getChildCount() == 0) {
            return;
        }
        int prevPage = mCurrentPage;
        mCurrentPage = validateNewPage(currentPage);
        updateCurrentPageScroll();
        notifyPageSwitchListener(prevPage);
        invalidate();
    }

    /**
     * Should be called whenever the page changes. In the case of a scroll, we wait until the page
     * has settled.
     */
    protected void notifyPageSwitchListener(int prevPage) {
        updatePageIndicator();
    }

    private void updatePageIndicator() {
        // Update the page indicator (when we aren't reordering)
        if (mPageIndicator != null) {
            mPageIndicator.setContentDescription(getPageIndicatorDescription());
            if (!isReordering(false)) {
                mPageIndicator.setActiveMarker(getNextPage());
            }
        }
    }
    protected void pageBeginTransition() {
        if (!mIsPageInTransition) {
            mIsPageInTransition = true;
            onPageBeginTransition();
        }
    }

    protected void pageEndTransition() {
        if (mIsPageInTransition) {
            mIsPageInTransition = false;
            onPageEndTransition();
        }
    }

    protected boolean isPageInTransition() {
        return mIsPageInTransition;
    }

    /**
     * Called when the page starts moving as part of the scroll. Subclasses can override this
     * to provide custom behavior during animation.
     */
    protected void onPageBeginTransition() {
    }

    /**
     * Called when the page ends moving as part of the scroll. Subclasses can override this
     * to provide custom behavior during animation.
     */
    protected void onPageEndTransition() {
        mWasInOverscroll = false;
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
        super.setOnLongClickListener(l);
    }

    protected int getUnboundedScrollX() {
        return mUnboundedScrollX;
    }

    @Override
    public void scrollBy(int x, int y) {
        scrollTo(getUnboundedScrollX() + x, getScrollY() + y);
    }

    @Override
    public void scrollTo(int x, int y) {
        // In free scroll mode, we clamp the scrollX
        if (mFreeScroll) {
            // If the scroller is trying to move to a location beyond the maximum allowed
            // in the free scroll mode, we make sure to end the scroll operation.
            if (!mScroller.isFinished() &&
                    (x > mFreeScrollMaxScrollX || x < mFreeScrollMinScrollX)) {
                forceFinishScroller(false);
            }

            x = Math.min(x, mFreeScrollMaxScrollX);
            x = Math.max(x, mFreeScrollMinScrollX);
        }

        mUnboundedScrollX = x;

        boolean isXBeforeFirstPage = mIsRtl ? (x > mMaxScrollX) : (x < 0);
        boolean isXAfterLastPage = mIsRtl ? (x < 0) : (x > mMaxScrollX);
        if (isXBeforeFirstPage) {
            super.scrollTo(mIsRtl ? mMaxScrollX : 0, y);
            if (mAllowOverScroll) {
                mWasInOverscroll = true;
                if (mIsRtl) {
                    overScroll(x - mMaxScrollX);
                } else {
                    overScroll(x);
                }
            }
        } else if (isXAfterLastPage) {
            super.scrollTo(mIsRtl ? 0 : mMaxScrollX, y);
            if (mAllowOverScroll) {
                mWasInOverscroll = true;
                if (mIsRtl) {
                    overScroll(x);
                } else {
                    overScroll(x - mMaxScrollX);
                }
            }
        } else {
            if (mWasInOverscroll) {
                overScroll(0);
                mWasInOverscroll = false;
            }
            mOverScrollX = x;
            super.scrollTo(x, y);
        }

        // Update the last motion events when scrolling
        if (isReordering(true)) {
            float[] p = mapPointFromParentToView(this, mParentDownMotionX, mParentDownMotionY);
            mLastMotionX = p[0];
            mLastMotionY = p[1];
            updateDragViewTranslationDuringDrag();
        }
    }

    private void sendScrollAccessibilityEvent() {
        AccessibilityManager am =
                (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am.isEnabled()) {
            if (mCurrentPage != getNextPage()) {
                AccessibilityEvent ev =
                        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED);
                ev.setScrollable(true);
                ev.setScrollX(getScrollX());
                ev.setScrollY(getScrollY());
                ev.setMaxScrollX(mMaxScrollX);
                ev.setMaxScrollY(0);

                sendAccessibilityEventUnchecked(ev);
            }
        }
    }

    // we moved this functionality to a helper function so SmoothPagedView can reuse it
    protected boolean computeScrollHelper() {
        return computeScrollHelper(true);
    }

    protected boolean computeScrollHelper(boolean shouldInvalidate) {
        if (mScroller.computeScrollOffset()) {
            // Don't bother scrolling if the page does not need to be moved
            if (getUnboundedScrollX() != mScroller.getCurrX()
                    || getScrollY() != mScroller.getCurrY()
                    || mOverScrollX != mScroller.getCurrX()) {
                float scaleX = mFreeScroll ? getScaleX() : 1f;
                int scrollX = (int) (mScroller.getCurrX() * (1 / scaleX));
                scrollTo(scrollX, mScroller.getCurrY());
            }
            if (shouldInvalidate) {
                invalidate();
            }
            return true;
        } else if (mNextPage != INVALID_PAGE && shouldInvalidate) {
            sendScrollAccessibilityEvent();

            int prevPage = mCurrentPage;
            mCurrentPage = validateNewPage(mNextPage);
            mNextPage = INVALID_PAGE;
            notifyPageSwitchListener(prevPage);

            // We don't want to trigger a page end moving unless the page has settled
            // and the user has stopped scrolling
            if (mTouchState == TOUCH_STATE_REST) {
                pageEndTransition();
            }

            onPostReorderingAnimationCompleted();
            AccessibilityManager am = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am.isEnabled()) {
                // Notify the user when the page changes
                announceForAccessibility(getCurrentPageDescription());
            }
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        computeScrollHelper();
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public boolean isFullScreenPage = false;

        // If true, the start edge of the page snaps to the start edge of the viewport.
        public boolean matchStartEdge = false;

        /**
         * {@inheritDoc}
         */
        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    public void addFullScreenPage(View page) {
        LayoutParams lp = generateDefaultLayoutParams();
        lp.isFullScreenPage = true;
        super.addView(page, 0, lp);
    }

    public int getNormalChildHeight() {
        return mNormalChildHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // We measure the dimensions of the PagedView to be larger than the pages so that when we
        // zoom out (and scale down), the view is still contained in the parent
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        // NOTE: We multiply by 2f to account for the fact that depending on the offset of the
        // viewport, we can be at most one and a half screens offset once we scale down
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxSize = Math.max(dm.widthPixels + mInsets.left + mInsets.right,
                dm.heightPixels + mInsets.top + mInsets.bottom);

        int parentWidthSize = (int) (2f * maxSize);
        int parentHeightSize = (int) (2f * maxSize);
        int scaledWidthSize, scaledHeightSize;
        if (mUseMinScale) {
            scaledWidthSize = (int) (parentWidthSize / mMinScale);
            scaledHeightSize = (int) (parentHeightSize / mMinScale);
        } else {
            scaledWidthSize = widthSize;
            scaledHeightSize = heightSize;
        }
        mViewport.set(0, 0, widthSize, heightSize);

        if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Return early if we aren't given a proper dimension
        if (widthSize <= 0 || heightSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        /* Allow the height to be set as WRAP_CONTENT. This allows the particular case
         * of the All apps view on XLarge displays to not take up more space then it needs. Width
         * is still not allowed to be set as WRAP_CONTENT since many parts of the code expect
         * each page to have the same width.
         */
        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        final int horizontalPadding = getPaddingLeft() + getPaddingRight();

        int referenceChildWidth = 0;
        // The children are given the same width and height as the workspace
        // unless they were set to WRAP_CONTENT
        if (DEBUG) Log.d(TAG, "PagedView.onMeasure(): " + widthSize + ", " + heightSize);
        if (DEBUG) Log.d(TAG, "PagedView.scaledSize: " + scaledWidthSize + ", " + scaledHeightSize);
        if (DEBUG) Log.d(TAG, "PagedView.parentSize: " + parentWidthSize + ", " + parentHeightSize);
        if (DEBUG) Log.d(TAG, "PagedView.horizontalPadding: " + horizontalPadding);
        if (DEBUG) Log.d(TAG, "PagedView.verticalPadding: " + verticalPadding);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            // disallowing padding in paged view (just pass 0)
            final View child = getPageAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                int childWidthMode;
                int childHeightMode;
                int childWidth;
                int childHeight;

                if (!lp.isFullScreenPage) {
                    if (lp.width == LayoutParams.WRAP_CONTENT) {
                        childWidthMode = MeasureSpec.AT_MOST;
                    } else {
                        childWidthMode = MeasureSpec.EXACTLY;
                    }

                    if (lp.height == LayoutParams.WRAP_CONTENT) {
                        childHeightMode = MeasureSpec.AT_MOST;
                    } else {
                        childHeightMode = MeasureSpec.EXACTLY;
                    }

                    childWidth = getViewportWidth() - horizontalPadding
                            - mInsets.left - mInsets.right;
                    childHeight = getViewportHeight() - verticalPadding
                            - mInsets.top - mInsets.bottom;
                    mNormalChildHeight = childHeight;
                } else {
                    childWidthMode = MeasureSpec.EXACTLY;
                    childHeightMode = MeasureSpec.EXACTLY;

                    childWidth = getViewportWidth();
                    childHeight = getViewportHeight();
                }
                if (referenceChildWidth == 0) {
                    referenceChildWidth = childWidth;
                }

                final int childWidthMeasureSpec =
                        MeasureSpec.makeMeasureSpec(childWidth, childWidthMode);
                    final int childHeightMeasureSpec =
                        MeasureSpec.makeMeasureSpec(childHeight, childHeightMode);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
        setMeasuredDimension(scaledWidthSize, scaledHeightSize);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getChildCount() == 0) {
            return;
        }

        if (DEBUG) Log.d(TAG, "PagedView.onLayout()");
        final int childCount = getChildCount();

        int offsetX = getViewportOffsetX();
        int offsetY = getViewportOffsetY();

        // Update the viewport offsets
        mViewport.offset(offsetX, offsetY);

        final int startIndex = mIsRtl ? childCount - 1 : 0;
        final int endIndex = mIsRtl ? -1 : childCount;
        final int delta = mIsRtl ? -1 : 1;

        int verticalPadding = getPaddingTop() + getPaddingBottom();

        LayoutParams lp = (LayoutParams) getChildAt(startIndex).getLayoutParams();
        LayoutParams nextLp;

        int childLeft = offsetX + (lp.isFullScreenPage ? 0 : getPaddingLeft());
        if (mPageScrolls == null || childCount != mChildCountOnLastLayout) {
            mPageScrolls = new int[childCount];
        }

        for (int i = startIndex; i != endIndex; i += delta) {
            final View child = getPageAt(i);
            if (child.getVisibility() != View.GONE) {
                lp = (LayoutParams) child.getLayoutParams();
                int childTop;
                if (lp.isFullScreenPage) {
                    childTop = offsetY;
                } else {
                    childTop = offsetY + getPaddingTop() + mInsets.top;
                    childTop += (getViewportHeight() - mInsets.top - mInsets.bottom - verticalPadding - child.getMeasuredHeight()) / 2;
                }

                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();

                if (DEBUG) Log.d(TAG, "\tlayout-child" + i + ": " + childLeft + ", " + childTop);
                child.layout(childLeft, childTop,
                        childLeft + child.getMeasuredWidth(), childTop + childHeight);

                int scrollOffsetLeft = lp.isFullScreenPage ? 0 : getPaddingLeft();
                mPageScrolls[i] = childLeft - scrollOffsetLeft - offsetX;

                int pageGap = mPageSpacing;
                int next = i + delta;
                if (next != endIndex) {
                    nextLp = (LayoutParams) getPageAt(next).getLayoutParams();
                } else {
                    nextLp = null;
                }

                // Prevent full screen pages from showing in the viewport
                // when they are not the current page.
                if (lp.isFullScreenPage) {
                    pageGap = getPaddingLeft();
                } else if (nextLp != null && nextLp.isFullScreenPage) {
                    pageGap = getPaddingRight();
                }

                childLeft += childWidth + pageGap + getChildGap();
            }
        }

        final LayoutTransition transition = getLayoutTransition();
        // If the transition is running defer updating max scroll, as some empty pages could
        // still be present, and a max scroll change could cause sudden jumps in scroll.
        if (transition != null && transition.isRunning()) {
            transition.addTransitionListener(new LayoutTransition.TransitionListener() {

                @Override
                public void startTransition(LayoutTransition transition, ViewGroup container,
                        View view, int transitionType) { }

                @Override
                public void endTransition(LayoutTransition transition, ViewGroup container,
                        View view, int transitionType) {
                    // Wait until all transitions are complete.
                    if (!transition.isRunning()) {
                        transition.removeTransitionListener(this);
                        updateMaxScrollX();
                    }
                }
            });
        } else {
            updateMaxScrollX();
        }

        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < childCount) {
            updateCurrentPageScroll();
            mFirstLayout = false;
        }

        if (mScroller.isFinished() && mChildCountOnLastLayout != childCount) {
            setCurrentPage(getNextPage());
        }
        mChildCountOnLastLayout = childCount;

        if (isReordering(true)) {
            updateDragViewTranslationDuringDrag();
        }
    }

    protected int getChildGap() {
        return 0;
    }

    @Thunk void updateMaxScrollX() {
        mMaxScrollX = computeMaxScrollX();
    }

    protected int computeMaxScrollX() {
        int childCount = getChildCount();
        if (childCount > 0) {
            final int index = mIsRtl ? 0 : childCount - 1;
            return getScrollForPage(index);
        } else {
            return 0;
        }
    }

    public void setPageSpacing(int pageSpacing) {
        mPageSpacing = pageSpacing;
        requestLayout();
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        // Update the page indicator, we don't update the page indicator as we
        // add/remove pages
        if (mPageIndicator != null && !isReordering(false)) {
            mPageIndicator.addMarker();
        }

        // This ensures that when children are added, they get the correct transforms / alphas
        // in accordance with any scroll effects.
        updateFreescrollBounds();
        invalidate();
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
        updateFreescrollBounds();
        mCurrentPage = validateNewPage(mCurrentPage);
        invalidate();
    }

    private void removeMarkerForView() {
        // Update the page indicator, we don't update the page indicator as we
        // add/remove pages
        if (mPageIndicator != null && !isReordering(false)) {
            mPageIndicator.removeMarker();
        }
    }

    @Override
    public void removeView(View v) {
        // XXX: We should find a better way to hook into this before the view
        // gets removed form its parent...
        removeMarkerForView();
        super.removeView(v);
    }
    @Override
    public void removeViewInLayout(View v) {
        // XXX: We should find a better way to hook into this before the view
        // gets removed form its parent...
        removeMarkerForView();
        super.removeViewInLayout(v);
    }
    @Override
    public void removeViewAt(int index) {
        // XXX: We should find a better way to hook into this before the view
        // gets removed form its parent...
        removeMarkerForView();
        super.removeViewAt(index);
    }
    @Override
    public void removeAllViewsInLayout() {
        // Update the page indicator, we don't update the page indicator as we
        // add/remove pages
        if (mPageIndicator != null) {
            mPageIndicator.setMarkersCount(0);
        }

        super.removeAllViewsInLayout();
    }

    protected int getChildOffset(int index) {
        if (index < 0 || index > getChildCount() - 1) return 0;

        int offset = getPageAt(index).getLeft() - getViewportOffsetX();

        return offset;
    }

    protected void getFreeScrollPageRange(int[] range) {
        range[0] = 0;
        range[1] = Math.max(0, getChildCount() - 1);
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
        if (super.dispatchUnhandledMove(focused, direction)) {
            return true;
        }

        if (mIsRtl) {
            if (direction == View.FOCUS_LEFT) {
                direction = View.FOCUS_RIGHT;
            } else if (direction == View.FOCUS_RIGHT) {
                direction = View.FOCUS_LEFT;
            }
        }
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
        return false;
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) {
            return;
        }

        // XXX-RTL: This will be fixed in a future CL
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
        if (mIsRtl) {
            return (x > (getViewportOffsetX() + getViewportWidth() -
                    getPaddingRight() - mPageSpacing));
        }
        return (x < getViewportOffsetX() + getPaddingLeft() + mPageSpacing);
    }

    /**
     * Return true if a tap at (x, y) should trigger a flip to the next page.
     */
    protected boolean hitsNextPage(float x, float y) {
        if (mIsRtl) {
            return (x < getViewportOffsetX() + getPaddingLeft() + mPageSpacing);
        }
        return  (x > (getViewportOffsetX() + getViewportWidth() -
                getPaddingRight() - mPageSpacing));
    }

    /** Returns whether x and y originated within the buffered viewport */
    private boolean isTouchPointInViewportWithBuffer(int x, int y) {
        sTmpRect.set(mViewport.left - mViewport.width() / 2, mViewport.top,
                mViewport.right + mViewport.width() / 2, mViewport.bottom);
        return sTmpRect.contains(x, y);
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
                }
                // if mActivePointerId is INVALID_POINTER, then we must have missed an ACTION_DOWN
                // event. in that case, treat the first occurence of a move event as a ACTION_DOWN
                // i.e. fall through to the next case (don't break)
                // (We sometimes miss ACTION_DOWN events in Workspace because it ignores all events
                // while it's small- this was causing a crash before we checked for INVALID_POINTER)
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                // Remember location of down touch
                mDownMotionX = x;
                mDownMotionY = y;
                mDownScrollX = getScrollX();
                mLastMotionX = x;
                mLastMotionY = y;
                float[] p = mapPointFromViewToParent(this, x, y);
                mParentDownMotionX = p[0];
                mParentDownMotionY = p[1];
                mLastMotionXRemainder = 0;
                mTotalMotionX = 0;
                mActivePointerId = ev.getPointerId(0);

                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                final int xDist = Math.abs(mScroller.getFinalX() - mScroller.getCurrX());
                final boolean finishedScrolling = (mScroller.isFinished() || xDist < mTouchSlop / 3);

                if (finishedScrolling) {
                    mTouchState = TOUCH_STATE_REST;
                    if (!mScroller.isFinished() && !mFreeScroll) {
                        setCurrentPage(getNextPage());
                        pageEndTransition();
                    }
                } else {
                    if (isTouchPointInViewportWithBuffer((int) mDownMotionX, (int) mDownMotionY)) {
                        mTouchState = TOUCH_STATE_SCROLLING;
                    } else {
                        mTouchState = TOUCH_STATE_REST;
                    }
                }

                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetTouchState();
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
        // Disallow scrolling if we don't have a valid pointer index
        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex == -1) return;

        // Disallow scrolling if we started the gesture from outside the viewport
        final float x = ev.getX(pointerIndex);
        final float y = ev.getY(pointerIndex);
        if (!isTouchPointInViewportWithBuffer((int) x, (int) y)) return;

        final int xDiff = (int) Math.abs(x - mLastMotionX);

        final int touchSlop = Math.round(touchSlopScale * mTouchSlop);
        boolean xMoved = xDiff > touchSlop;

        if (xMoved) {
            // Scroll if the user moved far enough along the X axis
            mTouchState = TOUCH_STATE_SCROLLING;
            mTotalMotionX += Math.abs(mLastMotionX - x);
            mLastMotionX = x;
            mLastMotionXRemainder = 0;
            onScrollInteractionBegin();
            pageBeginTransition();
            // Stop listening for things like pinches.
            requestDisallowInterceptTouchEvent(true);
        }
    }

    protected void cancelCurrentPageLongPress() {
        // Try canceling the long press. It could also have been scheduled
        // by a distant descendant, so use the mAllowLongPress flag to block
        // everything
        final View currentPage = getPageAt(mCurrentPage);
        if (currentPage != null) {
            currentPage.cancelLongPress();
        }
    }

    protected float getScrollProgress(int screenCenter, View v, int page) {
        final int halfScreenSize = getViewportWidth() / 2;

        int delta = screenCenter - (getScrollForPage(page) + halfScreenSize);
        int count = getChildCount();

        final int totalDistance;

        int adjacentPage = page + 1;
        if ((delta < 0 && !mIsRtl) || (delta > 0 && mIsRtl)) {
            adjacentPage = page - 1;
        }

        if (adjacentPage < 0 || adjacentPage > count - 1) {
            totalDistance = v.getMeasuredWidth() + mPageSpacing;
        } else {
            totalDistance = Math.abs(getScrollForPage(adjacentPage) - getScrollForPage(page));
        }

        float scrollProgress = delta / (totalDistance * 1.0f);
        scrollProgress = Math.min(scrollProgress, MAX_SCROLL_PROGRESS);
        scrollProgress = Math.max(scrollProgress, - MAX_SCROLL_PROGRESS);
        return scrollProgress;
    }

    public int getScrollForPage(int index) {
        if (mPageScrolls == null || index >= mPageScrolls.length || index < 0) {
            return 0;
        } else {
            return mPageScrolls[index];
        }
    }

    // While layout transitions are occurring, a child's position may stray from its baseline
    // position. This method returns the magnitude of this stray at any given time.
    public int getLayoutTransitionOffsetForPage(int index) {
        if (mPageScrolls == null || index >= mPageScrolls.length || index < 0) {
            return 0;
        } else {
            View child = getChildAt(index);

            int scrollOffset = 0;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (!lp.isFullScreenPage) {
                scrollOffset = mIsRtl ? getPaddingRight() : getPaddingLeft();
            }

            int baselineX = mPageScrolls[index] + scrollOffset + getViewportOffsetX();
            return (int) (child.getX() - baselineX);
        }
    }

    protected void dampedOverScroll(float amount) {
        if (Float.compare(amount, 0f) == 0) return;

        int overScrollAmount = OverScroll.dampedScroll(amount, getViewportWidth());
        if (amount < 0) {
            mOverScrollX = overScrollAmount;
            super.scrollTo(mOverScrollX, getScrollY());
        } else {
            mOverScrollX = mMaxScrollX + overScrollAmount;
            super.scrollTo(mOverScrollX, getScrollY());
        }
        invalidate();
    }

    protected void overScroll(float amount) {
        dampedOverScroll(amount);
    }

    /**
     * return true if freescroll has been enabled, false otherwise
     */
    public boolean enableFreeScroll() {
        setEnableFreeScroll(true);
        return true;
    }

    public void disableFreeScroll() {
        setEnableFreeScroll(false);
    }

    void updateFreescrollBounds() {
        getFreeScrollPageRange(mTempVisiblePagesRange);
        if (mIsRtl) {
            mFreeScrollMinScrollX = getScrollForPage(mTempVisiblePagesRange[1]);
            mFreeScrollMaxScrollX = getScrollForPage(mTempVisiblePagesRange[0]);
        } else {
            mFreeScrollMinScrollX = getScrollForPage(mTempVisiblePagesRange[0]);
            mFreeScrollMaxScrollX = getScrollForPage(mTempVisiblePagesRange[1]);
        }
    }

    private void setEnableFreeScroll(boolean freeScroll) {
        boolean wasFreeScroll = mFreeScroll;
        mFreeScroll = freeScroll;

        if (mFreeScroll) {
            updateFreescrollBounds();
            getFreeScrollPageRange(mTempVisiblePagesRange);
            if (getCurrentPage() < mTempVisiblePagesRange[0]) {
                setCurrentPage(mTempVisiblePagesRange[0]);
            } else if (getCurrentPage() > mTempVisiblePagesRange[1]) {
                setCurrentPage(mTempVisiblePagesRange[1]);
            }
        } else if (wasFreeScroll) {
            snapToPage(getNextPage());
        }

        setEnableOverscroll(!freeScroll);
    }

    protected void setEnableOverscroll(boolean enable) {
        mAllowOverScroll = enable;
    }

    private int getNearestHoverOverPageIndex() {
        if (mDragView != null) {
            int dragX = (int) (mDragView.getLeft() + (mDragView.getMeasuredWidth() / 2)
                    + mDragView.getTranslationX());
            getFreeScrollPageRange(mTempVisiblePagesRange);
            int minDistance = Integer.MAX_VALUE;
            int minIndex = indexOfChild(mDragView);
            for (int i = mTempVisiblePagesRange[0]; i <= mTempVisiblePagesRange[1]; i++) {
                View page = getPageAt(i);
                int pageX = (int) (page.getLeft() + page.getMeasuredWidth() / 2);
                int d = Math.abs(dragX - pageX);
                if (d < minDistance) {
                    minIndex = i;
                    minDistance = d;
                }
            }
            return minIndex;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);

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
                abortScrollerAnimation(false);
            }

            // Remember where the motion event started
            mDownMotionX = mLastMotionX = ev.getX();
            mDownMotionY = mLastMotionY = ev.getY();
            mDownScrollX = getScrollX();
            float[] p = mapPointFromViewToParent(this, mLastMotionX, mLastMotionY);
            mParentDownMotionX = p[0];
            mParentDownMotionY = p[1];
            mLastMotionXRemainder = 0;
            mTotalMotionX = 0;
            mActivePointerId = ev.getPointerId(0);

            if (mTouchState == TOUCH_STATE_SCROLLING) {
                onScrollInteractionBegin();
                pageBeginTransition();
            }
            break;

        case MotionEvent.ACTION_MOVE:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                // Scroll to follow the motion event
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);

                if (pointerIndex == -1) return true;

                final float x = ev.getX(pointerIndex);
                final float deltaX = mLastMotionX + mLastMotionXRemainder - x;

                mTotalMotionX += Math.abs(deltaX);

                // Only scroll and update mLastMotionX if we have moved some discrete amount.  We
                // keep the remainder because we are actually testing if we've moved from the last
                // scrolled position (which is discrete).
                if (Math.abs(deltaX) >= 1.0f) {
                    scrollBy((int) deltaX, 0);
                    mLastMotionX = x;
                    mLastMotionXRemainder = deltaX - (int) deltaX;
                } else {
                    awakenScrollBars();
                }
            } else if (mTouchState == TOUCH_STATE_REORDERING) {
                // Update the last motion position
                mLastMotionX = ev.getX();
                mLastMotionY = ev.getY();

                // Update the parent down so that our zoom animations take this new movement into
                // account
                float[] pt = mapPointFromViewToParent(this, mLastMotionX, mLastMotionY);
                mParentDownMotionX = pt[0];
                mParentDownMotionY = pt[1];
                updateDragViewTranslationDuringDrag();

                // Find the closest page to the touch point
                final int dragViewIndex = indexOfChild(mDragView);

                if (DEBUG) Log.d(TAG, "mLastMotionX: " + mLastMotionX);
                if (DEBUG) Log.d(TAG, "mLastMotionY: " + mLastMotionY);
                if (DEBUG) Log.d(TAG, "mParentDownMotionX: " + mParentDownMotionX);
                if (DEBUG) Log.d(TAG, "mParentDownMotionY: " + mParentDownMotionY);

                final int pageUnderPointIndex = getNearestHoverOverPageIndex();
                // Do not allow any page to be moved to 0th position.
                if (pageUnderPointIndex > 0 && pageUnderPointIndex != indexOfChild(mDragView)) {
                    mTempVisiblePagesRange[0] = 0;
                    mTempVisiblePagesRange[1] = getPageCount() - 1;
                    getFreeScrollPageRange(mTempVisiblePagesRange);
                    if (mTempVisiblePagesRange[0] <= pageUnderPointIndex &&
                            pageUnderPointIndex <= mTempVisiblePagesRange[1] &&
                            pageUnderPointIndex != mSidePageHoverIndex && mScroller.isFinished()) {
                        mSidePageHoverIndex = pageUnderPointIndex;
                        mSidePageHoverRunnable = new Runnable() {
                            @Override
                            public void run() {
                                // Setup the scroll to the correct page before we swap the views
                                snapToPage(pageUnderPointIndex);

                                // For each of the pages between the paged view and the drag view,
                                // animate them from the previous position to the new position in
                                // the layout (as a result of the drag view moving in the layout)
                                int shiftDelta = (dragViewIndex < pageUnderPointIndex) ? -1 : 1;
                                int lowerIndex = (dragViewIndex < pageUnderPointIndex) ?
                                        dragViewIndex + 1 : pageUnderPointIndex;
                                int upperIndex = (dragViewIndex > pageUnderPointIndex) ?
                                        dragViewIndex - 1 : pageUnderPointIndex;
                                for (int i = lowerIndex; i <= upperIndex; ++i) {
                                    View v = getChildAt(i);
                                    // dragViewIndex < pageUnderPointIndex, so after we remove the
                                    // drag view all subsequent views to pageUnderPointIndex will
                                    // shift down.
                                    int oldX = getViewportOffsetX() + getChildOffset(i);
                                    int newX = getViewportOffsetX() + getChildOffset(i + shiftDelta);

                                    // Animate the view translation from its old position to its new
                                    // position
                                    ObjectAnimator anim = (ObjectAnimator) v.getTag();
                                    if (anim != null) {
                                        anim.cancel();
                                    }

                                    v.setTranslationX(oldX - newX);
                                    anim = LauncherAnimUtils.ofFloat(v, View.TRANSLATION_X, 0);
                                    anim.setDuration(REORDERING_REORDER_REPOSITION_DURATION);
                                    anim.start();
                                    v.setTag(anim);
                                }

                                removeView(mDragView);
                                addView(mDragView, pageUnderPointIndex);
                                mSidePageHoverIndex = -1;
                                if (mPageIndicator != null) {
                                    mPageIndicator.setActiveMarker(getNextPage());
                                }
                            }
                        };
                        postDelayed(mSidePageHoverRunnable, REORDERING_SIDE_PAGE_HOVER_TIMEOUT);
                    }
                } else {
                    removeCallbacks(mSidePageHoverRunnable);
                    mSidePageHoverIndex = -1;
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
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityX = (int) velocityTracker.getXVelocity(activePointerId);
                final int deltaX = (int) (x - mDownMotionX);
                final int pageWidth = getPageAt(mCurrentPage).getMeasuredWidth();
                boolean isSignificantMove = Math.abs(deltaX) > pageWidth *
                        SIGNIFICANT_MOVE_THRESHOLD;

                mTotalMotionX += Math.abs(mLastMotionX + mLastMotionXRemainder - x);

                boolean isFling = mTotalMotionX > MIN_LENGTH_FOR_FLING &&
                        shouldFlingForVelocity(velocityX);

                if (!mFreeScroll) {
                    // In the case that the page is moved far to one direction and then is flung
                    // in the opposite direction, we use a threshold to determine whether we should
                    // just return to the starting page, or if we should skip one further.
                    boolean returnToOriginalPage = false;
                    if (Math.abs(deltaX) > pageWidth * RETURN_TO_ORIGINAL_PAGE_THRESHOLD &&
                            Math.signum(velocityX) != Math.signum(deltaX) && isFling) {
                        returnToOriginalPage = true;
                    }

                    int finalPage;
                    // We give flings precedence over large moves, which is why we short-circuit our
                    // test for a large move if a fling has been registered. That is, a large
                    // move to the left and fling to the right will register as a fling to the right.
                    boolean isDeltaXLeft = mIsRtl ? deltaX > 0 : deltaX < 0;
                    boolean isVelocityXLeft = mIsRtl ? velocityX > 0 : velocityX < 0;
                    if (((isSignificantMove && !isDeltaXLeft && !isFling) ||
                            (isFling && !isVelocityXLeft)) && mCurrentPage > 0) {
                        finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage - 1;
                        snapToPageWithVelocity(finalPage, velocityX);
                    } else if (((isSignificantMove && isDeltaXLeft && !isFling) ||
                            (isFling && isVelocityXLeft)) &&
                            mCurrentPage < getChildCount() - 1) {
                        finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage + 1;
                        snapToPageWithVelocity(finalPage, velocityX);
                    } else {
                        snapToDestination();
                    }
                } else {
                    if (!mScroller.isFinished()) {
                        abortScrollerAnimation(true);
                    }

                    float scaleX = getScaleX();
                    int vX = (int) (-velocityX * scaleX);
                    int initialScrollX = (int) (getScrollX() * scaleX);

                    mScroller.setInterpolator(mDefaultInterpolator);
                    mScroller.fling(initialScrollX,
                            getScrollY(), vX, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0);
                    mNextPage = getPageNearestToCenterOfScreen((int) (mScroller.getFinalX() / scaleX));
                    invalidate();
                }
                onScrollInteractionEnd();
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
            } else if (mTouchState == TOUCH_STATE_REORDERING) {
                // Update the last motion position
                mLastMotionX = ev.getX();
                mLastMotionY = ev.getY();

                // Update the parent down so that our zoom animations take this new movement into
                // account
                float[] pt = mapPointFromViewToParent(this, mLastMotionX, mLastMotionY);
                mParentDownMotionX = pt[0];
                mParentDownMotionY = pt[1];
                updateDragViewTranslationDuringDrag();
            } else {
                if (!mCancelTap) {
                    onUnhandledTap(ev);
                }
            }

            // Remove the callback to wait for the side page hover timeout
            removeCallbacks(mSidePageHoverRunnable);
            // End any intermediate reordering states
            resetTouchState();
            break;

        case MotionEvent.ACTION_CANCEL:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                snapToDestination();
                onScrollInteractionEnd();
            }
            resetTouchState();
            break;

        case MotionEvent.ACTION_POINTER_UP:
            onSecondaryPointerUp(ev);
            releaseVelocityTracker();
            break;
        }

        return true;
    }

    protected boolean shouldFlingForVelocity(int velocityX) {
        return Math.abs(velocityX) > mFlingThresholdVelocity;
    }

    private void resetTouchState() {
        releaseVelocityTracker();
        endReordering();
        mCancelTap = false;
        mTouchState = TOUCH_STATE_REST;
        mActivePointerId = INVALID_POINTER;
    }

    /**
     * Triggered by scrolling via touch
     */
    protected void onScrollInteractionBegin() {
    }

    protected void onScrollInteractionEnd() {
    }

    protected void onUnhandledTap(MotionEvent ev) {
        Launcher.getLauncher(getContext()).onClick(this);
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
                        boolean isForwardScroll = mIsRtl ? (hscroll < 0 || vscroll < 0)
                                                         : (hscroll > 0 || vscroll > 0);
                        if (isForwardScroll) {
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
            mVelocityTracker.clear();
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
            mLastMotionY = ev.getY(newPointerIndex);
            mLastMotionXRemainder = 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int page = indexToPage(indexOfChild(child));
        if (page >= 0 && page != getCurrentPage() && !isInTouchMode()) {
            snapToPage(page);
        }
    }

    int getPageNearestToCenterOfScreen() {
        return getPageNearestToCenterOfScreen(getScrollX());
    }

    private int getPageNearestToCenterOfScreen(int scaledScrollX) {
        int screenCenter = getViewportOffsetX() + scaledScrollX + (getViewportWidth() / 2);
        int minDistanceFromScreenCenter = Integer.MAX_VALUE;
        int minDistanceFromScreenCenterIndex = -1;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            View layout = (View) getPageAt(i);
            int childWidth = layout.getMeasuredWidth();
            int halfChildWidth = (childWidth / 2);
            int childCenter = getViewportOffsetX() + getChildOffset(i) + halfChildWidth;
            int distanceFromScreenCenter = Math.abs(childCenter - screenCenter);
            if (distanceFromScreenCenter < minDistanceFromScreenCenter) {
                minDistanceFromScreenCenter = distanceFromScreenCenter;
                minDistanceFromScreenCenterIndex = i;
            }
        }
        return minDistanceFromScreenCenterIndex;
    }

    protected void snapToDestination() {
        snapToPage(getPageNearestToCenterOfScreen(), getPageSnapDuration());
    }

    protected boolean isInOverScroll() {
        return (mOverScrollX > mMaxScrollX || mOverScrollX < 0);
    }

    protected int getPageSnapDuration() {
        if (isInOverScroll()) {
            return OVERSCROLL_PAGE_SNAP_ANIMATION_DURATION;
        }
        return PAGE_SNAP_ANIMATION_DURATION;
    }

    public static class ScrollInterpolator implements Interpolator {
        public ScrollInterpolator() {
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            return t*t*t*t*t + 1;
        }
    }

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    protected void snapToPageWithVelocity(int whichPage, int velocity) {
        whichPage = validateNewPage(whichPage);
        int halfScreenSize = getViewportWidth() / 2;

        final int newX = getScrollForPage(whichPage);
        int delta = newX - getUnboundedScrollX();
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

        snapToPage(whichPage, delta, duration);
    }

    public void snapToPage(int whichPage) {
        snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
    }

    public void snapToPageImmediately(int whichPage) {
        snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION, true, null);
    }

    protected void snapToPage(int whichPage, int duration) {
        snapToPage(whichPage, duration, false, null);
    }

    protected void snapToPage(int whichPage, int duration, TimeInterpolator interpolator) {
        snapToPage(whichPage, duration, false, interpolator);
    }

    protected void snapToPage(int whichPage, int duration, boolean immediate,
            TimeInterpolator interpolator) {
        whichPage = validateNewPage(whichPage);

        int newX = getScrollForPage(whichPage);
        final int delta = newX - getUnboundedScrollX();
        snapToPage(whichPage, delta, duration, immediate, interpolator);
    }

    protected void snapToPage(int whichPage, int delta, int duration) {
        snapToPage(whichPage, delta, duration, false, null);
    }

    protected void snapToPage(int whichPage, int delta, int duration, boolean immediate,
            TimeInterpolator interpolator) {
        whichPage = validateNewPage(whichPage);

        mNextPage = whichPage;

        awakenScrollBars(duration);
        if (immediate) {
            duration = 0;
        } else if (duration == 0) {
            duration = Math.abs(delta);
        }

        if (duration != 0) {
            pageBeginTransition();
        }

        if (!mScroller.isFinished()) {
            abortScrollerAnimation(false);
        }

        if (interpolator != null) {
            mScroller.setInterpolator(interpolator);
        } else {
            mScroller.setInterpolator(mDefaultInterpolator);
        }

        mScroller.startScroll(getUnboundedScrollX(), 0, delta, 0, duration);

        updatePageIndicator();

        // Trigger a compute() to finish switching pages if necessary
        if (immediate) {
            computeScroll();
            pageEndTransition();
        }

        invalidate();
    }

    public void scrollLeft() {
        if (getNextPage() > 0) snapToPage(getNextPage() - 1);
    }

    public void scrollRight() {
        if (getNextPage() < getChildCount() -1) snapToPage(getNextPage() + 1);
    }

    @Override
    public boolean performLongClick() {
        mCancelTap = true;
        return super.performLongClick();
    }

    public static class SavedState extends BaseSavedState {
        int currentPage = -1;

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Thunk SavedState(Parcel in) {
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

    // Animate the drag view back to the original position
    private void animateDragViewToOriginalPosition() {
        if (mDragView != null) {
            Animator anim = LauncherAnimUtils.ofPropertyValuesHolder(mDragView,
                    new PropertyListBuilder()
                            .scale(1)
                            .translationX(0)
                            .translationY(0)
                            .build())
                    .setDuration(REORDERING_DROP_REPOSITION_DURATION);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onPostReorderingAnimationCompleted();
                }
            });
            anim.start();
        }
    }

    public void onStartReordering() {
        // Set the touch state to reordering (allows snapping to pages, dragging a child, etc.)
        mTouchState = TOUCH_STATE_REORDERING;
        mIsReordering = true;

        // We must invalidate to trigger a redraw to update the layers such that the drag view
        // is always drawn on top
        invalidate();
    }

    @Thunk void onPostReorderingAnimationCompleted() {
        // Trigger the callback when reordering has settled
        --mPostReorderingPreZoomInRemainingAnimationCount;
        if (mPostReorderingPreZoomInRunnable != null &&
                mPostReorderingPreZoomInRemainingAnimationCount == 0) {
            mPostReorderingPreZoomInRunnable.run();
            mPostReorderingPreZoomInRunnable = null;
        }
    }

    public void onEndReordering() {
        mIsReordering = false;
    }

    public boolean startReordering(View v) {
        int dragViewIndex = indexOfChild(v);

        // Do not allow the first page to be moved around
        if (mTouchState != TOUCH_STATE_REST || dragViewIndex <= 0) return false;

        mTempVisiblePagesRange[0] = 0;
        mTempVisiblePagesRange[1] = getPageCount() - 1;
        getFreeScrollPageRange(mTempVisiblePagesRange);
        mReorderingStarted = true;

        // Check if we are within the reordering range
        if (mTempVisiblePagesRange[0] <= dragViewIndex &&
            dragViewIndex <= mTempVisiblePagesRange[1]) {
            // Find the drag view under the pointer
            mDragView = getChildAt(dragViewIndex);
            mDragView.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start();
            mDragViewBaselineLeft = mDragView.getLeft();
            snapToPage(getPageNearestToCenterOfScreen());
            disableFreeScroll();
            onStartReordering();
            return true;
        }
        return false;
    }

    boolean isReordering(boolean testTouchState) {
        boolean state = mIsReordering;
        if (testTouchState) {
            state &= (mTouchState == TOUCH_STATE_REORDERING);
        }
        return state;
    }
    void endReordering() {
        // For simplicity, we call endReordering sometimes even if reordering was never started.
        // In that case, we don't want to do anything.
        if (!mReorderingStarted) return;
        mReorderingStarted = false;

        mPostReorderingPreZoomInRunnable = new Runnable() {
            public void run() {
                // If we haven't flung-to-delete the current child,
                // then we just animate the drag view back into position
                onEndReordering();

                enableFreeScroll();
            }
        };

        mPostReorderingPreZoomInRemainingAnimationCount =
                NUM_ANIMATIONS_RUNNING_BEFORE_ZOOM_OUT;
        // Snap to the current page
        snapToPage(indexOfChild(mDragView), 0);
        // Animate the drag view back to the front position
        animateDragViewToOriginalPosition();
    }

    /* Accessibility */
    @SuppressWarnings("deprecation")
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
        info.setClassName(getClass().getName());

        // Accessibility-wise, PagedView doesn't support long click, so disabling it.
        // Besides disabling the accessibility long-click, this also prevents this view from getting
        // accessibility focus.
        info.setLongClickable(false);
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK);
    }

    @Override
    public void sendAccessibilityEvent(int eventType) {
        // Don't let the view send real scroll events.
        if (eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            super.sendAccessibilityEvent(eventType);
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setScrollable(getPageCount() > 1);
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

    protected String getPageIndicatorDescription() {
        return getCurrentPageDescription();
    }

    protected String getCurrentPageDescription() {
        return getContext().getString(R.string.default_scroll_format,
                getNextPage() + 1, getChildCount());
    }

    @Override
    public boolean onHoverEvent(android.view.MotionEvent event) {
        return true;
    }
}
