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

package com.android.launcher3.logging

import android.os.Process
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.util.SparseLongArray
import androidx.annotation.VisibleForTesting
import com.android.launcher3.LauncherConstants.TraceEvents.COLD_STARTUP_TRACE_METHOD_NAME
import com.android.launcher3.LauncherConstants.TraceEvents.SINGLE_TRACE_COOKIE
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC
import com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.Companion.LAUNCHER_LATENCY_PACKAGE_ID
import com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.LatencyType
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LockedUserState
import com.android.launcher3.views.ActivityContext

/** Interface to log launcher startup latency metrics. */
sealed interface StartupLatencyLogger {

    fun logWorkspaceLoadStartTime() {}

    /** Notes the end of an event. Final logs are pushed on [finishLogs] */
    fun logStart(event: LauncherLatencyEvent) {}

    /** Notes the start of an event. Final logs are pushed on [finishLogs] */
    fun logEnd(event: LauncherLatencyEvent) {}

    /**
     * Finishes the current logging session and returns a new logger to be used for the next session
     */
    fun finishLogs(workspaceCount: Int, isBindSync: Boolean): StartupLatencyLogger = this

    object NoOpLogger : StartupLatencyLogger

    @VisibleForTesting
    class ColdRebootStartupLogger(
        private val ctx: ActivityContext,
        private val timeProvider: () -> Long,
    ) : StartupLatencyLogger {

        @VisibleForTesting val startTimeByEvent = SparseLongArray()
        @VisibleForTesting val endTimeByEvent = SparseLongArray()

        private var cardinality: Int = -1

        init {
            Trace.beginAsyncSection(COLD_STARTUP_TRACE_METHOD_NAME, SINGLE_TRACE_COOKIE)
            logStart(LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION)
        }

        override fun logWorkspaceLoadStartTime() =
            logStart(LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC)

        override fun logStart(event: LauncherLatencyEvent) =
            startTimeByEvent.put(event.id, timeProvider.invoke())

        override fun logEnd(event: LauncherLatencyEvent) =
            endTimeByEvent.put(event.id, timeProvider.invoke())

        override fun finishLogs(workspaceCount: Int, isBindSync: Boolean): StartupLatencyLogger {
            if (!isBindSync) {
                cardinality = workspaceCount
                logEnd(LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC)
            }

            Executors.MAIN_EXECUTOR.handler.postAtFrontOfQueue {
                Log.i(
                    "Launcher",
                    "LauncherReady. " +
                        "User: " +
                        Process.myUserHandle() +
                        " TS: " +
                        SystemClock.uptimeMillis(),
                )
                logEnd(LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION)
                commitLogs()
            }
            Trace.endAsyncSection(COLD_STARTUP_TRACE_METHOD_NAME, SINGLE_TRACE_COOKIE)
            return NoOpLogger
        }

        private fun commitLogs() {
            val instanceId = InstanceIdSequence().newInstanceId()
            val logger = ctx.statsLogManager
            for (event in LauncherLatencyEvent.entries) {
                val start = startTimeByEvent.get(event.id)
                val end = endTimeByEvent.get(event.id)
                val duration = end - start
                if (start != 0L && end != 0L) {
                    logger
                        .latencyLogger()
                        .withType(LatencyType.COLD_DEVICE_REBOOTING)
                        .withInstanceId(instanceId)
                        .withLatency(duration)
                        .withPackageId(LAUNCHER_LATENCY_PACKAGE_ID)
                        .withCardinality(cardinality)
                        .log(event)
                }
            }
        }
    }

    companion object {

        private var isNewProcess: Boolean = true

        @JvmStatic
        fun getLogger(ctx: ActivityContext): StartupLatencyLogger {
            val isColdStartupAfterReboot =
                isNewProcess &&
                    !LockedUserState.get(ctx.asContext()).isUserUnlockedAtLauncherStartup
            isNewProcess = false
            return if (isColdStartupAfterReboot)
                ColdRebootStartupLogger(ctx) { SystemClock.elapsedRealtime() }
            else NoOpLogger
        }
    }
}
