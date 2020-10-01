/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.anim;

import android.content.Context;

import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener;
import androidx.dynamicanimation.animation.FlingAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.R;
import com.android.launcher3.util.DynamicResource;
import com.android.systemui.plugins.ResourceProvider;

/**
 * Given a property to animate and a target value and starting velocity, first apply friction to
 * the fling until we pass the target, then apply a spring force to pull towards the target.
 */
public class FlingSpringAnim {

    private final FlingAnimation mFlingAnim;
    private SpringAnimation mSpringAnim;
    private final boolean mSkipFlingAnim;

    private float mTargetPosition;

    public <K> FlingSpringAnim(K object, Context context, FloatPropertyCompat<K> property,
            float startPosition, float targetPosition, float startVelocity, float minVisChange,
            float minValue, float maxValue, float springVelocityFactor,
            OnAnimationEndListener onEndListener) {
        ResourceProvider rp = DynamicResource.provider(context);
        float damping = rp.getFloat(R.dimen.swipe_up_rect_xy_damping_ratio);
        float stiffness = rp.getFloat(R.dimen.swipe_up_rect_xy_stiffness);
        float friction = rp.getFloat(R.dimen.swipe_up_rect_xy_fling_friction);

        mFlingAnim = new FlingAnimation(object, property)
                .setFriction(friction)
                // Have the spring pull towards the target if we've slowed down too much before
                // reaching it.
                .setMinimumVisibleChange(minVisChange)
                .setStartVelocity(startVelocity)
                .setMinValue(minValue)
                .setMaxValue(maxValue);
        mTargetPosition = targetPosition;

        // We are already past the fling target, so skip it to avoid losing a frame of the spring.
        mSkipFlingAnim = startPosition <= minValue && startVelocity < 0
                || startPosition >= maxValue && startVelocity > 0;

        mFlingAnim.addEndListener(((animation, canceled, value, velocity) -> {
            mSpringAnim = new SpringAnimation(object, property)
                    .setStartValue(value)
                    .setStartVelocity(velocity * springVelocityFactor)
                    .setSpring(new SpringForce(mTargetPosition)
                            .setStiffness(stiffness)
                            .setDampingRatio(damping));
            mSpringAnim.addEndListener(onEndListener);
            mSpringAnim.animateToFinalPosition(mTargetPosition);
        }));
    }

    public float getTargetPosition() {
        return mTargetPosition;
    }

    public void updatePosition(float startPosition, float targetPosition) {
        mFlingAnim.setMinValue(Math.min(startPosition, targetPosition))
                .setMaxValue(Math.max(startPosition, targetPosition));
        mTargetPosition = targetPosition;
        if (mSpringAnim != null) {
            mSpringAnim.animateToFinalPosition(mTargetPosition);
        }
    }

    public void start() {
        mFlingAnim.start();
        if (mSkipFlingAnim) {
            mFlingAnim.cancel();
        }
    }

    public void end() {
        mFlingAnim.cancel();
        if (mSpringAnim.canSkipToEnd()) {
            mSpringAnim.skipToEnd();
        }
    }
}
