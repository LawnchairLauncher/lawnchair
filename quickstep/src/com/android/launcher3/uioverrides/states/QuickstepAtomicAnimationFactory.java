/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.view.View.VISIBLE;

import static com.android.launcher3.WorkspaceStateTransitionAnimation.getWorkspaceSpringScaleAnimator;

import android.animation.ValueAnimator;

import androidx.annotation.NonNull;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Workspace;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.AllAppsSwipeController;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.util.RecentsAtomicAnimationFactory;
import com.android.quickstep.views.RecentsView;

/**
 * Animation factory for quickstep specific transitions
 */
public class QuickstepAtomicAnimationFactory extends
        RecentsAtomicAnimationFactory<QuickstepLauncher, LauncherState> {

    // Scale workspace takes before animating in
    private static final float WORKSPACE_PREPARE_SCALE = 0.92f;

    // Due to use of physics, duration may differ between devices so we need to calculate and
    // cache the value.
    private int mHintToNormalDuration = -1;

    public QuickstepAtomicAnimationFactory(QuickstepLauncher activity) {
        super(activity);
    }

    @Override
    protected void applyOverviewToHomeAnimConfig(
            LauncherState fromState,
            StateAnimationConfig config,
            RecentsView<QuickstepLauncher, LauncherState> overview,
            boolean isPinnedTaskbar,
            boolean isThreeButton) {
        super.applyOverviewToHomeAnimConfig(fromState, config, overview, isPinnedTaskbar,
                isThreeButton);
        Workspace<?> workspace = getContainer().getWorkspace();
        // Start from a higher workspace scale, but only if we're invisible so we don't jump.
        boolean isWorkspaceVisible = workspace.getVisibility() == VISIBLE;
        if (isWorkspaceVisible) {
            CellLayout currentChild = (CellLayout) workspace.getChildAt(
                    workspace.getCurrentPage());
            isWorkspaceVisible = currentChild.getVisibility() == VISIBLE
                    && currentChild.getShortcutsAndWidgets().getAlpha() > 0;
        }
        if (!isWorkspaceVisible) {
            workspace.setScaleX(WORKSPACE_PREPARE_SCALE);
            workspace.setScaleY(WORKSPACE_PREPARE_SCALE);
        }
        Hotseat hotseat = getContainer().getHotseat();
        boolean isHotseatVisible = hotseat.getVisibility() == VISIBLE && hotseat.getAlpha() > 0;
        if (!isHotseatVisible) {
            hotseat.setScaleX(WORKSPACE_PREPARE_SCALE);
            hotseat.setScaleY(WORKSPACE_PREPARE_SCALE);
        }
    }

    @Override
    protected int getHintToNormalAnimationDuration(@NonNull LauncherState toState) {
        if (mHintToNormalDuration == -1) {
            QuickstepLauncher container = getContainer();
            ValueAnimator va = getWorkspaceSpringScaleAnimator(
                    container,
                    container.getWorkspace(),
                    toState.getWorkspaceScaleAndTranslation(container).scale);
            mHintToNormalDuration = (int) va.getDuration();
        }
        return mHintToNormalDuration;
    }

    @Override
    protected void applyAllAppsToNormalConfig(@NonNull StateAnimationConfig config) {
        super.applyAllAppsToNormalConfig(config);
        AllAppsSwipeController.applyAllAppsToNormalConfig(getContainer(), config);
    }

    @Override
    protected void applyNormalToAllAppsAnimConfig(@NonNull StateAnimationConfig config) {
        super.applyNormalToAllAppsAnimConfig(config);
        AllAppsSwipeController.applyNormalToAllAppsAnimConfig(getContainer(), config);
    }
}
