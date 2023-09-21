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

import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * An interface to track task stack changes. Classes should implement this instead of
 * {@link android.app.ITaskStackListener} to reduce IPC calls from system services.
 */
public interface TaskStackChangeListener {

    // Binder thread callbacks
    default void onTaskStackChangedBackground() { }

    // Main thread callbacks
    default void onTaskStackChanged() { }

    /**
     * @return whether the snapshot is consumed and the lifecycle of the snapshot extends beyond
     *         the lifecycle of this callback.
     */
    default boolean onTaskSnapshotChanged(int taskId, ThumbnailData snapshot) {
        return false;
    }
    default void onActivityPinned(String packageName, int userId, int taskId, int stackId) { }
    default void onActivityUnpinned() { }
    default void onActivityRestartAttempt(RunningTaskInfo task, boolean homeTaskVisible,
            boolean clearedTask, boolean wasVisible) { }
    default void onActivityForcedResizable(String packageName, int taskId, int reason) { }
    default void onActivityDismissingDockedStack() { }
    default void onActivityLaunchOnSecondaryDisplayFailed() { }

    default void onActivityLaunchOnSecondaryDisplayFailed(RunningTaskInfo taskInfo) {
        onActivityLaunchOnSecondaryDisplayFailed();
    }

    /**
     * @see #onActivityLaunchOnSecondaryDisplayRerouted(RunningTaskInfo taskInfo)
     */
    default void onActivityLaunchOnSecondaryDisplayRerouted() { }

    /**
     * Called when an activity was requested to be launched on a secondary display but was rerouted
     * to default display.
     *
     * @param taskInfo info about the Activity's task
     */
    default void onActivityLaunchOnSecondaryDisplayRerouted(RunningTaskInfo taskInfo) {
        onActivityLaunchOnSecondaryDisplayRerouted();
    }

    default void onTaskProfileLocked(RunningTaskInfo taskInfo) { }
    default void onTaskCreated(int taskId, ComponentName componentName) { }
    default void onTaskRemoved(int taskId) { }
    default void onTaskMovedToFront(int taskId) { }

    default void onTaskMovedToFront(RunningTaskInfo taskInfo) {
        onTaskMovedToFront(taskInfo.taskId);
    }

    /**
     * Called when a task’s description is changed due to an activity calling
     * ActivityManagerService.setTaskDescription
     *
     * @param taskInfo info about the task which changed, with
     * {@link RunningTaskInfo#taskDescription}
     */
    default void onTaskDescriptionChanged(RunningTaskInfo taskInfo) { }

    default void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation) { }

    default void onBackPressedOnTaskRoot(RunningTaskInfo taskInfo) { }

    /**
     * Called when a task is reparented to a stack on a different display.
     *
     * @param taskId id of the task which was moved to a different display.
     * @param newDisplayId id of the new display.
     */
    default void onTaskDisplayChanged(int taskId, int newDisplayId) { }

    /**
     * Called when any additions or deletions to the recent tasks list have been made.
     */
    default void onRecentTaskListUpdated() { }

    /** @see ITaskStackListener#onRecentTaskListFrozenChanged(boolean) */
    default void onRecentTaskListFrozenChanged(boolean frozen) { }

    /** @see ITaskStackListener#onActivityRotation(int)*/
    default void onActivityRotation(int displayId) { }

    /**
     * Called when the lock task mode changes. See ActivityManager#LOCK_TASK_MODE_* and
     * LockTaskController.
     */
    default void onLockTaskModeChanged(int mode) { }
}
