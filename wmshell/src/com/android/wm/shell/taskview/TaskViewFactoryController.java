/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.taskview;

import android.annotation.UiContext;
import android.content.Context;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.shared.annotations.ExternalThread;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Factory controller which can create {@link TaskView} */
public class TaskViewFactoryController {
    private final ShellTaskOrganizer mTaskOrganizer;
    private final ShellExecutor mShellExecutor;
    private final SyncTransactionQueue mSyncQueue;
    private final TaskViewTransitions mTaskViewTransitions;
    private final TaskViewFactory mImpl = new TaskViewFactoryImpl();

    public TaskViewFactoryController(ShellTaskOrganizer taskOrganizer,
            ShellExecutor shellExecutor, SyncTransactionQueue syncQueue,
            TaskViewTransitions taskViewTransitions) {
        mTaskOrganizer = taskOrganizer;
        mShellExecutor = shellExecutor;
        mSyncQueue = syncQueue;
        mTaskViewTransitions = taskViewTransitions;
    }

    public TaskViewFactoryController(ShellTaskOrganizer taskOrganizer,
            ShellExecutor shellExecutor, SyncTransactionQueue syncQueue) {
        mTaskOrganizer = taskOrganizer;
        mShellExecutor = shellExecutor;
        mSyncQueue = syncQueue;
        mTaskViewTransitions = null;
    }

    /**
     * @return the underlying {@link TaskViewFactory}.
     */
    public TaskViewFactory asTaskViewFactory() {
        return mImpl;
    }

    /** Creates an {@link TaskView} */
    public void create(@UiContext Context context, Executor executor, Consumer<TaskView> onCreate) {
        TaskView taskView = new TaskView(context, new TaskViewTaskController(context,
                mTaskOrganizer, mTaskViewTransitions, mSyncQueue));
        executor.execute(() -> {
            onCreate.accept(taskView);
        });
    }

    private class TaskViewFactoryImpl implements TaskViewFactory {
        @ExternalThread
        public void create(@UiContext Context context,
                Executor executor, Consumer<TaskView> onCreate) {
            mShellExecutor.execute(() -> {
                TaskViewFactoryController.this.create(context, executor, onCreate);
            });
        }
    }
}
