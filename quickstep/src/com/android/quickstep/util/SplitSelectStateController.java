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
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.RemoteAnimationAdapter;
import android.view.SurfaceControl;
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
    private static final String TAG = "SplitSelectStateCtor";

    private final Context mContext;
    private final Handler mHandler;
    private StatsLogManager mStatsLogManager;
    private final SystemUiProxy mSystemUiProxy;
    private final StateManager mStateManager;
    private final DepthController mDepthController;
    private @StagePosition int mStagePosition;
    private ItemInfo mItemInfo;
    private Intent mInitialTaskIntent;
    private int mInitialTaskId = INVALID_TASK_ID;
    private int mSecondTaskId = INVALID_TASK_ID;
    private String mSecondTaskPackageName;
    private boolean mRecentsAnimationRunning;
    @Nullable
    private UserHandle mUser;
    /** If not null, this is the TaskView we want to launch from */
    @Nullable
    private GroupedTaskView mLaunchingTaskView;
    /** Represents where split is intended to be invoked from. */
    private StatsLogManager.EventEnum mSplitEvent;

    public SplitSelectStateController(Context context, Handler handler, StateManager stateManager,
            DepthController depthController, StatsLogManager statsLogManager) {
        mContext = context;
        mHandler = handler;
        mStatsLogManager = statsLogManager;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(mContext);
        mStateManager = stateManager;
        mDepthController = depthController;
    }

    /**
     * To be called after first task selected
     */
    public void setInitialTaskSelect(int taskId, @StagePosition int stagePosition,
            StatsLogManager.EventEnum splitEvent, ItemInfo itemInfo) {
        mInitialTaskId = taskId;
        setInitialData(stagePosition, splitEvent, itemInfo);
    }

    public void setInitialTaskSelect(Intent intent, @StagePosition int stagePosition,
            @NonNull ItemInfo itemInfo, StatsLogManager.EventEnum splitEvent) {
        mInitialTaskIntent = intent;
        mUser = itemInfo.user;
        mItemInfo = itemInfo;
        setInitialData(stagePosition, splitEvent, itemInfo);
    }

    private void setInitialData(@StagePosition int stagePosition,
            StatsLogManager.EventEnum splitEvent, ItemInfo itemInfo) {
        mItemInfo = itemInfo;
        mStagePosition = stagePosition;
        mSplitEvent = splitEvent;
    }

    /**
     * To be called when the actual tasks ({@link #mInitialTaskId}, {@link #mSecondTaskId}) are
     * to be launched. Call after launcher side animations are complete.
     */
    public void launchSplitTasks(Consumer<Boolean> callback) {
        final Intent fillInIntent;
        if (mInitialTaskIntent != null) {
            fillInIntent = new Intent();
            if (TextUtils.equals(mInitialTaskIntent.getComponent().getPackageName(),
                    mSecondTaskPackageName)) {
                fillInIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            }
        } else {
            fillInIntent = null;
        }

        final PendingIntent pendingIntent = mInitialTaskIntent == null ? null : (mUser != null
                ? PendingIntent.getActivityAsUser(mContext, 0, mInitialTaskIntent,
                FLAG_MUTABLE, null /* options */, mUser)
                : PendingIntent.getActivity(mContext, 0, mInitialTaskIntent, FLAG_MUTABLE));

        Pair<InstanceId, com.android.launcher3.logging.InstanceId> instanceIds =
                LogUtils.getShellShareableInstanceId();
        launchTasks(mInitialTaskId, pendingIntent, fillInIntent, mSecondTaskId, mStagePosition,
                callback, false /* freezeTaskList */, DEFAULT_SPLIT_RATIO,
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
        if (mInitialTaskIntent != null) {
            mSecondTaskPackageName = task.getTopComponent().getPackageName();
        }
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
                stagePosition, callback, freezeTaskList, splitRatio, null);
    }

    /**
     * To be called when we want to launch split pairs from Overview. Split can be initiated from
     * either Overview or home, or all apps. Either both taskIds are set, or a pending intent + a
     * fill in intent with a taskId2 are set.
     * @param taskPendingIntent is null when split is initiated from Overview
     * @param stagePosition representing location of task1
     * @param shellInstanceId loggingId to be used by shell, will be non-null for actions that create
     *                   a split instance, null for cases that bring existing instaces to the
     *                   foreground (quickswitch, launching previous pairs from overview)
     */
    public void launchTasks(int taskId1, @Nullable PendingIntent taskPendingIntent,
            @Nullable Intent fillInIntent, int taskId2, @StagePosition int stagePosition,
            Consumer<Boolean> callback, boolean freezeTaskList, float splitRatio,
            @Nullable InstanceId shellInstanceId) {
        TestLogging.recordEvent(
                TestProtocol.SEQUENCE_MAIN, "launchSplitTasks");
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
                            ActivityThread.currentActivityThread().getApplicationThread()),
                    shellInstanceId);
            // TODO(b/237635859): handle intent/shortcut + task with shell transition
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
                        splitRatio, adapter, shellInstanceId);
            } else {
                final ShortcutInfo shortcutInfo = getShortcutInfo(mInitialTaskIntent,
                        taskPendingIntent.getCreatorUserHandle());
                if (shortcutInfo != null) {
                    mSystemUiProxy.startShortcutAndTaskWithLegacyTransition(shortcutInfo, taskId2,
                            mainOpts.toBundle(), null /* sideOptions */, stagePosition, splitRatio,
                            adapter, shellInstanceId);
                } else {
                    mSystemUiProxy.startIntentAndTaskWithLegacyTransition(taskPendingIntent,
                            fillInIntent, taskId2, mainOpts.toBundle(), null /* sideOptions */,
                            stagePosition, splitRatio, adapter, shellInstanceId);
                }
            }
        }
    }

    public @StagePosition int getActiveSplitStagePosition() {
        return mStagePosition;
    }

    public StatsLogManager.EventEnum getSplitEvent() {
        return mSplitEvent;
    }

    public void setRecentsAnimationRunning(boolean running) {
        this.mRecentsAnimationRunning = running;
    }

    @Nullable
    private ShortcutInfo getShortcutInfo(Intent intent, UserHandle userHandle) {
        if (intent == null || intent.getPackage() == null) {
            return null;
        }

        final String shortcutId = intent.getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID);
        if (shortcutId == null) {
            return null;
        }

        try {
            final Context context = mContext.createPackageContextAsUser(
                    intent.getPackage(), 0 /* flags */, userHandle);
            return new ShortcutInfo.Builder(context, shortcutId).build();
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to create a ShortcutInfo for " + intent.getPackage());
        }

        return null;
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
            TaskViewUtils.composeRecentsSplitLaunchAnimator(mLaunchingTaskView, mStateManager,
                    mDepthController, mInitialTaskId, mInitialTaskPendingIntent, mSecondTaskId,
                    info, t, () -> {
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
        mItemInfo = null;
        mSplitEvent = null;
    }

    /**
     * @return {@code true} if first task has been selected and waiting for the second task to be
     *         chosen
     */
    public boolean isSplitSelectActive() {
        return isInitialTaskIntentSet() && mSecondTaskId == INVALID_TASK_ID;
    }

    /**
     * @return {@code true} if the first and second task have been chosen and split is waiting to
     *          be launched
     */
    public boolean isBothSplitAppsConfirmed() {
        return isInitialTaskIntentSet() && mSecondTaskId != INVALID_TASK_ID;
    }

    private boolean isInitialTaskIntentSet() {
        return (mInitialTaskId != INVALID_TASK_ID || mInitialTaskIntent != null);
    }

    public int getInitialTaskId() {
        return mInitialTaskId;
    }
}
