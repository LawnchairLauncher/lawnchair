/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.animation.TimeInterpolator;
import android.content.Context;
import android.view.animation.Interpolator;
import android.widget.Scroller;

/**
 * Extension of {@link android.widget.Scroller} with the ability to modify the
 * Interpolator post-construction.
 */
public class LauncherScroller extends Scroller {

    private final InterpolatorWrapper mInterpolatorWrapper;

    public LauncherScroller(Context context) {
        this(context, new InterpolatorWrapper());
    }

    private LauncherScroller(Context context, InterpolatorWrapper interpolatorWrapper) {
        super(context, interpolatorWrapper);
        mInterpolatorWrapper = interpolatorWrapper;
    }

    public void setInterpolator(TimeInterpolator interpolator) {
        mInterpolatorWrapper.interpolator = interpolator;
    }

    private static class InterpolatorWrapper implements Interpolator {

        public TimeInterpolator interpolator;

        @Override
        public float getInterpolation(float v) {
            return interpolator == null ? v : interpolator.getInterpolation(v);
        }
    }
}
