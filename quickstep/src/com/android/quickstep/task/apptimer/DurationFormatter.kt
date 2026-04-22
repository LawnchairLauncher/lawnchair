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

package com.android.quickstep.task.apptimer

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.annotation.StringRes
import java.time.Duration
import java.util.Locale

/** Formats the given duration as a user friendly text. */
object DurationFormatter {
    fun format(
        context: Context,
        duration: Duration,
        @StringRes durationLessThanOneMinuteStringId: Int,
    ): String {
        val hours = Math.toIntExact(duration.toHours())
        val minutes = Math.toIntExact(duration.minusHours(hours.toLong()).toMinutes())
        return when {
            // Apply FormatWidth.NARROW if both the hour part and the minute part are non-zero.
            hours > 0 && minutes > 0 ->
                MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.NARROW)
                    .formatMeasures(
                        Measure(hours, MeasureUnit.HOUR),
                        Measure(minutes, MeasureUnit.MINUTE),
                    )
            // Apply FormatWidth.WIDE if only the hour part is non-zero (unless forced).
            hours > 0 ->
                MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
                    .formatMeasures(Measure(hours, MeasureUnit.HOUR))
            // Apply FormatWidth.WIDE if only the minute part is non-zero (unless forced).
            minutes > 0 ->
                MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
                    .formatMeasures(Measure(minutes, MeasureUnit.MINUTE))
            // Use a specific string for usage less than one minute but non-zero.
            duration > Duration.ZERO -> context.getString(durationLessThanOneMinuteStringId)
            // Otherwise, return 0-minute string.
            else ->
                MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
                    .formatMeasures(Measure(0, MeasureUnit.MINUTE))
        }
    }
}
