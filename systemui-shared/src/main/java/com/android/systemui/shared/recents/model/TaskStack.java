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

package com.android.systemui.shared.recents.model;

import android.content.ComponentName;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.recents.utilities.AnimationProps;
import com.android.systemui.shared.system.PackageManagerWrapper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;


/**
 * The task stack contains a list of multiple tasks.
 */
public class TaskStack {

    private static final String TAG = "TaskStack";

    /** Task stack callbacks */
    public interface TaskStackCallbacks {
        /**
         * Notifies when a new task has been added to the stack.
         */
        void onStackTaskAdded(TaskStack stack, Task newTask);

        /**
         * Notifies when a task has been removed from the stack.
         */
        void onStackTaskRemoved(TaskStack stack, Task removedTask, Task newFrontMostTask,
                AnimationProps animation, boolean fromDockGesture,
                boolean dismissRecentsIfAllRemoved);

        /**
         * Notifies when all tasks have been removed from the stack.
         */
        void onStackTasksRemoved(TaskStack stack);

        /**
         * Notifies when tasks in the stack have been updated.
         */
        void onStackTasksUpdated(TaskStack stack);
    }

    private final ArrayList<Task> mRawTaskList = new ArrayList<>();
    private final FilteredTaskList mStackTaskList = new FilteredTaskList();
    private TaskStackCallbacks mCb;

    public TaskStack() {
        // Ensure that we only show stack tasks
        mStackTaskList.setFilter((taskIdMap, t, index) -> t.isStackTask);
    }

    /** Sets the callbacks for this task stack. */
    public void setCallbacks(TaskStackCallbacks cb) {
        mCb = cb;
    }

    /**
     * Removes a task from the stack, with an additional {@param animation} hint to the callbacks on
     * how they should update themselves.
     */
    public void removeTask(Task t, AnimationProps animation, boolean fromDockGesture) {
        removeTask(t, animation, fromDockGesture, true /* dismissRecentsIfAllRemoved */);
    }

    /**
     * Removes a task from the stack, with an additional {@param animation} hint to the callbacks on
     * how they should update themselves.
     */
    public void removeTask(Task t, AnimationProps animation, boolean fromDockGesture,
            boolean dismissRecentsIfAllRemoved) {
        if (mStackTaskList.contains(t)) {
            mStackTaskList.remove(t);
            Task newFrontMostTask = getFrontMostTask();
            if (mCb != null) {
                // Notify that a task has been removed
                mCb.onStackTaskRemoved(this, t, newFrontMostTask, animation,
                        fromDockGesture, dismissRecentsIfAllRemoved);
            }
        }
        mRawTaskList.remove(t);
    }

    /**
     * Removes all tasks from the stack.
     */
    public void removeAllTasks(boolean notifyStackChanges) {
        ArrayList<Task> tasks = mStackTaskList.getTasks();
        for (int i = tasks.size() - 1; i >= 0; i--) {
            Task t = tasks.get(i);
            mStackTaskList.remove(t);
            mRawTaskList.remove(t);
        }
        if (mCb != null && notifyStackChanges) {
            // Notify that all tasks have been removed
            mCb.onStackTasksRemoved(this);
        }
    }


    /**
     * @see #setTasks(List, boolean)
     */
    public void setTasks(TaskStack stack, boolean notifyStackChanges) {
        setTasks(stack.mRawTaskList, notifyStackChanges);
    }

    /**
     * Sets a few tasks in one go, without calling any callbacks.
     *
     * @param tasks the new set of tasks to replace the current set.
     * @param notifyStackChanges whether or not to callback on specific changes to the list of tasks.
     */
    public void setTasks(List<Task> tasks, boolean notifyStackChanges) {
        // Compute a has set for each of the tasks
        ArrayMap<TaskKey, Task> currentTasksMap = createTaskKeyMapFromList(mRawTaskList);
        ArrayMap<TaskKey, Task> newTasksMap = createTaskKeyMapFromList(tasks);
        ArrayList<Task> addedTasks = new ArrayList<>();
        ArrayList<Task> removedTasks = new ArrayList<>();
        ArrayList<Task> allTasks = new ArrayList<>();

        // Disable notifications if there are no callbacks
        if (mCb == null) {
            notifyStackChanges = false;
        }

        // Remove any tasks that no longer exist
        int taskCount = mRawTaskList.size();
        for (int i = taskCount - 1; i >= 0; i--) {
            Task task = mRawTaskList.get(i);
            if (!newTasksMap.containsKey(task.key)) {
                if (notifyStackChanges) {
                    removedTasks.add(task);
                }
            }
        }

        // Add any new tasks
        taskCount = tasks.size();
        for (int i = 0; i < taskCount; i++) {
            Task newTask = tasks.get(i);
            Task currentTask = currentTasksMap.get(newTask.key);
            if (currentTask == null && notifyStackChanges) {
                addedTasks.add(newTask);
            } else if (currentTask != null) {
                // The current task has bound callbacks, so just copy the data from the new task
                // state and add it back into the list
                currentTask.copyFrom(newTask);
                newTask = currentTask;
            }
            allTasks.add(newTask);
        }

        // Sort all the tasks to ensure they are ordered correctly
        for (int i = allTasks.size() - 1; i >= 0; i--) {
            allTasks.get(i).temporarySortIndexInStack = i;
        }

        mStackTaskList.set(allTasks);
        mRawTaskList.clear();
        mRawTaskList.addAll(allTasks);

        // Only callback for the removed tasks after the stack has updated
        int removedTaskCount = removedTasks.size();
        Task newFrontMostTask = getFrontMostTask();
        for (int i = 0; i < removedTaskCount; i++) {
            mCb.onStackTaskRemoved(this, removedTasks.get(i), newFrontMostTask,
                    AnimationProps.IMMEDIATE, false /* fromDockGesture */,
                    true /* dismissRecentsIfAllRemoved */);
        }

        // Only callback for the newly added tasks after this stack has been updated
        int addedTaskCount = addedTasks.size();
        for (int i = 0; i < addedTaskCount; i++) {
            mCb.onStackTaskAdded(this, addedTasks.get(i));
        }

        // Notify that the task stack has been updated
        if (notifyStackChanges) {
            mCb.onStackTasksUpdated(this);
        }
    }

    /**
     * Gets the front-most task in the stack.
     */
    public Task getFrontMostTask() {
        ArrayList<Task> stackTasks = mStackTaskList.getTasks();
        if (stackTasks.isEmpty()) {
            return null;
        }
        return stackTasks.get(stackTasks.size() - 1);
    }

    /** Gets the task keys */
    public ArrayList<TaskKey> getTaskKeys() {
        ArrayList<TaskKey> taskKeys = new ArrayList<>();
        ArrayList<Task> tasks = computeAllTasksList();
        int taskCount = tasks.size();
        for (int i = 0; i < taskCount; i++) {
            Task task = tasks.get(i);
            taskKeys.add(task.key);
        }
        return taskKeys;
    }

    /**
     * Returns the set of "active" (non-historical) tasks in the stack that have been used recently.
     */
    public ArrayList<Task> getTasks() {
        return mStackTaskList.getTasks();
    }

    /**
     * Computes a set of all the active and historical tasks.
     */
    public ArrayList<Task> computeAllTasksList() {
        ArrayList<Task> tasks = new ArrayList<>();
        tasks.addAll(mStackTaskList.getTasks());
        return tasks;
    }

    /**
     * Returns the number of stack tasks.
     */
    public int getTaskCount() {
        return mStackTaskList.size();
    }

    /**
     * Returns the task in stack tasks which is the launch target.
     */
    public Task getLaunchTarget() {
        ArrayList<Task> tasks = mStackTaskList.getTasks();
        int taskCount = tasks.size();
        for (int i = 0; i < taskCount; i++) {
            Task task = tasks.get(i);
            if (task.isLaunchTarget) {
                return task;
            }
        }
        return null;
    }

    /**
     * Returns whether the next launch target should actually be the PiP task.
     */
    public boolean isNextLaunchTargetPip(long lastPipTime) {
        Task launchTarget = getLaunchTarget();
        Task nextLaunchTarget = getNextLaunchTargetRaw();
        if (nextLaunchTarget != null && lastPipTime > 0) {
            // If the PiP time is more recent than the next launch target, then launch the PiP task
            return lastPipTime > nextLaunchTarget.key.lastActiveTime;
        } else if (launchTarget != null && lastPipTime > 0 && getTaskCount() == 1) {
            // Otherwise, if there is no next launch target, but there is a PiP, then launch
            // the PiP task
            return true;
        }
        return false;
    }

    /**
     * Returns the task in stack tasks which should be launched next if Recents are toggled
     * again, or null if there is no task to be launched. Callers should check
     * {@link #isNextLaunchTargetPip(long)} before fetching the next raw launch target from the
     * stack.
     */
    public Task getNextLaunchTarget() {
        Task nextLaunchTarget = getNextLaunchTargetRaw();
        if (nextLaunchTarget != null) {
            return nextLaunchTarget;
        }
        return getTasks().get(getTaskCount() - 1);
    }

    private Task getNextLaunchTargetRaw() {
        int taskCount = getTaskCount();
        if (taskCount == 0) {
            return null;
        }
        int launchTaskIndex = indexOfTask(getLaunchTarget());
        if (launchTaskIndex != -1 && launchTaskIndex > 0) {
            return getTasks().get(launchTaskIndex - 1);
        }
        return null;
    }

    /** Returns the index of this task in this current task stack */
    public int indexOfTask(Task t) {
        return mStackTaskList.indexOf(t);
    }

    /** Finds the task with the specified task id. */
    public Task findTaskWithId(int taskId) {
        ArrayList<Task> tasks = computeAllTasksList();
        int taskCount = tasks.size();
        for (int i = 0; i < taskCount; i++) {
            Task task = tasks.get(i);
            if (task.key.id == taskId) {
                return task;
            }
        }
        return null;
    }
    
    /**
     * Computes the components of tasks in this stack that have been removed as a result of a change
     * in the specified package.
     */
    public ArraySet<ComponentName> computeComponentsRemoved(String packageName, int userId) {
        // Identify all the tasks that should be removed as a result of the package being removed.
        // Using a set to ensure that we callback once per unique component.
        ArraySet<ComponentName> existingComponents = new ArraySet<>();
        ArraySet<ComponentName> removedComponents = new ArraySet<>();
        ArrayList<TaskKey> taskKeys = getTaskKeys();
        int taskKeyCount = taskKeys.size();
        for (int i = 0; i < taskKeyCount; i++) {
            TaskKey t = taskKeys.get(i);

            // Skip if this doesn't apply to the current user
            if (t.userId != userId) continue;

            ComponentName cn = t.getComponent();
            if (cn.getPackageName().equals(packageName)) {
                if (existingComponents.contains(cn)) {
                    // If we know that the component still exists in the package, then skip
                    continue;
                }
                if (PackageManagerWrapper.getInstance().getActivityInfo(cn, userId) != null) {
                    existingComponents.add(cn);
                } else {
                    removedComponents.add(cn);
                }
            }
        }
        return removedComponents;
    }

    @Override
    public String toString() {
        String str = "Stack Tasks (" + mStackTaskList.size() + "):\n";
        ArrayList<Task> tasks = mStackTaskList.getTasks();
        int taskCount = tasks.size();
        for (int i = 0; i < taskCount; i++) {
            str += "    " + tasks.get(i).toString() + "\n";
        }
        return str;
    }

    /**
     * Given a list of tasks, returns a map of each task's key to the task.
     */
    private ArrayMap<TaskKey, Task> createTaskKeyMapFromList(List<Task> tasks) {
        ArrayMap<TaskKey, Task> map = new ArrayMap<>(tasks.size());
        int taskCount = tasks.size();
        for (int i = 0; i < taskCount; i++) {
            Task task = tasks.get(i);
            map.put(task.key, task);
        }
        return map;
    }

    public void dump(String prefix, PrintWriter writer) {
        String innerPrefix = prefix + "  ";

        writer.print(prefix); writer.print(TAG);
        writer.print(" numStackTasks="); writer.print(mStackTaskList.size());
        writer.println();
        ArrayList<Task> tasks = mStackTaskList.getTasks();
        int taskCount = tasks.size();
        for (int i = 0; i < taskCount; i++) {
            tasks.get(i).dump(innerPrefix, writer);
        }
    }
}
