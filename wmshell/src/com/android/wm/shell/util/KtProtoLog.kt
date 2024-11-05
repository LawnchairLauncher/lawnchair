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

package com.android.wm.shell.util

import android.util.Log
import com.android.internal.protolog.common.IProtoLogGroup
import com.android.internal.protolog.common.ProtoLog

/**
 * Log messages using an API similar to [com.android.internal.protolog.common.ProtoLog]. Useful for
 * logging from Kotlin classes as ProtoLog does not have support for Kotlin.
 *
 * All messages are logged to logcat if logging is enabled for that [IProtoLogGroup].
 */
// TODO(b/168581922): remove once ProtoLog adds support for Kotlin
class KtProtoLog {
    companion object {
        /** @see [com.android.internal.protolog.common.ProtoLog.d] */
        fun d(group: IProtoLogGroup, messageString: String, vararg args: Any) {
            if (group.isLogToLogcat) {
                Log.d(group.tag, String.format(messageString, *args))
            }
        }

        /** @see [com.android.internal.protolog.common.ProtoLog.v] */
        fun v(group: IProtoLogGroup, messageString: String, vararg args: Any) {
            if (group.isLogToLogcat) {
                Log.v(group.tag, String.format(messageString, *args))
            }
        }

        /** @see [com.android.internal.protolog.common.ProtoLog.i] */
        fun i(group: IProtoLogGroup, messageString: String, vararg args: Any) {
            if (group.isLogToLogcat) {
                Log.i(group.tag, String.format(messageString, *args))
            }
        }

        /** @see [com.android.internal.protolog.common.ProtoLog.w] */
        fun w(group: IProtoLogGroup, messageString: String, vararg args: Any) {
            if (group.isLogToLogcat) {
                Log.w(group.tag, String.format(messageString, *args))
            }
        }

        /** @see [com.android.internal.protolog.common.ProtoLog.e] */
        fun e(group: IProtoLogGroup, messageString: String, vararg args: Any) {
            if (group.isLogToLogcat) {
                Log.e(group.tag, String.format(messageString, *args))
            }
        }

        /** @see [com.android.internal.protolog.common.ProtoLog.wtf] */
        fun wtf(group: IProtoLogGroup, messageString: String, vararg args: Any) {
            if (group.isLogToLogcat) {
                Log.wtf(group.tag, String.format(messageString, *args))
            }
        }
    }
}
