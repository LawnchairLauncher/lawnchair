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

package com.android.quickstep

import android.content.Context
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.util.TaskCornerRadius
import com.android.systemui.shared.system.QuickStepContract

/**
 * Class for computing corner radius by interpolating between overview and fullscreen corner radius
 * with fullscreenProgress set in [setProgress].
 */
open class FullscreenDrawParams
@JvmOverloads
constructor(
    context: Context,
    private val taskCornerRadiusProvider: (Context) -> Float = ::computeTaskCornerRadius,
    private val windowCornerRadiusProvider: (Context) -> Float = ::computeWindowCornerRadius,
) : SafeCloseable {
    private var taskCornerRadius = 0f
    private var windowCornerRadius = 0f
    var currentCornerRadius = 0f

    init {
        updateCornerRadius(context)
    }

    /** Recomputes the start and end corner radius for the given Context. */
    fun updateCornerRadius(context: Context) {
        taskCornerRadius = taskCornerRadiusProvider(context)
        windowCornerRadius = windowCornerRadiusProvider(context)
    }

    /** Sets the progress in range [0, 1] */
    fun setProgress(fullscreenProgress: Float, parentScale: Float, taskViewScale: Float) {
        currentCornerRadius =
            Utilities.mapRange(fullscreenProgress, taskCornerRadius, windowCornerRadius) /
                parentScale /
                taskViewScale
    }

    override fun close() {}

    companion object {
        private fun computeTaskCornerRadius(context: Context): Float = TaskCornerRadius.get(context)

        private fun computeWindowCornerRadius(context: Context): Float {
            val activityContext: ActivityContext? = ActivityContext.lookupContextNoThrow(context)
            return if (
                activityContext?.deviceProfile?.isTaskbarPresent == true &&
                    DisplayController.isTransientTaskbar(context)
            ) {
                context.resources
                    .getDimensionPixelSize(R.dimen.persistent_taskbar_corner_radius)
                    .toFloat()
            } else {
                // The corner radius is fixed to match when Taskbar is persistent mode
                QuickStepContract.getWindowCornerRadius(context)
            }
        }
    }
}
