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

package com.android.quickstep.util

import android.util.TimeUtils
import android.view.Choreographer
import com.android.launcher3.util.window.RefreshRateTracker

/** [RefreshRateTracker] using main thread [Choreographer] */
object ChoreographerFrameRateTracker : RefreshRateTracker {

    override val singleFrameMs: Int
        get() =
            Choreographer.getMainThreadInstance()?.let {
                (it.frameIntervalNanos / TimeUtils.NANOS_PER_MS).toInt().coerceAtLeast(1)
            } ?: 1
}
