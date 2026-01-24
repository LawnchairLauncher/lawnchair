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

package com.android.quickstep.recents.data

import android.os.UserHandle
import java.time.Duration

/**
 * A fake implementation of [AppTimersRepository] that supports seeding the timer information for
 * testing.
 */
class FakeAppTimersRepository : AppTimersRepository {
    private val timers: MutableMap<TimerKey, Duration> = mutableMapOf()

    override suspend fun getRemainingDuration(
        packageName: String,
        userHandle: UserHandle,
    ): Duration? = timers[TimerKey(packageName, userHandle)]

    /** Seed timer info for an app identified by the provided [packageName] and [userHandle]. */
    fun setTimer(packageName: String, userHandle: UserHandle, remainingDuration: Duration) {
        timers[TimerKey(packageName, userHandle)] = remainingDuration
    }

    /** Clear timer for an app identified by the provided [packageName] and [userHandle]. */
    fun resetTimer(packageName: String, userHandle: UserHandle) {
        timers.remove(TimerKey(packageName, userHandle))
    }

    private data class TimerKey(val packageName: String, val userHandle: UserHandle)
}
