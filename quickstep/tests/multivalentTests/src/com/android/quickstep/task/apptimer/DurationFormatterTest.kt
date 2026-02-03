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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.R
import com.android.launcher3.util.SandboxApplication
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DurationFormatterTest {
    @get:Rule val context = SandboxApplication()

    private var systemLocale: Locale? = null

    @Before
    fun setup() {
        systemLocale = Locale.getDefault()
        val testLocale = Locale("en", "us")
        Locale.setDefault(testLocale)
    }

    @Test
    fun getReadableDuration_hasHoursAndMinutes_returnsNarrowString() {
        val result =
            DurationFormatter.format(
                context,
                Duration.ofHours(12).plusMinutes(55),
                durationLessThanOneMinuteStringId = R.string.shorter_duration_less_than_one_minute,
            )

        val expected = "12h 55m"
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun getReadableDuration_hasFullHours_returnsWideString() {
        val result =
            DurationFormatter.format(
                context = context,
                duration = Duration.ofHours(12),
                durationLessThanOneMinuteStringId = R.string.shorter_duration_less_than_one_minute,
            )

        val expected = "12 hours"
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun getReadableDuration_hasFullMinutesNoHours_returnsWideString() {
        val result =
            DurationFormatter.format(
                context = context,
                duration = Duration.ofMinutes(50),
                durationLessThanOneMinuteStringId = R.string.shorter_duration_less_than_one_minute,
            )

        val expected = "50 minutes"
        assertThat(result).isEqualTo(expected)
    }

    @After
    fun tearDown() {
        Locale.setDefault(systemLocale)
    }
}
