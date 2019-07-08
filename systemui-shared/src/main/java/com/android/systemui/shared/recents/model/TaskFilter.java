/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.recents.model;

import android.util.SparseArray;

/**
 * An interface for a task filter to query whether a particular task should show in a stack.
 */
public interface TaskFilter {
    /** Returns whether the filter accepts the specified task */
    boolean acceptTask(SparseArray<Task> taskIdMap, Task t, int index);
}
