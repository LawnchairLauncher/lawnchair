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

package com.android.wm.shell.fullscreen;

import static com.android.wm.shell.ShellTaskOrganizer.TASK_LISTENER_TYPE_FULLSCREEN;
import static com.android.wm.shell.ShellTaskOrganizer.taskListenerTypeToString;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.graphics.Point;
import android.util.SparseArray;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.io.PrintWriter;
import java.util.Optional;

/**
  * Organizes tasks presented in {@link android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN}.
 * @param <T> the type of window decoration instance
  */
public class FullscreenTaskListener implements ShellTaskOrganizer.TaskListener {
    private static final String TAG = "FullscreenTaskListener";

    private final ShellTaskOrganizer mShellTaskOrganizer;

    private final SparseArray<State> mTasks = new SparseArray<>();

    private static class State {
        RunningTaskInfo mTaskInfo;
        SurfaceControl mLeash;
    }
    private final SyncTransactionQueue mSyncQueue;
    private final Optional<RecentTasksController> mRecentTasksOptional;
    private final Optional<WindowDecorViewModel> mWindowDecorViewModelOptional;
    /**
     * This constructor is used by downstream products.
     */
    public FullscreenTaskListener(SyncTransactionQueue syncQueue) {
        this(null /* shellInit */, null /* shellTaskOrganizer */, syncQueue, Optional.empty(),
                Optional.empty());
    }

    public FullscreenTaskListener(ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional) {
        mShellTaskOrganizer = shellTaskOrganizer;
        mSyncQueue = syncQueue;
        mRecentTasksOptional = recentTasksOptional;
        mWindowDecorViewModelOptional = windowDecorViewModelOptional;
        // Note: Some derivative FullscreenTaskListener implementations do not use ShellInit
        if (shellInit != null) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    private void onInit() {
        mShellTaskOrganizer.addListenerForType(this, TASK_LISTENER_TYPE_FULLSCREEN);
    }

    @Override
    public void onTaskAppeared(RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (mTasks.get(taskInfo.taskId) != null) {
            throw new IllegalStateException("Task appeared more than once: #" + taskInfo.taskId);
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Fullscreen Task Appeared: #%d",
                taskInfo.taskId);
        final Point positionInParent = taskInfo.positionInParent;
        final State state = new State();
        state.mLeash = leash;
        state.mTaskInfo = taskInfo;
        mTasks.put(taskInfo.taskId, state);

        if (Transitions.ENABLE_SHELL_TRANSITIONS) return;
        updateRecentsForVisibleFullscreenTask(taskInfo);
        boolean createdWindowDecor = false;
        if (mWindowDecorViewModelOptional.isPresent()) {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            createdWindowDecor = mWindowDecorViewModelOptional.get()
                    .onTaskOpening(taskInfo, leash, t, t);
            t.apply();
        }
        if (!createdWindowDecor) {
            mSyncQueue.runInSync(t -> {
                if (!leash.isValid()) {
                    // Task vanished before sync completion
                    return;
                }
                // Reset several properties back to fullscreen (PiP, for example, leaves all these
                // properties in a bad state).
                t.setWindowCrop(leash, null);
                t.setPosition(leash, positionInParent.x, positionInParent.y);
                t.setAlpha(leash, 1f);
                t.setMatrix(leash, 1, 0, 0, 1);
                if (taskInfo.isVisible) {
                    t.show(leash);
                }
            });
        }
    }

    @Override
    public void onTaskInfoChanged(RunningTaskInfo taskInfo) {
        final State state = mTasks.get(taskInfo.taskId);
        final Point oldPositionInParent = state.mTaskInfo.positionInParent;
        boolean oldVisible = state.mTaskInfo.isVisible;

        if (mWindowDecorViewModelOptional.isPresent()) {
            mWindowDecorViewModelOptional.get().onTaskInfoChanged(taskInfo);
        }
        state.mTaskInfo = taskInfo;
        if (Transitions.ENABLE_SHELL_TRANSITIONS) return;
        updateRecentsForVisibleFullscreenTask(taskInfo);

        final Point positionInParent = state.mTaskInfo.positionInParent;
        boolean positionInParentChanged = !oldPositionInParent.equals(positionInParent);
        boolean becameVisible = !oldVisible && state.mTaskInfo.isVisible;

        if (becameVisible || positionInParentChanged) {
            mSyncQueue.runInSync(t -> {
                if (!state.mLeash.isValid()) {
                    // Task vanished before sync completion
                    return;
                }
                if (becameVisible) {
                    t.show(state.mLeash);
                }
                t.setPosition(state.mLeash, positionInParent.x, positionInParent.y);
            });
        }
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Fullscreen Task Vanished: #%d",
                taskInfo.taskId);
        mTasks.remove(taskInfo.taskId);
        mWindowDecorViewModelOptional.ifPresent(v -> v.onTaskVanished(taskInfo));
        if (Transitions.ENABLE_SHELL_TRANSITIONS) return;
        if (mWindowDecorViewModelOptional.isPresent()) {
            mWindowDecorViewModelOptional.get().destroyWindowDecoration(taskInfo);
        }
    }

    private void updateRecentsForVisibleFullscreenTask(RunningTaskInfo taskInfo) {
        mRecentTasksOptional.ifPresent(recentTasks -> {
            if (taskInfo.isVisible) {
                // Remove any persisted splits if either tasks are now made fullscreen and visible
                recentTasks.removeSplitPair(taskInfo.taskId);
            }
        });
    }

    @Override
    public void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
        b.setParent(findTaskSurface(taskId));
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
            SurfaceControl.Transaction t) {
        t.reparent(sc, findTaskSurface(taskId));
    }

    private SurfaceControl findTaskSurface(int taskId) {
        if (!mTasks.contains(taskId)) {
            throw new IllegalArgumentException("There is no surface for taskId=" + taskId);
        }
        return mTasks.get(taskId).mLeash;
    }

    @Override
    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + this);
        pw.println(innerPrefix + mTasks.size() + " Tasks");
    }

    @Override
    public String toString() {
        return TAG + ":" + taskListenerTypeToString(TASK_LISTENER_TYPE_FULLSCREEN);
    }
}
