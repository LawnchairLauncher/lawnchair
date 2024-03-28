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
import com.android.launcher3.util.Preconditions

/** Logger for logging Launcher activity's startup latency. */
open class ColdRebootStartupLatencyLogger : StartupLatencyLogger {

    companion object {
        const val TAG = "ColdRebootStartupLatencyLogger"
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

    // ColdRebootStartupLatencyLogger should only send launcher startup logs once in each launcher
    // activity lifecycle. After launcher activity startup is completed, the logger should be torn
    // down and reject all logging calls. This flag should be checked at all APIs to prevent logging
    // invalid startup metrics (such as loading workspace in screen rotation).
    var isTornDown = false
    private var isInTest = false

    /** Subclass can override this method to handle collected latency metrics. */
    @MainThread
    override fun log(): ColdRebootStartupLatencyLogger {
        return this
    }

    @MainThread
    override fun logWorkspaceLoadStartTime() =
        logWorkspaceLoadStartTime(SystemClock.elapsedRealtime())

    @VisibleForTesting
    @MainThread
    fun logWorkspaceLoadStartTime(startTimeMs: Long): ColdRebootStartupLatencyLogger {
        Preconditions.assertUIThread()
        if (isTornDown) {
            return this
        }
        workspaceLoadStartTime = startTimeMs
        return this
    }

    /**
     * Log size of workspace. Larger number of workspace items (icons, folders, widgets) means
     * longer latency to initialize workspace.
     */
    @MainThread
    override fun logCardinality(cardinality: Int): ColdRebootStartupLatencyLogger {
        Preconditions.assertUIThread()
        if (isTornDown) {
            return this
        }
        this.cardinality = cardinality
        return this
    }

    @MainThread
    override fun logStart(event: LauncherLatencyEvent) =
        logStart(event, SystemClock.elapsedRealtime())

    @MainThread
    override fun logStart(
        event: LauncherLatencyEvent,
        startTimeMs: Long
    ): ColdRebootStartupLatencyLogger {
        // In unit test no looper is attached to current thread
        Preconditions.assertUIThread()
        if (isTornDown) {
            return this
        }
        if (validateLoggingEventAtStart(event)) {
            startTimeByEvent.put(event.id, startTimeMs)
        }
        return this
    }

    @MainThread
    override fun logEnd(event: LauncherLatencyEvent) = logEnd(event, SystemClock.elapsedRealtime())

    @MainThread
    override fun logEnd(
        event: LauncherLatencyEvent,
        endTimeMs: Long
    ): ColdRebootStartupLatencyLogger {
        // In unit test no looper is attached to current thread
        Preconditions.assertUIThread()
        if (isTornDown) {
            return this
        }
        maybeLogStartOfWorkspaceLoadTime(event)
        if (validateLoggingEventAtEnd(event)) {
            endTimeByEvent.put(event.id, endTimeMs)
        }

        return this
    }

    @MainThread
    override fun reset() {
        // In unit test no looper is attached to current thread
        Preconditions.assertUIThread()
        startTimeByEvent.clear()
        endTimeByEvent.clear()
        cardinality = UNSET_INT
        workspaceLoadStartTime = UNSET_LONG
        isTornDown = true
    }

    @MainThread
    private fun maybeLogStartOfWorkspaceLoadTime(event: LauncherLatencyEvent) {
        if (workspaceLoadStartTime == UNSET_LONG) {
            return
        }
        if (event == LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC) {
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
