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
package com.android.quickstep.util

import android.util.Pair
import com.android.internal.logging.InstanceIdSequence
import com.android.launcher3.logging.InstanceId

object LogUtils {
    @JvmStatic
    fun splitFailureMessage(caller: String, reason: String): String {
        return "($caller) Splitscreen aborted: $reason"
    }

    /**
     * @return a [Pair] of two InstanceIds but with different types, one that can be used by
     *   framework (if needing to pass through an intent or such) and one used in Launcher
     */
    @JvmStatic
    fun getShellShareableInstanceId(): Pair<com.android.internal.logging.InstanceId, InstanceId> {
        val internalInstanceId = InstanceIdSequence(InstanceId.INSTANCE_ID_MAX).newInstanceId()
        val launcherInstanceId = InstanceId(internalInstanceId.id)
        return Pair(internalInstanceId, launcherInstanceId)
    }
}
