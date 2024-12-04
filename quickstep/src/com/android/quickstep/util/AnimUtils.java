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

import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.os.Bundle;
import android.os.IRemoteCallback;
import android.view.animation.Interpolator;

import com.android.launcher3.util.RunnableList;

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

    /**
     * Returns a IRemoteCallback which completes the provided list as a result
     */
    public static IRemoteCallback completeRunnableListCallback(RunnableList list) {
        return new IRemoteCallback.Stub() {
            @Override
            public void sendResult(Bundle bundle) {
                MAIN_EXECUTOR.execute(list::executeAllAndDestroy);
            }
        };
    }

    /**
     * Returns a function that runs the given interpolator such that the entire progress is set
     * between the given duration. That is, we set the interpolation to 0 until startDelay and reach
     * 1 by (startDelay + duration).
     */
    public static Interpolator clampToDuration(Interpolator interpolator, float startDelay,
            float duration, float totalDuration) {
        return clampToProgress(interpolator, startDelay / totalDuration,
                (startDelay + duration) / totalDuration);
    }
}
