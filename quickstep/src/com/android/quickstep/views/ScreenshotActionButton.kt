/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.quickstep.views

import android.content.Context
import android.util.AttributeSet

/** A button on the Overview Actions Bar for screenshotting the focused app. */
class ScreenshotActionButton : ActionButton {
    companion object {
        const val FLAG_MULTIPLE_TASKS_HIDE_SCREENSHOT = 1 shl 0
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    /** Show/hide the button when the focused task is a single/pair. */
    override fun updateForMultipleTasks(hasMultipleTasks: Boolean) {
        // Hidden for multiple tasks
        updateHiddenFlags(FLAG_MULTIPLE_TASKS_HIDE_SCREENSHOT, hasMultipleTasks)
    }
}
