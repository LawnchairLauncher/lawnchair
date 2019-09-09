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

import java.util.Optional;

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
     * Bind the task model to the holder. This will take the current task content in the task
     * object (i.e. icon, thumbnail, label) and either apply the content immediately or simply bind
     * the content to animate to at a later time. If the task does not have all its content loaded,
     * the view will prepare appropriate default placeholders and it is the callers responsibility
     * to change them at a later time.
     *
     * Regardless of whether it is animating, input handlers will be bound immediately (see
     * {@link TaskActionController}).
     *
     * @param task the task to bind to the view
     * @param willAnimate true if UI should animate in later, false if it should apply immediately
     */
    public void bindTask(@NonNull Task task, boolean willAnimate) {
        mTask = task;
        if (willAnimate) {
            mTaskItemView.startContentAnimation(task.icon, task.thumbnail, task.titleDescription);
        } else {
            mTaskItemView.setIcon(task.icon);
            mTaskItemView.setThumbnail(task.thumbnail);
            mTaskItemView.setLabel(task.titleDescription);
        }
    }

    /**
     * Bind a generic empty UI to the holder to make it clear that the item is loading/unbound and
     * should not be expected to react to user input.
     */
    public void bindEmptyUi() {
        mTask = null;
        mTaskItemView.resetToEmptyUi();
    }

    /**
     * Gets the task currently bound to this view. May be null if task holder is in a loading state.
     *
     * @return the current task
     */
    public Optional<Task> getTask() {
        return Optional.ofNullable(mTask);
    }
}
