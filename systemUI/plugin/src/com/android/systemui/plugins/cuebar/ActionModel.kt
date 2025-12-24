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

package com.android.systemui.plugins.cuebar

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.graphics.drawable.Drawable
import com.android.systemui.plugins.annotations.ProvidesInterface

@ProvidesInterface(version = ActionModel.VERSION)
data class ActionModel(
    val icon: IconModel,
    val label: String,
    val attribution: String?,
    val onPerformAction: () -> Unit,
    // This is used to perform a long click on the expanded action chip.
    val onPerformLongClick: () -> Unit,
    val taskId: Int = INVALID_TASK_ID,
    val actionType: String? = null,
    val oneTapEnabled: Boolean = false,
    val oneTapDelayMs: Long = 0L,
) {
    companion object {
        const val VERSION = 1
    }
}

@ProvidesInterface(version = IconModel.VERSION)
data class IconModel(val small: Drawable, val large: Drawable, val iconId: String) {
    companion object {
        const val VERSION = 1
    }
}
