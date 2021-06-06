/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.app.ActivityManager;
import android.app.TaskInfo;
import android.content.ComponentName;

public class TaskInfoCompat {

    public static int getUserId(TaskInfo info) {
        return info.userId;
    }

    public static int getActivityType(TaskInfo info) {
        return info.configuration.windowConfiguration.getActivityType();
    }

    public static int getWindowingMode(TaskInfo info) {
        return info.configuration.windowConfiguration.getWindowingMode();
    }

    public static boolean supportsSplitScreenMultiWindow(TaskInfo info) {
        return info.supportsSplitScreenMultiWindow;
    }

    public static ComponentName getTopActivity(TaskInfo info) {
        return info.topActivity;
    }

    public static ActivityManager.TaskDescription getTaskDescription(TaskInfo info) {
        return info.taskDescription;
    }
}
