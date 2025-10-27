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
package com.android.launcher3.taskbar.growth

import android.content.Context
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllers
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.launcher3.util.DisplayController
import com.android.launcher3.views.ActivityContext
import java.io.PrintWriter

/** Controls nudge lifecycles. */
class NudgeController(context: Context) : LoggableTaskbarController {

    protected val activityContext: TaskbarActivityContext = ActivityContext.lookupContext(context)

    private val isNudgeEnabled: Boolean
        get() {
            return !Utilities.isRunningInTestHarness() &&
                !activityContext.isPhoneMode &&
                !activityContext.isTinyTaskbar
        }

    private lateinit var controllers: TaskbarControllers

    fun init(controllers: TaskbarControllers) {
        this.controllers = controllers
    }

    fun maybeShow(payload: NudgePayload) {
        if (!isNudgeEnabled || !DisplayController.isTransientTaskbar(activityContext)) {
            return
        }
        // TODO: b/398033012 - create and show nudge view based on the payload.
    }

    /** Closes the current [nudgeView]. */
    fun hide() {
        // TODO: b/398033012 - hide the nudge view.
    }

    override fun dumpLogs(prefix: String?, pw: PrintWriter?) {
        pw?.println(prefix + "NudgeController:")
        pw?.println("$prefix\tisNudgeEnabled=$isNudgeEnabled")
    }
}
