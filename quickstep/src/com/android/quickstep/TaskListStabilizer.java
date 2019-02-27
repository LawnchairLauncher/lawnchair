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

import android.app.ActivityManager.RecentTaskInfo;
import android.content.ComponentName;
import android.os.Process;
import android.os.SystemClock;

import com.android.launcher3.util.IntArray;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps the task list stable during quick switch gestures. So if you swipe right to switch from app
 * A to B, you can then swipe right again to get to app C or left to get back to A.
 */
public class TaskListStabilizer {

    private static final long TASK_CACHE_TIMEOUT_MS = 5000;

    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {

        @Override
        public void onTaskCreated(int taskId, ComponentName componentName) {
            endStabilizationSession();
        }

        @Override
        public void onTaskRemoved(int taskId) {
            endStabilizationSession();
        }
    };

    // Task ids ordered based on recency, 0th index is the least recent task
    private final IntArray mSystemOrder;
    private final IntArray mStabilizedOrder;

    // Wrapper objects used for sorting tasks
    private final ArrayList<TaskWrapper> mTaskWrappers = new ArrayList<>();

    private boolean mInStabilizationSession;
    private long mSessionStartTime;

    public TaskListStabilizer() {
        // Initialize the task ids map
        List<RecentTaskInfo> rawTasks = ActivityManagerWrapper.getInstance().getRecentTasks(
                Integer.MAX_VALUE, Process.myUserHandle().getIdentifier());
        mSystemOrder = new IntArray(rawTasks.size());
        for (RecentTaskInfo info : rawTasks) {
            mSystemOrder.add(new TaskKey(info).id);
        }
        // We will lazily copy the task id's from mSystemOrder when a stabilization session starts.
        mStabilizedOrder = new IntArray(rawTasks.size());

        ActivityManagerWrapper.getInstance().registerTaskStackListener(mTaskStackListener);
    }

    public synchronized void startStabilizationSession() {
        if (!mInStabilizationSession) {
            mStabilizedOrder.clear();
            mStabilizedOrder.addAll(mSystemOrder);
        }
        mInStabilizationSession = true;
        mSessionStartTime = SystemClock.uptimeMillis();
    }

    public synchronized void endStabilizationSession() {
        mInStabilizationSession = false;
    }

    public synchronized ArrayList<Task> reorder(ArrayList<Task> tasks) {
        mSystemOrder.clear();
        for (Task task : tasks) {
            mSystemOrder.add(task.key.id);
        }

        if ((SystemClock.uptimeMillis() - mSessionStartTime) > TASK_CACHE_TIMEOUT_MS) {
            endStabilizationSession();
        }

        if (!mInStabilizationSession) {
            return tasks;
        }

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
            wrapper.index = mStabilizedOrder.indexOf(wrapper.task.key.id);

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
