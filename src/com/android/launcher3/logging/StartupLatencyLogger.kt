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

package com.android.launcher3.logging

import androidx.annotation.MainThread

/** Interface to log launcher startup latency metrics. */
interface StartupLatencyLogger {

    @MainThread fun log(): StartupLatencyLogger = this

    @MainThread fun logWorkspaceLoadStartTime(): StartupLatencyLogger = this

    /**
     * Log size of workspace. Larger number of workspace items (icons, folders, widgets) means
     * longer latency to initialize workspace.
     */
    @MainThread fun logCardinality(cardinality: Int): StartupLatencyLogger = this

    @MainThread
    fun logStart(event: StatsLogManager.LauncherLatencyEvent): StartupLatencyLogger = this

    @MainThread
    fun logStart(
        event: StatsLogManager.LauncherLatencyEvent,
        startTimeMs: Long
    ): StartupLatencyLogger = this

    @MainThread fun logEnd(event: StatsLogManager.LauncherLatencyEvent): StartupLatencyLogger = this

    @MainThread
    fun logEnd(event: StatsLogManager.LauncherLatencyEvent, endTimeMs: Long): StartupLatencyLogger =
        this

    @MainThread fun reset()

    companion object {
        val NO_OP: StartupLatencyLogger =
            object : StartupLatencyLogger {
                override fun reset() {}
            }
    }
}
