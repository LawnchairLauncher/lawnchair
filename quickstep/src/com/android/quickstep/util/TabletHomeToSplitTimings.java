/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.quickstep.util;

import static com.android.app.animation.Interpolators.LINEAR;

import android.view.animation.Interpolator;

/**
 * Timings for the Home > OverviewSplitSelect animation on tablets.
 */
public class TabletHomeToSplitTimings
        extends TabletOverviewToSplitTimings implements SplitAnimationTimings {
    @Override
    public Interpolator getStagedRectXInterpolator() { return LINEAR; }
    @Override
    public Interpolator getStagedRectScaleXInterpolator() { return LINEAR; }
    @Override
    public Interpolator getStagedRectScaleYInterpolator() { return LINEAR; }

    public int getScrimFadeInStart() { return 0; }
    public int getScrimFadeInEnd() { return 167; }

    public float getScrimFadeInStartOffset() {
        return (float) getScrimFadeInStart() / getDuration();
    }
    public float getScrimFadeInEndOffset() {
        return (float) getScrimFadeInEnd() / getDuration();
    }
}
