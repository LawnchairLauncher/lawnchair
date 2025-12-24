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
import android.util.Log

/** List of [HandoffSuggestion] instances that are currently available for handoff. */
class HandoffSuggestionList {

    private val previousSuggestions = mutableMapOf<Int, HandoffSuggestion>()

    /** Currently active Handoff suggestions. */
    val suggestions: List<HandoffSuggestion>
        get() = previousSuggestions.values.toList()

    /**
     * Updates the list of suggestions based on the given list of remote tasks, returning `true` if
     * a new suggestion was added or an existing suggestion was removed.
     */
    fun updateSuggestions(remoteTasks: List<RemoteTask>): Boolean {
        Log.v(TAG, "Updating handoff suggestions.")
        var didChange = false
        for (remoteTask in remoteTasks) {
            val suggestion = previousSuggestions[remoteTask.deviceId]
            if (suggestion == null) {
                previousSuggestions[remoteTask.deviceId] = HandoffSuggestion(remoteTask)

                Log.v(TAG, "Adding new suggestion for deviceId ${remoteTask.deviceId}")
                didChange = true
            } else {
                Log.v(TAG, "Updating suggestion for deviceId ${remoteTask.deviceId}")
                previousSuggestions[remoteTask.deviceId]?.updateRemoteTask(remoteTask)
            }
        }

        for (deviceId in previousSuggestions.keys) {
            if (remoteTasks.none { it.deviceId == deviceId }) {
                Log.v(TAG, "Removing suggestion for deviceId ${deviceId}")
                previousSuggestions.remove(deviceId)
                didChange = true
            }
        }

        Log.v(TAG, "Finished updating handoff suggestions. didChange=$didChange")
        return didChange
    }

    /** Clears the list of suggestions. */
    public fun clear() {
        Log.v(TAG, "Clearing Handoff suggestions.")
        previousSuggestions.clear()
    }

    private companion object {
        const val TAG = "HandoffSuggestionList"
    }
}
