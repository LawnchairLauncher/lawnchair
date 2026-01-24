/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.app.Activity
import android.app.TaskInfo
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.window.DesktopExperienceFlags

/**
 * A transparent activity used in the desktop mode to show the wallpaper under the freeform windows.
 * This activity will be running in `FULLSCREEN` windowing mode, which ensures it hides Launcher.
 * When entering desktop, we would ensure that it's added behind desktop apps and removed when
 * leaving the desktop mode.
 *
 * Note! This activity should NOT interact directly with any other code in the Shell without calling
 * onto the shell main thread. Activities are always started on the main thread.
 */
class DesktopWallpaperActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        // Set to |false| by default. This shouldn't matter because
        // [Activity#onTopResumedActivityChanged] is supposed to be called after [onResume] which
        // should set the correct state. However, there's a lifecycle bug that causes it not to
        // be called after [onCreate] (see b/416700931) and may leave the wallpaper touchable after
        // entering desktop mode with another app. To prevent this make it not focusable by
        // default, as it is more likely a user will enter desktop with a task than without one
        // (entering through an empty desk may result in a reversed bug: unfocusable when we wanted
        // it to be focusable).
        updateFocusableFlag(focusable = false)
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        Log.d(TAG, "onTopResumedActivityChanged: $isTopResumedActivity")
        // Let the activity be focusable when it is top-resumed (e.g. empty desk), otherwise input
        // events will result in an ANR because the focused app would have no focusable window.
        updateFocusableFlag(focusable = isTopResumedActivity)
    }

    private fun updateFocusableFlag(focusable: Boolean) {
        if (focusable) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    companion object {
        private const val TAG = "DesktopWallpaperActivity"
        private const val SYSTEM_UI_PACKAGE_NAME = "com.android.systemui"

        @JvmStatic
        val wallpaperActivityComponent =
            ComponentName(SYSTEM_UI_PACKAGE_NAME, DesktopWallpaperActivity::class.java.name)

        @JvmStatic
        fun isWallpaperTask(taskInfo: TaskInfo) =
            taskInfo.baseIntent.component?.let(::isWallpaperComponent) ?: false

        @JvmStatic
        fun isWallpaperComponent(component: ComponentName) = component == wallpaperActivityComponent
    }
}
