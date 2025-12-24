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
import android.graphics.drawable.Drawable

/** A suggestion for a remote application that can be handed off to this device. */
class HandoffSuggestion {

    data class Metadata(val label: String, val icon: Drawable)

    var remoteTask: RemoteTask
        private set

    /** The device ID of the remote device that this suggestion is for. */
    val deviceId: Int
        get() = remoteTask.deviceId

    /** Metadata for this suggestion. This is `null` until it is loaded. */
    var metadata: Metadata? = null

    constructor(remoteTask: RemoteTask) {
        this.remoteTask = remoteTask
    }

    /**
     * Updates the suggested application with the given remote task.
     *
     * If the remote task is the same as the previous remote task, this is a no-op. If the
     * application has changed, the associated metadata is cleared to indicate that a reload is
     * needed.
     */
    fun updateRemoteTask(remoteTask: RemoteTask) {
        if (remoteTask.id != this.remoteTask.id) {
            this.remoteTask = remoteTask
            this.metadata = null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HandoffSuggestion) return false

        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId
}
