/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.annotation.NonNull;
import android.os.UserHandle;

import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * Listener for receiving various task properties changes
 */
public interface TaskVisualsChangeListener {

    /**
     * Called when the task thumbnail changes
     */
    default Task onTaskThumbnailChanged(int taskId, ThumbnailData thumbnailData) {
        return null;
    }

    /**
     * Called when the icon for a task changes
     */
    default void onTaskIconChanged(@NonNull String pkg, @NonNull UserHandle user) {}

    /**
     * Called when the icon for a task changes
     */
    default void onTaskIconChanged(int taskId) {}
}
