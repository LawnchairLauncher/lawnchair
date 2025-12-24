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

package com.android.launcher3.taskbar.handoff

import android.companion.datatransfer.continuity.RemoteTask
import android.companion.datatransfer.continuity.TaskContinuityManager
import android.os.Handler
import android.util.Log
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import java.io.PrintWriter

/**
 * Controller for the Handoff feature in the Taskbar.
 *
 * This controller is responsible for managing the Handoff suggestions and loading the metadata
 * (label and icon) for them. It also updates the suggestions in the UI when the metadata is loaded.
 */
class TaskbarHandoffController(val taskbarActivityContext: TaskbarActivityContext) :
    LoggableTaskbarController, TaskContinuityManager.RemoteTaskListener {

    private var taskContinuityManager: TaskContinuityManager? = null
    private val suggestionList = HandoffSuggestionList()
    private val suggestionMetadataLoader =
        HandoffSuggestionMetadataLoader(
            taskbarActivityContext,
            Handler(taskbarActivityContext.mainLooper),
        )

    /** A list of currently active Handoff suggestions. */
    public val suggestions: List<HandoffSuggestion>
        get() {
            return suggestionList.suggestions
        }

    /** Starts the controller. */
    public fun init() {
        if (android.companion.Flags.enableTaskContinuity()) {
            taskContinuityManager =
                taskbarActivityContext.applicationContext.getSystemService(
                    TaskContinuityManager::class.java
                )

            taskContinuityManager?.registerRemoteTaskListener(
                taskbarActivityContext.mainExecutor,
                this,
            )
        }
    }

    /** Stops the controller. */
    public fun onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "Stopping controller.")
        }

        suggestionMetadataLoader.cancelPendingLoads()
        suggestionList.clear()
        taskContinuityManager?.unregisterRemoteTaskListener(this)
    }

    public override fun onRemoteTasksChanged(remoteTasks: List<RemoteTask>) {
        if (DEBUG) {
            Log.d(TAG, "onRemoteTasksChanged: updating suggestions.")
        }

        suggestionList.updateSuggestions(remoteTasks)
        suggestionMetadataLoader.loadMetadata(suggestionList.suggestions) { suggestion ->
            if (DEBUG) {
                Log.d(
                    TAG,
                    "HandoffSuggestion metadata updated for deviceId ${suggestion.deviceId}.")
            }
            // TODO: joeantonetti - Update the suggestion in the UI.
        }
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "TaskbarHandoffController:")
        pw.println(prefix + "\tsuggestions=" + suggestionList.suggestions)
    }

    private companion object {
        const val DEBUG = false
        const val TAG = "TaskbarHandoffController"
    }
}
