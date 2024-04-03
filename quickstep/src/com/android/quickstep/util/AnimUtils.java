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

/**
 * Utility class containing methods to help manage animations, interpolators, and timings.
 */
public class AnimUtils {
    /**
     * Fetches device-specific timings for the Overview > Split animation
     * (splitscreen initiated from Overview).
     */
    public static SplitAnimationTimings getDeviceOverviewToSplitTimings(boolean isTablet) {
        return isTablet
                ? SplitAnimationTimings.TABLET_OVERVIEW_TO_SPLIT
                : SplitAnimationTimings.PHONE_OVERVIEW_TO_SPLIT;
    }

    /**
     * Fetches device-specific timings for the Split > Confirm animation
     * (splitscreen confirmed by selecting a second app).
     */
    public static SplitAnimationTimings getDeviceSplitToConfirmTimings(boolean isTablet) {
        return isTablet
                ? SplitAnimationTimings.TABLET_SPLIT_TO_CONFIRM
                : SplitAnimationTimings.PHONE_SPLIT_TO_CONFIRM;
    }

    /**
     * Fetches device-specific timings for the app pair launch animation.
     */
    public static SplitAnimationTimings getDeviceAppPairLaunchTimings(boolean isTablet) {
        return isTablet
                ? SplitAnimationTimings.TABLET_APP_PAIR_LAUNCH
                : SplitAnimationTimings.PHONE_APP_PAIR_LAUNCH;
    }
}
