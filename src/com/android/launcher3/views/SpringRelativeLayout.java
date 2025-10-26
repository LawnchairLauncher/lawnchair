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

/**
 * View group to allow rendering overscroll effect in a child at the parent level
 */
public class SpringRelativeLayout extends RelativeLayout {

    // fixed edge at the time force is applied
    private final EdgeEffect mEdgeGlowTop;
    private final EdgeEffect mEdgeGlowBottom;

    public SpringRelativeLayout(Context context) {
        this(context, null);
    }

    public SpringRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpringRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mEdgeGlowTop = new EdgeEffect(context, attrs);
        mEdgeGlowBottom = new EdgeEffect(context, attrs);
        setWillNotDraw(false);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (!mEdgeGlowTop.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(0, 0);
            mEdgeGlowTop.setSize(getWidth(), getHeight());
            if (mEdgeGlowTop.draw(canvas)) {
                postInvalidateOnAnimation();
            }
            canvas.restoreToCount(restoreCount);
        }
        if (!mEdgeGlowBottom.isFinished()) {
            final int restoreCount = canvas.save();
            final int width = getWidth();
            final int height = getHeight();
            canvas.translate(-width, height);
            canvas.rotate(180, width, 0);
            mEdgeGlowBottom.setSize(width, height);
            if (mEdgeGlowBottom.draw(canvas)) {
                postInvalidateOnAnimation();
            }
            canvas.restoreToCount(restoreCount);
        }
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
            }
            return super.createEdgeEffect(view, direction);
        }
    }

    private class EdgeEffectProxy extends EdgeEffect {

        private final EdgeEffect mParent;

        EdgeEffectProxy(Context context, EdgeEffect parent) {
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