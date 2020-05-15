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
import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_ACTIONS;
import static com.android.launcher3.logging.LoggerUtils.newContainerTarget;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;
import static com.android.quickstep.SysUINavigationMode.hideShelfInTwoButtonLandscape;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    protected static final Rect sTempRect = new Rect();

    private static final int STATE_FLAGS = FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_WORKSPACE_INACCESSIBLE
            | FLAG_CLOSE_POPUPS;

    public OverviewState(int id) {
        this(id, STATE_FLAGS);
    }

    protected OverviewState(int id, int stateFlags) {
        this(id, ContainerType.TASKSWITCHER, stateFlags);
    }

    protected OverviewState(int id, int logContainer, int stateFlags) {
        super(id, logContainer, stateFlags);
    }

    @Override
    public int getTransitionDuration(Context context) {
        // In no-button mode, overview comes in all the way from the left, so give it more time.
        boolean isNoButtonMode = SysUINavigationMode.INSTANCE.get(context).getMode() == NO_BUTTON;
        return isNoButtonMode && ENABLE_OVERVIEW_ACTIONS.get() ? 380 : 250;
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
    public ScaleAndTranslation getHotseatScaleAndTranslation(Launcher launcher) {
        if ((getVisibleElements(launcher) & HOTSEAT_ICONS) != 0) {
            DeviceProfile dp = launcher.getDeviceProfile();
            if (dp.allAppsIconSizePx >= dp.iconSizePx) {
                return new ScaleAndTranslation(1, 0, 0);
            } else {
                float scale = ((float) dp.allAppsIconSizePx) / dp.iconSizePx;
                // Distance between the screen center (which is the pivotY for hotseat) and the
                // bottom of the hotseat (which we want to preserve)
                float distanceFromBottom = dp.heightPx / 2 - dp.hotseatBarBottomPaddingPx;
                // On scaling, the bottom edge is moved closer to the pivotY. We move the
                // hotseat back down so that the bottom edge's position is preserved.
                float translationY = distanceFromBottom * (1 - scale);
                return new ScaleAndTranslation(scale, 0, translationY);
            }
        }
        return getWorkspaceScaleAndTranslation(launcher);
    }

    @Override
    public float[] getOverviewScaleAndOffset(Launcher launcher) {
        return new float[] {NO_SCALE, NO_OFFSET};
    }

    @Override
    public ScaleAndTranslation getQsbScaleAndTranslation(Launcher launcher) {
        if (this == OVERVIEW && ENABLE_OVERVIEW_ACTIONS.get()
                && removeShelfFromOverview(launcher)) {
            // Treat the QSB as part of the hotseat so they move together.
            return getHotseatScaleAndTranslation(launcher);
        }
        return super.getQsbScaleAndTranslation(launcher);
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
        RecentsView recentsView = launcher.getOverviewPanel();
        if (ENABLE_OVERVIEW_ACTIONS.get() && removeShelfFromOverview(launcher) ||
                hideShelfInTwoButtonLandscape(launcher, recentsView.getPagedOrientationHandler())) {
            return OVERVIEW_BUTTONS;
        } else if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            return VERTICAL_SWIPE_INDICATOR | OVERVIEW_BUTTONS;
        } else {
            boolean hasAllAppsHeaderExtra = launcher.getAppsView() != null
                    && launcher.getAppsView().getFloatingHeaderView().hasVisibleContent();
            return HOTSEAT_SEARCH_BOX | VERTICAL_SWIPE_INDICATOR | OVERVIEW_BUTTONS
                    | (hasAllAppsHeaderExtra ? ALL_APPS_HEADER_EXTRA : HOTSEAT_ICONS);
        }
    }

    @Override
    public float getOverviewScrimAlpha(Launcher launcher) {
        return 0.5f;
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        if ((getVisibleElements(launcher) & ALL_APPS_HEADER_EXTRA) == 0) {
            // We have no all apps content, so we're still at the fully down progress.
            return super.getVerticalProgress(launcher);
        }
        return getDefaultVerticalProgress(launcher);
    }

    public static float getDefaultVerticalProgress(Launcher launcher) {
        return 1 - (getDefaultSwipeHeight(launcher)
                / launcher.getAllAppsController().getShiftRange());
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
        return 1f;
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

    /**
     *  New Overview substate that represents the overview in modal mode (one task shown on its own)
     */
    public static OverviewState newModalTaskState(int id) {
        return new OverviewModalTaskState(id);
    }
}
