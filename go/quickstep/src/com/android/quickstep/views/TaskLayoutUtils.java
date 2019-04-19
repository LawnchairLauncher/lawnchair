/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.views;

import static com.android.quickstep.TaskAdapter.MAX_TASKS_TO_DISPLAY;

import android.content.Context;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;

/**
 * Utils to determine dynamically task and view sizes based off the device height and width.
 */
public final class TaskLayoutUtils {

    private static final float BUTTON_TO_DEVICE_HEIGHT_RATIO = 36.0f/569;
    private static final float BUTTON_WIDTH_TO_HEIGHT_RATIO = 53.0f/18;
    private static final float BUTTON_MARGIN_TO_BUTTON_HEIGHT_RATIO = 5.0f/9;

    private TaskLayoutUtils() {}

    public static int getTaskListHeight(DeviceProfile dp) {
        // TODO: Remove this as task height is determined directly from device height.
        int clearAllSpace = getClearAllButtonHeight(dp) + 2 * getClearAllButtonTopBottomMargin(dp);
        return getDeviceLongWidth(dp) - clearAllSpace;
    }

    /**
     * Calculate task height based off the available height in portrait mode such that when the
     * recents list is full, the total height fills in the available device height perfectly. In
     * landscape mode, we keep the same task height so that tasks scroll off the top.
     *
     * @param context current context
     * @return task height
     */
    public static int getTaskHeight(Context context) {
        final int availableHeight =
                InvariantDeviceProfile.INSTANCE.get(context).portraitProfile.availableHeightPx;
        // TODO: Take into account clear all button height for task height
        return (int) (availableHeight * 1.0f / MAX_TASKS_TO_DISPLAY);
    }

    public static int getClearAllButtonHeight(DeviceProfile dp) {
        return (int) (BUTTON_TO_DEVICE_HEIGHT_RATIO * getDeviceLongWidth(dp));
    }

    public static int getClearAllButtonWidth(DeviceProfile dp) {
        return (int) (BUTTON_WIDTH_TO_HEIGHT_RATIO * getClearAllButtonHeight(dp));
    }

    public static int getClearAllButtonTopBottomMargin(DeviceProfile dp) {
        return (int) (BUTTON_MARGIN_TO_BUTTON_HEIGHT_RATIO * getClearAllButtonHeight(dp));
    }

    private static int getDeviceLongWidth(DeviceProfile dp) {
        return Math.max(dp.availableHeightPx, dp.availableWidthPx);
    }
}
