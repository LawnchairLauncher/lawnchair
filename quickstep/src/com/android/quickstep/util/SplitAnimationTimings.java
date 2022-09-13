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

import android.view.animation.Interpolator;

/**
 * An interface that supports the centralization of timing information for splitscreen animations.
 */
public interface SplitAnimationTimings {
    int ENTER_DURATION = 866;
    int ABORT_DURATION = 500;
    int CONFIRM_DURATION = 383;

    SplitAnimationTimings OVERVIEW_TO_SPLIT = new OverviewToSplitTimings();
    SplitAnimationTimings SPLIT_TO_CONFIRM = new SplitToConfirmTimings();

    // Shared methods
    int getDuration();
    int getThumbnailFadeToGrayStart();
    int getThumbnailFadeToGrayEnd();
    int getDockedIconFadeInStart();
    int getDockedIconFadeInEnd();
    int getStagedRectSlideStart();
    int getStagedRectSlideEnd();
    Interpolator getStagedRectSlideInterpolator();
    default float getThumbnailFadeToGrayStartOffset() {
        return (float) getThumbnailFadeToGrayStart() / getDuration();
    }
    default float getThumbnailFadeToGrayEndOffset() {
        return (float) getThumbnailFadeToGrayEnd() / getDuration();
    }
    default float getDockedIconFadeInStartOffset() {
        return (float) getDockedIconFadeInStart() / getDuration();
    }
    default float getDockedIconFadeInEndOffset() {
        return (float) getDockedIconFadeInEnd() / getDuration();
    }
    default float getStagedRectSlideStartOffset() {
        return (float) getStagedRectSlideStart() / getDuration();
    }
    default float getStagedRectSlideEndOffset() {
        return (float) getStagedRectSlideEnd() / getDuration();
    }

    // Defaults for OverviewToSplit
    default float getGridSlideStartOffset() { return 0; }
    default float getGridSlideStaggerOffset() { return 0; }
    default float getGridSlideDurationOffset() { return 0; }
    default float getActionsFadeStartOffset() { return 0; }
    default float getActionsFadeEndOffset() { return 0; }
    default float getIconFadeStartOffset() { return 0; }
    default float getIconFadeEndOffset() { return 0; }
    default float getInstructionsContainerFadeInStartOffset() { return 0; }
    default float getInstructionsContainerFadeInEndOffset() { return 0; }
    default float getInstructionsTextFadeInStartOffset() { return 0; }
    default float getInstructionsTextFadeInEndOffset() { return 0; }
    default float getInstructionsUnfoldStartOffset() { return 0; }
    default float getInstructionsUnfoldEndOffset() { return 0; }

    // Defaults for SplitToConfirm
    default float getInstructionsFadeStartOffset() { return 0; }
    default float getInstructionsFadeEndOffset() { return 0; }
}

