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
package com.android.quickstep.views;

import static com.android.launcher3.LauncherState.CLEAR_ALL_BUTTON;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_MODAL_TASK;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.LauncherState.SPRING_LOADED;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.LauncherState;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.popup.QuickstepSystemShortcut;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.PendingSplitSelectInfo;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.quickstep.LauncherActivityInterface;
import com.android.quickstep.util.SplitSelectStateController;

/**
 * {@link RecentsView} used in Launcher activity
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherRecentsView extends RecentsView<QuickstepLauncher, LauncherState>
        implements StateListener<LauncherState> {

    public LauncherRecentsView(Context context) {
        this(context, null);
    }

    public LauncherRecentsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherRecentsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr, LauncherActivityInterface.INSTANCE);
        mActivity.getStateManager().addStateListener(this);
    }

    @Override
    public void init(OverviewActionsView actionsView,
            SplitSelectStateController splitPlaceholderView) {
        super.init(actionsView, splitPlaceholderView);
        setContentAlpha(0);
    }

    @Override
    public void startHome() {
        mActivity.getStateManager().goToState(NORMAL);
        AbstractFloatingView.closeAllOpenViews(mActivity, mActivity.isStarted());
    }

    @Override
    protected void onTaskLaunchAnimationEnd(boolean success) {
        if (success) {
            mActivity.getStateManager().moveToRestState();
        } else {
            LauncherState state = mActivity.getStateManager().getState();
            mActivity.getAllAppsController().setState(state);
        }
        super.onTaskLaunchAnimationEnd(success);
    }

    @Override
    public void onTaskIconChanged(int taskId) {
        // If Launcher needs to return to split select state, do it now, after the icon has updated.
        if (mActivity.hasPendingSplitSelectInfo()) {
            PendingSplitSelectInfo recoveryData = mActivity.getPendingSplitSelectInfo();
            if (recoveryData.getStagedTaskId() == taskId) {
                initiateSplitSelect(
                        getTaskViewByTaskId(recoveryData.getStagedTaskId()),
                        recoveryData.getStagePosition(), recoveryData.getSource()
                );
                mActivity.finishSplitSelectRecovery();
            }
        }
    }

    @Override
    public void reset() {
        super.reset();
        setLayoutRotation(Surface.ROTATION_0, Surface.ROTATION_0);
    }

    @Override
    public void onStateTransitionStart(LauncherState toState) {
        setOverviewStateEnabled(toState.overviewUi);
        setOverviewGridEnabled(toState.displayOverviewTasksAsGrid(mActivity.getDeviceProfile()));
        setOverviewFullscreenEnabled(toState.getOverviewFullscreenProgress() == 1);
        if (toState == OVERVIEW_MODAL_TASK) {
            setOverviewSelectEnabled(true);
        }
        setFreezeViewVisibility(true);
    }

    @Override
    public void onStateTransitionComplete(LauncherState finalState) {
        if (finalState == NORMAL || finalState == SPRING_LOADED) {
            // Clean-up logic that occurs when recents is no longer in use/visible.
            reset();
        }
        boolean isOverlayEnabled = finalState == OVERVIEW || finalState == OVERVIEW_MODAL_TASK;
        setOverlayEnabled(isOverlayEnabled);
        setFreezeViewVisibility(false);
        if (finalState != OVERVIEW_MODAL_TASK) {
            setOverviewSelectEnabled(false);
        }

        if (isOverlayEnabled) {
            runActionOnRemoteHandles(remoteTargetHandle ->
                    remoteTargetHandle.getTaskViewSimulator().setDrawsBelowRecents(true));
        }
    }

    @Override
    public void setOverviewStateEnabled(boolean enabled) {
        super.setOverviewStateEnabled(enabled);
        if (enabled) {
            LauncherState state = mActivity.getStateManager().getState();
            boolean hasClearAllButton = (state.getVisibleElements(mActivity)
                    & CLEAR_ALL_BUTTON) != 0;
            setDisallowScrollToClearAll(!hasClearAllButton);
        }
        if (mActivity.getDesktopVisibilityController() != null) {
            mActivity.getDesktopVisibilityController().setOverviewStateEnabled(enabled);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        // Do not let touch escape to siblings below this view.
        return result || mActivity.getStateManager().getState().overviewUi;
    }

    @Override
    protected DepthController getDepthController() {
        return mActivity.getDepthController();
    }

    @Override
    public void setModalStateEnabled(boolean isModalState, boolean animate) {
        if (isModalState) {
            mActivity.getStateManager().goToState(LauncherState.OVERVIEW_MODAL_TASK, animate);
        } else {
            if (mActivity.isInState(LauncherState.OVERVIEW_MODAL_TASK)) {
                mActivity.getStateManager().goToState(LauncherState.OVERVIEW, animate);
                resetModalVisuals();
            }
        }
    }

    @Override
    protected void onDismissAnimationEnds() {
        super.onDismissAnimationEnds();
        if (mActivity.isInState(OVERVIEW_SPLIT_SELECT)) {
            // We want to keep the tasks translations in this temporary state
            // after resetting the rest above
            setTaskViewsPrimarySplitTranslation(mTaskViewsPrimarySplitTranslation);
            setTaskViewsSecondarySplitTranslation(mTaskViewsSecondarySplitTranslation);
        }
    }

    @Override
    public void initiateSplitSelect(TaskView taskView,
            @SplitConfigurationOptions.StagePosition int stagePosition,
            StatsLogManager.EventEnum splitEvent) {
        super.initiateSplitSelect(taskView, stagePosition, splitEvent);
        mActivity.getStateManager().goToState(LauncherState.OVERVIEW_SPLIT_SELECT);
    }

    @Override
    public void initiateSplitSelect(QuickstepSystemShortcut.SplitSelectSource splitSelectSource) {
        super.initiateSplitSelect(splitSelectSource);
        mActivity.getStateManager().goToState(LauncherState.OVERVIEW_SPLIT_SELECT);
    }

    @Override
    protected boolean canLaunchFullscreenTask() {
        return !mActivity.isInState(OVERVIEW_SPLIT_SELECT);
    }
}
