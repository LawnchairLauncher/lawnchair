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

import android.os.Trace
import android.util.Log
import com.android.systemui.common.buffer.RingBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.core.LogMessage
import com.android.systemui.log.core.MessageBuffer
import com.android.systemui.log.core.MessageInitializer
import com.android.systemui.log.core.MessagePrinter
import com.google.errorprone.annotations.CompileTimeConstant
import java.io.PrintWriter
import kotlin.math.max

/**
 * A simple ring buffer of recyclable log messages
 *
 * The goal of this class is to enable logging that is both extremely chatty and extremely
 * lightweight. If done properly, logging a message will not result in any heap allocations or
 * string generation. Messages are only converted to strings if the log is actually dumped (usually
 * as the result of taking a bug report).
 *
 * You can dump the entire buffer at any time by running:
 * ```
 * $ adb shell dumpsys activity service com.android.systemui/.SystemUIService <bufferName>
 * ```
 *
 * ...where `bufferName` is the (case-sensitive) [name] passed to the constructor.
 *
 * By default, only messages of WARN level or higher are echoed to logcat, but this can be adjusted
 * locally (usually for debugging purposes).
 *
 * To enable logcat echoing for an entire buffer:
 * ```
 * $ adb shell cmd statusbar echo -b <bufferName>:<level>
 * ```
 *
 * To enable logcat echoing for a specific tag:
 * ```
 * $ adb shell cmd statusbar echo -t <tagName>:<level>
 * ```
 *
 * In either case, `level` can be any of `verbose`, `debug`, `info`, `warn`, `error`, `assert`, or
 * the first letter of any of the previous.
 *
 * In SystemUI, buffers are provided by LogModule. Instances should be created using a SysUI
 * LogBufferFactory.
 *
 * @param name The name of this buffer, printed when the buffer is dumped and in some other
 *   situations.
 * @param maxSize The maximum number of messages to keep in memory at any one time. Buffers start
 *   out empty and grow up to [maxSize] as new messages are logged. Once the buffer's size reaches
 *   the maximum, it behaves like a ring buffer.
 */
class LogBuffer
@JvmOverloads
constructor(
    private val name: String,
    private val maxSize: Int,
    private val logcatEchoTracker: LogcatEchoTracker,
    private val systrace: Boolean = true,
) : MessageBuffer {
    private val buffer = RingBuffer(maxSize) { LogMessageImpl.create() }

    var frozen = false
        private set

    private val mutable
        get() = !frozen && maxSize > 0

    /**
     * Logs a message to the log buffer
     *
     * May also log the message to logcat if echoing is enabled for this buffer or tag.
     *
     * The actual string of the log message is not constructed until it is needed. To accomplish
     * this, logging a message is a two-step process. First, a fresh instance of [LogMessage] is
     * obtained and is passed to the [messageInitializer]. The initializer stores any relevant data
     * on the message's fields. The message is then inserted into the buffer where it waits until it
     * is either pushed out by newer messages or it needs to printed. If and when this latter moment
     * occurs, the [messagePrinter] function is called on the message. It reads whatever data the
     * initializer stored and converts it to a human-readable log message.
     *
     * @param tag A string of at most 23 characters, used for grouping logs into categories or
     *   subjects. If this message is echoed to logcat, this will be the tag that is used.
     * @param level Which level to log the message at, both to the buffer and to logcat if it's
     *   echoed. In general, a module should split most of its logs into either INFO or DEBUG level.
     *   INFO level should be reserved for information that other parts of the system might care
     *   about, leaving the specifics of code's day-to-day operations to DEBUG.
     * @param messageInitializer A function that will be called immediately to store relevant data
     *   on the log message. The value of `this` will be the LogMessage to be initialized.
     * @param messagePrinter A function that will be called if and when the message needs to be
     *   dumped to logcat or a bug report. It should read the data stored by the initializer and
     *   convert it to a human-readable string. The value of `this` will be the LogMessage to be
     *   printed. **IMPORTANT:** The printer should ONLY ever reference fields on the LogMessage and
     *   NEVER any variables in its enclosing scope. Otherwise, the runtime will need to allocate a
     *   new instance of the printer for each call, thwarting our attempts at avoiding any sort of
     *   allocation.
     * @param exception Provide any exception that need to be logged. This is saved as
     *   [LogMessage.exception]
     */
    @JvmOverloads
    inline fun log(
        tag: String,
        level: LogLevel,
        messageInitializer: MessageInitializer,
        noinline messagePrinter: MessagePrinter,
        exception: Throwable? = null,
    ) {
        val message = obtain(tag, level, messagePrinter, exception)
        messageInitializer(message)
        commit(message)
    }

    /**
     * Logs a compile-time string constant [message] to the log buffer. Use sparingly.
     *
     * May also log the message to logcat if echoing is enabled for this buffer or tag. This is for
     * simpler use-cases where [message] is a compile time string constant. For use-cases where the
     * log message is built during runtime, use the [LogBuffer.log] overloaded method that takes in
     * an initializer and a message printer.
     *
     * Log buffers are limited by the number of entries, so logging more frequently will limit the
     * time window that the LogBuffer covers in a bug report. Richer logs, on the other hand, make a
     * bug report more actionable, so using the [log] with a messagePrinter to add more detail to
     * every log may do more to improve overall logging than adding more logs with this method.
     */
    @JvmOverloads
    fun log(
        tag: String,
        level: LogLevel,
        @CompileTimeConstant message: String,
        exception: Throwable? = null,
    ) = log(tag, level, { str1 = message }, { str1!! }, exception)

    /**
     * You should call [log] instead of this method.
     *
     * Obtains the next [LogMessage] from the ring buffer. If the buffer is not yet at max size,
     * grows the buffer by one.
     *
     * After calling [obtain], the message will now be at the end of the buffer. The caller must
     * store any relevant data on the message and then call [commit].
     */
    @Synchronized
    override fun obtain(
        tag: String,
        level: LogLevel,
        messagePrinter: MessagePrinter,
        exception: Throwable?,
    ): LogMessage {
        if (!mutable) {
            return FROZEN_MESSAGE
        }
        val message = buffer.advance()
        message.reset(tag, level, System.currentTimeMillis(), messagePrinter, exception)
        return message
    }

    /**
     * You should call [log] instead of this method.
     *
     * After acquiring a message via [obtain], call this method to signal to the buffer that you
     * have finished filling in its data fields. The message will be echoed to logcat if necessary.
     */
    @Synchronized
    override fun commit(message: LogMessage) {
        if (!mutable) {
            return
        }
        echoToDesiredEndpoints(message)
    }

    /** Sends message to echo after determining whether to use Logcat and/or systrace. */
    private fun echoToDesiredEndpoints(message: LogMessage) {
        val includeInLogcat =
            logcatEchoTracker.isBufferLoggable(name, message.level) ||
                logcatEchoTracker.isTagLoggable(message.tag, message.level)

        val includeInSystrace = systrace && Trace.isTagEnabled(Trace.TRACE_TAG_APP)

        if (includeInLogcat || includeInSystrace) {
            val strMessage = message.messagePrinter(message)
            if (includeInLogcat) {
                echoToLogcat(message, strMessage)
            }
            if (includeInSystrace) {
                echoToSystrace(message.level, message.tag, strMessage)
            }
        }
    }

    /** Converts the entire buffer to a newline-delimited string */
    @Synchronized
    fun dump(pw: PrintWriter, tailLength: Int) {
        val iterationStart =
            if (tailLength <= 0) {
                0
            } else {
                max(0, buffer.size - tailLength)
            }

        for (i in iterationStart until buffer.size) {
            buffer[i].dump(pw)
        }
    }

    /**
     * "Freezes" the contents of the buffer, making it immutable until [unfreeze] is called. Calls
     * to [log], [obtain], and [commit] will not affect the buffer and will return dummy values if
     * necessary.
     */
    @Synchronized
    fun freeze() {
        if (!frozen) {
            log(TAG, LogLevel.DEBUG, { str1 = name }, { "$str1 frozen" })
            frozen = true
        }
    }

    /** Undoes the effects of calling [freeze]. */
    @Synchronized
    fun unfreeze() {
        if (frozen) {
            frozen = false
            log(TAG, LogLevel.DEBUG, { str1 = name }, { "$str1 unfrozen" })
        }
    }

    private fun echoToSystrace(level: LogLevel, tag: String, strMessage: String) {
        Trace.instantForTrack(
            Trace.TRACE_TAG_APP,
            "UI Events",
            "$name - ${level.shortString} $tag: $strMessage"
        )
    }

    private fun echoToLogcat(message: LogMessage, strMessage: String) {
        when (message.level) {
            LogLevel.VERBOSE -> Log.v(message.tag, strMessage, message.exception)
            LogLevel.DEBUG -> Log.d(message.tag, strMessage, message.exception)
            LogLevel.INFO -> Log.i(message.tag, strMessage, message.exception)
            LogLevel.WARNING -> Log.w(message.tag, strMessage, message.exception)
            LogLevel.ERROR -> Log.e(message.tag, strMessage, message.exception)
            LogLevel.WTF -> Log.wtf(message.tag, strMessage, message.exception)
        }
    }
}

private const val TAG = "LogBuffer"
private val FROZEN_MESSAGE = LogMessageImpl.create()
