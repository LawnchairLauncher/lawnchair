/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.view.MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE;

import static com.android.launcher3.config.FeatureFlags.ENABLE_TRACKPAD_GESTURE;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.MotionEvent;

/** Handles motion events from trackpad. */
public class MotionEventsUtils {

    /** {@link MotionEvent#CLASSIFICATION_MULTI_FINGER_SWIPE} is hidden. */
    public static final int CLASSIFICATION_MULTI_FINGER_SWIPE = 4;

    /** {@link MotionEvent#AXIS_GESTURE_SWIPE_FINGER_COUNT} is hidden. */
    private static final int AXIS_GESTURE_SWIPE_FINGER_COUNT = 53;

    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static boolean isTrackpadScroll(MotionEvent event) {
        return ENABLE_TRACKPAD_GESTURE.get()
                && event.getClassification() == CLASSIFICATION_TWO_FINGER_SWIPE;
    }

    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static boolean isTrackpadMultiFingerSwipe(MotionEvent event) {
        return ENABLE_TRACKPAD_GESTURE.get()
                && event.getClassification() == CLASSIFICATION_MULTI_FINGER_SWIPE;
    }

    public static boolean isTrackpadThreeFingerSwipe(MotionEvent event) {
        return isTrackpadMultiFingerSwipe(event) && event.getAxisValue(
                AXIS_GESTURE_SWIPE_FINGER_COUNT) == 3;
    }

    public static boolean isTrackpadFourFingerSwipe(MotionEvent event) {
        return isTrackpadMultiFingerSwipe(event) && event.getAxisValue(
                AXIS_GESTURE_SWIPE_FINGER_COUNT) == 4;
    }

    public static boolean isTrackpadMotionEvent(MotionEvent event) {
        return isTrackpadScroll(event) || isTrackpadMultiFingerSwipe(event);
    }
}
