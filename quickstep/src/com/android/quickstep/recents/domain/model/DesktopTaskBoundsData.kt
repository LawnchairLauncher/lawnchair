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

package com.android.quickstep.recents.domain.model

import android.graphics.Rect

/**
 * Represents the layout data for a desktop task in Overview. It can either be a task that is
 * rendered with specific bounds, or a task that is considered hidden from the main overview grid
 * (e.g., couldn't fit).
 */
sealed class DesktopTaskBoundsData {
    /** The ID of the task. */
    abstract val taskId: Int

    /**
     * Data for a desktop task that is rendered with calculated bounds in the Overview grid.
     *
     * @param bounds The calculated bounds for the task in the Overview grid.
     */
    data class RenderedDesktopTaskBoundsData(override val taskId: Int, val bounds: Rect) :
        DesktopTaskBoundsData()

    /**
     * Data for a desktop task that is not rendered in the main Overview grid (e.g., it couldn't fit
     * or was otherwise excluded by the layout algorithm). The view layer is responsible for
     * deciding how to represent this, if at all (e.g., with a small placeholder).
     */
    data class HiddenDesktopTaskBoundsData(override val taskId: Int) : DesktopTaskBoundsData()
}
