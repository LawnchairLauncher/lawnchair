/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.quickstep.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.systemui.shared.recents.model.Task;

/**
 * A {@link Task} container that can contain one or two tasks, depending on if the two tasks
 * are represented as an app-pair in the recents task list.
 */
public class GroupTask {
    public @NonNull Task task1;
    public @Nullable Task task2;
    public @Nullable
    SplitBounds mSplitBounds;

    public GroupTask(@NonNull Task t1, @Nullable Task t2,
            @Nullable SplitBounds splitBounds) {
        task1 = t1;
        task2 = t2;
        mSplitBounds = splitBounds;
    }

    public GroupTask(@NonNull GroupTask group) {
        task1 = new Task(group.task1);
        task2 = group.task2 != null
                ? new Task(group.task2)
                : null;
        mSplitBounds = group.mSplitBounds;
    }

    public boolean containsTask(int taskId) {
        return task1.key.id == taskId || (task2 != null && task2.key.id == taskId);
    }

    public boolean hasMultipleTasks() {
        return task2 != null;
    }
}
