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

package com.android.wm.shell.common.split;

import static com.android.wm.shell.common.split.SplitScreenConstants.CONTROLLED_ACTIVITY_TYPES;
import static com.android.wm.shell.common.split.SplitScreenConstants.CONTROLLED_WINDOWING_MODES;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;

import androidx.annotation.Nullable;

import com.android.internal.util.ArrayUtils;
import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;

/** Helper utility class for split screen components to use. */
public class SplitScreenUtils {
    /** Reverse the split position. */
    @SplitScreenConstants.SplitPosition
    public static int reverseSplitPosition(@SplitScreenConstants.SplitPosition int position) {
        switch (position) {
            case SPLIT_POSITION_TOP_OR_LEFT:
                return SPLIT_POSITION_BOTTOM_OR_RIGHT;
            case SPLIT_POSITION_BOTTOM_OR_RIGHT:
                return SPLIT_POSITION_TOP_OR_LEFT;
            case SPLIT_POSITION_UNDEFINED:
            default:
                return SPLIT_POSITION_UNDEFINED;
        }
    }

    /** Returns true if the task is valid for split screen. */
    public static boolean isValidToSplit(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo != null && taskInfo.supportsMultiWindow
                && ArrayUtils.contains(CONTROLLED_ACTIVITY_TYPES, taskInfo.getActivityType())
                && ArrayUtils.contains(CONTROLLED_WINDOWING_MODES, taskInfo.getWindowingMode());
    }

    /** Retrieve package name from an intent */
    @Nullable
    public static String getPackageName(Intent intent) {
        if (intent == null || intent.getComponent() == null) {
            return null;
        }
        return intent.getComponent().getPackageName();
    }

    /** Retrieve package name from a PendingIntent */
    @Nullable
    public static String getPackageName(PendingIntent pendingIntent) {
        if (pendingIntent == null || pendingIntent.getIntent() == null) {
            return null;
        }
        return getPackageName(pendingIntent.getIntent());
    }

    /** Retrieve package name from a taskId */
    @Nullable
    public static String getPackageName(int taskId, ShellTaskOrganizer taskOrganizer) {
        final ActivityManager.RunningTaskInfo taskInfo = taskOrganizer.getRunningTaskInfo(taskId);
        return taskInfo != null ? getPackageName(taskInfo.baseIntent) : null;
    }

    /** Retrieve user id from a taskId */
    public static int getUserId(int taskId, ShellTaskOrganizer taskOrganizer) {
        final ActivityManager.RunningTaskInfo taskInfo = taskOrganizer.getRunningTaskInfo(taskId);
        return taskInfo != null ? taskInfo.userId : -1;
    }

    /** Generates a common log message for split screen failures */
    public static String splitFailureMessage(String caller, String reason) {
        return "(" + caller + ") Splitscreen aborted: " + reason;
    }

    /**
     * Returns whether left/right split is allowed in portrait.
     */
    public static boolean allowLeftRightSplitInPortrait(Resources res) {
        return Flags.enableLeftRightSplitInPortrait() && res.getBoolean(
                com.android.internal.R.bool.config_leftRightSplitInPortrait);
    }

    /**
     * Returns whether left/right split is supported in the given configuration.
     */
    public static boolean isLeftRightSplit(boolean allowLeftRightSplitInPortrait,
            Configuration config) {
        // Compare the max bounds sizes as on near-square devices, the insets may result in a
        // configuration in the other orientation
        final boolean isLargeScreen = config.smallestScreenWidthDp >= 600;
        final Rect maxBounds = config.windowConfiguration.getMaxBounds();
        final boolean isLandscape = maxBounds.width() >= maxBounds.height();
        return isLeftRightSplit(allowLeftRightSplitInPortrait, isLargeScreen, isLandscape);
    }

    /**
     * Returns whether left/right split is supported in the given configuration state. This method
     * is useful for cases where we need to calculate this given last saved state.
     */
    public static boolean isLeftRightSplit(boolean allowLeftRightSplitInPortrait,
            boolean isLargeScreen, boolean isLandscape) {
        if (allowLeftRightSplitInPortrait && isLargeScreen) {
            return !isLandscape;
        } else {
            return isLandscape;
        }
    }

    /** Returns the specified background color that matches a RunningTaskInfo. */
    public static Color getResizingBackgroundColor(ActivityManager.RunningTaskInfo taskInfo) {
        final int taskBgColor = taskInfo.taskDescription.getBackgroundColor();
        return Color.valueOf(taskBgColor == -1 ? Color.WHITE : taskBgColor);
    }
}
