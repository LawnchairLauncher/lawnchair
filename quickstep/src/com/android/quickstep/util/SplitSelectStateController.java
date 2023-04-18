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
import static com.android.launcher3.util.SplitConfigurationOptions.getOppositeStagePosition;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.RemoteTransition;
import android.window.TransitionInfo;

import androidx.annotation.Nullable;

import com.android.internal.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;

import java.util.function.Consumer;

/**
 * Represent data needed for the transient state when user has selected one app for split screen
 * and is in the process of either a) selecting a second app or b) exiting intention to invoke split
 */
public class SplitSelectStateController {
    private static final String TAG = "SplitSelectStateCtor";

    private final Context mContext;
    private final Handler mHandler;
    private final RecentsModel mRecentTasksModel;
    private final SplitAnimationController mSplitAnimationController;
    private StatsLogManager mStatsLogManager;
    private final SystemUiProxy mSystemUiProxy;
    private final StateManager mStateManager;
    @Nullable
    private DepthController mDepthController;
    private @StagePosition int mInitialStagePosition;
    private ItemInfo mItemInfo;
    /** {@link #mInitialTaskIntent} and {@link #mInitialUser} (the user of the Intent) are set
     * together when split is initiated from an Intent. */
    private Intent mInitialTaskIntent;
    private UserHandle mInitialUser;
    private int mInitialTaskId = INVALID_TASK_ID;
    /** {@link #mSecondTaskIntent} and {@link #mSecondUser} (the user of the Intent) are set
     * together when split is confirmed with an Intent. */
    private Intent mSecondTaskIntent;
    private UserHandle mSecondUser;
    private int mSecondTaskId = INVALID_TASK_ID;
    private boolean mRecentsAnimationRunning;
    /** If {@code true}, animates the existing task view split placeholder view */
    private boolean mAnimateCurrentTaskDismissal;
    /**
     * Acts as a subset of {@link #mAnimateCurrentTaskDismissal}, we can't be dismissing from a
     * split pair task view without wanting to animate current task dismissal overall
     */
    private boolean mDismissingFromSplitPair;
    /** If not null, this is the TaskView we want to launch from */
    @Nullable
    private GroupedTaskView mLaunchingTaskView;
    /** Represents where split is intended to be invoked from. */
    private StatsLogManager.EventEnum mSplitEvent;

    private FloatingTaskView mFirstFloatingTaskView;

    public SplitSelectStateController(Context context, Handler handler, StateManager stateManager,
            DepthController depthController, StatsLogManager statsLogManager,
            SystemUiProxy systemUiProxy, RecentsModel recentsModel) {
        mContext = context;
        mHandler = handler;
        mStatsLogManager = statsLogManager;
        mSystemUiProxy = systemUiProxy;
        mStateManager = stateManager;
        mDepthController = depthController;
        mRecentTasksModel = recentsModel;
        mSplitAnimationController = new SplitAnimationController(this);
    }

    /**
     * @param alreadyRunningTask if set to {@link android.app.ActivityTaskManager#INVALID_TASK_ID}
     *                           then @param intent will be used to launch the initial task
     * @param intent will be ignored if @param alreadyRunningTask is set
     */
    public void setInitialTaskSelect(@Nullable Intent intent, @StagePosition int stagePosition,
            @NonNull ItemInfo itemInfo, StatsLogManager.EventEnum splitEvent,
            int alreadyRunningTask) {
        if (alreadyRunningTask != INVALID_TASK_ID) {
            mInitialTaskId = alreadyRunningTask;
        } else {
            mInitialTaskIntent = intent;
            mInitialUser = itemInfo.user;
        }

        setInitialData(stagePosition, splitEvent, itemInfo);
    }

    /**
     * To be called after first task selected from using a split shortcut from the fullscreen
     * running app.
     */
    public void setInitialTaskSelect(ActivityManager.RunningTaskInfo info,
            @StagePosition int stagePosition, @NonNull ItemInfo itemInfo,
            StatsLogManager.EventEnum splitEvent) {
        mInitialTaskId = info.taskId;
        setInitialData(stagePosition, splitEvent, itemInfo);
    }

    private void setInitialData(@StagePosition int stagePosition,
            StatsLogManager.EventEnum splitEvent, ItemInfo itemInfo) {
        mItemInfo = itemInfo;
        mInitialStagePosition = stagePosition;
        mSplitEvent = splitEvent;
    }

    /**
     * Pulls the list of active Tasks from RecentsModel, and finds the most recently active Task
     * matching a given ComponentName. Then uses that Task (which could be null) with the given
     * callback.
     *
     * Used in various task-switching or splitscreen operations when we need to check if there is a
     * currently running Task of a certain type and use the most recent one.
     */
    public void findLastActiveTaskAndRunCallback(ComponentKey componentKey,
            Consumer<Task> callback) {
        mRecentTasksModel.getTasks(taskGroups -> {
            Task lastActiveTask = null;
            // Loop through tasks in reverse, since they are ordered with most-recent tasks last.
            for (int i = taskGroups.size() - 1; i >= 0; i--) {
                GroupTask groupTask = taskGroups.get(i);
                Task task1 = groupTask.task1;
                if (isInstanceOfComponent(task1, componentKey)) {
                    lastActiveTask = task1;
                    break;
                }
                Task task2 = groupTask.task2;
                if (isInstanceOfComponent(task2, componentKey)) {
                    lastActiveTask = task2;
                    break;
                }
            }

            callback.accept(lastActiveTask);
        });
    }

    /**
     * Checks if a given Task is the most recently-active Task of type componentName. Used for
     * selecting already-running Tasks for splitscreen.
     */
    public boolean isInstanceOfComponent(@Nullable Task task, ComponentKey componentKey) {
        // Exclude the task that is already staged
        if (task == null || task.key.id == mInitialTaskId) {
            return false;
        }

        return task.key.baseIntent.getComponent().equals(componentKey.componentName)
                && task.key.userId == componentKey.user.getIdentifier();
    }

    /**
     * To be called when the actual tasks ({@link #mInitialTaskId}, {@link #mSecondTaskId}) are
     * to be launched. Call after launcher side animations are complete.
     */
    public void launchSplitTasks(Consumer<Boolean> callback) {
        Pair<InstanceId, com.android.launcher3.logging.InstanceId> instanceIds =
                LogUtils.getShellShareableInstanceId();
        launchTasks(mInitialTaskId, mInitialTaskIntent, mSecondTaskId, mSecondTaskIntent,
                mInitialStagePosition, callback, false /* freezeTaskList */, DEFAULT_SPLIT_RATIO,
                instanceIds.first);

        mStatsLogManager.logger()
                .withItemInfo(mItemInfo)
                .withInstanceId(instanceIds.second)
                .log(mSplitEvent);
    }

    /**
     * To be called as soon as user selects the second task (even if animations aren't complete)
     * @param task The second task that will be launched.
     */
    public void setSecondTask(Task task) {
        mSecondTaskId = task.key.id;
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     * @param intent The second intent that will be launched.
     * @param user The user of that intent.
     */
    public void setSecondTask(Intent intent, UserHandle user) {
        mSecondTaskIntent = intent;
        mSecondUser = user;
    }

    /**
     * To be called when we want to launch split pairs from an existing GroupedTaskView.
     */
    public void launchTasks(GroupedTaskView groupedTaskView, Consumer<Boolean> callback,
            boolean freezeTaskList) {
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
        launchTasks(taskId1, null /* intent1 */, taskId2, null /* intent2 */, stagePosition,
                callback, freezeTaskList, splitRatio, null);
    }

    /**
     * To be called when we want to launch split pairs from Overview. Split can be initiated from
     * either Overview or home, or all apps. Either both taskIds are set, or a pending intent + a
     * fill in intent with a taskId2 are set.
     * @param intent1 is null when split is initiated from Overview
     * @param stagePosition representing location of task1
     * @param shellInstanceId loggingId to be used by shell, will be non-null for actions that
     *                   create a split instance, null for cases that bring existing instaces to the
     *                   foreground (quickswitch, launching previous pairs from overview)
     */
    public void launchTasks(int taskId1, @Nullable Intent intent1, int taskId2,
            @Nullable Intent intent2, @StagePosition int stagePosition,
            Consumer<Boolean> callback, boolean freezeTaskList, float splitRatio,
            @Nullable InstanceId shellInstanceId) {
        TestLogging.recordEvent(
                TestProtocol.SEQUENCE_MAIN, "launchSplitTasks");
        final ActivityOptions options1 = ActivityOptions.makeBasic();
        if (freezeTaskList) {
            options1.setFreezeRecentTasksReordering();
        }
        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            final RemoteSplitLaunchTransitionRunner animationRunner =
                    new RemoteSplitLaunchTransitionRunner(taskId1, taskId2, callback);
            final RemoteTransition remoteTransition = new RemoteTransition(animationRunner,
                    ActivityThread.currentActivityThread().getApplicationThread());
            if (intent1 == null && intent2 == null) {
                mSystemUiProxy.startTasks(taskId1, options1.toBundle(), taskId2,
                        null /* options2 */, stagePosition, splitRatio, remoteTransition,
                        shellInstanceId);
            } else if (intent2 == null) {
                launchIntentOrShortcut(intent1, mInitialUser, options1, taskId2, stagePosition,
                        splitRatio, remoteTransition, shellInstanceId);
            } else if (intent1 == null) {
                launchIntentOrShortcut(intent2, mSecondUser, options1, taskId1,
                        getOppositeStagePosition(stagePosition), splitRatio, remoteTransition,
                        shellInstanceId);
            } else {
                mSystemUiProxy.startIntents(getPendingIntent(intent1, mInitialUser),
                        options1.toBundle(), getPendingIntent(intent2, mSecondUser),
                        null /* options2 */, stagePosition, splitRatio, remoteTransition,
                        shellInstanceId);
            }
        } else {
            final RemoteSplitLaunchAnimationRunner animationRunner =
                    new RemoteSplitLaunchAnimationRunner(taskId1, taskId2, callback);
            final RemoteAnimationAdapter adapter = new RemoteAnimationAdapter(
                    animationRunner, 300, 150,
                    ActivityThread.currentActivityThread().getApplicationThread());

            if (intent1 == null && intent2 == null) {
                mSystemUiProxy.startTasksWithLegacyTransition(taskId1, options1.toBundle(),
                        taskId2, null /* options2 */, stagePosition, splitRatio, adapter,
                        shellInstanceId);
            } else if (intent2 == null) {
                launchIntentOrShortcutLegacy(intent1, mInitialUser, options1, taskId2,
                        stagePosition, splitRatio, adapter, shellInstanceId);
            } else if (intent1 == null) {
                launchIntentOrShortcutLegacy(intent2, mSecondUser, options1, taskId1,
                        getOppositeStagePosition(stagePosition), splitRatio, adapter,
                        shellInstanceId);
            } else {
                mSystemUiProxy.startIntentsWithLegacyTransition(
                        getPendingIntent(intent1, mInitialUser),
                        getShortcutInfo(intent1, mInitialUser), options1.toBundle(),
                        getPendingIntent(intent2, mSecondUser),
                        getShortcutInfo(intent2, mSecondUser), null /* options2 */, stagePosition,
                        splitRatio, adapter, shellInstanceId);
            }
        }
    }

    private void launchIntentOrShortcut(Intent intent, UserHandle user, ActivityOptions options1,
            int taskId, @StagePosition int stagePosition, float splitRatio,
            RemoteTransition remoteTransition, @Nullable InstanceId shellInstanceId) {
        final ShortcutInfo shortcutInfo = getShortcutInfo(intent, user);
        if (shortcutInfo != null) {
            mSystemUiProxy.startShortcutAndTask(shortcutInfo,
                    options1.toBundle(), taskId, null /* options2 */, stagePosition,
                    splitRatio, remoteTransition, shellInstanceId);
        } else {
            mSystemUiProxy.startIntentAndTask(getPendingIntent(intent, user),
                    options1.toBundle(), taskId, null /* options2 */, stagePosition, splitRatio,
                    remoteTransition, shellInstanceId);
        }
    }

    private void launchIntentOrShortcutLegacy(Intent intent, UserHandle user,
            ActivityOptions options1, int taskId, @StagePosition int stagePosition,
            float splitRatio, RemoteAnimationAdapter adapter,
            @Nullable InstanceId shellInstanceId) {
        final ShortcutInfo shortcutInfo = getShortcutInfo(intent, user);
        if (shortcutInfo != null) {
            mSystemUiProxy.startShortcutAndTaskWithLegacyTransition(shortcutInfo,
                    options1.toBundle(), taskId, null /* options2 */, stagePosition,
                    splitRatio, adapter, shellInstanceId);
        } else {
            mSystemUiProxy.startIntentAndTaskWithLegacyTransition(
                    getPendingIntent(intent, user), options1.toBundle(), taskId,
                    null /* options2 */, stagePosition, splitRatio, adapter, shellInstanceId);
        }
    }

    private PendingIntent getPendingIntent(Intent intent, UserHandle user) {
        return intent == null ? null : (user != null
                ? PendingIntent.getActivityAsUser(mContext, 0, intent,
                FLAG_MUTABLE, null /* options */, user)
                : PendingIntent.getActivity(mContext, 0, intent, FLAG_MUTABLE));
    }

    public @StagePosition int getActiveSplitStagePosition() {
        return mInitialStagePosition;
    }

    public StatsLogManager.EventEnum getSplitEvent() {
        return mSplitEvent;
    }

    public void setRecentsAnimationRunning(boolean running) {
        mRecentsAnimationRunning = running;
    }

    @Nullable
    private ShortcutInfo getShortcutInfo(Intent intent, UserHandle user) {
        if (intent == null || intent.getPackage() == null) {
            return null;
        }

        final String shortcutId = intent.getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID);
        if (shortcutId == null) {
            return null;
        }

        try {
            final Context context = mContext.createPackageContextAsUser(
                    intent.getPackage(), 0 /* flags */, user);
            return new ShortcutInfo.Builder(context, shortcutId).build();
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to create a ShortcutInfo for " + intent.getPackage());
        }

        return null;
    }

    public boolean isAnimateCurrentTaskDismissal() {
        return mAnimateCurrentTaskDismissal;
    }

    public void setAnimateCurrentTaskDismissal(boolean animateCurrentTaskDismissal) {
        mAnimateCurrentTaskDismissal = animateCurrentTaskDismissal;
    }

    public boolean isDismissingFromSplitPair() {
        return mDismissingFromSplitPair;
    }

    public void setDismissingFromSplitPair(boolean dismissingFromSplitPair) {
        mDismissingFromSplitPair = dismissingFromSplitPair;
    }

    public SplitAnimationController getSplitAnimationController() {
        return mSplitAnimationController;
    }

    /**
     * Requires Shell Transitions
     */
    private class RemoteSplitLaunchTransitionRunner extends IRemoteTransition.Stub {

        private final int mInitialTaskId;
        private final int mSecondTaskId;
        private final Consumer<Boolean> mSuccessCallback;

        RemoteSplitLaunchTransitionRunner(int initialTaskId, int secondTaskId,
                Consumer<Boolean> callback) {
            mInitialTaskId = initialTaskId;
            mSecondTaskId = secondTaskId;
            mSuccessCallback = callback;
        }

        @Override
        public void startAnimation(IBinder transition, TransitionInfo info,
                SurfaceControl.Transaction t,
                IRemoteTransitionFinishedCallback finishedCallback) {
            final Runnable finishAdapter = () ->  {
                try {
                    finishedCallback.onTransitionFinished(null /* wct */, null /* sct */);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to call transition finished callback", e);
                }
            };

            MAIN_EXECUTOR.execute(() -> {
                TaskViewUtils.composeRecentsSplitLaunchAnimator(mLaunchingTaskView, mStateManager,
                        mDepthController, mInitialTaskId, mSecondTaskId, info, t, () -> {
                            finishAdapter.run();
                            if (mSuccessCallback != null) {
                                mSuccessCallback.accept(true);
                            }
                        });
                // After successful launch, call resetState
                resetState();
            });
        }

        @Override
        public void mergeAnimation(IBinder transition, TransitionInfo info,
                SurfaceControl.Transaction t, IBinder mergeTarget,
                IRemoteTransitionFinishedCallback finishedCallback) { }
    }

    /**
     * LEGACY
     * Remote animation runner for animation to launch an app.
     */
    private class RemoteSplitLaunchAnimationRunner extends RemoteAnimationRunnerCompat {

        private final int mInitialTaskId;
        private final int mSecondTaskId;
        private final Consumer<Boolean> mSuccessCallback;

        RemoteSplitLaunchAnimationRunner(int initialTaskId, int secondTaskId,
                Consumer<Boolean> successCallback) {
            mInitialTaskId = initialTaskId;
            mSecondTaskId = secondTaskId;
            mSuccessCallback = successCallback;
        }

        @Override
        public void onAnimationStart(int transit, RemoteAnimationTarget[] apps,
                RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
                Runnable finishedCallback) {
            postAsyncCallback(mHandler,
                    () -> TaskViewUtils.composeRecentsSplitLaunchAnimatorLegacy(
                            mLaunchingTaskView, mInitialTaskId, mSecondTaskId, apps, wallpapers,
                            nonApps, mStateManager, mDepthController, () -> {
                                finishedCallback.run();
                                if (mSuccessCallback != null) {
                                    mSuccessCallback.accept(true);
                                }
                                resetState();
                            }));
        }

        @Override
        public void onAnimationCancelled(boolean isKeyguardOccluded) {
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
        mSecondTaskIntent = null;
        mInitialUser = null;
        mSecondUser = null;
        mInitialStagePosition = SplitConfigurationOptions.STAGE_POSITION_UNDEFINED;
        mRecentsAnimationRunning = false;
        mLaunchingTaskView = null;
        mItemInfo = null;
        mSplitEvent = null;
        mAnimateCurrentTaskDismissal = false;
        mDismissingFromSplitPair = false;
    }

    /**
     * @return {@code true} if first task has been selected and waiting for the second task to be
     *         chosen
     */
    public boolean isSplitSelectActive() {
        return isInitialTaskIntentSet() && !isSecondTaskIntentSet();
    }

    /**
     * @return {@code true} if the first and second task have been chosen and split is waiting to
     *          be launched
     */
    public boolean isBothSplitAppsConfirmed() {
        return isInitialTaskIntentSet() && isSecondTaskIntentSet();
    }

    private boolean isInitialTaskIntentSet() {
        return (mInitialTaskId != INVALID_TASK_ID || mInitialTaskIntent != null);
    }

    public int getInitialTaskId() {
        return mInitialTaskId;
    }

    public int getSecondTaskId() {
        return mSecondTaskId;
    }

    private boolean isSecondTaskIntentSet() {
        return (mSecondTaskId != INVALID_TASK_ID || mSecondTaskIntent != null);
    }

    public void setFirstFloatingTaskView(FloatingTaskView floatingTaskView) {
        mFirstFloatingTaskView = floatingTaskView;
    }

    public FloatingTaskView getFirstFloatingTaskView() {
        return mFirstFloatingTaskView;
    }
}
