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

package com.android.quickstep.recents.domain.usecase

import android.os.UserHandle
import com.android.quickstep.recents.data.AppTimersRepository
import java.time.Duration

/**
 * Use case that provides remaining duration on the app usage limit timer set for the given user.
 *
 * Responsible for applying business rules around how the remaining time is treated within overview
 * module e.g. rounds partial minutes to the next minute.
 */
class GetRemainingAppTimerDurationUseCase(private val appTimersRepository: AppTimersRepository) {
    suspend operator fun invoke(packageName: String, userHandle: UserHandle): Duration? {
        val totalRemainingDuration =
            appTimersRepository.getRemainingDuration(packageName, userHandle) ?: return null
        val totalRemainingMs = totalRemainingDuration.toMillis()

        val isLessThanAMinute = totalRemainingMs < MS_IN_A_MINUTE
        val isInWholeMinutes = (totalRemainingMs % MS_IN_A_MINUTE) == 0L
        return when {
            isLessThanAMinute || isInWholeMinutes -> totalRemainingDuration
            else -> Duration.ofMinutes(totalRemainingDuration.toMinutes() + 1)
        }
    }

    companion object {
        private const val MS_IN_A_MINUTE: Int = 60000
    }
}
