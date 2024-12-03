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

import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.core.LogMessage
import com.android.systemui.log.core.MessagePrinter

/** Recyclable implementation of [LogMessage]. */
data class LogMessageImpl(
    override var level: LogLevel,
    override var tag: String,
    override var timestamp: Long,
    override var messagePrinter: MessagePrinter,
    override var exception: Throwable?,
    override var str1: String?,
    override var str2: String?,
    override var str3: String?,
    override var int1: Int,
    override var int2: Int,
    override var long1: Long,
    override var long2: Long,
    override var double1: Double,
    override var bool1: Boolean,
    override var bool2: Boolean,
    override var bool3: Boolean,
    override var bool4: Boolean,
) : LogMessage {

    fun reset(
        tag: String,
        level: LogLevel,
        timestamp: Long,
        renderer: MessagePrinter,
        exception: Throwable? = null,
    ) {
        this.level = level
        this.tag = tag
        this.timestamp = timestamp
        this.messagePrinter = renderer
        this.exception = exception
        str1 = null
        str2 = null
        str3 = null
        int1 = 0
        int2 = 0
        long1 = 0
        long2 = 0
        double1 = 0.0
        bool1 = false
        bool2 = false
        bool3 = false
        bool4 = false
    }

    companion object Factory {
        fun create(): LogMessageImpl {
            return LogMessageImpl(
                LogLevel.DEBUG,
                DEFAULT_TAG,
                0,
                DEFAULT_PRINTER,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0.0,
                false,
                false,
                false,
                false
            )
        }
    }
}

private const val DEFAULT_TAG = "UnknownTag"
private val DEFAULT_PRINTER: MessagePrinter = { "Unknown message: $this" }
