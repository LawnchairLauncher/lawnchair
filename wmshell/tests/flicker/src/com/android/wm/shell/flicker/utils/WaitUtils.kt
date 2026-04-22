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

@file:JvmName("WaitUtils")

package com.android.wm.shell.flicker.utils

import android.os.SystemClock

private const val DEFAULT_TIMEOUT = 10000L
private const val DEFAULT_POLL_INTERVAL = 1000L

fun wait(condition: () -> Boolean): Boolean {
    val (success, _) = waitForResult(extractor = condition, validator = { it })
    return success
}

fun <R> waitForResult(
    timeout: Long = DEFAULT_TIMEOUT,
    interval: Long = DEFAULT_POLL_INTERVAL,
    extractor: () -> R,
    validator: (R) -> Boolean = { it != null }
): Pair<Boolean, R?> {
    val startTime = SystemClock.uptimeMillis()
    do {
        val result = extractor()
        if (validator(result)) {
            return (true to result)
        }
        SystemClock.sleep(interval)
    } while (SystemClock.uptimeMillis() - startTime < timeout)

    return (false to null)
}
