/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.recents.model;

import android.util.ArrayMap;
import android.util.SparseArray;

import com.android.systemui.shared.recents.model.Task.TaskKey;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of filtered tasks.
 */
class FilteredTaskList {

    private final ArrayList<Task> mTasks = new ArrayList<>();
    private final ArrayList<Task> mFilteredTasks = new ArrayList<>();
    private final ArrayMap<TaskKey, Integer> mFilteredTaskIndices = new ArrayMap<>();
    private TaskFilter mFilter;

    /** Sets the task filter, and returns whether the set of filtered tasks have changed. */
    boolean setFilter(TaskFilter filter) {
        ArrayList<Task> prevFilteredTasks = new ArrayList<>(mFilteredTasks);
        mFilter = filter;
        updateFilteredTasks();
        return !prevFilteredTasks.equals(mFilteredTasks);
    }

    /** Adds a new task to the task list */
    void add(Task t) {
        mTasks.add(t);
        updateFilteredTasks();
    }

    /** Sets the list of tasks */
    void set(List<Task> tasks) {
        mTasks.clear();
        mTasks.addAll(tasks);
        updateFilteredTasks();
    }

    /** Removes a task from the base list only if it is in the filtered list */
    boolean remove(Task t) {
        if (mFilteredTasks.contains(t)) {
            boolean removed = mTasks.remove(t);
            updateFilteredTasks();
            return removed;
        }
        return false;
    }

    /** Returns the index of this task in the list of filtered tasks */
    int indexOf(Task t) {
        if (t != null && mFilteredTaskIndices.containsKey(t.key)) {
            return mFilteredTaskIndices.get(t.key);
        }
        return -1;
    }

    /** Returns the size of the list of filtered tasks */
    int size() {
        return mFilteredTasks.size();
    }

    /** Returns whether the filtered list contains this task */
    boolean contains(Task t) {
        return mFilteredTaskIndices.containsKey(t.key);
    }

    /** Updates the list of filtered tasks whenever the base task list changes */
    private void updateFilteredTasks() {
        mFilteredTasks.clear();
        if (mFilter != null) {
            // Create a sparse array from task id to Task
            SparseArray<Task> taskIdMap = new SparseArray<>();
            int taskCount = mTasks.size();
            for (int i = 0; i < taskCount; i++) {
                Task t = mTasks.get(i);
                taskIdMap.put(t.key.id, t);
            }

            for (int i = 0; i < taskCount; i++) {
                Task t = mTasks.get(i);
                if (mFilter.acceptTask(taskIdMap, t, i)) {
                    mFilteredTasks.add(t);
                }
            }
        } else {
            mFilteredTasks.addAll(mTasks);
        }
        updateFilteredTaskIndices();
    }

    /** Updates the mapping of tasks to indices. */
    private void updateFilteredTaskIndices() {
        int taskCount = mFilteredTasks.size();
        mFilteredTaskIndices.clear();
        for (int i = 0; i < taskCount; i++) {
            Task t = mFilteredTasks.get(i);
            mFilteredTaskIndices.put(t.key, i);
        }
    }

    /** Returns the list of filtered tasks */
    ArrayList<Task> getTasks() {
        return mFilteredTasks;
    }
}
