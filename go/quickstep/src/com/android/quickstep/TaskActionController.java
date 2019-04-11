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

import android.app.ActivityOptions;
import android.view.View;

import com.android.quickstep.views.TaskItemView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;

/**
 * Controller that provides logic for task-related commands on recents and updating the model/view
 * as appropriate.
 */
public final class TaskActionController {

    private final TaskListLoader mLoader;
    private final TaskAdapter mAdapter;

    public TaskActionController(TaskListLoader loader, TaskAdapter adapter) {
        mLoader = loader;
        mAdapter = adapter;
    }

    /**
     * Launch the task associated with the task holder, animating into the app.
     *
     * @param viewHolder the task view holder to launch
     */
    public void launchTask(TaskHolder viewHolder) {
        if (viewHolder.getTask() == null) {
            return;
        }
        TaskItemView itemView = (TaskItemView) (viewHolder.itemView);
        View v = itemView.getThumbnailView();
        int left = 0;
        int top = 0;
        int width = v.getMeasuredWidth();
        int height = v.getMeasuredHeight();

        ActivityOptions opts = ActivityOptions.makeClipRevealAnimation(v, left, top, width, height);
        ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(viewHolder.getTask().key,
                opts, null /* resultCallback */, null /* resultCallbackHandler */);
    }

    /**
     * Removes the task holder and the task, updating the model and the view.
     *
     * @param viewHolder the task view holder to remove
     */
    public void removeTask(TaskHolder viewHolder) {
        if (viewHolder.getTask() == null) {
            return;
        }
        int position = viewHolder.getAdapterPosition();
        Task task = viewHolder.getTask();
        ActivityManagerWrapper.getInstance().removeTask(task.key.id);
        mLoader.removeTask(task);
        mAdapter.notifyItemRemoved(position);
    }

    /**
     * Clears all tasks and updates the model and view.
     */
    public void clearAllTasks() {
        int count = mAdapter.getItemCount();
        ActivityManagerWrapper.getInstance().removeAllRecentTasks();
        mLoader.clearAllTasks();
        mAdapter.notifyItemRangeRemoved(0 /* positionStart */, count);
    }
}
