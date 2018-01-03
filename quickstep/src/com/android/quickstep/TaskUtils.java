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
 * limitations under the License.
 */

package com.android.quickstep;

import android.content.pm.PackageManager;
import android.util.Log;

import com.android.launcher3.Launcher;
import com.android.systemui.shared.recents.model.Task;

/**
 * Contains helpful methods for retrieving data from {@link Task}s.
 * TODO: remove this once we switch to getting the icon and label from IconCache.
 */
public class TaskUtils {
    private static final String TAG = "TaskUtils";

    public static CharSequence getTitle(Launcher launcher, Task task) {
        PackageManager pm = launcher.getPackageManager();
        try {
            return pm.getPackageInfo(task.getTopComponent().getPackageName(), 0)
                    .applicationInfo.loadLabel(pm);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get title for task " + task, e);
        }
        return "";
    }
}
