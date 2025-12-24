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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.graphics.Point;
import android.util.SparseArray;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity;
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.sysui.ShellInit;
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
    private final Optional<DesktopWallpaperActivityTokenProvider>
            mDesktopWallpaperActivityTokenProviderOptional;

    /**
     * This constructor is used by downstream products.
     */
    public FullscreenTaskListener(SyncTransactionQueue syncQueue) {
        this(null /* shellInit */, null /* shellTaskOrganizer */, syncQueue, Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    public FullscreenTaskListener(ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional,
            Optional<DesktopWallpaperActivityTokenProvider>
                    desktopWallpaperActivityTokenProviderOptional) {
        mShellTaskOrganizer = shellTaskOrganizer;
        mSyncQueue = syncQueue;
        mRecentTasksOptional = recentTasksOptional;
        mWindowDecorViewModelOptional = windowDecorViewModelOptional;
        mDesktopWallpaperActivityTokenProviderOptional =
                desktopWallpaperActivityTokenProviderOptional;
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
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Fullscreen Task Vanished: #%d",
                taskInfo.taskId);
        mTasks.remove(taskInfo.taskId);
        mWindowDecorViewModelOptional.ifPresent(v -> v.onTaskVanished(taskInfo));
        mDesktopWallpaperActivityTokenProviderOptional.ifPresent(
                provider -> {
                    if (DesktopWallpaperActivity.isWallpaperTask(taskInfo)) {
                        provider.removeToken(taskInfo.getToken());
                    }
                });
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
        final SurfaceControl taskSurface = findTaskSurface(taskId);
        if (taskSurface != null) {
            b.setParent(taskSurface);
        }
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
            SurfaceControl.Transaction t) {
        final SurfaceControl taskSurface = findTaskSurface(taskId);
        if (taskSurface != null) {
            t.reparent(sc, taskSurface);
        }
    }

    private SurfaceControl findTaskSurface(int taskId) {
        final State state = mTasks.get(taskId);
        if (state != null) {
            return state.mLeash;
        }
        ProtoLog.w(ShellProtoLogGroup.WM_SHELL_TASK_ORG, "Surface not found: #%d",
                taskId);
        return null;
    }

    @Override
    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + this);
        pw.println(innerPrefix + mTasks.size() + " Tasks");
    }

    @Override
    public String toString() {
        return TAG;
    }
}
