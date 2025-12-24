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

package com.android.wm.shell.bubbles.logging

import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES
import com.android.wm.shell.shared.bubbles.logging.DebugLogger

/**
 * An implementation of [DebugLogger] that logs events to [ProtoLog] with [WM_SHELL_BUBBLES] group.
 */
class BubbleProtoLog : DebugLogger {

    override fun d(message: String, vararg parameters: Any, eventData: String?) {
        ProtoLog.d(WM_SHELL_BUBBLES, message, *parameters)
    }

    override fun v(message: String, vararg parameters: Any, eventData: String?) {
        ProtoLog.v(WM_SHELL_BUBBLES, message, *parameters)
    }

    override fun i(message: String, vararg parameters: Any, eventData: String?) {
        ProtoLog.i(WM_SHELL_BUBBLES, message, *parameters)
    }

    override fun w(message: String, vararg parameters: Any, eventData: String?) {
        ProtoLog.w(WM_SHELL_BUBBLES, message, *parameters)
    }

    override fun e(message: String, vararg parameters: Any, eventData: String?) {
        ProtoLog.e(WM_SHELL_BUBBLES, message, *parameters)
    }
}
