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
 * limitations under the License
 */

package com.android.launcher3.util

import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A utility class to record and log events. Events are stored in a fixed size array and old logs
 * are purged as new events come.
 */
class EventLogArray(private val name: String, size: Int) {

    companion object {
        private const val TYPE_ONE_OFF = 0
        private const val TYPE_FLOAT = 1
        private const val TYPE_INTEGER = 2
        private const val TYPE_BOOL_TRUE = 3
        private const val TYPE_BOOL_FALSE = 4
        private fun isEntrySame(entry: EventEntry?, type: Int, event: String): Boolean {
            return entry != null && entry.type == type && entry.event == event
        }
    }

    private val logs: Array<EventEntry?>
    private var nextIndex = 0

    init {
        logs = arrayOfNulls(size)
    }

    fun addLog(event: String) {
        addLog(TYPE_ONE_OFF, event, 0f)
    }

    fun addLog(event: String, extras: Int) {
        addLog(TYPE_INTEGER, event, extras.toFloat())
    }

    fun addLog(event: String, extras: Float) {
        addLog(TYPE_FLOAT, event, extras)
    }

    fun addLog(event: String, extras: Boolean) {
        addLog(if (extras) TYPE_BOOL_TRUE else TYPE_BOOL_FALSE, event, 0f)
    }

    private fun addLog(type: Int, event: String, extras: Float) {
        // Merge the logs if it's a duplicate
        val last = (nextIndex + logs.size - 1) % logs.size
        val secondLast = (nextIndex + logs.size - 2) % logs.size
        if (isEntrySame(logs[last], type, event) && isEntrySame(logs[secondLast], type, event)) {
            logs[last]!!.update(type, event, extras)
            logs[secondLast]!!.duplicateCount++
            return
        }
        if (logs[nextIndex] == null) {
            logs[nextIndex] = EventEntry()
        }
        logs[nextIndex]!!.update(type, event, extras)
        nextIndex = (nextIndex + 1) % logs.size
    }

    fun dump(prefix: String, writer: PrintWriter) {
        writer.println("$prefix$name event history:")
        val sdf = SimpleDateFormat("  HH:mm:ss.SSSZ  ", Locale.US)
        val date = Date()
        for (i in logs.indices) {
            val log = logs[(nextIndex + logs.size - i - 1) % logs.size] ?: continue
            date.time = log.time
            val msg = StringBuilder(prefix).append(sdf.format(date)).append(log.event)
            when (log.type) {
                TYPE_BOOL_FALSE -> msg.append(": false")
                TYPE_BOOL_TRUE -> msg.append(": true")
                TYPE_FLOAT -> msg.append(": ").append(log.extras)
                TYPE_INTEGER -> msg.append(": ").append(log.extras.toInt())
                else -> {}
            }
            if (log.duplicateCount > 0) {
                msg.append(" & ").append(log.duplicateCount).append(" similar events")
            }
            writer.println(msg)
        }
    }

    /** A single event entry. */
    private class EventEntry {
        var type = 0
        var event: String? = null
        var extras = 0f
        var time: Long = 0
        var duplicateCount = 0
        fun update(type: Int, event: String, extras: Float) {
            this.type = type
            this.event = event
            this.extras = extras
            time = System.currentTimeMillis()
            duplicateCount = 0
        }
    }
}
