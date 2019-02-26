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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.List;

/**
 * Contains helpful methods for retrieving data from {@link Task}s.
 */
public final class TaskUtils {

    private static final String TAG = "TaskUtils";

    private TaskUtils() {}

    /**
     * TODO: remove this once we switch to getting the icon and label from IconCache.
     */
    public static CharSequence getTitle(Context context, Task task) {
        LauncherAppsCompat launcherAppsCompat = LauncherAppsCompat.getInstance(context);
        PackageManager packageManager = context.getPackageManager();
        UserHandle user = UserHandle.of(task.key.userId);
        ApplicationInfo applicationInfo = launcherAppsCompat.getApplicationInfo(
            task.getTopComponent().getPackageName(), 0, user);
        if (applicationInfo == null) {
            Log.e(TAG, "Failed to get title for task " + task);
            return "";
        }
        return packageManager.getUserBadgedLabel(
            applicationInfo.loadLabel(packageManager), user);
    }

    public static ComponentKey getLaunchComponentKeyForTask(Task.TaskKey taskKey) {
        final ComponentName cn = taskKey.sourceComponent != null
                ? taskKey.sourceComponent
                : taskKey.getComponent();
        return new ComponentKey(cn, UserHandle.of(taskKey.userId));
    }


    public static boolean taskIsATargetWithMode(RemoteAnimationTargetCompat[] targets,
            int taskId, int mode) {
        for (RemoteAnimationTargetCompat target : targets) {
            if (target.mode == mode && target.taskId == taskId) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkCurrentOrManagedUserId(int currentUserId, Context context) {
        if (currentUserId == UserHandle.myUserId()) {
            return true;
        }
        List<UserHandle> allUsers = UserManagerCompat.getInstance(context).getUserProfiles();
        for (int i = allUsers.size() - 1; i >= 0; i--) {
            if (currentUserId == allUsers.get(i).getIdentifier()) {
                return true;
            }
        }
        return false;
    }
}
