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
 * Organizes timing information for split screen animations.
 */
public interface SplitAnimationTimings {
    /** Total duration (ms) for initiating split screen (staging the first app) on tablets. */
    int TABLET_ENTER_DURATION = 866;
    /** Total duration (ms) for confirming split screen (selecting the second app) on tablets. */
    int TABLET_CONFIRM_DURATION = 500;
    /** Total duration (ms) for initiating split screen (staging the first app) on phones. */
    int PHONE_ENTER_DURATION = 517;
    /** Total duration (ms) for confirming split screen (selecting the second app) on phones. */
    int PHONE_CONFIRM_DURATION = 333;
    /** Total duration (ms) for aborting split screen (before selecting the second app). */
    int ABORT_DURATION = 500;
    /** Total duration (ms) for launching an app pair from its icon on tablets. */
    int TABLET_APP_PAIR_LAUNCH_DURATION = 998;
    /** Total duration (ms) for launching an app pair from its icon on phones. */
    int PHONE_APP_PAIR_LAUNCH_DURATION = 915;

    // Initialize timing classes so they can be accessed statically
    SplitAnimationTimings TABLET_OVERVIEW_TO_SPLIT = new TabletOverviewToSplitTimings();
    SplitAnimationTimings TABLET_HOME_TO_SPLIT = new TabletHomeToSplitTimings();
    SplitAnimationTimings TABLET_SPLIT_TO_CONFIRM = new TabletSplitToConfirmTimings();
    SplitAnimationTimings PHONE_OVERVIEW_TO_SPLIT = new PhoneOverviewToSplitTimings();
    SplitAnimationTimings PHONE_SPLIT_TO_CONFIRM = new PhoneSplitToConfirmTimings();
    SplitAnimationTimings TABLET_APP_PAIR_LAUNCH = new TabletAppPairLaunchTimings();
    SplitAnimationTimings PHONE_APP_PAIR_LAUNCH = new PhoneAppPairLaunchTimings();

    // Shared methods: all split animations have these parameters
    int getDuration();
    /** Start fading in the floating view tile at this time (in ms). */
    int getPlaceholderFadeInStart();
    int getPlaceholderFadeInEnd();
    /** Start fading in the app icon at this time (in ms). */
    int getPlaceholderIconFadeInStart();
    int getPlaceholderIconFadeInEnd();
    /** Start translating the floating view tile at this time (in ms). */
    int getStagedRectSlideStart();
    /** The floating tile has reached its final position at this time (in ms). */
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

    // DEFAULT VALUES: We define default values here so that SplitAnimationTimings can be used
    // flexibly in animation-running functions, e.g. a single function that handles 2 types of split
    // animations. The values are not intended to be used, and can safely be removed if refactoring
    // these classes.

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

    // Defaults for AppPair
    default float getCellSplitStartOffset() { return 0; }
    default float getCellSplitEndOffset() { return 0; }
    default float getAppRevealStartOffset() { return 0; }
    default float getAppRevealEndOffset() { return 0; }
    default Interpolator getCellSplitInterpolator() { return LINEAR; }
    default Interpolator getIconFadeInterpolator() { return LINEAR; }
}

