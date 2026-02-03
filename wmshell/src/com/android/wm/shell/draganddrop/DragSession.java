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

package com.android.wm.shell.draganddrop;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.ClipDescription.EXTRA_HIDE_DRAG_SOURCE_TASK_ID;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.PendingIntent;
import android.app.WindowConfiguration;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.pm.ActivityInfo;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.List;

/**
 * Per-drag session data.
 */
public class DragSession {
    private final ActivityTaskManager mActivityTaskManager;
    @Nullable
    private final ClipData mInitialDragData;
    private final int mInitialDragFlags;

    final DisplayLayout displayLayout;
    // The activity info associated with the activity in the appData or the launchableIntent
    @Nullable
    ActivityInfo activityInfo;
    // The intent bundle that includes data about an app-type drag that is started by
    // Launcher/SysUI.  Only one of appDragData OR launchableIntent will be non-null for a session.
    @Nullable
    Intent appData;
    // A launchable intent that is specified in the ClipData directly.
    // Only one of appDragData OR launchableIntent will be non-null for a session.
    @Nullable
    PendingIntent launchableIntent;
    // Stores the current running task at the time that the drag was initiated
    ActivityManager.RunningTaskInfo runningTaskInfo;
    @WindowConfiguration.WindowingMode
    int runningTaskWinMode = WINDOWING_MODE_UNDEFINED;
    @WindowConfiguration.ActivityType
    int runningTaskActType = ACTIVITY_TYPE_STANDARD;
    boolean dragItemSupportsSplitscreen;
    final int hideDragSourceTaskId;

    DragSession(ActivityTaskManager activityTaskManager,
            DisplayLayout dispLayout, ClipData data, int dragFlags) {
        mActivityTaskManager = activityTaskManager;
        mInitialDragData = data;
        mInitialDragFlags = dragFlags;
        displayLayout = dispLayout;
        hideDragSourceTaskId = data != null && data.getDescription().getExtras() != null
                ? data.getDescription().getExtras().getInt(EXTRA_HIDE_DRAG_SOURCE_TASK_ID, -1)
                : -1;
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                "Extracting drag source taskId: taskId=%d", hideDragSourceTaskId);
    }

    /**
     * Returns the clip description associated with the drag.
     */
    ClipDescription getClipDescription() {
        return mInitialDragData.getDescription();
    }

    /**
     * Updates the running task for this drag session.
     */
    void updateRunningTask() {
        final boolean hideDragSourceTask = hideDragSourceTaskId != -1;
        final List<ActivityManager.RunningTaskInfo> tasks =
                mActivityTaskManager.getTasks(5, false /* filterOnlyVisibleRecents */);
        for (int i = 0; i < tasks.size(); i++) {
            final ActivityManager.RunningTaskInfo task = tasks.get(i);
            if (hideDragSourceTask && hideDragSourceTaskId == task.taskId) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                        "Skipping running task: id=%d component=%s", task.taskId,
                        task.baseIntent != null ? task.baseIntent.getComponent() : "null");
                continue;
            }
            if (!task.isVisible) {
                // Skip invisible tasks
                continue;
            }
            if (task.configuration.windowConfiguration.isAlwaysOnTop()) {
                // Skip always-on-top floating tasks
                continue;
            }
            runningTaskInfo = task;
            runningTaskWinMode = task.getWindowingMode();
            runningTaskActType = task.getActivityType();
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                    "Running task: id=%d component=%s", task.taskId,
                    task.baseIntent != null ? task.baseIntent.getComponent() : "null");
            break;
        }
    }

    /**
     * Updates the session data based on the current state of the system at the start of the drag.
     */
    void initialize(boolean skipUpdateRunningTask) {
        if (!skipUpdateRunningTask) {
            updateRunningTask();
        }

        activityInfo = mInitialDragData.getItemAt(0).getActivityInfo();
        // TODO: This should technically check & respect config_supportsNonResizableMultiWindow
        dragItemSupportsSplitscreen = activityInfo == null
                || ActivityInfo.isResizeableMode(activityInfo.resizeMode);
        appData = DragUtils.isAppDrag(getClipDescription())
                ? mInitialDragData.getItemAt(0).getIntent()
                : null;
        launchableIntent = appData != null
                ? null
                : DragUtils.getLaunchIntent(mInitialDragData, mInitialDragFlags);
    }
}
