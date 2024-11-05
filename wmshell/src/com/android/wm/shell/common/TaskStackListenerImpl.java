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

package com.android.wm.shell.common;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.util.Log;
import android.window.TaskSnapshot;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a {@link android.app.TaskStackListener}.
 */
public class TaskStackListenerImpl extends TaskStackListener implements Handler.Callback {
    private static final String TAG = TaskStackListenerImpl.class.getSimpleName();

    private static final int ON_TASK_STACK_CHANGED = 1;
    private static final int ON_TASK_SNAPSHOT_CHANGED = 2;
    private static final int ON_ACTIVITY_PINNED = 3;
    private static final int ON_ACTIVITY_RESTART_ATTEMPT = 4;
    private static final int ON_ACTIVITY_FORCED_RESIZABLE = 5;
    private static final int ON_ACTIVITY_DISMISSING_DOCKED_STACK = 6;
    private static final int ON_TASK_PROFILE_LOCKED = 7;
    private static final int ON_ACTIVITY_UNPINNED = 8;
    private static final int ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_FAILED = 9;
    private static final int ON_TASK_CREATED = 10;
    private static final int ON_TASK_REMOVED = 11;
    private static final int ON_TASK_MOVED_TO_FRONT = 12;
    private static final int ON_ACTIVITY_REQUESTED_ORIENTATION_CHANGE = 13;
    private static final int ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_REROUTED = 14;
    private static final int ON_BACK_PRESSED_ON_TASK_ROOT = 15;
    private static final int ON_TASK_DISPLAY_CHANGED = 16;
    private static final int ON_TASK_LIST_UPDATED = 17;
    private static final int ON_TASK_LIST_FROZEN_UNFROZEN = 18;
    private static final int ON_TASK_DESCRIPTION_CHANGED = 19;
    private static final int ON_ACTIVITY_ROTATION = 20;

    /**
     * List of {@link TaskStackListenerCallback} registered from {@link #addListener}.
     */
    private final List<TaskStackListenerCallback> mTaskStackListeners = new ArrayList<>();
    private final List<TaskStackListenerCallback> mTmpListeners = new ArrayList<>();

    private final IActivityTaskManager mActivityTaskManager;
    // NOTE: In this case we do want to use a handler since we rely on the message system to
    // efficiently dedupe sequential calls
    private Handler mMainHandler;

    public TaskStackListenerImpl(Handler mainHandler) {
        mActivityTaskManager = ActivityTaskManager.getService();
        mMainHandler = new Handler(mainHandler.getLooper(), this);
    }

    @VisibleForTesting
    TaskStackListenerImpl(IActivityTaskManager activityTaskManager) {
        mActivityTaskManager = activityTaskManager;
    }

    @VisibleForTesting
    void setHandler(Handler mainHandler) {
        mMainHandler = mainHandler;
    }

    public void addListener(TaskStackListenerCallback listener) {
        final boolean wasEmpty;
        synchronized (mTaskStackListeners) {
            wasEmpty = mTaskStackListeners.isEmpty();
            mTaskStackListeners.add(listener);
        }
        if (wasEmpty) {
            // Register mTaskStackListener to IActivityManager only once if needed.
            try {
                mActivityTaskManager.registerTaskStackListener(this);
            } catch (Exception e) {
                Log.w(TAG, "Failed to call registerTaskStackListener", e);
            }
        }
    }

    public void removeListener(TaskStackListenerCallback listener) {
        final boolean wasEmpty;
        final boolean isEmpty;
        synchronized (mTaskStackListeners) {
            wasEmpty = mTaskStackListeners.isEmpty();
            mTaskStackListeners.remove(listener);
            isEmpty = mTaskStackListeners.isEmpty();
        }
        if (!wasEmpty && isEmpty) {
            // Unregister mTaskStackListener once we have no more listeners
            try {
                mActivityTaskManager.unregisterTaskStackListener(this);
            } catch (Exception e) {
                Log.w(TAG, "Failed to call unregisterTaskStackListener", e);
            }
        }
    }

    @Override
    public void onRecentTaskListUpdated() {
        mMainHandler.obtainMessage(ON_TASK_LIST_UPDATED).sendToTarget();
    }

    @Override
    public void onRecentTaskListFrozenChanged(boolean frozen) {
        mMainHandler.obtainMessage(ON_TASK_LIST_FROZEN_UNFROZEN, frozen ? 1 : 0,
                0 /* unused */).sendToTarget();
    }

    @Override
    public void onTaskStackChanged() {
        // Call the task changed callback for the non-ui thread listeners first. Copy to a set
        // of temp listeners so that we don't lock on mTaskStackListeners while calling all the
        // callbacks. This call is always on the same binder thread, so we can just synchronize
        // on the copying of the listener list.
        synchronized (mTaskStackListeners) {
            mTmpListeners.addAll(mTaskStackListeners);
        }
        for (int i = mTmpListeners.size() - 1; i >= 0; i--) {
            mTmpListeners.get(i).onTaskStackChangedBackground();
        }
        mTmpListeners.clear();

        mMainHandler.removeMessages(ON_TASK_STACK_CHANGED);
        mMainHandler.sendEmptyMessage(ON_TASK_STACK_CHANGED);
    }

    @Override
    public void onTaskProfileLocked(ActivityManager.RunningTaskInfo taskInfo, int userId) {
        mMainHandler.obtainMessage(ON_TASK_PROFILE_LOCKED, userId, 0, taskInfo).sendToTarget();
    }

    @Override
    public void onTaskDisplayChanged(int taskId, int newDisplayId) {
        mMainHandler.obtainMessage(ON_TASK_DISPLAY_CHANGED, taskId,
                newDisplayId).sendToTarget();
    }

    @Override
    public void onTaskCreated(int taskId, ComponentName componentName) {
        mMainHandler.obtainMessage(ON_TASK_CREATED, taskId, 0, componentName).sendToTarget();
    }

    @Override
    public void onTaskRemoved(int taskId) {
        mMainHandler.obtainMessage(ON_TASK_REMOVED, taskId, 0).sendToTarget();
    }

    @Override
    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
        mMainHandler.obtainMessage(ON_TASK_MOVED_TO_FRONT, taskInfo).sendToTarget();
    }

    @Override
    public void onTaskDescriptionChanged(ActivityManager.RunningTaskInfo taskInfo) {
        mMainHandler.obtainMessage(ON_TASK_DESCRIPTION_CHANGED, taskInfo).sendToTarget();
    }

    @Override
    public void onTaskSnapshotChanged(int taskId, TaskSnapshot snapshot) {
        mMainHandler.obtainMessage(ON_TASK_SNAPSHOT_CHANGED, taskId, 0, snapshot)
                .sendToTarget();
    }

    @Override
    public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo) {
        mMainHandler.obtainMessage(ON_BACK_PRESSED_ON_TASK_ROOT, taskInfo).sendToTarget();
    }

    @Override
    public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = packageName;
        args.argi1 = userId;
        args.argi2 = taskId;
        args.argi3 = stackId;
        mMainHandler.removeMessages(ON_ACTIVITY_PINNED);
        mMainHandler.obtainMessage(ON_ACTIVITY_PINNED, args).sendToTarget();
    }

    @Override
    public void onActivityUnpinned() {
        mMainHandler.removeMessages(ON_ACTIVITY_UNPINNED);
        mMainHandler.sendEmptyMessage(ON_ACTIVITY_UNPINNED);
    }

    @Override
    public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
            boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
        final SomeArgs args = SomeArgs.obtain();
        args.arg1 = task;
        args.argi1 = homeTaskVisible ? 1 : 0;
        args.argi2 = clearedTask ? 1 : 0;
        args.argi3 = wasVisible ? 1 : 0;
        mMainHandler.obtainMessage(ON_ACTIVITY_RESTART_ATTEMPT, args).sendToTarget();
    }

    @Override
    public void onActivityForcedResizable(String packageName, int taskId, int reason) {
        mMainHandler.obtainMessage(ON_ACTIVITY_FORCED_RESIZABLE, taskId, reason, packageName)
                .sendToTarget();
    }

    @Override
    public void onActivityDismissingDockedTask() {
        mMainHandler.sendEmptyMessage(ON_ACTIVITY_DISMISSING_DOCKED_STACK);
    }

    @Override
    public void onActivityLaunchOnSecondaryDisplayFailed(
            ActivityManager.RunningTaskInfo taskInfo,
            int requestedDisplayId) {
        mMainHandler.obtainMessage(ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_FAILED,
                requestedDisplayId,
                0 /* unused */,
                taskInfo).sendToTarget();
    }

    @Override
    public void onActivityLaunchOnSecondaryDisplayRerouted(
            ActivityManager.RunningTaskInfo taskInfo,
            int requestedDisplayId) {
        mMainHandler.obtainMessage(ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_REROUTED,
                requestedDisplayId, 0 /* unused */, taskInfo).sendToTarget();
    }

    @Override
    public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation) {
        mMainHandler.obtainMessage(ON_ACTIVITY_REQUESTED_ORIENTATION_CHANGE, taskId,
                requestedOrientation).sendToTarget();
    }

    @Override
    public void onActivityRotation(int displayId) {
        mMainHandler.obtainMessage(ON_ACTIVITY_ROTATION, displayId, 0 /* unused */)
                .sendToTarget();
    }

    @Override
    public boolean handleMessage(Message msg) {
        synchronized (mTaskStackListeners) {
            switch (msg.what) {
                case ON_TASK_STACK_CHANGED: {
                    Trace.beginSection("onTaskStackChanged");
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onTaskStackChanged();
                    }
                    Trace.endSection();
                    break;
                }
                case ON_TASK_SNAPSHOT_CHANGED: {
                    Trace.beginSection("onTaskSnapshotChanged");
                    final TaskSnapshot snapshot = (TaskSnapshot) msg.obj;
                    boolean snapshotConsumed = false;
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        boolean consumed = mTaskStackListeners.get(i).onTaskSnapshotChanged(
                                msg.arg1, snapshot);
                        snapshotConsumed |= consumed;
                    }
                    if (!snapshotConsumed && snapshot.getHardwareBuffer() != null) {
                        snapshot.getHardwareBuffer().close();
                    }
                    Trace.endSection();
                    break;
                }
                case ON_ACTIVITY_PINNED: {
                    final SomeArgs args = (SomeArgs) msg.obj;
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onActivityPinned((String) args.arg1, args.argi1,
                                args.argi2, args.argi3);
                    }
                    break;
                }
                case ON_ACTIVITY_UNPINNED: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onActivityUnpinned();
                    }
                    break;
                }
                case ON_ACTIVITY_RESTART_ATTEMPT: {
                    final SomeArgs args = (SomeArgs) msg.obj;
                    final ActivityManager.RunningTaskInfo
                            task = (ActivityManager.RunningTaskInfo) args.arg1;
                    final boolean homeTaskVisible = args.argi1 != 0;
                    final boolean clearedTask = args.argi2 != 0;
                    final boolean wasVisible = args.argi3 != 0;
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onActivityRestartAttempt(task,
                                homeTaskVisible, clearedTask, wasVisible);
                    }
                    break;
                }
                case ON_ACTIVITY_FORCED_RESIZABLE: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onActivityForcedResizable(
                                (String) msg.obj, msg.arg1, msg.arg2);
                    }
                    break;
                }
                case ON_ACTIVITY_DISMISSING_DOCKED_STACK: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onActivityDismissingDockedStack();
                    }
                    break;
                }
                case ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_FAILED: {
                    final ActivityManager.RunningTaskInfo
                            info = (ActivityManager.RunningTaskInfo) msg.obj;
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i)
                                .onActivityLaunchOnSecondaryDisplayFailed(info);
                    }
                    break;
                }
                case ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_REROUTED: {
                    final ActivityManager.RunningTaskInfo
                            info = (ActivityManager.RunningTaskInfo) msg.obj;
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i)
                                .onActivityLaunchOnSecondaryDisplayRerouted(info);
                    }
                    break;
                }
                case ON_TASK_PROFILE_LOCKED: {
                    final ActivityManager.RunningTaskInfo
                            info = (ActivityManager.RunningTaskInfo) msg.obj;
                    final int userId = msg.arg1;
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onTaskProfileLocked(info, userId);
                    }
                    break;
                }
                case ON_TASK_CREATED: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onTaskCreated(msg.arg1,
                                (ComponentName) msg.obj);
                    }
                    break;
                }
                case ON_TASK_REMOVED: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onTaskRemoved(msg.arg1);
                    }
                    break;
                }
                case ON_TASK_MOVED_TO_FRONT: {
                    final ActivityManager.RunningTaskInfo
                            info = (ActivityManager.RunningTaskInfo) msg.obj;
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onTaskMovedToFront(info);
                    }
                    break;
                }
                case ON_ACTIVITY_REQUESTED_ORIENTATION_CHANGE: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i)
                                .onActivityRequestedOrientationChanged(msg.arg1, msg.arg2);
                    }
                    break;
                }
                case ON_BACK_PRESSED_ON_TASK_ROOT: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onBackPressedOnTaskRoot(
                                (ActivityManager.RunningTaskInfo) msg.obj);
                    }
                    break;
                }
                case ON_TASK_DISPLAY_CHANGED: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onTaskDisplayChanged(msg.arg1, msg.arg2);
                    }
                    break;
                }
                case ON_TASK_LIST_UPDATED: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onRecentTaskListUpdated();
                    }
                    break;
                }
                case ON_TASK_LIST_FROZEN_UNFROZEN: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onRecentTaskListFrozenChanged(
                                msg.arg1 != 0);
                    }
                    break;
                }
                case ON_TASK_DESCRIPTION_CHANGED: {
                    final ActivityManager.RunningTaskInfo
                            info = (ActivityManager.RunningTaskInfo) msg.obj;
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onTaskDescriptionChanged(info);
                    }
                    break;
                }
                case ON_ACTIVITY_ROTATION: {
                    for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                        mTaskStackListeners.get(i).onActivityRotation(msg.arg1);
                    }
                    break;
                }
            }
        }
        if (msg.obj instanceof SomeArgs) {
            ((SomeArgs) msg.obj).recycle();
        }
        return true;
    }
}