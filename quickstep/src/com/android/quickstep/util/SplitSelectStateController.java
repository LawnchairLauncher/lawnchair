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
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DESKTOP_MODE_SPLIT_LEFT_TOP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DESKTOP_MODE_SPLIT_RIGHT_BOTTOM;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_SELECTED_SECOND_APP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_SELECTION_COMPLETE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_SELECTION_EXIT_HOME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_SELECTION_INITIATED;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_PENDINGINTENT_PENDINGINTENT;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_PENDINGINTENT_TASK;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_SHORTCUT_TASK;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_SINGLE_INTENT_FULLSCREEN;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_SINGLE_SHORTCUT_FULLSCREEN;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_SINGLE_TASK_FULLSCREEN;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_TASK_PENDINGINTENT;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_TASK_SHORTCUT;
import static com.android.quickstep.util.SplitSelectDataHolder.SPLIT_TASK_TASK;
import static com.android.quickstep.views.DesktopTaskView.isDesktopModeSupported;
import static com.android.wm.shell.common.split.SplitScreenConstants.KEY_EXTRA_WIDGET_INTENT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SNAP_TO_50_50;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.NonNull;
import android.annotation.UiThread;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
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
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.BackPressHandler;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SplitSelectionListener;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.SplitInstructionsView;
import com.android.systemui.animation.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.wm.shell.common.split.SplitScreenConstants.PersistentSnapPosition;
import com.android.wm.shell.splitscreen.ISplitSelectListener;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represent data needed for the transient state when user has selected one app for split screen
 * and is in the process of either a) selecting a second app or b) exiting intention to invoke split
 */
public class SplitSelectStateController {
    private static final String TAG = "SplitSelectStateCtor";

    private StatefulActivity mContext;
    private final Handler mHandler;
    private final RecentsModel mRecentTasksModel;
    @Nullable
    private Runnable mActivityBackCallback;
    private final SplitAnimationController mSplitAnimationController;
    private final AppPairsController mAppPairsController;
    private final SplitSelectDataHolder mSplitSelectDataHolder;
    private final StatsLogManager mStatsLogManager;
    private final SystemUiProxy mSystemUiProxy;
    private final StateManager mStateManager;
    private SplitFromDesktopController mSplitFromDesktopController;
    @Nullable
    private DepthController mDepthController;
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
    /** If not null, this is the icon we want to launch from */
    private AppPairIcon mLaunchingIconView;

    /** True when the first selected split app is being launched in fullscreen. */
    private boolean mLaunchingFirstAppFullscreen;

    /**
     * Should be a constant from {@link com.android.internal.jank.Cuj} or -1, does not need to be
     * set for all launches.
     */
    private int mLaunchCuj = -1;

    private FloatingTaskView mFirstFloatingTaskView;
    private SplitInstructionsView mSplitInstructionsView;

    private final List<SplitSelectionListener> mSplitSelectionListeners = new ArrayList<>();
    /**
     * Tracks metrics from when first app is selected to split launch or cancellation. This also
     * gets passed over to shell when attempting to invoke split.
     */
    private Pair<InstanceId, com.android.launcher3.logging.InstanceId> mSessionInstanceIds;

    private final BackPressHandler mSplitBackHandler = new BackPressHandler() {
        @Override
        public boolean canHandleBack() {
            return FeatureFlags.enableSplitContextually() && isSplitSelectActive();
        }

        @Override
        public void onBackInvoked() {
            // When exiting from split selection, leave current context to go to
            // homescreen as well
            getSplitAnimationController().playPlaceholderDismissAnim(mContext,
                    LAUNCHER_SPLIT_SELECTION_EXIT_HOME);
            if (mActivityBackCallback != null) {
                mActivityBackCallback.run();
            }
        }
    };

    public SplitSelectStateController(StatefulActivity context, Handler handler,
            StateManager stateManager, DepthController depthController,
            StatsLogManager statsLogManager, SystemUiProxy systemUiProxy, RecentsModel recentsModel,
            Runnable activityBackCallback) {
        mContext = context;
        mHandler = handler;
        mStatsLogManager = statsLogManager;
        mSystemUiProxy = systemUiProxy;
        mStateManager = stateManager;
        mDepthController = depthController;
        mRecentTasksModel = recentsModel;
        mActivityBackCallback = activityBackCallback;
        mSplitAnimationController = new SplitAnimationController(this);
        mAppPairsController = new AppPairsController(context, this, statsLogManager);
        mSplitSelectDataHolder = new SplitSelectDataHolder(mContext);
    }

    public void onDestroy() {
        mContext = null;
        mActivityBackCallback = null;
        mAppPairsController.onDestroy();
        mSplitSelectDataHolder.onDestroy();
    }

    /**
     * @param alreadyRunningTask if set to {@link android.app.ActivityTaskManager#INVALID_TASK_ID}
     *                           then @param intent will be used to launch the initial task
     * @param intent will be ignored if @param alreadyRunningTask is set
     */
    public void setInitialTaskSelect(@Nullable Intent intent, @StagePosition int stagePosition,
            @NonNull ItemInfo itemInfo, StatsLogManager.EventEnum splitEvent,
            int alreadyRunningTask) {
        mSplitSelectDataHolder.setInitialTaskSelect(intent, stagePosition, itemInfo, splitEvent,
                alreadyRunningTask);
        createAndLogInstanceIdsForSession();
    }

    /**
     * To be called after first task selected from using a split shortcut from the fullscreen
     * running app.
     */
    public void setInitialTaskSelect(ActivityManager.RunningTaskInfo info,
            @StagePosition int stagePosition, @NonNull ItemInfo itemInfo,
            StatsLogManager.EventEnum splitEvent) {
        mSplitSelectDataHolder.setInitialTaskSelect(info, stagePosition, itemInfo, splitEvent);
        createAndLogInstanceIdsForSession();
    }

    /**
     * Given a list of task keys, searches through active Tasks in RecentsModel to find the last
     * active instances of these tasks. Returns an empty array if there is no such running task.
     *
     * @param componentKeys The list of ComponentKeys to search for.
     * @param callback The callback that will be executed on the list of found tasks.
     * @param findExactPairMatch If {@code true}, only finds tasks that contain BOTH of the wanted
     *                           tasks (i.e. searching for a running pair of tasks.)
     */
    public void findLastActiveTasksAndRunCallback(@Nullable List<ComponentKey> componentKeys,
            boolean findExactPairMatch, Consumer<Task[]> callback) {
        mRecentTasksModel.getTasks(taskGroups -> {
            if (componentKeys == null || componentKeys.isEmpty()) {
                callback.accept(new Task[]{});
                return;
            }

            Task[] lastActiveTasks = new Task[componentKeys.size()];

            if (findExactPairMatch) {
                // Loop through tasks in reverse, since they are ordered with most-recent tasks last
                for (int i = taskGroups.size() - 1; i >= 0; i--) {
                    GroupTask groupTask = taskGroups.get(i);
                    if (isInstanceOfAppPair(
                            groupTask, componentKeys.get(0), componentKeys.get(1))) {
                        lastActiveTasks[0] = groupTask.task1;
                        break;
                    }
                }
            } else {
                // For each key we are looking for, add to lastActiveTasks with the corresponding
                // Task (or do nothing if not found).
                for (int i = 0; i < componentKeys.size(); i++) {
                    ComponentKey key = componentKeys.get(i);
                    Task lastActiveTask = null;
                    // Loop through tasks in reverse, since they are ordered with recent tasks last
                    for (int j = taskGroups.size() - 1; j >= 0; j--) {
                        GroupTask groupTask = taskGroups.get(j);
                        Task task1 = groupTask.task1;
                        // Don't add duplicate Tasks
                        if (isInstanceOfComponent(task1, key)
                                && !Arrays.asList(lastActiveTasks).contains(task1)) {
                            lastActiveTask = task1;
                            break;
                        }
                        Task task2 = groupTask.task2;
                        if (isInstanceOfComponent(task2, key)
                                && !Arrays.asList(lastActiveTasks).contains(task2)) {
                            lastActiveTask = task2;
                            break;
                        }
                    }

                    lastActiveTasks[i] = lastActiveTask;
                }
            }

            callback.accept(lastActiveTasks);
        });
    }

    /**
     * Checks if a given Task is the most recently-active Task of type componentName. Used for
     * selecting already-running Tasks for splitscreen.
     */
    public boolean isInstanceOfComponent(@Nullable Task task, @NonNull ComponentKey componentKey) {
        // Exclude the task that is already staged
        if (task == null || task.key.id == mSplitSelectDataHolder.getInitialTaskId()) {
            return false;
        }

        return task.key.baseIntent.getComponent().equals(componentKey.componentName)
                && task.key.userId == componentKey.user.getIdentifier();
    }

    /**
     * Checks if a given GroupTask is a pair of apps that matches two given ComponentKeys. We check
     * both permutations because task order is not guaranteed in GroupTasks.
     */
    public boolean isInstanceOfAppPair(GroupTask groupTask, @NonNull ComponentKey componentKey1,
            @NonNull ComponentKey componentKey2) {
        return ((isInstanceOfComponent(groupTask.task1, componentKey1)
                && isInstanceOfComponent(groupTask.task2, componentKey2))
                ||
                (isInstanceOfComponent(groupTask.task1, componentKey2)
                        && isInstanceOfComponent(groupTask.task2, componentKey1)));
    }

    /**
     * Listener will only get callbacks going forward from the point of registration. No
     * methods will be fired upon registering.
     */
    public void registerSplitListener(@NonNull SplitSelectionListener listener) {
        if (mSplitSelectionListeners.contains(listener)) {
            return;
        }
        mSplitSelectionListeners.add(listener);
    }

    public void unregisterSplitListener(@NonNull SplitSelectionListener listener) {
        mSplitSelectionListeners.remove(listener);
    }

    private void dispatchOnSplitSelectionExit() {
        for (SplitSelectionListener listener : mSplitSelectionListeners) {
            listener.onSplitSelectionExit(false);
        }
    }

    /**
     * To be called when the both split tasks are ready to be launched. Call after launcher side
     * animations are complete.
     */
    public void launchSplitTasks(@PersistentSnapPosition int snapPosition,
            @Nullable Consumer<Boolean> callback) {
        launchTasks(callback, false /* freezeTaskList */, snapPosition, mSessionInstanceIds.first);

        mStatsLogManager.logger()
                .withItemInfo(mSplitSelectDataHolder.getSecondItemInfo())
                .withInstanceId(mSessionInstanceIds.second)
                .log(LAUNCHER_SPLIT_SELECTED_SECOND_APP);
    }

    /**
     * A version of {@link #launchTasks(Consumer, boolean, int, InstanceId)} with no success
     * callback.
     */
    public void launchSplitTasks(@PersistentSnapPosition int snapPosition) {
        launchSplitTasks(snapPosition, /* callback */ null);
    }

    /**
     * A version of {@link #launchSplitTasks(int, Consumer)} that launches with default split ratio.
     */
    public void launchSplitTasks(@Nullable Consumer<Boolean> callback) {
        launchSplitTasks(SNAP_TO_50_50, callback);
    }

    /**
     * A version of {@link #launchSplitTasks(int, Consumer)} that launches with a default split
     * ratio and no callback.
     */
    public void launchSplitTasks() {
        launchSplitTasks(SNAP_TO_50_50, null);
    }

    /**
     * Use to log an event when user exists split selection when the second app **IS NOT** selected.
     * This must be called before playing any exit animations since most animations will call
     * {@link #resetState()} which removes {@link #mSessionInstanceIds}.
     */
    public void logExitReason(StatsLogManager.EventEnum splitExitEvent) {
        StatsLogManager.StatsLogger logger = mStatsLogManager.logger();
        if (mSessionInstanceIds != null) {
            logger.withInstanceId(mSessionInstanceIds.second);
        } else {
            Log.w(TAG, "Missing session instanceIds");
        }
        logger.log(splitExitEvent);
    }

    /**
     * To be called as soon as user selects the second task (even if animations aren't complete)
     * @param task The second task that will be launched.
     */
    public void setSecondTask(Task task, ItemInfo itemInfo) {
        mSplitSelectDataHolder.setSecondTask(task.key.id, itemInfo);
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     * @param intent The second intent that will be launched.
     * @param user The user of that intent.
     */
    public void setSecondTask(Intent intent, UserHandle user, ItemInfo itemInfo) {
        mSplitSelectDataHolder.setSecondTask(intent, user, itemInfo);
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     * @param pendingIntent The second PendingIntent that will be launched.
     */
    public void setSecondTask(PendingIntent pendingIntent, ItemInfo itemInfo) {
        mSplitSelectDataHolder.setSecondTask(pendingIntent, itemInfo);
    }

    public void setSecondWidget(PendingIntent pendingIntent, Intent widgetIntent) {
        mSplitSelectDataHolder.setSecondWidget(pendingIntent, widgetIntent, null /*itemInfo*/);
    }

    /**
     * To be called when we want to launch split pairs from Overview. Split can be initiated from
     * either Overview or home, or all apps. Either both taskIds are set, or a pending intent + a
     * fill in intent with a taskId2 are set.
     * @param shellInstanceId loggingId to be used by shell, will be non-null for actions that
     *                   create a split instance, null for cases that bring existing instaces to the
     *                   foreground (quickswitch, launching previous pairs from overview)
     */
    public void launchTasks(@Nullable Consumer<Boolean> callback, boolean freezeTaskList,
            @PersistentSnapPosition int snapPosition, @Nullable InstanceId shellInstanceId) {
        TestLogging.recordEvent(
                TestProtocol.SEQUENCE_MAIN, "launchSplitTasks");
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
        Intent widgetIntent = launchData.getWidgetSecondIntent();
        int firstUserId = launchData.getInitialUserId();
        int secondUserId = launchData.getSecondUserId();
        int initialStagePosition = launchData.getInitialStagePosition();
        Bundle optionsBundle = options1.toBundle();
        Bundle extrasBundle = new Bundle(1);
        extrasBundle.putParcelable(KEY_EXTRA_WIDGET_INTENT, widgetIntent);
        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            final RemoteTransition remoteTransition = getShellRemoteTransition(firstTaskId,
                    secondTaskId, callback, "LaunchSplitPair");
            switch (launchData.getSplitLaunchType()) {
                case SPLIT_TASK_TASK ->
                        mSystemUiProxy.startTasks(firstTaskId, optionsBundle, secondTaskId,
                                null /* options2 */, initialStagePosition, snapPosition,
                                remoteTransition, shellInstanceId);

                case SPLIT_TASK_PENDINGINTENT ->
                        mSystemUiProxy.startIntentAndTask(secondPI, secondUserId, optionsBundle,
                                firstTaskId, extrasBundle, initialStagePosition, snapPosition,
                                remoteTransition, shellInstanceId);

                case SPLIT_TASK_SHORTCUT ->
                        mSystemUiProxy.startShortcutAndTask(secondShortcut, optionsBundle,
                                firstTaskId, null /*options2*/, initialStagePosition, snapPosition,
                                remoteTransition, shellInstanceId);

                case SPLIT_PENDINGINTENT_TASK ->
                        mSystemUiProxy.startIntentAndTask(firstPI, firstUserId, optionsBundle,
                                secondTaskId, null /*options2*/, initialStagePosition, snapPosition,
                                remoteTransition, shellInstanceId);

                case SPLIT_PENDINGINTENT_PENDINGINTENT ->
                        mSystemUiProxy.startIntents(firstPI, firstUserId, firstShortcut,
                                optionsBundle, secondPI, secondUserId, secondShortcut, extrasBundle,
                                initialStagePosition, snapPosition, remoteTransition,
                                shellInstanceId);

                case SPLIT_SHORTCUT_TASK ->
                        mSystemUiProxy.startShortcutAndTask(firstShortcut, optionsBundle,
                                secondTaskId, null /*options2*/, initialStagePosition, snapPosition,
                                remoteTransition, shellInstanceId);
            }
        } else {
            final RemoteAnimationAdapter adapter = getLegacyRemoteAdapter(firstTaskId, secondTaskId,
                    callback);
            switch (launchData.getSplitLaunchType()) {
                case SPLIT_TASK_TASK ->
                        mSystemUiProxy.startTasksWithLegacyTransition(firstTaskId, optionsBundle,
                                secondTaskId, null /* options2 */, initialStagePosition,
                                snapPosition, adapter, shellInstanceId);

                case SPLIT_TASK_PENDINGINTENT ->
                        mSystemUiProxy.startIntentAndTaskWithLegacyTransition(secondPI,
                                secondUserId, optionsBundle, firstTaskId, null /*options2*/,
                                initialStagePosition, snapPosition, adapter, shellInstanceId);

                case SPLIT_TASK_SHORTCUT ->
                        mSystemUiProxy.startShortcutAndTaskWithLegacyTransition(secondShortcut,
                                optionsBundle, firstTaskId, null /*options2*/, initialStagePosition,
                                snapPosition, adapter, shellInstanceId);

                case SPLIT_PENDINGINTENT_TASK ->
                        mSystemUiProxy.startIntentAndTaskWithLegacyTransition(firstPI, firstUserId,
                                optionsBundle, secondTaskId, null /*options2*/,
                                initialStagePosition, snapPosition, adapter, shellInstanceId);

                case SPLIT_PENDINGINTENT_PENDINGINTENT ->
                        mSystemUiProxy.startIntentsWithLegacyTransition(firstPI, firstUserId,
                                firstShortcut, optionsBundle, secondPI, secondUserId,
                                secondShortcut, null /*options2*/, initialStagePosition,
                                snapPosition, adapter, shellInstanceId);

                case SPLIT_SHORTCUT_TASK ->
                        mSystemUiProxy.startShortcutAndTaskWithLegacyTransition(firstShortcut,
                                optionsBundle, secondTaskId, null /*options2*/,
                                initialStagePosition, snapPosition, adapter, shellInstanceId);
            }
        }
    }

    /**
     * Used to launch split screen from a split pair that already exists, optionally with a custom
     * remote transition.
     * <p>
     * See {@link SplitSelectStateController#launchExistingSplitPair(
     * GroupedTaskView, int, int, int, Consumer, boolean, int, RemoteTransition)}
     */
    public void launchExistingSplitPair(@Nullable GroupedTaskView groupedTaskView,
            int firstTaskId, int secondTaskId, @StagePosition int stagePosition,
            Consumer<Boolean> callback, boolean freezeTaskList,
            @PersistentSnapPosition int snapPosition) {
        launchExistingSplitPair(
                groupedTaskView,
                firstTaskId,
                secondTaskId,
                stagePosition,
                callback,
                freezeTaskList,
                snapPosition,
                /* remoteTransition= */ null);
    }


    /**
     * Used to launch split screen from a split pair that already exists (usually accessible through
     * Overview). This is different than {@link #launchTasks(Consumer, boolean, int, InstanceId)}
     * in that this only launches split screen that are existing tasks. This doesn't determine which
     * API should be used (i.e. launching split with existing tasks vs intents vs shortcuts, etc).
     *
     * <p/>
     * NOTE: This is not to be used to launch AppPairs.
     */
    public void launchExistingSplitPair(@Nullable GroupedTaskView groupedTaskView,
            int firstTaskId, int secondTaskId, @StagePosition int stagePosition,
            Consumer<Boolean> callback, boolean freezeTaskList,
            @PersistentSnapPosition int snapPosition, @Nullable RemoteTransition remoteTransition) {
        mLaunchingTaskView = groupedTaskView;
        final ActivityOptions options1 = ActivityOptions.makeBasic();
        if (freezeTaskList) {
            options1.setFreezeRecentTasksReordering();
        }
        Bundle optionsBundle = options1.toBundle();

        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            final RemoteTransition transition = remoteTransition == null
                    ? getShellRemoteTransition(
                            firstTaskId, secondTaskId, callback, "LaunchExistingPair")
                    : remoteTransition;
            mSystemUiProxy.startTasks(firstTaskId, optionsBundle, secondTaskId, null /* options2 */,
                    stagePosition, snapPosition, transition, null /*shellInstanceId*/);
        } else {
            final RemoteAnimationAdapter adapter = getLegacyRemoteAdapter(firstTaskId,
                    secondTaskId, callback);
            mSystemUiProxy.startTasksWithLegacyTransition(firstTaskId, optionsBundle, secondTaskId,
                    null /* options2 */, stagePosition, snapPosition, adapter,
                    null /*shellInstanceId*/);
        }
    }

    /**
     * Launches the initially selected task/intent in fullscreen (note the same SystemUi APIs are
     * used as {@link #launchSplitTasks(int, Consumer)} because they are overloaded to launch both
     * split and fullscreen tasks)
     */
    public void launchInitialAppFullscreen(Consumer<Boolean> callback) {
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
                "LaunchAppFullscreen");
        InstanceId instanceId = mSessionInstanceIds.first;
        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            switch (launchData.getSplitLaunchType()) {
                case SPLIT_SINGLE_TASK_FULLSCREEN -> mSystemUiProxy.startTasks(firstTaskId,
                        optionsBundle, secondTaskId, null /* options2 */, initialStagePosition,
                        SNAP_TO_50_50, remoteTransition, instanceId);
                case SPLIT_SINGLE_INTENT_FULLSCREEN -> mSystemUiProxy.startIntentAndTask(firstPI,
                        firstUserId, optionsBundle, secondTaskId, null /*options2*/,
                        initialStagePosition, SNAP_TO_50_50, remoteTransition, instanceId);
                case SPLIT_SINGLE_SHORTCUT_FULLSCREEN -> mSystemUiProxy.startShortcutAndTask(
                        initialShortcut, optionsBundle, firstTaskId, null /* options2 */,
                        initialStagePosition, SNAP_TO_50_50, remoteTransition, instanceId);
            }
        } else {
            final RemoteAnimationAdapter adapter = getLegacyRemoteAdapter(firstTaskId,
                    secondTaskId, callback);
            switch (launchData.getSplitLaunchType()) {
                case SPLIT_SINGLE_TASK_FULLSCREEN -> mSystemUiProxy.startTasksWithLegacyTransition(
                        firstTaskId, optionsBundle, secondTaskId, null /* options2 */,
                        initialStagePosition, SNAP_TO_50_50, adapter, instanceId);
                case SPLIT_SINGLE_INTENT_FULLSCREEN ->
                        mSystemUiProxy.startIntentAndTaskWithLegacyTransition(firstPI, firstUserId,
                                optionsBundle, secondTaskId, null /*options2*/,
                                initialStagePosition, SNAP_TO_50_50, adapter, instanceId);
                case SPLIT_SINGLE_SHORTCUT_FULLSCREEN ->
                        mSystemUiProxy.startShortcutAndTaskWithLegacyTransition(
                                initialShortcut, optionsBundle, firstTaskId, null /* options2 */,
                                initialStagePosition, SNAP_TO_50_50, adapter, instanceId);
            }
        }
    }

    public void initSplitFromDesktopController(Launcher launcher) {
        mSplitFromDesktopController = new SplitFromDesktopController(launcher);
    }

    private RemoteTransition getShellRemoteTransition(int firstTaskId, int secondTaskId,
            @Nullable Consumer<Boolean> callback, String transitionName) {
        final RemoteSplitLaunchTransitionRunner animationRunner =
                new RemoteSplitLaunchTransitionRunner(firstTaskId, secondTaskId, callback);
        return new RemoteTransition(animationRunner,
                ActivityThread.currentActivityThread().getApplicationThread(), transitionName);
    }

    private RemoteAnimationAdapter getLegacyRemoteAdapter(int firstTaskId, int secondTaskId,
            @Nullable Consumer<Boolean> callback) {
        final RemoteSplitLaunchAnimationRunner animationRunner =
                new RemoteSplitLaunchAnimationRunner(firstTaskId, secondTaskId, callback);
        return new RemoteAnimationAdapter(animationRunner, 300, 150,
                ActivityThread.currentActivityThread().getApplicationThread());
    }

    /**
     * Will initialize {@link #mSessionInstanceIds} if null and log the first split event from
     * {@link #mSplitSelectDataHolder}
     */
    private void createAndLogInstanceIdsForSession() {
        if (mSessionInstanceIds != null) {
            Log.w(TAG, "SessionIds should be null");
        }
        // Log separately the start of the session and then the first app selected
        mSessionInstanceIds = LogUtils.getShellShareableInstanceId();
        mStatsLogManager.logger()
                .withInstanceId(mSessionInstanceIds.second)
                .log(LAUNCHER_SPLIT_SELECTION_INITIATED);

        mStatsLogManager.logger()
                .withItemInfo(mSplitSelectDataHolder.getItemInfo())
                .withInstanceId(mSessionInstanceIds.second)
                .log(mSplitSelectDataHolder.getSplitEvent());
    }

    public @StagePosition int getActiveSplitStagePosition() {
        return mSplitSelectDataHolder.getInitialStagePosition();
    }

    public StatsLogManager.EventEnum getSplitEvent() {
        return mSplitSelectDataHolder.getSplitEvent();
    }

    public void setRecentsAnimationRunning(boolean running) {
        mRecentsAnimationRunning = running;
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

    public void setLaunchingCuj(int launchCuj) {
        mLaunchCuj = launchCuj;
    }

    /**
     * Requires Shell Transitions
     */
    private class RemoteSplitLaunchTransitionRunner extends IRemoteTransition.Stub {

        private final int mInitialTaskId;
        private final int mSecondTaskId;
        private Consumer<Boolean> mFinishCallback;

        RemoteSplitLaunchTransitionRunner(int initialTaskId, int secondTaskId,
                @Nullable Consumer<Boolean> callback) {
            mInitialTaskId = initialTaskId;
            mSecondTaskId = secondTaskId;
            mFinishCallback = callback;
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
                // Only animate from taskView if it's already visible
                boolean shouldLaunchFromTaskView = mLaunchingTaskView != null
                        && mLaunchingTaskView.getRecentsView() != null
                        && mLaunchingTaskView.getRecentsView().isTaskViewVisible(
                        mLaunchingTaskView);
                mSplitAnimationController.playSplitLaunchAnimation(
                        shouldLaunchFromTaskView ? mLaunchingTaskView : null,
                        mLaunchingIconView,
                        mInitialTaskId,
                        mSecondTaskId,
                        null /* apps */,
                        null /* wallpapers */,
                        null /* nonApps */,
                        mStateManager,
                        mDepthController,
                        info, t, () -> {
                            finishAdapter.run();
                            cleanup(true /*success*/);
                        });
            });
        }

        @Override
        public void mergeAnimation(IBinder transition, TransitionInfo info,
                SurfaceControl.Transaction t, IBinder mergeTarget,
                IRemoteTransitionFinishedCallback finishedCallback) { }

        @Override
        public void onTransitionConsumed(IBinder transition, boolean aborted)
                throws RemoteException {
            MAIN_EXECUTOR.execute(() -> {
                cleanup(false /*success*/);
            });
        }

        /**
         * Must be called on UI thread.
         * @param success if launching the split apps occurred successfully or not
         */
        @UiThread
        private void cleanup(boolean success) {
            if (mFinishCallback != null) {
                mFinishCallback.accept(success);
                mFinishCallback = null;
            }
            resetState();
        }
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
                @Nullable Consumer<Boolean> successCallback) {
            mInitialTaskId = initialTaskId;
            mSecondTaskId = secondTaskId;
            mSuccessCallback = successCallback;
        }

        @Override
        public void onAnimationStart(int transit, RemoteAnimationTarget[] apps,
                RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
                Runnable finishedCallback) {
            postAsyncCallback(mHandler,
                    () -> mSplitAnimationController.playSplitLaunchAnimation(mLaunchingTaskView,
                            mLaunchingIconView, mInitialTaskId, mSecondTaskId, apps, wallpapers,
                            nonApps, mStateManager, mDepthController, null /* info */, null /* t */,
                            () -> {
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
     * To be called whenever we exit split selection state. If
     * {@link FeatureFlags#enableSplitContextually()} is set, this should be the
     * central way split is getting reset, which should then go through the callbacks to reset
     * other state.
     */
    public void resetState() {
        mSplitSelectDataHolder.resetState();
        dispatchOnSplitSelectionExit();
        mRecentsAnimationRunning = false;
        mLaunchingTaskView = null;
        mLaunchingIconView = null;
        mAnimateCurrentTaskDismissal = false;
        mDismissingFromSplitPair = false;
        mFirstFloatingTaskView = null;
        mSplitInstructionsView = null;
        mLaunchingFirstAppFullscreen = false;

        if (mLaunchCuj != -1) {
            InteractionJankMonitorWrapper.end(mLaunchCuj);
        }
        mLaunchCuj = -1;

        if (mSessionInstanceIds != null) {
            mStatsLogManager.logger()
                    .withInstanceId(mSessionInstanceIds.second)
                    .log(LAUNCHER_SPLIT_SELECTION_COMPLETE);
        }
        mSessionInstanceIds = null;
    }

    /**
     * @return {@code true} if first task has been selected and waiting for the second task to be
     *         chosen
     */
    public boolean isSplitSelectActive() {
        return mSplitSelectDataHolder.isSplitSelectActive();
    }

    /**
     * @return {@code true} if the first and second task have been chosen and split is waiting to
     *          be launched
     */
    public boolean isBothSplitAppsConfirmed() {
        return mSplitSelectDataHolder.isBothSplitAppsConfirmed();
    }

    public boolean isLaunchingFirstAppFullscreen() {
        return mLaunchingFirstAppFullscreen;
    }

    public int getInitialTaskId() {
        return mSplitSelectDataHolder.getInitialTaskId();
    }

    public int getSecondTaskId() {
        return mSplitSelectDataHolder.getSecondTaskId();
    }

    public void setLaunchingFirstAppFullscreen() {
        mLaunchingFirstAppFullscreen = true;
    }
    public void setFirstFloatingTaskView(FloatingTaskView floatingTaskView) {
        mFirstFloatingTaskView = floatingTaskView;
    }

    public void setSplitInstructionsView(SplitInstructionsView splitInstructionsView) {
        mSplitInstructionsView = splitInstructionsView;
    }

    @Nullable
    public FloatingTaskView getFirstFloatingTaskView() {
        return mFirstFloatingTaskView;
    }

    @Nullable
    public SplitInstructionsView getSplitInstructionsView() {
        return mSplitInstructionsView;
    }

    public AppPairsController getAppPairsController() {
        return mAppPairsController;
    }

    public void setLaunchingIconView(AppPairIcon launchingIconView) {
        mLaunchingIconView = launchingIconView;
    }

    public BackPressHandler getSplitBackHandler() {
        return mSplitBackHandler;
    }

    public void dump(String prefix, PrintWriter writer) {
        if (mSplitSelectDataHolder != null) {
            mSplitSelectDataHolder.dump(prefix, writer);
        }
    }

    public class SplitFromDesktopController {
        private static final String TAG = "SplitFromDesktopController";

        private final Launcher mLauncher;
        private final OverviewComponentObserver mOverviewComponentObserver;
        private final int mSplitPlaceholderSize;
        private final int mSplitPlaceholderInset;
        private ActivityManager.RunningTaskInfo mTaskInfo;
        private ISplitSelectListener mSplitSelectListener;
        private Drawable mAppIcon;

        public SplitFromDesktopController(Launcher launcher) {
            mLauncher = launcher;
            RecentsAnimationDeviceState deviceState = new RecentsAnimationDeviceState(
                    launcher.getApplicationContext());
            mOverviewComponentObserver =
                    new OverviewComponentObserver(launcher.getApplicationContext(), deviceState);
            mSplitPlaceholderSize = mLauncher.getResources().getDimensionPixelSize(
                    R.dimen.split_placeholder_size);
            mSplitPlaceholderInset = mLauncher.getResources().getDimensionPixelSize(
                    R.dimen.split_placeholder_inset);
            mSplitSelectListener = new ISplitSelectListener.Stub() {
                @Override
                public boolean onRequestSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
                        int splitPosition, Rect taskBounds) {
                    if (!isDesktopModeSupported()) return false;
                    MAIN_EXECUTOR.execute(() -> enterSplitSelect(taskInfo, splitPosition,
                            taskBounds));
                    return true;
                }
            };
            SystemUiProxy.INSTANCE.get(mLauncher).registerSplitSelectListener(mSplitSelectListener);
        }

        /**
         * Enter split select from desktop mode.
         * @param taskInfo the desktop task to move to split stage
         * @param splitPosition the stage position used for this transition
         * @param taskBounds the bounds of the task, used for {@link FloatingTaskView} animation
         */
        public void enterSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
                int splitPosition, Rect taskBounds) {
            mTaskInfo = taskInfo;
            String packageName = mTaskInfo.realActivity.getPackageName();
            PackageManager pm = mLauncher.getApplicationContext().getPackageManager();
            IconProvider provider = new IconProvider(mLauncher.getApplicationContext());
            try {
                mAppIcon = provider.getIcon(pm.getActivityInfo(mTaskInfo.baseActivity,
                     PackageManager.ComponentInfoFlags.of(0)));
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Package not found: " + packageName, e);
            }
            RecentsAnimationCallbacks callbacks = new RecentsAnimationCallbacks(
                    SystemUiProxy.INSTANCE.get(mLauncher.getApplicationContext()),
                    false /* allowMinimizeSplitScreen */);

            DesktopSplitRecentsAnimationListener listener =
                    new DesktopSplitRecentsAnimationListener(splitPosition, taskBounds);

            MAIN_EXECUTOR.execute(() -> {
                callbacks.addListener(listener);
                UI_HELPER_EXECUTOR.execute(
                        // Transition from app to enter stage split in launcher with
                        // recents animation.
                        () -> ActivityManagerWrapper.getInstance().startRecentsActivity(
                                mOverviewComponentObserver.getOverviewIntent(),
                                SystemClock.uptimeMillis(), callbacks, null, null));
            });
        }

        private class DesktopSplitRecentsAnimationListener implements
                RecentsAnimationCallbacks.RecentsAnimationListener {
            private final Rect mTempRect = new Rect();
            private final RectF mTaskBounds = new RectF();
            private final int mSplitPosition;

            DesktopSplitRecentsAnimationListener(int splitPosition, Rect taskBounds) {
                mSplitPosition = splitPosition;
                mTaskBounds.set(taskBounds);
            }

            @Override
            public void onRecentsAnimationStart(RecentsAnimationController controller,
                    RecentsAnimationTargets targets) {
                StatsLogManager.LauncherEvent launcherDesktopSplitEvent =
                        mSplitPosition == STAGE_POSITION_BOTTOM_OR_RIGHT ?
                        LAUNCHER_DESKTOP_MODE_SPLIT_RIGHT_BOTTOM :
                        LAUNCHER_DESKTOP_MODE_SPLIT_LEFT_TOP;
                setInitialTaskSelect(mTaskInfo, mSplitPosition,
                        null, launcherDesktopSplitEvent);

                RecentsView recentsView = mLauncher.getOverviewPanel();
                recentsView.getPagedOrientationHandler().getInitialSplitPlaceholderBounds(
                        mSplitPlaceholderSize, mSplitPlaceholderInset,
                        mLauncher.getDeviceProfile(), getActiveSplitStagePosition(), mTempRect);

                PendingAnimation anim = new PendingAnimation(
                        SplitAnimationTimings.TABLET_HOME_TO_SPLIT.getDuration());
                final FloatingTaskView floatingTaskView = FloatingTaskView.getFloatingTaskView(
                        mLauncher, mLauncher.getDragLayer(),
                        null /* thumbnail */,
                        mAppIcon, new RectF());
                floatingTaskView.setAlpha(1);
                floatingTaskView.addStagingAnimation(anim, mTaskBounds, mTempRect,
                        false /* fadeWithThumbnail */, true /* isStagedTask */);
                setFirstFloatingTaskView(floatingTaskView);

                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        controller.finish(true /* toRecents */, null /* onFinishComplete */,
                                false /* sendUserLeaveHint */);
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        SystemUiProxy.INSTANCE.get(mLauncher.getApplicationContext())
                                .onDesktopSplitSelectAnimComplete(mTaskInfo);
                    }
                });
                anim.buildAnim().start();
            }
        }
    }
}
