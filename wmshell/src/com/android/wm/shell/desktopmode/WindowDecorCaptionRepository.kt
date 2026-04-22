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

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import com.android.wm.shell.desktopmode.CaptionState.AppHandle
import com.android.wm.shell.desktopmode.CaptionState.AppHeader
import com.android.wm.shell.desktopmode.CaptionState.NoCaption
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Repository to observe caption state. */
class WindowDecorCaptionRepository {
    private val _captionStateFlow = MutableStateFlow<CaptionState>(NoCaption())
    /** Observer for app handle state changes. */
    val captionStateFlow: StateFlow<CaptionState> = _captionStateFlow
    private val _appToWebUsageFlow = MutableSharedFlow<Unit>()
    /** Observer for App-to-Web usage. */
    val appToWebUsageFlow = _appToWebUsageFlow

    /** Notifies [captionStateFlow] if there is a change to caption state. */
    fun notifyCaptionChanged(captionState: CaptionState) {
        _captionStateFlow.value = captionState
    }

    /** Notifies [appToWebUsageFlow] if App-to-Web feature is used. */
    fun onAppToWebUsage() {
        _appToWebUsageFlow.tryEmit(Unit)
    }
}

/**
 * Represents the current status of the caption.
 *
 * It can be one of three options:
 * * [AppHandle]: Indicating that there is at least one visible app handle on the screen.
 * * [AppHeader]: Indicating that there is at least one visible app chip on the screen.
 * * [NoCaption]: Signifying that no caption handle visible for the given task.
 */
sealed class CaptionState {
    abstract val isFocused: Boolean

    data class AppHandle(
        val runningTaskInfo: RunningTaskInfo,
        val isHandleMenuExpanded: Boolean,
        val globalAppHandleBounds: Rect,
        val isCapturedLinkAvailable: Boolean,
        val appHandleIdentifier: AppHandleIdentifier,
        override val isFocused: Boolean,
    ) : CaptionState()

    data class AppHeader(
        val runningTaskInfo: RunningTaskInfo,
        val isHeaderMenuExpanded: Boolean,
        val globalAppChipBounds: Rect,
        val isCapturedLinkAvailable: Boolean,
        override val isFocused: Boolean,
    ) : CaptionState()

    data class NoCaption(val taskId: Int = INVALID_TASK_ID) : CaptionState() {
        override val isFocused = false
    }

    private companion object {
        private const val INVALID_TASK_ID = -1
    }
}
