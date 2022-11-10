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

import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.view.animation.Interpolator;

/**
 * An interface that supports the centralization of timing information for splitscreen animations.
 */
public interface SplitAnimationTimings {
    int TABLET_ENTER_DURATION = 866;
    int TABLET_CONFIRM_DURATION = 500;

    int PHONE_ENTER_DURATION = 517;
    int PHONE_CONFIRM_DURATION = 333;

    int ABORT_DURATION = 500;

    SplitAnimationTimings TABLET_OVERVIEW_TO_SPLIT = new TabletOverviewToSplitTimings();
    SplitAnimationTimings TABLET_HOME_TO_SPLIT = new TabletHomeToSplitTimings();
    SplitAnimationTimings TABLET_SPLIT_TO_CONFIRM = new TabletSplitToConfirmTimings();

    SplitAnimationTimings PHONE_OVERVIEW_TO_SPLIT = new PhoneOverviewToSplitTimings();
    SplitAnimationTimings PHONE_SPLIT_TO_CONFIRM = new PhoneSplitToConfirmTimings();

    // Shared methods
    int getDuration();
    int getPlaceholderFadeInStart();
    int getPlaceholderFadeInEnd();
    int getPlaceholderIconFadeInStart();
    int getPlaceholderIconFadeInEnd();
    int getStagedRectSlideStart();
    int getStagedRectSlideEnd();
    Interpolator getStagedRectXInterpolator();
    Interpolator getStagedRectYInterpolator();
    Interpolator getStagedRectScaleXInterpolator();
    Interpolator getStagedRectScaleYInterpolator();
    default float getPlaceholderFadeInStartOffset() {
        return (float) getPlaceholderFadeInStart() / getDuration();
    }
    default float getPlaceholderFadeInEndOffset() {
        return (float) getPlaceholderFadeInEnd() / getDuration();
    }
    default float getPlaceholderIconFadeInStartOffset() {
        return (float) getPlaceholderIconFadeInStart() / getDuration();
    }
    default float getPlaceholderIconFadeInEndOffset() {
        return (float) getPlaceholderIconFadeInEnd() / getDuration();
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
    default Interpolator getGridSlidePrimaryInterpolator() { return LINEAR; }
    default Interpolator getGridSlideSecondaryInterpolator() { return LINEAR; }

    // Defaults for HomeToSplit
    default float getScrimFadeInStartOffset() { return 0; }
    default float getScrimFadeInEndOffset() { return 0; }

    // Defaults for SplitToConfirm
    default float getInstructionsFadeStartOffset() { return 0; }
    default float getInstructionsFadeEndOffset() { return 0; }
}

