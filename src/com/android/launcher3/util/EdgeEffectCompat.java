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
package com.android.launcher3.util;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.EdgeEffect;

import com.android.launcher3.Utilities;

/**
 * Extension of {@link EdgeEffect} to allow backwards compatibility
 */
public class EdgeEffectCompat extends EdgeEffect {

    public EdgeEffectCompat(Context context) {
        super(context);
    }

    @Override
    public float getDistance() {
        return Utilities.ATLEAST_S ? super.getDistance() : 0;
    }

    @Override
    public float onPullDistance(float deltaDistance, float displacement) {
        if (Utilities.ATLEAST_S) {
            return super.onPullDistance(deltaDistance, displacement);
        } else {
            onPull(deltaDistance, displacement);
            return deltaDistance;
        }
    }

    public float onPullDistance(float deltaDistance, float displacement, MotionEvent ev) {
        return onPullDistance(deltaDistance, displacement);
    }

    public void onFlingVelocity(int velocity) { }

    public void onRelease(MotionEvent ev) {
        onRelease();
    }
}
