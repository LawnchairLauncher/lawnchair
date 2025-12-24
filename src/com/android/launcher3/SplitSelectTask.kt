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

package com.android.launcher3

import android.app.PendingIntent
import android.content.Intent

/** Data class to hold split select task. */
data class SplitSelectTask(
    val taskId: Int = INVALID_TASK_ID,
    val intent: Intent? = null,
    val pendingIntent: PendingIntent? = null,
) {
    val isIntentSet
        get() = taskId != INVALID_TASK_ID || intent != null || pendingIntent != null

    companion object {
        // Copied from android.app.ActivityTaskManager.INVALID_TASK_ID, which is not public to
        // com.android.launcher3
        const val INVALID_TASK_ID = -1
    }
}
