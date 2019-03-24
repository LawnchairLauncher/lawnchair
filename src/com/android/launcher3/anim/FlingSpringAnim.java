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

import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener;
import androidx.dynamicanimation.animation.FlingAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Given a property to animate and a target value and starting velocity, first apply friction to
 * the fling until we pass the target, then apply a spring force to pull towards the target.
 */
public class FlingSpringAnim {

    private static final float FLING_FRICTION = 1.5f;
    // Have the spring pull towards the target if we've slowed down too much before reaching it.
    private static final float FLING_END_THRESHOLD_PX = 50f;
    private static final float SPRING_STIFFNESS = 350f;
    private static final float SPRING_DAMPING = SpringForce.DAMPING_RATIO_LOW_BOUNCY;

    private final FlingAnimation mFlingAnim;

    public <K> FlingSpringAnim(K object, FloatPropertyCompat<K> property, float startPosition,
            float targetPosition, float startVelocity, OnAnimationEndListener onEndListener) {
        mFlingAnim = new FlingAnimation(object, property)
                .setFriction(FLING_FRICTION)
                .setMinimumVisibleChange(FLING_END_THRESHOLD_PX)
                .setStartVelocity(startVelocity)
                .setMinValue(Math.min(startPosition, targetPosition))
                .setMaxValue(Math.max(startPosition, targetPosition));
        mFlingAnim.addEndListener(((animation, canceled, value, velocity) -> {
            SpringAnimation springAnim = new SpringAnimation(object, property)
                    .setStartVelocity(velocity)
                    .setSpring(new SpringForce(targetPosition)
                            .setStiffness(SPRING_STIFFNESS)
                            .setDampingRatio(SPRING_DAMPING));
            springAnim.addEndListener(onEndListener);
            springAnim.start();
        }));
    }

    public void start() {
        mFlingAnim.start();
    }
}
