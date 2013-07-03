/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

/**
 * Scroller that gradually reaches a target velocity.
 */
class RampUpScroller {
    private final Interpolator mInterpolator;
    private final long mRampUpTime;

    private long mStartTime;
    private long mDeltaTime;
    private float mTargetVelocityX;
    private float mTargetVelocityY;
    private int mDeltaX;
    private int mDeltaY;

    /**
     * Creates a new ramp-up scroller that reaches full velocity after a
     * specified duration.
     *
     * @param rampUpTime Duration before the scroller reaches target velocity.
     */
    public RampUpScroller(long rampUpTime) {
        mInterpolator = new AccelerateInterpolator();
        mRampUpTime = rampUpTime;
    }

    /**
     * Starts the scroller at the current animation time.
     */
    public void start() {
        mStartTime = AnimationUtils.currentAnimationTimeMillis();
        mDeltaTime = mStartTime;
    }

    /**
     * Computes the current scroll deltas. This usually only be called after
     * starting the scroller with {@link #start()}.
     *
     * @see #getDeltaX()
     * @see #getDeltaY()
     */
    public void computeScrollDelta() {
        final long currentTime = AnimationUtils.currentAnimationTimeMillis();
        final long elapsedSinceStart = currentTime - mStartTime;
        final float scale;
        if (elapsedSinceStart < mRampUpTime) {
            scale = mInterpolator.getInterpolation((float) elapsedSinceStart / mRampUpTime);
        } else {
            scale = 1f;
        }

        final long elapsedSinceDelta = currentTime - mDeltaTime;
        mDeltaTime = currentTime;

        mDeltaX = (int) (elapsedSinceDelta * scale * mTargetVelocityX);
        mDeltaY = (int) (elapsedSinceDelta * scale * mTargetVelocityY);
    }

    /**
     * Sets the target velocity for this scroller.
     *
     * @param x The target X velocity in pixels per millisecond.
     * @param y The target Y velocity in pixels per millisecond.
     */
    public void setTargetVelocity(float x, float y) {
        mTargetVelocityX = x;
        mTargetVelocityY = y;
    }

    /**
     * @return The target X velocity for this scroller.
     */
    public float getTargetVelocityX() {
        return mTargetVelocityX;
    }

    /**
     * @return The target Y velocity for this scroller.
     */
    public float getTargetVelocityY() {
        return mTargetVelocityY;
    }

    /**
     * The distance traveled in the X-coordinate computed by the last call to
     * {@link #computeScrollDelta()}.
     */
    public int getDeltaX() {
        return mDeltaX;
    }

    /**
     * The distance traveled in the Y-coordinate computed by the last call to
     * {@link #computeScrollDelta()}.
     */
    public int getDeltaY() {
        return mDeltaY;
    }
}
