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

package com.android.wm.shell.bubbles.animation;

import androidx.dynamicanimation.animation.DynamicAnimation;

/**
 * End listener that removes itself from its animation when called for the first time. Useful since
 * anonymous OnAnimationEndListener instances can't pass themselves to
 * {@link DynamicAnimation#removeEndListener}, but can call through to this superclass
 * implementation.
 */
public class OneTimeEndListener implements DynamicAnimation.OnAnimationEndListener {

    @Override
    public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value,
            float velocity) {
        animation.removeEndListener(this);
    }
}
