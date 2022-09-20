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

import static com.android.launcher3.anim.Interpolators.DEACCEL_2;

import android.view.animation.Interpolator;

/**
 * Timings for the Overview > OverviewSplitSelect animation.
 */
public class OverviewToSplitTimings implements SplitAnimationTimings {
    public int getPlaceholderFadeInStart() { return 0; }
    public int getPlaceholderFadeInEnd() { return 133; }
    public int getPlaceholderIconFadeInStart() { return 167; }
    public int getPlaceholderIconFadeInEnd() { return 250; }
    public int getStagedRectSlideStart() { return 0; }
    public int getStagedRectSlideEnd() { return 417; }
    public int getGridSlideStart() { return 67; }
    public int getGridSlideStagger() { return 16; }
    public int getGridSlideDuration() { return 500; }
    public int getActionsFadeStart() { return 0; }
    public int getActionsFadeEnd() { return 83; }
    public int getIconFadeStart() { return 0; }
    public int getIconFadeEnd() { return 83; }
    public int getInstructionsContainerFadeInStart() { return 167; }
    public int getInstructionsContainerFadeInEnd() { return 250; }
    public int getInstructionsTextFadeInStart() { return 217; }
    public int getInstructionsTextFadeInEnd() { return 300; }
    public int getInstructionsUnfoldStart() { return 167; }
    public int getInstructionsUnfoldEnd() { return 500; }

    public int getDuration() { return ENTER_DURATION; }
    public Interpolator getStagedRectXInterpolator() { return DEACCEL_2; }
    public Interpolator getStagedRectYInterpolator() { return DEACCEL_2; }
    public Interpolator getStagedRectScaleXInterpolator() { return DEACCEL_2; }
    public Interpolator getStagedRectScaleYInterpolator() { return DEACCEL_2; }

    public float getGridSlideStartOffset() {
        return (float) getGridSlideStart() / getDuration();
    }
    public float getGridSlideStaggerOffset() {
        return (float) getGridSlideStagger() / getDuration();
    }
    public float getGridSlideDurationOffset() {
        return (float) getGridSlideDuration() / getDuration();
    }
    public float getActionsFadeStartOffset() {
        return (float) getActionsFadeStart() / getDuration();
    }
    public float getActionsFadeEndOffset() {
        return (float) getActionsFadeEnd() / getDuration();
    }
    public float getIconFadeStartOffset() {
        return (float) getIconFadeStart() / getDuration();
    }
    public float getIconFadeEndOffset() {
        return (float) getIconFadeEnd() / getDuration();
    }
    public float getInstructionsContainerFadeInStartOffset() {
        return (float) getInstructionsContainerFadeInStart() / getDuration();
    }
    public float getInstructionsContainerFadeInEndOffset() {
        return (float) getInstructionsContainerFadeInEnd() / getDuration();
    }
    public float getInstructionsTextFadeInStartOffset() {
        return (float) getInstructionsTextFadeInStart() / getDuration();
    }
    public float getInstructionsTextFadeInEndOffset() {
        return (float) getInstructionsTextFadeInEnd() / getDuration();
    }
    public float getInstructionsUnfoldStartOffset() {
        return (float) getInstructionsUnfoldStart() / getDuration();
    }
    public float getInstructionsUnfoldEndOffset() {
        return (float) getInstructionsUnfoldEnd() / getDuration();
    }
}
