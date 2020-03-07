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
package com.android.launcher3.anim;

import androidx.dynamicanimation.animation.SpringForce;

/**
 * Utility class to store configurations for spring animation
 */
public class SpringProperty {

    public static final SpringProperty DEFAULT = new SpringProperty();

    // Play spring when the animation is going towards the end
    public static final int FLAG_CAN_SPRING_ON_END = 1 << 0;
    // Play spring when animation is going towards the start (in reverse direction)
    public static final int FLAG_CAN_SPRING_ON_START = 1 << 1;

    public final int flags;

    float mDampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY;
    float mStiffness = SpringForce.STIFFNESS_MEDIUM;

    public SpringProperty() {
        this(0);
    }

    public SpringProperty(int flags) {
        this.flags = flags;
    }

    public SpringProperty setDampingRatio(float dampingRatio) {
        mDampingRatio = dampingRatio;
        return this;
    }

    public SpringProperty setStiffness(float stiffness) {
        mStiffness = stiffness;
        return this;
    }
}
