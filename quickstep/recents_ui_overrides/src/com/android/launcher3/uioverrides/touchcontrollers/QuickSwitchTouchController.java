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

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.QUICK_SWITCH;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.DEACCEL_2;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE;
import static com.android.launcher3.util.SystemUiController.UI_STATE_OVERVIEW;
import static com.android.quickstep.views.RecentsView.UPDATE_SYSUI_FLAGS_THRESHOLD;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;

import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.ActivityManagerWrapper;

/**
 * Handles quick switching to a recent task from the home screen.
 */
public class QuickSwitchTouchController extends AbstractStateChangeTouchController {

    protected final RecentsView mOverviewPanel;

    public QuickSwitchTouchController(Launcher launcher) {
        this(launcher, SingleAxisSwipeDetector.HORIZONTAL);
    }

    protected QuickSwitchTouchController(Launcher l, SingleAxisSwipeDetector.Direction dir) {
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
        int stateFlags = SystemUiProxy.INSTANCE.get(mLauncher).getLastSystemUiStateFlags();
        if ((stateFlags & SYSUI_STATE_OVERVIEW_DISABLED) != 0) {
            return NORMAL;
        }
        return isDragTowardPositive ? QUICK_SWITCH : NORMAL;
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        super.onDragStart(start, startDisplacement);
        mStartContainerType = LauncherLogProto.ContainerType.NAVBAR;
        ActivityManagerWrapper.getInstance()
                .closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        super.onSwipeInteractionCompleted(targetState, logAction);
    }

    @Override
    protected float initCurrentAnimation(int animComponents) {
        StateAnimationConfig config = new StateAnimationConfig();
        setupInterpolators(config);
        config.duration = (long) (getShiftRange() * 2);
        mCurrentAnimation = mLauncher.getStateManager()
                .createAnimationToNewWorkspace(mToState, config)
                .setOnCancelRunnable(this::clearState);
        mCurrentAnimation.getAnimationPlayer().addUpdateListener(valueAnimator ->
                updateFullscreenProgress((Float) valueAnimator.getAnimatedValue()));
        return 1 / getShiftRange();
    }

    private void setupInterpolators(StateAnimationConfig stateAnimationConfig) {
        stateAnimationConfig.setInterpolator(ANIM_WORKSPACE_FADE, DEACCEL_2);
        stateAnimationConfig.setInterpolator(ANIM_ALL_APPS_FADE, DEACCEL_2);
        if (SysUINavigationMode.getMode(mLauncher) == Mode.NO_BUTTON) {
            // Overview lives to the left of workspace, so translate down later than over
            stateAnimationConfig.setInterpolator(ANIM_WORKSPACE_TRANSLATE, ACCEL_2);
            stateAnimationConfig.setInterpolator(ANIM_VERTICAL_PROGRESS, ACCEL_2);
            stateAnimationConfig.setInterpolator(ANIM_OVERVIEW_SCALE, ACCEL_2);
            stateAnimationConfig.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, ACCEL_2);
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
        int sysuiFlags = 0;
        if (progress > UPDATE_SYSUI_FLAGS_THRESHOLD) {
            TaskView tv = mOverviewPanel.getTaskViewAt(0);
            if (tv != null) {
                sysuiFlags = tv.getThumbnail().getSysUiStatusNavFlags();
            }
        }
        mLauncher.getSystemUiController().updateUiState(UI_STATE_OVERVIEW, sysuiFlags);
    }

    @Override
    protected float getShiftRange() {
        return mLauncher.getDeviceProfile().widthPx / 2f;
    }

    @Override
    protected int getLogContainerTypeForNormalState(MotionEvent ev) {
        return LauncherLogProto.ContainerType.NAVBAR;
    }

    @Override
    protected int getDirectionForLog() {
        return Utilities.isRtl(mLauncher.getResources()) ? Direction.LEFT : Direction.RIGHT;
    }
}
