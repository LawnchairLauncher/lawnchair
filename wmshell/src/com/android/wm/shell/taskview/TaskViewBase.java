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

package com.android.wm.shell.taskview;

import android.app.ActivityManager;
import android.graphics.Rect;
import android.view.SurfaceControl;

/**
 * A stub for SurfaceView used by {@link TaskViewTaskController}
 */
public interface TaskViewBase {
    /**
     * Returns the current bounds on screen for the task view.
     * @return
     */
    // TODO(b/266242294): Remove getBoundsOnScreen() and instead send the bounds from the TaskView
    //  to TaskViewTaskController.
    Rect getCurrentBoundsOnScreen();

    /**
     * This method should set the resize background color on the SurfaceView that is exposed to
     * clients.
     * See {@link android.view.SurfaceView#setResizeBackgroundColor(SurfaceControl.Transaction,
     * int)}
     */
    void setResizeBgColor(SurfaceControl.Transaction transaction, int color);

    /**
     * Called when a task appears on the TaskView. See
     * {@link TaskViewTaskController#onTaskAppeared(ActivityManager.RunningTaskInfo,
     * SurfaceControl)} for details.
     */
    default void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
    }

    /**
     * Called when a task is vanished from the TaskView. See
     * {@link TaskViewTaskController#onTaskVanished(ActivityManager.RunningTaskInfo)} for details.
     */
    default void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
    }

    /**
     * Called when the task in the TaskView is changed. See
     * {@link TaskViewTaskController#onTaskInfoChanged(ActivityManager.RunningTaskInfo)} for details.
     */
    default void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
    }
}
