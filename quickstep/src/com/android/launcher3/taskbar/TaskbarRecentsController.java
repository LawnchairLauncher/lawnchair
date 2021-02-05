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
package com.android.launcher3.taskbar;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.util.CancellableTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.util.ArrayList;

/**
 * Works with TaskbarController to update the TaskbarView's Recent items.
 */
public class TaskbarRecentsController {

    private final int mNumRecentIcons = 2;
    private final BaseQuickstepLauncher mLauncher;
    private final TaskbarController.TaskbarRecentsControllerCallbacks mTaskbarCallbacks;
    private final RecentsModel mRecentsModel;

    private final TaskStackChangeListener mTaskStackChangeListener = new TaskStackChangeListener() {
        @Override
        public void onTaskStackChanged() {
            reloadRecentTasksIfNeeded();
        }
    };

    // TODO: add TaskbarVisualsChangedListener as well (for calendar/clock?)

    // Used to keep track of the last requested task list id, so that we do not request to load the
    // tasks again if we have already requested it and the task list has not changed
    private int mTaskListChangeId = -1;

    // The current background requests to load the task icons
    private CancellableTask[] mIconLoadRequests = new CancellableTask[mNumRecentIcons];

    public TaskbarRecentsController(BaseQuickstepLauncher launcher,
            TaskbarController.TaskbarRecentsControllerCallbacks taskbarCallbacks) {
        mLauncher = launcher;
        mTaskbarCallbacks = taskbarCallbacks;
        mRecentsModel = RecentsModel.INSTANCE.get(mLauncher);
    }

    protected void init() {
        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackChangeListener);
        reloadRecentTasksIfNeeded();
    }

    protected void cleanup() {
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                mTaskStackChangeListener);
        cancelAllPendingIconLoadTasks();
    }

    private void reloadRecentTasksIfNeeded() {
        if (!mRecentsModel.isTaskListValid(mTaskListChangeId)) {
            mTaskListChangeId = mRecentsModel.getTasks(this::onRecentTasksChanged);
        }
    }

    private void cancelAllPendingIconLoadTasks() {
        for (int i = 0; i < mIconLoadRequests.length; i++) {
            if (mIconLoadRequests[i] != null) {
                mIconLoadRequests[i].cancel();
            }
            mIconLoadRequests[i] = null;
        }
    }

    private void onRecentTasksChanged(ArrayList<Task> tasks) {
        mTaskbarCallbacks.updateRecentItems(tasks);
    }

    /**
     * For each Task, loads its icon from the cache in the background, then calls
     * {@link TaskbarController.TaskbarRecentsControllerCallbacks#updateRecentTaskAtIndex}.
     */
    protected void loadIconsForTasks(Task[] tasks) {
        cancelAllPendingIconLoadTasks();
        for (int i = 0; i < tasks.length; i++) {
            Task task = tasks[i];
            if (task == null) {
                continue;
            }
            final int taskIndex = i;
            mIconLoadRequests[i] = mRecentsModel.getIconCache().updateIconInBackground(
                    task, updatedTask -> onTaskIconLoaded(task, taskIndex));
        }
    }

    private void onTaskIconLoaded(Task task, int taskIndex) {
        mTaskbarCallbacks.updateRecentTaskAtIndex(taskIndex, task);
    }

    protected int getNumRecentIcons() {
        return mNumRecentIcons;
    }
}
