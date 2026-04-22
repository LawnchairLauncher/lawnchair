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

/** Keeps track of which [LogBuffer] messages should also appear in logcat. */
interface LogcatEchoTracker {
    /** Whether [bufferName] should echo messages of [level] or higher to logcat. */
    fun isBufferLoggable(bufferName: String, level: LogLevel): Boolean

    /** Whether [tagName] should echo messages of [level] or higher to logcat. */
    fun isTagLoggable(tagName: String, level: LogLevel): Boolean
}
