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
package com.android.launcher3.views;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link LinearLayout} container which allows scrolling parts of its content based on the
 * scroll of a different view. Views which are marked as sticky are not scrolled, giving the
 * illusion of a sticky header.
 */
public class StickyHeaderLayout extends LinearLayout implements
        RecyclerView.OnChildAttachStateChangeListener {

    private static final FloatProperty<StickyHeaderLayout> SCROLL_OFFSET =
            new FloatProperty<StickyHeaderLayout>("scrollAnimOffset") {
                @Override
                public void setValue(StickyHeaderLayout view, float offset) {
                    view.mScrollOffset = offset;
                    view.updateHeaderScroll();
                }

                @Override
                public Float get(StickyHeaderLayout view) {
                    return view.mScrollOffset;
                }
            };

    private static final MotionEventProxyMethod INTERCEPT_PROXY = ViewGroup::onInterceptTouchEvent;
    private static final MotionEventProxyMethod TOUCH_PROXY = ViewGroup::onTouchEvent;

    private RecyclerView mCurrentRecyclerView;
    private EmptySpaceView mCurrentEmptySpaceView;

    private float mLastScroll = 0;
    private float mScrollOffset = 0;
    private Animator mOffsetAnimator;

    private boolean mShouldForwardToRecyclerView = false;
    private int mHeaderHeight;

    public StickyHeaderLayout(Context context) {
        this(context, /* attrs= */ null);
    }

    public StickyHeaderLayout(Context context, AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public StickyHeaderLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, /* defStyleRes= */ 0);
    }

    public StickyHeaderLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Sets the recycler view, this sticky header should track
     */
    public void setCurrentRecyclerView(RecyclerView currentRecyclerView) {
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
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            MyLayoutParams lp = (MyLayoutParams) child.getLayoutParams();
            child.setTranslationY(Math.max(mLastScroll, lp.scrollLimit));
        }
    }

    private float getCurrentScroll() {
        return mScrollOffset + (mCurrentEmptySpaceView == null ? 0 : mCurrentEmptySpaceView.getY());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mHeaderHeight = getMeasuredHeight();
        if (mCurrentEmptySpaceView != null) {
            mCurrentEmptySpaceView.setFixedHeight(mHeaderHeight);
        }
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

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return (mShouldForwardToRecyclerView = proxyMotionEvent(event, INTERCEPT_PROXY))
                || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mShouldForwardToRecyclerView && proxyMotionEvent(event, TOUCH_PROXY)
                || super.onTouchEvent(event);
    }

    private boolean proxyMotionEvent(MotionEvent event, MotionEventProxyMethod method) {
        float dx = mCurrentRecyclerView.getLeft() - getLeft();
        float dy = mCurrentRecyclerView.getTop() - getTop();
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

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // Update various stick parameters
        int count = getChildCount();
        int stickyHeaderHeight = 0;
        for (int i = 0; i < count; i++) {
            View v = getChildAt(i);
            MyLayoutParams lp = (MyLayoutParams) v.getLayoutParams();
            if (lp.sticky) {
                lp.scrollLimit = -v.getTop() + stickyHeaderHeight;
                stickyHeaderHeight += v.getHeight();
            } else {
                lp.scrollLimit = Integer.MIN_VALUE;
            }
        }
        updateHeaderScroll();
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MyLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        return new MyLayoutParams(lp.width, lp.height);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MyLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MyLayoutParams;
    }

    /**
     * Return a list of all the children that have the sticky layout param set.
     */
    public List<View> getStickyChildren() {
        List<View> stickyChildren = new ArrayList<>();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View v = getChildAt(i);
            MyLayoutParams lp = (MyLayoutParams) v.getLayoutParams();
            if (lp.sticky) {
                stickyChildren.add(v);
            }
        }
        return stickyChildren;
    }

    private static class MyLayoutParams extends LayoutParams {

        public final boolean sticky;
        public int scrollLimit;

        MyLayoutParams(int width, int height) {
            super(width, height);
            sticky = false;
        }

        MyLayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.StickyScroller_Layout);
            sticky = a.getBoolean(R.styleable.StickyScroller_Layout_layout_sticky, false);
            a.recycle();
        }
    }

    private interface MotionEventProxyMethod {

        boolean proxyEvent(ViewGroup view, MotionEvent event);
    }

    /**
     * Empty view which allows listening for 'Y' changes
     */
    public static class EmptySpaceView extends View {

        private Runnable mOnYChangeCallback;
        private int mHeight = 0;

        public EmptySpaceView(Context context) {
            super(context);
            animate().setUpdateListener(v -> notifyYChanged());
        }

        /**
         * Sets the height for the empty view
         * @return true if the height changed, false otherwise
         */
        public boolean setFixedHeight(int height) {
            if (mHeight != height) {
                mHeight = height;
                requestLayout();
                return true;
            }
            return false;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, makeMeasureSpec(mHeight, EXACTLY));
        }

        public void setOnYChangeCallback(Runnable callback) {
            mOnYChangeCallback = callback;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            notifyYChanged();
        }

        @Override
        public void offsetTopAndBottom(int offset) {
            super.offsetTopAndBottom(offset);
            notifyYChanged();
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            notifyYChanged();
        }

        private void notifyYChanged() {
            if (mOnYChangeCallback != null) {
                mOnYChangeCallback.run();
            }
        }
    }
}
