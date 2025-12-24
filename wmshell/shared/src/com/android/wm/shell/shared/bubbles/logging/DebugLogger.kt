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

/**
 * Bubbles debug logger interface.
 *
 * Usage of this API is similar to the `android.utils.Log` class.
 *
 * Instead of plain text log messages, each call consists of a `messageString` (which is a format
 * string for the log message and must be a string literal or a concatenation of string literals)
 * and a vararg array of parameters for the formatter.
 *
 * The syntax for the message string is based on [android.text.TextUtils.formatSimple].
 *
 * All methods are thread safe.
 */
interface DebugLogger {

    /**
     * Logs a DEBUG level message.
     *
     * The [message] is a format string, with [parameters] substituted into it. An optional
     * [eventData] string may also be provided, which implementations can include in the log output.
     */
    fun d(message: String, vararg parameters: Any, eventData: String? = null) {}

    /**
     * Logs a VERBOSE level message.
     *
     * The [message] is a format string, with [parameters] substituted into it. An optional
     * [eventData] string may also be provided, which implementations can include in the log output.
     */
    fun v(message: String, vararg parameters: Any, eventData: String? = null) {}

    /**
     * Logs an INFO level message.
     *
     * The [message] is a format string, with [parameters] substituted into it. An optional
     * [eventData] string may also be provided, which implementations can include in the log output.
     */
    fun i(message: String, vararg parameters: Any, eventData: String? = null) {}

    /**
     * Logs a WARNING level message.
     *
     * The [message] is a format string, with [parameters] substituted into it. An optional
     * [eventData] string may also be provided, which implementations can include in the log output.
     */
    fun w(message: String, vararg parameters: Any, eventData: String? = null) {}

    /**
     * Logs an ERROR level message.
     *
     * The [message] is a format string, with [parameters] substituted into it. An optional
     * [eventData] string may also be provided, which implementations can include in the log output.
     */
    fun e(message: String, vararg parameters: Any, eventData: String? = null) {}
}
