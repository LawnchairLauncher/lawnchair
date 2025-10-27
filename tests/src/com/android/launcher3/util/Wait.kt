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
package com.android.launcher3.util

import android.os.SystemClock
import android.util.Log
import com.android.launcher3.tapl.LauncherInstrumentation
import java.util.function.Supplier
import org.junit.Assert

/** A utility class for waiting for a condition to be true. */
object Wait {
    private const val DEFAULT_SLEEP_MS: Long = 200

    @JvmStatic
    @JvmOverloads
    fun atMost(
        message: String,
        condition: Condition,
        launcherInstrumentation: LauncherInstrumentation? = null,
        timeout: Long = TestUtil.DEFAULT_UI_TIMEOUT,
    ) {
        atMost({ message }, condition, launcherInstrumentation, timeout)
    }

    @JvmStatic
    @JvmOverloads
    fun atMost(
        message: Supplier<String>,
        condition: Condition,
        launcherInstrumentation: LauncherInstrumentation? = null,
        timeout: Long = TestUtil.DEFAULT_UI_TIMEOUT,
    ) {
        val startTime = SystemClock.uptimeMillis()
        val endTime = startTime + timeout
        Log.d("Wait", "atMost: $startTime - $endTime")
        while (SystemClock.uptimeMillis() < endTime) {
            try {
                if (condition.isTrue()) {
                    return
                }
            } catch (t: Throwable) {
                throw RuntimeException(t)
            }
            SystemClock.sleep(DEFAULT_SLEEP_MS)
        }

        // Check once more before returning false.
        try {
            if (condition.isTrue()) {
                return
            }
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }
        Log.d("Wait", "atMost: timed out: " + SystemClock.uptimeMillis())
        launcherInstrumentation?.checkForAnomaly(false, false)
        Assert.fail(message.get())
    }

    /** Interface representing a generic condition */
    fun interface Condition {

        @Throws(Throwable::class) fun isTrue(): Boolean
    }
}
