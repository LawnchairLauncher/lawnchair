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

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.util.SparseBooleanArray;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.util.LooperExecutor;
import com.android.systemui.shared.recents.model.GroupTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.KeyguardManagerCompat;
import com.android.wm.shell.recents.IRecentTasksListener;
import com.android.wm.shell.util.GroupedRecentTaskInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * Manages the recent task list from the system, caching it as necessary.
 */
@TargetApi(Build.VERSION_CODES.R)
public class RecentTasksList {

    private static final TaskLoadResult INVALID_RESULT = new TaskLoadResult(-1, false, 0);

    private final KeyguardManagerCompat mKeyguardManager;
    private final LooperExecutor mMainThreadExecutor;
    private final SystemUiProxy mSysUiProxy;

    // The list change id, increments as the task list changes in the system
    private int mChangeId;
    // Whether we are currently updating the tasks in the background (up to when the result is
    // posted back on the main thread)
    private boolean mLoadingTasksInBackground;

    private TaskLoadResult mResultsBg = INVALID_RESULT;
    private TaskLoadResult mResultsUi = INVALID_RESULT;

    public RecentTasksList(LooperExecutor mainThreadExecutor,
            KeyguardManagerCompat keyguardManager, SystemUiProxy sysUiProxy) {
        mMainThreadExecutor = mainThreadExecutor;
        mKeyguardManager = keyguardManager;
        mChangeId = 1;
        mSysUiProxy = sysUiProxy;
        sysUiProxy.registerRecentTasksListener(new IRecentTasksListener.Stub() {
            @Override
            public void onRecentTasksChanged() throws RemoteException {
                mMainThreadExecutor.execute(RecentTasksList.this::onRecentTasksChanged);
            }
        });
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
     * @param callback The callback to receive the list of recent tasks
     * @return The change id of the current task list
     */
    public synchronized int getTasks(boolean loadKeysOnly,
            Consumer<ArrayList<GroupTask>> callback) {
        final int requestLoadId = mChangeId;
        if (mResultsUi.isValidForRequest(requestLoadId, loadKeysOnly)) {
            // The list is up to date, send the callback on the next frame,
            // so that requestID can be returned first.
            if (callback != null) {
                // Copy synchronously as the changeId might change by next frame
                ArrayList<GroupTask> result = copyOf(mResultsUi);
                mMainThreadExecutor.post(() -> {
                    callback.accept(result);
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
                    ArrayList<GroupTask> result = copyOf(mResultsUi);
                    callback.accept(result);
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
    }

    private synchronized void invalidateLoadedTasks() {
        UI_HELPER_EXECUTOR.execute(() -> mResultsBg = INVALID_RESULT);
        mResultsUi = INVALID_RESULT;
        mChangeId++;
    }

    /**
     * Loads and creates a list of all the recent tasks.
     */
    @VisibleForTesting
    TaskLoadResult loadTasksInBackground(int numTasks, int requestId, boolean loadKeysOnly) {
        int currentUserId = Process.myUserHandle().getIdentifier();
        ArrayList<GroupedRecentTaskInfo> rawTasks =
                mSysUiProxy.getRecentTasks(numTasks, currentUserId);
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
        for (GroupedRecentTaskInfo rawTask : rawTasks) {
            ActivityManager.RecentTaskInfo taskInfo1 = rawTask.mTaskInfo1;
            ActivityManager.RecentTaskInfo taskInfo2 = rawTask.mTaskInfo2;
            Task.TaskKey task1Key = new Task.TaskKey(taskInfo1);
            Task task1 = loadKeysOnly
                    ? new Task(task1Key)
                    : Task.from(task1Key, taskInfo1,
                            tmpLockedUsers.get(task1Key.userId) /* isLocked */);
            task1.setLastSnapshotData(taskInfo1);
            Task task2 = null;
            if (taskInfo2 != null) {
                Task.TaskKey task2Key = new Task.TaskKey(taskInfo2);
                task2 = loadKeysOnly
                        ? new Task(task2Key)
                        : Task.from(task2Key, taskInfo2,
                                tmpLockedUsers.get(task2Key.userId) /* isLocked */);
                task2.setLastSnapshotData(taskInfo2);
            }
            allTasks.add(new GroupTask(task1, task2));
        }

        return allTasks;
    }

    private ArrayList<GroupTask> copyOf(ArrayList<GroupTask> tasks) {
        ArrayList<GroupTask> newTasks = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            newTasks.add(new GroupTask(tasks.get(i)));
        }
        return newTasks;
    }

    private static class TaskLoadResult extends ArrayList<GroupTask> {

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