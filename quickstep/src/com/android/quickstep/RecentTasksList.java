/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.quickstep;

import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.wm.shell.shared.GroupedTaskInfo.TYPE_DESK;
import static com.android.wm.shell.shared.GroupedTaskInfo.TYPE_SPLIT;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.KeyguardManager;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ParceledListSlice;
import android.os.Process;
import android.os.RemoteException;
import android.util.SparseBooleanArray;
import android.window.DesktopExperienceFlags;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.LooperExecutor;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.ExternalDisplaysKt;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SingleTask;
import com.android.quickstep.util.SplitTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.wm.shell.Flags;
import com.android.wm.shell.recents.IRecentTasksListener;
import com.android.wm.shell.shared.GroupedTaskInfo;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;

import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.collections.MapsKt;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import app.lawnchair.LawnchairApp;
import app.lawnchair.compat.LawnchairQuickstepCompat;

/**
 * Manages the recent task list from the system, caching it as necessary.
 */
public class RecentTasksList {

    private static final TaskLoadResult INVALID_RESULT = new TaskLoadResult(-1, false, 0);

    private final Context mContext;
    private final KeyguardManager mKeyguardManager;
    private final LooperExecutor mMainThreadExecutor;
    private final SystemUiProxy mSysUiProxy;

    // The list change id, increments as the task list changes in the system
    private int mChangeId;
    // Whether we are currently updating the tasks in the background (up to when the result is
    // posted back on the main thread)
    private boolean mLoadingTasksInBackground;

    private TaskLoadResult mResultsBg = INVALID_RESULT;
    private TaskLoadResult mResultsUi = INVALID_RESULT;

    private @Nullable RecentsModel.RunningTasksListener mRunningTasksListener;
    private @Nullable RecentsModel.RecentTasksChangedListener mRecentTasksChangedListener;
    // Tasks are stored in order of least recently launched to most recently launched.
    private ArrayList<RunningTaskInfo> mRunningTasks;

    public RecentTasksList(Context context, LooperExecutor mainThreadExecutor,
            KeyguardManager keyguardManager, SystemUiProxy sysUiProxy,
            TopTaskTracker topTaskTracker,
            DaggerSingletonTracker tracker) {
        mContext = context;
        mMainThreadExecutor = mainThreadExecutor;
        mKeyguardManager = keyguardManager;
        mChangeId = 1;
        mSysUiProxy = sysUiProxy;
        if (LawnchairApp.isRecentsEnabled()) {
            if (LawnchairQuickstepCompat.ATLEAST_U) {
                final IRecentTasksListener recentTasksListener = new IRecentTasksListener.Stub() {
                    @Override
                    public void onRecentTasksChanged() throws RemoteException {
                        mMainThreadExecutor.execute(RecentTasksList.this::onRecentTasksChanged);
                    }

                    @Override
                    public void onRunningTaskAppeared(RunningTaskInfo taskInfo) {
                        mMainThreadExecutor.execute(() -> {
                            RecentTasksList.this.onRunningTaskAppeared(taskInfo);
                        });
                    }

                    @Override
                    public void onRunningTaskVanished(RunningTaskInfo taskInfo) {
                        mMainThreadExecutor.execute(() -> {
                            RecentTasksList.this.onRunningTaskVanished(taskInfo);
                        });
                    }

                    @Override
                    public void onRunningTaskChanged(RunningTaskInfo taskInfo) {
                        mMainThreadExecutor.execute(() -> {
                            RecentTasksList.this.onRunningTaskChanged(taskInfo);
                        });
                    }

                    @Override
                    public void onTaskMovedToFront(GroupedTaskInfo taskToFront) {
                        mMainThreadExecutor.execute(() -> {
                            topTaskTracker.handleTaskMovedToFront(
                                taskToFront.getBaseGroupedTask().getTaskInfo1());
                        });
                    }

                    @Override
                    public void onTaskInfoChanged(RunningTaskInfo taskInfo) {
                        mMainThreadExecutor.execute(() -> topTaskTracker.onTaskChanged(taskInfo));
                    }

                    @Override
                    public void onVisibleTasksChanged(GroupedTaskInfo[] visibleTasks) {
                        mMainThreadExecutor.execute(() -> {
                            topTaskTracker.onVisibleTasksChanged(visibleTasks);
                        });
                    }
            };

                mSysUiProxy.registerRecentTasksListener(recentTasksListener);
                tracker.addCloseable(
                    () -> mSysUiProxy.unregisterRecentTasksListener(recentTasksListener));
            } else if (LawnchairQuickstepCompat.ATLEAST_Q) {
                TaskStackChangeListeners.getInstance()
                    .registerTaskStackListener(new TaskStackChangeListener() {
                        @Override
                        public void onTaskStackChanged() {
                            onRecentTasksChanged();
                        }

                        @Override
                        public void onRecentTaskListUpdated() {
                            onRecentTasksChanged();
                        }

                        @Override
                        public void onTaskRemoved(int taskId) {
                            onRecentTasksChanged();
                        }

                        @Override
                        public void onActivityPinned(String packageName, int userId, int taskId,
                            int stackId) {
                            onRecentTasksChanged();
                        }

                        @Override
                        public void onActivityUnpinned() {
                            onRecentTasksChanged();
                        }
                    });
            }
        }

        // We may receive onRunningTaskAppeared events later for tasks which have already been
        // included in the list returned by mSysUiProxy.getRunningTasks(), or may receive
        // onRunningTaskVanished for tasks not included in the returned list. These cases will be
        // addressed when the tasks are added to/removed from mRunningTasks.
        initRunningTasks(mSysUiProxy.getRunningTasks(Integer.MAX_VALUE));
    }

    @VisibleForTesting
    public boolean isLoadingTasksInBackground() {
        return mLoadingTasksInBackground;
    }

    /**
     * Fetches the task keys skipping any local cache.
     */
    public void getTaskKeys(int numTasks, Consumer<ArrayList<GroupTask>> callback) {
        // Kick off task loading in the background
        UI_HELPER_EXECUTOR.execute(() -> {
            ArrayList<GroupTask> tasks = loadTasksInBackground(numTasks, -1,
                    true /* loadKeysOnly */);
            mMainThreadExecutor.execute(() -> callback.accept(tasks));
        });
    }

    /**
     * Asynchronously fetches the list of recent tasks, reusing cached list if available.
     *
     * @param loadKeysOnly Whether to load other associated task data, or just the key
     * @param callback     The callback to receive the list of recent tasks
     * @return The change id of the current task list
     */
    public synchronized int getTasks(
            boolean loadKeysOnly,
            @Nullable Consumer<List<GroupTask>> callback,
            Predicate<GroupTask> filter) {
        return getTasks(
                loadKeysOnly,
                callback == null ? null : (tasks, requestId) -> callback.accept(tasks),
                filter);
    }

    /**
     * Asynchronously fetches the list of recent tasks, reusing cached list if available.
     *
     * @param loadKeysOnly Whether to load other associated task data, or just the key
     * @param callback     The callback to receive the list of recent tasks
     * @return The change id of the current task list
     */
    public synchronized int getTasks(
            boolean loadKeysOnly,
            @Nullable BiConsumer<List<GroupTask>, Integer> callback,
            Predicate<GroupTask> filter) {
        final int requestLoadId = mChangeId;
        if (mResultsUi.isValidForRequest(requestLoadId, loadKeysOnly)) {
            // The list is up to date, send the callback on the next frame,
            // so that requestID can be returned first.
            if (callback != null) {
                // Copy synchronously as the changeId might change by next frame
                // and filter GroupTasks
                ArrayList<GroupTask> result = mResultsUi.stream().filter(filter)
                        .map(GroupTask::copy)
                        .collect(Collectors.toCollection(ArrayList<GroupTask>::new));

                mMainThreadExecutor.post(() -> {
                    callback.accept(result, requestLoadId);
                });
            }

            return requestLoadId;
        }

        // Kick off task loading in the background
        mLoadingTasksInBackground = true;
        UI_HELPER_EXECUTOR.execute(() -> {
            if (!mResultsBg.isValidForRequest(requestLoadId, loadKeysOnly)) {
                mResultsBg = loadTasksInBackground(Integer.MAX_VALUE, requestLoadId, loadKeysOnly);
            }
            TaskLoadResult loadResult = mResultsBg;
            mMainThreadExecutor.execute(() -> {
                mLoadingTasksInBackground = false;
                mResultsUi = loadResult;
                if (callback != null) {
                    // filter the tasks if needed before passing them into the callback
                    ArrayList<GroupTask> result = mResultsUi.stream().filter(filter)
                            .map(GroupTask::copy)
                            .collect(Collectors.toCollection(ArrayList<GroupTask>::new));

                    callback.accept(result, requestLoadId);
                }
            });
        });

        return requestLoadId;
    }

    /**
     * @return Whether the provided {@param changeId} is the latest recent tasks list id.
     */
    public synchronized boolean isTaskListValid(int changeId) {
        return mChangeId == changeId;
    }

    public void onRecentTasksChanged() {
        invalidateLoadedTasks();
        if (mRecentTasksChangedListener != null) {
            mRecentTasksChangedListener.onRecentTasksChanged();
        }
    }

    private synchronized void invalidateLoadedTasks() {
        UI_HELPER_EXECUTOR.execute(() -> mResultsBg = INVALID_RESULT);
        mResultsUi = INVALID_RESULT;
        mChangeId++;
    }

    /**
     * Registers a listener for running tasks
     */
    public void registerRunningTasksListener(RecentsModel.RunningTasksListener listener) {
        mRunningTasksListener = listener;
    }

    /**
     * Removes the previously registered running tasks listener
     */
    public void unregisterRunningTasksListener() {
        mRunningTasksListener = null;
    }

    /**
     * Registers a listener for running tasks
     */
    public void registerRecentTasksChangedListener(
            RecentsModel.RecentTasksChangedListener listener) {
        mRecentTasksChangedListener = listener;
    }

    /**
     * Removes the previously registered running tasks listener
     */
    public void unregisterRecentTasksChangedListener() {
        mRecentTasksChangedListener = null;
    }

    private void initRunningTasks(List<RunningTaskInfo> runningTasks) {
        // Tasks are retrieved in order of most recently launched/used to least recently launched.
        mRunningTasks = new ArrayList<>(runningTasks);
        Collections.reverse(mRunningTasks);
    }

    /**
     * Gets the set of running tasks.
     */
    public ArrayList<RunningTaskInfo> getRunningTasks() {
        return mRunningTasks;
    }

    private void onRunningTaskAppeared(RunningTaskInfo taskInfo) {
        // Make sure this task is not already in the list
        for (RunningTaskInfo existingTask : mRunningTasks) {
            if (taskInfo.taskId == existingTask.taskId) {
                return;
            }
        }
        mRunningTasks.add(taskInfo);
        if (mRunningTasksListener != null) {
            mRunningTasksListener.onRunningTasksChanged();
        }
    }

    private void onRunningTaskVanished(RunningTaskInfo taskInfo) {
        // Find the task from the list of running tasks, if it exists
        for (RunningTaskInfo existingTask : mRunningTasks) {
            if (existingTask.taskId != taskInfo.taskId) continue;

            mRunningTasks.remove(existingTask);
            if (mRunningTasksListener != null) {
                mRunningTasksListener.onRunningTasksChanged();
            }
            return;
        }
    }

    private void onRunningTaskChanged(RunningTaskInfo taskInfo) {
        // Find the task from the list of running tasks, if it exists
        for (RunningTaskInfo existingTask : mRunningTasks) {
            if (existingTask.taskId != taskInfo.taskId) continue;

            mRunningTasks.remove(existingTask);
            mRunningTasks.add(taskInfo);
            if (mRunningTasksListener != null) {
                mRunningTasksListener.onRunningTasksChanged();
            }
            return;
        }
    }

    /**
     * Loads and creates a list of all the recent tasks.
     */
    @VisibleForTesting
    TaskLoadResult loadTasksInBackground(int numTasks, int requestId, boolean loadKeysOnly) {
        int currentUserId = Process.myUserHandle().getIdentifier();
        ArrayList<GroupedTaskInfo> rawTasks;
        try {
            rawTasks = mSysUiProxy.getRecentTasks(numTasks, currentUserId);
        } catch (SystemUiProxy.GetRecentTasksException e) {
            return INVALID_RESULT;
        }
        // The raw tasks are given in most-recent to least-recent order, we need to reverse it
        Collections.reverse(rawTasks);

        SparseBooleanArray tmpLockedUsers = new SparseBooleanArray() {
            @Override
            public boolean get(int key) {
                if (indexOfKey(key) < 0) {
                    // Fill the cached locked state as we fetch
                    put(key, mKeyguardManager.isDeviceLocked(key));
                }
                return super.get(key);
            }
        };

        TaskLoadResult allTasks = new TaskLoadResult(requestId, loadKeysOnly, rawTasks.size());

        boolean isFirstVisibleTaskFound = false;
        for (GroupedTaskInfo rawTask : rawTasks) {
            if (rawTask.isBaseType(TYPE_DESK)) {
                // TYPE_DESK tasks is only created when desktop mode can be entered,
                // leftover TYPE_DESK tasks created when flag was on should be ignored.
                if (DesktopModeStatus.canEnterDesktopMode(mContext)) {
                    List<DesktopTask> desktopTasks = createDesktopTasks(
                            rawTask.getBaseGroupedTask());
                    allTasks.addAll(desktopTasks);

                    // If any task in desktop group task is visible, set isFirstVisibleTaskFound to
                    // true. This way if there is a transparent task in the list later on, it does
                    // not get its own tile in Overview.
                    if (rawTask.getBaseGroupedTask().getTaskInfoList().stream().anyMatch(
                            taskInfo -> taskInfo.isVisible)) {
                        isFirstVisibleTaskFound = true;
                    }
                }
                continue;
            }

            // [getTaskInfo1] will not be null for types below beside [TYPE_DESK].
            if (Flags.enableShellTopTaskTracking()) {
                final TaskInfo taskInfo1 = rawTask.getBaseGroupedTask().getTaskInfo1();
                final Task.TaskKey task1Key = new Task.TaskKey(taskInfo1);
                final Task task1 = Task.from(task1Key, taskInfo1,
                        tmpLockedUsers.get(task1Key.userId) /* isLocked */);

                if (rawTask.isBaseType(TYPE_SPLIT)) {
                    final TaskInfo taskInfo2 = rawTask.getBaseGroupedTask().getTaskInfo2();
                    final Task.TaskKey task2Key = new Task.TaskKey(taskInfo2);
                    final Task task2 = Task.from(task2Key, taskInfo2,
                            tmpLockedUsers.get(task2Key.userId) /* isLocked */);
                    allTasks.add(new SplitTask(task1, task2,
                            rawTask.getBaseGroupedTask().getSplitBounds()));
                } else {
                    allTasks.add(new SingleTask(task1));
                }
            } else {
                TaskInfo taskInfo1 = rawTask.getTaskInfo1();
                TaskInfo taskInfo2 = rawTask.getTaskInfo2();
                Task.TaskKey task1Key = new Task.TaskKey(taskInfo1);
                Task task1 = loadKeysOnly
                        ? new Task(task1Key)
                        : Task.from(task1Key, taskInfo1,
                                tmpLockedUsers.get(task1Key.userId) /* isLocked */);
                Task task2 = null;
                if (taskInfo2 != null) {
                    // Is split task
                    Task.TaskKey task2Key = new Task.TaskKey(taskInfo2);
                    task2 = loadKeysOnly
                            ? new Task(task2Key)
                            : Task.from(task2Key, taskInfo2,
                                    tmpLockedUsers.get(task2Key.userId) /* isLocked */);
                } else {
                    // Is fullscreen task
                    if (isFirstVisibleTaskFound) {
                        boolean isExcluded = (taskInfo1.baseIntent.getFlags()
                                & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0;
                        if (taskInfo1.isTopActivityTransparent && isExcluded) {
                            // If there are already visible tasks, then ignore the excluded tasks
                            // and don't add them to the returned list
                            continue;
                        }
                    }
                }
                if (taskInfo1.isVisible) {
                    isFirstVisibleTaskFound = true;
                }
                if (task2 != null) {
                    Objects.requireNonNull(rawTask.getSplitBounds());
                    allTasks.add(new SplitTask(task1, task2, rawTask.getSplitBounds()));
                } else {
                    allTasks.add(new SingleTask(task1));
                }
            }
        }

        return allTasks;
    }

    private Task createTask(TaskInfo taskInfo, Set<Integer> minimizedTaskIds) {
        Task.TaskKey key = new Task.TaskKey(taskInfo);
        Task task = Task.from(key, taskInfo, false);
        task.positionInParent = taskInfo.positionInParent;
        task.appBounds = taskInfo.configuration.windowConfiguration.getAppBounds();
        task.isVisible = taskInfo.isVisible;
        task.isMinimized = minimizedTaskIds.contains(taskInfo.taskId);
        return task;
    }

    private List<DesktopTask> createDesktopTasks(GroupedTaskInfo recentTaskInfo) {
        int[] minimizedTaskIdArray = recentTaskInfo.getMinimizedTaskIds();
        Set<Integer> minimizedTaskIds = minimizedTaskIdArray != null
                ? CollectionsKt.toSet(ArraysKt.asIterable(minimizedTaskIdArray))
                : Collections.emptySet();
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue()) {
            // This code is not needed when the multiple desktop feature is enabled, since Shell
            // will send a single `GroupedTaskInfo` for each desk with a unique `deskId` across
            // all displays.
            Map<Integer, List<Task>> perDisplayTasks = new HashMap<>();
            for (TaskInfo taskInfo : recentTaskInfo.getTaskInfoList()) {
                Task task = createTask(taskInfo, minimizedTaskIds);
                List<Task> tasks = perDisplayTasks.computeIfAbsent(
                        ExternalDisplaysKt.getSafeDisplayId(task),
                        k -> new ArrayList<>());
                tasks.add(task);
            }
            // When the multiple desktop feature is disabled, there can only be up to a single desk
            // on each display, The desk ID doesn't matter and should not be used.
            return MapsKt.map(perDisplayTasks,
                    it -> new DesktopTask(DesktopVisibilityController.INACTIVE_DESK_ID, it.getKey(),
                            it.getValue()));
        } else {
            final int deskId = recentTaskInfo.getDeskId();
            final int displayId = recentTaskInfo.getDeskDisplayId();
            List<Task> tasks = CollectionsKt.map(recentTaskInfo.getTaskInfoList(),
                    it -> createTask(it, minimizedTaskIds));
            return List.of(new DesktopTask(deskId, displayId, tasks));
        }
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "RecentTasksList:");
        writer.println(prefix + "  mChangeId=" + mChangeId);
        writer.println(prefix + "  mResultsUi=[id=" + mResultsUi.mRequestId + ", tasks=");
        for (GroupTask task : mResultsUi) {
            int count = 0;
            for (Task t : task.getTasks()) {
                ComponentName cn = t.getTopComponent();
                writer.println(prefix + "    t" + (++count) + ": (id=" + t.key.id
                        + "; package=" + (cn != null ? cn.getPackageName() + ")" : "no package)"));
            }
        }
        writer.println(prefix + "  ]");
        int currentUserId = Process.myUserHandle().getIdentifier();
        ArrayList<GroupedTaskInfo> rawTasks;
        try {
            rawTasks = mSysUiProxy.getRecentTasks(Integer.MAX_VALUE, currentUserId);
        } catch (SystemUiProxy.GetRecentTasksException e) {
            rawTasks = new ArrayList<>();
        }
        writer.println(prefix + "  rawTasks=[");
        for (GroupedTaskInfo task : rawTasks) {
            writer.println(prefix + task);
        }
        writer.println(prefix + "  ]");
    }

    @VisibleForTesting
    static class TaskLoadResult extends ArrayList<GroupTask> {

        final int mRequestId;

        // If the result was loaded with keysOnly  = true
        final boolean mKeysOnly;

        TaskLoadResult(int requestId, boolean keysOnly, int size) {
            super(size);
            mRequestId = requestId;
            mKeysOnly = keysOnly;
        }

        boolean isValidForRequest(int requestId, boolean loadKeysOnly) {
            return mRequestId == requestId && (!mKeysOnly || loadKeysOnly);
        }
    }
}
