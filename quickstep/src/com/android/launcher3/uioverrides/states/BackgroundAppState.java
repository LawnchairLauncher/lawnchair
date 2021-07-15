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
package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_BACKGROUND;

import android.content.Context;
import android.graphics.Color;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;

/**
 * State indicating that the Launcher is behind an app
 */
public class BackgroundAppState extends OverviewState {

    private static final int STATE_FLAGS = FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI
            | FLAG_WORKSPACE_INACCESSIBLE | FLAG_NON_INTERACTIVE | FLAG_CLOSE_POPUPS;

    public BackgroundAppState(int id) {
        this(id, LAUNCHER_STATE_BACKGROUND);
    }

    protected BackgroundAppState(int id, int logContainer) {
        super(id, logContainer, STATE_FLAGS);
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            return super.getVerticalProgress(launcher);
        }
        RecentsView recentsView = launcher.getOverviewPanel();
        int transitionLength = LayoutUtils.getShelfTrackingDistance(launcher,
                launcher.getDeviceProfile(),
                recentsView.getPagedOrientationHandler());
        AllAppsTransitionController controller = launcher.getAllAppsController();
        float scrollRange = Math.max(controller.getShiftRange(), 1);
        float progressDelta = (transitionLength / scrollRange);
        return super.getVerticalProgress(launcher) + progressDelta;
    }

    @Override
    public float[] getOverviewScaleAndOffset(Launcher launcher) {
        return getOverviewScaleAndOffsetForBackgroundState(launcher);
    }

    @Override
    public float getOverviewFullscreenProgress() {
        return 1;
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return super.getVisibleElements(launcher)
                & ~OVERVIEW_ACTIONS
                & ~CLEAR_ALL_BUTTON
                & ~VERTICAL_SWIPE_INDICATOR
                | TASKBAR;
    }

    @Override
    public boolean displayOverviewTasksAsGrid(DeviceProfile deviceProfile) {
        return false;
    }

    @Override
    protected float getDepthUnchecked(Context context) {
        return 1;
    }

    @Override
    public int getWorkspaceScrimColor(Launcher launcher) {
        return Color.TRANSPARENT;
    }

    public static float[] getOverviewScaleAndOffsetForBackgroundState(
            BaseDraggingActivity activity) {
        return new float[] {
                ((RecentsView) activity.getOverviewPanel()).getMaxScaleForFullScreen(),
                NO_OFFSET};
    }
}
