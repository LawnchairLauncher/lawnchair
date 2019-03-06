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

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.systemui.shared.recents.model.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * This class is responsible for maintaining the list of tasks and the task content. The list must
 * be updated explicitly with {@link #loadTaskList} whenever the list needs to be
 * up-to-date.
 */
public final class TaskListLoader {

    private final RecentsModel mRecentsModel;

    private ArrayList<Task> mTaskList = new ArrayList<>();
    private int mTaskListChangeId;

    public TaskListLoader(Context context) {
        mRecentsModel = RecentsModel.INSTANCE.get(context);
    }

    /**
     * Returns the current task list as of the last completed load (see
     * {@link #loadTaskList}). This list of tasks is guaranteed to always have all its task
     * content loaded.
     *
     * @return the current list of tasks w/ all content loaded
     */
    public ArrayList<Task> getCurrentTaskList() {
        return mTaskList;
    }

    /**
     * Fetches the most recent tasks and updates the task list asynchronously. In addition it
     * loads the content for each task (icon and label). The callback and task list being updated
     * only occur when all task content is fully loaded and up-to-date.
     *
     * @param onTasksLoadedCallback callback for when the tasks are fully loaded. Done on the UI
     *                              thread
     */
    public void loadTaskList(@Nullable Consumer<ArrayList<Task>> onTasksLoadedCallback) {
        if (mRecentsModel.isTaskListValid(mTaskListChangeId)) {
            // Current task list is already up to date. No need to update.
            if (onTasksLoadedCallback != null) {
                onTasksLoadedCallback.accept(mTaskList);
            }
            return;
        }
        // TODO: Look into error checking / more robust handling for when things go wrong.
        mTaskListChangeId = mRecentsModel.getTasks(tasks -> {
            // Reverse tasks to put most recent at the bottom of the view
            Collections.reverse(tasks);
            // Load task content
            loadTaskContents(tasks, () -> {
                mTaskList = tasks;
                if (onTasksLoadedCallback != null) {
                    onTasksLoadedCallback.accept(mTaskList);
                }
            });
        });
    }

    /**
     * Loads task content for a list of tasks, including the label and the icon. Uses the list of
     * tasks since the last load as a cache for loaded content.
     *
     * @param tasksToLoad list of tasks that need to load their content
     * @param onLoadedCallback runnable to run after all tasks have loaded their content
     */
    private void loadTaskContents(ArrayList<Task> tasksToLoad,
            @Nullable Runnable onLoadedCallback) {
        AtomicInteger loadRequestsCount = new AtomicInteger(0);
        for (Task task : tasksToLoad) {
            int index = mTaskList.indexOf(task);
            if (index >= 0) {
                // If we've already loaded the task and have its content then just copy it over.
                Task loadedTask = mTaskList.get(index);
                task.titleDescription = loadedTask.titleDescription;
                task.icon = loadedTask.icon;
            } else {
                // Otherwise, load the content in the background.
                loadRequestsCount.getAndIncrement();
                mRecentsModel.getIconCache().updateIconInBackground(task, loadedTask -> {
                    if (loadRequestsCount.decrementAndGet() == 0 && onLoadedCallback != null) {
                        onLoadedCallback.run();
                    }
                });
            }
        }
        if (loadRequestsCount.get() == 0 && onLoadedCallback != null) {
            onLoadedCallback.run();
        }
    }
}
