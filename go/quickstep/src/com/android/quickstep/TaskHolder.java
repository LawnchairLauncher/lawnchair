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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.quickstep.views.TaskItemView;
import com.android.systemui.shared.recents.model.Task;

/**
 * A recycler view holder that holds the task view and binds {@link Task} content (app title, icon,
 * etc.) to the view.
 */
public final class TaskHolder extends ViewHolder {

    private final TaskItemView mTaskItemView;
    private Task mTask;

    public TaskHolder(TaskItemView itemView) {
        super(itemView);
        mTaskItemView = itemView;
    }

    public TaskItemView getTaskItemView() {
        return mTaskItemView;
    }

    /**
     * Bind a task to the holder, resetting the view and preparing it for content to load in.
     *
     * @param task the task to bind to the view
     */
    public void bindTask(Task task) {
        mTask = task;
        mTaskItemView.resetTaskItemView();
    }

    /**
     * Gets the task currently bound to this view
     *
     * @return the current task
     */
    public @NonNull Task getTask() {
        return mTask;
    }
}
