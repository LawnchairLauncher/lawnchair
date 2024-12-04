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
import android.app.ActivityManager
import android.content.ComponentName
import android.os.Bundle
import android.view.WindowManager
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.util.KtProtoLog

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
        KtProtoLog.d(WM_SHELL_DESKTOP_MODE, "DesktopWallpaperActivity: onCreate")
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    companion object {
        private const val SYSTEM_UI_PACKAGE_NAME = "com.android.systemui"
        private val wallpaperActivityComponent =
            ComponentName(SYSTEM_UI_PACKAGE_NAME, DesktopWallpaperActivity::class.java.name)

        @JvmStatic
        fun isWallpaperTask(taskInfo: ActivityManager.RunningTaskInfo) =
            taskInfo.baseIntent.component?.let(::isWallpaperComponent) ?: false

        @JvmStatic
        fun isWallpaperComponent(component: ComponentName) =
            component == wallpaperActivityComponent
    }
}
