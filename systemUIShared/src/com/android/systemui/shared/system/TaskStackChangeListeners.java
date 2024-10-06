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

import android.annotation.NonNull;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Trace;
import android.util.Log;
import android.window.TaskSnapshot;

import androidx.annotation.VisibleForTesting;

import com.android.internal.os.SomeArgs;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks all the task stack listeners
 */
public class TaskStackChangeListeners {

    private static final String TAG = TaskStackChangeListeners.class.getSimpleName();
    private static final TaskStackChangeListeners INSTANCE = new TaskStackChangeListeners();

    private final Impl mImpl;

    /**
     * Proxies calls to the given handler callback synchronously for testing purposes.
     */
    private static class TestSyncHandler extends Handler {
        private Handler.Callback mCb;

        public TestSyncHandler() {
            super(Looper.getMainLooper());
        }

        public void setCallback(Handler.Callback cb) {
            mCb = cb;
        }

        @Override
        public boolean sendMessageAtTime(@NonNull Message msg, long uptimeMillis) {
            return mCb.handleMessage(msg);
        }
    }

    private TaskStackChangeListeners() {
        mImpl = new Impl(Looper.getMainLooper());
    }

    private TaskStackChangeListeners(Handler h) {
        mImpl = new Impl(h);
    }

    public static TaskStackChangeListeners getInstance() {
        return INSTANCE;
    }

    /**
     * Returns an instance of the listeners that can be called upon synchronously for testsing
     * purposes.
     */
    @VisibleForTesting
    public static TaskStackChangeListeners getTestInstance() {
        TestSyncHandler h = new TestSyncHandler();
        TaskStackChangeListeners l = new TaskStackChangeListeners(h);
        h.setCallback(l.mImpl);
        return l;
    }

    /**
     * Registers a task stack listener with the system.
     * This should be called on the main thread.
     */
    public void registerTaskStackListener(TaskStackChangeListener listener) {
        synchronized (mImpl) {
            mImpl.addListener(listener);
        }
    }

    /**
     * Unregisters a task stack listener with the system.
     * This should be called on the main thread.
     */
    public void unregisterTaskStackListener(TaskStackChangeListener listener) {
        synchronized (mImpl) {
            mImpl.removeListener(listener);
        }
    }

    /**
     * Returns an instance of the listener to call upon from tests.
     */
    @VisibleForTesting
    public TaskStackListener getListenerImpl() {
        return mImpl;
    }

    private class Impl extends TaskStackListener implements Handler.Callback {

        private static final int ON_TASK_STACK_CHANGED = 1;
        private static final int ON_TASK_SNAPSHOT_CHANGED = 2;
        private static final int ON_ACTIVITY_PINNED = 3;
        private static final int ON_ACTIVITY_RESTART_ATTEMPT = 4;
        private static final int ON_ACTIVITY_FORCED_RESIZABLE = 6;
        private static final int ON_ACTIVITY_DISMISSING_DOCKED_STACK = 7;
        private static final int ON_TASK_PROFILE_LOCKED = 8;
        private static final int ON_ACTIVITY_UNPINNED = 10;
        private static final int ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_FAILED = 11;
        private static final int ON_TASK_CREATED = 12;
        private static final int ON_TASK_REMOVED = 13;
        private static final int ON_TASK_MOVED_TO_FRONT = 14;
        private static final int ON_ACTIVITY_REQUESTED_ORIENTATION_CHANGE = 15;
        private static final int ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_REROUTED = 16;
        private static final int ON_BACK_PRESSED_ON_TASK_ROOT = 17;
        private static final int ON_TASK_DISPLAY_CHANGED = 18;
        private static final int ON_TASK_LIST_UPDATED = 19;
        private static final int ON_TASK_LIST_FROZEN_UNFROZEN = 20;
        private static final int ON_TASK_DESCRIPTION_CHANGED = 21;
        private static final int ON_ACTIVITY_ROTATION = 22;
        private static final int ON_LOCK_TASK_MODE_CHANGED = 23;
        private static final int ON_TASK_SNAPSHOT_INVALIDATED = 24;

        /**
         * List of {@link TaskStackChangeListener} registered from {@link #addListener}.
         */
        private final List<TaskStackChangeListener> mTaskStackListeners = new ArrayList<>();
        private final List<TaskStackChangeListener> mTmpListeners = new ArrayList<>();

        private final Handler mHandler;
        private boolean mRegistered;

        private Impl(Looper looper) {
            mHandler = new Handler(looper, this);
        }

        private Impl(Handler handler) {
            mHandler = handler;
        }

        public void addListener(TaskStackChangeListener listener) {
            synchronized (mTaskStackListeners) {
                mTaskStackListeners.add(listener);
            }
            if (!mRegistered) {
                // Register mTaskStackListener to IActivityManager only once if needed.
                try {
                    ActivityTaskManager.getService().registerTaskStackListener(this);
                    mRegistered = true;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to call registerTaskStackListener", e);
                }
            }
        }

        public void removeListener(TaskStackChangeListener listener) {
            boolean isEmpty;
            synchronized (mTaskStackListeners) {
                mTaskStackListeners.remove(listener);
                isEmpty = mTaskStackListeners.isEmpty();
            }
            if (isEmpty && mRegistered) {
                // Unregister mTaskStackListener once we have no more listeners
                try {
                    ActivityTaskManager.getService().unregisterTaskStackListener(this);
                    mRegistered = false;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to call unregisterTaskStackListener", e);
                }
            }
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

            mHandler.removeMessages(ON_TASK_STACK_CHANGED);
            mHandler.sendEmptyMessage(ON_TASK_STACK_CHANGED);
        }

        @Override
        public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
            mHandler.removeMessages(ON_ACTIVITY_PINNED);
            mHandler.obtainMessage(ON_ACTIVITY_PINNED,
                    new PinnedActivityInfo(packageName, userId, taskId, stackId)).sendToTarget();
        }

        @Override
        public void onActivityUnpinned() {
            mHandler.removeMessages(ON_ACTIVITY_UNPINNED);
            mHandler.sendEmptyMessage(ON_ACTIVITY_UNPINNED);
        }

        @Override
        public void onActivityRestartAttempt(RunningTaskInfo task, boolean homeTaskVisible,
                boolean clearedTask, boolean wasVisible) {
            final SomeArgs args = SomeArgs.obtain();
            args.arg1 = task;
            args.argi1 = homeTaskVisible ? 1 : 0;
            args.argi2 = clearedTask ? 1 : 0;
            args.argi3 = wasVisible ? 1 : 0;
            mHandler.removeMessages(ON_ACTIVITY_RESTART_ATTEMPT);
            mHandler.obtainMessage(ON_ACTIVITY_RESTART_ATTEMPT, args).sendToTarget();
        }

        @Override
        public void onActivityForcedResizable(String packageName, int taskId, int reason) {
            mHandler.obtainMessage(ON_ACTIVITY_FORCED_RESIZABLE, taskId, reason, packageName)
                    .sendToTarget();
        }

        @Override
        public void onActivityDismissingDockedTask() {
            mHandler.sendEmptyMessage(ON_ACTIVITY_DISMISSING_DOCKED_STACK);
        }

        @Override
        public void onActivityLaunchOnSecondaryDisplayFailed(RunningTaskInfo taskInfo,
                int requestedDisplayId) {
            mHandler.obtainMessage(ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_FAILED,
                    requestedDisplayId,
                    0 /* unused */,
                    taskInfo).sendToTarget();
        }

        @Override
        public void onActivityLaunchOnSecondaryDisplayRerouted(RunningTaskInfo taskInfo,
                int requestedDisplayId) {
            mHandler.obtainMessage(ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_REROUTED,
                    requestedDisplayId, 0 /* unused */, taskInfo).sendToTarget();
        }

        @Override
        public void onTaskProfileLocked(RunningTaskInfo taskInfo, int userId) {
            mHandler.obtainMessage(ON_TASK_PROFILE_LOCKED, userId, 0, taskInfo).sendToTarget();
        }

        @Override
        public void onTaskSnapshotChanged(int taskId, TaskSnapshot snapshot) {
            mHandler.obtainMessage(ON_TASK_SNAPSHOT_CHANGED, taskId, 0, snapshot).sendToTarget();
        }

        @Override
        public void onTaskSnapshotInvalidated(int taskId) {
            mHandler.obtainMessage(ON_TASK_SNAPSHOT_INVALIDATED, taskId, 0 /* unused */)
                    .sendToTarget();
        }

        @Override
        public void onTaskCreated(int taskId, ComponentName componentName) {
            mHandler.obtainMessage(ON_TASK_CREATED, taskId, 0, componentName).sendToTarget();
        }

        @Override
        public void onTaskRemoved(int taskId) {
            mHandler.obtainMessage(ON_TASK_REMOVED, taskId, 0).sendToTarget();
        }

        @Override
        public void onTaskMovedToFront(RunningTaskInfo taskInfo) {
            mHandler.obtainMessage(ON_TASK_MOVED_TO_FRONT, taskInfo).sendToTarget();
        }

        @Override
        public void onBackPressedOnTaskRoot(RunningTaskInfo taskInfo) {
            mHandler.obtainMessage(ON_BACK_PRESSED_ON_TASK_ROOT, taskInfo).sendToTarget();
        }

        @Override
        public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation) {
            mHandler.obtainMessage(ON_ACTIVITY_REQUESTED_ORIENTATION_CHANGE, taskId,
                    requestedOrientation).sendToTarget();
        }

        @Override
        public void onTaskDisplayChanged(int taskId, int newDisplayId) {
            mHandler.obtainMessage(ON_TASK_DISPLAY_CHANGED, taskId, newDisplayId).sendToTarget();
        }

        @Override
        public void onRecentTaskListUpdated() {
            mHandler.obtainMessage(ON_TASK_LIST_UPDATED).sendToTarget();
        }

        @Override
        public void onRecentTaskListFrozenChanged(boolean frozen) {
            mHandler.obtainMessage(ON_TASK_LIST_FROZEN_UNFROZEN, frozen ? 1 : 0, 0 /* unused */)
                    .sendToTarget();
        }

        @Override
        public void onTaskDescriptionChanged(RunningTaskInfo taskInfo) {
            mHandler.obtainMessage(ON_TASK_DESCRIPTION_CHANGED, taskInfo).sendToTarget();
        }

        @Override
        public void onActivityRotation(int displayId) {
            mHandler.obtainMessage(ON_ACTIVITY_ROTATION, displayId, 0 /* unused */)
                    .sendToTarget();
        }

        @Override
        public void onLockTaskModeChanged(int mode) {
            mHandler.obtainMessage(ON_LOCK_TASK_MODE_CHANGED, mode, 0 /* unused */).sendToTarget();
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
                        final ThumbnailData thumbnail = ThumbnailData.fromSnapshot(snapshot);
                        boolean snapshotConsumed = false;
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            boolean consumed = mTaskStackListeners.get(i).onTaskSnapshotChanged(
                                    msg.arg1, thumbnail);
                            snapshotConsumed |= consumed;
                        }
                        if (!snapshotConsumed) {
                            thumbnail.recycleBitmap();
                            if (snapshot.getHardwareBuffer() != null) {
                                snapshot.getHardwareBuffer().close();
                            }
                        }
                        Trace.endSection();
                        break;
                    }
                    case ON_ACTIVITY_PINNED: {
                        final PinnedActivityInfo info = (PinnedActivityInfo) msg.obj;
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            mTaskStackListeners.get(i).onActivityPinned(
                                    info.mPackageName, info.mUserId, info.mTaskId,
                                    info.mStackId);
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
                        final RunningTaskInfo task = (RunningTaskInfo) args.arg1;
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
                        final RunningTaskInfo info = (RunningTaskInfo) msg.obj;
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            mTaskStackListeners.get(i)
                                    .onActivityLaunchOnSecondaryDisplayFailed(info);
                        }
                        break;
                    }
                    case ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_REROUTED: {
                        final RunningTaskInfo info = (RunningTaskInfo) msg.obj;
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            mTaskStackListeners.get(i)
                                    .onActivityLaunchOnSecondaryDisplayRerouted(info);
                        }
                        break;
                    }
                    case ON_TASK_PROFILE_LOCKED: {
                        final RunningTaskInfo info = (RunningTaskInfo) msg.obj;
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
                        final RunningTaskInfo info = (RunningTaskInfo) msg.obj;
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
                                    (RunningTaskInfo) msg.obj);
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
                        final RunningTaskInfo info = (RunningTaskInfo) msg.obj;
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
                    case ON_LOCK_TASK_MODE_CHANGED: {
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            mTaskStackListeners.get(i).onLockTaskModeChanged(msg.arg1);
                        }
                        break;
                    }
                    case ON_TASK_SNAPSHOT_INVALIDATED: {
                        Trace.beginSection("onTaskSnapshotInvalidated");
                        final ThumbnailData thumbnail = new ThumbnailData();
                        for (int i = mTaskStackListeners.size() - 1; i >= 0; i--) {
                            mTaskStackListeners.get(i).onTaskSnapshotChanged(msg.arg1, thumbnail);
                        }
                        Trace.endSection();
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

    private static class PinnedActivityInfo {
        final String mPackageName;
        final int mUserId;
        final int mTaskId;
        final int mStackId;

        PinnedActivityInfo(String packageName, int userId, int taskId, int stackId) {
            mPackageName = packageName;
            mUserId = userId;
            mTaskId = taskId;
            mStackId = stackId;
        }
    }
}
