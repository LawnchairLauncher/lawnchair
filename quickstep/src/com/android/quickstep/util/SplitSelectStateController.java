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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.PendingIntent.FLAG_MUTABLE;

import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.DEFAULT_SPLIT_RATIO;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;

import android.annotation.NonNull;
import android.app.ActivityOptions;
import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
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

    private final Context mContext;
    private final Handler mHandler;
    private final SystemUiProxy mSystemUiProxy;
    private final StateManager mStateManager;
    private final DepthController mDepthController;
    private @StagePosition int mStagePosition;
    private Intent mInitialTaskIntent;
    private int mInitialTaskId = INVALID_TASK_ID;
    private int mSecondTaskId = INVALID_TASK_ID;
    private boolean mRecentsAnimationRunning;
    /** If not null, this is the TaskView we want to launch from */
    @Nullable
    private GroupedTaskView mLaunchingTaskView;

    public SplitSelectStateController(Context context, Handler handler, StateManager stateManager,
            DepthController depthController) {
        mContext = context;
        mHandler = handler;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(mContext);
        mStateManager = stateManager;
        mDepthController = depthController;
    }

    /**
     * To be called after first task selected
     */
    public void setInitialTaskSelect(int taskId, @StagePosition int stagePosition) {
        mInitialTaskId = taskId;
        mStagePosition = stagePosition;
        mInitialTaskIntent = null;
    }

    public void setInitialTaskSelect(Intent intent, @StagePosition int stagePosition) {
        mInitialTaskIntent = intent;
        mStagePosition = stagePosition;
        mInitialTaskId = INVALID_TASK_ID;
    }

    /**
     * To be called after second task selected
     */
    public void setSecondTask(Task task, Consumer<Boolean> callback) {
        mSecondTaskId = task.key.id;
        final Intent fillInIntent;
        if (mInitialTaskIntent != null) {
            fillInIntent = new Intent();
            if (TextUtils.equals(mInitialTaskIntent.getComponent().getPackageName(),
                    task.getTopComponent().getPackageName())) {
                fillInIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            }
        } else {
            fillInIntent = null;
        }
        final PendingIntent pendingIntent =
                mInitialTaskIntent == null ? null : PendingIntent.getActivity(mContext, 0,
                        mInitialTaskIntent, FLAG_MUTABLE);
        launchTasks(mInitialTaskId, pendingIntent, fillInIntent, mSecondTaskId, mStagePosition,
                callback, false /* freezeTaskList */, DEFAULT_SPLIT_RATIO);
    }

    /**
     * To be called when we want to launch split pairs from an existing GroupedTaskView.
     */
    public void launchTasks(GroupedTaskView groupedTaskView,
            Consumer<Boolean> callback, boolean freezeTaskList) {
        mLaunchingTaskView = groupedTaskView;
        TaskView.TaskIdAttributeContainer[] taskIdAttributeContainers =
                groupedTaskView.getTaskIdAttributeContainers();
        launchTasks(taskIdAttributeContainers[0].getTask().key.id,
                taskIdAttributeContainers[1].getTask().key.id,
                taskIdAttributeContainers[0].getStagePosition(), callback, freezeTaskList,
                groupedTaskView.getSplitRatio());
    }

    /**
     * To be called when we want to launch split pairs from Overview when split is initiated from
     * Overview.
     */
    public void launchTasks(int taskId1, int taskId2, @StagePosition int stagePosition,
            Consumer<Boolean> callback, boolean freezeTaskList, float splitRatio) {
        launchTasks(taskId1, null /* taskPendingIntent */, null /* fillInIntent */, taskId2,
                stagePosition, callback, freezeTaskList, splitRatio);
    }

    /**
     * To be called when we want to launch split pairs from Overview. Split can be initiated from
     * either Overview or home, or all apps. Either both taskIds are set, or a pending intent + a
     * fill in intent with a taskId2 are set.
     * @param taskPendingIntent is null when split is initiated from Overview
     * @param stagePosition representing location of task1
     */
    public void launchTasks(int taskId1, @Nullable PendingIntent taskPendingIntent,
            @Nullable Intent fillInIntent, int taskId2, @StagePosition int stagePosition,
            Consumer<Boolean> callback, boolean freezeTaskList, float splitRatio) {
        // Assume initial task is for top/left part of screen
        final int[] taskIds = stagePosition == STAGE_POSITION_TOP_OR_LEFT
                ? new int[]{taskId1, taskId2}
                : new int[]{taskId2, taskId1};
        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            RemoteSplitLaunchTransitionRunner animationRunner =
                    new RemoteSplitLaunchTransitionRunner(taskId1, taskPendingIntent, taskId2,
                            callback);
            mSystemUiProxy.startTasks(taskIds[0], null /* mainOptions */, taskIds[1],
                    null /* sideOptions */, STAGE_POSITION_BOTTOM_OR_RIGHT, splitRatio,
                    new RemoteTransitionCompat(animationRunner, MAIN_EXECUTOR,
                            ActivityThread.currentActivityThread().getApplicationThread()));
            // TODO: handle intent + task with shell transition
        } else {
            RemoteSplitLaunchAnimationRunner animationRunner =
                    new RemoteSplitLaunchAnimationRunner(taskId1, taskPendingIntent, taskId2,
                            callback);
            final RemoteAnimationAdapter adapter = new RemoteAnimationAdapter(
                    RemoteAnimationAdapterCompat.wrapRemoteAnimationRunner(animationRunner),
                    300, 150,
                    ActivityThread.currentActivityThread().getApplicationThread());

            ActivityOptions mainOpts = ActivityOptions.makeBasic();
            if (freezeTaskList) {
                mainOpts.setFreezeRecentTasksReordering();
            }
            if (taskPendingIntent == null) {
                mSystemUiProxy.startTasksWithLegacyTransition(taskIds[0], mainOpts.toBundle(),
                        taskIds[1], null /* sideOptions */, STAGE_POSITION_BOTTOM_OR_RIGHT,
                        splitRatio, adapter);
            } else {
                mSystemUiProxy.startIntentAndTaskWithLegacyTransition(taskPendingIntent,
                        fillInIntent, taskId2, mainOpts.toBundle(), null /* sideOptions */,
                        stagePosition, splitRatio, adapter);
            }
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

        private final int mInitialTaskId;
        private final PendingIntent mInitialTaskPendingIntent;
        private final int mSecondTaskId;
        private final Consumer<Boolean> mSuccessCallback;

        RemoteSplitLaunchTransitionRunner(int initialTaskId, PendingIntent initialTaskPendingIntent,
                int secondTaskId, Consumer<Boolean> callback) {
            mInitialTaskId = initialTaskId;
            mInitialTaskPendingIntent = initialTaskPendingIntent;
            mSecondTaskId = secondTaskId;
            mSuccessCallback = callback;
        }

        @Override
        public void startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction t, @NonNull Runnable finishCallback) {
            TaskViewUtils.composeRecentsSplitLaunchAnimator(mInitialTaskId,
                    mInitialTaskPendingIntent, mSecondTaskId, info, t, () -> {
                    finishCallback.run();
                    if (mSuccessCallback != null) {
                        mSuccessCallback.accept(true);
                    }
                });
            // After successful launch, call resetState
            resetState();
        }
    }

    /**
     * LEGACY
     * Remote animation runner for animation to launch an app.
     */
    private class RemoteSplitLaunchAnimationRunner implements RemoteAnimationRunnerCompat {

        private final int mInitialTaskId;
        private final PendingIntent mInitialTaskPendingIntent;
        private final int mSecondTaskId;
        private final Consumer<Boolean> mSuccessCallback;

        RemoteSplitLaunchAnimationRunner(int initialTaskId, PendingIntent initialTaskPendingIntent,
                int secondTaskId, Consumer<Boolean> successCallback) {
            mInitialTaskId = initialTaskId;
            mInitialTaskPendingIntent = initialTaskPendingIntent;
            mSecondTaskId = secondTaskId;
            mSuccessCallback = successCallback;
        }

        @Override
        public void onAnimationStart(int transit, RemoteAnimationTargetCompat[] apps,
                RemoteAnimationTargetCompat[] wallpapers, RemoteAnimationTargetCompat[] nonApps,
                Runnable finishedCallback) {
            postAsyncCallback(mHandler,
                    () -> TaskViewUtils.composeRecentsSplitLaunchAnimatorLegacy(
                            mLaunchingTaskView, mInitialTaskId, mInitialTaskPendingIntent,
                            mSecondTaskId, apps, wallpapers, nonApps, mStateManager,
                            mDepthController, () -> {
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
        mInitialTaskId = INVALID_TASK_ID;
        mInitialTaskIntent = null;
        mSecondTaskId = INVALID_TASK_ID;
        mStagePosition = SplitConfigurationOptions.STAGE_POSITION_UNDEFINED;
        mRecentsAnimationRunning = false;
        mLaunchingTaskView = null;
    }

    /**
     * @return {@code true} if first task has been selected and waiting for the second task to be
     *         chosen
     */
    public boolean isSplitSelectActive() {
        return (mInitialTaskId != INVALID_TASK_ID || mInitialTaskIntent != null)
                && mSecondTaskId == INVALID_TASK_ID;
    }
}
