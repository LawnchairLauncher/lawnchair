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
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.time.Duration

/** A helper class that is responsible for building the digital wellbeing timer text. */
class TimerTextHelper(private val context: Context, timeLeft: Duration) {
    val formattedDuration =
        DurationFormatter.format(context, timeLeft, R.string.shorter_duration_less_than_one_minute)

    /** Provides the time left as a user friendly text that fits in the [availableWidth]. */
    fun getTextThatFits(availableWidth: Int, textPaint: android.text.TextPaint): CharSequence {
        val iconOnlyText = Utilities.prefixTextWithIcon(context, R.drawable.ic_hourglass_top, "")

        if (availableWidth == 0) {
            return iconOnlyText
        }

        // "$icon $formattedDuration left today"
        val fullText =
            Utilities.prefixTextWithIcon(
                context,
                R.drawable.ic_hourglass_top,
                context.getString(R.string.time_left_for_app, formattedDuration),
            )

        val textWidth = textPaint.measureText(fullText, /* start= */ 0, /* end= */ fullText.length)
        val textToWidthRatio = textWidth / availableWidth
        return when {
            textToWidthRatio > ICON_ONLY_WIDTH_RATIO_THRESHOLD ->
                // "$icon"
                iconOnlyText

            textToWidthRatio > ICON_SHORT_TEXT_WIDTH_RATIO_THRESHOLD ->
                // "$icon $formattedDuration"
                Utilities.prefixTextWithIcon(
                    context,
                    R.drawable.ic_hourglass_top,
                    formattedDuration,
                )

            else -> fullText
        }
    }

    companion object {
        // If the full text ("$icon $formattedDuration left today") takes a lot more space than is
        // available, it is likely short text won't fit either. So, we fallback to just an icon.
        private const val ICON_ONLY_WIDTH_RATIO_THRESHOLD = 1.2f

        // If the full text fits but leaves very little space, use short text instead for
        // comfortable viewing.
        private const val ICON_SHORT_TEXT_WIDTH_RATIO_THRESHOLD = 0.8f
    }
}
