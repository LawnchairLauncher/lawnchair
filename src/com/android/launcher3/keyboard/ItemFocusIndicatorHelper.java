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
import android.util.FloatProperty;
import android.view.View;

import com.android.launcher3.R;

/**
 * A helper class to draw background of a focused item.
 * @param <T> Item type
 */
public abstract class ItemFocusIndicatorHelper<T> implements AnimatorUpdateListener {

    private static final float MIN_VISIBLE_ALPHA = 0.2f;
    private static final long ANIM_DURATION = 150;

    public static final FloatProperty<ItemFocusIndicatorHelper> ALPHA =
            new FloatProperty<ItemFocusIndicatorHelper>("alpha") {

                @Override
                public void setValue(ItemFocusIndicatorHelper object, float value) {
                    object.setAlpha(value);
                }

                @Override
                public Float get(ItemFocusIndicatorHelper object) {
                    return object.mAlpha;
                }
            };

    public static final FloatProperty<ItemFocusIndicatorHelper> SHIFT =
            new FloatProperty<ItemFocusIndicatorHelper>("shift") {

                @Override
                public void setValue(ItemFocusIndicatorHelper object, float value) {
                    object.mShift = value;
                }

                @Override
                public Float get(ItemFocusIndicatorHelper object) {
                    return object.mShift;
                }
            };

    private static final RectEvaluator RECT_EVALUATOR = new RectEvaluator(new Rect());
    private static final Rect sTempRect1 = new Rect();
    private static final Rect sTempRect2 = new Rect();

    private final View mContainer;
    protected final Paint mPaint;
    private final int mMaxAlpha;

    private final Rect mDirtyRect = new Rect();
    private boolean mIsDirty = false;

    private T mLastFocusedItem;

    private T mCurrentItem;
    private T mTargetItem;
    /**
     * The fraction indicating the position of the focusRect between {@link #mCurrentItem}
     * & {@link #mTargetItem}
     */
    private float mShift;

    private ObjectAnimator mCurrentAnimation;
    private float mAlpha;
    private float mRadius;

    public ItemFocusIndicatorHelper(View container, int color) {
        mContainer = container;

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mMaxAlpha = Color.alpha(color);
        mPaint.setColor(0xFF000000 | color);

        setAlpha(0);
        mShift = 0;
        mRadius = container.getResources().getDimensionPixelSize(
                R.dimen.grid_visualization_rounding_radius);
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

    /**
     * Draws the indicator on the canvas
     */
    public void draw(Canvas c) {
        if (mAlpha <= 0) return;

        Rect newRect = getDrawRect();
        if (newRect != null) {
            mDirtyRect.set(newRect);
            c.drawRoundRect((float) mDirtyRect.left, (float) mDirtyRect.top,
                    (float) mDirtyRect.right, (float) mDirtyRect.bottom,
                    mRadius, mRadius, mPaint);
            mIsDirty = true;
        }
    }

    private Rect getDrawRect() {
        if (mCurrentItem != null && shouldDraw(mCurrentItem)) {
            viewToRect(mCurrentItem, sTempRect1);

            if (mShift > 0 && mTargetItem != null) {
                viewToRect(mTargetItem, sTempRect2);
                return RECT_EVALUATOR.evaluate(mShift, sTempRect1, sTempRect2);
            } else {
                return sTempRect1;
            }
        }
        return null;
    }

    /**
     * Returns true if the provided item is valid
     */
    protected boolean shouldDraw(T item) {
        return true;
    }

    protected void changeFocus(T item, boolean hasFocus) {
        if (hasFocus) {
            endCurrentAnimation();

            if (mAlpha > MIN_VISIBLE_ALPHA) {
                mTargetItem = item;

                mCurrentAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(ALPHA, 1),
                        PropertyValuesHolder.ofFloat(SHIFT, 1));
                mCurrentAnimation.addListener(new ViewSetListener(item, true));
            } else {
                setCurrentItem(item);

                mCurrentAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(ALPHA, 1));
            }

            mLastFocusedItem = item;
        } else {
            if (mLastFocusedItem == item) {
                mLastFocusedItem = null;
                endCurrentAnimation();
                mCurrentAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(ALPHA, 0));
                mCurrentAnimation.addListener(new ViewSetListener(null, false));
            }
        }

        // invalidate once
        invalidateDirty();

        mLastFocusedItem = hasFocus ? item : null;
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

    protected void setCurrentItem(T item) {
        mCurrentItem = item;
        mShift = 0;
        mTargetItem = null;
    }

    /**
     * Gets the position of the item relative to {@link #mContainer}.
     */
    public abstract void viewToRect(T item, Rect outRect);

    private class ViewSetListener extends AnimatorListenerAdapter {
        private final T mItemToSet;
        private final boolean mCallOnCancel;
        private boolean mCalled = false;

        ViewSetListener(T item, boolean callOnCancel) {
            mItemToSet = item;
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
                setCurrentItem(mItemToSet);
                mCalled = true;
            }
        }
    }
}
