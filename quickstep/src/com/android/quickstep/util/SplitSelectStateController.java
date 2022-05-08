/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.quickstep.util;

import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.DEFAULT_SPLIT_RATIO;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;

import android.app.ActivityOptions;
import android.app.ActivityThread;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.view.RemoteAnimationAdapter;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.annotation.Nullable;

import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.RemoteTransitionCompat;
import com.android.systemui.shared.system.RemoteTransitionRunner;

import java.util.function.Consumer;

/**
 * Represent data needed for the transient state when user has selected one app for split screen
 * and is in the process of either a) selecting a second app or b) exiting intention to invoke split
 */
public class SplitSelectStateController {

    private final Handler mHandler;
    private final SystemUiProxy mSystemUiProxy;
    private final StateManager mStateManager;
    private final DepthController mDepthController;
    private @StagePosition int mStagePosition;
    private Task mInitialTask;
    private Task mSecondTask;
    private boolean mRecentsAnimationRunning;
    /** If not null, this is the TaskView we want to launch from */
    @Nullable
    private GroupedTaskView mLaunchingTaskView;

    public SplitSelectStateController(Handler handler, SystemUiProxy systemUiProxy,
            StateManager stateManager,
            DepthController depthController) {
        mHandler = handler;
        mSystemUiProxy = systemUiProxy;
        mStateManager = stateManager;
        mDepthController = depthController;
    }

    /**
     * To be called after first task selected
     */
    public void setInitialTaskSelect(Task task, @StagePosition int stagePosition,
            Rect initialBounds) {
        mInitialTask = task;
        mStagePosition = stagePosition;
    }

    /**
     * To be called after second task selected
     */
    public void setSecondTaskId(Task task, Consumer<Boolean> callback) {
        mSecondTask = task;
        launchTasks(mInitialTask, mSecondTask, mStagePosition, callback,
                false /* freezeTaskList */, DEFAULT_SPLIT_RATIO);
    }

    /**
     * To be called when we want to launch split pairs from an existing GroupedTaskView.
     */
    public void launchTasks(GroupedTaskView groupedTaskView,
            Consumer<Boolean> callback, boolean freezeTaskList) {
        mLaunchingTaskView = groupedTaskView;
        TaskView.TaskIdAttributeContainer[] taskIdAttributeContainers =
                groupedTaskView.getTaskIdAttributeContainers();
        launchTasks(taskIdAttributeContainers[0].getTask(), taskIdAttributeContainers[1].getTask(),
                taskIdAttributeContainers[0].getStagePosition(), callback, freezeTaskList,
                groupedTaskView.getSplitRatio());
    }

    /**
     * @param stagePosition representing location of task1
     */
    public void launchTasks(Task task1, Task task2, @StagePosition int stagePosition,
            Consumer<Boolean> callback, boolean freezeTaskList, float splitRatio) {
        // Assume initial task is for top/left part of screen
        final int[] taskIds = stagePosition == STAGE_POSITION_TOP_OR_LEFT
                ? new int[]{task1.key.id, task2.key.id}
                : new int[]{task2.key.id, task1.key.id};
        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            RemoteSplitLaunchTransitionRunner animationRunner =
                    new RemoteSplitLaunchTransitionRunner(task1, task2);
            mSystemUiProxy.startTasks(taskIds[0], null /* mainOptions */, taskIds[1],
                    null /* sideOptions */, STAGE_POSITION_BOTTOM_OR_RIGHT, splitRatio,
                    new RemoteTransitionCompat(animationRunner, MAIN_EXECUTOR,
                            ActivityThread.currentActivityThread().getApplicationThread()));
        } else {
            RemoteSplitLaunchAnimationRunner animationRunner =
                    new RemoteSplitLaunchAnimationRunner(task1, task2, callback);
            final RemoteAnimationAdapter adapter = new RemoteAnimationAdapter(
                    RemoteAnimationAdapterCompat.wrapRemoteAnimationRunner(animationRunner),
                    300, 150,
                    ActivityThread.currentActivityThread().getApplicationThread());

            ActivityOptions mainOpts = ActivityOptions.makeBasic();
            if (freezeTaskList) {
                mainOpts.setFreezeRecentTasksReordering();
            }
            mSystemUiProxy.startTasksWithLegacyTransition(taskIds[0], mainOpts.toBundle(),
                    taskIds[1], null /* sideOptions */, STAGE_POSITION_BOTTOM_OR_RIGHT,
                    splitRatio, adapter);
        }
    }

    public @StagePosition int getActiveSplitStagePosition() {
        return mStagePosition;
    }

    public void setRecentsAnimationRunning(boolean running) {
        this.mRecentsAnimationRunning = running;
    }

    /**
     * Requires Shell Transitions
     */
    private class RemoteSplitLaunchTransitionRunner implements RemoteTransitionRunner {

        private final Task mInitialTask;
        private final Task mSecondTask;

        RemoteSplitLaunchTransitionRunner(Task initialTask, Task secondTask) {
            mInitialTask = initialTask;
            mSecondTask = secondTask;
        }

        @Override
        public void startAnimation(IBinder transition, TransitionInfo info,
                SurfaceControl.Transaction t, Runnable finishCallback) {
            TaskViewUtils.composeRecentsSplitLaunchAnimator(mInitialTask,
                    mSecondTask, info, t, finishCallback);
            // After successful launch, call resetState
            resetState();
        }
    }

    /**
     * LEGACY
     * Remote animation runner for animation to launch an app.
     */
    private class RemoteSplitLaunchAnimationRunner implements RemoteAnimationRunnerCompat {

        private final Task mInitialTask;
        private final Task mSecondTask;
        private final Consumer<Boolean> mSuccessCallback;

        RemoteSplitLaunchAnimationRunner(Task initialTask, Task secondTask,
                Consumer<Boolean> successCallback) {
            mInitialTask = initialTask;
            mSecondTask = secondTask;
            mSuccessCallback = successCallback;
        }

        @Override
        public void onAnimationStart(int transit, RemoteAnimationTargetCompat[] apps,
                RemoteAnimationTargetCompat[] wallpapers, RemoteAnimationTargetCompat[] nonApps,
                Runnable finishedCallback) {
            postAsyncCallback(mHandler,
                    () -> TaskViewUtils.composeRecentsSplitLaunchAnimatorLegacy(
                            mLaunchingTaskView, mInitialTask, mSecondTask, apps, wallpapers,
                            nonApps, mStateManager, mDepthController, () -> {
                                finishedCallback.run();
                                if (mSuccessCallback != null) {
                                    mSuccessCallback.accept(true);
                                }
                                resetState();
                            }));
        }

        @Override
        public void onAnimationCancelled() {
            postAsyncCallback(mHandler, () -> {
                if (mSuccessCallback != null) {
                    // Launching legacy tasks while recents animation is running will always cause
                    // onAnimationCancelled to be called (should be fixed w/ shell transitions?)
                    mSuccessCallback.accept(mRecentsAnimationRunning);
                }
                resetState();
            });
        }
    }

    /**
     * To be called if split select was cancelled
     */
    public void resetState() {
        mInitialTask = null;
        mSecondTask = null;
        mStagePosition = SplitConfigurationOptions.STAGE_POSITION_UNDEFINED;
        mRecentsAnimationRunning = false;
        mLaunchingTaskView = null;
    }

    /**
     * @return {@code true} if first task has been selected and waiting for the second task to be
     *         chosen
     */
    public boolean isSplitSelectActive() {
        return mInitialTask != null && mSecondTask == null;
    }
}
