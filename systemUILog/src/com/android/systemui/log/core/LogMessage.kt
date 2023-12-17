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

package com.android.systemui.log.core

import android.icu.text.SimpleDateFormat
import java.io.PrintWriter
import java.util.Locale

/**
 * Generic data class for storing messages logged to a [LogBuffer]
 *
 * Each LogMessage has a few standard fields ([level], [tag], and [timestamp]). The rest are generic
 * data slots that may or may not be used, depending on the nature of the specific message being
 * logged.
 *
 * When a message is logged, the code doing the logging stores data in one or more of the generic
 * fields ([str1], [int1], etc). When it comes time to dump the message to logcat/bugreport/etc, the
 * [messagePrinter] function reads the data stored in the generic fields and converts that to a
 * human- readable string. Thus, for every log type there must be a specialized initializer function
 * that stores data specific to that log type and a specialized printer function that prints that
 * data.
 *
 * See [LogBuffer.log] for more information.
 */
interface LogMessage {
    val level: LogLevel
    val tag: String
    val timestamp: Long
    val messagePrinter: MessagePrinter
    val exception: Throwable?

    var str1: String?
    var str2: String?
    var str3: String?
    var int1: Int
    var int2: Int
    var long1: Long
    var long2: Long
    var double1: Double
    var bool1: Boolean
    var bool2: Boolean
    var bool3: Boolean
    var bool4: Boolean

    /** Function that dumps the [LogMessage] to the provided [writer]. */
    fun dump(writer: PrintWriter) {
        val formattedTimestamp = DATE_FORMAT.format(timestamp)
        val shortLevel = level.shortString
        val messageToPrint = messagePrinter(this)
        printLikeLogcat(writer, formattedTimestamp, shortLevel, tag, messageToPrint)
        exception?.printStackTrace(writer)
    }
}

/**
 * A function that will be called immediately to store relevant data on the log message. The value
 * of `this` will be the LogMessage to be initialized.
 */
typealias MessageInitializer = LogMessage.() -> Unit

/**
 * A function that will be called if and when the message needs to be dumped to logcat or a bug
 * report. It should read the data stored by the initializer and convert it to a human-readable
 * string. The value of `this` will be the LogMessage to be printed. **IMPORTANT:** The printer
 * should ONLY ever reference fields on the LogMessage and NEVER any variables in its enclosing
 * scope. Otherwise, the runtime will need to allocate a new instance of the printer for each call,
 * thwarting our attempts at avoiding any sort of allocation.
 */
typealias MessagePrinter = LogMessage.() -> String

private fun printLikeLogcat(
    pw: PrintWriter,
    formattedTimestamp: String,
    shortLogLevel: String,
    tag: String,
    message: String
) {
    pw.print(formattedTimestamp)
    pw.print(" ")
    pw.print(shortLogLevel)
    pw.print(" ")
    pw.print(tag)
    pw.print(": ")
    pw.println(message)
}

private val DATE_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
