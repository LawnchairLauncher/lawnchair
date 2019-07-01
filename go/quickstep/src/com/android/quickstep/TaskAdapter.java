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

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.launcher3.R;
import com.android.quickstep.views.TaskItemView;
import com.android.systemui.shared.recents.model.Task;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Recycler view adapter that dynamically inflates and binds {@link TaskHolder} instances with the
 * appropriate {@link Task} from the recents task list.
 */
public final class TaskAdapter extends Adapter<ViewHolder> {

    public static final int CHANGE_EVENT_TYPE_EMPTY_TO_CONTENT = 0;
    public static final int MAX_TASKS_TO_DISPLAY = 6;
    public static final int TASKS_START_POSITION = 1;

    public static final int ITEM_TYPE_TASK = 0;
    public static final int ITEM_TYPE_CLEAR_ALL = 1;

    private static final String TAG = "TaskAdapter";
    private final TaskListLoader mLoader;
    private TaskActionController mTaskActionController;
    private OnClickListener mClearAllListener;
    private boolean mIsShowingLoadingUi;

    public TaskAdapter(@NonNull TaskListLoader loader) {
        mLoader = loader;
    }

    public void setActionController(TaskActionController taskActionController) {
        mTaskActionController = taskActionController;
    }

    public void setOnClearAllClickListener(OnClickListener listener) {
        mClearAllListener = listener;
    }

    /**
     * Sets all positions in the task adapter to loading views, binding new views if necessary.
     * This changes the task adapter's view of the data, so the appropriate notify events should be
     * called in addition to this method to reflect the changes.
     *
     * @param isShowingLoadingUi true to bind loading task views to all positions, false to return
     *                           to the real data
     */
    public void setIsShowingLoadingUi(boolean isShowingLoadingUi) {
        mIsShowingLoadingUi = isShowingLoadingUi;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case ITEM_TYPE_TASK:
                TaskItemView itemView = (TaskItemView) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.task_item_view, parent, false);
                TaskHolder taskHolder = new TaskHolder(itemView);
                itemView.setOnClickListener(
                        view -> mTaskActionController.launchTaskFromView(taskHolder));
                return taskHolder;
            case ITEM_TYPE_CLEAR_ALL:
                View clearView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.clear_all_button, parent, false);
                ClearAllHolder clearAllHolder = new ClearAllHolder(clearView);
                Button clearViewButton = clearView.findViewById(R.id.clear_all_button);
                clearViewButton.setOnClickListener(mClearAllListener);
                return clearAllHolder;
            default:
                throw new IllegalArgumentException("No known holder for item type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        onBindViewHolderInternal(holder, position, false /* willAnimate */);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }
        int changeType = (int) payloads.get(0);
        if (changeType == CHANGE_EVENT_TYPE_EMPTY_TO_CONTENT) {
            // Bind in preparation for animation
            onBindViewHolderInternal(holder, position, true /* willAnimate */);
        } else {
            throw new IllegalArgumentException("Payload content is not a valid change event type: "
                    + changeType);
        }
    }

    private void onBindViewHolderInternal(@NonNull ViewHolder holder, int position,
            boolean willAnimate) {
        int itemType = getItemViewType(position);
        switch (itemType) {
            case ITEM_TYPE_TASK:
                TaskHolder taskHolder = (TaskHolder) holder;
                if (mIsShowingLoadingUi) {
                    taskHolder.bindEmptyUi();
                    return;
                }
                List<Task> tasks = mLoader.getCurrentTaskList();
                int taskPos = position - TASKS_START_POSITION;
                if (taskPos >= tasks.size()) {
                    // Task list has updated.
                    return;
                }
                Task task = tasks.get(taskPos);
                taskHolder.bindTask(task, willAnimate /* willAnimate */);
                mLoader.loadTaskIconAndLabel(task, () -> {
                    // Ensure holder still has the same task.
                    if (Objects.equals(Optional.of(task), taskHolder.getTask())) {
                        taskHolder.getTaskItemView().setIcon(task.icon);
                        taskHolder.getTaskItemView().setLabel(task.titleDescription);
                    }
                });
                mLoader.loadTaskThumbnail(task, () -> {
                    if (Objects.equals(Optional.of(task), taskHolder.getTask())) {
                        taskHolder.getTaskItemView().setThumbnail(task.thumbnail);
                    }
                });
                break;
            case ITEM_TYPE_CLEAR_ALL:
                // Nothing to bind.
                break;
            default:
                throw new IllegalArgumentException("No known holder for item type: " + itemType);
        }
    }

    @Override
    public int getItemViewType(int position) {
        // Bottom is always clear all button.
        return (position == 0) ? ITEM_TYPE_CLEAR_ALL : ITEM_TYPE_TASK;
    }

    @Override
    public int getItemCount() {
        int itemCount = TASKS_START_POSITION;
        if (mIsShowingLoadingUi) {
            // Show loading version of all items.
            itemCount += MAX_TASKS_TO_DISPLAY;
        } else {
            itemCount += Math.min(mLoader.getCurrentTaskList().size(), MAX_TASKS_TO_DISPLAY);
        }
        return itemCount;
    }
}
