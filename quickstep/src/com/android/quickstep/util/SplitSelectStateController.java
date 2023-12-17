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
import static com.android.launcher3.config.FeatureFlags.ENABLE_SPLIT_FROM_DESKTOP_TO_WORKSPACE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DESKTOP_MODE_SPLIT_LEFT_TOP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DESKTOP_MODE_SPLIT_RIGHT_BOTTOM;
import static com.android.launcher3.testing.shared.TestProtocol.LAUNCH_SPLIT_PAIR;
import static com.android.launcher3.testing.shared.TestProtocol.testLogD;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.DEFAULT_SPLIT_RATIO;
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.Context;
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
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
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
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.SplitInstructionsView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.wm.shell.splitscreen.ISplitSelectListener;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represent data needed for the transient state when user has selected one app for split screen
 * and is in the process of either a) selecting a second app or b) exiting intention to invoke split
 */
public class SplitSelectStateController {
    private static final String TAG = "SplitSelectStateCtor";

    private Context mContext;
    private final Handler mHandler;
    private final RecentsModel mRecentTasksModel;
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

    private FloatingTaskView mFirstFloatingTaskView;
    private SplitInstructionsView mSplitInstructionsView;

    private final List<SplitSelectionListener> mSplitSelectionListeners = new ArrayList<>();

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
        mAppPairsController = new AppPairsController(context, this, statsLogManager);
        mSplitSelectDataHolder = new SplitSelectDataHolder(mContext);
    }

    public void onDestroy() {
        mContext = null;
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
    }

    /**
     * To be called after first task selected from using a split shortcut from the fullscreen
     * running app.
     */
    public void setInitialTaskSelect(ActivityManager.RunningTaskInfo info,
            @StagePosition int stagePosition, @NonNull ItemInfo itemInfo,
            StatsLogManager.EventEnum splitEvent) {
        mSplitSelectDataHolder.setInitialTaskSelect(info, stagePosition, itemInfo, splitEvent);
    }

    /**
     * Maps a List<ComponentKey> to List<@Nullable Task>, searching through active Tasks in
     * RecentsModel. If found, the Task will be the most recently-interacted-with instance of that
     * Task. Then runs the given callback on that List.
     * <p>
     * Used in various task-switching or splitscreen operations when we need to check if there is a
     * currently running Task of a certain type and use the most recent one.
     */
    public void findLastActiveTasksAndRunCallback(
            @Nullable List<ComponentKey> componentKeys, Consumer<List<Task>> callback) {
        mRecentTasksModel.getTasks(taskGroups -> {
            if (componentKeys == null || componentKeys.isEmpty()) {
                callback.accept(Collections.emptyList());
                return;
            }

            List<Task> lastActiveTasks = new ArrayList<>();
            // For each key we are looking for, add to lastActiveTasks with the corresponding Task
            // (or null if not found).
            for (ComponentKey key : componentKeys) {
                Task lastActiveTask = null;
                // Loop through tasks in reverse, since they are ordered with most-recent tasks last
                for (int i = taskGroups.size() - 1; i >= 0; i--) {
                    GroupTask groupTask = taskGroups.get(i);
                    Task task1 = groupTask.task1;
                    // Don't add duplicate Tasks
                    if (isInstanceOfComponent(task1, key) && !lastActiveTasks.contains(task1)) {
                        lastActiveTask = task1;
                        break;
                    }
                    Task task2 = groupTask.task2;
                    if (isInstanceOfComponent(task2, key) && !lastActiveTasks.contains(task2)) {
                        lastActiveTask = task2;
                        break;
                    }
                }

                lastActiveTasks.add(lastActiveTask);
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
    public void launchSplitTasks(@Nullable Consumer<Boolean> callback) {
        Pair<InstanceId, com.android.launcher3.logging.InstanceId> instanceIds =
                LogUtils.getShellShareableInstanceId();
        launchTasks(callback, false /* freezeTaskList */, DEFAULT_SPLIT_RATIO,
                instanceIds.first);

        mStatsLogManager.logger()
                .withItemInfo(mSplitSelectDataHolder.getItemInfo())
                .withInstanceId(instanceIds.second)
                .log(mSplitSelectDataHolder.getSplitEvent());
    }

    /**
     * A version of {@link #launchTasks(Consumer, boolean, float, InstanceId)} with no success
     * callback.
     */
    public void launchSplitTasks() {
        launchSplitTasks(null);
    }

    /**
     * To be called as soon as user selects the second task (even if animations aren't complete)
     * @param task The second task that will be launched.
     */
    public void setSecondTask(Task task) {
        mSplitSelectDataHolder.setSecondTask(task.key.id);
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     * @param intent The second intent that will be launched.
     * @param user The user of that intent.
     */
    public void setSecondTask(Intent intent, UserHandle user) {
        mSplitSelectDataHolder.setSecondTask(intent, user);
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     * @param pendingIntent The second PendingIntent that will be launched.
     */
    public void setSecondTask(PendingIntent pendingIntent) {
        mSplitSelectDataHolder.setSecondTask(pendingIntent);
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
            float splitRatio, @Nullable InstanceId shellInstanceId) {
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
        int firstUserId = launchData.getInitialUserId();
        int secondUserId = launchData.getSecondUserId();
        int initialStagePosition = launchData.getInitialStagePosition();
        Bundle optionsBundle = options1.toBundle();

        if (TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            final RemoteTransition remoteTransition = getShellRemoteTransition(firstTaskId,
                    secondTaskId, callback, "LaunchSplitPair");
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
     * Overview). This is different than {@link #launchTasks(Consumer, boolean, float, InstanceId)}
     * in that this only launches split screen that are existing tasks. This doesn't determine which
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
                    secondTaskId, callback, "LaunchExistingPair");
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

    /**
     * Requires Shell Transitions
     */
    private class RemoteSplitLaunchTransitionRunner extends IRemoteTransition.Stub {

        private final int mInitialTaskId;
        private final int mSecondTaskId;
        private final Consumer<Boolean> mSuccessCallback;

        RemoteSplitLaunchTransitionRunner(int initialTaskId, int secondTaskId,
                @Nullable Consumer<Boolean> callback) {
            mInitialTaskId = initialTaskId;
            mSecondTaskId = secondTaskId;
            mSuccessCallback = callback;
        }

        @Override
        public void startAnimation(IBinder transition, TransitionInfo info,
                SurfaceControl.Transaction t,
                IRemoteTransitionFinishedCallback finishedCallback) {
            testLogD(LAUNCH_SPLIT_PAIR, "Received split startAnimation");
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
                            resetState();
                        });
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
     * To be called whenever we exit split selection state. If
     * {@link FeatureFlags#ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE} is set, this should be the
     * central way split is getting reset, which should then go through the callbacks to reset
     * other state.
     */
    public void resetState() {
        mSplitSelectDataHolder.resetState();
        dispatchOnSplitSelectionExit();
        mRecentsAnimationRunning = false;
        mLaunchingTaskView = null;
        mAnimateCurrentTaskDismissal = false;
        mDismissingFromSplitPair = false;
        mFirstFloatingTaskView = null;
        mSplitInstructionsView = null;
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

    public int getInitialTaskId() {
        return mSplitSelectDataHolder.getInitialTaskId();
    }

    public int getSecondTaskId() {
        return mSplitSelectDataHolder.getSecondTaskId();
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
                    if (!ENABLE_SPLIT_FROM_DESKTOP_TO_WORKSPACE.get()) return false;
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
