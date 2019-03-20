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
 * Controller responsible for task logic that occurs on various input to the recents view.
 */
public final class TaskInputController {

    private final TaskListLoader mLoader;
    private final TaskAdapter mAdapter;

    public TaskInputController(TaskListLoader loader,TaskAdapter adapter) {
        mLoader = loader;
        mAdapter = adapter;
    }

    /**
     * Logic that occurs when a task view is tapped. Launches the respective task.
     *
     * @param viewHolder the task view holder that has been tapped
     */
    public void onTaskClicked(TaskHolder viewHolder) {
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

    public void onTaskSwiped(TaskHolder viewHolder) {
        int position = viewHolder.getAdapterPosition();
        Task task = viewHolder.getTask();
        ActivityManagerWrapper.getInstance().removeTask(task.key.id);
        mLoader.removeTask(task);
        mAdapter.notifyItemRemoved(position);
    }

    // TODO: Implement "Clear all" and notify adapter that data has updated

}
