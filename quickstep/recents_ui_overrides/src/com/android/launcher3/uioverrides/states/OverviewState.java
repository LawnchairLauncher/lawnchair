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

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;
import static com.android.launcher3.anim.Interpolators.DEACCEL_2;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.logging.LoggerUtils.getTargetStr;
import static com.android.launcher3.logging.LoggerUtils.newContainerTarget;
import static com.android.launcher3.states.RotationHelper.REQUEST_ROTATE;

import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    protected static final Rect sTempRect = new Rect();

    private static final int STATE_FLAGS = FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_DISABLE_ACCESSIBILITY;

    public OverviewState(int id) {
        this(id, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    protected OverviewState(int id, int transitionDuration, int stateFlags) {
        this(id, ContainerType.TASKSWITCHER, transitionDuration, stateFlags);
    }

    protected OverviewState(int id, int logContainer, int transitionDuration, int stateFlags) {
        super(id, logContainer, transitionDuration, stateFlags);
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        RecentsView recentsView = launcher.getOverviewPanel();
        Workspace workspace = launcher.getWorkspace();
        View workspacePage = workspace.getPageAt(workspace.getCurrentPage());
        float workspacePageWidth = workspacePage != null && workspacePage.getWidth() != 0
                ? workspacePage.getWidth() : launcher.getDeviceProfile().availableWidthPx;
        recentsView.getTaskSize(sTempRect);
        float scale = (float) sTempRect.width() / workspacePageWidth;
        float parallaxFactor = 0.5f;
        return new ScaleAndTranslation(scale, 0, -getDefaultSwipeHeight(launcher) * parallaxFactor);
    }

    @Override
    public ScaleAndTranslation getOverviewScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(1f, 0f, 0f);
    }

    @Override
    public void onStateEnabled(Launcher launcher) {
        RecentsView rv = launcher.getOverviewPanel();
        rv.setOverviewStateEnabled(true);
        AbstractFloatingView.closeAllOpenViews(launcher);
    }

    @Override
    public void onStateDisabled(Launcher launcher) {
        RecentsView rv = launcher.getOverviewPanel();
        rv.setOverviewStateEnabled(false);
    }

    @Override
    public void onStateTransitionEnd(Launcher launcher) {
        launcher.getRotationHelper().setCurrentStateRequest(REQUEST_ROTATE);
        DiscoveryBounce.showForOverviewIfNeeded(launcher);
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
        if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            return VERTICAL_SWIPE_INDICATOR;
        } else {
            return HOTSEAT_SEARCH_BOX | VERTICAL_SWIPE_INDICATOR |
                    (launcher.getAppsView().getFloatingHeaderView().hasVisibleContent()
                            ? ALL_APPS_HEADER_EXTRA : HOTSEAT_ICONS);
        }
    }

    @Override
    public float getWorkspaceScrimAlpha(Launcher launcher) {
        return 0.5f;
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        if ((getVisibleElements(launcher) & ALL_APPS_HEADER_EXTRA) == 0) {
            // We have no all apps content, so we're still at the fully down progress.
            return super.getVerticalProgress(launcher);
        }
        return 1 - (getDefaultSwipeHeight(launcher)
                / launcher.getAllAppsController().getShiftRange());
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getString(R.string.accessibility_desc_recent_apps);
    }

    public static float getDefaultSwipeHeight(Launcher launcher) {
        return getDefaultSwipeHeight(launcher.getDeviceProfile());
    }

    public static float getDefaultSwipeHeight(DeviceProfile dp) {
        return dp.allAppsCellHeightPx - dp.allAppsIconTextSizePx;
    }

    @Override
    public void onBackPressed(Launcher launcher) {
        TaskView taskView = launcher.<RecentsView>getOverviewPanel().getRunningTaskView();
        if (taskView != null) {
            launcher.getUserEventDispatcher().logActionCommand(Action.Command.BACK,
                    newContainerTarget(ContainerType.OVERVIEW));
            taskView.launchTask(true);
        } else {
            super.onBackPressed(launcher);
        }
    }

    public static OverviewState newBackgroundState(int id) {
        return new BackgroundAppState(id);
    }

    public static OverviewState newPeekState(int id) {
        return new OverviewPeekState(id);
    }

    public static OverviewState newSwitchState(int id) {
        return new QuickSwitchState(id);
    }
}
