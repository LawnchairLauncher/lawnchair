/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.log

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Trace
import android.provider.Settings
import com.android.systemui.log.core.LogLevel

/**
 * Version of [LogcatEchoTracker] for debuggable builds
 *
 * The log level of individual buffers or tags can be controlled via global settings:
 * ```
 * # Echo any message to <bufferName> of <level> or higher
 * $ adb shell settings put global systemui/buffer/<bufferName> <level>
 *
 * # Echo any message of <tag> and of <level> or higher
 * $ adb shell settings put global systemui/tag/<tag> <level>
 * ```
 */
class LogcatEchoTrackerDebug private constructor(private val contentResolver: ContentResolver) :
    LogcatEchoTracker {
    private val cachedBufferLevels: MutableMap<String, LogLevel> = mutableMapOf()
    private val cachedTagLevels: MutableMap<String, LogLevel> = mutableMapOf()
    override val logInBackgroundThread = true

    companion object Factory {
        @JvmStatic
        fun create(contentResolver: ContentResolver, mainLooper: Looper): LogcatEchoTrackerDebug {
            val tracker = LogcatEchoTrackerDebug(contentResolver)
            tracker.attach(mainLooper)
            return tracker
        }
    }

    private fun clearCache() {
        Trace.beginSection("LogcatEchoTrackerDebug#clearCache")
        cachedBufferLevels.clear()
        Trace.endSection()
    }

    private fun attach(mainLooper: Looper) {
        Trace.beginSection("LogcatEchoTrackerDebug#attach")
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(BUFFER_PATH),
            true,
            object : ContentObserver(Handler(mainLooper)) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    clearCache()
                }
            }
        )

        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(TAG_PATH),
            true,
            object : ContentObserver(Handler(mainLooper)) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    clearCache()
                }
            }
        )
        Trace.endSection()
    }

    /** Whether [bufferName] should echo messages of [level] or higher to logcat. */
    @Synchronized
    override fun isBufferLoggable(bufferName: String, level: LogLevel): Boolean {
        return level.ordinal >= getLogLevel(bufferName, BUFFER_PATH, cachedBufferLevels).ordinal
    }

    /** Whether [tagName] should echo messages of [level] or higher to logcat. */
    @Synchronized
    override fun isTagLoggable(tagName: String, level: LogLevel): Boolean {
        return level >= getLogLevel(tagName, TAG_PATH, cachedTagLevels)
    }

    private fun getLogLevel(
        name: String,
        path: String,
        cache: MutableMap<String, LogLevel>
    ): LogLevel {
        return cache[name] ?: readSetting("$path/$name").also { cache[name] = it }
    }

    private fun readSetting(path: String): LogLevel {
        return try {
            Trace.beginSection("LogcatEchoTrackerDebug#readSetting")
            parseProp(Settings.Global.getString(contentResolver, path))
        } catch (_: Settings.SettingNotFoundException) {
            DEFAULT_LEVEL
        } finally {
            Trace.endSection()
        }
    }

    private fun parseProp(propValue: String?): LogLevel {
        return when (propValue?.lowercase()) {
            "verbose" -> LogLevel.VERBOSE
            "v" -> LogLevel.VERBOSE
            "debug" -> LogLevel.DEBUG
            "d" -> LogLevel.DEBUG
            "info" -> LogLevel.INFO
            "i" -> LogLevel.INFO
            "warning" -> LogLevel.WARNING
            "warn" -> LogLevel.WARNING
            "w" -> LogLevel.WARNING
            "error" -> LogLevel.ERROR
            "e" -> LogLevel.ERROR
            "assert" -> LogLevel.WTF
            "wtf" -> LogLevel.WTF
            else -> DEFAULT_LEVEL
        }
    }
}

private val DEFAULT_LEVEL = LogLevel.WARNING
private const val BUFFER_PATH = "systemui/buffer"
private const val TAG_PATH = "systemui/tag"
