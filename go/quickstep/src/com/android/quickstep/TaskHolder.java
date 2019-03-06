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

import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.systemui.shared.recents.model.Task;

/**
 * A recycler view holder that holds the task view and binds {@link Task} content (app title, icon,
 * etc.) to the view.
 */
final class TaskHolder extends ViewHolder {

    // TODO: Implement the actual task view to be held.
    // For now, we just use a simple text view.
    private final TextView mStubView;

    public TaskHolder(TextView stubView) {
        super(stubView);
        mStubView = stubView;
    }

    /**
     * Bind task content to the view. This includes the task icon and title as well as binding
     * input handlers such as which task to launch/remove.
     *
     * @param task the task to bind to the view this
     */
    public void bindTask(Task task) {
        mStubView.setText("Stub task view: " + task.titleDescription);
    }
}
