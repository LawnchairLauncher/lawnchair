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

import android.app.ActivityManager.TaskSnapshot;
import android.content.ComponentName;
import android.os.UserHandle;
import android.util.Log;

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
    public void onPinnedActivityRestartAttempt(boolean clearedTask) { }
    public void onPinnedStackAnimationStarted() { }
    public void onPinnedStackAnimationEnded() { }
    public void onActivityForcedResizable(String packageName, int taskId, int reason) { }
    public void onActivityDismissingDockedStack() { }
    public void onActivityLaunchOnSecondaryDisplayFailed() { }
    public void onTaskProfileLocked(int taskId, int userId) { }
    public void onTaskCreated(int taskId, ComponentName componentName) { }
    public void onTaskRemoved(int taskId) { }
    public void onTaskMovedToFront(int taskId) { }
    public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation) { }

    /**
     * Checks that the current user matches the process. Since
     * {@link android.app.ITaskStackListener} is not multi-user aware, handlers of
     * {@link TaskStackChangeListener} should make this call to verify that we don't act on events
     * originating from another user's interactions.
     */
    protected final boolean checkCurrentUserId(int currentUserId, boolean debug) {
        int processUserId = UserHandle.myUserId();
        if (processUserId != currentUserId) {
            if (debug) {
                Log.d("TaskStackChangeListener", "UID mismatch. Process is uid=" + processUserId
                        + " and the current user is uid=" + currentUserId);
            }
            return false;
        }
        return true;
    }
}
