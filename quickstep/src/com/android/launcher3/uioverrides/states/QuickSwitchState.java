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
package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_BACKGROUND;
import static com.android.window.flags.Flags.enableDesktopWindowingMode;

import android.graphics.Color;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

/**
 * State to indicate we are about to launch a recent task. Note that this state is only used when
 * quick switching from launcher; quick switching from an app uses LauncherSwipeHandler.
 * @see com.android.quickstep.GestureState.GestureEndTarget#NEW_TASK
 */
public class QuickSwitchState extends BackgroundAppState {

    public QuickSwitchState(int id) {
        super(id, LAUNCHER_STATE_BACKGROUND);
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        float shiftRange = launcher.getAllAppsController().getShiftRange();
        float shiftProgress = getVerticalProgress(launcher) - NORMAL.getVerticalProgress(launcher);
        float translationY = shiftProgress * shiftRange;
        return new ScaleAndTranslation(0.9f, 0, translationY);
    }

    @Override
    public int getWorkspaceScrimColor(Launcher launcher) {
        if (enableDesktopWindowingMode()) {
            if (launcher.areFreeformTasksVisible()) {
                // No scrim while freeform tasks are visible
                return Color.TRANSPARENT;
            }
        }
        DeviceProfile dp = launcher.getDeviceProfile();
        if (dp.isTaskbarPresentInApps) {
            return launcher.getColor(R.color.taskbar_background);
        }
        return Themes.getAttrColor(launcher, R.attr.overviewScrimColor);
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        // Don't move all apps shelf while quick-switching (just let it fade).
        return 1f;
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return NONE;
    }

    @Override
    public boolean isTaskbarStashed(Launcher launcher) {
        return !launcher.getDeviceProfile().isTaskbarPresentInApps;
    }

    @Override
    public boolean isTaskbarAlignedWithHotseat(Launcher launcher) {
        return false;
    }
}
