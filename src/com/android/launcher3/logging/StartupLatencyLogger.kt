package com.android.launcher3.logging

import android.os.SystemClock
import android.util.Log
import android.util.SparseLongArray
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.core.util.contains
import androidx.core.util.isEmpty
import com.android.launcher3.BuildConfig
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent
import com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.LatencyType
import com.android.launcher3.util.Preconditions

/** Logger for logging Launcher activity's startup latency. */
open class StartupLatencyLogger(val latencyType: LatencyType) {

    companion object {
        const val TAG = "LauncherStartupLatencyLogger"
        const val UNSET_INT = -1
        const val UNSET_LONG = -1L
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    val startTimeByEvent = SparseLongArray()
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    val endTimeByEvent = SparseLongArray()

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED) var cardinality: Int = UNSET_INT
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    var workspaceLoadStartTime: Long = UNSET_LONG

    private var isInTest = false

    /** Subclass can override this method to handle collected latency metrics. */
    @MainThread
    open fun log(): StartupLatencyLogger {
        return this
    }

    @MainThread
    fun logWorkspaceLoadStartTime() = logWorkspaceLoadStartTime(SystemClock.elapsedRealtime())

    @VisibleForTesting
    @MainThread
    fun logWorkspaceLoadStartTime(startTimeMs: Long): StartupLatencyLogger {
        Preconditions.assertUIThread()
        workspaceLoadStartTime = startTimeMs
        return this
    }

    /**
     * Log size of workspace. Larger number of workspace items (icons, folders, widgets) means
     * longer latency to initialize workspace.
     */
    @MainThread
    fun logCardinality(cardinality: Int): StartupLatencyLogger {
        Preconditions.assertUIThread()
        this.cardinality = cardinality
        return this
    }

    @MainThread
    fun logStart(event: LauncherLatencyEvent) = logStart(event, SystemClock.elapsedRealtime())

    @MainThread
    fun logStart(event: LauncherLatencyEvent, startTimeMs: Long): StartupLatencyLogger {
        // In unit test no looper is attached to current thread
        Preconditions.assertUIThread()
        if (validateLoggingEventAtStart(event)) {
            startTimeByEvent.put(event.id, startTimeMs)
        }
        return this
    }

    @MainThread
    fun logEnd(event: LauncherLatencyEvent) = logEnd(event, SystemClock.elapsedRealtime())

    @MainThread
    fun logEnd(event: LauncherLatencyEvent, endTimeMs: Long): StartupLatencyLogger {
        // In unit test no looper is attached to current thread
        Preconditions.assertUIThread()
        maybeLogStartOfWorkspaceLoadTime(event)
        if (validateLoggingEventAtEnd(event)) {
            endTimeByEvent.put(event.id, endTimeMs)
        }

        return this
    }

    @MainThread
    fun reset() {
        // In unit test no looper is attached to current thread
        Preconditions.assertUIThread()
        startTimeByEvent.clear()
        endTimeByEvent.clear()
        cardinality = UNSET_INT
        workspaceLoadStartTime = UNSET_LONG
    }

    @MainThread
    private fun maybeLogStartOfWorkspaceLoadTime(event: LauncherLatencyEvent) {
        if (workspaceLoadStartTime == UNSET_LONG) {
            return
        }
        if (
            event == LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC ||
                event == LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC
        ) {
            logStart(event, workspaceLoadStartTime)
            workspaceLoadStartTime = UNSET_LONG
        }
    }

    /** @return true if we can log start of [LauncherLatencyEvent] and vice versa. */
    @MainThread
    private fun validateLoggingEventAtStart(event: LauncherLatencyEvent): Boolean {
        if (!BuildConfig.IS_STUDIO_BUILD && !isInTest) {
            return true
        }
        if (startTimeByEvent.contains(event.id)) {
            Log.e(TAG, "Cannot restart same ${event.name} event")
            return false
        } else if (
            startTimeByEvent.isEmpty() &&
                event != LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION
        ) {
            Log.e(
                TAG,
                "The first log start event must be " +
                    "${LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.name}.",
            )
            return false
        } else if (
            event == LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC &&
                startTimeByEvent.get(
                    LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC.id
                ) != 0L
        ) {
            Log.e(
                TAG,
                "Cannot start ${LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC.name} event after ${LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC.name} starts",
            )
            return false
        } else if (
            event == LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC &&
                startTimeByEvent.get(
                    LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC.id
                ) != 0L
        ) {
            Log.e(
                TAG,
                "Cannot start ${LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC.name} event after ${LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC.name} starts",
            )
            return false
        }

        return true
    }

    /** @return true if we can log end of [LauncherLatencyEvent] and vice versa. */
    @MainThread
    private fun validateLoggingEventAtEnd(event: LauncherLatencyEvent): Boolean {
        if (!BuildConfig.IS_STUDIO_BUILD && !isInTest) {
            return true
        }
        if (!startTimeByEvent.contains(event.id)) {
            Log.e(TAG, "Cannot end ${event.name} event before starting it")
            return false
        } else if (endTimeByEvent.contains(event.id)) {
            Log.e(TAG, "Cannot end same ${event.name} event again")
            return false
        } else if (
            event != LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION &&
                endTimeByEvent.contains(
                    LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.id
                )
        ) {
            Log.e(
                TAG,
                "Cannot end ${event.name} event after ${LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.name}",
            )
            return false
        }
        return true
    }

    @VisibleForTesting
    fun setIsInTest() {
        isInTest = true
    }
}
