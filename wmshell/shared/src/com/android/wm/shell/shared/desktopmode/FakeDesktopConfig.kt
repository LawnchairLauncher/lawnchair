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

package com.android.wm.shell.shared.desktopmode

import android.app.TaskInfo
import java.io.PrintWriter

class FakeDesktopConfig : DesktopConfig {

    override var shouldMaximizeWhenDragToTopEdge: Boolean = false
    override var useDesktopOverrideDensity: Boolean = false

    override var windowDecorPreWarmSize: Int = 0
    override var windowDecorScvhPoolSize: Int = 0
    override var isVeiledResizeEnabled: Boolean = true
    override var useAppToWebBuildTimeGenericLinks: Boolean = false
    override var useRoundedCorners: Boolean = true

    var useWindowShadowWhenFocused = true
    var useWindowShadowWhenUnfocused = true

    override fun useWindowShadow(isFocusedWindow: Boolean): Boolean =
        if (isFocusedWindow) useWindowShadowWhenFocused else useWindowShadowWhenUnfocused

    var defaultSetBackground = false
    val overrideSetBackgroundPerTaskId = mutableMapOf<Int, Boolean>()

    override fun shouldSetBackground(taskInfo: TaskInfo): Boolean =
        overrideSetBackgroundPerTaskId[taskInfo.taskId] ?: defaultSetBackground

    override var maxTaskLimit: Int = 0

    override var maxDeskLimit: Int = 0

    override var desktopDensityOverride: Int = 284

    override fun dump(
        pw: PrintWriter,
        prefix: String,
    ) { }
}