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

import static com.android.app.animation.Interpolators.SCROLL;
import static com.android.launcher3.compat.AccessibilityManagerCompat.isAccessibilityEnabled;
import static com.android.launcher3.compat.AccessibilityManagerCompat.isObservedEventType;
import static com.android.launcher3.testing.shared.TestProtocol.SCROLL_FINISHED_MESSAGE;
import static com.android.launcher3.touch.OverScroll.OVERSCROLL_DAMP_FACTOR;
import static com.android.launcher3.touch.PagedOrientationHandler.VIEW_SCROLL_BY;
import static com.android.launcher3.touch.PagedOrientationHandler.VIEW_SCROLL_TO;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
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
import android.widget.OverScroller;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.touch.PagedOrientationHandler.ChildBounds;
import com.android.launcher3.util.EdgeEffectCompat;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * An abstraction of the original Workspace which supports browsing through a
 * sequential list of "pages"
 */
public abstract class PagedView<T extends View & PageIndicator> extends ViewGroup {
    private static final String TAG = "PagedView";
    private static final boolean DEBUG = false;
    public static final boolean DEBUG_FAILED_QUICKSWITCH = false;

    public static final int ACTION_MOVE_ALLOW_EASY_FLING = MotionEvent.ACTION_MASK - 1;
    public static final int INVALID_PAGE = -1;
    protected static final ComputePageScrollsLogic SIMPLE_SCROLL_LOGIC = (v) -> v.getVisibility() != GONE;

    private static final float RETURN_TO_ORIGINAL_PAGE_THRESHOLD = 0.33f;
    // The page is moved more than halfway, automatically move to the next page on touch up.
    private static final float SIGNIFICANT_MOVE_THRESHOLD = 0.4f;

    private static final float MAX_SCROLL_PROGRESS = 1.0f;

    private boolean mFreeScroll = false;

    private int mFlingThresholdVelocity;
    private int mEasyFlingThresholdVelocity;
    private int mMinFlingVelocity;
    private int mMinSnapVelocity;
    private int mPageSnapAnimationDuration;

    protected boolean mFirstLayout = true;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mCurrentPage;
    // Difference between current scroll position and mCurrentPage's page scroll. Used to maintain
    // relative scroll position unchanged in updateCurrentPageScroll. Cleared when snapping to a
    // page.
    protected int mCurrentPageScrollDiff;
    // The current page the PagedView is scrolling over on it's way to the destination page.
    protected int mCurrentScrollOverPage;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mNextPage = INVALID_PAGE;
    protected int mMaxScroll;
    protected int mMinScroll;
    protected OverScroller mScroller;
    private VelocityTracker mVelocityTracker;
    protected int mPageSpacing = 0;

    private float mDownMotionX;
    private float mDownMotionY;
    private float mDownMotionPrimary;
    private int mLastMotion;
    private float mTotalMotion;
    // Used in special cases where the fling checks can be relaxed for an intentional gesture
    private boolean mAllowEasyFling;
    protected PagedOrientationHandler mOrientationHandler = PagedOrientationHandler.PORTRAIT;

    private final ArrayList<Runnable> mOnPageScrollsInitializedCallbacks = new ArrayList<>();

    // We should always check pageScrollsInitialized() is true when using mPageScrolls.
    @Nullable protected int[] mPageScrolls = null;
    private boolean mIsBeingDragged;

    // The amount of movement to begin scrolling
    protected int mTouchSlop;
    // The amount of movement to begin paging
    protected int mPageSlop;
    private int mMaximumVelocity;
    protected boolean mAllowOverScroll = true;

    protected static final int INVALID_POINTER = -1;

    protected int mActivePointerId = INVALID_POINTER;

    protected boolean mIsPageInTransition = false;
    private Runnable mOnPageTransitionEndCallback;

    // Page Indicator
    @Thunk int mPageIndicatorViewId;
    protected T mPageIndicator;

    protected final Rect mInsets = new Rect();
    protected boolean mIsRtl;

    // Similar to the platform implementation of isLayoutValid();
    protected boolean mIsLayoutValid;

    private int[] mTmpIntPair = new int[2];

    protected EdgeEffectCompat mEdgeGlowLeft;
    protected EdgeEffectCompat mEdgeGlowRight;

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

        mScroller = new OverScroller(context, SCROLL);
        mCurrentPage = 0;
        mCurrentScrollOverPage = 0;

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mPageSlop = configuration.getScaledPagingTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        updateVelocityValues();

        initEdgeEffect();
        setDefaultFocusHighlightEnabled(false);
        setWillNotDraw(false);
    }

    protected void initEdgeEffect() {
        mEdgeGlowLeft = new EdgeEffectCompat(getContext());
        mEdgeGlowRight = new EdgeEffectCompat(getContext());
    }

    public void initParentViews(View parent) {
        if (mPageIndicatorViewId > -1) {
            mPageIndicator = parent.findViewById(mPageIndicatorViewId);
            mPageIndicator.setMarkersCount(getChildCount() / getPanelCount());
        }
    }

    public T getPageIndicator() {
        return mPageIndicator;
    }

    /**
     * Returns the index of the currently displayed page. When in free scroll mode, this is the page
     * that the user was on before entering free scroll mode (e.g. the home screen page they
     * long-pressed on to enter the overview). Try using {@link #getDestinationPage()}
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

    /**
     * Updates the scroll of the current page immediately to its final scroll position.  We use this
     * in CustomizePagedView to allow tabs to share the same PagedView while resetting the scroll of
     * the previous tab page.
     */
    protected void updateCurrentPageScroll() {
        // If the current page is invalid, just reset the scroll position to zero
        int newPosition = 0;
        if (0 <= mCurrentPage && mCurrentPage < getPageCount()) {
            newPosition = getScrollForPage(mCurrentPage) + mCurrentPageScrollDiff;
        }
        mOrientationHandler.setPrimary(this, VIEW_SCROLL_TO, newPosition);
        mScroller.startScroll(mScroller.getCurrX(), 0, newPosition - mScroller.getCurrX(), 0);
        forceFinishScroller();
    }

    /**
     *  Immediately finishes any overscroll effect and jumps to the end of the scroller animation.
     */
    public void abortScrollerAnimation() {
        mEdgeGlowLeft.finish();
        mEdgeGlowRight.finish();
        abortScrollerAnimation(true);
    }

    protected void onScrollerAnimationAborted() {
        // No-Op
    }

    private void abortScrollerAnimation(boolean resetNextPage) {
        mScroller.abortAnimation();
        onScrollerAnimationAborted();
        // We need to clean up the next page here to avoid computeScrollHelper from
        // updating current page on the pass.
        if (resetNextPage) {
            mNextPage = INVALID_PAGE;
            pageEndTransition();
        }
    }

    /**
     * Immediately finishes any in-progress scroll, maintaining the current position. Also sets
     * mNextPage = INVALID_PAGE and calls pageEndTransition().
     */
    public void forceFinishScroller() {
        mScroller.forceFinished(true);
        // We need to clean up the next page here to avoid computeScrollHelper from
        // updating current page on the pass.
        mNextPage = INVALID_PAGE;
        pageEndTransition();
    }

    private int validateNewPage(int newPage) {
        newPage = ensureWithinScrollBounds(newPage);
        // Ensure that it is clamped by the actual set of children in all cases
        newPage = Utilities.boundToRange(newPage, 0, getPageCount() - 1);

        if (getPanelCount() > 1) {
            // Always return left most panel as new page
            newPage = getLeftmostVisiblePageForIndex(newPage);
        }
        return newPage;
    }

    /**
     * In most cases where panelCount is 1, this method will just return the page index that was
     * passed in.
     * But for example when two panel home is enabled we might need the leftmost visible page index
     * because that page is the current page.
     */
    public int getLeftmostVisiblePageForIndex(int pageIndex) {
        int panelCount = getPanelCount();
        return pageIndex - pageIndex % panelCount;
    }

    /**
     * Returns the number of pages that are shown at the same time.
     */
    protected int getPanelCount() {
        return 1;
    }

    /**
     * Returns an IntSet with the indices of the currently visible pages
     */
    public IntSet getVisiblePageIndices() {
        return getPageIndices(mCurrentPage);
    }

    /**
     * In case the panelCount is 1 this just returns the same page index in an IntSet.
     * But in cases where the panelCount > 1 this will return all the page indices that belong
     * together, i.e. on the Workspace they are next to each other and shown at the same time.
     */
    private IntSet getPageIndices(int pageIndex) {
        // we want to make sure the pageIndex is the leftmost page
        pageIndex = getLeftmostVisiblePageForIndex(pageIndex);

        IntSet pageIndices = new IntSet();
        int panelCount = getPanelCount();
        int pageCount = getPageCount();
        for (int page = pageIndex; page < pageIndex + panelCount && page < pageCount; page++) {
            pageIndices.add(page);
        }
        return pageIndices;
    }

    /**
     * Returns an IntSet with the indices of the neighbour pages that are in the focus direction.
     */
    private IntSet getNeighbourPageIndices(int focus) {
        int panelCount = getPanelCount();
        // getNextPage is more reliable than getCurrentPage
        int currentPage = getNextPage();

        int nextPage;
        if (focus == View.FOCUS_LEFT) {
            nextPage = currentPage - panelCount;
        } else if (focus == View.FOCUS_RIGHT) {
            nextPage = currentPage + panelCount;
        } else {
            // no neighbours to other directions
            return new IntSet();
        }
        nextPage = validateNewPage(nextPage);
        if (nextPage == currentPage) {
            // We reached the end of the pages
            return new IntSet();
        }

        return getPageIndices(nextPage);
    }

    /**
     * Executes the callback against each visible page
     */
    public void forEachVisiblePage(Consumer<View> callback) {
        getVisiblePageIndices().forEach(pageIndex -> {
            View page = getPageAt(pageIndex);
            if (page != null) {
                callback.accept(page);
            }
        });
    }

    /**
     * Returns true if the view is on one of the current pages, false otherwise.
     */
    public boolean isVisible(View child) {
        return isVisible(indexOfChild(child));
    }

    /**
     * Returns true if the page with the given index is currently visible, false otherwise.
     */
    private boolean isVisible(int pageIndex) {
        return getLeftmostVisiblePageForIndex(pageIndex) == mCurrentPage;
    }

    /**
     * @return The closest page to the provided page that is within mMinScrollX and mMaxScrollX.
     */
    private int ensureWithinScrollBounds(int page) {
        int dir = !mIsRtl ? 1 : - 1;
        int currScroll = getScrollForPage(page);
        int prevScroll;
        while (currScroll < mMinScroll) {
            page += dir;
            prevScroll = currScroll;
            currScroll = getScrollForPage(page);
            if (currScroll <= prevScroll) {
                Log.e(TAG, "validateNewPage: failed to find a page > mMinScrollX");
                break;
            }
        }
        while (currScroll > mMaxScroll) {
            page -= dir;
            prevScroll = currScroll;
            currScroll = getScrollForPage(page);
            if (currScroll >= prevScroll) {
                Log.e(TAG, "validateNewPage: failed to find a page < mMaxScrollX");
                break;
            }
        }
        return page;
    }

    public void setCurrentPage(int currentPage) {
        setCurrentPage(currentPage, INVALID_PAGE);
    }

    /**
     * Sets the current page.
     */
    public void setCurrentPage(int currentPage, int overridePrevPage) {
        if (!mScroller.isFinished()) {
            abortScrollerAnimation(true);
        }
        // don't introduce any checks like mCurrentPage == currentPage here-- if we change the
        // the default
        if (getChildCount() == 0) {
            return;
        }
        int prevPage = overridePrevPage != INVALID_PAGE ? overridePrevPage : mCurrentPage;
        mCurrentPage = validateNewPage(currentPage);
        mCurrentScrollOverPage = mCurrentPage;
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
        if (mIsPageInTransition && !mIsBeingDragged && mScroller.isFinished()
                && (!isShown() || (mEdgeGlowLeft.isFinished() && mEdgeGlowRight.isFinished()))) {
            mIsPageInTransition = false;
            onPageEndTransition();
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        pageEndTransition();
        super.onVisibilityAggregated(isVisible);
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
        mCurrentPageScrollDiff = 0;
        AccessibilityManagerCompat.sendTestProtocolEventToTest(getContext(),
                SCROLL_FINISHED_MESSAGE);
        AccessibilityManagerCompat.sendCustomAccessibilityEvent(getPageAt(mCurrentPage),
                AccessibilityEvent.TYPE_VIEW_FOCUSED, null);
        if (mOnPageTransitionEndCallback != null) {
            mOnPageTransitionEndCallback.run();
            mOnPageTransitionEndCallback = null;
        }
    }

    /**
     * Sets a callback to run once when the scrolling finishes. If there is currently
     * no page in transition, then the callback is called immediately.
     */
    public void setOnPageTransitionEndCallback(@Nullable Runnable callback) {
        if (mIsPageInTransition || callback == null) {
            mOnPageTransitionEndCallback = callback;
        } else {
            callback.run();
        }
    }

    @Override
    public void scrollTo(int x, int y) {
        x = Utilities.boundToRange(x,
                mOrientationHandler.getPrimaryValue(mMinScroll, 0), mMaxScroll);
        y = Utilities.boundToRange(y,
                mOrientationHandler.getPrimaryValue(0, mMinScroll), mMaxScroll);
        super.scrollTo(x, y);
    }

    private void sendScrollAccessibilityEvent() {
        if (isObservedEventType(getContext(), AccessibilityEvent.TYPE_VIEW_SCROLLED)) {
            if (mCurrentPage != getNextPage()) {
                AccessibilityEvent ev =
                        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED);
                ev.setScrollable(true);
                ev.setScrollX(getScrollX());
                ev.setScrollY(getScrollY());
                mOrientationHandler.setMaxScroll(ev, mMaxScroll);
                sendAccessibilityEventUnchecked(ev);
            }
        }
    }

    protected void announcePageForAccessibility() {
        if (isAccessibilityEnabled(getContext())) {
            // Notify the user when the page changes
            announceForAccessibility(getCurrentPageDescription());
        }
    }

    protected boolean computeScrollHelper() {
        if (mScroller.computeScrollOffset()) {
            // Don't bother scrolling if the page does not need to be moved
            int oldPos = mOrientationHandler.getPrimaryScroll(this);
            int newPos = mScroller.getCurrX();
            if (oldPos != newPos) {
                mOrientationHandler.setPrimary(this, VIEW_SCROLL_TO, mScroller.getCurrX());
            }

            if (mAllowOverScroll) {
                if (newPos < mMinScroll && oldPos >= mMinScroll) {
                    mEdgeGlowLeft.onAbsorb((int) mScroller.getCurrVelocity());
                    abortScrollerAnimation(false);
                    onEdgeAbsorbingScroll();
                } else if (newPos > mMaxScroll && oldPos <= mMaxScroll) {
                    mEdgeGlowRight.onAbsorb((int) mScroller.getCurrVelocity());
                    abortScrollerAnimation(false);
                    onEdgeAbsorbingScroll();
                }
            }

            // If the scroller has scrolled to the final position and there is no edge effect, then
            // finish the scroller to skip waiting for additional settling
            int finalPos = mOrientationHandler.getPrimaryValue(mScroller.getFinalX(),
                    mScroller.getFinalY());
            if (newPos == finalPos && mEdgeGlowLeft.isFinished() && mEdgeGlowRight.isFinished()) {
                abortScrollerAnimation(false);
            }

            invalidate();
            return true;
        } else if (mNextPage != INVALID_PAGE) {
            sendScrollAccessibilityEvent();
            int prevPage = mCurrentPage;
            mCurrentPage = validateNewPage(mNextPage);
            mCurrentScrollOverPage = mCurrentPage;
            mNextPage = INVALID_PAGE;
            notifyPageSwitchListener(prevPage);

            // We don't want to trigger a page end moving unless the page has settled
            // and the user has stopped scrolling
            if (!mIsBeingDragged) {
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

    private void updateVelocityValues() {
        Resources res = getResources();
        mFlingThresholdVelocity = res.getDimensionPixelSize(R.dimen.fling_threshold_velocity);
        mEasyFlingThresholdVelocity =
                res.getDimensionPixelSize(R.dimen.easy_fling_threshold_velocity);
        mMinFlingVelocity = res.getDimensionPixelSize(R.dimen.min_fling_velocity);
        mMinSnapVelocity = res.getDimensionPixelSize(R.dimen.min_page_snap_velocity);
        mPageSnapAnimationDuration = res.getInteger(R.integer.config_pageSnapAnimationDuration);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateVelocityValues();
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

    private int getPageWidthSize(int widthSize) {
        // It's necessary to add the padding back because it is remove when measuring children,
        // like when MeasureSpec.getSize in CellLayout.
        return (widthSize - mInsets.left - mInsets.right - getPaddingLeft() - getPaddingRight())
                / getPanelCount() + getPaddingLeft() + getPaddingRight();
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
                getPageWidthSize(widthSize), MeasureSpec.EXACTLY);
        int myHeightSpec = MeasureSpec.makeMeasureSpec(
                heightSize - mInsets.top - mInsets.bottom, MeasureSpec.EXACTLY);

        // measureChildren takes accounts for content padding, we only need to care about extra
        // space due to insets.
        measureChildren(myWidthSpec, myHeightSpec);
        setMeasuredDimension(widthSize, heightSize);
    }

    /** Returns true iff this PagedView's scroll amounts are initialized to each page index. */
    protected boolean isPageScrollsInitialized() {
        return mPageScrolls != null && mPageScrolls.length == getChildCount();
    }

    /**
     * Queues the given callback to be run once {@code mPageScrolls} has been initialized.
     */
    public void runOnPageScrollsInitialized(Runnable callback) {
        mOnPageScrollsInitializedCallbacks.add(callback);
        if (isPageScrollsInitialized()) {
            onPageScrollsInitialized();
        }
    }

    protected void onPageScrollsInitialized() {
        for (Runnable callback : mOnPageScrollsInitializedCallbacks) {
            callback.run();
        }
        mOnPageScrollsInitializedCallbacks.clear();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mIsLayoutValid = true;
        final int childCount = getChildCount();
        int[] pageScrolls = mPageScrolls;
        boolean pageScrollChanged = false;
        if (!isPageScrollsInitialized()) {
            pageScrolls = new int[childCount];
            pageScrollChanged = true;
        }

        if (DEBUG) Log.d(TAG, "PagedView.onLayout()");

        pageScrollChanged |= getPageScrolls(pageScrolls, true, SIMPLE_SCROLL_LOGIC);
        mPageScrolls = pageScrolls;

        if (childCount == 0) {
            onPageScrollsInitialized();
            return;
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
                        updateMinAndMaxScrollX();
                    }
                }
            });
        } else {
            updateMinAndMaxScrollX();
        }

        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < childCount) {
            updateCurrentPageScroll();
            mFirstLayout = false;
        }

        if (mScroller.isFinished() && pageScrollChanged) {
            // TODO(b/246283207): Remove logging once root cause of flake detected.
            if (Utilities.isRunningInTestHarness() && !(this instanceof Workspace)) {
                Log.d("b/246283207", this.getClass().getSimpleName() + "#onLayout() -> "
                        + "if(mScroller.isFinished() && pageScrollChanged) -> getNextPage(): "
                        + getNextPage() + ", getScrollForPage(getNextPage()): "
                        + getScrollForPage(getNextPage()));
            }
            setCurrentPage(getNextPage());
        }
        onPageScrollsInitialized();
    }

    /**
     * Initializes {@code outPageScrolls} with scroll positions for view at that index. The length
     * of {@code outPageScrolls} should be same as the the childCount
     */
    protected boolean getPageScrolls(int[] outPageScrolls, boolean layoutChildren,
            ComputePageScrollsLogic scrollLogic) {
        final int childCount = getChildCount();

        final int startIndex = mIsRtl ? childCount - 1 : 0;
        final int endIndex = mIsRtl ? -1 : childCount;
        final int delta = mIsRtl ? -1 : 1;

        final int pageCenter = mOrientationHandler.getCenterForPage(this, mInsets);

        final int scrollOffsetStart = mOrientationHandler.getScrollOffsetStart(this, mInsets);
        final int scrollOffsetEnd = mOrientationHandler.getScrollOffsetEnd(this, mInsets);
        boolean pageScrollChanged = false;
        int panelCount = getPanelCount();

        for (int i = startIndex, childStart = scrollOffsetStart; i != endIndex; i += delta) {
            final View child = getPageAt(i);
            if (scrollLogic.shouldIncludeView(child)) {
                ChildBounds bounds = mOrientationHandler.getChildBounds(child, childStart,
                    pageCenter, layoutChildren);
                final int primaryDimension = bounds.primaryDimension;
                final int childPrimaryEnd = bounds.childPrimaryEnd;

                // In case the pages are of different width, align the page to left edge for non-RTL
                // or right edge for RTL.
                final int pageScroll =
                        mIsRtl ? childPrimaryEnd - scrollOffsetEnd : childStart - scrollOffsetStart;
                if (outPageScrolls[i] != pageScroll) {
                    pageScrollChanged = true;
                    outPageScrolls[i] = pageScroll;
                }
                childStart += primaryDimension + getChildGap(i, i + delta);

                // This makes sure that the space is added after the page, not after each panel
                int lastPanel = mIsRtl ? 0 : panelCount - 1;
                if (i % panelCount == lastPanel) {
                    childStart += mPageSpacing;
                }
            }
        }

        if (panelCount > 1) {
            for (int i = 0; i < childCount; i++) {
                // In case we have multiple panels, always use left most panel's page scroll for all
                // panels on the screen.
                int adjustedScroll = outPageScrolls[getLeftmostVisiblePageForIndex(i)];
                if (outPageScrolls[i] != adjustedScroll) {
                    outPageScrolls[i] = adjustedScroll;
                    pageScrollChanged = true;
                }
            }
        }
        return pageScrollChanged;
    }

    protected int getChildGap(int fromIndex, int toIndex) {
        return 0;
    }

    protected void updateMinAndMaxScrollX() {
        mMinScroll = computeMinScroll();
        mMaxScroll = computeMaxScroll();
    }

    protected int computeMinScroll() {
        return 0;
    }

    protected int computeMaxScroll() {
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

    public int getPageSpacing() {
        return mPageSpacing;
    }

    private void dispatchPageCountChanged() {
        if (mPageIndicator != null) {
            mPageIndicator.setMarkersCount(getChildCount() / getPanelCount());
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
        runOnPageScrollsInitialized(() -> {
            mCurrentPage = validateNewPage(mCurrentPage);
            mCurrentScrollOverPage = mCurrentPage;
        });
        dispatchPageCountChanged();
    }

    protected int getChildOffset(int index) {
        if (index < 0 || index > getChildCount() - 1) return 0;
        View pageAtIndex = getPageAt(index);
        return mOrientationHandler.getChildStart(pageAtIndex);
    }

    protected int getChildVisibleSize(int index) {
        View layout = getPageAt(index);
        return mOrientationHandler.getMeasuredSize(layout);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int page = indexOfChild(child);
        if (!isVisible(page) || !mScroller.isFinished()) {
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

        int currentPage = getNextPage();
        int closestNeighbourIndex = -1;
        int closestNeighbourDistance = Integer.MAX_VALUE;
        // Find the closest neighbour page
        for (int neighbourPageIndex : getNeighbourPageIndices(direction)) {
            int distance = Math.abs(neighbourPageIndex - currentPage);
            if (closestNeighbourDistance > distance) {
                closestNeighbourDistance = distance;
                closestNeighbourIndex = neighbourPageIndex;
            }
        }
        if (closestNeighbourIndex != -1) {
            View page = getPageAt(closestNeighbourIndex);
            snapToPage(closestNeighbourIndex);
            page.requestFocus(direction);
            return true;
        }

        return false;
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) {
            return;
        }

        // nextPage is more reliable when multiple control movements have been done in a short
        // period of time
        getPageIndices(getNextPage())
                .addAll(getNeighbourPageIndices(direction))
                .forEach(pageIndex ->
                        getPageAt(pageIndex).addFocusables(views, direction, focusableMode));
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
            cancelCurrentPageLongPress();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */

        // Skip touch handling if there are no pages to swipe
        if (getChildCount() <= 0) return false;

        acquireVelocityTrackerAndAddMovement(ev);

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && mIsBeingDragged) {
            return true;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from their original down touch.
                 */
                if (mActivePointerId != INVALID_POINTER) {
                    determineScrollingStart(ev);
                }
                // if mActivePointerId is INVALID_POINTER, then we must have missed an ACTION_DOWN
                // event. in that case, treat the first occurrence of a move event as a ACTION_DOWN
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
                mDownMotionPrimary = mOrientationHandler.getPrimaryDirection(ev, 0);
                mLastMotion = (int) mDownMotionPrimary;
                mTotalMotion = 0;
                mAllowEasyFling = false;
                mActivePointerId = ev.getPointerId(0);
                updateIsBeingDraggedOnTouchDown(ev);
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
        return mIsBeingDragged;
    }

    /**
     * If being flinged and user touches the screen, initiate drag; otherwise don't.
     */
    protected void updateIsBeingDraggedOnTouchDown(MotionEvent ev) {
        // mScroller.isFinished should be false when being flinged.
        final int xDist = Math.abs(mScroller.getFinalX() - mScroller.getCurrX());
        final boolean finishedScrolling = (mScroller.isFinished() || xDist < mPageSlop / 3);

        if (finishedScrolling) {
            mIsBeingDragged = false;
            if (!mScroller.isFinished() && !mFreeScroll) {
                setCurrentPage(getNextPage());
                pageEndTransition();
            }
            mIsBeingDragged = !mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished();
        } else {
            mIsBeingDragged = true;
        }

        // Catch the edge effect if it is active.
        float displacement = mOrientationHandler.getSecondaryValue(ev.getX(), ev.getY())
                / mOrientationHandler.getSecondaryValue(getWidth(), getHeight());
        if (!mEdgeGlowLeft.isFinished()) {
            mEdgeGlowLeft.onPullDistance(0f, 1f - displacement);
        }
        if (!mEdgeGlowRight.isFinished()) {
            mEdgeGlowRight.onPullDistance(0f, displacement);
        }
    }

    public boolean isHandlingTouch() {
        return mIsBeingDragged;
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

        final float primaryDirection = mOrientationHandler.getPrimaryDirection(ev, pointerIndex);
        final int diff = (int) Math.abs(primaryDirection - mLastMotion);
        final int touchSlop = Math.round(touchSlopScale * mTouchSlop);
        boolean moved = diff > touchSlop || ev.getAction() == ACTION_MOVE_ALLOW_EASY_FLING;

        if (moved) {
            // Scroll if the user moved far enough along the X axis
            mIsBeingDragged = true;
            mTotalMotion += Math.abs(mLastMotion - primaryDirection);
            mLastMotion = (int) primaryDirection;
            pageBeginTransition();
            // Stop listening for things like pinches.
            requestDisallowInterceptTouchEvent(true);
        }
    }

    protected void cancelCurrentPageLongPress() {
        // Try canceling the long press. It could also have been scheduled
        // by a distant descendant, so use the mAllowLongPress flag to block
        // everything
        forEachVisiblePage(View::cancelLongPress);
    }

    protected float getScrollProgress(int screenCenter, View v, int page) {
        final int halfScreenSize = getMeasuredWidth() / 2;
        int delta = screenCenter - (getScrollForPage(page) + halfScreenSize);
        int panelCount = getPanelCount();
        int pageCount = getChildCount();

        int adjacentPage = page + panelCount;
        if ((delta < 0 && !mIsRtl) || (delta > 0 && mIsRtl)) {
            adjacentPage = page - panelCount;
        }

        final int totalDistance;
        if (adjacentPage < 0 || adjacentPage > pageCount - 1) {
            totalDistance = (v.getMeasuredWidth() + mPageSpacing) * panelCount;
        } else {
            totalDistance = Math.abs(getScrollForPage(adjacentPage) - getScrollForPage(page));
        }

        float scrollProgress = delta / (totalDistance * 1.0f);
        scrollProgress = Math.min(scrollProgress, MAX_SCROLL_PROGRESS);
        scrollProgress = Math.max(scrollProgress, -MAX_SCROLL_PROGRESS);
        return scrollProgress;
    }

    public int getScrollForPage(int index) {
        if (!isPageScrollsInitialized() || index >= mPageScrolls.length || index < 0) {
            return 0;
        } else {
            return mPageScrolls[index];
        }
    }

    // While layout transitions are occurring, a child's position may stray from its baseline
    // position. This method returns the magnitude of this stray at any given time.
    public int getLayoutTransitionOffsetForPage(int index) {
        if (!isPageScrollsInitialized() || index >= mPageScrolls.length || index < 0) {
            return 0;
        } else {
            View child = getChildAt(index);

            int scrollOffset = mIsRtl ? getPaddingRight() : getPaddingLeft();
            int baselineX = mPageScrolls[index] + scrollOffset;
            return (int) (child.getX() - baselineX);
        }
    }

    public void setEnableFreeScroll(boolean freeScroll) {
        if (mFreeScroll == freeScroll) {
            return;
        }

        boolean wasFreeScroll = mFreeScroll;
        mFreeScroll = freeScroll;

        if (mFreeScroll) {
            setCurrentPage(getNextPage());
        } else if (wasFreeScroll) {
            if (getScrollForPage(getNextPage()) != getScrollX()) {
                snapToPage(getNextPage());
            }
        }
    }

    protected void setEnableOverscroll(boolean enable) {
        mAllowOverScroll = enable;
    }

    protected boolean isSignificantMove(float absoluteDelta, int pageOrientedSize) {
        return absoluteDelta > pageOrientedSize * SIGNIFICANT_MOVE_THRESHOLD;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Skip touch handling if there are no pages to swipe
        if (getChildCount() <= 0) return false;

        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            updateIsBeingDraggedOnTouchDown(ev);

            /*
             * If being flinged and user touches, stop the fling. isFinished
             * will be false if being flinged.
             */
            if (!mScroller.isFinished()) {
                abortScrollerAnimation(false);
            }

            // Remember where the motion event started
            mDownMotionX = ev.getX();
            mDownMotionY = ev.getY();
            mDownMotionPrimary = mOrientationHandler.getPrimaryDirection(ev, 0);
            mLastMotion = (int) mDownMotionPrimary;
            mTotalMotion = 0;
            mAllowEasyFling = false;
            mActivePointerId = ev.getPointerId(0);
            if (mIsBeingDragged) {
                pageBeginTransition();
            }
            break;

        case ACTION_MOVE_ALLOW_EASY_FLING:
            // Start scrolling immediately
            determineScrollingStart(ev);
            mAllowEasyFling = true;
            break;

        case MotionEvent.ACTION_MOVE:
            if (mIsBeingDragged) {
                // Scroll to follow the motion event
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);

                if (pointerIndex == -1) return true;
                int oldScroll = mOrientationHandler.getPrimaryScroll(this);
                int dx = (int) ev.getX(pointerIndex);
                int dy = (int) ev.getY(pointerIndex);

                int direction = mOrientationHandler.getPrimaryValue(dx, dy);
                int delta = mLastMotion - direction;

                int width = getWidth();
                int height = getHeight();
                float size = mOrientationHandler.getPrimaryValue(width, height);
                float displacement = (width == 0 || height == 0) ? 0
                        : (float) mOrientationHandler.getSecondaryValue(dx, dy)
                                / mOrientationHandler.getSecondaryValue(width, height);
                mTotalMotion += Math.abs(delta);

                if (mAllowOverScroll) {
                    int consumed = 0;
                    if (delta < 0 && mEdgeGlowRight.getDistance() != 0f) {
                        consumed = Math.round(size *
                                mEdgeGlowRight.onPullDistance(delta / size, displacement));
                    } else if (delta > 0 && mEdgeGlowLeft.getDistance() != 0f) {
                        consumed = Math.round(-size *
                                mEdgeGlowLeft.onPullDistance(-delta / size, 1 - displacement));
                    }
                    delta -= consumed;
                }
                delta /= mOrientationHandler.getPrimaryScale(this);

                // Only scroll and update mLastMotionX if we have moved some discrete amount.  We
                // keep the remainder because we are actually testing if we've moved from the last
                // scrolled position (which is discrete).
                mLastMotion = direction;

                if (delta != 0) {
                    mOrientationHandler.setPrimary(this, VIEW_SCROLL_BY, delta);

                    if (mAllowOverScroll) {
                        final float pulledToX = oldScroll + delta;

                        if (pulledToX < mMinScroll) {
                            mEdgeGlowLeft.onPullDistance(-delta / size, 1.f - displacement);
                            if (!mEdgeGlowRight.isFinished()) {
                                mEdgeGlowRight.onRelease();
                            }
                        } else if (pulledToX > mMaxScroll) {
                            mEdgeGlowRight.onPullDistance(delta / size, displacement);
                            if (!mEdgeGlowLeft.isFinished()) {
                                mEdgeGlowLeft.onRelease();
                            }
                        }

                        if (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished()) {
                            postInvalidateOnAnimation();
                        }
                    }

                } else {
                    awakenScrollBars();
                }
            } else {
                determineScrollingStart(ev);
            }
            break;

        case MotionEvent.ACTION_UP:
            if (mIsBeingDragged) {
                final int activePointerId = mActivePointerId;
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) return true;

                final float primaryDirection = mOrientationHandler.getPrimaryDirection(ev,
                    pointerIndex);
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

                int velocity = (int) mOrientationHandler.getPrimaryVelocity(velocityTracker,
                        mActivePointerId);
                float delta = primaryDirection - mDownMotionPrimary;

                View current = getPageAt(mCurrentPage);
                if (current == null) {
                    Log.e(TAG, "current page was null. this should not happen.");
                    return true;
                }

                int pageOrientedSize = (int) (mOrientationHandler.getMeasuredSize(current)
                        * mOrientationHandler.getPrimaryScale(this));
                boolean isSignificantMove = isSignificantMove(Math.abs(delta), pageOrientedSize);

                mTotalMotion += Math.abs(mLastMotion - primaryDirection);
                boolean passedSlop = mAllowEasyFling || mTotalMotion > mPageSlop;
                boolean isFling = passedSlop && shouldFlingForVelocity(velocity);
                boolean isDeltaLeft = mIsRtl ? delta > 0 : delta < 0;
                boolean isVelocityLeft = mIsRtl ? velocity > 0 : velocity < 0;
                if (DEBUG_FAILED_QUICKSWITCH && !isFling && mAllowEasyFling) {
                    Log.d("Quickswitch", "isFling=false vel=" + velocity
                            + " threshold=" + mEasyFlingThresholdVelocity);
                }

                if (!mFreeScroll) {
                    // In the case that the page is moved far to one direction and then is flung
                    // in the opposite direction, we use a threshold to determine whether we should
                    // just return to the starting page, or if we should skip one further.
                    boolean returnToOriginalPage = false;
                    if (Math.abs(delta) > pageOrientedSize * RETURN_TO_ORIGINAL_PAGE_THRESHOLD &&
                            Math.signum(velocity) != Math.signum(delta) && isFling) {
                        returnToOriginalPage = true;
                    }

                    int finalPage;
                    // We give flings precedence over large moves, which is why we short-circuit our
                    // test for a large move if a fling has been registered. That is, a large
                    // move to the left and fling to the right will register as a fling to the right.

                    if (((isSignificantMove && !isDeltaLeft && !isFling) ||
                            (isFling && !isVelocityLeft)) && mCurrentPage > 0) {
                        finalPage = returnToOriginalPage
                                ? mCurrentPage : mCurrentPage - getPanelCount();
                        runOnPageScrollsInitialized(
                                () -> snapToPageWithVelocity(finalPage, velocity));
                    } else if (((isSignificantMove && isDeltaLeft && !isFling) ||
                            (isFling && isVelocityLeft)) &&
                            mCurrentPage < getChildCount() - 1) {
                        finalPage = returnToOriginalPage
                                ? mCurrentPage : mCurrentPage + getPanelCount();
                        runOnPageScrollsInitialized(
                                () -> snapToPageWithVelocity(finalPage, velocity));
                    } else {
                        runOnPageScrollsInitialized(this::snapToDestination);
                    }
                } else {
                    if (!mScroller.isFinished()) {
                        abortScrollerAnimation(true);
                    }

                    int initialScroll = mOrientationHandler.getPrimaryScroll(this);
                    int maxScroll = mMaxScroll;
                    int minScroll = mMinScroll;

                    if (((initialScroll >= maxScroll) && (isVelocityLeft || !isFling)) ||
                        ((initialScroll <= minScroll) && (!isVelocityLeft || !isFling))) {
                        mScroller.springBack(initialScroll, 0, minScroll, maxScroll, 0, 0);
                        mNextPage = getDestinationPage();
                    } else {
                        int velocity1 = -velocity;
                        // Continue a scroll or fling in progress
                        mScroller.fling(initialScroll, 0, velocity1, 0, minScroll, maxScroll, 0, 0,
                                Math.round(getWidth() * 0.5f * OVERSCROLL_DAMP_FACTOR), 0);

                        int finalPos = mScroller.getFinalX();
                        mNextPage = getDestinationPage(finalPos);
                        runOnPageScrollsInitialized(this::onNotSnappingToPageInFreeScroll);
                    }
                    invalidate();
                }
            }

            mEdgeGlowLeft.onRelease();
            mEdgeGlowRight.onRelease();
            // End any intermediate reordering states
            resetTouchState();
            break;

        case MotionEvent.ACTION_CANCEL:
            if (mIsBeingDragged) {
                runOnPageScrollsInitialized(this::snapToDestination);
            }
            mEdgeGlowLeft.onRelease();
            mEdgeGlowRight.onRelease();
            resetTouchState();
            break;

        case MotionEvent.ACTION_POINTER_UP:
            onSecondaryPointerUp(ev);
            releaseVelocityTracker();
            break;
        }

        return true;
    }

    protected void onNotSnappingToPageInFreeScroll() { }

    /**
     * Called when the view edges absorb part of the scroll. Subclasses can override this
     * to provide custom behavior during animation.
     */
    protected void onEdgeAbsorbingScroll() {
    }

    /**
     * Called when the current page closest to the center of the screen changes as part of the
     * scroll. Subclasses can override this to provide custom behavior during scroll.
     */
    protected void onScrollOverPageChanged() {
    }

    protected boolean shouldFlingForVelocity(int velocity) {
        float threshold = mAllowEasyFling ? mEasyFlingThresholdVelocity : mFlingThresholdVelocity;
        return Math.abs(velocity) > threshold;
    }

    protected void resetTouchState() {
        releaseVelocityTracker();
        mIsBeingDragged = false;
        mActivePointerId = INVALID_POINTER;
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
                    if (!canScroll(Math.abs(vscroll), Math.abs(hscroll))) {
                        return false;
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

    /**
     * Returns true if the paged view can scroll for the provided vertical and horizontal
     * scroll values
     */
    protected boolean canScroll(float absVScroll, float absHScroll) {
        ActivityContext ac = ActivityContext.lookupContext(getContext());
        return (ac == null || AbstractFloatingView.getTopOpenView(ac) == null);
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
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mDownMotionPrimary = mOrientationHandler.getPrimaryDirection(ev, newPointerIndex);
            mLastMotion = (int) mDownMotionPrimary;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (!shouldHandleRequestChildFocus()) {
            return;
        }
        // In case the device is controlled by a controller, mCurrentPage isn't updated properly
        // which results in incorrect navigation
        int nextPage = getNextPage();
        if (nextPage != mCurrentPage) {
            setCurrentPage(nextPage);
        }

        int page = indexOfChild(child);
        if (page >= 0 && !isVisible(page) && !isInTouchMode()) {
            snapToPage(page);
        }
    }

    protected boolean shouldHandleRequestChildFocus() {
        return true;
    }

    public int getDestinationPage() {
        return getDestinationPage(mOrientationHandler.getPrimaryScroll(this));
    }

    protected int getDestinationPage(int primaryScroll) {
        return getPageNearestToCenterOfScreen(primaryScroll);
    }

    public int getPageNearestToCenterOfScreen() {
        return getPageNearestToCenterOfScreen(mOrientationHandler.getPrimaryScroll(this));
    }

    private int getPageNearestToCenterOfScreen(int primaryScroll) {
        int screenCenter = getScreenCenter(primaryScroll);
        int minDistanceFromScreenCenter = Integer.MAX_VALUE;
        int minDistanceFromScreenCenterIndex = -1;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            int distanceFromScreenCenter = Math.abs(
                    getDisplacementFromScreenCenter(i, screenCenter));
            if (distanceFromScreenCenter < minDistanceFromScreenCenter) {
                minDistanceFromScreenCenter = distanceFromScreenCenter;
                minDistanceFromScreenCenterIndex = i;
            }
        }
        return minDistanceFromScreenCenterIndex;
    }

    private int getDisplacementFromScreenCenter(int childIndex, int screenCenter) {
        int childSize = getChildVisibleSize(childIndex);
        int halfChildSize = (childSize / 2);
        int childCenter = getChildOffset(childIndex) + halfChildSize;
        return childCenter - screenCenter;
    }

    protected int getDisplacementFromScreenCenter(int childIndex) {
        int primaryScroll = mOrientationHandler.getPrimaryScroll(this);
        int screenCenter = getScreenCenter(primaryScroll);
        return getDisplacementFromScreenCenter(childIndex, screenCenter);
    }

    protected int getScreenCenter(int primaryScroll) {
        float primaryScale = mOrientationHandler.getPrimaryScale(this);
        float primaryPivot =  mOrientationHandler.getPrimaryValue(getPivotX(), getPivotY());
        int pageOrientationSize = mOrientationHandler.getMeasuredSize(this);
        return Math.round(primaryScroll + (pageOrientationSize / 2f - primaryPivot) / primaryScale
                + primaryPivot);
    }

    protected void snapToDestination() {
        snapToPage(getDestinationPage(), mPageSnapAnimationDuration);
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
        int halfScreenSize = mOrientationHandler.getMeasuredSize(this) / 2;

        final int newLoc = getScrollForPage(whichPage);
        int delta = newLoc - mOrientationHandler.getPrimaryScroll(this);
        int duration = 0;

        if (Math.abs(velocity) < mMinFlingVelocity) {
            // If the velocity is low enough, then treat this more as an automatic page advance
            // as opposed to an apparent physical response to flinging
            return snapToPage(whichPage, mPageSnapAnimationDuration);
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
        return snapToPage(whichPage, mPageSnapAnimationDuration);
    }

    public boolean snapToPageImmediately(int whichPage) {
        return snapToPage(whichPage, mPageSnapAnimationDuration, true);
    }

    public boolean snapToPage(int whichPage, int duration) {
        return snapToPage(whichPage, duration, false);
    }

    protected boolean snapToPage(int whichPage, int duration, boolean immediate) {
        whichPage = validateNewPage(whichPage);

        int newLoc = getScrollForPage(whichPage);
        final int delta = newLoc - mOrientationHandler.getPrimaryScroll(this);
        return snapToPage(whichPage, delta, duration, immediate);
    }

    protected boolean snapToPage(int whichPage, int delta, int duration) {
        return snapToPage(whichPage, delta, duration, false);
    }

    protected boolean snapToPage(int whichPage, int delta, int duration, boolean immediate) {
        if (mFirstLayout) {
            setCurrentPage(whichPage);
            return false;
        }

        if (FeatureFlags.IS_STUDIO_BUILD && !Utilities.isRunningInTestHarness()) {
            duration *= Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.WINDOW_ANIMATION_SCALE, 1);
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

        mScroller.startScroll(mOrientationHandler.getPrimaryScroll(this), 0, delta, 0, duration);
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
            snapToPage(getNextPage() - getPanelCount());
            return true;
        }
        return mAllowOverScroll;
    }

    public boolean scrollRight() {
        if (getNextPage() < getChildCount() - 1) {
            snapToPage(getNextPage() + getPanelCount());
            return true;
        }
        return mAllowOverScroll;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        if (mScroller.isFinished()) {
            // This was not caused by the scroller, skip it.
            return;
        }
        int newDestinationPage = getDestinationPage();
        if (newDestinationPage >= 0 && newDestinationPage != mCurrentScrollOverPage) {
            mCurrentScrollOverPage = newDestinationPage;
            onScrollOverPageChanged();
        }
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
        info.setScrollable(getPageCount() > 0);
        int primaryScroll = mOrientationHandler.getPrimaryScroll(this);
        if (getCurrentPage() < getPageCount() - getPanelCount()
                || (getCurrentPage() == getPageCount() - getPanelCount()
                && primaryScroll != getScrollForPage(getPageCount() - getPanelCount()))) {
            info.addAction(pagesFlipped ?
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
                    : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(mIsRtl ?
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT);
        }
        if (getCurrentPage() > 0
                || (getCurrentPage() == 0 && primaryScroll != getScrollForPage(0))) {
            info.addAction(pagesFlipped ?
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                    : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            info.addAction(mIsRtl ?
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT);
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
        event.setScrollable(mAllowOverScroll || getPageCount() > 1);
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
            } break;
            case android.R.id.accessibilityActionPageRight: {
                if (!mIsRtl) {
                    return scrollRight();
                } else {
                    return scrollLeft();
                }
            }
            case android.R.id.accessibilityActionPageLeft: {
                if (!mIsRtl) {
                    return scrollLeft();
                } else {
                    return scrollRight();
                }
            }
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

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        drawEdgeEffect(canvas);
        pageEndTransition();
    }

    protected void drawEdgeEffect(Canvas canvas) {
        if (mAllowOverScroll && (!mEdgeGlowRight.isFinished() || !mEdgeGlowLeft.isFinished())) {
            final int width = getWidth();
            final int height = getHeight();
            if (!mEdgeGlowLeft.isFinished()) {
                final int restoreCount = canvas.save();
                canvas.rotate(-90);
                canvas.translate(-height, Math.min(mMinScroll, getScrollX()));
                mEdgeGlowLeft.setSize(height, width);
                if (mEdgeGlowLeft.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowRight.isFinished()) {
                final int restoreCount = canvas.save();
                canvas.rotate(90, width, 0);
                canvas.translate(width, -(Math.max(mMaxScroll, getScrollX())));

                mEdgeGlowRight.setSize(height, width);
                if (mEdgeGlowRight.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }
}
