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
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.PendingIntent;
import android.app.WindowConfiguration;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.pm.ActivityInfo;

import androidx.annotation.Nullable;

import com.android.wm.shell.common.DisplayLayout;

import java.util.List;

/**
 * Per-drag session data.
 */
public class DragSession {
    private final ActivityTaskManager mActivityTaskManager;
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

    DragSession(ActivityTaskManager activityTaskManager,
            DisplayLayout dispLayout, ClipData data, int dragFlags) {
        mActivityTaskManager = activityTaskManager;
        mInitialDragData = data;
        mInitialDragFlags = dragFlags;
        displayLayout = dispLayout;
    }

    /**
     * Returns the clip description associated with the drag.
     * @return
     */
    ClipDescription getClipDescription() {
        return mInitialDragData.getDescription();
    }

    /**
     * Updates the session data based on the current state of the system.
     */
    void update() {
        List<ActivityManager.RunningTaskInfo> tasks =
                mActivityTaskManager.getTasks(1, false /* filterOnlyVisibleRecents */);
        if (!tasks.isEmpty()) {
            final ActivityManager.RunningTaskInfo task = tasks.get(0);
            runningTaskInfo = task;
            runningTaskWinMode = task.getWindowingMode();
            runningTaskActType = task.getActivityType();
        }

        activityInfo = mInitialDragData.getItemAt(0).getActivityInfo();
        // TODO: This should technically check & respect config_supportsNonResizableMultiWindow
        dragItemSupportsSplitscreen = activityInfo == null
                || ActivityInfo.isResizeableMode(activityInfo.resizeMode);
        appData = mInitialDragData.getItemAt(0).getIntent();
        launchableIntent = DragUtils.getLaunchIntent(mInitialDragData, mInitialDragFlags);
    }
}
