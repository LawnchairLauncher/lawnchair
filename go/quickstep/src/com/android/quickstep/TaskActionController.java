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

import static com.android.quickstep.TaskAdapter.TASKS_START_POSITION;
import static com.android.quickstep.TaskUtils.getLaunchComponentKeyForTask;

import android.app.ActivityOptions;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.logging.StatsLogManager;
import com.android.quickstep.views.TaskItemView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.ActivityManagerWrapper;

/**
 * Controller that provides logic for task-related commands on recents and updating the model/view
 * as appropriate.
 */
public final class TaskActionController {

    private final TaskListLoader mLoader;
    private final TaskAdapter mAdapter;
    private final StatsLogManager mStatsLogManager;

    public TaskActionController(TaskListLoader loader, TaskAdapter adapter,
            StatsLogManager logManager) {
        mLoader = loader;
        mAdapter = adapter;
        mStatsLogManager = logManager;
    }

    /**
     * Launch the task associated with the task holder, animating into the app from the task view.
     *
     * @param viewHolder the task view holder to launch
     */
    public void launchTaskFromView(@NonNull TaskHolder viewHolder) {
        if (!viewHolder.getTask().isPresent()) {
            return;
        }
        TaskItemView itemView = (TaskItemView) (viewHolder.itemView);
        View v = itemView.getThumbnailView();
        int left = 0;
        int top = 0;
        int width = v.getMeasuredWidth();
        int height = v.getMeasuredHeight();

        TaskKey key = viewHolder.getTask().get().key;
        ActivityOptions opts = ActivityOptions.makeClipRevealAnimation(v, left, top, width, height);
        ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(key, opts,
                null /* resultCallback */, null /* resultCallbackHandler */);
        mStatsLogManager.logTaskLaunch(null /* view */, getLaunchComponentKeyForTask(key));
    }

    /**
     * Launch the task directly with a basic animation.
     *
     * @param task the task to launch
     */
    public void launchTask(@NonNull Task task) {
        ActivityOptions opts = ActivityOptions.makeBasic();
        ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(task.key, opts,
                null /* resultCallback */, null /* resultCallbackHandler */);
        mStatsLogManager.logTaskLaunch(null /* view */, getLaunchComponentKeyForTask(task.key));
    }

    /**
     * Removes the task holder and the task, updating the model and the view.
     *
     * @param viewHolder the task view holder to remove
     */
    public void removeTask(TaskHolder viewHolder) {
        if (!viewHolder.getTask().isPresent()) {
            return;
        }
        int position = viewHolder.getAdapterPosition();
        Task task = viewHolder.getTask().get();
        ActivityManagerWrapper.getInstance().removeTask(task.key.id);
        mLoader.removeTask(task);
        mAdapter.notifyItemRemoved(position);
        // TODO(b/131840601): Add logging point for removal.
    }

    /**
     * Clears all tasks and updates the model and view.
     */
    public void clearAllTasks() {
        int count = mAdapter.getItemCount();
        ActivityManagerWrapper.getInstance().removeAllRecentTasks();
        mLoader.clearAllTasks();
        mAdapter.notifyItemRangeRemoved(TASKS_START_POSITION /* positionStart */, count);
    }
}
