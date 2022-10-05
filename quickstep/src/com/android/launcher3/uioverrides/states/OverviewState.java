/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.launcher3.anim.Interpolators.DEACCEL_2;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemProperties;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Themes;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    private static final int OVERVIEW_SLIDE_IN_DURATION = 380;
    private static final int OVERVIEW_POP_IN_DURATION = 250;
    private static final int OVERVIEW_EXIT_DURATION = 250;

    protected static final Rect sTempRect = new Rect();

    private static final int STATE_FLAGS = FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_WORKSPACE_INACCESSIBLE
            | FLAG_CLOSE_POPUPS;

    public OverviewState(int id) {
        this(id, STATE_FLAGS);
    }

    protected OverviewState(int id, int stateFlags) {
        this(id, LAUNCHER_STATE_OVERVIEW, stateFlags);
    }

    protected OverviewState(int id, int logContainer, int stateFlags) {
        super(id, logContainer, stateFlags);
    }

    @Override
    public int getTransitionDuration(Context context, boolean isToState) {
        if (isToState) {
            // In gesture modes, overview comes in all the way from the side, so give it more time.
            return DisplayController.getNavigationMode(context).hasGestures
                    ? OVERVIEW_SLIDE_IN_DURATION
                    : OVERVIEW_POP_IN_DURATION;
        } else {
            // When exiting Overview, exit quickly.
            return OVERVIEW_EXIT_DURATION;
        }
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        RecentsView recentsView = launcher.getOverviewPanel();
        float workspacePageHeight = launcher.getDeviceProfile().getCellLayoutHeight();
        recentsView.getTaskSize(sTempRect);
        float scale = (float) sTempRect.height() / workspacePageHeight;
        float parallaxFactor = 0.5f;
        return new ScaleAndTranslation(scale, 0, -getDefaultSwipeHeight(launcher) * parallaxFactor);
    }

    @Override
    public float[] getOverviewScaleAndOffset(Launcher launcher) {
        return new float[] {NO_SCALE, NO_OFFSET};
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        return new PageAlphaProvider(DEACCEL_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return 0;
            }
        };
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return CLEAR_ALL_BUTTON | OVERVIEW_ACTIONS;
    }

    @Override
    public boolean isTaskbarStashed(Launcher launcher) {
        return !FeatureFlags.ENABLE_TASKBAR_IN_OVERVIEW.get();
    }

    @Override
    public boolean isTaskbarAlignedWithHotseat(Launcher launcher) {
        return !FeatureFlags.ENABLE_TASKBAR_IN_OVERVIEW.get();
    }

    @Override
    public int getWorkspaceScrimColor(Launcher launcher) {
        return Themes.getAttrColor(launcher, R.attr.overviewScrimColor);
    }

    @Override
    public boolean displayOverviewTasksAsGrid(DeviceProfile deviceProfile) {
        return deviceProfile.isTablet;
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getString(R.string.accessibility_recent_apps);
    }

    public static float getDefaultSwipeHeight(Launcher launcher) {
        return LayoutUtils.getDefaultSwipeHeight(launcher, launcher.getDeviceProfile());
    }

    @Override
    protected float getDepthUnchecked(Context context) {
        //TODO revert when b/178661709 is fixed
        return SystemProperties.getBoolean("ro.launcher.depth.overview", true) ? 1 : 0;
    }

    @Override
    public void onBackPressed(Launcher launcher) {
        RecentsView recentsView = launcher.getOverviewPanel();
        TaskView taskView = recentsView.getRunningTaskView();
        if (taskView != null) {
            if (recentsView.isTaskViewFullyVisible(taskView)) {
                taskView.launchTasks();
            } else {
                recentsView.snapToPage(recentsView.indexOfChild(taskView));
            }
        } else {
            super.onBackPressed(launcher);
        }
    }

    public static OverviewState newBackgroundState(int id) {
        return new BackgroundAppState(id);
    }

    public static OverviewState newSwitchState(int id) {
        return new QuickSwitchState(id);
    }

    /**
     *  New Overview substate that represents the overview in modal mode (one task shown on its own)
     */
    public static OverviewState newModalTaskState(int id) {
        return new OverviewModalTaskState(id);
    }

    /**
     * New Overview substate representing state where 1 app for split screen has been selected and
     * pinned and user is selecting the second one
     */
    public static OverviewState newSplitSelectState(int id) {
        return new SplitScreenSelectState(id);
    }
}
