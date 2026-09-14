/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.EdgeEffect;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.util.EdgeEffectCompat;
import com.android.launcher3.util.TranslateEdgeEffect;

/**
 * View group to allow rendering overscroll effect in a child at the parent level
 */
public class SpringRelativeLayout extends RelativeLayout {

    // fixed edge at the time force is applied
    private final TranslateEdgeEffect mEdgeGlowTop;
    private final TranslateEdgeEffect mEdgeGlowBottom;
    private final float[] mTempFloat = new float[1];
    private int mOverScrollShift = 0;

    public SpringRelativeLayout(Context context) {
        this(context, null);
    }

    public SpringRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpringRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mEdgeGlowTop = new TranslateEdgeEffect(context);
        mEdgeGlowBottom = new TranslateEdgeEffect(context);
        setWillNotDraw(false);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
    }

    private float getUndampedOverScrollShift() {
        int height = getHeight();
        int width = getWidth();
        float effectiveShift = 0;
        if (!mEdgeGlowTop.isFinished()) {
            mEdgeGlowTop.setSize(width, height);
            boolean animating = mEdgeGlowTop.getTranslationShift(mTempFloat);
            effectiveShift = mTempFloat[0];
            if (animating) {
                postInvalidateOnAnimation();
            }
        }
        if (!mEdgeGlowBottom.isFinished()) {
            mEdgeGlowBottom.setSize(width, height);
            boolean animating = mEdgeGlowBottom.getTranslationShift(mTempFloat);
            effectiveShift -= mTempFloat[0];
            if (animating) {
                postInvalidateOnAnimation();
            }
        }
        return effectiveShift * height;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // ponytail: translation overscroll for app drawer and bottom sheets; no stretch
        if (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished()) {
            final int restoreCount = canvas.save();
            int height = getHeight();
            int scroll = OverScroll.dampedScroll(getUndampedOverScrollShift(), height, 0.15f);
            if (mOverScrollShift != scroll) {
                mOverScrollShift = scroll;
                onOverScrollChanged();
            }
            canvas.translate(0, scroll);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(restoreCount);
        } else {
            if (mOverScrollShift != 0) {
                mOverScrollShift = 0;
                onOverScrollChanged();
            }
            super.dispatchDraw(canvas);
        }
    }

    public int getOverScrollShift() {
        return mOverScrollShift;
    }

    protected void onOverScrollChanged() {
    }

    /**
     * Absorbs the velocity as a result for swipe-up fling
     */
    protected void absorbSwipeUpVelocity(int velocity) {
        mEdgeGlowBottom.onAbsorb(velocity);
        invalidate();
    }

    protected void absorbPullDeltaDistance(float deltaDistance, float displacement) {
        mEdgeGlowBottom.onPull(deltaDistance, displacement);
        invalidate();
    }

    public void onRelease() {
        mEdgeGlowBottom.onRelease();
    }

    public EdgeEffectFactory createEdgeEffectFactory() {
        return new ProxyEdgeEffectFactory();
    }

    private class ProxyEdgeEffectFactory extends EdgeEffectFactory {

        @NonNull @Override
        protected EdgeEffect createEdgeEffect(RecyclerView view, int direction) {
            if (direction == DIRECTION_TOP) {
                return new EdgeEffectProxy(getContext(), mEdgeGlowTop);
            } else if (direction == DIRECTION_BOTTOM) {
                return new EdgeEffectProxy(getContext(), mEdgeGlowBottom);
            }
            return super.createEdgeEffect(view, direction);
        }
    }

    private class EdgeEffectProxy extends EdgeEffect {

        private final EdgeEffectCompat mParent;

        EdgeEffectProxy(Context context, EdgeEffectCompat parent) {
            super(context);
            mParent = parent;
        }

        @Override
        public boolean draw(Canvas canvas) {
            return false;
        }

        private void invalidateParentScrollEffect() {
            if (!mParent.isFinished()) {
                invalidate();
            }
        }

        @Override
        public void onAbsorb(int velocity) {
            mParent.onAbsorb(velocity);
            invalidateParentScrollEffect();
        }

        @Override
        public void onPull(float deltaDistance) {
            mParent.onPull(deltaDistance);
            invalidateParentScrollEffect();
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            mParent.onPull(deltaDistance, displacement);
            invalidateParentScrollEffect();
        }

        @Override
        public float onPullDistance(float deltaDistance, float displacement) {
            float consumed = mParent.onPullDistance(deltaDistance, displacement);
            invalidateParentScrollEffect();
            return consumed;
        }

        @Override
        public float getDistance() {
            return mParent.getDistance();
        }

        @Override
        public void onRelease() {
            mParent.onRelease();
            invalidateParentScrollEffect();
        }

        @Override
        public void finish() {
            mParent.finish();
        }

        @Override
        public boolean isFinished() {
            return mParent.isFinished();
        }
    }
}
