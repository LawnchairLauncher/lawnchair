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
 * limitations under the License.
 */

package com.android.systemui.shared.system;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.ITaskStackListener;
import android.content.ComponentName;
import android.os.IBinder;

import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * An interface to track task stack changes. Classes should implement this instead of
 * {@link android.app.ITaskStackListener} to reduce IPC calls from system services.
 */
public abstract class TaskStackChangeListener {

    // Binder thread callbacks
    public void onTaskStackChangedBackground() { }

    // Main thread callbacks
    public void onTaskStackChanged() { }
    public void onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) { }
    public void onActivityPinned(String packageName, int userId, int taskId, int stackId) { }
    public void onActivityUnpinned() { }
    public void onActivityRestartAttempt(RunningTaskInfo task, boolean homeTaskVisible,
            boolean clearedTask, boolean wasVisible) { }
    public void onActivityForcedResizable(String packageName, int taskId, int reason) { }
    public void onActivityDismissingDockedStack() { }
    public void onActivityLaunchOnSecondaryDisplayFailed() { }

    public void onActivityLaunchOnSecondaryDisplayFailed(RunningTaskInfo taskInfo) {
        onActivityLaunchOnSecondaryDisplayFailed();
    }

    /**
     * @see #onActivityLaunchOnSecondaryDisplayRerouted(RunningTaskInfo taskInfo)
     */
    public void onActivityLaunchOnSecondaryDisplayRerouted() { }

    /**
     * Called when an activity was requested to be launched on a secondary display but was rerouted
     * to default display.
     *
     * @param taskInfo info about the Activity's task
     */
    public void onActivityLaunchOnSecondaryDisplayRerouted(RunningTaskInfo taskInfo) {
        onActivityLaunchOnSecondaryDisplayRerouted();
    }

    /**
     * Called when contents are drawn for the first time on a display which can only contain one
     * task.
     *
     * @param displayId the id of the display on which contents are drawn.
     */
    public void onSingleTaskDisplayDrawn(int displayId) { }

    /**
     * Called when the last task is removed from a display which can only contain one task.
     *
     * @param displayId the id of the display from which the window is removed.
     */
    public void onSingleTaskDisplayEmpty(int displayId) {}

    public void onTaskProfileLocked(int taskId, int userId) { }
    public void onTaskCreated(int taskId, ComponentName componentName) { }
    public void onTaskRemoved(int taskId) { }
    public void onTaskMovedToFront(int taskId) { }

    public void onTaskMovedToFront(RunningTaskInfo taskInfo) {
        onTaskMovedToFront(taskInfo.taskId);
    }

    /**
     * Called when a task’s description is changed due to an activity calling
     * ActivityManagerService.setTaskDescription
     *
     * @param taskInfo info about the task which changed, with {@link TaskInfo#taskDescription}
     */
    public void onTaskDescriptionChanged(RunningTaskInfo taskInfo) { }

    public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation) { }
    public void onSizeCompatModeActivityChanged(int displayId, IBinder activityToken) { }

    public void onBackPressedOnTaskRoot(RunningTaskInfo taskInfo) { }

    /**
     * Called when a task is reparented to a stack on a different display.
     *
     * @param taskId id of the task which was moved to a different display.
     * @param newDisplayId id of the new display.
     */
    public void onTaskDisplayChanged(int taskId, int newDisplayId) { }

    /**
     * Called when any additions or deletions to the recent tasks list have been made.
     */
    public void onRecentTaskListUpdated() { }

    /** @see ITaskStackListener#onRecentTaskListFrozenChanged(boolean) */
    public void onRecentTaskListFrozenChanged(boolean frozen) { }

    /** @see ITaskStackListener#onActivityRotation(int)*/
    public void onActivityRotation(int displayId) { }
}
