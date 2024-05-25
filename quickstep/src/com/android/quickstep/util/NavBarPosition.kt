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
package com.android.quickstep.util

import android.view.Surface
import com.android.launcher3.util.DisplayController.Info
import com.android.launcher3.util.NavigationMode
import com.android.launcher3.util.NavigationMode.NO_BUTTON

/** Utility class to check nav bar position. */
data class NavBarPosition(
    val isTablet: Boolean,
    val displayRotation: Int,
    val mode: NavigationMode
) {
    constructor(
        mode: NavigationMode,
        info: Info
    ) : this(info.isTablet(info.realBounds), info.rotation, mode)

    val isRightEdge: Boolean
        get() = mode != NO_BUTTON && displayRotation == Surface.ROTATION_90 && !isTablet
    val isLeftEdge: Boolean
        get() = mode != NO_BUTTON && displayRotation == Surface.ROTATION_270 && !isTablet

    val rotation: Float
        get() = if (isLeftEdge) 90f else if (isRightEdge) -90f else 0f
}
