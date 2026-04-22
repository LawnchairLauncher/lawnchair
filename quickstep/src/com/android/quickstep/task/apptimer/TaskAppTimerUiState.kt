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

package com.android.quickstep.task.apptimer

import androidx.annotation.IdRes
import java.time.Duration

/**
 * UI state of the digital wellbeing app timer toast
 *
 * @property taskDescription description of the task for which the timer is being displayed.
 */
sealed class TaskAppTimerUiState(open val taskDescription: String?) {
    /** Timer information not available in UI. */
    data object Uninitialized : TaskAppTimerUiState(null)

    /** No timer information to display */
    data class NoTimer(override val taskDescription: String?) :
        TaskAppTimerUiState(taskDescription)

    /**
     * Represents the UI state necessary to show an app timer on a task
     *
     * @property timeRemaining time remaining on the app timer for the application.
     * @property taskDescription description of the task for which the timer is being displayed.
     * @property taskPackageName package name for of the top component for the task's app.
     * @property accessibilityActionId action id to use for tap like accessibility actions on this
     *   timer.
     */
    data class Timer(
        val timeRemaining: Duration,
        override val taskDescription: String?,
        val taskPackageName: String,
        @IdRes val accessibilityActionId: Int,
    ) : TaskAppTimerUiState(taskDescription)
}
