/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.app.ActivityManager.RunningTaskInfo
import android.view.SurfaceControl
import com.android.wm.shell.compatui.letterbox.events.ReachabilityGestureListener
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEvent

// The key to use for identify the letterbox sessions.
data class LetterboxKey(val displayId: Int, val taskId: Int)

// Encapsulates the surfaces in the multiple surfaces scenario.
data class LetterboxSurfaces(
    var leftSurface: SurfaceControl? = null,
    var topSurface: SurfaceControl? = null,
    var rightSurface: SurfaceControl? = null,
    var bottomSurface: SurfaceControl? = null,
) : Iterable<SurfaceControl?> {
    override fun iterator() =
        listOf(leftSurface, topSurface, rightSurface, bottomSurface).iterator()
}

// Encapsulate the object used for event detection.
data class LetterboxInputItems(
    val inputDetector: LetterboxInputDetector,
    val gestureListener: ReachabilityGestureListener,
)

/** Extract the [LetterboxKey] from the [LetterboxLifecycleEvent]. */
fun RunningTaskInfo.letterboxKey(): LetterboxKey =
    LetterboxKey(displayId = displayId, taskId = taskId)
