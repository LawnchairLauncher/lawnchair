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

import com.android.systemui.shared.system.ActivityManagerWrapper;

/**
 * Controller responsible for task logic that occurs on various input to the recents view.
 */
public final class TaskInputController {

    TaskAdapter mAdapter;

    public TaskInputController(TaskAdapter adapter) {
        mAdapter = adapter;
    }

    /**
     * Logic that occurs when a task view is tapped. Launches the respective task.
     *
     * @param viewHolder the task view holder that has been tapped
     */
    public void onTaskClicked(TaskHolder viewHolder) {
        // TODO: Add app launch animation as part of the launch options here.
        ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(viewHolder.getTask().key,
                null /* options */, null /* resultCallback */, null /* resultCallbackHandler */);
    }

    // TODO: Implement swipe to delete and notify adapter that data has updated

    // TODO: Implement "Clear all" and notify adapter that data has updated
}
