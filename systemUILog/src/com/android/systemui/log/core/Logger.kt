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

package com.android.systemui.log.core

import com.google.errorprone.annotations.CompileTimeConstant

/** Logs messages to the [MessageBuffer] with [tag]. */
open class Logger(val buffer: MessageBuffer, val tag: String) {
    /**
     * Logs a message to the buffer.
     *
     * The actual string of the log message is not constructed until it is needed. To accomplish
     * this, logging a message is a two-step process. First, a fresh instance of [LogMessage] is
     * obtained and is passed to the [messageInitializer]. The initializer stores any relevant data
     * on the message's fields. The message is then inserted into the buffer where it waits until it
     * is either pushed out by newer messages or it needs to printed. If and when this latter moment
     * occurs, the [messagePrinter] function is called on the message. It reads whatever data the
     * initializer stored and converts it to a human-readable log message.
     *
     * @param level Which level to log the message at, both to the buffer and to logcat if it's
     *   echoed. In general, a module should split most of its logs into either INFO or DEBUG level.
     *   INFO level should be reserved for information that other parts of the system might care
     *   about, leaving the specifics of code's day-to-day operations to DEBUG.
     * @param messagePrinter A function that will be called if and when the message needs to be
     *   dumped to logcat or a bug report. It should read the data stored by the initializer and
     *   convert it to a human-readable string. The value of `this` will be the [LogMessage] to be
     *   printed. **IMPORTANT:** The printer should ONLY ever reference fields on the [LogMessage]
     *   and NEVER any variables in its enclosing scope. Otherwise, the runtime will need to
     *   allocate a new instance of the printer for each call, thwarting our attempts at avoiding
     *   any sort of allocation.
     * @param exception Provide any exception that need to be logged. This is saved as
     *   [LogMessage.exception]
     * @param messageInitializer A function that will be called immediately to store relevant data
     *   on the log message. The value of `this` will be the [LogMessage] to be initialized.
     */
    @JvmOverloads
    inline fun log(
        level: LogLevel,
        noinline messagePrinter: MessagePrinter,
        exception: Throwable? = null,
        messageInitializer: MessageInitializer,
    ) {
        val message = buffer.obtain(tag, level, messagePrinter, exception)
        messageInitializer(message)
        buffer.commit(message)
    }

    /**
     * Logs a compile-time string constant [message] to the log buffer. Use sparingly.
     *
     * This is for simpler use-cases where [message] is a compile time string constant. For
     * use-cases where the log message is built during runtime, use the [log] overloaded method that
     * takes in an initializer and a message printer.
     *
     * Buffers are limited by the number of entries, so logging more frequently will limit the time
     * window that the [MessageBuffer] covers in a bug report. Richer logs, on the other hand, make
     * a bug report more actionable, so using the [log] with a [MessagePrinter] to add more details
     * to every log may do more to improve overall logging than adding more logs with this method.
     */
    @JvmOverloads
    fun log(
        level: LogLevel,
        @CompileTimeConstant message: String,
        exception: Throwable? = null,
    ) = log(level, { str1!! }, exception) { str1 = message }

    /**
     * Logs a message to the buffer at [LogLevel.VERBOSE].
     *
     * @see log
     */
    @JvmOverloads
    inline fun v(
        noinline messagePrinter: MessagePrinter,
        exception: Throwable? = null,
        messageInitializer: MessageInitializer,
    ) = log(LogLevel.VERBOSE, messagePrinter, exception, messageInitializer)

    /**
     * Logs a compile-time string constant [message] to the log buffer at [LogLevel.VERBOSE]. Use
     * sparingly.
     *
     * @see log
     */
    @JvmOverloads
    fun v(
        @CompileTimeConstant message: String,
        exception: Throwable? = null,
    ) = log(LogLevel.VERBOSE, message, exception)

    /**
     * Logs a message to the buffer at [LogLevel.DEBUG].
     *
     * @see log
     */
    @JvmOverloads
    inline fun d(
        noinline messagePrinter: MessagePrinter,
        exception: Throwable? = null,
        messageInitializer: MessageInitializer,
    ) = log(LogLevel.DEBUG, messagePrinter, exception, messageInitializer)

    /**
     * Logs a compile-time string constant [message] to the log buffer at [LogLevel.DEBUG]. Use
     * sparingly.
     *
     * @see log
     */
    @JvmOverloads
    fun d(
        @CompileTimeConstant message: String,
        exception: Throwable? = null,
    ) = log(LogLevel.DEBUG, message, exception)

    /**
     * Logs a message to the buffer at [LogLevel.INFO].
     *
     * @see log
     */
    @JvmOverloads
    inline fun i(
        noinline messagePrinter: MessagePrinter,
        exception: Throwable? = null,
        messageInitializer: MessageInitializer,
    ) = log(LogLevel.INFO, messagePrinter, exception, messageInitializer)

    /**
     * Logs a compile-time string constant [message] to the log buffer at [LogLevel.INFO]. Use
     * sparingly.
     *
     * @see log
     */
    @JvmOverloads
    fun i(
        @CompileTimeConstant message: String,
        exception: Throwable? = null,
    ) = log(LogLevel.INFO, message, exception)

    /**
     * Logs a message to the buffer at [LogLevel.WARNING].
     *
     * @see log
     */
    @JvmOverloads
    inline fun w(
        noinline messagePrinter: MessagePrinter,
        exception: Throwable? = null,
        messageInitializer: MessageInitializer,
    ) = log(LogLevel.WARNING, messagePrinter, exception, messageInitializer)

    /**
     * Logs a compile-time string constant [message] to the log buffer at [LogLevel.WARNING]. Use
     * sparingly.
     *
     * @see log
     */
    @JvmOverloads
    fun w(
        @CompileTimeConstant message: String,
        exception: Throwable? = null,
    ) = log(LogLevel.WARNING, message, exception)

    /**
     * Logs a message to the buffer at [LogLevel.ERROR].
     *
     * @see log
     */
    @JvmOverloads
    inline fun e(
        noinline messagePrinter: MessagePrinter,
        exception: Throwable? = null,
        messageInitializer: MessageInitializer,
    ) = log(LogLevel.ERROR, messagePrinter, exception, messageInitializer)

    /**
     * Logs a compile-time string constant [message] to the log buffer at [LogLevel.ERROR]. Use
     * sparingly.
     *
     * @see log
     */
    @JvmOverloads
    fun e(
        @CompileTimeConstant message: String,
        exception: Throwable? = null,
    ) = log(LogLevel.ERROR, message, exception)

    /**
     * Logs a message to the buffer at [LogLevel.WTF].
     *
     * @see log
     */
    @JvmOverloads
    inline fun wtf(
        noinline messagePrinter: MessagePrinter,
        exception: Throwable? = null,
        messageInitializer: MessageInitializer,
    ) = log(LogLevel.WTF, messagePrinter, exception, messageInitializer)

    /**
     * Logs a compile-time string constant [message] to the log buffer at [LogLevel.WTF]. Use
     * sparingly.
     *
     * @see log
     */
    @JvmOverloads
    fun wtf(
        @CompileTimeConstant message: String,
        exception: Throwable? = null,
    ) = log(LogLevel.WTF, message, exception)
}
