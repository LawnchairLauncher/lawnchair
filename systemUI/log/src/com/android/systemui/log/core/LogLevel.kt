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

import android.util.Log

/** Enum version of @Log.Level */
enum class LogLevel(
    val nativeLevel: Int,
    val shortString: String,
    val logcatFunc: (String, String, Throwable?) -> Unit,
) {
    VERBOSE(Log.VERBOSE, "V", Log::v),
    DEBUG(Log.DEBUG, "D", Log::d),
    INFO(Log.INFO, "I", Log::i),
    WARNING(Log.WARN, "W", Log::w),
    ERROR(Log.ERROR, "E", Log::e),
    WTF(Log.ASSERT, "WTF", Log::wtf),
}
