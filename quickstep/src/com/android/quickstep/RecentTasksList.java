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
import android.util.SparseBooleanArray;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.util.LooperExecutor;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.KeyguardManagerCompat;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the recent task list from the system, caching it as necessary.
 */
@TargetApi(Build.VERSION_CODES.R)
public class RecentTasksList extends TaskStackChangeListener {

    private static final TaskLoadResult INVALID_RESULT = new TaskLoadResult(-1, false, 0);

    private final KeyguardManagerCompat mKeyguardManager;
    private final LooperExecutor mMainThreadExecutor;
    private final ActivityManagerWrapper mActivityManagerWrapper;

    // The list change id, increments as the task list changes in the system
    private int mChangeId;

    private TaskLoadResult mResultsBg = INVALID_RESULT;
    private TaskLoadResult mResultsUi = INVALID_RESULT;

    public RecentTasksList(LooperExecutor mainThreadExecutor,
            KeyguardManagerCompat keyguardManager, ActivityManagerWrapper activityManagerWrapper) {
        mMainThreadExecutor = mainThreadExecutor;
        mKeyguardManager = keyguardManager;
        mChangeId = 1;
        mActivityManagerWrapper = activityManagerWrapper;
        mActivityManagerWrapper.registerTaskStackListener(this);
    }

    /**
     * Fetches the task keys skipping any local cache.
     */
    public void getTaskKeys(int numTasks, Consumer<ArrayList<Task>> callback) {
        // Kick off task loading in the background
        UI_HELPER_EXECUTOR.execute(() -> {
            ArrayList<Task> tasks = loadTasksInBackground(numTasks, -1, true /* loadKeysOnly */);
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
    public synchronized int getTasks(boolean loadKeysOnly, Consumer<ArrayList<Task>> callback) {
        final int requestLoadId = mChangeId;
        if (mResultsUi.isValidForRequest(requestLoadId, loadKeysOnly)) {
            // The list is up to date, send the callback on the next frame,
            // so that requestID can be returned first.
            if (callback != null) {
                // Copy synchronously as the changeId might change by next frame
                ArrayList<Task> result = copyOf(mResultsUi);
                mMainThreadExecutor.post(() -> callback.accept(result));
            }

            return requestLoadId;
        }

        // Kick off task loading in the background
        UI_HELPER_EXECUTOR.execute(() -> {
            if (!mResultsBg.isValidForRequest(requestLoadId, loadKeysOnly)) {
                mResultsBg = loadTasksInBackground(Integer.MAX_VALUE, requestLoadId, loadKeysOnly);
            }
            TaskLoadResult loadResult = mResultsBg;
            mMainThreadExecutor.execute(() -> {
                mResultsUi = loadResult;
                if (callback != null) {
                    ArrayList<Task> result = copyOf(mResultsUi);
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

    @Override
    public void onTaskStackChanged() {
        invalidateLoadedTasks();
    }

    @Override
    public void onRecentTaskListUpdated() {
        // In some cases immediately after booting, the tasks in the system recent task list may be
        // loaded, but not in the active task hierarchy in the system.  These tasks are displayed in 
        // overview, but removing them don't result in a onTaskStackChanged() nor a onTaskRemoved()
        // callback (those are for changes to the active tasks), but the task list is still updated,
        // so we should also invalidate the change id to ensure we load a new list instead of 
        // reusing a stale list.
        invalidateLoadedTasks();
    }

    @Override
    public void onTaskRemoved(int taskId) {
        invalidateLoadedTasks();
    }


    @Override
    public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
        invalidateLoadedTasks();
    }

    @Override
    public synchronized void onActivityUnpinned() {
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
        List<ActivityManager.RecentTaskInfo> rawTasks =
                mActivityManagerWrapper.getRecentTasks(numTasks, currentUserId);
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
        for (ActivityManager.RecentTaskInfo rawTask : rawTasks) {
            Task.TaskKey taskKey = new Task.TaskKey(rawTask);
            Task task;
            if (!loadKeysOnly) {
                boolean isLocked = tmpLockedUsers.get(taskKey.userId);
                task = Task.from(taskKey, rawTask, isLocked);
            } else {
                task = new Task(taskKey);
            }
            allTasks.add(task);
        }

        return allTasks;
    }

    private ArrayList<Task> copyOf(ArrayList<Task> tasks) {
        ArrayList<Task> newTasks = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            newTasks.add(new Task(t.key, t.colorPrimary, t.colorBackground, t.isDockable,
                    t.isLocked, t.taskDescription, t.topActivity));
        }
        return newTasks;
    }

    private static class TaskLoadResult extends ArrayList<Task> {

        final int mId;

        // If the result was loaded with keysOnly  = true
        final boolean mKeysOnly;

        TaskLoadResult(int id, boolean keysOnly, int size) {
            super(size);
            mId = id;
            mKeysOnly = keysOnly;
        }

        boolean isValidForRequest(int requestId, boolean loadKeysOnly) {
            return mId == requestId && (!mKeysOnly || loadKeysOnly);
        }
    }
}