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

/**
 * [MessageBuffer] is an interface that represents a buffer of log messages, and provides methods to
 * [obtain] a log message and [commit] it to the buffer.
 */
interface MessageBuffer {
    /**
     * Obtains the next [LogMessage] from the buffer.
     *
     * After calling [obtain], the caller must store any relevant data on the message and then call
     * [commit].
     */
    fun obtain(
        tag: String,
        level: LogLevel,
        messagePrinter: MessagePrinter,
        exception: Throwable? = null,
    ): LogMessage

    /**
     * After acquiring a log message via [obtain], call this method to signal to the buffer that
     * data fields have been filled.
     */
    fun commit(message: LogMessage)
}
