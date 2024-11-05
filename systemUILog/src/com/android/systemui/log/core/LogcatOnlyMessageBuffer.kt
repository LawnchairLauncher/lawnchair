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

import android.util.Log
import com.android.systemui.log.LogMessageImpl

/**
 * A simple implementation of [MessageBuffer] that forwards messages to [android.util.Log]
 * immediately. This defeats the intention behind [LogBuffer] and should only be used when
 * [LogBuffer]s are unavailable in a certain context.
 */
class LogcatOnlyMessageBuffer(
    val targetLogLevel: LogLevel,
) : MessageBuffer {
    private val singleMessage = LogMessageImpl.Factory.create()
    private var isObtained: Boolean = false

    @Synchronized
    override fun obtain(
        tag: String,
        level: LogLevel,
        messagePrinter: MessagePrinter,
        exception: Throwable?,
    ): LogMessage {
        if (isObtained) {
            throw UnsupportedOperationException(
                "Message has already been obtained. Call order is incorrect."
            )
        }

        singleMessage.reset(tag, level, System.currentTimeMillis(), messagePrinter, exception)
        isObtained = true
        return singleMessage
    }

    @Synchronized
    override fun commit(message: LogMessage) {
        if (singleMessage != message) {
            throw IllegalArgumentException("Message argument is not the expected message.")
        }
        if (!isObtained) {
            throw UnsupportedOperationException(
                "Message has not been obtained. Call order is incorrect."
            )
        }

        if (message.level >= targetLogLevel) {
            val strMessage = message.messagePrinter(message)
            when (message.level) {
                LogLevel.VERBOSE -> Log.v(message.tag, strMessage, message.exception)
                LogLevel.DEBUG -> Log.d(message.tag, strMessage, message.exception)
                LogLevel.INFO -> Log.i(message.tag, strMessage, message.exception)
                LogLevel.WARNING -> Log.w(message.tag, strMessage, message.exception)
                LogLevel.ERROR -> Log.e(message.tag, strMessage, message.exception)
                LogLevel.WTF -> Log.wtf(message.tag, strMessage, message.exception)
            }
        }

        isObtained = false
    }
}
