/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import android.app.Activity;
import android.app.ActivityManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.util.ActivityLifecycleCallbacksAdapter;
import com.android.quickstep.RecentsModel;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

/**
 * This class tracks the failure of a task launch through the Launcher.startActivitySafely() call,
 * in an edge case in which a task may already be visible on screen (ie. in PIP) and no transition
 * will be run in WM, which results in expected callbacks to not be processed.
 *
 * We transiently register a task stack listener during a task launch and if the restart signal is
 * received, then the registered callback will be notified.
 */
public class TaskRestartedDuringLaunchListener implements TaskStackChangeListener {

    private static final String TAG = "TaskRestartedDuringLaunchListener";

    private @NonNull Runnable mTaskRestartedCallback = null;

    /**
     * Registers a failure listener callback if it detects a scenario in which an app launch
     * resulted in an already existing task to be "restarted".
     */
    public void register(@NonNull Runnable taskRestartedCallback) {
        TaskStackChangeListeners.getInstance().registerTaskStackListener(this);
        mTaskRestartedCallback = taskRestartedCallback;
    }

    /**
     * Unregisters the failure listener.
     */
    public void unregister() {
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(this);
        mTaskRestartedCallback = null;
    }

    @Override
    public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
            boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
        if (wasVisible) {
            Log.d(TAG, "Detected activity restart during launch for task=" + task.taskId);
            mTaskRestartedCallback.run();
            unregister();
        }
    }
}
