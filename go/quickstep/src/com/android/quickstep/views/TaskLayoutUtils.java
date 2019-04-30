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

import com.android.launcher3.InvariantDeviceProfile;

/**
 * Utils to determine dynamically task and view sizes based off the device height and width.
 */
public final class TaskLayoutUtils {

    private static final float CLEAR_ALL_ITEM_TO_HEIGHT_RATIO = 7.0f / 64;

    private TaskLayoutUtils() {}

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
        final int availableTaskSpace = availableHeight - getClearAllItemHeight(context);
        return (int) (availableTaskSpace * 1.0f / MAX_TASKS_TO_DISPLAY);
    }

    /**
     * Calculate clear all item height scaled to available height in portrait mode.
     *
     * @param context current context
     * @return clear all item height
     */
    public static int getClearAllItemHeight(Context context) {
        final int availableHeight =
                InvariantDeviceProfile.INSTANCE.get(context).portraitProfile.availableHeightPx;
        return (int) (CLEAR_ALL_ITEM_TO_HEIGHT_RATIO * availableHeight);
    }
}
