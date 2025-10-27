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

import android.app.TaskInfo
import android.content.ComponentName
import android.content.res.Resources
import android.window.DesktopExperienceFlags
import com.android.systemui.shared.recents.model.Task

class DesksUtils {
    companion object {
        val sysUiPackage =
            Resources.getSystem().getString(com.android.internal.R.string.config_systemUi)

        @JvmStatic
        fun areMultiDesksFlagsEnabled() =
            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue &&
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_FRONTEND.isTrue

        /** Returns true if this [task] contains the [DesktopWallpaperActivity]. */
        @JvmStatic
        fun isDesktopWallpaperTask(task: Task) =
            task.key.component?.let(::isDesktopWallpaperComponent) == true

        @JvmStatic
        fun isDesktopWallpaperTask(taskInfo: TaskInfo): Boolean {
            // TODO: b/403118101 - In some launcher tests, there is a task with baseIntent set to
            // null. Remove this check after finding out how that task is created.
            if (taskInfo.baseIntent == null) return false
            return taskInfo.baseIntent.component?.let(::isDesktopWallpaperComponent) == true
        }

        @JvmStatic
        fun isDesktopWallpaperComponent(component: ComponentName) =
            component.className.contains("DesktopWallpaperActivity") &&
                component.packageName.contains(sysUiPackage)
    }
}
