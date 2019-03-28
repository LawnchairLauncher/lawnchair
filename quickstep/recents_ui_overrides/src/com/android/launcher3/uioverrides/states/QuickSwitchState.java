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

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;

import android.graphics.Rect;

import com.android.launcher3.Launcher;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;

/**
 * State to indicate we are about to launch a recent task. Note that this state is only used when
 * quick switching from launcher; quick switching from an app uses WindowTransformSwipeHelper.
 * @see com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget#NEW_TASK
 */
public class QuickSwitchState extends OverviewState {
    private static final int STATE_FLAGS =
            FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_DISABLE_ACCESSIBILITY;

    public QuickSwitchState(int id) {
        super(id, LauncherLogProto.ContainerType.APP, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    @Override
    public ScaleAndTranslation getOverviewScaleAndTranslation(Launcher launcher) {
        RecentsView recentsView = launcher.getOverviewPanel();
        if (recentsView.getTaskViewCount() == 0) {
            return super.getOverviewScaleAndTranslation(launcher);
        }
        // Compute scale and translation y such that the most recent task view fills the screen.
        TaskThumbnailView dummyThumbnail = recentsView.getTaskViewAt(0).getThumbnail();
        ClipAnimationHelper clipAnimationHelper = new ClipAnimationHelper(launcher);
        clipAnimationHelper.fromTaskThumbnailView(dummyThumbnail, recentsView);
        Rect targetRect = new Rect();
        recentsView.getTaskSize(targetRect);
        clipAnimationHelper.updateTargetRect(targetRect);
        float toScale = clipAnimationHelper.getSourceRect().width()
                / clipAnimationHelper.getTargetRect().width();
        float toTranslationY = clipAnimationHelper.getSourceRect().centerY()
                - clipAnimationHelper.getTargetRect().centerY();
        return new ScaleAndTranslation(toScale, 0, toTranslationY);
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        float shiftRange = launcher.getAllAppsController().getShiftRange();
        float shiftProgress = getVerticalProgress(launcher) - NORMAL.getVerticalProgress(launcher);
        float translationY = shiftProgress * shiftRange;
        return new ScaleAndTranslation(1, 0, translationY);
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        return BACKGROUND_APP.getVerticalProgress(launcher);
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return NONE;
    }

    @Override
    public void onStateTransitionEnd(Launcher launcher) {
        TaskView tasktolaunch = launcher.<RecentsView>getOverviewPanel().getTaskViewAt(0);
        if (tasktolaunch != null) {
            tasktolaunch.launchTask(false);
        } else {
            launcher.getStateManager().goToState(NORMAL);
        }
    }
}
