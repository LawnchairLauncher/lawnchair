/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.wm.shell.bubbles.animation;

import android.graphics.Matrix;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;

/**
 * Matrix whose scale properties can be animated using physics animations, via the {@link #SCALE_X}
 * and {@link #SCALE_Y} FloatProperties.
 *
 * This is useful when you need to perform a scale animation with a pivot point, since pivot points
 * are not supported by standard View scale operations but are supported by matrices.
 *
 * NOTE: DynamicAnimation assumes that all custom properties are denominated in pixels, and thus
 * considers 1 to be the smallest user-visible change for custom properties. This means that if you
 * animate {@link #SCALE_X} and {@link #SCALE_Y} to 3f, for example, the animation would have only
 * three frames.
 *
 * To work around this, whenever animating to a desired scale value, animate to the value returned
 * by {@link #getAnimatableValueForScaleFactor} instead. The SCALE_X and SCALE_Y properties will
 * convert that (larger) value into the appropriate scale factor when scaling the matrix.
 */
public class AnimatableScaleMatrix extends Matrix {

    /**
     * The X value of the scale.
     *
     * NOTE: This must be set or animated to the value returned by
     * {@link #getAnimatableValueForScaleFactor}, not the desired scale factor itself.
     */
    public static final FloatPropertyCompat<AnimatableScaleMatrix> SCALE_X =
            new FloatPropertyCompat<AnimatableScaleMatrix>("matrixScaleX") {
        @Override
        public float getValue(AnimatableScaleMatrix object) {
            return getAnimatableValueForScaleFactor(object.mScaleX);
        }

        @Override
        public void setValue(AnimatableScaleMatrix object, float value) {
            object.setScaleX(value * DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
        }
    };

    /**
     * The Y value of the scale.
     *
     * NOTE: This must be set or animated to the value returned by
     * {@link #getAnimatableValueForScaleFactor}, not the desired scale factor itself.
     */
    public static final FloatPropertyCompat<AnimatableScaleMatrix> SCALE_Y =
            new FloatPropertyCompat<AnimatableScaleMatrix>("matrixScaleY") {
                @Override
                public float getValue(AnimatableScaleMatrix object) {
                    return getAnimatableValueForScaleFactor(object.mScaleY);
                }

                @Override
                public void setValue(AnimatableScaleMatrix object, float value) {
                    object.setScaleY(value * DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
                }
            };

    private float mScaleX = 1f;
    private float mScaleY = 1f;

    private float mPivotX = 0f;
    private float mPivotY = 0f;

    /**
     * Return the value to animate SCALE_X or SCALE_Y to in order to achieve the desired scale
     * factor.
     */
    public static float getAnimatableValueForScaleFactor(float scale) {
        return scale * (1f / DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
    }

    @Override
    public void setScale(float sx, float sy, float px, float py) {
        mScaleX = sx;
        mScaleY = sy;
        mPivotX = px;
        mPivotY = py;
        super.setScale(mScaleX, mScaleY, mPivotX, mPivotY);
    }

    public void setScaleX(float scaleX) {
        mScaleX = scaleX;
        super.setScale(mScaleX, mScaleY, mPivotX, mPivotY);
    }

    public void setScaleY(float scaleY) {
        mScaleY = scaleY;
        super.setScale(mScaleX, mScaleY, mPivotX, mPivotY);
    }

    public void setPivotX(float pivotX) {
        mPivotX = pivotX;
        super.setScale(mScaleX, mScaleY, mPivotX, mPivotY);
    }

    public void setPivotY(float pivotY) {
        mPivotY = pivotY;
        super.setScale(mScaleX, mScaleY, mPivotX, mPivotY);
    }

    public float getScaleX() {
        return mScaleX;
    }

    public float getScaleY() {
        return mScaleY;
    }

    public float getPivotX() {
        return mPivotX;
    }

    public float getPivotY() {
        return mPivotY;
    }

    @Override
    public boolean equals(Object obj) {
        // Use object equality to allow this matrix to be used as a map key (which is required for
        // PhysicsAnimator's animator caching).
        return obj == this;
    }
}
