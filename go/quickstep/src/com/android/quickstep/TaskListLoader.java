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
import java.util.List;
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
     * Returns the current task list as of the last completed load (see {@link #loadTaskList}) as a
     * read-only list. This list of tasks is not guaranteed to have all content loaded.
     *
     * @return the current list of tasks
     */
    public List<Task> getCurrentTaskList() {
        return Collections.unmodifiableList(mTaskList);
    }

    /**
     * Whether or not the loader needs to load data to be up to date. This can return true if the
     * task list is already up to date OR there is already a load in progress for the task list to
     * become up to date.
     *
     * @return true if already up to date or load in progress, false otherwise
     */
    public boolean needsToLoad() {
        return !mRecentsModel.isTaskListValid(mTaskListChangeId);
    }

    /**
     * Fetches the most recent tasks and updates the task list asynchronously. This call does not
     * provide guarantees the task content (icon, thumbnail, label) are loaded but will fill in
     * what it has. May run the callback immediately if there have been no changes in the task
     * list since the start of the last load.
     *
     * @param onLoadedCallback callback to run when task list is loaded
     */
    public void loadTaskList(@Nullable Consumer<ArrayList<Task>> onLoadedCallback) {
        if (!needsToLoad()) {
            if (onLoadedCallback != null) {
                onLoadedCallback.accept(mTaskList);
            }
            return;
        }
        // TODO: Look into error checking / more robust handling for when things go wrong.
        mTaskListChangeId = mRecentsModel.getTasks(loadedTasks -> {
            ArrayList<Task> tasks = new ArrayList<>(loadedTasks);
            // Reverse tasks to put most recent at the bottom of the view
            Collections.reverse(tasks);
            // Load task content
            for (Task task : tasks) {
                int loadedPos = mTaskList.indexOf(task);
                if (loadedPos == -1) {
                    continue;
                }
                Task loadedTask = mTaskList.get(loadedPos);
                task.icon = loadedTask.icon;
                task.titleDescription = loadedTask.titleDescription;
                task.thumbnail = loadedTask.thumbnail;
            }
            mTaskList = tasks;
            onLoadedCallback.accept(tasks);
        });
    }

    /**
     * Load task icon and label asynchronously if it is not already loaded in the task. If the task
     * already has an icon, this calls the callback immediately.
     *
     * @param task task to update with icon + label
     * @param onLoadedCallback callback to run when task has icon and label
     */
    public void loadTaskIconAndLabel(Task task, @Nullable Runnable onLoadedCallback) {
        mRecentsModel.getIconCache().updateIconInBackground(task,
                loadedTask -> onLoadedCallback.run());
    }

    /**
     * Load thumbnail asynchronously if not already loaded in the task. If the task already has a
     * thumbnail or if the thumbnail is cached, this calls the callback immediately.
     *
     * @param task task to update with the thumbnail
     * @param onLoadedCallback callback to run when task has thumbnail
     */
    public void loadTaskThumbnail(Task task, @Nullable Runnable onLoadedCallback) {
        mRecentsModel.getThumbnailCache().updateThumbnailInBackground(task,
                thumbnail -> onLoadedCallback.run());
    }

    /**
     * Removes the task from the current task list.
     */
    void removeTask(Task task) {
        mTaskList.remove(task);
    }

    /**
     * Clears the current task list.
     */
    void clearAllTasks() {
        mTaskList.clear();
    }
}
