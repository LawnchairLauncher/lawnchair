/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.wm.shell.common

import android.app.PendingIntent
import android.app.TaskInfo
import android.content.ComponentName
import android.content.Intent
import com.android.wm.shell.ShellTaskOrganizer

/** Utils to obtain [ComponentName]s. */
object ComponentUtils {
    /** Retrieves the package name from an [Intent].  */
    @JvmStatic
    fun getPackageName(intent: Intent?): String? =
        intent?.component?.packageName ?: intent?.`package`

    /** Retrieves the package name from a [PendingIntent].  */
    @JvmStatic
    fun getPackageName(pendingIntent: PendingIntent?): String? =
        getPackageName(pendingIntent?.intent)

    /** Retrieves the package name from a [taskId].  */
    @JvmStatic
    fun getPackageName(taskId: Int, taskOrganizer: ShellTaskOrganizer): String? {
        return getPackageName(taskOrganizer.getRunningTaskInfo(taskId))
    }

    /** Retrieves the package name from a [TaskInfo]. */
    @JvmStatic
    fun getPackageName(taskInfo: TaskInfo?): String? = getPackageName(taskInfo?.baseIntent)
}
