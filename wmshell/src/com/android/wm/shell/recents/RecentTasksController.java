/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.recents;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.content.pm.PackageManager.FEATURE_PC;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.wm.shell.Flags.enableShellTopTaskTracking;
import static com.android.wm.shell.desktopmode.DesktopWallpaperActivity.isWallpaperTask;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_TASK_OBSERVER;

import android.Manifest;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.app.IApplicationThread;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Slog;
import android.util.SparseIntArray;
import android.window.DesktopModeFlags;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SingleInstanceRemoteListener;
import com.android.wm.shell.common.TaskStackListenerCallback;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.desktopmode.DesktopRepository;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.GroupedTaskInfo;
import com.android.wm.shell.shared.annotations.ExternalThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.split.SplitBounds;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.sysui.UserChangeListener;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages the recent task list from the system, caching it as necessary.
 */
public class RecentTasksController implements TaskStackListenerCallback,
        RemoteCallable<RecentTasksController>, DesktopRepository.ActiveTasksListener,
        TaskStackTransitionObserver.TaskStackTransitionObserverListener, UserChangeListener,
        DesktopRepository.DeskChangeListener {
    private static final String TAG = RecentTasksController.class.getSimpleName();

    // When the multiple desktops feature is disabled, all freeform tasks are lumped together into
    // a single `GroupedTaskInfo` whose type is `TYPE_DESK`, and its `mDeskId` doesn't matter, so
    // we pick the below arbitrary value.
    private static final int INVALID_DESK_ID = -1;

    private final Context mContext;
    private final ShellController mShellController;
    private final ShellCommandHandler mShellCommandHandler;
    private final Optional<DesktopUserRepositories> mDesktopUserRepositories;

    private final ShellExecutor mMainExecutor;
    private final TaskStackListenerImpl mTaskStackListener;
    private final RecentTasksImpl mImpl = new RecentTasksImpl();
    private final ActivityTaskManager mActivityTaskManager;
    private final TaskStackTransitionObserver mTaskStackTransitionObserver;
    private final RecentsShellCommandHandler mRecentsShellCommandHandler;
    private final DesktopState mDesktopState;
    private RecentsTransitionHandler mTransitionHandler = null;
    private IRecentTasksListener mListener;
    private final boolean mPcFeatureEnabled;

    // Mapping of split task ids, mappings are symmetrical (ie. if t1 is the taskid of a task in a
    // pair, then mSplitTasks[t1] = t2, and mSplitTasks[t2] = t1)
    private final SparseIntArray mSplitTasks = new SparseIntArray();

    private int mUserId;
    /**
     * Maps taskId to {@link SplitBounds} for both taskIDs.
     * Meaning there will be two taskId integers mapping to the same object.
     * If there's any ordering to the pairing than we can probably just get away with only one
     * taskID mapping to it, leaving both for consistency with {@link #mSplitTasks} for now.
     */
    private final Map<Integer, SplitBounds> mTaskSplitBoundsMap = new HashMap<>();

    /**
     * Cached list of the visible tasks, sorted from top most to bottom most.
     */
    private final List<RunningTaskInfo> mVisibleTasks = new ArrayList<>();
    private final Map<Integer, TaskInfo> mVisibleTasksMap = new HashMap<>();

    // Temporary vars used in `generateList()`
    private final Map<Integer, TaskInfo> mTmpRemaining = new HashMap<>();
    private final Map<Integer, Desk> mTmpDesks = new HashMap<>();

    /**
     * Creates {@link RecentTasksController}, returns {@code null} if the feature is not
     * supported.
     */
    @Nullable
    public static RecentTasksController create(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            ShellCommandHandler shellCommandHandler,
            TaskStackListenerImpl taskStackListener,
            ActivityTaskManager activityTaskManager,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            TaskStackTransitionObserver taskStackTransitionObserver,
            @ShellMainThread ShellExecutor mainExecutor,
            DesktopState desktopState
    ) {
        if (!context.getResources().getBoolean(com.android.internal.R.bool.config_hasRecents)) {
            return null;
        }
        return new RecentTasksController(context, shellInit, shellController, shellCommandHandler,
                taskStackListener, activityTaskManager, desktopUserRepositories,
                taskStackTransitionObserver, mainExecutor, desktopState);
    }

    RecentTasksController(Context context,
            ShellInit shellInit,
            ShellController shellController,
            ShellCommandHandler shellCommandHandler,
            TaskStackListenerImpl taskStackListener,
            ActivityTaskManager activityTaskManager,
            Optional<DesktopUserRepositories> desktopUserRepositories,
            TaskStackTransitionObserver taskStackTransitionObserver,
            ShellExecutor mainExecutor,
            DesktopState desktopState) {
        mContext = context;
        mShellController = shellController;
        mShellCommandHandler = shellCommandHandler;
        mActivityTaskManager = activityTaskManager;
        mPcFeatureEnabled = mContext.getPackageManager().hasSystemFeature(FEATURE_PC);
        mTaskStackListener = taskStackListener;
        mDesktopUserRepositories = desktopUserRepositories;
        mTaskStackTransitionObserver = taskStackTransitionObserver;
        mMainExecutor = mainExecutor;
        mRecentsShellCommandHandler = new RecentsShellCommandHandler(this);
        mDesktopState = desktopState;
        shellInit.addInitCallback(this::onInit, this);
    }

    public RecentTasks asRecentTasks() {
        return mImpl;
    }

    private ExternalInterfaceBinder createExternalInterface() {
        return new IRecentTasksImpl(this);
    }

    @RequiresPermission(Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE)
    void onInit() {
        mShellController.addExternalInterface(IRecentTasks.DESCRIPTOR,
                this::createExternalInterface, this);
        mShellCommandHandler.addDumpCallback(this::dump, this);
        mShellCommandHandler.addCommandCallback("recents", mRecentsShellCommandHandler, this);
        mUserId = ActivityManager.getCurrentUser();
        mDesktopUserRepositories.ifPresent(
                desktopUserRepositories -> {
                    desktopUserRepositories.getCurrent().addActiveTaskListener(this);
                    if (mDesktopState.enableMultipleDesktops()) {
                        desktopUserRepositories.getCurrent().addDeskChangeListener(this,
                                mMainExecutor);
                    }
                });
        mTaskStackListener.addListener(this);
        mTaskStackTransitionObserver.addTaskStackTransitionObserverListener(this,
                mMainExecutor);
        mContext.getSystemService(KeyguardManager.class).addKeyguardLockedStateListener(
                mMainExecutor, isKeyguardLocked -> notifyRecentTasksChanged());
    }

    void setTransitionHandler(RecentsTransitionHandler handler) {
        mTransitionHandler = handler;
    }

    /**
     * Adds a split pair. This call does not validate the taskIds, only that they are not the same.
     */
    public boolean addSplitPair(int taskId1, int taskId2, SplitBounds splitBounds) {
        if (taskId1 == taskId2) {
            return false;
        }
        if (mSplitTasks.get(taskId1, INVALID_TASK_ID) == taskId2
                && mTaskSplitBoundsMap.get(taskId1).equals(splitBounds)) {
            // If the two tasks are already paired and the bounds are the same, then skip updating
            return false;
        }
        // Remove any previous pairs
        removeSplitPair(taskId1);
        removeSplitPair(taskId2);
        mTaskSplitBoundsMap.remove(taskId1);
        mTaskSplitBoundsMap.remove(taskId2);

        mSplitTasks.put(taskId1, taskId2);
        mSplitTasks.put(taskId2, taskId1);
        mTaskSplitBoundsMap.put(taskId1, splitBounds);
        mTaskSplitBoundsMap.put(taskId2, splitBounds);
        notifyRecentTasksChanged();
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Add split pair: %d, %d, %s",
                taskId1, taskId2, splitBounds);
        return true;
    }

    /**
     * Removes a split pair.
     */
    public void removeSplitPair(int taskId) {
        int pairedTaskId = mSplitTasks.get(taskId, INVALID_TASK_ID);
        if (pairedTaskId != INVALID_TASK_ID) {
            mSplitTasks.delete(taskId);
            mSplitTasks.delete(pairedTaskId);
            mTaskSplitBoundsMap.remove(taskId);
            mTaskSplitBoundsMap.remove(pairedTaskId);
            notifyRecentTasksChanged();
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Remove split pair: %d, %d",
                    taskId, pairedTaskId);
        }
    }

    @Nullable
    public SplitBounds getSplitBoundsForTaskId(int taskId) {
        if (taskId == INVALID_TASK_ID) {
            return null;
        }

        // We could do extra verification of requiring both taskIds of a pair and verifying that
        // the same split bounds object is returned... but meh. Seems unnecessary.
        SplitBounds splitBounds = mTaskSplitBoundsMap.get(taskId);
        if (splitBounds != null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                    "getSplitBoundsForTaskId: taskId=%d splitBoundsTasks=[%d, %d]", taskId,
                    splitBounds.leftTopTaskId, splitBounds.rightBottomTaskId);
        } else {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                    "getSplitBoundsForTaskId: expected split bounds for taskId=%d but not found",
                    taskId);
        }
        return splitBounds;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mMainExecutor;
    }

    @Override
    public void onTaskStackChanged() {
        if (!enableShellTopTaskTracking()) {
            // Skip notifying recent tasks changed whenever task stack changes
            notifyRecentTasksChanged();
        }
    }

    @Override
    public void onRecentTaskListUpdated() {
        // In some cases immediately after booting, the tasks in the system recent task list may be
        // loaded, but not in the active task hierarchy in the system.  These tasks are displayed in
        // overview, but removing them don't result in a onTaskStackChanged() nor a onTaskRemoved()
        // callback (those are for changes to the active tasks), but the task list is still updated,
        // so we should also invalidate the change id to ensure we load a new list instead of
        // reusing a stale list.
        notifyRecentTasksChanged();
    }

    /**
     * This method only gets notified when a task is removed from recents as a result of another
     * task being added to recent tasks.
     */
    @Override
    public void onRecentTaskRemovedForAddTask(int taskId) {
        mDesktopUserRepositories.ifPresent(
                desktopUserRepositories -> desktopUserRepositories.getCurrent().removeTask(taskId));
    }

    public void onTaskAdded(RunningTaskInfo taskInfo) {
        notifyRunningTaskAppeared(taskInfo);
        if (!enableShellTopTaskTracking()) {
            notifyRecentTasksChanged();
        }
    }

    public void onTaskRemoved(RunningTaskInfo taskInfo) {
        // Remove any split pairs associated with this task
        removeSplitPair(taskInfo.taskId);
        notifyRunningTaskVanished(taskInfo);
        if (!enableShellTopTaskTracking()) {
            // Only notify recent tasks changed if we aren't already notifying the visible tasks
            notifyRecentTasksChanged();
        }
    }

    /**
     * Notify listeners that the running infos related to recent tasks was updated.
     *
     * This currently includes windowing mode and visibility.
     */
    public void onTaskRunningInfoChanged(RunningTaskInfo taskInfo) {
        notifyRecentTasksChanged();
        notifyRunningTaskChanged(taskInfo);
    }

    @Override
    public void onActiveTasksChanged(int displayId) {
        notifyRecentTasksChanged();
    }

    @Override
    public void onTaskMovedToFrontThroughTransition(RunningTaskInfo runningTaskInfo) {
        notifyTaskMovedToFront(runningTaskInfo);
    }

    @Override
    public void onTaskChangedThroughTransition(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        notifyTaskInfoChanged(taskInfo);
    }

    @Override
    public void onVisibleTasksChanged(@NonNull List<? extends RunningTaskInfo> visibleTasks) {
        mVisibleTasks.clear();
        mVisibleTasks.addAll(visibleTasks);
        mVisibleTasksMap.clear();
        mVisibleTasksMap.putAll(mVisibleTasks.stream().collect(
                Collectors.toMap(TaskInfo::getTaskId, task -> task)));
        // Notify with all the info and not just the running task info
        notifyVisibleTasksChanged(mVisibleTasks);
    }

    @VisibleForTesting
    void notifyRecentTasksChanged() {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENT_TASKS, "Notify recent tasks changed");
        if (mListener == null) {
            return;
        }
        try {
            mListener.onRecentTasksChanged();
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed call notifyRecentTasksChanged", e);
        }
    }

    /**
     * Notify the running task listener that a task appeared on desktop environment.
     */
    private void notifyRunningTaskAppeared(RunningTaskInfo taskInfo) {
        if (mListener == null
                || !shouldEnableRunningTasksForDesktopMode()
                || taskInfo.realActivity == null
                || excludeTaskFromGeneratedList(taskInfo)) {
            return;
        }
        try {
            mListener.onRunningTaskAppeared(taskInfo);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed call onRunningTaskAppeared", e);
        }
    }

    /**
     * Notify the running task listener that a task was changed on desktop environment.
     */
    private void notifyRunningTaskChanged(RunningTaskInfo taskInfo) {
        if (mListener == null
                || !shouldEnableRunningTasksForDesktopMode()
                || taskInfo.realActivity == null
                || excludeTaskFromGeneratedList(taskInfo)) {
            return;
        }
        try {
            mListener.onRunningTaskChanged(taskInfo);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed call onRunningTaskChanged", e);
        }
    }

    /**
     * Notify the running task listener that a task was removed on desktop environment.
     */
    private void notifyRunningTaskVanished(RunningTaskInfo taskInfo) {
        if (mListener == null
                || !shouldEnableRunningTasksForDesktopMode()
                || taskInfo.realActivity == null
                || excludeTaskFromGeneratedList(taskInfo)) {
            return;
        }
        try {
            mListener.onRunningTaskVanished(taskInfo);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed call onRunningTaskVanished", e);
        }
    }

    /**
     * Notify the recents task listener that a task moved to front via a transition.
     */
    private void notifyTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
        if (mListener == null
                || !DesktopModeFlags.ENABLE_TASK_STACK_OBSERVER_IN_SHELL.isTrue()
                || taskInfo.realActivity == null
                || enableShellTopTaskTracking()
                || excludeTaskFromGeneratedList(taskInfo)) {
            return;
        }
        try {
            mListener.onTaskMovedToFront(GroupedTaskInfo.forFullscreenTasks(taskInfo));
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed call onTaskMovedToFront", e);
        }
    }

    /**
     * Notify the recents task listener that a task changed via a transition.
     */
    private void notifyTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (mListener == null
                || !DesktopModeFlags.ENABLE_TASK_STACK_OBSERVER_IN_SHELL.isTrue()
                || taskInfo.realActivity == null
                || enableShellTopTaskTracking()
                || excludeTaskFromGeneratedList(taskInfo)) {
            return;
        }
        try {
            mListener.onTaskInfoChanged(taskInfo);
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed call onTaskInfoChanged", e);
        }
    }

    /**
     * Notifies that the test of visible tasks have changed.
     */
    private void notifyVisibleTasksChanged(@NonNull List<? extends RunningTaskInfo> visibleTasks) {
        if (mListener == null
                || !DesktopModeFlags.ENABLE_TASK_STACK_OBSERVER_IN_SHELL.isTrue()
                || !enableShellTopTaskTracking()) {
            return;
        }
        try {
            // Compute the visible recent tasks in order, and move the task to the top
            mListener.onVisibleTasksChanged(generateList(visibleTasks, "visibleTasksChanged")
                    .toArray(new GroupedTaskInfo[0]));
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed call onVisibleTasksChanged", e);
        }
    }

    private boolean shouldEnableRunningTasksForDesktopMode() {
        return mPcFeatureEnabled
                || (mDesktopState.canEnterDesktopMode()
                && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS.isTrue());
    }

    @VisibleForTesting
    void registerRecentTasksListener(IRecentTasksListener listener) {
        mListener = listener;
        if (enableShellTopTaskTracking()) {
            ProtoLog.v(WM_SHELL_TASK_OBSERVER, "registerRecentTasksListener");
            // Post a notification for the current set of visible tasks
            mMainExecutor.executeDelayed(() -> notifyVisibleTasksChanged(mVisibleTasks), 0);
        }
    }

    @VisibleForTesting
    void unregisterRecentTasksListener() {
        mListener = null;
    }

    @VisibleForTesting
    boolean hasRecentTasksListener() {
        return mListener != null;
    }

    @VisibleForTesting
    ArrayList<GroupedTaskInfo> getRecentTasks(int maxNum, int flags, int userId) {
        // Note: the returned task list is ordered from the most-recent to least-recent order
        return generateList(mActivityTaskManager.getRecentTasks(maxNum, flags, userId),
                "getRecentTasks");
    }

    /**
     * Returns whether the given task should be excluded from the generated list.
     */
    private boolean excludeTaskFromGeneratedList(TaskInfo taskInfo) {
        if (taskInfo.getWindowingMode() == WINDOWING_MODE_PINNED) {
            // We don't current send pinned tasks as a part of recent or running tasks
            return true;
        }
        if (isWallpaperTask(taskInfo)) {
            // Don't add the fullscreen wallpaper task as an entry in grouped tasks
            return true;
        }
        return false;
    }

    /**
     * Represents a desk whose ID is `mDeskId` inside the display with `mDisplayId` and contains
     * the tasks in `mDeskTasks`. Some of these tasks are minimized and their IDs are contained
     * in the `mMinimizedDeskTasks` set.
     */
    private static class Desk {
        final int mDeskId;
        final int mDisplayId;
        boolean mHasVisibleTasks = false;
        final ArrayList<TaskInfo> mDeskTasks = new ArrayList<>();
        final Set<Integer> mMinimizedDeskTasks = new HashSet<>();

        Desk(int deskId, int displayId) {
            mDeskId = deskId;
            mDisplayId = displayId;
        }

        void addTask(TaskInfo taskInfo, boolean isMinimized, boolean isVisible) {
            mDeskTasks.add(taskInfo);
            if (isMinimized) {
                mMinimizedDeskTasks.add(taskInfo.taskId);
            }
            mHasVisibleTasks |= isVisible;
        }

        boolean hasTasks() {
            return !mDeskTasks.isEmpty();
        }

        GroupedTaskInfo createDeskTaskInfo() {
            return GroupedTaskInfo.forDeskTasks(mDeskId, mDisplayId, mDeskTasks,
                    mMinimizedDeskTasks);
        }
    }

    /**
     * Clears the `mTmpDesks` map, and re-initializes it with the current state of desks from all
     * displays, without adding any desk tasks. This is a preparation step so that tasks can be
     * added to these desks in `generateList()`.
     *
     * This is needed since with the multiple-desktops flags, we want to include desk even if
     * they're empty (i.e. have no tasks).
     *
     * @param multipleDesktopsEnabled whether the multiple desktops feature is enabled.
     */
    private void initializeDesksMap(boolean multipleDesktopsEnabled) {
        mTmpDesks.clear();

        if (mDesktopState.canEnterDesktopMode()
                && mDesktopUserRepositories.isPresent()) {
            if (multipleDesktopsEnabled) {
                for (var deskId : mDesktopUserRepositories.get().getCurrent().getAllDeskIds()) {
                    getOrCreateDesk(deskId);
                }
            } else {
                // When the multiple desks feature is disabled, we lump all freeform windows in a
                // single `GroupedTaskInfo` regardless of their display. The `deskId` in this case
                // doesn't matter and can be any arbitrary value.
                getOrCreateDesk(/* deskId = */ INVALID_DESK_ID);
            }
        }
    }

    /**
     * Returns the `Desk` whose ID is `deskId` from the `mTmpDesks` map if it exists, or it creates
     * one and adds it to the map and then returns it.
     */
    private Desk getOrCreateDesk(int deskId) {
        var desk = mTmpDesks.get(deskId);
        if (desk == null) {
            final int displayId;
            if (deskId == INVALID_DESK_ID) {
                displayId = INVALID_DISPLAY;
            } else {
                displayId = mDesktopUserRepositories.get().getCurrent().getDisplayForDesk(deskId);
            }
            desk = new Desk(deskId, displayId);
            mTmpDesks.put(deskId, desk);
        }
        return desk;
    }

    /**
     * Generates a list of GroupedTaskInfos for the given raw list of tasks (either recents or
     * running tasks).
     *
     * The general flow is:
     *  - Collect the desktop tasks
     *  - Collect the visible tasks (in order), including the desktop tasks if visible
     *  - Construct the final list with the visible tasks, followed by the subsequent tasks
     *      - if enableShellTopTaskTracking() is enabled, the visible tasks will be grouped into
     *        a single mixed task
     *      - if the desktop tasks are not visible, they will be appended to the end of the list
     *
     * TODO(346588978): Generate list in per-display order
     *
     * @param tasks The list of tasks ordered from most recent to least recent
     */
    @VisibleForTesting
    <T extends TaskInfo> ArrayList<GroupedTaskInfo> generateList(@NonNull List<T> tasks,
            String reason) {
        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }

        if (enableShellTopTaskTracking()) {
            ProtoLog.v(WM_SHELL_TASK_OBSERVER, "RecentTasksController.generateList(%s)", reason);
        }

        final boolean multipleDesktopsEnabled = mDesktopState.enableMultipleDesktops();
        initializeDesksMap(multipleDesktopsEnabled);

        // When the multiple desktops feature is enabled, we include all desks even if they're
        // empty.
        final boolean shouldIncludeEmptyDesktops = multipleDesktopsEnabled;

        // Make a mapping of task id -> task info for the remaining tasks to be processed, this
        // mapping is used to keep track of split tasks that may exist later in the task list that
        // should be ignored because they've already been grouped
        mTmpRemaining.clear();
        mTmpRemaining.putAll(tasks.stream().collect(
                Collectors.toMap(TaskInfo::getTaskId, task -> task)));

        // The final grouped tasks
        ArrayList<GroupedTaskInfo> groupedTasks = new ArrayList<>(tasks.size());
        ArrayList<GroupedTaskInfo> visibleGroupedTasks = new ArrayList<>();

        // Phase 1: Extract the desktops and visible fullscreen/split tasks.
        for (int i = 0; i < tasks.size(); i++) {
            final TaskInfo taskInfo = tasks.get(i);
            final int taskId = taskInfo.taskId;

            if (!mTmpRemaining.containsKey(taskInfo.taskId)) {
                // Skip if we've already processed it
                continue;
            }

            if (excludeTaskFromGeneratedList(taskInfo)) {
                // Skip and update the list if we are excluding this task
                mTmpRemaining.remove(taskId);
                continue;
            }

            // Desktop tasks
            if (mDesktopState.canEnterDesktopMode()
                    && mDesktopUserRepositories.isPresent()
                    && mDesktopUserRepositories.get().getCurrent().isActiveTask(taskId)) {
                // If task has their app bounds set to null which happens after reboot, set the
                // app bounds to persisted lastFullscreenBounds. Also set the position in parent
                // to the top left of the bounds.
                if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue()
                        && taskInfo.configuration.windowConfiguration.getAppBounds() == null
                        && taskInfo.lastNonFullscreenBounds != null) {
                    taskInfo.configuration.windowConfiguration.setAppBounds(
                            taskInfo.lastNonFullscreenBounds);
                    taskInfo.positionInParent = new Point(taskInfo.lastNonFullscreenBounds.left,
                            taskInfo.lastNonFullscreenBounds.top);
                }
                // Lump all freeform tasks together as if they were all in a single desk whose ID is
                // `INVALID_DESK_ID` when the multiple desktops feature is disabled.
                final int deskId = multipleDesktopsEnabled
                        ? mDesktopUserRepositories.get().getCurrent().getDeskIdForTask(taskId)
                        : INVALID_DESK_ID;
                final Desk desk = getOrCreateDesk(deskId);
                desk.addTask(taskInfo,
                        mDesktopUserRepositories.get().getCurrent().isMinimizedTask(taskId),
                        mVisibleTasksMap.containsKey(taskId));
                mTmpRemaining.remove(taskId);
                continue;
            }

            if (enableShellTopTaskTracking()) {
                // Visible tasks
                if (mVisibleTasksMap.containsKey(taskId)) {
                    // Split tasks
                    if (extractAndAddSplitGroupedTask(taskInfo, mTmpRemaining,
                            visibleGroupedTasks)) {
                        continue;
                    }

                    // Fullscreen tasks
                    visibleGroupedTasks.add(GroupedTaskInfo.forFullscreenTasks(taskInfo));
                    mTmpRemaining.remove(taskId);
                }
            } else {
                // Split tasks
                if (extractAndAddSplitGroupedTask(taskInfo, mTmpRemaining, groupedTasks)) {
                    continue;
                }

                // Fullscreen tasks
                groupedTasks.add(GroupedTaskInfo.forFullscreenTasks(taskInfo));
            }
        }

        if (enableShellTopTaskTracking()) {
            // Phase 2: If there were desktop tasks and they are visible, add them to the visible
            //          list as well (the actual order doesn't matter for Overview)
            for (var desk : mTmpDesks.values()) {
                if (desk.mHasVisibleTasks) {
                    visibleGroupedTasks.add(desk.createDeskTaskInfo());
                }
            }

            if (!visibleGroupedTasks.isEmpty()) {
                // Phase 3: Combine the visible tasks into a single mixed grouped task, only if
                //          there are > 1 tasks to group, and add them to the final list
                if (visibleGroupedTasks.size() > 1) {
                    groupedTasks.add(GroupedTaskInfo.forMixed(visibleGroupedTasks));
                } else {
                    groupedTasks.addAll(visibleGroupedTasks);
                }
            }
            dumpGroupedTasks(groupedTasks, "Phase 3");

            // Phase 4: For the remaining non-visible split and fullscreen tasks, add grouped tasks
            //          in order to the final list
            for (int i = 0; i < tasks.size(); i++) {
                final TaskInfo taskInfo = tasks.get(i);
                if (!mTmpRemaining.containsKey(taskInfo.taskId)) {
                    // Skip if we've already processed it
                    continue;
                }

                // Split tasks
                if (extractAndAddSplitGroupedTask(taskInfo, mTmpRemaining, groupedTasks)) {
                    continue;
                }

                // Fullscreen tasks
                groupedTasks.add(GroupedTaskInfo.forFullscreenTasks(taskInfo));
            }
            dumpGroupedTasks(groupedTasks, "Phase 4");

            // Phase 5: If there were desktop tasks and they are not visible (ie. weren't added
            //          above), add them to the end of the final list (the actual order doesn't
            //          matter for Overview)
            for (var desk : mTmpDesks.values()) {
                if (!desk.mHasVisibleTasks && (desk.hasTasks() || shouldIncludeEmptyDesktops)) {
                    groupedTasks.add(desk.createDeskTaskInfo());
                }
            }
            dumpGroupedTasks(groupedTasks, "Phase 5");
        } else {
            // Add the desktop tasks at the end of the list
            for (var desk : mTmpDesks.values()) {
                if (desk.hasTasks() || shouldIncludeEmptyDesktops) {
                    groupedTasks.add(desk.createDeskTaskInfo());
                }
            }
        }

        return groupedTasks;
    }

    /**
     * Only to be called from `generateList()`. If the given {@param taskInfo} has a paired task,
     * then a split grouped task with the pair is added to {@param tasksOut}.
     *
     * @return whether a split task was extracted and added to the given list
     */
    private boolean extractAndAddSplitGroupedTask(@NonNull TaskInfo taskInfo,
            @NonNull Map<Integer, TaskInfo> remainingTasks,
            @NonNull ArrayList<GroupedTaskInfo> tasksOut) {
        final int pairedTaskId = mSplitTasks.get(taskInfo.taskId, INVALID_TASK_ID);
        if (pairedTaskId == INVALID_TASK_ID || !remainingTasks.containsKey(pairedTaskId)) {
            return false;
        }

        // Add both this task and its pair to the list, and mark the paired task to be
        // skipped when it is encountered in the list
        final TaskInfo pairedTaskInfo = remainingTasks.get(pairedTaskId);
        remainingTasks.remove(taskInfo.taskId);
        remainingTasks.remove(pairedTaskId);
        tasksOut.add(GroupedTaskInfo.forSplitTasks(taskInfo, pairedTaskInfo,
                mTaskSplitBoundsMap.get(pairedTaskId)));
        return true;
    }

    /** Dumps the set of tasks to protolog */
    private void dumpGroupedTasks(List<GroupedTaskInfo> groupedTasks, String reason) {
        if (!WM_SHELL_TASK_OBSERVER.isEnabled()) {
            return;
        }
        ProtoLog.v(WM_SHELL_TASK_OBSERVER, "    Tasks (%s):", reason);
        for (GroupedTaskInfo task : groupedTasks) {
            ProtoLog.v(WM_SHELL_TASK_OBSERVER, "        %s", task);
        }
    }

    /**
     * Returns the top running leaf task ignoring {@param ignoreTaskToken} if it is specified.
     * NOTE: This path currently makes assumptions that ignoreTaskToken is for the top task.
     */
    @Nullable
    public RunningTaskInfo getTopRunningTask(
            @Nullable WindowContainerToken ignoreTaskToken) {
        final List<RunningTaskInfo> tasks = enableShellTopTaskTracking()
                ? mVisibleTasks
                : mActivityTaskManager.getTasks(2, false /* filterOnlyVisibleRecents */);
        for (int i = 0; i < tasks.size(); i++) {
            final RunningTaskInfo task = tasks.get(i);
            if (task.token.equals(ignoreTaskToken)) {
                continue;
            }
            return task;
        }
        return null;
    }

    /**
     * Find the background task that match the given component.  Ignores tasks match
     * {@param ignoreTaskToken} if it is non-null.
     */
    @Nullable
    public RecentTaskInfo findTaskInBackground(ComponentName componentName,
            int userId, @Nullable WindowContainerToken ignoreTaskToken) {
        if (componentName == null) {
            return null;
        }
        List<RecentTaskInfo> tasks = mActivityTaskManager.getRecentTasks(
                Integer.MAX_VALUE, ActivityManager.RECENT_IGNORE_UNAVAILABLE,
                ActivityManager.getCurrentUser());
        for (int i = 0; i < tasks.size(); i++) {
            final RecentTaskInfo task = tasks.get(i);
            if (task.isVisible) {
                continue;
            }
            if (task.token.equals(ignoreTaskToken)) {
                continue;
            }
            if (componentName.equals(task.baseIntent.getComponent()) && userId == task.userId) {
                return task;
            }
        }
        return null;
    }

    /**
     * Find the background task (in the recent tasks list) that matches the given taskId.
     */
    @Nullable
    public RecentTaskInfo findTaskInBackground(int taskId) {
        List<RecentTaskInfo> tasks = mActivityTaskManager.getRecentTasks(
                Integer.MAX_VALUE, ActivityManager.RECENT_IGNORE_UNAVAILABLE,
                ActivityManager.getCurrentUser());
        for (int i = 0; i < tasks.size(); i++) {
            final RecentTaskInfo task = tasks.get(i);
            if (task.isVisible) {
                continue;
            }
            if (taskId == task.taskId) {
                return task;
            }
        }
        return null;
    }

    /**
     * Remove the background task that match the given taskId. This will remove the task regardless
     * of whether it's active or recent.
     */
    public boolean removeBackgroundTask(int taskId) {
        return mActivityTaskManager.removeTask(taskId);
    }

    /** Removes all recent tasks that are visible. */
    public void removeAllVisibleRecentTasks() throws RemoteException {
        ActivityTaskManager.getService().removeAllVisibleRecentTasks();
    }

    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(prefix + " mListener=" + mListener);
        pw.println(prefix + "Tasks:");
        ArrayList<GroupedTaskInfo> recentTasks = getRecentTasks(Integer.MAX_VALUE,
                ActivityManager.RECENT_IGNORE_UNAVAILABLE, ActivityManager.getCurrentUser());
        for (int i = 0; i < recentTasks.size(); i++) {
            pw.println(innerPrefix + recentTasks.get(i));
        }
    }

    /**
     * The interface for calls from outside the Shell, within the host process.
     */
    @ExternalThread
    private class RecentTasksImpl implements RecentTasks {
        @Override
        public void getRecentTasks(int maxNum, int flags, int userId, Executor executor,
                Consumer<List<GroupedTaskInfo>> callback) {
            mMainExecutor.execute(() -> {
                List<GroupedTaskInfo> tasks =
                        RecentTasksController.this.getRecentTasks(maxNum, flags, userId);
                executor.execute(() -> callback.accept(tasks));
            });
        }

        @Override
        public void addAnimationStateListener(Executor executor, Consumer<Boolean> listener) {
            mMainExecutor.execute(() -> {
                if (mTransitionHandler == null) {
                    return;
                }
                mTransitionHandler.addTransitionStateListener(new RecentsTransitionStateListener() {
                    @Override
                    public void onTransitionStateChanged(@RecentsTransitionState int state) {
                        executor.execute(() -> {
                            listener.accept(RecentsTransitionStateListener.isAnimating(state));
                        });
                    }
                });
            });
        }

        @Override
        public void setTransitionBackgroundColor(@Nullable Color color) {
            mMainExecutor.execute(() -> {
                if (mTransitionHandler == null) {
                    return;
                }
                mTransitionHandler.setTransitionBackgroundColor(color);
            });
        }
    }

    @Override
    public void onUserChanged(int newUserId, @NonNull Context userContext) {
        if (mDesktopUserRepositories.isEmpty()) return;

        DesktopRepository previousUserRepository =
                mDesktopUserRepositories.get().getProfile(mUserId);
        mUserId = newUserId;
        DesktopRepository currentUserRepository =
                mDesktopUserRepositories.get().getProfile(newUserId);

        // No-op if both profile ids map to the same user.
        if (previousUserRepository.getUserId() == currentUserRepository.getUserId()) return;
        previousUserRepository.removeActiveTasksListener(this);
        currentUserRepository.addActiveTaskListener(this);
    }

    @Override
    public void onDeskAdded(int displayId, int deskId) {
        notifyRecentTasksChanged();
    }

    @Override
    public void onDeskRemoved(int displayId, int deskId) {
        notifyRecentTasksChanged();
    }

    @Override
    public void onActiveDeskChanged(int displayId, int newActiveDeskId, int oldActiveDeskId) {
        // No-op for now, as only desk additions/removals affect the recent tasks list directly.
    }

    @Override
    public void onCanCreateDesksChanged(boolean canCreateDesks) {
        // No-op for now.
    }

    /**
     * The interface for calls from outside the host process.
     */
    @BinderThread
    private static class IRecentTasksImpl extends IRecentTasks.Stub
            implements ExternalInterfaceBinder {
        private RecentTasksController mController;
        private final SingleInstanceRemoteListener<RecentTasksController,
                IRecentTasksListener> mListener;
        private final IRecentTasksListener mRecentTasksListener = new IRecentTasksListener.Stub() {
            @Override
            public void onRecentTasksChanged() throws RemoteException {
                mListener.call(l -> l.onRecentTasksChanged());
            }

            @Override
            public void onRunningTaskAppeared(RunningTaskInfo taskInfo) {
                mListener.call(l -> l.onRunningTaskAppeared(taskInfo));
            }

            @Override
            public void onRunningTaskVanished(RunningTaskInfo taskInfo) {
                mListener.call(l -> l.onRunningTaskVanished(taskInfo));
            }

            @Override
            public void onRunningTaskChanged(RunningTaskInfo taskInfo) {
                mListener.call(l -> l.onRunningTaskChanged(taskInfo));
            }

            @Override
            public void onTaskMovedToFront(GroupedTaskInfo taskToFront) {
                mListener.call(l -> l.onTaskMovedToFront(taskToFront));
            }

            @Override
            public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
                mListener.call(l -> l.onTaskInfoChanged(taskInfo));
            }

            @Override
            public void onVisibleTasksChanged(GroupedTaskInfo[] visibleTasks) {
                mListener.call(l -> l.onVisibleTasksChanged(visibleTasks));
            }
        };

        public IRecentTasksImpl(RecentTasksController controller) {
            mController = controller;
            mListener = new SingleInstanceRemoteListener<>(controller,
                    c -> c.registerRecentTasksListener(mRecentTasksListener),
                    c -> c.unregisterRecentTasksListener());
        }

        /**
         * Invalidates this instance, preventing future calls from updating the controller.
         */
        @Override
        public void invalidate() {
            mController = null;
            // Unregister the listener to ensure any registered binder death recipients are unlinked
            mListener.unregister();
        }

        @Override
        public void registerRecentTasksListener(IRecentTasksListener listener)
                throws RemoteException {
            executeRemoteCallWithTaskPermission(mController, "registerRecentTasksListener",
                    (controller) -> mListener.register(listener));
        }

        @Override
        public void unregisterRecentTasksListener(IRecentTasksListener listener)
                throws RemoteException {
            executeRemoteCallWithTaskPermission(mController, "unregisterRecentTasksListener",
                    (controller) -> mListener.unregister());
        }

        @Override
        public GroupedTaskInfo[] getRecentTasks(int maxNum, int flags, int userId)
                throws RemoteException {
            if (mController == null) {
                // The controller is already invalidated -- just return an empty task list for now
                return new GroupedTaskInfo[0];
            }

            final GroupedTaskInfo[][] out = new GroupedTaskInfo[][]{null};
            executeRemoteCallWithTaskPermission(mController, "getRecentTasks",
                    (controller) -> {
                        List<GroupedTaskInfo> tasks = controller.getRecentTasks(
                                maxNum, flags, userId);
                        out[0] = tasks.toArray(new GroupedTaskInfo[0]);
                    },
                    true /* blocking */);
            return out[0];
        }

        @Override
        public RunningTaskInfo[] getRunningTasks(int maxNum) {
            final RunningTaskInfo[][] tasks =
                    new RunningTaskInfo[][]{null};
            executeRemoteCallWithTaskPermission(mController, "getRunningTasks",
                    (controller) -> tasks[0] = ActivityTaskManager.getInstance().getTasks(maxNum)
                            .toArray(new RunningTaskInfo[0]),
                    true /* blocking */);
            return tasks[0];
        }

        @Override
        public void startRecentsTransition(PendingIntent intent, Intent fillIn, Bundle options,
                @Nullable WindowContainerTransaction wct,
                IApplicationThread appThread, IRecentsAnimationRunner listener) {
            if (mController.mTransitionHandler == null) {
                Slog.e(TAG, "Used shell-transitions startRecentsTransition without"
                        + " shell-transitions");
                return;
            }
            executeRemoteCallWithTaskPermission(mController, "startRecentsTransition",
                    (controller) -> controller.mTransitionHandler.startRecentsTransition(
                            intent, fillIn, options, wct, appThread, listener));
        }
    }
}
