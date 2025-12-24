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

import android.graphics.Region

/** Listener for changes and dismissal of the exclusion region. */
interface ExclusionRegionListener {
    /** Inform the implementing class of this task's change in region resize handles. */
    fun onExclusionRegionChanged(taskId: Int, region: Region)

    /**
     * Inform the implementing class that this task no longer needs an exclusion region, likely due
     * to it closing.
     */
    fun onExclusionRegionDismissed(taskId: Int)
}
