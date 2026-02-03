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

package com.android.quickstep.util

import android.app.TaskInfo
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import com.android.systemui.shared.recents.model.Task

/** Whether this displayId belongs to an external display */
val Int.isExternalDisplay
    get() = !isDefaultDisplay

/** Whether this displayId belongs to the default display */
val Int.isDefaultDisplay
    get() = this == DEFAULT_DISPLAY

val Int?.safeDisplayId
    get() =
        this.let { displayId ->
            when (displayId) {
                null -> DEFAULT_DISPLAY
                INVALID_DISPLAY -> DEFAULT_DISPLAY
                else -> displayId
            }
        }

/** Returns displayId of this [Task], default to [DEFAULT_DISPLAY] */
val Task?.safeDisplayId
    get() = this?.key?.displayId.safeDisplayId

/** Returns if this task belongs tto [DEFAULT_DISPLAY] */
val Task?.isExternalDisplay
    get() = safeDisplayId.isExternalDisplay

/** Returns displayId of this [TaskInfo], default to [DEFAULT_DISPLAY] */
val TaskInfo?.safeDisplayId
    get() = this?.displayId.safeDisplayId
