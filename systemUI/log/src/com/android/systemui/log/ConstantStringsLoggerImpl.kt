/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.google.errorprone.annotations.CompileTimeConstant

class ConstantStringsLoggerImpl(val buffer: LogBuffer, val tag: String) : ConstantStringsLogger {
    override fun v(@CompileTimeConstant msg: String) = buffer.log(tag, LogLevel.VERBOSE, msg)

    override fun d(@CompileTimeConstant msg: String) = buffer.log(tag, LogLevel.DEBUG, msg)

    override fun w(@CompileTimeConstant msg: String) = buffer.log(tag, LogLevel.WARNING, msg)

    override fun e(@CompileTimeConstant msg: String) = buffer.log(tag, LogLevel.ERROR, msg)
}
