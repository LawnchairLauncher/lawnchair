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

import static com.android.quickstep.GestureState.GestureEndTarget.RECENTS;
import static com.android.quickstep.fallback.RecentsState.DEFAULT;
import static com.android.quickstep.fallback.RecentsState.HOME;
import static com.android.quickstep.fallback.RecentsState.MODAL_TASK;
import static com.android.quickstep.fallback.RecentsState.OVERVIEW_SPLIT_SELECT;

import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.popup.QuickstepSystemShortcut;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.util.SplitConfigurationOptions;
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

@TargetApi(Build.VERSION_CODES.R)
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
        mActivity.getStateManager().addStateListener(this);
    }

    @Override
    public void init(OverviewActionsView actionsView, SplitSelectStateController splitController) {
        super.init(actionsView, splitController);
        setOverviewStateEnabled(true);
        setOverlayEnabled(true);
    }

    @Override
    public void startHome() {
        mActivity.startHome();
        AbstractFloatingView.closeAllOpenViews(mActivity, mActivity.isStarted());
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
        int runningTaskId = getTaskIdsForRunningTaskView()[0];
        if (mHomeTask != null && mHomeTask.key.id != runningTaskId) {
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
    protected void applyLoadPlan(ArrayList<GroupTask> taskGroups) {
        // When quick-switching on 3p-launcher, we add a "stub" tile corresponding to Launcher
        // as well. This tile is never shown as we have setCurrentTaskHidden, but allows use to
        // track the index of the next task appropriately, as if we are switching on any other app.
        // TODO(b/195607777) Confirm home task info is front-most task and not mixed in with others
        int runningTaskId = getTaskIdsForRunningTaskView()[0];
        if (mHomeTask != null && mHomeTask.key.id == runningTaskId
                && !taskGroups.isEmpty()) {
            // Check if the task list has running task
            boolean found = false;
            for (GroupTask group : taskGroups) {
                if (group.containsTask(runningTaskId)) {
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
    public void setModalStateEnabled(boolean isModalState, boolean animate) {
        if (isModalState) {
            mActivity.getStateManager().goToState(RecentsState.MODAL_TASK, animate);
        } else {
            if (mActivity.isInState(RecentsState.MODAL_TASK)) {
                mActivity.getStateManager().goToState(DEFAULT, animate);
                resetModalVisuals();
            }
        }
    }

    @Override
    public void initiateSplitSelect(TaskView taskView,
            @SplitConfigurationOptions.StagePosition int stagePosition,
            StatsLogManager.EventEnum splitEvent) {
        super.initiateSplitSelect(taskView, stagePosition, splitEvent);
        mActivity.getStateManager().goToState(OVERVIEW_SPLIT_SELECT);
    }

    @Override
    public void onStateTransitionStart(RecentsState toState) {
        setOverviewStateEnabled(true);
        setOverviewGridEnabled(toState.displayOverviewTasksAsGrid(mActivity.getDeviceProfile()));
        setOverviewFullscreenEnabled(toState.isFullScreen());
        if (toState == MODAL_TASK) {
            setOverviewSelectEnabled(true);
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
        if (finalState != OVERVIEW_SPLIT_SELECT) {
            resetFromSplitSelectionState();
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
            RecentsState state = mActivity.getStateManager().getState();
            setDisallowScrollToClearAll(!state.hasClearAllButton());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        // Do not let touch escape to siblings below this view.
        return result || mActivity.getStateManager().getState().overviewUi();
    }

    @Override
    public void initiateSplitSelect(QuickstepSystemShortcut.SplitSelectSource splitSelectSource) {
        super.initiateSplitSelect(splitSelectSource);
        mActivity.getStateManager().goToState(OVERVIEW_SPLIT_SELECT);
    }

    @Override
    protected boolean canLaunchFullscreenTask() {
        return !mActivity.isInState(OVERVIEW_SPLIT_SELECT);
    }
}
