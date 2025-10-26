/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;

public class LayoutUtils {

    private static final float SQUARE_ASPECT_RATIO_TOLERANCE = 0.05f;

    /**
     * The height for the swipe up motion
     */
    public static float getDefaultSwipeHeight(Context context, DeviceProfile dp) {
        float swipeHeight = dp.allAppsCellHeightPx - dp.allAppsIconTextSizePx;
        if (DisplayController.getNavigationMode(context) == NavigationMode.NO_BUTTON) {
            swipeHeight -= dp.getInsets().bottom;
        }
        return swipeHeight;
    }

    public static int getShelfTrackingDistance(
            Context context,
            DeviceProfile dp,
            RecentsPagedOrientationHandler orientationHandler,
            BaseContainerInterface<?, ?> baseContainerInterface) {
        // Track the bottom of the window.
        Rect taskSize = new Rect();
        baseContainerInterface.calculateTaskSize(context, dp, taskSize,
                orientationHandler);
        return orientationHandler.getDistanceToBottomOfRect(dp, taskSize);
    }

    /**
     * Recursively sets view and all children enabled/disabled.
     * @param view Top most parent view to change.
     * @param enabled True = enable, False = disable.
     */
    public static void setViewEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                setViewEnabled(((ViewGroup) view).getChildAt(i), enabled);
            }
        }
    }

    /**
     * Returns true iff the device's aspect ratio is within
     * {@link LayoutUtils#SQUARE_ASPECT_RATIO_TOLERANCE} of 1:1
     */
    public static boolean isAspectRatioSquare(float aspectRatio) {
        return Float.compare(aspectRatio, 1f - SQUARE_ASPECT_RATIO_TOLERANCE) >= 0
                && Float.compare(aspectRatio, 1f + SQUARE_ASPECT_RATIO_TOLERANCE) <= 0;
    }
}
