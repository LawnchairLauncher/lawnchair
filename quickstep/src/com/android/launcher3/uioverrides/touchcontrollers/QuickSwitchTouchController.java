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
package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.app.animation.Interpolators.ACCELERATE_2;
import static com.android.app.animation.Interpolators.DECELERATE_2;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.QUICK_SWITCH_FROM_HOME;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_BACKGROUND;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE;
import static com.android.launcher3.util.SystemUiController.UI_STATE_FULLSCREEN_TASK;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;

import android.view.MotionEvent;

import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

/**
 * Handles quick switching to a recent task from the home screen.
 */
public class QuickSwitchTouchController extends AbstractStateChangeTouchController {

    protected final RecentsView mOverviewPanel;

    public QuickSwitchTouchController(QuickstepLauncher launcher) {
        this(launcher, SingleAxisSwipeDetector.HORIZONTAL);
    }

    protected QuickSwitchTouchController(QuickstepLauncher l,
            SingleAxisSwipeDetector.Direction dir) {
        super(l, dir);
        mOverviewPanel = l.getOverviewPanel();
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            return true;
        }
        if (!mLauncher.isInState(LauncherState.NORMAL)) {
            return false;
        }
        if ((ev.getEdgeFlags() & Utilities.EDGE_NAV_BAR) == 0) {
            return false;
        }
        return true;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        long stateFlags = SystemUiProxy.INSTANCE.get(mLauncher).getLastSystemUiStateFlags();
        if ((stateFlags & SYSUI_STATE_OVERVIEW_DISABLED) != 0) {
            return NORMAL;
        }
        return isDragTowardPositive ? QUICK_SWITCH_FROM_HOME : NORMAL;
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        super.onDragStart(start, startDisplacement);
        mStartContainerType = LAUNCHER_STATE_BACKGROUND;
        TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState) {
        super.onSwipeInteractionCompleted(targetState);
    }

    @Override
    protected float initCurrentAnimation() {
        StateAnimationConfig config = new StateAnimationConfig();
        setupInterpolators(config);
        config.duration = (long) (getShiftRange() * 2);

        // Set RecentView's initial properties for coming in from the side.
        RECENTS_SCALE_PROPERTY.set(mOverviewPanel,
                QUICK_SWITCH_FROM_HOME.getOverviewScaleAndOffset(mLauncher)[0] * 0.85f);
        ADJACENT_PAGE_HORIZONTAL_OFFSET.set(mOverviewPanel, 1f);
        mOverviewPanel.setContentAlpha(1);

        mCurrentAnimation = mLauncher.getStateManager()
                .createAnimationToNewWorkspace(mToState, config);
        mCurrentAnimation.getTarget().addListener(mClearStateOnCancelListener);
        mCurrentAnimation.getAnimationPlayer().addUpdateListener(valueAnimator ->
                updateFullscreenProgress((Float) valueAnimator.getAnimatedValue()));
        return 1 / getShiftRange();
    }

    private void setupInterpolators(StateAnimationConfig stateAnimationConfig) {
        stateAnimationConfig.setInterpolator(ANIM_WORKSPACE_FADE, DECELERATE_2);
        stateAnimationConfig.setInterpolator(ANIM_ALL_APPS_FADE, DECELERATE_2);
        if (DisplayController.getNavigationMode(mLauncher) == NavigationMode.NO_BUTTON) {
            // Overview lives to the left of workspace, so translate down later than over
            stateAnimationConfig.setInterpolator(ANIM_WORKSPACE_TRANSLATE, ACCELERATE_2);
            stateAnimationConfig.setInterpolator(ANIM_VERTICAL_PROGRESS, ACCELERATE_2);
            stateAnimationConfig.setInterpolator(ANIM_OVERVIEW_SCALE, ACCELERATE_2);
            stateAnimationConfig.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, ACCELERATE_2);
            stateAnimationConfig.setInterpolator(ANIM_OVERVIEW_FADE, INSTANT);
        } else {
            stateAnimationConfig.setInterpolator(ANIM_WORKSPACE_TRANSLATE, LINEAR);
            stateAnimationConfig.setInterpolator(ANIM_VERTICAL_PROGRESS, LINEAR);
        }
    }

    @Override
    protected void updateProgress(float progress) {
        super.updateProgress(progress);
        updateFullscreenProgress(Utilities.boundToRange(progress, 0, 1));
    }

    private void updateFullscreenProgress(float progress) {
        mOverviewPanel.setFullscreenProgress(progress);
        if (progress > UPDATE_SYSUI_FLAGS_THRESHOLD) {
            int sysuiFlags = 0;
            TaskView tv = mOverviewPanel.getTaskViewAt(0);
            if (tv != null) {
                sysuiFlags = tv.getFirstThumbnailViewDeprecated().getSysUiStatusNavFlags();
            }
            mLauncher.getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, sysuiFlags);
        } else {
            mLauncher.getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, 0);
        }
    }

    @Override
    protected float getShiftRange() {
        return mLauncher.getDeviceProfile().widthPx / 2f;
    }
}
