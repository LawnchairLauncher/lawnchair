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

package com.android.wm.shell.windowdecor.extension

import android.app.TaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.view.WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
import android.view.WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND

val TaskInfo.isTransparentCaptionBarAppearance: Boolean
    get() {
        val appearance = taskDescription?.topOpaqueSystemBarsAppearance ?: 0
        return (appearance and APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND) != 0
    }

val TaskInfo.isLightCaptionBarAppearance: Boolean
    get() {
        val appearance = taskDescription?.topOpaqueSystemBarsAppearance ?: 0
        return (appearance and APPEARANCE_LIGHT_CAPTION_BARS) != 0
    }

val TaskInfo.isFullscreen: Boolean
    get() = windowingMode == WINDOWING_MODE_FULLSCREEN
