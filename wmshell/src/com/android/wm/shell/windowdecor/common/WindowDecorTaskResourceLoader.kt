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
package com.android.wm.shell.windowdecor.common

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Bitmap

/** A utility and cache for window decoration UI resources. */
interface WindowDecorTaskResourceLoader {

    /**
     * Suspending function that returns the user readable name and icon for use by the app header
     * and menus for this task.
     */
    suspend fun getNameAndHeaderIcon(taskInfo: RunningTaskInfo): Pair<CharSequence, Bitmap>

    /**
     * Non-suspending function that returns the user readable name and icon for use by the app
     * header and menus for this task.
     */
    fun getNameAndHeaderIcon(taskInfo: RunningTaskInfo, callback: (CharSequence, Bitmap) -> Unit)

    /** Returns the icon for use by the resize veil for this task. */
    suspend fun getVeilIcon(taskInfo: RunningTaskInfo): Bitmap

    /** Called when a window decoration for this task is created. */
    fun onWindowDecorCreated(taskInfo: RunningTaskInfo)

    /** Called when a window decoration for this task is closed. */
    fun onWindowDecorClosed(taskInfo: RunningTaskInfo)
}
