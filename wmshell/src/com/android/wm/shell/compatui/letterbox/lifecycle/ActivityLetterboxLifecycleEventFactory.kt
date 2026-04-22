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

import android.window.TransitionInfo.Change
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoRepository
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT

/**
 * [LetterboxLifecycleEventFactory] implementation which creates a [LetterboxLifecycleEvent] from
 * a [TransitionInfo.Change] using a [ActivityTransitionInfo] when present.
 */
class ActivityLetterboxLifecycleEventFactory(
    private val taskRepository: LetterboxTaskInfoRepository
) : LetterboxLifecycleEventFactory {

    companion object {
        @JvmStatic
        private val TAG = "ActivityLetterboxLifecycleEventFactory"
    }

    override fun canHandle(change: Change): Boolean = change.activityTransitionInfo != null

    override fun createLifecycleEvent(change: Change): LetterboxLifecycleEvent? {
        val activityTransitionInfo = change.activityTransitionInfo
        val taskBounds = change.endAbsBounds

        val letterboxBoundsTmp = activityTransitionInfo?.appCompatTransitionInfo?.letterboxBounds
        val taskId = activityTransitionInfo?.taskId ?: -1
        taskRepository.find(taskId)?.let {
            val isLetterboxed = letterboxBoundsTmp != taskBounds
            // Letterbox bounds are null when the activity is not letterboxed.
            val letterboxBounds = if (isLetterboxed) letterboxBoundsTmp else null
            val taskToken = it.containerToken
            val taskLeash = it.containerLeash
            return LetterboxLifecycleEvent(
                type = change.asLetterboxLifecycleEventType(),
                taskId = taskId,
                taskBounds = taskBounds,
                letterboxBounds = letterboxBounds,
                taskLeash = taskLeash,
                containerToken = taskToken
            )
        }
        ProtoLog.w(WM_SHELL_APP_COMPAT, "$TAG: Task not found for taskId: $taskId")
        return null
    }
}
