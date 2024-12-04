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

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.app.animation.Interpolators.LINEAR;

import android.view.animation.Interpolator;

/**
 * Timings for the OverviewSplitSelect > confirmed animation.
 */
abstract class SplitToConfirmTimings implements SplitAnimationTimings {
    // Overwritten by device-specific timings
    abstract public int getPlaceholderFadeInStart();
    abstract public int getPlaceholderFadeInEnd();
    abstract public int getPlaceholderIconFadeInStart();
    abstract public int getPlaceholderIconFadeInEnd();
    abstract public int getStagedRectSlideStart();
    abstract public int getStagedRectSlideEnd();
    abstract public int getBackingScrimFadeInStart();
    abstract public int getBackingScrimFadeInEnd();

    // Common timings
    public int getInstructionsFadeStart() { return 0; }
    public int getInstructionsFadeEnd() { return 67; }
    public Interpolator getStagedRectXInterpolator() { return EMPHASIZED; }
    public Interpolator getStagedRectYInterpolator() { return EMPHASIZED; }
    public Interpolator getStagedRectScaleXInterpolator() { return EMPHASIZED; }
    public Interpolator getStagedRectScaleYInterpolator() { return EMPHASIZED; }
    public Interpolator getBackingScrimFadeInterpolator() { return LINEAR; }

    abstract public int getDuration();

    public float getInstructionsFadeStartOffset() {
        return (float) getInstructionsFadeStart() / getDuration();
    }
    public float getInstructionsFadeEndOffset() {
        return (float) getInstructionsFadeEnd() / getDuration();
    }
    public float getBackingScrimFadeInStartOffset() {
        return (float) getBackingScrimFadeInStart() / getDuration();
    }
    public float getBackingScrimFadeInEndOffset() {
        return (float) getBackingScrimFadeInEnd() / getDuration();
    }
}
