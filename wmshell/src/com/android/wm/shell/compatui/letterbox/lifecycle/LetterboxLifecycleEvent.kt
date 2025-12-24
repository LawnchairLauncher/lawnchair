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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.app.TaskInfo
import android.graphics.Rect
import android.view.SurfaceControl
import android.window.TransitionInfo.Change
import android.window.WindowContainerToken
import com.android.wm.shell.compatui.letterbox.LetterboxKey
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventType.CLOSE
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventType.NONE
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventType.OPEN
import com.android.wm.shell.shared.TransitionUtil.isClosingType
import com.android.wm.shell.shared.TransitionUtil.isOpeningType

enum class LetterboxLifecycleEventType {
    NONE,
    OPEN,
    CLOSE,
}

/** Encapsulate all the information required by a [LetterboxLifecycleController] */
data class LetterboxLifecycleEvent(
    val type: LetterboxLifecycleEventType = NONE,
    val taskId: Int = -1,
    val displayId: Int = -1,
    val taskBounds: Rect,
    val letterboxBounds: Rect? = null,
    val containerToken: WindowContainerToken? = null,
    val taskLeash: SurfaceControl? = null,
    val isBubble: Boolean = false,
    val isTranslucent: Boolean = false,
    val supportsInput: Boolean = true,
)

/** Extract the [LetterboxKey] from the [LetterboxLifecycleEvent]. */
fun LetterboxLifecycleEvent.letterboxKey(): LetterboxKey =
    LetterboxKey(displayId = displayId, taskId = taskId)

/** Maps a [TransitionInfo.Change] mode in a [LetterboxLifecycleEventType]. */
fun Change.asLetterboxLifecycleEventType() =
    when {
        isClosingType(mode) -> CLOSE
        isOpeningType(mode) -> OPEN
        else -> NONE
    }

/**
 * Logic to skip a [Change] if not related to Letterboxing. We always skip changes about closing.
 */
fun Change.shouldSkipForLetterbox(): Boolean = isClosingType(mode)

/**
 * Returns [true] if the [Change] is about an [Activity] and so it contains a
 * [ActivityTransitionInfo].
 */
fun Change.isActivityChange(): Boolean = activityTransitionInfo != null

/** Returns [true] if the [Change] is related to a translucent container. */
fun Change.isTranslucent() = taskInfo?.isTopActivityTransparent ?: false

/** Returns [true] if the related [Task] is a leaf task. */
val TaskInfo.isALeafTask: Boolean
    get() = appCompatTaskInfo?.isLeafTask ?: false

/**
 * Returns [true] if the [Task] hosts Activities. This is true if the Change has [Activity] as
 * target or if task is a leaf task.
 */
fun Change.isChangeForALeafTask(): Boolean =
    taskInfo?.appCompatTaskInfo?.isLeafTask ?: isActivityChange()
