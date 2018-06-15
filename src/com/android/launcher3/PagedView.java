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

import static com.android.launcher3.compat.AccessibilityManagerCompat.isAccessibilityEnabled;
import static com.android.launcher3.compat.AccessibilityManagerCompat.isObservedEventType;

import android.animation.LayoutTransition;
import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.ScrollView;

import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;

/**
 * An abstraction of the original Workspace which supports browsing through a
 * sequential list of "pages"
 */
public abstract class PagedView<T extends View & PageIndicator> extends ViewGroup {
    private static final String TAG = "PagedView";
    private static final boolean DEBUG = false;

    protected static final int INVALID_PAGE = -1;
    protected static final ComputePageScrollsLogic SIMPLE_SCROLL_LOGIC = (v) -> v.getVisibility() != GONE;

    public static final int PAGE_SNAP_ANIMATION_DURATION = 750;
    public static final int SLOW_PAGE_SNAP_ANIMATION_DURATION = 950;

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
    private boolean mSettleOnPageInFreeScroll = false;

    protected int mFlingThresholdVelocity;
    protected int mMinFlingVelocity;
    protected int mMinSnapVelocity;

    protected boolean mFirstLayout = true;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mCurrentPage;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mNextPage = INVALID_PAGE;
    protected int mMaxScrollX;
    protected LauncherScroller mScroller;
    private Interpolator mDefaultInterpolator;
    private VelocityTracker mVelocityTracker;
    protected int mPageSpacing = 0;

    private float mDownMotionX;
    private float mDownMotionY;
    private float mLastMotionX;
    private float mLastMotionXRemainder;
    private float mTotalMotionX;

    protected int[] mPageScrolls;

    protected final static int TOUCH_STATE_REST = 0;
    protected final static int TOUCH_STATE_SCROLLING = 1;
    protected final static int TOUCH_STATE_PREV_PAGE = 2;
    protected final static int TOUCH_STATE_NEXT_PAGE = 3;

    protected int mTouchState = TOUCH_STATE_REST;

    protected int mTouchSlop;
    private int mMaximumVelocity;
    protected boolean mAllowOverScroll = true;

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
    protected T mPageIndicator;

    // Convenience/caching
    private static final Rect sTmpRect = new Rect();

    protected final Rect mInsets = new Rect();
    protected boolean mIsRtl;

    // Similar to the platform implementation of isLayoutValid();
    protected boolean mIsLayoutValid;

    private int[] mTmpIntPair = new int[2];

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
        setDefaultInterpolator(Interpolators.SCROLL);
        mCurrentPage = 0;

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledPagingTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        float density = getResources().getDisplayMetrics().density;
        mFlingThresholdVelocity = (int) (FLING_THRESHOLD_VELOCITY * density);
        mMinFlingVelocity = (int) (MIN_FLING_VELOCITY * density);
        mMinSnapVelocity = (int) (MIN_SNAP_VELOCITY * density);

        if (Utilities.ATLEAST_OREO) {
            setDefaultFocusHighlightEnabled(false);
        }
    }

    protected void setDefaultInterpolator(Interpolator interpolator) {
        mDefaultInterpolator = interpolator;
        mScroller.setInterpolator(mDefaultInterpolator);
    }

    public void initParentViews(View parent) {
        if (mPageIndicatorViewId > -1) {
            mPageIndicator = parent.findViewById(mPageIndicatorViewId);
            mPageIndicator.setMarkersCount(getChildCount());
        }
    }

    public T getPageIndicator() {
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
            pageEndTransition();
        }
    }

    private void forceFinishScroller(boolean resetNextPage) {
        mScroller.forceFinished(true);
        // We need to clean up the next page here to avoid computeScrollHelper from
        // updating current page on the pass.
        if (resetNextPage) {
            mNextPage = INVALID_PAGE;
            pageEndTransition();
        }
    }

    private int validateNewPage(int newPage) {
        // Ensure that it is clamped by the actual set of children in all cases
        return Utilities.boundToRange(newPage, 0, getPageCount() - 1);
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
        if (mPageIndicator != null) {
            mPageIndicator.setActiveMarker(getNextPage());
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
            if (!mScroller.isFinished() && (x > mMaxScrollX || x < 0)) {
                forceFinishScroller(false);
            }

            x = Utilities.boundToRange(x, 0, mMaxScrollX);
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
    }

    private void sendScrollAccessibilityEvent() {
        if (isObservedEventType(getContext(), AccessibilityEvent.TYPE_VIEW_SCROLLED)) {
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

    protected void announcePageForAccessibility() {
        if (isAccessibilityEnabled(getContext())) {
            // Notify the user when the page changes
            announceForAccessibility(getCurrentPageDescription());
        }
    }

    protected boolean computeScrollHelper(boolean shouldInvalidate) {
        if (mScroller.computeScrollOffset()) {
            // Don't bother scrolling if the page does not need to be moved
            if (getUnboundedScrollX() != mScroller.getCurrX()
                    || getScrollY() != mScroller.getCurrY()
                    || mOverScrollX != mScroller.getCurrX()) {
                scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
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

            if (canAnnouncePageDescription()) {
                announcePageForAccessibility();
            }
        }
        return false;
    }

    @Override
    public void computeScroll() {
        computeScrollHelper();
    }

    public int getExpectedHeight() {
        return getMeasuredHeight();
    }

    public int getNormalChildHeight() {
        return  getExpectedHeight() - getPaddingTop() - getPaddingBottom()
                - mInsets.top - mInsets.bottom;
    }

    public int getExpectedWidth() {
        return getMeasuredWidth();
    }

    public int getNormalChildWidth() {
        return  getExpectedWidth() - getPaddingLeft() - getPaddingRight()
                - mInsets.left - mInsets.right;
    }

    @Override
    public void requestLayout() {
        mIsLayoutValid = false;
        super.requestLayout();
    }

    @Override
    public void forceLayout() {
        mIsLayoutValid = false;
        super.forceLayout();
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

        if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Return early if we aren't given a proper dimension
        if (widthSize <= 0 || heightSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // The children are given the same width and height as the workspace
        // unless they were set to WRAP_CONTENT
        if (DEBUG) Log.d(TAG, "PagedView.onMeasure(): " + widthSize + ", " + heightSize);

        int myWidthSpec = MeasureSpec.makeMeasureSpec(
                widthSize - mInsets.left - mInsets.right, MeasureSpec.EXACTLY);
        int myHeightSpec = MeasureSpec.makeMeasureSpec(
                heightSize - mInsets.top - mInsets.bottom, MeasureSpec.EXACTLY);

        // measureChildren takes accounts for content padding, we only need to care about extra
        // space due to insets.
        measureChildren(myWidthSpec, myHeightSpec);
        setMeasuredDimension(widthSize, heightSize);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mIsLayoutValid = true;
        final int childCount = getChildCount();
        boolean pageScrollChanged = false;
        if (mPageScrolls == null || childCount != mPageScrolls.length) {
            mPageScrolls = new int[childCount];
            pageScrollChanged = true;
        }

        if (childCount == 0) {
            return;
        }

        if (DEBUG) Log.d(TAG, "PagedView.onLayout()");

        if (getPageScrolls(mPageScrolls, true, SIMPLE_SCROLL_LOGIC)) {
            pageScrollChanged = true;
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

        if (mScroller.isFinished() && pageScrollChanged) {
            setCurrentPage(getNextPage());
        }
    }

    /**
     * Initializes {@code outPageScrolls} with scroll positions for view at that index. The length
     * of {@code outPageScrolls} should be same as the the childCount
     *
     */
    protected boolean getPageScrolls(int[] outPageScrolls, boolean layoutChildren,
            ComputePageScrollsLogic scrollLogic) {
        final int childCount = getChildCount();

        final int startIndex = mIsRtl ? childCount - 1 : 0;
        final int endIndex = mIsRtl ? -1 : childCount;
        final int delta = mIsRtl ? -1 : 1;

        final int verticalCenter = (getPaddingTop() + getMeasuredHeight() + mInsets.top
                - mInsets.bottom - getPaddingBottom()) / 2;

        final int scrollOffsetLeft = mInsets.left + getPaddingLeft();
        final int scrollOffsetRight = getWidth() - getPaddingRight() - mInsets.right;
        boolean pageScrollChanged = false;

        for (int i = startIndex, childLeft = scrollOffsetLeft; i != endIndex; i += delta) {
            final View child = getPageAt(i);
            if (scrollLogic.shouldIncludeView(child)) {
                final int childWidth = child.getMeasuredWidth();
                final int childRight = childLeft + childWidth;

                if (layoutChildren) {
                    final int childHeight = child.getMeasuredHeight();
                    final int childTop = verticalCenter - childHeight / 2;
                    child.layout(childLeft, childTop, childRight, childTop + childHeight);
                }

                // In case the pages are of different width, align the page to left or right edge
                // based on the orientation.
                final int pageScroll = mIsRtl
                        ? (childLeft - scrollOffsetLeft)
                        : Math.max(0, childRight  - scrollOffsetRight);
                if (outPageScrolls[i] != pageScroll) {
                    pageScrollChanged = true;
                    outPageScrolls[i] = pageScroll;
                }

                childLeft += childWidth + mPageSpacing + getChildGap();
            }
        }
        return pageScrollChanged;
    }

    protected int getChildGap() {
        return 0;
    }

    private void updateMaxScrollX() {
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

    private void dispatchPageCountChanged() {
        if (mPageIndicator != null) {
            mPageIndicator.setMarkersCount(getChildCount());
        }
        // This ensures that when children are added, they get the correct transforms / alphas
        // in accordance with any scroll effects.
        invalidate();
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        dispatchPageCountChanged();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        mCurrentPage = validateNewPage(mCurrentPage);
        dispatchPageCountChanged();
    }

    protected int getChildOffset(int index) {
        if (index < 0 || index > getChildCount() - 1) return 0;
        return getPageAt(index).getLeft();
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int page = indexToPage(indexOfChild(child));
        if (page != mCurrentPage || !mScroller.isFinished()) {
            if (immediate) {
                setCurrentPage(page);
            } else {
                snapToPage(page);
            }
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
                getChildAt(getCurrentPage() - 1).requestFocus(direction);
                return true;
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (getCurrentPage() < getPageCount() - 1) {
                snapToPage(getCurrentPage() + 1);
                getChildAt(getCurrentPage() + 1).requestFocus(direction);
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

    /** Returns whether x and y originated within the buffered viewport */
    private boolean isTouchPointInViewportWithBuffer(int x, int y) {
        sTmpRect.set(-getMeasuredWidth() / 2, 0, 3 * getMeasuredWidth() / 2, getMeasuredHeight());
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
                mLastMotionX = x;
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

    public boolean isHandlingTouch() {
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
        final int halfScreenSize = getMeasuredWidth() / 2;

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

            int scrollOffset = mIsRtl ? getPaddingRight() : getPaddingLeft();
            int baselineX = mPageScrolls[index] + scrollOffset;
            return (int) (child.getX() - baselineX);
        }
    }

    protected void dampedOverScroll(float amount) {
        if (Float.compare(amount, 0f) == 0) return;

        int overScrollAmount = OverScroll.dampedScroll(amount, getMeasuredWidth());
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


    protected void enableFreeScroll(boolean settleOnPageInFreeScroll) {
        setEnableFreeScroll(true);
        mSettleOnPageInFreeScroll = settleOnPageInFreeScroll;
    }

    private void setEnableFreeScroll(boolean freeScroll) {
        boolean wasFreeScroll = mFreeScroll;
        mFreeScroll = freeScroll;

        if (mFreeScroll) {
            setCurrentPage(getNextPage());
        } else if (wasFreeScroll) {
            snapToPage(getNextPage());
        }

        setEnableOverscroll(!freeScroll);
    }

    protected void setEnableOverscroll(boolean enable) {
        mAllowOverScroll = enable;
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
            mDownMotionY = ev.getY();
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
                boolean isFling = mTotalMotionX > mTouchSlop && shouldFlingForVelocity(velocityX);

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
                    int unscaledScrollX = (int) (mScroller.getFinalX() / scaleX);
                    mNextPage = getPageNearestToCenterOfScreen(unscaledScrollX);
                    int firstPageScroll = getScrollForPage(!mIsRtl ? 0 : getPageCount() - 1);
                    int lastPageScroll = getScrollForPage(!mIsRtl ? getPageCount() - 1 : 0);
                    if (mSettleOnPageInFreeScroll && unscaledScrollX > 0
                            && unscaledScrollX < mMaxScrollX) {
                        // If scrolling ends in the half of the added space that is closer to the
                        // end, settle to the end. Otherwise snap to the nearest page.
                        // If flinging past one of the ends, don't change the velocity as it will
                        // get stopped at the end anyway.
                        final int finalX = unscaledScrollX < firstPageScroll / 2 ?
                                0 :
                                unscaledScrollX > (lastPageScroll + mMaxScrollX) / 2 ?
                                        mMaxScrollX :
                                        getScrollForPage(mNextPage);

                        mScroller.setFinalX((int) (finalX * getScaleX()));
                        // Ensure the scroll/snap doesn't happen too fast;
                        int extraScrollDuration = OVERSCROLL_PAGE_SNAP_ANIMATION_DURATION
                                - mScroller.getDuration();
                        if (extraScrollDuration > 0) {
                            mScroller.extendDuration(extraScrollDuration);
                        }
                    }
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
            }

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

    public int getPageNearestToCenterOfScreen() {
        return getPageNearestToCenterOfScreen(getScrollX());
    }

    private int getPageNearestToCenterOfScreen(int scaledScrollX) {
        int screenCenter = scaledScrollX + (getMeasuredWidth() / 2);
        int minDistanceFromScreenCenter = Integer.MAX_VALUE;
        int minDistanceFromScreenCenterIndex = -1;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            View layout = getPageAt(i);
            int childWidth = layout.getMeasuredWidth();
            int halfChildWidth = (childWidth / 2);
            int childCenter = getChildOffset(i) + halfChildWidth;
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

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    protected boolean snapToPageWithVelocity(int whichPage, int velocity) {
        whichPage = validateNewPage(whichPage);
        int halfScreenSize = getMeasuredWidth() / 2;

        final int newX = getScrollForPage(whichPage);
        int delta = newX - getUnboundedScrollX();
        int duration = 0;

        if (Math.abs(velocity) < mMinFlingVelocity) {
            // If the velocity is low enough, then treat this more as an automatic page advance
            // as opposed to an apparent physical response to flinging
            return snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
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

        return snapToPage(whichPage, delta, duration);
    }

    public boolean snapToPage(int whichPage) {
        return snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
    }

    public boolean snapToPageImmediately(int whichPage) {
        return snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION, true, null);
    }

    public boolean snapToPage(int whichPage, int duration) {
        return snapToPage(whichPage, duration, false, null);
    }

    public boolean snapToPage(int whichPage, int duration, TimeInterpolator interpolator) {
        return snapToPage(whichPage, duration, false, interpolator);
    }

    protected boolean snapToPage(int whichPage, int duration, boolean immediate,
            TimeInterpolator interpolator) {
        whichPage = validateNewPage(whichPage);

        int newX = getScrollForPage(whichPage);
        final int delta = newX - getUnboundedScrollX();
        return snapToPage(whichPage, delta, duration, immediate, interpolator);
    }

    protected boolean snapToPage(int whichPage, int delta, int duration) {
        return snapToPage(whichPage, delta, duration, false, null);
    }

    protected boolean snapToPage(int whichPage, int delta, int duration, boolean immediate,
            TimeInterpolator interpolator) {
        if (mFirstLayout) {
            setCurrentPage(whichPage);
            return false;
        }

        if (FeatureFlags.IS_DOGFOOD_BUILD) {
            duration *= Settings.System.getFloat(getContext().getContentResolver(),
                    Settings.System.WINDOW_ANIMATION_SCALE, 1);
        }

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
        return Math.abs(delta) > 0;
    }

    public boolean scrollLeft() {
        if (getNextPage() > 0) {
            snapToPage(getNextPage() - 1);
            return true;
        }
        return false;
    }

    public boolean scrollRight() {
        if (getNextPage() < getChildCount() - 1) {
            snapToPage(getNextPage() + 1);
            return true;
        }
        return false;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        // Some accessibility services have special logic for ScrollView. Since we provide same
        // accessibility info as ScrollView, inform the service to handle use the same way.
        return ScrollView.class.getName();
    }

    protected boolean isPageOrderFlipped() {
        return false;
    }

    /* Accessibility */
    @SuppressWarnings("deprecation")
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        final boolean pagesFlipped = isPageOrderFlipped();
        info.setScrollable(getPageCount() > 1);
        if (getCurrentPage() < getPageCount() - 1) {
            info.addAction(pagesFlipped ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }
        if (getCurrentPage() > 0) {
            info.addAction(pagesFlipped ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }

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
        final boolean pagesFlipped = isPageOrderFlipped();
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
                if (pagesFlipped ? scrollLeft() : scrollRight()) {
                    return true;
                }
            } break;
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                if (pagesFlipped ? scrollRight() : scrollLeft()) {
                    return true;
                }
            }
            break;
        }
        return false;
    }

    protected boolean canAnnouncePageDescription() {
        return true;
    }

    protected String getCurrentPageDescription() {
        return getContext().getString(R.string.default_scroll_format,
                getNextPage() + 1, getChildCount());
    }

    protected float getDownMotionX() {
        return mDownMotionX;
    }

    protected float getDownMotionY() {
        return mDownMotionY;
    }

    protected interface ComputePageScrollsLogic {

        boolean shouldIncludeView(View view);
    }

    public int[] getVisibleChildrenRange() {
        float visibleLeft = 0;
        float visibleRight = visibleLeft + getMeasuredWidth();
        float scaleX = getScaleX();
        if (scaleX < 1 && scaleX > 0) {
            float mid = getMeasuredWidth() / 2;
            visibleLeft = mid - ((mid - visibleLeft) / scaleX);
            visibleRight = mid + ((visibleRight - mid) / scaleX);
        }

        int leftChild = -1;
        int rightChild = -1;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getPageAt(i);

            float left = child.getLeft() + child.getTranslationX() - getScrollX();
            if (left <= visibleRight && (left + child.getMeasuredWidth()) >= visibleLeft) {
                if (leftChild == -1) {
                    leftChild = i;
                }
                rightChild = i;
            }
        }
        mTmpIntPair[0] = leftChild;
        mTmpIntPair[1] = rightChild;
        return mTmpIntPair;
    }
}
