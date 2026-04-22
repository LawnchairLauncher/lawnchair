/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.back

import android.view.MotionEvent
import android.view.VelocityTracker

internal class ProgressVelocityTracker {
    private val velocityTracker: VelocityTracker = VelocityTracker.obtain()
    private var downTime = -1L

    fun addPosition(timeMillis: Long, position: Float) {
        if (downTime == -1L) downTime = timeMillis
        velocityTracker.addMovement(
            MotionEvent.obtain(
                /* downTime */ downTime,
                /* eventTime */ timeMillis,
                /* action */ MotionEvent.ACTION_MOVE,
                /* x */ position,
                /* y */ 0f,
                /* metaState */0
            )
        )
    }

    /** calculates current velocity (unit: progress per second) */
    fun calculateVelocity(): Float {
        velocityTracker.computeCurrentVelocity(1000)
        return velocityTracker.xVelocity
    }

    fun resetTracking() {
        velocityTracker.clear()
        downTime = -1L
    }
}