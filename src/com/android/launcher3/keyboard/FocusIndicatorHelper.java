/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.keyboard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Property;
import android.view.View;
import android.view.View.OnFocusChangeListener;

import com.android.launcher3.R;

/**
 * A helper class to draw background of a focused view.
 */
public abstract class FocusIndicatorHelper implements
        OnFocusChangeListener, AnimatorUpdateListener {

    private static final float MIN_VISIBLE_ALPHA = 0.2f;
    private static final long ANIM_DURATION = 150;

    public static final Property<FocusIndicatorHelper, Float> ALPHA =
            new Property<FocusIndicatorHelper, Float>(Float.TYPE, "alpha") {
                @Override
                public void set(FocusIndicatorHelper object, Float value) {
                    object.setAlpha(value);
                }

                @Override
                public Float get(FocusIndicatorHelper object) {
                    return object.mAlpha;
                }
            };

    public static final Property<FocusIndicatorHelper, Float> SHIFT =
            new Property<FocusIndicatorHelper, Float>(
                    Float.TYPE, "shift") {

                @Override
                public void set(FocusIndicatorHelper object, Float value) {
                    object.mShift = value;
                }

                @Override
                public Float get(FocusIndicatorHelper object) {
                    return object.mShift;
                }
            };

    private static final RectEvaluator RECT_EVALUATOR = new RectEvaluator(new Rect());
    private static final Rect sTempRect1 = new Rect();
    private static final Rect sTempRect2 = new Rect();

    private final View mContainer;
    private final Paint mPaint;
    private final int mMaxAlpha;

    private final Rect mDirtyRect = new Rect();
    private boolean mIsDirty = false;

    private View mLastFocusedView;

    private View mCurrentView;
    private View mTargetView;
    /**
     * The fraction indicating the position of the focusRect between {@link #mCurrentView}
     * & {@link #mTargetView}
     */
    private float mShift;

    private ObjectAnimator mCurrentAnimation;
    private float mAlpha;

    public FocusIndicatorHelper(View container) {
        mContainer = container;

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int color = container.getResources().getColor(R.color.focused_background);
        mMaxAlpha = Color.alpha(color);
        mPaint.setColor(0xFF000000 | color);

        setAlpha(0);
        mShift = 0;
    }

    protected void setAlpha(float alpha) {
        mAlpha = alpha;
        mPaint.setAlpha((int) (mAlpha * mMaxAlpha));
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        invalidateDirty();
    }

    protected void invalidateDirty() {
        if (mIsDirty) {
            mContainer.invalidate(mDirtyRect);
            mIsDirty = false;
        }

        Rect newRect = getDrawRect();
        if (newRect != null) {
            mContainer.invalidate(newRect);
        }
    }

    public void draw(Canvas c) {
        if (mAlpha > 0) {
            Rect newRect = getDrawRect();
            if (newRect != null) {
                mDirtyRect.set(newRect);
                c.drawRect(mDirtyRect, mPaint);
                mIsDirty = true;
            }
        }
    }

    private Rect getDrawRect() {
        if (mCurrentView != null && mCurrentView.isAttachedToWindow()) {
            viewToRect(mCurrentView, sTempRect1);

            if (mShift > 0 && mTargetView != null) {
                viewToRect(mTargetView, sTempRect2);
                return RECT_EVALUATOR.evaluate(mShift, sTempRect1, sTempRect2);
            } else {
                return sTempRect1;
            }
        }
        return null;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            endCurrentAnimation();

            if (mAlpha > MIN_VISIBLE_ALPHA) {
                mTargetView = v;

                mCurrentAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(ALPHA, 1),
                        PropertyValuesHolder.ofFloat(SHIFT, 1));
                mCurrentAnimation.addListener(new ViewSetListener(v, true));
            } else {
                setCurrentView(v);

                mCurrentAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(ALPHA, 1));
            }

            mLastFocusedView = v;
        } else {
            if (mLastFocusedView == v) {
                mLastFocusedView = null;
                endCurrentAnimation();
                mCurrentAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(ALPHA, 0));
                mCurrentAnimation.addListener(new ViewSetListener(null, false));
            }
        }

        // invalidate once
        invalidateDirty();

        mLastFocusedView = hasFocus ? v : null;
        if (mCurrentAnimation != null) {
            mCurrentAnimation.addUpdateListener(this);
            mCurrentAnimation.setDuration(ANIM_DURATION).start();
        }
    }

    protected void endCurrentAnimation() {
        if (mCurrentAnimation != null) {
            mCurrentAnimation.cancel();
            mCurrentAnimation = null;
        }
    }

    protected void setCurrentView(View v) {
        mCurrentView = v;
        mShift = 0;
        mTargetView = null;
    }

    /**
     * Gets the position of {@param v} relative to {@link #mContainer}.
     */
    public abstract void viewToRect(View v, Rect outRect);

    private class ViewSetListener extends AnimatorListenerAdapter {
        private final View mViewToSet;
        private final boolean mCallOnCancel;
        private boolean mCalled = false;

        public ViewSetListener(View v, boolean callOnCancel) {
            mViewToSet = v;
            mCallOnCancel = callOnCancel;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            if (!mCallOnCancel) {
                mCalled = true;
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mCalled) {
                setCurrentView(mViewToSet);
                mCalled = true;
            }
        }
    }

    /**
     * Simple subclass which assumes that the target view is a child of the container.
     */
    public static class SimpleFocusIndicatorHelper extends FocusIndicatorHelper {

        public SimpleFocusIndicatorHelper(View container) {
            super(container);
        }

        @Override
        public void viewToRect(View v, Rect outRect) {
            outRect.set(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        }
    }
}
