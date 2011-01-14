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

package com.android.launcher2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeMap;
import java.util.TreeSet;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Checkable;
import android.widget.Scroller;

import com.android.launcher.R;

/**
 * An abstraction of the original Workspace which supports browsing through a
 * sequential list of "pages"
 */
public abstract class PagedView extends ViewGroup {
    private static final String TAG = "PagedView";
    protected static final int INVALID_PAGE = -1;

    // the min drag distance for a fling to register, to prevent random page shifts
    private static final int MIN_LENGTH_FOR_FLING = 25;
    // The min drag distance to trigger a page shift (regardless of velocity)
    private static final int MIN_LENGTH_FOR_MOVE = 200;

    private static final int PAGE_SNAP_ANIMATION_DURATION = 550;
    protected static final float NANOTIME_DIV = 1000000000.0f;

    private static final float OVERSCROLL_DAMP_FACTOR = 0.08f;
    private static final int MINIMUM_SNAP_VELOCITY = 2200;
    private static final int MIN_FLING_VELOCITY = 250;

    // the velocity at which a fling gesture will cause us to snap to the next page
    protected int mSnapVelocity = 500;

    protected float mSmoothingTime;
    protected float mTouchX;

    protected boolean mFirstLayout = true;

    protected int mCurrentPage;
    protected int mNextPage = INVALID_PAGE;
    protected int mRestorePage = -1;
    protected int mMaxScrollX;
    protected Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    private float mDownMotionX;
    protected float mLastMotionX;
    protected float mLastMotionY;
    private int mLastScreenCenter = -1;

    protected final static int TOUCH_STATE_REST = 0;
    protected final static int TOUCH_STATE_SCROLLING = 1;
    protected final static int TOUCH_STATE_PREV_PAGE = 2;
    protected final static int TOUCH_STATE_NEXT_PAGE = 3;
    protected final static float ALPHA_QUANTIZE_LEVEL = 0.0001f;

    protected int mTouchState = TOUCH_STATE_REST;

    protected OnLongClickListener mLongClickListener;

    protected boolean mAllowLongPress = true;

    protected int mTouchSlop;
    private int mPagingTouchSlop;
    private int mMaximumVelocity;
    protected int mPageSpacing;
    protected int mPageLayoutPaddingTop;
    protected int mPageLayoutPaddingBottom;
    protected int mPageLayoutPaddingLeft;
    protected int mPageLayoutPaddingRight;
    protected int mPageLayoutWidthGap;
    protected int mPageLayoutHeightGap;
    protected int mCellCountX;
    protected int mCellCountY;
    protected boolean mCenterPagesVertically;
    protected boolean mAllowOverScroll = true;
    protected int mUnboundedScrollX;

    // parameter that adjusts the layout to be optimized for CellLayouts with that scale factor
    protected float mLayoutScale = 1.0f;

    protected static final int INVALID_POINTER = -1;

    protected int mActivePointerId = INVALID_POINTER;

    private PageSwitchListener mPageSwitchListener;

    private ArrayList<Boolean> mDirtyPageContent;
    private boolean mDirtyPageAlpha;

    // choice modes
    protected static final int CHOICE_MODE_NONE = 0;
    protected static final int CHOICE_MODE_SINGLE = 1;
    // Multiple selection mode is not supported by all Launcher actions atm
    protected static final int CHOICE_MODE_MULTIPLE = 2;

    protected int mChoiceMode;
    private ActionMode mActionMode;

    // NOTE: This is a shared icon cache across all the PagedViews.  Currently it is only used in
    // AllApps and Customize, and allows them to share holographic icons for the application view
    // (which is in both).
    protected static PagedViewIconCache mPageViewIconCache = new PagedViewIconCache();

    // If true, syncPages and syncPageItems will be called to refresh pages
    protected boolean mContentIsRefreshable = true;

    // If true, modify alpha of neighboring pages as user scrolls left/right
    protected boolean mFadeInAdjacentScreens = true;

    // It true, use a different slop parameter (pagingTouchSlop = 2 * touchSlop) for deciding
    // to switch to a new page
    protected boolean mUsePagingTouchSlop = true;

    // If true, the subclass should directly update mScrollX itself in its computeScroll method
    // (SmoothPagedView does this)
    protected boolean mDeferScrollUpdate = false;

    protected boolean mIsPageMoving = false;

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
        mChoiceMode = CHOICE_MODE_NONE;

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.PagedView, defStyle, 0);
        mPageSpacing = a.getDimensionPixelSize(R.styleable.PagedView_pageSpacing, 0);
        mPageLayoutPaddingTop = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingTop, 10);
        mPageLayoutPaddingBottom = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingBottom, 10);
        mPageLayoutPaddingLeft = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingLeft, 10);
        mPageLayoutPaddingRight = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutPaddingRight, 10);
        mPageLayoutWidthGap = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutWidthGap, -1);
        mPageLayoutHeightGap = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutHeightGap, -1);
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
        mScroller = new Scroller(getContext(), new ScrollInterpolator());
        mCurrentPage = 0;
        mCenterPagesVertically = true;

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mPagingTouchSlop = configuration.getScaledPagingTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    public void setPageSwitchListener(PageSwitchListener pageSwitchListener) {
        mPageSwitchListener = pageSwitchListener;
        if (mPageSwitchListener != null) {
            mPageSwitchListener.onPageSwitch(getPageAt(mCurrentPage), mCurrentPage);
        }
    }

    /**
     * Returns the index of the currently displayed page.
     *
     * @return The index of the currently displayed page.
     */
    int getCurrentPage() {
        return mCurrentPage;
    }

    int getPageCount() {
        return getChildCount();
    }

    View getPageAt(int index) {
        return getChildAt(index);
    }

    int getScrollWidth() {
        return getWidth();
    }

    /**
     * Updates the scroll of the current page immediately to its final scroll position.  We use this
     * in CustomizePagedView to allow tabs to share the same PagedView while resetting the scroll of
     * the previous tab page.
     */
    protected void updateCurrentPageScroll() {
        int newX = getChildOffset(mCurrentPage) - getRelativeChildOffset(mCurrentPage);
        scrollTo(newX, 0);
        mScroller.setFinalX(newX);
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
        notifyPageSwitchListener();
        invalidate();
    }

    protected void notifyPageSwitchListener() {
        if (mPageSwitchListener != null) {
            mPageSwitchListener.onPageSwitch(getPageAt(mCurrentPage), mCurrentPage);
        }
    }

    private void pageBeginMoving() {
        mIsPageMoving = true;
        onPageBeginMoving();
    }

    private void pageEndMoving() {
        onPageEndMoving();
        mIsPageMoving = false;
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
        scrollTo(mUnboundedScrollX + x, mScrollY + y);
    }

    @Override
    public void scrollTo(int x, int y) {
        mUnboundedScrollX = x;

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
            super.scrollTo(x, y);
        }

        mTouchX = x;
        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
    }

    // we moved this functionality to a helper function so SmoothPagedView can reuse it
    protected boolean computeScrollHelper() {
        if (mScroller.computeScrollOffset()) {
            mDirtyPageAlpha = true;
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            invalidate();
            return true;
        } else if (mNextPage != INVALID_PAGE) {
            mDirtyPageAlpha = true;
            mCurrentPage = Math.max(0, Math.min(mNextPage, getPageCount() - 1));
            mNextPage = INVALID_PAGE;
            notifyPageSwitchListener();
            // We don't want to trigger a page end moving unless the page has settled
            // and the user has stopped scrolling
            if (mTouchState == TOUCH_STATE_REST) {
                pageEndMoving();
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
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Workspace can only be used in EXACTLY mode.");
        }

        /* Allow the height to be set as WRAP_CONTENT. This allows the particular case
         * of the All apps view on XLarge displays to not take up more space then it needs. Width
         * is still not allowed to be set as WRAP_CONTENT since many parts of the code expect
         * each page to have the same width.
         */
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int maxChildHeight = 0;

        final int verticalPadding = mPaddingTop + mPaddingBottom;

        // The children are given the same width and height as the workspace
        // unless they were set to WRAP_CONTENT
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            // disallowing padding in paged view (just pass 0)
            final View child = getChildAt(i);
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
                MeasureSpec.makeMeasureSpec(widthSize, childWidthMode);
            final int childHeightMeasureSpec =
                MeasureSpec.makeMeasureSpec(heightSize - verticalPadding, childHeightMode);

            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            maxChildHeight = Math.max(maxChildHeight, child.getMeasuredHeight());
        }

        if (heightMode == MeasureSpec.AT_MOST) {
            heightSize = maxChildHeight + verticalPadding;
        }
        if (childCount > 0) {
            mMaxScrollX = getChildOffset(childCount - 1) - getRelativeChildOffset(childCount - 1);
        } else {
            mMaxScrollX = 0;
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    protected void moveToNewPageWithoutMovingCellLayouts(int newCurrentPage) {
        int newX = getChildOffset(newCurrentPage) - getRelativeChildOffset(newCurrentPage);
        int delta = newX - mScrollX;

        final int screenCount = getChildCount();
        for (int i = 0; i < screenCount; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);
            cl.setX(cl.getX() + delta);
        }
        setCurrentPage(newCurrentPage);
    }

    // A layout scale of 1.0f assumes that the CellLayouts, in their unshrunken state, have a
    // scale of 1.0f. A layout scale of 0.8f assumes the CellLayouts have a scale of 0.8f, and
    // tightens the layout accordingly
    public void setLayoutScale(float childrenScale) {
        mLayoutScale = childrenScale;

        // Now we need to do a re-layout, but preserving absolute X and Y coordinates
        int childCount = getChildCount();
        float childrenX[] = new float[childCount];
        float childrenY[] = new float[childCount];
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            childrenX[i] = child.getX();
            childrenY[i] = child.getY();
        }
        onLayout(false, mLeft, mTop, mRight, mBottom);
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            child.setX(childrenX[i]);
            child.setY(childrenY[i]);
        }
        // Also, the page offset has changed  (since the pages are now smaller);
        // update the page offset, but again preserving absolute X and Y coordinates
        moveToNewPageWithoutMovingCellLayouts(mCurrentPage);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getChildCount()) {
            setHorizontalScrollBarEnabled(false);
            int newX = getChildOffset(mCurrentPage) - getRelativeChildOffset(mCurrentPage);
            scrollTo(newX, 0);
            mScroller.setFinalX(newX);
            setHorizontalScrollBarEnabled(true);
            mFirstLayout = false;
        }

        final int verticalPadding = mPaddingTop + mPaddingBottom;
        final int childCount = getChildCount();
        int childLeft = 0;
        if (childCount > 0) {
            childLeft = getRelativeChildOffset(0);
        }

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                final int childWidth = getScaledMeasuredWidth(child);
                final int childHeight = child.getMeasuredHeight();
                int childTop = mPaddingTop;
                if (mCenterPagesVertically) {
                    childTop += ((getMeasuredHeight() - verticalPadding) - childHeight) / 2;
                }

                child.layout(childLeft, childTop,
                        childLeft + child.getMeasuredWidth(), childTop + childHeight);
                childLeft += childWidth + mPageSpacing;
            }
        }
        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getChildCount()) {
            mFirstLayout = false;
        }
    }

    protected void updateAdjacentPagesAlpha() {
        if (mFadeInAdjacentScreens) {
            if (mDirtyPageAlpha || (mTouchState == TOUCH_STATE_SCROLLING) || !mScroller.isFinished()) {
                int halfScreenSize = getMeasuredWidth() / 2;
                int screenCenter = mScrollX + halfScreenSize;
                final int childCount = getChildCount();
                for (int i = 0; i < childCount; ++i) {
                    View layout = (View) getChildAt(i);
                    int childWidth = getScaledMeasuredWidth(layout);
                    int halfChildWidth = (childWidth / 2);
                    int childCenter = getChildOffset(i) + halfChildWidth;

                    // On the first layout, we may not have a width nor a proper offset, so for now
                    // we should just assume full page width (and calculate the offset according to
                    // that).
                    if (childWidth <= 0) {
                        childWidth = getMeasuredWidth();
                        childCenter = (i * childWidth) + (childWidth / 2);
                    }

                    int d = halfChildWidth;
                    int distanceFromScreenCenter = childCenter - screenCenter;
                    if (distanceFromScreenCenter > 0) {
                        if (i > 0) {
                            d += getScaledMeasuredWidth(getChildAt(i - 1)) / 2;
                        }
                    } else {
                        if (i < childCount - 1) {
                            d += getScaledMeasuredWidth(getChildAt(i + 1)) / 2;
                        }
                    }
                    d += mPageSpacing;

                    // Preventing potential divide-by-zero
                    d = Math.max(1, d);

                    float dimAlpha = (float) (Math.abs(distanceFromScreenCenter)) / d;
                    dimAlpha = Math.max(0.0f, Math.min(1.0f, (dimAlpha * dimAlpha)));
                    float alpha = 1.0f - dimAlpha;

                    if (alpha < ALPHA_QUANTIZE_LEVEL) {
                        alpha = 0.0f;
                    } else if (alpha > 1.0f - ALPHA_QUANTIZE_LEVEL) {
                        alpha = 1.0f;
                    }

                    if (Float.compare(alpha, layout.getAlpha()) != 0) {
                        layout.setAlpha(alpha);
                    }
                }
                mDirtyPageAlpha = false;
            }
        }
    }

    protected void screenScrolled(int screenCenter) {
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int halfScreenSize = getMeasuredWidth() / 2;
        int screenCenter = mScrollX + halfScreenSize;

        if (screenCenter != mLastScreenCenter) {
            screenScrolled(screenCenter);
            updateAdjacentPagesAlpha();
            mLastScreenCenter = screenCenter;
        }

        // Find out which screens are visible; as an optimization we only call draw on them
        // As an optimization, this code assumes that all pages have the same width as the 0th
        // page.
        final int pageCount = getChildCount();
        if (pageCount > 0) {
            final int pageWidth = getScaledMeasuredWidth(getChildAt(0));
            final int screenWidth = getMeasuredWidth();
            int x = getRelativeChildOffset(0) + pageWidth;
            int leftScreen = 0;
            int rightScreen = 0;
            while (x <= mScrollX) {
                leftScreen++;
                x += getScaledMeasuredWidth(getChildAt(leftScreen)) + mPageSpacing;
            }
            rightScreen = leftScreen;
            while (x < mScrollX + screenWidth && rightScreen < pageCount) {
                rightScreen++;
                if (rightScreen < pageCount) {
                    x += getScaledMeasuredWidth(getChildAt(rightScreen)) + mPageSpacing;
                }
            }
            rightScreen = Math.min(getChildCount() - 1, rightScreen);

            final long drawingTime = getDrawingTime();
            // Clip to the bounds
            canvas.save();
            canvas.clipRect(mScrollX, mScrollY, mScrollX + mRight - mLeft,
                    mScrollY + mBottom - mTop);

            for (int i = leftScreen; i <= rightScreen; i++) {
                drawChild(canvas, getChildAt(i), drawingTime);
            }
            canvas.restore();
        }
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int page = indexOfChild(child);
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
            v.requestFocus(direction, previouslyFocusedRect);
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
            getPageAt(mCurrentPage).addFocusables(views, direction);
        }
        if (direction == View.FOCUS_LEFT) {
            if (mCurrentPage > 0) {
                getPageAt(mCurrentPage - 1).addFocusables(views, direction);
            }
        } else if (direction == View.FOCUS_RIGHT){
            if (mCurrentPage < getPageCount() - 1) {
                getPageAt(mCurrentPage + 1).addFocusables(views, direction);
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
            final View currentPage = getChildAt(mCurrentPage);
            currentPage.cancelLongPress();
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
                mLastMotionX = x;
                mLastMotionY = y;
                mActivePointerId = ev.getPointerId(0);
                mAllowLongPress = true;

                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                final int xDist = Math.abs(mScroller.getFinalX() - mScroller.getCurrX());
                final boolean finishedScrolling = (mScroller.isFinished() || xDist < mTouchSlop);
                if (finishedScrolling) {
                    mTouchState = TOUCH_STATE_REST;
                    mScroller.abortAnimation();
                } else {
                    mTouchState = TOUCH_STATE_SCROLLING;
                }

                // check if this can be the beginning of a tap on the side of the pages
                // to scroll the current page
                if ((mTouchState != TOUCH_STATE_PREV_PAGE) && !handlePagingClicks() &&
                        (mTouchState != TOUCH_STATE_NEXT_PAGE)) {
                    if (getChildCount() > 0) {
                        int width = getMeasuredWidth();
                        int offset = getRelativeChildOffset(mCurrentPage);
                        if (x < offset - mPageSpacing) {
                            mTouchState = TOUCH_STATE_PREV_PAGE;
                        } else if (x > (width - offset + mPageSpacing)) {
                            mTouchState = TOUCH_STATE_NEXT_PAGE;
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
                onWallpaperTap(ev);
            case MotionEvent.ACTION_CANCEL:
                mTouchState = TOUCH_STATE_REST;
                mAllowLongPress = false;
                mActivePointerId = INVALID_POINTER;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mTouchState != TOUCH_STATE_REST;
    }

    protected void animateClickFeedback(View v, final Runnable r) {
        // animate the view slightly to show click feedback running some logic after it is "pressed"
        Animation anim = AnimationUtils.loadAnimation(getContext(), 
                R.anim.paged_view_click_feedback);
        anim.setAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationRepeat(Animation animation) {
                r.run();
            }
            @Override
            public void onAnimationEnd(Animation animation) {}
        });
        v.startAnimation(anim);
    }

    /*
     * Determines if we should change the touch state to start scrolling after the
     * user moves their touch point too far.
     */
    protected void determineScrollingStart(MotionEvent ev) {
        /*
         * Locally do absolute value. mLastMotionX is set to the y value
         * of the down event.
         */
        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
        final float x = ev.getX(pointerIndex);
        final float y = ev.getY(pointerIndex);
        final int xDiff = (int) Math.abs(x - mLastMotionX);
        final int yDiff = (int) Math.abs(y - mLastMotionY);

        final int touchSlop = mTouchSlop;
        boolean xPaged = xDiff > mPagingTouchSlop;
        boolean xMoved = xDiff > touchSlop;
        boolean yMoved = yDiff > touchSlop;

        if (xMoved || yMoved) {
            if (mUsePagingTouchSlop ? xPaged : xMoved) {
                // Scroll if the user moved far enough along the X axis
                mTouchState = TOUCH_STATE_SCROLLING;
                mLastMotionX = x;
                mTouchX = mScrollX;
                mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                pageBeginMoving();
            }
            // Either way, cancel any pending longpress
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
    }

    protected boolean handlePagingClicks() {
        return false;
    }

    // This curve determines how the effect of scrolling over the limits of the page dimishes
    // as the user pulls further and further from the bounds
    private float overScrollInfluenceCurve(float f) {
        f -= 1.0f;
        return f * f * f + 1.0f;
    }

    protected void overScroll(float amount) {
        int screenSize = getMeasuredWidth();

        float f = (amount / screenSize);

        if (f == 0) return;
        f = f / (Math.abs(f)) * (overScrollInfluenceCurve(Math.abs(f)));

        int overScrollAmount = (int) Math.round(OVERSCROLL_DAMP_FACTOR * f * screenSize);
        if (amount < 0) {
            mScrollX = overScrollAmount;
        } else {
            mScrollX = mMaxScrollX + overScrollAmount;
        }
        invalidate();
    }

    protected float maxOverScroll() {
        // Using the formula in overScroll, assuming that f = 1.0 (which it should generally not
        // exceed). Used to find out how much extra wallpaper we need for the overscroll effect
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
                final int deltaX = (int) (mLastMotionX - x);
                mLastMotionX = x;

                if (deltaX != 0) {
                    mTouchX += deltaX;
                    mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                    if (!mDeferScrollUpdate) {
                        scrollBy(deltaX, 0);
                    } else {
                        invalidate();
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
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityX = (int) velocityTracker.getXVelocity(activePointerId);
                final int deltaX = (int) (x - mDownMotionX);
                boolean isfling = Math.abs(deltaX) > MIN_LENGTH_FOR_FLING;
                boolean isSignificantMove = Math.abs(deltaX) > MIN_LENGTH_FOR_MOVE;

                final int snapVelocity = mSnapVelocity;
                if ((isSignificantMove && deltaX > 0 ||
                        (isfling && velocityX > snapVelocity)) && mCurrentPage > 0) {
                    snapToPageWithVelocity(mCurrentPage - 1, velocityX);
                } else if ((isSignificantMove && deltaX < 0 ||
                        (isfling && velocityX < -snapVelocity)) &&
                        mCurrentPage < getChildCount() - 1) {
                    snapToPageWithVelocity(mCurrentPage + 1, velocityX);
                } else {
                    snapToDestination();
                }
            } else if (mTouchState == TOUCH_STATE_PREV_PAGE && !handlePagingClicks()) {
                // at this point we have not moved beyond the touch slop
                // (otherwise mTouchState would be TOUCH_STATE_SCROLLING), so
                // we can just page
                int nextPage = Math.max(0, mCurrentPage - 1);
                if (nextPage != mCurrentPage) {
                    snapToPage(nextPage);
                } else {
                    snapToDestination();
                }
            } else if (mTouchState == TOUCH_STATE_NEXT_PAGE && !handlePagingClicks()) {
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
                onWallpaperTap(ev);
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
            mLastMotionY = ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
        if (mTouchState == TOUCH_STATE_REST) {
            onWallpaperTap(ev);
        }
    }

    protected void onWallpaperTap(MotionEvent ev) {
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int page = indexOfChild(child);
        if (page >= 0 && !isInTouchMode()) {
            snapToPage(page);
        }
    }

    protected int getChildIndexForRelativeOffset(int relativeOffset) {
        final int childCount = getChildCount();
        int left;
        int right;
        for (int i = 0; i < childCount; ++i) {
            left = getRelativeChildOffset(i);
            right = (left + getScaledMeasuredWidth(getChildAt(i)));
            if (left <= relativeOffset && relativeOffset <= right) {
                return i;
            }
        }
        return -1;
    }

    protected int getRelativeChildOffset(int index) {
        return (getMeasuredWidth() - getChildAt(index).getMeasuredWidth()) / 2;
    }

    protected int getChildOffset(int index) {
        if (getChildCount() == 0)
            return 0;

        int offset = getRelativeChildOffset(0);
        for (int i = 0; i < index; ++i) {
            offset += getScaledMeasuredWidth(getChildAt(i)) + mPageSpacing;
        }
        return offset;
    }

    protected int getScaledMeasuredWidth(View child) {
        return (int) (child.getMeasuredWidth() * mLayoutScale + 0.5f);
    }

    int getPageNearestToCenterOfScreen() {
        int minDistanceFromScreenCenter = getMeasuredWidth();
        int minDistanceFromScreenCenterIndex = -1;
        int screenCenter = mScrollX + (getMeasuredWidth() / 2);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            View layout = (View) getChildAt(i);
            int childWidth = getScaledMeasuredWidth(layout);
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
        snapToPage(getPageNearestToCenterOfScreen(), PAGE_SNAP_ANIMATION_DURATION);
    }

    private static class ScrollInterpolator implements Interpolator {
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
    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    protected void snapToPageWithVelocity(int whichPage, int velocity) {
        whichPage = Math.max(0, Math.min(whichPage, getChildCount() - 1));
        int halfScreenSize = getMeasuredWidth() / 2;

        final int newX = getChildOffset(whichPage) - getRelativeChildOffset(whichPage);
        int delta = newX - mUnboundedScrollX;
        int duration = 0;

        if (Math.abs(velocity) < MIN_FLING_VELOCITY) {
            // If the velocity is low enough, then treat this more as an automatic page advance
            // as opposed to an apparent physical response to flinging
            snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
            return;
        }

        // Here we compute a "distance" that will be used in the computation of the overall
        // snap duration. This is a function of the actual distance that needs to be traveled;
        // we keep this value close to half screen size in order to reduce the variance in snap
        // duration as a function of the distance the page needs to travel.
        float distanceRatio = 1.0f * Math.abs(delta) / 2 * halfScreenSize;
        float distance = halfScreenSize + halfScreenSize *
                distanceInfluenceForSnapDuration(distanceRatio);

        velocity = Math.abs(velocity);
        velocity = Math.max(MINIMUM_SNAP_VELOCITY, velocity);

        // we want the page's snap velocity to approximately match the velocity at which the
        // user flings, so we scale the duration by a value near to the derivative of the scroll
        // interpolator at zero, ie. 5. We use 6 to make it a little slower.
        duration = 6 * Math.round(1000 * Math.abs(distance / velocity));

        snapToPage(whichPage, delta, duration);
    }

    protected void snapToPage(int whichPage) {
        snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
    }

    protected void snapToPage(int whichPage, int duration) {
        whichPage = Math.max(0, Math.min(whichPage, getPageCount() - 1));

        int newX = getChildOffset(whichPage) - getRelativeChildOffset(whichPage);
        final int sX = mUnboundedScrollX;
        final int delta = newX - sX;
        snapToPage(whichPage, delta, duration);
    }

    protected void snapToPage(int whichPage, int delta, int duration) {
        mNextPage = whichPage;

        View focusedChild = getFocusedChild();
        if (focusedChild != null && whichPage != mCurrentPage &&
                focusedChild == getChildAt(mCurrentPage)) {
            focusedChild.clearFocus();
        }

        pageBeginMoving();
        awakenScrollBars(duration);
        if (duration == 0) {
            duration = Math.abs(delta);
        }

        if (!mScroller.isFinished()) mScroller.abortAnimation();
        mScroller.startScroll(mUnboundedScrollX, 0, delta, 0, duration);

        // only load some associated pages
        loadAssociatedPages(mNextPage);
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
                if (vp == getChildAt(i)) {
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

    public void loadAssociatedPages(int page) {
        if (mContentIsRefreshable) {
            final int count = getChildCount();
            if (page < count) {
                int lowerPageBound = getAssociatedLowerPageBound(page);
                int upperPageBound = getAssociatedUpperPageBound(page);
                for (int i = 0; i < count; ++i) {
                    final ViewGroup layout = (ViewGroup) getChildAt(i);
                    final int childCount = layout.getChildCount();
                    if (lowerPageBound <= i && i <= upperPageBound) {
                        if (mDirtyPageContent.get(i)) {
                            syncPageItems(i);
                            mDirtyPageContent.set(i, false);
                        }
                    } else {
                        if (childCount > 0) {
                            layout.removeAllViews();
                        }
                        mDirtyPageContent.set(i, true);
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

    protected void startChoiceMode(int mode, ActionMode.Callback callback) {
        if (isChoiceMode(CHOICE_MODE_NONE)) {
            mChoiceMode = mode;
            mActionMode = startActionMode(callback);
        }
    }

    public void endChoiceMode() {
        if (!isChoiceMode(CHOICE_MODE_NONE)) {
            mChoiceMode = CHOICE_MODE_NONE;
            resetCheckedGrandchildren();
            if (mActionMode != null) mActionMode.finish();
            mActionMode = null;
        }
    }

    protected boolean isChoiceMode(int mode) {
        return mChoiceMode == mode;
    }

    protected ArrayList<Checkable> getCheckedGrandchildren() {
        ArrayList<Checkable> checked = new ArrayList<Checkable>();
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            final ViewGroup layout = (ViewGroup) getChildAt(i);
            final int grandChildCount = layout.getChildCount();
            for (int j = 0; j < grandChildCount; ++j) {
                final View v = layout.getChildAt(j);
                if (v instanceof Checkable && ((Checkable) v).isChecked()) {
                    checked.add((Checkable) v);
                }
            }
        }
        return checked;
    }

    /**
     * If in CHOICE_MODE_SINGLE and an item is checked, returns that item.
     * Otherwise, returns null.
     */
    protected Checkable getSingleCheckedGrandchild() {
        if (mChoiceMode == CHOICE_MODE_SINGLE) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; ++i) {
                final ViewGroup layout = (ViewGroup) getChildAt(i);
                final int grandChildCount = layout.getChildCount();
                for (int j = 0; j < grandChildCount; ++j) {
                    final View v = layout.getChildAt(j);
                    if (v instanceof Checkable && ((Checkable) v).isChecked()) {
                        return (Checkable) v;
                    }
                }
            }
        }
        return null;
    }

    public Object getChosenItem() {
        View checkedView = (View) getSingleCheckedGrandchild();
        if (checkedView != null) {
            return checkedView.getTag();
        }
        return null;
    }

    protected void resetCheckedGrandchildren() {
        // loop through children, and set all of their children to _not_ be checked
        final ArrayList<Checkable> checked = getCheckedGrandchildren();
        for (int i = 0; i < checked.size(); ++i) {
            final Checkable c = checked.get(i);
            c.setChecked(false);
        }
    }

    public void setRestorePage(int restorePage) {
        mRestorePage = restorePage;
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
    public abstract void syncPageItems(int page);

    public void invalidatePageData() {
        if (mContentIsRefreshable) {
            // Update all the pages
            syncPages();

            // Mark each of the pages as dirty
            final int count = getChildCount();
            mDirtyPageContent.clear();
            for (int i = 0; i < count; ++i) {
                mDirtyPageContent.add(true);
            }

            // Use the restore page if necessary
            if (mRestorePage > -1) {
                mCurrentPage = mRestorePage;
                mRestorePage = -1;
            }

            // Load any pages that are necessary for the current window of views
            loadAssociatedPages(mCurrentPage);
            mDirtyPageAlpha = true;
            updateAdjacentPagesAlpha();
            requestLayout();
        }
    }
}
