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

import android.icu.text.SimpleDateFormat
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.android.wm.shell.Flags
import java.io.PrintWriter
import java.util.Locale

/**
 * An implementation of [DebugLogger] that stores a history of events, respecting the [MAX_EVENTS]
 * limit. It can add events history as part of a system dump method.
 */
class BubbleEventHistoryLogger : DebugLogger {

    @VisibleForTesting
    val recentEvents: MutableList<BubbleEvent> = mutableListOf()

    override fun d(message: String, vararg parameters: Any, eventData: String?) {
        logEvent("d: ${TextUtils.formatSimple(message, *parameters)}", eventData)
    }

    override fun v(message: String, vararg parameters: Any, eventData: String?) {
        logEvent("v: ${TextUtils.formatSimple(message, *parameters)}", eventData)
    }

    override fun i(message: String, vararg parameters: Any, eventData: String?) {
        logEvent("i: ${TextUtils.formatSimple(message, *parameters)}", eventData)
    }

    override fun w(message: String, vararg parameters: Any, eventData: String?) {
        logEvent("w: ${TextUtils.formatSimple(message, *parameters)}", eventData)
    }

    override fun e(message: String, vararg parameters: Any, eventData: String?) {
        logEvent("e: ${TextUtils.formatSimple(message, *parameters)}", eventData)
    }

    @VisibleForTesting
    @Synchronized
    fun logEvent(
        title: String,
        eventData: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        if (!Flags.enableBubbleEventHistoryLogs()) return
        if (recentEvents.size >= MAX_EVENTS) {
            recentEvents.removeAt(0)
        }
        recentEvents.add(BubbleEvent(title, eventData, timestamp))
    }

    /**
     * Dumps the collected events history to the provided [PrintWriter], adding the [prefix] at the
     * beginning of each line.
     */
    fun dump(pw: PrintWriter, prefix: String = "") {
        if (!Flags.enableBubbleEventHistoryLogs()) return
        val recentEventsCopy = synchronized(this) { ArrayList(recentEvents) }
        pw.println("${prefix}Bubbles events history:")
        recentEventsCopy.reversed().forEach { event ->
            val eventFormattedTime = DATE_FORMATTER.format(event.timestamp)
            var eventData = ""
            if (!event.eventData.isNullOrBlank()) {
                eventData = " | ${event.eventData}"
            }
            pw.println("$prefix  $eventFormattedTime ${event.title}$eventData")
        }
    }

    companion object {
        const val DATE_FORMAT = "MM-dd HH:mm:ss.SSS"
        const val MAX_EVENTS: Int = 25
        @VisibleForTesting
        val DATE_FORMATTER = SimpleDateFormat(DATE_FORMAT, Locale.US)
    }
}
