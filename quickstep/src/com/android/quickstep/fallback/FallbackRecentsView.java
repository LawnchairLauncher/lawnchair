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

import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.testing.TestProtocol;
import com.android.quickstep.FallbackActivityInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.SplitPlaceholderView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.R)
public class FallbackRecentsView extends RecentsView<RecentsActivity, RecentsState>
        implements StateListener<RecentsState> {

    private RunningTaskInfo mHomeTaskInfo;

    public FallbackRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FallbackRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr, FallbackActivityInterface.INSTANCE);
        mActivity.getStateManager().addStateListener(this);
    }

    @Override
    public void init(OverviewActionsView actionsView, SplitPlaceholderView splitPlaceholderView) {
        super.init(actionsView, splitPlaceholderView);
        setOverviewStateEnabled(true);
        setOverlayEnabled(true);
    }

    @Override
    public void startHome() {
        mActivity.startHome();
    }

    /**
     * When starting gesture interaction from home, we add a temporary invisible tile corresponding
     * to the home task. This allows us to handle quick-switch similarly to a quick-switching
     * from a foreground task.
     */
    public void onGestureAnimationStartOnHome(RunningTaskInfo homeTaskInfo) {
        mHomeTaskInfo = homeTaskInfo;
        onGestureAnimationStart(homeTaskInfo);
    }

    /**
     * When the gesture ends and we're going to recents view, we also remove the temporary
     * invisible tile added for the home task. This also pushes the remaining tiles back
     * to the center.
     */
    @Override
    public void onPrepareGestureEndAnimation(
            @Nullable AnimatorSet animatorSet, GestureState.GestureEndTarget endTarget) {
        super.onPrepareGestureEndAnimation(animatorSet, endTarget);
        if (mHomeTaskInfo != null && endTarget == RECENTS && animatorSet != null) {
            TaskView tv = getTaskView(mHomeTaskInfo.taskId);
            if (tv != null) {
                PendingAnimation pa = createTaskDismissAnimation(tv, true, false, 150);
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
    public void setCurrentTask(int runningTaskId) {
        super.setCurrentTask(runningTaskId);
        if (mHomeTaskInfo != null && mHomeTaskInfo.taskId != runningTaskId) {
            mHomeTaskInfo = null;
            setRunningTaskHidden(false);
        }
    }

    @Override
    protected boolean shouldAddStubTaskView(RunningTaskInfo runningTaskInfo) {
        if (mHomeTaskInfo != null && runningTaskInfo != null &&
                mHomeTaskInfo.taskId == runningTaskInfo.taskId
                && getTaskViewCount() == 0) {
            // Do not add a stub task if we are running over home with empty recents, so that we
            // show the empty recents message instead of showing a stub task and later removing it.
            return false;
        }
        return super.shouldAddStubTaskView(runningTaskInfo);
    }

    @Override
    protected void applyLoadPlan(ArrayList<Task> tasks) {
        // When quick-switching on 3p-launcher, we add a "stub" tile corresponding to Launcher
        // as well. This tile is never shown as we have setCurrentTaskHidden, but allows use to
        // track the index of the next task appropriately, as if we are switching on any other app.
        if (mHomeTaskInfo != null && mHomeTaskInfo.taskId == mRunningTaskId && !tasks.isEmpty()) {
            // Check if the task list has running task
            boolean found = false;
            for (Task t : tasks) {
                if (t.key.id == mRunningTaskId) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ArrayList<Task> newList = new ArrayList<>(tasks.size() + 1);
                newList.addAll(tasks);
                newList.add(Task.from(new TaskKey(mHomeTaskInfo), mHomeTaskInfo, false));
                tasks = newList;
            }
        }
        super.applyLoadPlan(tasks);
    }

    @Override
    public void setRunningTaskHidden(boolean isHidden) {
        if (mHomeTaskInfo != null) {
            // Always keep the home task hidden
            isHidden = true;
        }
        super.setRunningTaskHidden(isHidden);
    }

    @Override
    public void setModalStateEnabled(boolean isModalState) {
        super.setModalStateEnabled(isModalState);
        if (isModalState) {
            mActivity.getStateManager().goToState(RecentsState.MODAL_TASK);
        } else {
            if (mActivity.isInState(RecentsState.MODAL_TASK)) {
                mActivity.getStateManager().goToState(DEFAULT);
            }
        }
    }

    @Override
    public void onStateTransitionStart(RecentsState toState) {
        setOverviewStateEnabled(true);
        setOverviewGridEnabled(toState.displayOverviewTasksAsGrid(mActivity.getDeviceProfile()));
        setOverviewFullscreenEnabled(toState.isFullScreen());
        setFreezeViewVisibility(true);
    }

    @Override
    public void onStateTransitionComplete(RecentsState finalState) {
        if (finalState == HOME) {
            // Clean-up logic that occurs when recents is no longer in use/visible.
            reset();
        }
        setOverlayEnabled(finalState == DEFAULT || finalState == MODAL_TASK);
        setFreezeViewVisibility(false);
    }

    @Override
    public void setOverviewStateEnabled(boolean enabled) {
        super.setOverviewStateEnabled(enabled);
        if (enabled) {
            RecentsState state = mActivity.getStateManager().getState();
            setDisallowScrollToClearAll(!state.hasClearAllButton());
        }
    }
}
