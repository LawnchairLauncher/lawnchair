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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

/**
 * Layout manager for task list that restricts child height based off the max number of tasks the
 * recycler view should hold and the height of the recycler view.
 */
public final class TaskLayoutManager extends LinearLayoutManager {

    public TaskLayoutManager(Context context, int vertical, boolean b) {
        super(context, vertical, b);
    }

    @Override
    public void measureChildWithMargins(@NonNull View child, int widthUsed, int heightUsed) {
        // Request child view takes up 1 / MAX_TASKS of the total view height.
        int heightUsedByView = (int) (getHeight() *
                (TaskAdapter.MAX_TASKS_TO_DISPLAY - 1.0f) / TaskAdapter.MAX_TASKS_TO_DISPLAY);
        super.measureChildWithMargins(child, widthUsed, heightUsedByView);
    }
}
