/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.config.FeatureFlags.ENABLE_TASK_STABILIZER;

import android.app.ActivityManager.RecentTaskInfo;
import android.content.ComponentName;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.android.launcher3.util.IntArray;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskListStabilizer {

    private static final long TASK_CACHE_TIMEOUT_MS = 5000;

    private static final int INVALID_TASK_ID = -1;

    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {

        @Override
        public void onTaskCreated(int taskId, ComponentName componentName) {
            onTaskCreatedInternal(taskId);
        }

        @Override
        public void onTaskMovedToFront(int taskId) {
            onTaskMovedToFrontInternal(taskId);
        }

        @Override
        public void onTaskRemoved(int taskId) {
            onTaskRemovedInternal(taskId);
        }
    };

    // Task ids ordered based on recency, 0th index is the latest task
    private final IntArray mOrderedTaskIds;

    // Wrapper objects used for sorting tasks
    private final ArrayList<TaskWrapper> mTaskWrappers = new ArrayList<>();

    // Information about recent task re-order which has not been applied yet
    private int mScheduledMoveTaskId = INVALID_TASK_ID;
    private long mScheduledMoveTime = 0;

    public TaskListStabilizer() {
        if (ENABLE_TASK_STABILIZER.get()) {
            // Initialize the task ids map
            List<RecentTaskInfo> rawTasks = ActivityManagerWrapper.getInstance().getRecentTasks(
                    Integer.MAX_VALUE, Process.myUserHandle().getIdentifier());
            mOrderedTaskIds = new IntArray(rawTasks.size());
            for (RecentTaskInfo info : rawTasks) {
                mOrderedTaskIds.add(new TaskKey(info).id);
            }

            ActivityManagerWrapper.getInstance().registerTaskStackListener(mTaskStackListener);
        } else {
            mOrderedTaskIds = null;
        }
    }

    private synchronized void onTaskCreatedInternal(int taskId) {
        applyScheduledMoveUnchecked();
        mOrderedTaskIds.add(taskId);
    }

    private synchronized void onTaskRemovedInternal(int taskId) {
        applyScheduledMoveUnchecked();
        mOrderedTaskIds.removeValue(taskId);
    }

    private void applyScheduledMoveUnchecked() {
        if (mScheduledMoveTaskId != INVALID_TASK_ID) {
            // Mode the scheduled task to front
            mOrderedTaskIds.removeValue(mScheduledMoveTaskId);
            mOrderedTaskIds.add(mScheduledMoveTaskId);
            mScheduledMoveTaskId = INVALID_TASK_ID;
        }
    }

    /**
     * Checks if the scheduled move has timed out and moves the task to front accordingly.
     */
    private void applyScheduledMoveIfTime() {
        if (mScheduledMoveTaskId != INVALID_TASK_ID
                && (SystemClock.uptimeMillis() - mScheduledMoveTime) > TASK_CACHE_TIMEOUT_MS) {
            applyScheduledMoveUnchecked();
        }
    }

    private synchronized void onTaskMovedToFrontInternal(int taskId) {
        applyScheduledMoveIfTime();
        mScheduledMoveTaskId = taskId;
        mScheduledMoveTime = SystemClock.uptimeMillis();
    }


    public synchronized ArrayList<Task> reorder(ArrayList<Task> tasks) {
        if (!ENABLE_TASK_STABILIZER.get()) {
            return tasks;
        }

        applyScheduledMoveIfTime();

        // Ensure that we have enough wrappers
        int taskCount = tasks.size();
        for (int i = taskCount - mTaskWrappers.size(); i > 0; i--) {
            mTaskWrappers.add(new TaskWrapper());
        }

        List<TaskWrapper> listToSort = mTaskWrappers.size() == taskCount
                ? mTaskWrappers : mTaskWrappers.subList(0, taskCount);
        int missingTaskIndex = -taskCount;

        for (int i = 0; i < taskCount; i++){
            TaskWrapper wrapper = listToSort.get(i);
            wrapper.task = tasks.get(i);
            wrapper.index = mOrderedTaskIds.indexOf(wrapper.task.key.id);

            // Ensure that missing tasks are put in the front, in the order they appear in the
            // original list
            if (wrapper.index < 0) {
                wrapper.index = missingTaskIndex;
                missingTaskIndex++;
            }
        }
        Collections.sort(listToSort);

        ArrayList<Task> result = new ArrayList<>(taskCount);
        for (int i = 0; i < taskCount; i++) {
            result.add(listToSort.get(i).task);
        }
        return result;
    }

    private static class TaskWrapper implements Comparable<TaskWrapper> {
        Task task;
        int index;

        @Override
        public int compareTo(TaskWrapper other) {
            return Integer.compare(index, other.index);
        }
    }
}
