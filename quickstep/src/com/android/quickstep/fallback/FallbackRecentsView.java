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
package com.android.quickstep.fallback;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import static com.android.quickstep.GestureState.GestureEndTarget.RECENTS;
import static com.android.quickstep.fallback.RecentsState.DEFAULT;
import static com.android.quickstep.fallback.RecentsState.HOME;
import static com.android.quickstep.fallback.RecentsState.MODAL_TASK;
import static com.android.quickstep.fallback.RecentsState.OVERVIEW_SPLIT_SELECT;

import android.animation.AnimatorSet;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.desktop.DesktopRecentsTransitionController;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;
import com.android.quickstep.FallbackActivityInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.RotationTouchHelper;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FallbackRecentsView extends RecentsView<RecentsActivity, RecentsState>
        implements StateListener<RecentsState> {

    private static final int TASK_DISMISS_DURATION = 150;

    @Nullable
    private Task mHomeTask;

    public FallbackRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FallbackRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr, FallbackActivityInterface.INSTANCE);
        mContainer.getStateManager().addStateListener(this);
    }

    @Override
    public void init(OverviewActionsView actionsView, SplitSelectStateController splitController,
            @Nullable DesktopRecentsTransitionController desktopRecentsTransitionController) {
        super.init(actionsView, splitController, desktopRecentsTransitionController);
        setOverviewStateEnabled(true);
        setOverlayEnabled(true);
    }

    @Override
    protected void handleStartHome(boolean animated) {
        mContainer.startHome();
        AbstractFloatingView.closeAllOpenViews(mContainer, mContainer.isStarted());
    }

    @Override
    protected boolean canStartHomeSafely() {
        return mContainer.canStartHomeSafely();
    }

    @Override
    public StateManager<RecentsState, RecentsActivity> getStateManager() {
        return mContainer.getStateManager();
    }

    /**
     * When starting gesture interaction from home, we add a temporary invisible tile corresponding
     * to the home task. This allows us to handle quick-switch similarly to a quick-switching
     * from a foreground task.
     */
    public void onGestureAnimationStartOnHome(Task[] homeTask,
            RotationTouchHelper rotationTouchHelper) {
        // TODO(b/195607777) General fallback love, but this might be correct
        //  Home task should be defined as the front-most task info I think?
        mHomeTask = homeTask.length > 0 ? homeTask[0] : null;
        onGestureAnimationStart(homeTask, rotationTouchHelper);
    }

    /**
     * When the gesture ends and we're going to recents view, we also remove the temporary
     * invisible tile added for the home task. This also pushes the remaining tiles back
     * to the center.
     */
    @Override
    public void onPrepareGestureEndAnimation(
            @Nullable AnimatorSet animatorSet, GestureState.GestureEndTarget endTarget,
            TaskViewSimulator[] taskViewSimulators) {
        super.onPrepareGestureEndAnimation(animatorSet, endTarget, taskViewSimulators);
        if (mHomeTask != null && endTarget == RECENTS && animatorSet != null) {
            TaskView tv = getTaskViewByTaskId(mHomeTask.key.id);
            if (tv != null) {
                PendingAnimation pa = new PendingAnimation(TASK_DISMISS_DURATION);
                createTaskDismissAnimation(pa, tv, true, false,
                        TASK_DISMISS_DURATION, false /* dismissingForSplitSelection*/);
                pa.addEndListener(e -> setCurrentTask(-1));
                AnimatorPlaybackController controller = pa.createPlaybackController();
                controller.dispatchOnStart();
                animatorSet.play(controller.getAnimationPlayer());
            }
        }
    }

    @Override
    public void onGestureAnimationEnd() {
        if (mCurrentGestureEndTarget == GestureState.GestureEndTarget.HOME) {
            // Clean-up logic that occurs when recents is no longer in use/visible.
            reset();
        }
        super.onGestureAnimationEnd();
    }

    @Override
    public void setCurrentTask(int runningTaskViewId) {
        super.setCurrentTask(runningTaskViewId);
        int[] runningTaskIds = getTaskIdsForRunningTaskView();
        if (mHomeTask != null
                && Arrays.stream(runningTaskIds).noneMatch(taskId -> taskId == mHomeTask.key.id)) {
            mHomeTask = null;
            setRunningTaskHidden(false);
        }
    }

    @Nullable
    @Override
    protected TaskView getHomeTaskView() {
        return mHomeTask != null ? getTaskViewByTaskId(mHomeTask.key.id) : null;
    }

    @Override
    protected boolean shouldAddStubTaskView(Task[] runningTasks) {
        if (runningTasks.length > 1) {
            // can't be in split screen w/ home task
            return super.shouldAddStubTaskView(runningTasks);
        }

        Task runningTask = runningTasks[0];
        if (mHomeTask != null && runningTask != null
                && mHomeTask.key.id == runningTask.key.id
                && getTaskViewCount() == 0 && mLoadPlanEverApplied) {
            // Do not add a stub task if we are running over home with empty recents, so that we
            // show the empty recents message instead of showing a stub task and later removing it.
            // Ignore empty task signal if applyLoadPlan has never run.
            return false;
        }
        return super.shouldAddStubTaskView(runningTasks);
    }

    @Override
    protected void applyLoadPlan(List<GroupTask> taskGroups) {
        // When quick-switching on 3p-launcher, we add a "stub" tile corresponding to Launcher
        // as well. This tile is never shown as we have setCurrentTaskHidden, but allows use to
        // track the index of the next task appropriately, as if we are switching on any other app.
        // TODO(b/195607777) Confirm home task info is front-most task and not mixed in with others
        int[] runningTaskIds = getTaskIdsForRunningTaskView();
        if (mHomeTask != null
                && Arrays.stream(runningTaskIds).allMatch(taskId -> taskId == mHomeTask.key.id)
                && !taskGroups.isEmpty()) {
            // Check if the task list has running task
            boolean found = false;
            for (GroupTask group : taskGroups) {
                if (Arrays.stream(runningTaskIds).allMatch(group::containsTask)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ArrayList<GroupTask> newList = new ArrayList<>(taskGroups.size() + 1);
                newList.addAll(taskGroups);
                newList.add(new GroupTask(mHomeTask, null, null));
                taskGroups = newList;
            }
        }
        super.applyLoadPlan(taskGroups);
    }

    @Override
    public void setRunningTaskHidden(boolean isHidden) {
        if (mHomeTask != null) {
            // Always keep the home task hidden
            isHidden = true;
        }
        super.setRunningTaskHidden(isHidden);
    }

    @Override
    public void setModalStateEnabled(int taskId, boolean animate) {
        if (taskId != INVALID_TASK_ID) {
            setSelectedTask(taskId);
            mContainer.getStateManager().goToState(RecentsState.MODAL_TASK, animate);
        } else {
            if (mContainer.isInState(RecentsState.MODAL_TASK)) {
                mContainer.getStateManager().goToState(DEFAULT, animate);
            }
        }
    }

    @Override
    public void initiateSplitSelect(TaskView taskView,
            @SplitConfigurationOptions.StagePosition int stagePosition,
            StatsLogManager.EventEnum splitEvent) {
        super.initiateSplitSelect(taskView, stagePosition, splitEvent);
        mContainer.getStateManager().goToState(OVERVIEW_SPLIT_SELECT);
    }

    @Override
    public void onStateTransitionStart(RecentsState toState) {
        setOverviewStateEnabled(true);
        setOverviewGridEnabled(toState.displayOverviewTasksAsGrid(mContainer.getDeviceProfile()));
        setOverviewFullscreenEnabled(toState.isFullScreen());
        if (toState == MODAL_TASK) {
            setOverviewSelectEnabled(true);
        } else {
            resetModalVisuals();
        }

        // Set border after select mode changes to avoid showing border during state transition
        if (!toState.isRecentsViewVisible() || toState == MODAL_TASK) {
            setTaskBorderEnabled(false);
        }

        setFreezeViewVisibility(true);
    }

    @Override
    public void onStateTransitionComplete(RecentsState finalState) {
        if (finalState == HOME) {
            // Clean-up logic that occurs when recents is no longer in use/visible.
            reset();
        }
        boolean isOverlayEnabled = finalState == DEFAULT || finalState == MODAL_TASK;
        setOverlayEnabled(isOverlayEnabled);
        setFreezeViewVisibility(false);
        if (finalState != MODAL_TASK) {
            setOverviewSelectEnabled(false);
        }

        if (finalState.isRecentsViewVisible() && finalState != MODAL_TASK) {
            setTaskBorderEnabled(true);
        }

        if (finalState != OVERVIEW_SPLIT_SELECT) {
            if (FeatureFlags.enableSplitContextually()) {
                mSplitSelectStateController.resetState();
            } else {
                resetFromSplitSelectionState();
            }
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
            RecentsState state = mContainer.getStateManager().getState();
            setDisallowScrollToClearAll(!state.hasClearAllButton());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        // Do not let touch escape to siblings below this view.
        return result || mContainer.getStateManager().getState().isRecentsViewVisible();
    }

    @Override
    public void initiateSplitSelect(SplitSelectSource splitSelectSource) {
        super.initiateSplitSelect(splitSelectSource);
        mContainer.getStateManager().goToState(OVERVIEW_SPLIT_SELECT);
    }

    @Override
    protected boolean canLaunchFullscreenTask() {
        return !mContainer.isInState(OVERVIEW_SPLIT_SELECT);
    }

    /** Returns if app pairs are supported in this launcher. */
    @Override
    public boolean supportsAppPairs() {
        return false;
    }
}
