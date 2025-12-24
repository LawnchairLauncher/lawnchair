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

package com.android.wm.shell.shared.bubbles.logging

import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.PrintWriter

/**
 * Logs debug events related to bubbles.
 *
 * By default, this logger uses [BubbleEventHistoryLogger] for event storage. Additional logging
 * behaviors can be introduced by adding more loggers via the [addLogger] method.
 *
 * The [dump] method outputs the history collected by the default [BubbleEventHistoryLogger].
 *
 * For logging parameters and methods please refer to [DebugLogger] class.
 */
object BubbleLog {

    const val TAG = "BubbleLog"

    private val bubbleEventHistoryLogger = BubbleEventHistoryLogger()
    @VisibleForTesting val loggers = mutableListOf<DebugLogger>(bubbleEventHistoryLogger)

    /**
     * Adds a new [DebugLogger] to the list of loggers used by this class.
     *
     * When events are logged (e.g., via the [d] method), they will be dispatched to all registered
     * loggers, including any added through this method.
     *
     * @param debugLogger The [DebugLogger] instance to add.
     */
    @JvmStatic
    fun addLogger(debugLogger: DebugLogger) {
        loggers.add(debugLogger)
    }

    /** Logs a DEBUG level message for all registered [DebugLogger]s. */
    @JvmOverloads
    @JvmStatic
    fun d(message: String, vararg parameters: Any = emptyArray(), eventData: String? = null) {
        logSafelyForAllLoggers { logger -> logger.d(message, *parameters, eventData = eventData) }
    }

    /** Logs a VERBOSE level message for all registered [DebugLogger]s. */
    @JvmOverloads
    @JvmStatic
    fun v(message: String, vararg parameters: Any = emptyArray(), eventData: String? = null) {
        logSafelyForAllLoggers { logger -> logger.v(message, *parameters, eventData = eventData) }
    }

    /** Logs an INFO level message for all registered [DebugLogger]s. */
    @JvmOverloads
    @JvmStatic
    fun i(message: String, vararg parameters: Any = emptyArray(), eventData: String? = null) {
        logSafelyForAllLoggers { logger -> logger.i(message, *parameters, eventData = eventData) }
    }

    /** Logs a WARNING level message for all registered [DebugLogger]s. */
    @JvmOverloads
    @JvmStatic
    fun w(message: String, vararg parameters: Any = emptyArray(), eventData: String? = null) {
        logSafelyForAllLoggers { logger -> logger.w(message, *parameters, eventData = eventData) }
    }

    /** Logs an ERROR level message for all registered [DebugLogger]s. */
    @JvmOverloads
    @JvmStatic
    fun e(message: String, vararg parameters: Any = emptyArray(), eventData: String? = null) {
        logSafelyForAllLoggers { logger -> logger.e(message, *parameters, eventData = eventData) }
    }

    private inline fun logSafelyForAllLoggers(logFunction: (DebugLogger) -> Unit) {
        for (logger in loggers) {
            try {
                logFunction.invoke(logger)
            } catch (e: Exception) {
                Log.e(TAG, "Exception while logging for $logger", e)
            }
        }
    }

    @JvmStatic
    fun dump(pw: PrintWriter, prefix: String = "") {
        bubbleEventHistoryLogger.dump(pw, prefix)
    }
}
