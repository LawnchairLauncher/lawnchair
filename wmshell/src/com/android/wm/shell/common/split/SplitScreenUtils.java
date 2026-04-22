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

import static com.android.wm.shell.shared.split.SplitScreenConstants.CONTROLLED_ACTIVITY_TYPES;
import static com.android.wm.shell.shared.split.SplitScreenConstants.CONTROLLED_WINDOWING_MODES;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_10_45_45;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_45_45_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;

import android.app.ActivityManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;

import com.android.internal.util.ArrayUtils;
import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.shared.split.SplitScreenConstants;

/** Helper utility class for split screen components to use. */
public class SplitScreenUtils {
    private static final int LARGE_SCREEN_MIN_EDGE_DP = 600;

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
        return res.getBoolean(com.android.internal.R.bool.config_leftRightSplitInPortrait);
    }

    /**
     * Returns whether left/right split is supported in the given configuration.
     */
    public static boolean isLeftRightSplit(boolean allowLeftRightSplitInPortrait,
            Configuration config) {
        // Compare the max bounds sizes as on near-square devices, the insets may result in a
        // configuration in the other orientation
        final Rect maxBounds = config.windowConfiguration.getMaxBounds();
        final boolean isLandscape = maxBounds.width() >= maxBounds.height();
        return isLeftRightSplit(allowLeftRightSplitInPortrait, isLargeScreen(config), isLandscape);
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

    /**
     * Returns whether the current config is a large screen (tablet or unfolded foldable)
     */
    public static boolean isLargeScreen(Configuration config) {
        return config.smallestScreenWidthDp >= LARGE_SCREEN_MIN_EDGE_DP;
    }

    /**
     * Convenience function for {@link #isLargeScreen(Configuration)}.
     */
    public static boolean isLargeScreen(Resources res) {
        return isLargeScreen(res.getConfiguration());
    }

    /**
     * Returns whether the current device is a foldable
     */
    public static boolean isFoldable(Resources res) {
        return res.getIntArray(com.android.internal.R.array.config_foldedDeviceStates).length != 0;
    }

    /**
     * Returns whether we should allow split ratios to go offscreen or not. If the device is a phone
     * or a foldable (either screen), we allow it.
     */
    public static boolean allowOffscreenRatios(Resources res) {
        return Flags.enableFlexibleTwoAppSplit() && (!isLargeScreen(res) || isFoldable(res));
    }

    /**
     * Within a particular split layout, we label the stages numerically: 0, 1, 2... from left to
     * right (or top to bottom). This function takes in a stage index (0th, 1st, 2nd...) and a
     * PersistentSnapPosition and returns if that particular stage is offscreen in that layout.
     */
    public static boolean isPartiallyOffscreen(int stageIndex,
            @SplitScreenConstants.PersistentSnapPosition int snapPosition) {
        switch(snapPosition) {
            case SNAP_TO_2_10_90:
            case SNAP_TO_3_10_45_45:
                return stageIndex == 0;
            case SNAP_TO_2_90_10:
                return stageIndex == 1;
            case SNAP_TO_3_45_45_10:
                return stageIndex == 2;
            default:
                return false;
        }
    }
}
