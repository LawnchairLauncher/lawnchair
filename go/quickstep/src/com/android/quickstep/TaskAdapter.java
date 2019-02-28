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

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView.Adapter;

import com.android.systemui.shared.recents.model.Task;

import java.util.ArrayList;

/**
 * Recycler view adapter that dynamically inflates and binds {@link TaskHolder} instances with the
 * appropriate {@link Task} from the recents task list.
 */
public final class TaskAdapter extends Adapter<TaskHolder> {

    private static final int MAX_TASKS_TO_DISPLAY = 6;
    private static final String TAG = "TaskAdapter";
    private final TaskListLoader mLoader;

    public TaskAdapter(@NonNull TaskListLoader loader) {
        mLoader = loader;
    }

    @Override
    public TaskHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // TODO: Swap in an actual task view here (view w/ icon, label, etc.)
        TextView stubView = new TextView(parent.getContext());
        return new TaskHolder(stubView);
    }

    @Override
    public void onBindViewHolder(TaskHolder holder, int position) {
        ArrayList<Task> tasks = mLoader.getCurrentTaskList();
        if (position >= tasks.size()) {
            // Task list has updated.
            return;
        }
        holder.bindTask(tasks.get(position));
    }

    @Override
    public int getItemCount() {
        return Math.min(mLoader.getCurrentTaskList().size(), MAX_TASKS_TO_DISPLAY);
    }
}
