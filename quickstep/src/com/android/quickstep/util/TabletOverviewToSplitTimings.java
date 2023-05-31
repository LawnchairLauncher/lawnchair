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

import static com.android.app.animation.Interpolators.DECELERATE_2;

import android.view.animation.Interpolator;

/**
 * Timings for the Overview > OverviewSplitSelect animation on tablets.
 */
public class TabletOverviewToSplitTimings
        extends OverviewToSplitTimings implements SplitAnimationTimings {
    public int getPlaceholderFadeInStart() { return 0; }
    public int getPlaceholderFadeInEnd() { return 133; }
    public int getPlaceholderIconFadeInStart() { return 167; }
    public int getPlaceholderIconFadeInEnd() { return 250; }
    public int getStagedRectSlideStart() { return 0; }
    public int getStagedRectSlideEnd() { return 417; }
    public int getGridSlideStart() { return 67; }
    public int getGridSlideStagger() { return 16; }
    public int getGridSlideDuration() { return 500; }

    public int getDuration() { return TABLET_ENTER_DURATION; }
    public Interpolator getStagedRectXInterpolator() { return DECELERATE_2; }
    public Interpolator getStagedRectYInterpolator() { return DECELERATE_2; }
    public Interpolator getStagedRectScaleXInterpolator() { return DECELERATE_2; }
    public Interpolator getStagedRectScaleYInterpolator() { return DECELERATE_2; }
}
