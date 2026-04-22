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

package com.android.wm.shell.desktopmode.desktopfirst

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE

/** The display windowing mode for desktop-first display. */
const val DESKTOP_FIRST_DISPLAY_WINDOWING_MODE = WINDOWING_MODE_FREEFORM

/** The display windowing mode for touch-first display. */
const val TOUCH_FIRST_DISPLAY_WINDOWING_MODE = WINDOWING_MODE_FULLSCREEN

/** Returns true if a display is desktop-first. */
fun RootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId: Int) =
    getDisplayAreaInfo(displayId)?.configuration?.windowConfiguration?.windowingMode?.let {
        it == DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
    }
        ?: run {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "isDisplayDesktopFirst display=%d not found",
                displayId,
            )
            false
        }
