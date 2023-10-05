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
import static android.app.PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT;
import static android.app.PendingIntent.FLAG_MUTABLE;

import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.DEFAULT_SPLIT_RATIO;
import static com.android.launcher3.util.SplitConfigurationOptions.getOppositeStagePosition;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_PENDINGINTENT_PENDINGINTENT;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_PENDINGINTENT_TASK;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_SHORTCUT_TASK;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_SINGLE_INTENT_FULLSCREEN;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_SINGLE_SHORTCUT_FULLSCREEN;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_SINGLE_TASK_FULLSCREEN;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_TASK_PENDINGINTENT;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_TASK_SHORTCUT;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_TASK_TASK;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
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
import com.android.launcher3.config.FeatureFlags;
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

import java.io.PrintWriter;
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
    private final AppPairsController mAppPairsController;
    private final SplitSelectDataHolder mSplitSelectDataHolder;
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
     * together when split is confirmed with an Intent. Either this or {@link #mSecondPendingIntent}
     * will be set, but not both
     */
    private Intent mSecondTaskIntent;
    /**
     * Set when split is confirmed via a widget. Either this or {@link #mSecondTaskIntent} will be
     * set, but not both
     */
    private PendingIntent mSecondPendingIntent;
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
        mAppPairsController = new AppPairsController(context, this);
        mSplitSelectDataHolder = new SplitSelectDataHolder(mContext);
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

        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            mSplitSelectDataHolder.setInitialTaskSelect(intent, stagePosition, itemInfo, splitEvent,
                    alreadyRunningTask);
        }
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

        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            mSplitSelectDataHolder.setInitialTaskSelect(info, stagePosition, itemInfo, splitEvent);
        }
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
     * <p>
     * Used in various task-switching or splitscreen operations when we need to check if there is a
     * currently running Task of a certain type and use the most recent one.
     */
    public void findLastActiveTaskAndRunCallback(
            @Nullable ComponentKey componentKey, Consumer<Task> callback) {
        mRecentTasksModel.getTasks(taskGroups -> {
            if (componentKey == null) {
                callback.accept(null);
                return;
            }
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
    public boolean isInstanceOfComponent(@Nullable Task task, @NonNull ComponentKey componentKey) {
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

        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            mSplitSelectDataHolder.setSecondTask(task.key.id);
        }
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     * @param intent The second intent that will be launched.
     * @param user The user of that intent.
     */
    public void setSecondTask(Intent intent, UserHandle user) {
        mSecondTaskIntent = intent;
        mSecondUser = user;

        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            mSplitSelectDataHolder.setSecondTask(intent, user);
        }
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     * Sets {@link #mSecondUser} from that of the pendingIntent
     * @param pendingIntent The second PendingIntent that will be launched.
     */
    public void setSecondTask(PendingIntent pendingIntent) {
        mSecondPendingIntent = pendingIntent;
        mSecondUser = pendingIntent.getCreatorUserHandle();

        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            mSplitSelectDataHolder.setSecondTask(pendingIntent);
        }
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
        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            launchTasksRefactored(callback, freezeTaskList, splitRatio, shellInstanceId);
            return;
        }

        final ActivityOptions options1 = ActivityOptions.makeBasic();
        if (freezeTaskList) {
            options1.setFreezeRecentTasksReordering();
        }
        boolean hasSecondaryPendingIntent = mSecondPendingIntent != null;
        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            final RemoteTransition remoteTransition = getShellRemoteTransition(taskId1, taskId2,
                    callback);
            if (intent1 == null && (intent2 == null && !hasSecondaryPendingIntent)) {
                mSystemUiProxy.startTasks(taskId1, options1.toBundle(), taskId2,
                        null /* options2 */, stagePosition, splitRatio, remoteTransition,
                        shellInstanceId);
            } else if (intent2 == null && !hasSecondaryPendingIntent) {
                launchIntentOrShortcut(intent1, mInitialUser, options1, taskId2, stagePosition,
                        splitRatio, remoteTransition, shellInstanceId);
            } else if (intent1 == null) {
                launchIntentOrShortcut(intent2, mSecondUser, options1, taskId1,
                        getOppositeStagePosition(stagePosition), splitRatio, remoteTransition,
                        shellInstanceId);
            } else {
                mSystemUiProxy.startIntents(getPendingIntent(intent1, mInitialUser),
                        mInitialUser.getIdentifier(), getShortcutInfo(intent1, mInitialUser),
                        options1.toBundle(), hasSecondaryPendingIntent
                                ? mSecondPendingIntent
                                : getPendingIntent(intent2, mSecondUser),
                        mSecondUser.getIdentifier(), getShortcutInfo(intent2, mSecondUser),
                        null /* options2 */, stagePosition, splitRatio, remoteTransition,
                        shellInstanceId);
            }
        } else {
            final RemoteAnimationAdapter adapter = getLegacyRemoteAdapter(taskId1, taskId2,
                    callback);

            if (intent1 == null && (intent2 == null && !hasSecondaryPendingIntent)) {
                mSystemUiProxy.startTasksWithLegacyTransition(taskId1, options1.toBundle(),
                        taskId2, null /* options2 */, stagePosition, splitRatio, adapter,
                        shellInstanceId);
            } else if (intent2 == null && !hasSecondaryPendingIntent) {
                launchIntentOrShortcutLegacy(intent1, mInitialUser, options1, taskId2,
                        stagePosition, splitRatio, adapter, shellInstanceId);
            } else if (intent1 == null) {
                launchIntentOrShortcutLegacy(intent2, mSecondUser, options1, taskId1,
                        getOppositeStagePosition(stagePosition), splitRatio, adapter,
                        shellInstanceId);
            } else {
                mSystemUiProxy.startIntentsWithLegacyTransition(
                        getPendingIntent(intent1, mInitialUser), mInitialUser.getIdentifier(),
                        getShortcutInfo(intent1, mInitialUser), options1.toBundle(),
                        hasSecondaryPendingIntent
                                ? mSecondPendingIntent
                                : getPendingIntent(intent2, mSecondUser),
                        mSecondUser.getIdentifier(), getShortcutInfo(intent2, mSecondUser),
                        null /* options2 */, stagePosition, splitRatio, adapter, shellInstanceId);
            }
        }
    }

    private void launchTasksRefactored(Consumer<Boolean> callback, boolean freezeTaskList,
            float splitRatio, @Nullable InstanceId shellInstanceId) {
        final ActivityOptions options1 = ActivityOptions.makeBasic();
        if (freezeTaskList) {
            options1.setFreezeRecentTasksReordering();
        }

        SplitSelectDataHolder.SplitLaunchData launchData =
                mSplitSelectDataHolder.getSplitLaunchData();
        int firstTaskId = launchData.getInitialTaskId();
        int secondTaskId = launchData.getSecondTaskId();
        ShortcutInfo firstShortcut = launchData.getInitialShortcut();
        ShortcutInfo secondShortcut = launchData.getSecondShortcut();
        PendingIntent firstPI = launchData.getInitialPendingIntent();
        PendingIntent secondPI = launchData.getSecondPendingIntent();
        int firstUserId = launchData.getInitialUserId();
        int secondUserId = launchData.getSecondUserId();
        int initialStagePosition = launchData.getInitialStagePosition();
        Bundle optionsBundle = options1.toBundle();

        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            final RemoteTransition remoteTransition = getShellRemoteTransition(firstTaskId,
                    secondTaskId, callback);
            switch (launchData.getSplitLaunchType()) {
                case SPLIT_TASK_TASK ->
                        mSystemUiProxy.startTasks(firstTaskId, optionsBundle, secondTaskId,
                                null /* options2 */, initialStagePosition, splitRatio,
                                remoteTransition, shellInstanceId);

                case SPLIT_TASK_PENDINGINTENT ->
                        mSystemUiProxy.startIntentAndTask(secondPI, secondUserId, optionsBundle,
                                firstTaskId, null /*options2*/, initialStagePosition, splitRatio,
                                remoteTransition, shellInstanceId);

                case SPLIT_TASK_SHORTCUT ->
                        mSystemUiProxy.startShortcutAndTask(secondShortcut, optionsBundle,
                                firstTaskId, null /*options2*/, initialStagePosition, splitRatio,
                                remoteTransition, shellInstanceId);

                case SPLIT_PENDINGINTENT_TASK ->
                        mSystemUiProxy.startIntentAndTask(firstPI, firstUserId, optionsBundle,
                                secondTaskId, null /*options2*/, initialStagePosition, splitRatio,
                                remoteTransition, shellInstanceId);

                case SPLIT_PENDINGINTENT_PENDINGINTENT ->
                        mSystemUiProxy.startIntents(firstPI, firstUserId, firstShortcut,
                                optionsBundle, secondPI, secondUserId, secondShortcut,
                                null /*options2*/, initialStagePosition, splitRatio,
                                remoteTransition, shellInstanceId);

                case SPLIT_SHORTCUT_TASK ->
                        mSystemUiProxy.startShortcutAndTask(firstShortcut, optionsBundle,
                                secondTaskId, null /*options2*/, initialStagePosition, splitRatio,
                                remoteTransition, shellInstanceId);
            }
        } else {
            final RemoteAnimationAdapter adapter = getLegacyRemoteAdapter(firstTaskId, secondTaskId,
                    callback);
            switch (launchData.getSplitLaunchType()) {
                case SPLIT_TASK_TASK ->
                        mSystemUiProxy.startTasksWithLegacyTransition(firstTaskId, optionsBundle,
                                secondTaskId, null /* options2 */, initialStagePosition,
                                splitRatio, adapter, shellInstanceId);

                case SPLIT_TASK_PENDINGINTENT ->
                        mSystemUiProxy.startIntentAndTaskWithLegacyTransition(secondPI,
                                secondUserId, optionsBundle, firstTaskId, null /*options2*/,
                                initialStagePosition, splitRatio, adapter, shellInstanceId);

                case SPLIT_TASK_SHORTCUT ->
                        mSystemUiProxy.startShortcutAndTaskWithLegacyTransition(secondShortcut,
                                optionsBundle, firstTaskId, null /*options2*/, initialStagePosition,
                                splitRatio, adapter, shellInstanceId);

                case SPLIT_PENDINGINTENT_TASK ->
                        mSystemUiProxy.startIntentAndTaskWithLegacyTransition(firstPI, firstUserId,
                                optionsBundle, secondTaskId, null /*options2*/,
                                initialStagePosition, splitRatio, adapter, shellInstanceId);

                case SPLIT_PENDINGINTENT_PENDINGINTENT ->
                        mSystemUiProxy.startIntentsWithLegacyTransition(firstPI, firstUserId,
                                firstShortcut, optionsBundle, secondPI, secondUserId,
                                secondShortcut, null /*options2*/, initialStagePosition, splitRatio,
                                adapter, shellInstanceId);

                case SPLIT_SHORTCUT_TASK ->
                        mSystemUiProxy.startShortcutAndTaskWithLegacyTransition(firstShortcut,
                                optionsBundle, secondTaskId, null /*options2*/,
                                initialStagePosition, splitRatio, adapter, shellInstanceId);
            }
        }
    }

    /**
     * Used to launch split screen from a split pair that already exists (usually accessible through
     * Overview). This is different than
     * {@link #launchTasks(int, Intent, int, Intent, int, Consumer, boolean, float, InstanceId)} in
     * that this only launches split screen that are existing tasks. This doesn't determine which
     * API should be used (i.e. launching split with existing tasks vs intents vs shortcuts, etc).
     *
     * <p/>
     * NOTE: This is not to be used to launch AppPairs.
     */
    public void launchExistingSplitPair(@Nullable GroupedTaskView groupedTaskView,
            int firstTaskId, int secondTaskId, @StagePosition int stagePosition,
            Consumer<Boolean> callback, boolean freezeTaskList, float splitRatio) {
        mLaunchingTaskView = groupedTaskView;
        final ActivityOptions options1 = ActivityOptions.makeBasic();
        if (freezeTaskList) {
            options1.setFreezeRecentTasksReordering();
        }
        Bundle optionsBundle = options1.toBundle();

        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            final RemoteTransition remoteTransition = getShellRemoteTransition(firstTaskId,
                    secondTaskId, callback);
            mSystemUiProxy.startTasks(firstTaskId, optionsBundle, secondTaskId,
                    null /* options2 */, stagePosition, splitRatio,
                    remoteTransition, null /*shellInstanceId*/);
        } else {
            final RemoteAnimationAdapter adapter = getLegacyRemoteAdapter(firstTaskId,
                    secondTaskId, callback);
            mSystemUiProxy.startTasksWithLegacyTransition(firstTaskId, optionsBundle,
                    secondTaskId, null /* options2 */, stagePosition,
                    splitRatio, adapter, null /*shellInstanceId*/);
        }
    }

    /**
     * Launches the initially selected task/intent in fullscreen (note the same SystemUi APIs are
     * used as {@link #launchSplitTasks(Consumer)} because they are overloaded to launch both
     * split and fullscreen tasks)
     */
    public void launchInitialAppFullscreen(Consumer<Boolean> callback) {
        if (!FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            launchSplitTasks(callback);
            return;
        }

        final ActivityOptions options1 = ActivityOptions.makeBasic();
        SplitSelectDataHolder.SplitLaunchData launchData =
                mSplitSelectDataHolder.getFullscreenLaunchData();
        int firstTaskId = launchData.getInitialTaskId();
        int secondTaskId = launchData.getSecondTaskId();
        PendingIntent firstPI = launchData.getInitialPendingIntent();
        int firstUserId = launchData.getInitialUserId();
        int initialStagePosition = launchData.getInitialStagePosition();
        ShortcutInfo initialShortcut = launchData.getInitialShortcut();
        Bundle optionsBundle = options1.toBundle();

        final RemoteSplitLaunchTransitionRunner animationRunner =
                new RemoteSplitLaunchTransitionRunner(firstTaskId, secondTaskId, callback);
        final RemoteTransition remoteTransition = new RemoteTransition(animationRunner,
                ActivityThread.currentActivityThread().getApplicationThread(),
                "LaunchSplitPair");
        InstanceId instanceId = LogUtils.getShellShareableInstanceId().first;
        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            switch (launchData.getSplitLaunchType()) {
                case SPLIT_SINGLE_TASK_FULLSCREEN -> mSystemUiProxy.startTasks(firstTaskId,
                        optionsBundle, secondTaskId, null /* options2 */, initialStagePosition,
                        DEFAULT_SPLIT_RATIO, remoteTransition, instanceId);
                case SPLIT_SINGLE_INTENT_FULLSCREEN -> mSystemUiProxy.startIntentAndTask(firstPI,
                        firstUserId, optionsBundle, secondTaskId, null /*options2*/,
                        initialStagePosition, DEFAULT_SPLIT_RATIO, remoteTransition,
                        instanceId);
                case SPLIT_SINGLE_SHORTCUT_FULLSCREEN -> mSystemUiProxy.startShortcutAndTask(
                        initialShortcut, optionsBundle, firstTaskId, null /* options2 */,
                        initialStagePosition, DEFAULT_SPLIT_RATIO, remoteTransition, instanceId);
            }
        } else {
            final RemoteAnimationAdapter adapter = getLegacyRemoteAdapter(firstTaskId,
                    secondTaskId, callback);
            switch (launchData.getSplitLaunchType()) {
                case SPLIT_SINGLE_TASK_FULLSCREEN -> mSystemUiProxy.startTasksWithLegacyTransition(
                        firstTaskId, optionsBundle, secondTaskId, null /* options2 */,
                        initialStagePosition, DEFAULT_SPLIT_RATIO, adapter, instanceId);
                case SPLIT_SINGLE_INTENT_FULLSCREEN ->
                        mSystemUiProxy.startIntentAndTaskWithLegacyTransition(firstPI, firstUserId,
                                optionsBundle, secondTaskId, null /*options2*/,
                                initialStagePosition, DEFAULT_SPLIT_RATIO, adapter,
                                instanceId);
                case SPLIT_SINGLE_SHORTCUT_FULLSCREEN ->
                        mSystemUiProxy.startShortcutAndTaskWithLegacyTransition(
                                initialShortcut, optionsBundle, firstTaskId, null /* options2 */,
                                initialStagePosition, DEFAULT_SPLIT_RATIO, adapter, instanceId);
            }
        }
    }

    private RemoteTransition getShellRemoteTransition(int firstTaskId, int secondTaskId,
            Consumer<Boolean> callback) {
        final RemoteSplitLaunchTransitionRunner animationRunner =
                new RemoteSplitLaunchTransitionRunner(firstTaskId, secondTaskId, callback);
        return new RemoteTransition(animationRunner,
                ActivityThread.currentActivityThread().getApplicationThread(), "LaunchSplitPair");
    }

    private RemoteAnimationAdapter getLegacyRemoteAdapter(int firstTaskId, int secondTaskId,
            Consumer<Boolean> callback) {
        final RemoteSplitLaunchAnimationRunner animationRunner =
                new RemoteSplitLaunchAnimationRunner(firstTaskId, secondTaskId, callback);
        return new RemoteAnimationAdapter(animationRunner, 300, 150,
                ActivityThread.currentActivityThread().getApplicationThread());
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
            mSystemUiProxy.startIntentAndTask(getPendingIntent(intent, user), user.getIdentifier(),
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
                    getPendingIntent(intent, user), user.getIdentifier(), options1.toBundle(),
                    taskId, null /* options2 */, stagePosition, splitRatio, adapter,
                    shellInstanceId);
        }
    }

    /**
     * We treat launching by intents as grouped in two ways,
     * If {@param intent} represents the first app, we always convert the intent to pending intent
     * It it represents second app, either the second intent OR mSecondPendingIntent will be used
     *    convert second intent to a pendingIntent OR return mSecondPendingIntent as is
     */
    private PendingIntent getPendingIntent(Intent intent, UserHandle user) {
        boolean isParamFirstIntent = intent != null && intent == mInitialTaskIntent;
        if (!isParamFirstIntent && mSecondPendingIntent != null) {
            // Because mSecondPendingIntent and mSecondTaskIntent can't both be set, we know we need
            // to be using mSecondPendingIntent
            return mSecondPendingIntent;
        }

        // intent param must either be mInitialTaskIntent or mSecondTaskIntent, convert either to
        // a new PendingIntent
        return intent == null ? null : (user != null
                ? PendingIntent.getActivityAsUser(mContext, 0, intent,
                FLAG_MUTABLE | FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT, null /* options */, user)
                : PendingIntent.getActivity(mContext, 0, intent,
                        FLAG_MUTABLE | FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT));
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
        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            mSplitSelectDataHolder.resetState();
        }
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
        mSecondPendingIntent = null;
    }

    /**
     * @return {@code true} if first task has been selected and waiting for the second task to be
     *         chosen
     */
    public boolean isSplitSelectActive() {
        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            return mSplitSelectDataHolder.isSplitSelectActive();
        } else {
            return isInitialTaskIntentSet() && !isSecondTaskIntentSet();
        }
    }

    /**
     * @return {@code true} if the first and second task have been chosen and split is waiting to
     *          be launched
     */
    public boolean isBothSplitAppsConfirmed() {
        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            return mSplitSelectDataHolder.isBothSplitAppsConfirmed();
        } else {
            return isInitialTaskIntentSet() && isSecondTaskIntentSet();
        }
    }

    private boolean isInitialTaskIntentSet() {
        return (mInitialTaskId != INVALID_TASK_ID || mInitialTaskIntent != null);
    }

    public int getInitialTaskId() {
        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            return mSplitSelectDataHolder.getInitialTaskId();
        } else {
            return mInitialTaskId;
        }
    }

    public int getSecondTaskId() {
        if (FeatureFlags.ENABLE_SPLIT_LAUNCH_DATA_REFACTOR.get()) {
            return mSplitSelectDataHolder.getSecondTaskId();
        } else {
            return mSecondTaskId;
        }
    }

    private boolean isSecondTaskIntentSet() {
        return (mSecondTaskId != INVALID_TASK_ID || mSecondTaskIntent != null
                || mSecondPendingIntent != null);
    }

    public void setFirstFloatingTaskView(FloatingTaskView floatingTaskView) {
        mFirstFloatingTaskView = floatingTaskView;
    }

    public FloatingTaskView getFirstFloatingTaskView() {
        return mFirstFloatingTaskView;
    }

    public AppPairsController getAppPairsController() {
        return mAppPairsController;
    }

    public void dump(String prefix, PrintWriter writer) {
        if (mSplitSelectDataHolder != null) {
            mSplitSelectDataHolder.dump(prefix, writer);
        }
    }
}
