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

import static com.android.launcher3.BaseActivity.EVENT_DESTROYED;
import static com.android.launcher3.BaseActivity.EVENT_RESUMED;
import static com.android.launcher3.BaseActivity.EVENT_STOPPED;

import androidx.annotation.NonNull;

import com.android.launcher3.BaseActivity;
import com.android.quickstep.RecentsModel;

/**
 * This class tracks the failure of a task launch through the TaskView.launchTask() call, in an
 * edge case in which starting a new task may initially succeed (startActivity returns true), but
 * the launch ultimately fails if the activity finishes while it is resuming.
 *
 * There are two signals this class checks, the launcher lifecycle and the transition completion.
 * If we hit either of those signals and the task is no longer valid, then the registered failure
 * callback will be notified.
 */
public class TaskRemovedDuringLaunchListener {

    private BaseActivity mActivity;
    private int mLaunchedTaskId = INVALID_TASK_ID;
    private Runnable mTaskLaunchFailedCallback = null;

    private final Runnable mUnregisterCallback = this::unregister;
    private final Runnable mResumeCallback = this::checkTaskLaunchFailed;

    /**
     * Registers a failure listener callback if it detects a scenario in which an app launch
     * failed before the transition finished.
     */
    public void register(BaseActivity activity, int launchedTaskId,
            @NonNull Runnable taskLaunchFailedCallback) {
        // The normal task launch case, Launcher stops and updates its state correctly
        activity.addEventCallback(EVENT_STOPPED, mUnregisterCallback);
        // The transition hasn't finished but Launcher was resumed, check if the launch failed
        activity.addEventCallback(EVENT_RESUMED, mResumeCallback);
        // If we somehow don't get any of the above signals, then just unregister this listener
        activity.addEventCallback(EVENT_DESTROYED, mUnregisterCallback);

        mActivity = activity;
        mLaunchedTaskId = launchedTaskId;
        mTaskLaunchFailedCallback = taskLaunchFailedCallback;
    }

    /**
     * Unregisters the failure listener.
     */
    private void unregister() {
        mActivity.removeEventCallback(EVENT_STOPPED, mUnregisterCallback);
        mActivity.removeEventCallback(EVENT_RESUMED, mResumeCallback);
        mActivity.removeEventCallback(EVENT_DESTROYED, mUnregisterCallback);

        mActivity = null;
        mLaunchedTaskId = INVALID_TASK_ID;
        mTaskLaunchFailedCallback = null;
    }

    /**
     * Called when the transition finishes.
     */
    public void onTransitionFinished() {
        // The transition finished and Launcher was not stopped, check if the launch failed
        checkTaskLaunchFailed();
    }

    private void checkTaskLaunchFailed() {
        if (mLaunchedTaskId != INVALID_TASK_ID) {
            final int launchedTaskId = mLaunchedTaskId;
            final Runnable taskLaunchFailedCallback = mTaskLaunchFailedCallback;
            RecentsModel.INSTANCE.getNoCreate().isTaskRemoved(mLaunchedTaskId, (taskRemoved) -> {
                if (taskRemoved) {
                    ActiveGestureLog.INSTANCE.addLog(
                            new ActiveGestureLog.CompoundString("Launch failed, task (id=")
                                    .append(launchedTaskId)
                                    .append(") finished mid transition"));
                    taskLaunchFailedCallback.run();
                }
            }, (task) -> true /* filter */);
            unregister();
        }
    }
}
