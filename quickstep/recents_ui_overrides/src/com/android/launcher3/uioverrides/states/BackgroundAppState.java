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

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

/**
 * State indicating that the Launcher is behind an app
 */
public class BackgroundAppState extends OverviewState {

    private static final int STATE_FLAGS =
            FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_DISABLE_ACCESSIBILITY
                    | FLAG_DISABLE_INTERACTION;

    public BackgroundAppState(int id) {
        this(id, LauncherLogProto.ContainerType.TASKSWITCHER);
    }

    protected BackgroundAppState(int id, int logContainer) {
        super(id, logContainer, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    @Override
    public void onStateEnabled(Launcher launcher) {
        AbstractFloatingView.closeAllOpenViews(launcher, false);
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            return super.getVerticalProgress(launcher);
        }
        int transitionLength = LayoutUtils.getShelfTrackingDistance(launcher,
                launcher.getDeviceProfile());
        AllAppsTransitionController controller = launcher.getAllAppsController();
        float scrollRange = Math.max(controller.getShiftRange(), 1);
        float progressDelta = (transitionLength / scrollRange);
        return super.getVerticalProgress(launcher) + progressDelta;
    }

    @Override
    public ScaleAndTranslation getOverviewScaleAndTranslation(Launcher launcher) {
        // Initialize the recents view scale to what it would be when starting swipe up
        RecentsView recentsView = launcher.getOverviewPanel();
        int taskCount = recentsView.getTaskViewCount();
        if (taskCount == 0) {
            return super.getOverviewScaleAndTranslation(launcher);
        }
        TaskView dummyTask;
        if (recentsView.getCurrentPage() >= 0) {
            if (recentsView.getCurrentPage() <= taskCount - 1) {
                dummyTask = recentsView.getCurrentPageTaskView();
            } else {
                dummyTask = recentsView.getTaskViewAt(taskCount - 1);
            }
        } else {
            dummyTask = recentsView.getTaskViewAt(0);
        }
        return recentsView.getTempClipAnimationHelper().updateForFullscreenOverview(dummyTask)
                .getScaleAndTranslation();
    }

    @Override
    public float getOverviewFullscreenProgress() {
        return 1;
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return super.getVisibleElements(launcher)
                & ~RECENTS_CLEAR_ALL_BUTTON & ~VERTICAL_SWIPE_INDICATOR;
    }

    @Override
    public ScaleAndTranslation getHotseatScaleAndTranslation(Launcher launcher) {
        if ((getVisibleElements(launcher) & HOTSEAT_ICONS) != 0) {
            // Translate hotseat offscreen if we show it in overview.
            ScaleAndTranslation scaleAndTranslation = super.getHotseatScaleAndTranslation(launcher);
            scaleAndTranslation.translationY += LayoutUtils.getShelfTrackingDistance(launcher,
                    launcher.getDeviceProfile());
            return scaleAndTranslation;
        }
        return super.getHotseatScaleAndTranslation(launcher);
    }
}
