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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.widget.Scroller;

import com.android.launcher.R;

/**
 * An abstraction of the original Workspace which supports browsing through a
 * sequential list of "pages" (or PagedViewCellLayouts).
 */
public abstract class PagedView extends ViewGroup {
    private static final String TAG = "PagedView";
    private static final int INVALID_SCREEN = -1;

    // the velocity at which a fling gesture will cause us to snap to the next screen
    private static final int SNAP_VELOCITY = 500;

    // the min drag distance for a fling to register, to prevent random screen shifts
    private static final int MIN_LENGTH_FOR_FLING = 50;

    private boolean mFirstLayout = true;

    private int mCurrentScreen;
    private int mNextScreen = INVALID_SCREEN;
    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;

    private float mDownMotionX;
    private float mLastMotionX;
    private float mLastMotionY;

    private final static int TOUCH_STATE_REST = 0;
    private final static int TOUCH_STATE_SCROLLING = 1;
    private final static int TOUCH_STATE_PREV_PAGE = 2;
    private final static int TOUCH_STATE_NEXT_PAGE = 3;

    private int mTouchState = TOUCH_STATE_REST;

    private OnLongClickListener mLongClickListener;

    private boolean mAllowLongPress = true;

    private int mTouchSlop;
    private int mPagingTouchSlop;
    private int mMaximumVelocity;

    private static final int INVALID_POINTER = -1;

    private int mActivePointerId = INVALID_POINTER;

    private ScreenSwitchListener mScreenSwitchListener;

    private boolean mDimmedPagesDirty;
    private final Handler mHandler = new Handler();

    public interface ScreenSwitchListener {
        void onScreenSwitch(View newScreen, int newScreenIndex);
    }

    public PagedView(Context context) {
        this(context, null);
    }

    public PagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setHapticFeedbackEnabled(false);
        initWorkspace();
    }

    /**
     * Initializes various states for this workspace.
     */
    private void initWorkspace() {
        mScroller = new Scroller(getContext());
        mCurrentScreen = 0;

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mPagingTouchSlop = configuration.getScaledPagingTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    public void setScreenSwitchListener(ScreenSwitchListener screenSwitchListener) {
        mScreenSwitchListener = screenSwitchListener;
        if (mScreenSwitchListener != null) {
            mScreenSwitchListener.onScreenSwitch(getScreenAt(mCurrentScreen), mCurrentScreen);
        }
    }

    /**
     * Returns the index of the currently displayed screen.
     *
     * @return The index of the currently displayed screen.
     */
    int getCurrentScreen() {
        return mCurrentScreen;
    }

    int getScreenCount() {
        return getChildCount();
    }

    View getScreenAt(int index) {
        return getChildAt(index);
    }

    int getScrollWidth() {
        return getWidth();
    }

    /**
     * Sets the current screen.
     *
     * @param currentScreen
     */
    void setCurrentScreen(int currentScreen) {
        if (!mScroller.isFinished()) mScroller.abortAnimation();
        if (getChildCount() == 0) return;

        mCurrentScreen = Math.max(0, Math.min(currentScreen, getScreenCount() - 1));
        scrollTo(getChildOffset(mCurrentScreen) - getRelativeChildOffset(mCurrentScreen), 0);

        invalidate();
        notifyScreenSwitchListener();
    }

    private void notifyScreenSwitchListener() {
        if (mScreenSwitchListener != null) {
            mScreenSwitchListener.onScreenSwitch(getScreenAt(mCurrentScreen), mCurrentScreen);
        }
    }

    /**
     * Registers the specified listener on each screen contained in this workspace.
     *
     * @param l The listener used to respond to long clicks.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mLongClickListener = l;
        final int count = getScreenCount();
        for (int i = 0; i < count; i++) {
            getScreenAt(i).setOnLongClickListener(l);
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        } else if (mNextScreen != INVALID_SCREEN) {
            mCurrentScreen = Math.max(0, Math.min(mNextScreen, getScreenCount() - 1));
            notifyScreenSwitchListener();
            mNextScreen = INVALID_SCREEN;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Workspace can only be used in EXACTLY mode.");
        }

        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Workspace can only be used in EXACTLY mode.");
        }

        // The children are given the same width and height as the workspace
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
        }

        setMeasuredDimension(widthSize, heightSize);

        if (mFirstLayout) {
            setHorizontalScrollBarEnabled(false);
            scrollTo(mCurrentScreen * widthSize, 0);
            setHorizontalScrollBarEnabled(true);
            mFirstLayout = false;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int childCount = getChildCount();
        int childLeft = 0;
        if (childCount > 0) {
            childLeft = (getMeasuredWidth() - getChildAt(0).getMeasuredWidth()) / 2;
        }

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                final int childWidth = child.getMeasuredWidth();
                child.layout(childLeft, 0, childLeft + childWidth, child.getMeasuredHeight());
                childLeft += childWidth;
            }
        }
    }

    protected void invalidateDimmedPages() {
        mDimmedPagesDirty = true;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mDimmedPagesDirty || (mTouchState == TOUCH_STATE_SCROLLING) ||
                !mScroller.isFinished()) {
            int screenCenter = mScrollX + (getMeasuredWidth() / 2);
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; ++i) {
                PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(i);
                int childWidth = layout.getMeasuredWidth();
                int halfChildWidth = (childWidth / 2);
                int childCenter = getChildOffset(i) + halfChildWidth;
                int distanceFromScreenCenter = Math.abs(childCenter - screenCenter);
                float dimAlpha = 0.0f;
                if (distanceFromScreenCenter < halfChildWidth) {
                    dimAlpha = 0.0f;
                } else if (distanceFromScreenCenter > childWidth) {
                    dimAlpha = 1.0f;
                } else {
                    dimAlpha = (float) (distanceFromScreenCenter - halfChildWidth) / halfChildWidth;
                    dimAlpha = (dimAlpha * dimAlpha);
                }
                dimAlpha = Math.max(0.0f, Math.min(1.0f, dimAlpha));
                if (Float.compare(dimAlpha, layout.getDimmedBitmapAlpha()) != 0)
                    layout.setDimmedBitmapAlpha(dimAlpha);
            }
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int screen = indexOfChild(child);
        if (screen != mCurrentScreen || !mScroller.isFinished()) {
            snapToScreen(screen);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int focusableScreen;
        if (mNextScreen != INVALID_SCREEN) {
            focusableScreen = mNextScreen;
        } else {
            focusableScreen = mCurrentScreen;
        }
        View v = getScreenAt(focusableScreen);
        if (v != null) {
            v.requestFocus(direction, previouslyFocusedRect);
        }
        return false;
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (direction == View.FOCUS_LEFT) {
            if (getCurrentScreen() > 0) {
                snapToScreen(getCurrentScreen() - 1);
                return true;
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (getCurrentScreen() < getScreenCount() - 1) {
                snapToScreen(getCurrentScreen() + 1);
                return true;
            }
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (mCurrentScreen >= 0 && mCurrentScreen < getScreenCount()) {
            getScreenAt(mCurrentScreen).addFocusables(views, direction);
        }
        if (direction == View.FOCUS_LEFT) {
            if (mCurrentScreen > 0) {
                getScreenAt(mCurrentScreen - 1).addFocusables(views, direction);
            }
        } else if (direction == View.FOCUS_RIGHT){
            if (mCurrentScreen < getScreenCount() - 1) {
                getScreenAt(mCurrentScreen + 1).addFocusables(views, direction);
            }
        }
    }

    /**
     * If one of our descendant views decides that it could be focused now, only
     * pass that along if it's on the current screen.
     *
     * This happens when live folders requery, and if they're off screen, they
     * end up calling requestFocus, which pulls it on screen.
     */
    @Override
    public void focusableViewAvailable(View focused) {
        View current = getScreenAt(mCurrentScreen);
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
            final View currentScreen = getChildAt(mCurrentScreen);
            currentScreen.cancelLongPress();
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
                determineScrollingStart(ev);
                break;
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
                mTouchState = mScroller.isFinished() ? TOUCH_STATE_REST : TOUCH_STATE_SCROLLING;

                // check if this can be the beginning of a tap on the side of the screens
                // to scroll the current page
                if ((mTouchState != TOUCH_STATE_PREV_PAGE) &&
                        (mTouchState != TOUCH_STATE_NEXT_PAGE)) {
                    if (getChildCount() > 0) {
                        int relativeChildLeft = getChildOffset(0);
                        int relativeChildRight = relativeChildLeft + getChildAt(0).getMeasuredWidth();
                        if (x < relativeChildLeft) {
                            mTouchState = TOUCH_STATE_PREV_PAGE;
                        } else if (x > relativeChildRight) {
                            mTouchState = TOUCH_STATE_NEXT_PAGE;
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // Release the drag
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
    private void determineScrollingStart(MotionEvent ev) {
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
            if (xPaged) {
                // Scroll if the user moved far enough along the X axis
                mTouchState = TOUCH_STATE_SCROLLING;
                mLastMotionX = x;
            }
            // Either way, cancel any pending longpress
            if (mAllowLongPress) {
                mAllowLongPress = false;
                // Try canceling the long press. It could also have been scheduled
                // by a distant descendant, so use the mAllowLongPress flag to block
                // everything
                final View currentScreen = getScreenAt(mCurrentScreen);
                currentScreen.cancelLongPress();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

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
            break;

        case MotionEvent.ACTION_MOVE:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                // Scroll to follow the motion event
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float x = ev.getX(pointerIndex);
                final int deltaX = (int) (mLastMotionX - x);
                mLastMotionX = x;

                int sx = getScrollX();
                if (deltaX < 0) {
                    if (sx > 0) {
                        scrollBy(Math.max(-sx, deltaX), 0);
                    }
                } else if (deltaX > 0) {
                    final int lastChildIndex = getChildCount() - 1;
                    final int availableToScroll = getChildOffset(lastChildIndex) -
                        getRelativeChildOffset(lastChildIndex) - sx;
                    if (availableToScroll > 0) {
                        scrollBy(Math.min(availableToScroll, deltaX), 0);
                    }
                } else {
                    awakenScrollBars();
                }
            } else if ((mTouchState == TOUCH_STATE_PREV_PAGE) ||
                    (mTouchState == TOUCH_STATE_NEXT_PAGE)) {
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
                boolean isfling = Math.abs(mDownMotionX - x) > MIN_LENGTH_FOR_FLING;

                if (isfling && velocityX > SNAP_VELOCITY && mCurrentScreen > 0) {
                    snapToScreen(mCurrentScreen - 1);
                } else if (isfling && velocityX < -SNAP_VELOCITY &&
                        mCurrentScreen < getChildCount() - 1) {
                    snapToScreen(mCurrentScreen + 1);
                } else {
                    snapToDestination();
                }

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
            } else if (mTouchState == TOUCH_STATE_PREV_PAGE) {
                // at this point we have not moved beyond the touch slop
                // (otherwise mTouchState would be TOUCH_STATE_SCROLLING), so
                // we can just page
                int nextScreen = Math.max(0, mCurrentScreen - 1);
                if (nextScreen != mCurrentScreen) {
                    snapToScreen(nextScreen);
                } else {
                    snapToDestination();
                }
            } else if (mTouchState == TOUCH_STATE_NEXT_PAGE) {
                // at this point we have not moved beyond the touch slop
                // (otherwise mTouchState would be TOUCH_STATE_SCROLLING), so
                // we can just page
                int nextScreen = Math.min(getChildCount() - 1, mCurrentScreen + 1);
                if (nextScreen != mCurrentScreen) {
                    snapToScreen(nextScreen);
                } else {
                    snapToDestination();
                }
            }
            mTouchState = TOUCH_STATE_REST;
            mActivePointerId = INVALID_POINTER;
            break;

        case MotionEvent.ACTION_CANCEL:
            mTouchState = TOUCH_STATE_REST;
            mActivePointerId = INVALID_POINTER;
            break;

        case MotionEvent.ACTION_POINTER_UP:
            onSecondaryPointerUp(ev);
            break;
        }

        return true;
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
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int screen = indexOfChild(child);
        if (screen >= 0 && !isInTouchMode()) {
            snapToScreen(screen);
        }
    }

    protected int getRelativeChildOffset(int index) {
        return (getMeasuredWidth() - getChildAt(index).getMeasuredWidth()) / 2;
    }

    protected int getChildOffset(int index) {
        if (getChildCount() == 0)
            return 0;

        int offset = getRelativeChildOffset(0);
        for (int i = 0; i < index; ++i) {
            offset += getChildAt(i).getMeasuredWidth();
        }
        return offset;
    }

    protected void snapToDestination() {
        int minDistanceFromScreenCenter = getMeasuredWidth();
        int minDistanceFromScreenCenterIndex = -1;
        int screenCenter = mScrollX + (getMeasuredWidth() / 2);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(i);
            int childWidth = layout.getMeasuredWidth();
            int halfChildWidth = (childWidth / 2);
            int childCenter = getChildOffset(i) + halfChildWidth;
            int distanceFromScreenCenter = Math.abs(childCenter - screenCenter);
            if (distanceFromScreenCenter < minDistanceFromScreenCenter) {
                minDistanceFromScreenCenter = distanceFromScreenCenter;
                minDistanceFromScreenCenterIndex = i;
            }
        }
        snapToScreen(minDistanceFromScreenCenterIndex, 1000);
    }

    void snapToScreen(int whichScreen) {
        snapToScreen(whichScreen, 1000);
    }

    void snapToScreen(int whichScreen, int duration) {
        whichScreen = Math.max(0, Math.min(whichScreen, getScreenCount() - 1));

        mNextScreen = whichScreen;

        int newX = getChildOffset(whichScreen) - getRelativeChildOffset(whichScreen);
        final int sX = getScrollX();
        final int delta = newX - sX;
        awakenScrollBars(duration);
        if (duration == 0) {
            duration = Math.abs(delta);
        }

        if (!mScroller.isFinished()) mScroller.abortAnimation();
        mScroller.startScroll(sX, 0, delta, 0, duration);

        // only load some associated pages
        loadAssociatedPages(mNextScreen);

        invalidate();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final SavedState state = new SavedState(super.onSaveInstanceState());
        state.currentScreen = mCurrentScreen;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        if (savedState.currentScreen != -1) {
            mCurrentScreen = savedState.currentScreen;
        }
    }

    public void scrollLeft() {
        if (mScroller.isFinished()) {
            if (mCurrentScreen > 0) snapToScreen(mCurrentScreen - 1);
        } else {
            if (mNextScreen > 0) snapToScreen(mNextScreen - 1);
        }
    }

    public void scrollRight() {
        if (mScroller.isFinished()) {
            if (mCurrentScreen < getChildCount() -1) snapToScreen(mCurrentScreen + 1);
        } else {
            if (mNextScreen < getChildCount() -1) snapToScreen(mNextScreen + 1);
        }
    }

    public int getScreenForView(View v) {
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

    public static class SavedState extends BaseSavedState {
        int currentScreen = -1;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            currentScreen = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(currentScreen);
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

    private void clearDimmedBitmaps(boolean skipCurrentScreens) {
        final int count = getChildCount();
        if (mCurrentScreen < count) {
            if (skipCurrentScreens) {
                int lowerScreenBound = Math.max(0, mCurrentScreen - 1);
                int upperScreenBound = Math.min(mCurrentScreen + 1, count - 1);
                for (int i = 0; i < count; ++i) {
                    if (i < lowerScreenBound || i > upperScreenBound) {
                        PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(i);
                        layout.clearDimmedBitmap();
                    }
                }
            } else {
                for (int i = 0; i < count; ++i) {
                    PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(i);
                    layout.clearDimmedBitmap();
                }
            }
        }
    }
    Runnable clearLayoutOtherDimmedBitmapsRunnable = new Runnable() {
        @Override
        public void run() {
            if (mScroller.isFinished()) {
                clearDimmedBitmaps(true);
                mHandler.removeMessages(0);
            } else {
                mHandler.postDelayed(clearLayoutOtherDimmedBitmapsRunnable, 50);
            }
        }
    };
    Runnable clearLayoutDimmedBitmapsRunnable = new Runnable() {
        @Override
        public void run() {
            if (mScroller.isFinished()) {
                clearDimmedBitmaps(false);
                mHandler.removeMessages(0);
            } else {
                mHandler.postDelayed(clearLayoutOtherDimmedBitmapsRunnable, 50);
            }
        }
    };

    // called when this paged view is no longer visible
    public void cleanup() {
        // clear all the layout dimmed bitmaps
        mHandler.removeMessages(0);
        mHandler.postDelayed(clearLayoutDimmedBitmapsRunnable, 500);
    }

    public void loadAssociatedPages(int screen) {
        final int count = getChildCount();
        if (screen < count) {
            int lowerScreenBound = Math.max(0, screen - 1);
            int upperScreenBound = Math.min(screen + 1, count - 1);
            boolean hasDimmedBitmap = false;
            for (int i = 0; i < count; ++i) {
                if (lowerScreenBound <= i && i <= upperScreenBound) {
                    syncPageItems(i);
                } else {
                    PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(i);
                    if (layout.getChildCount() > 0) {
                        layout.removeAllViews();
                    }
                    hasDimmedBitmap |= layout.getDimmedBitmapAlpha() > 0.0f;
                }
            }

            if (hasDimmedBitmap) {
                mHandler.removeMessages(0);
                mHandler.postDelayed(clearLayoutOtherDimmedBitmapsRunnable, 500);
            }
        }
    }

    public abstract void syncPages();
    public abstract void syncPageItems(int page);
    public void invalidatePageData() {
        syncPages();
        loadAssociatedPages(mCurrentScreen);
        invalidateDimmedPages();
        requestLayout();
    }
}
