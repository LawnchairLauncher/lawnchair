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

package com.android.quickstep.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.UserHandle
import com.android.launcher3.Utilities

object IconLabelUtil {
    @JvmStatic
    @JvmOverloads
    fun getBadgedContentDescription(
        context: Context,
        info: ActivityInfo,
        userId: Int,
        taskDescription: ActivityManager.TaskDescription? = null,
    ): String {
        val packageManager = context.packageManager
        var taskLabel = taskDescription?.let { Utilities.trim(it.label) }
        if (taskLabel.isNullOrEmpty()) {
            taskLabel = Utilities.trim(info.loadLabel(packageManager))
        }

        val applicationLabel = Utilities.trim(info.applicationInfo.loadLabel(packageManager))
        val badgedApplicationLabel =
            if (userId != UserHandle.myUserId())
                packageManager
                    .getUserBadgedLabel(applicationLabel, UserHandle.of(userId))
                    .toString()
            else applicationLabel
        return if (applicationLabel == taskLabel) badgedApplicationLabel
        else "$badgedApplicationLabel $taskLabel"
    }
}
